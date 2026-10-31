import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import "./admin";

export const NOTIFICATIONS = "notifications";
/** How long a notification stays in the in-app centre. */
export const NOTIFICATION_TTL_MS = 48 * 60 * 60 * 1000;

/** The stored in-app notification (user_profiles/{uid}/notifications/{id}). */
export function notificationDoc(title: string, body: string, data: Record<string, string>, now: number) {
  return {
    title,
    body,
    category: data.category ?? "GENERAL",
    targetTab: data.targetTab ?? null,
    bookingId: data.bookingId ?? null,
    spaceId: data.spaceId ?? null,
    createdAt: now,
    expireAt: now + NOTIFICATION_TTL_MS,
    read: false,
  };
}

/**
 * Sends a real FCM push to [uid]'s device, reading the token
 * ProSpaceMessagingService.onNewToken (client) persisted to
 * user_profiles/{uid}.fcmToken. Silently no-ops if the user has no token on file
 * (never signed in on a device with push configured, or hasn't opened the app
 * since this feature shipped) — this must never throw and break the write that
 * triggered it (booking creation/acceptance/rejection still has to succeed even
 * if the push itself can't be delivered).
 *
 * [data] is delivered as a data-only payload (not `notification`) so the client's
 * own FirebaseMessagingService.onMessageReceived always fires — including while
 * the app is foregrounded — and decides how to render it (matches the existing
 * category/title/body/targetTab/bookingId contract that service already parses).
 */
export async function sendPushToUser(
  uid: string,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  try {
    const db = getFirestore();
    const profileRef = db.collection("user_profiles").doc(uid);
    const profileSnap = await profileRef.get();
    if (!profileSnap.exists) return; // a deleted account: no inbox, no push

    // In-app notification centre: every push is kept for 48 h (pruneExpiredNotifications
    // deletes it afterwards), whether or not a device can receive it right now.
    let notificationId: string | undefined;
    try {
      const ref = profileRef.collection(NOTIFICATIONS).doc();
      await ref.set(notificationDoc(title, body, data, Date.now()));
      notificationId = ref.id;
    } catch (e) {
      logger.warn(`sendPushToUser: couldn't store the notification for ${uid}: ${(e as Error).message}`);
    }

    const token = profileSnap.data()?.fcmToken as string | undefined;
    if (!token) {
      logger.info(`sendPushToUser: no fcmToken on file for ${uid}, stored in-app only`);
      return;
    }

    try {
      await getMessaging().send({
        token,
        data: { title, body, ...data, ...(notificationId ? { notificationId } : {}) },
        android: { priority: "high" },
      });
    } catch (sendError) {
      const code = (sendError as { code?: string }).code;
      // An uninstalled app or a rotated token: forget it so future pushes don't keep
      // failing, but only if the profile still holds that same token.
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        await db.runTransaction(async (tx) => {
          const ref = db.collection("user_profiles").doc(uid);
          const current = (await tx.get(ref)).data()?.fcmToken;
          if (current === token) tx.update(ref, { fcmToken: FieldValue.delete() });
        });
        logger.info(`sendPushToUser: removed stale fcmToken for ${uid}`);
        return;
      }
      throw sendError;
    }
  } catch (e) {
    logger.error(`sendPushToUser failed for ${uid}: ${(e as Error).message}`);
  }
}

/**
 * Broadcasts a push to every ADMIN account with an fcmToken on file — for
 * events an Admin needs to act on (e.g. a new listing-verification request)
 * that aren't scoped to any single user the way sendPushToUser's are. Reuses
 * the same data-only payload contract; failures for one admin never block
 * delivery to the rest.
 */
export async function sendPushToAdmins(
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  try {
    const db = getFirestore();
    const adminsSnap = await db.collection("user_profiles").where("role", "==", "ADMIN").get();
    await Promise.all(adminsSnap.docs.map((doc) => sendPushToUser(doc.id, title, body, data)));
  } catch (e) {
    logger.error(`sendPushToAdmins failed: ${(e as Error).message}`);
  }
}
