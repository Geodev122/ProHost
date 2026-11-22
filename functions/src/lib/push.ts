import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
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
    // Firestore TTL deletes only on a Timestamp field; `expireAt` is epoch millis (the app compares it as a number), so
    // the policy on notifications.ttlAt (Maintenance `ttl`) clears expired items without the hourly prune job.
    ttlAt: Timestamp.fromMillis(now + NOTIFICATION_TTL_MS),
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
/**
 * The FCM message for one device. Android reads the data-only payload in ProHostMessagingService (unchanged); iOS needs
 * an APNs alert to show anything, and gets the same data keys (targetTab, bookingId, spaceId, notificationId) for the tap.
 */
export function fcmMessage(
  token: string,
  title: string,
  body: string,
  data: Record<string, string>,
  notificationId?: string
) {
  const category = data.category || "GENERAL";
  // A push older than the in-app copy (48 h) is no longer worth showing; FCM and APNs drop it instead of delivering late.
  const ttlSeconds = Math.floor(NOTIFICATION_TTL_MS / 1000);
  return {
    token,
    data: { title, body, ...data, ...(notificationId ? { notificationId } : {}) },
    android: { priority: "high" as const, ttl: NOTIFICATION_TTL_MS },
    apns: {
      headers: {
        "apns-priority": "10",
        "apns-expiration": String(Math.floor(Date.now() / 1000) + ttlSeconds),
        ...(notificationId ? { "apns-collapse-id": notificationId.slice(0, 64) } : {}),
      },
      // thread-id groups alerts of one kind (bookings, billing…) in Notification Centre, like Android's channels.
      payload: { aps: { alert: { title, body }, sound: "default", "thread-id": category } },
    },
  };
}

/** Every device token on a profile: the per-device list plus the legacy single field, de-duplicated, newest last, at most 10. */
export function deviceTokens(profile: { fcmToken?: unknown; fcmTokens?: unknown } | undefined): string[] {
  const list = Array.isArray(profile?.fcmTokens) ? (profile!.fcmTokens as unknown[]) : [];
  const all = [...list, profile?.fcmToken].filter((t): t is string => typeof t === "string" && t.length > 0);
  return [...new Set(all)].slice(-10);
}

const STALE_TOKEN_CODES = new Set(["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]);

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

    // Every signed-in device (a member with an Android phone and an iPhone gets the push on both).
    const tokens = deviceTokens(profileSnap.data());
    if (tokens.length === 0) {
      logger.info(`sendPushToUser: no device token on file for ${uid}, stored in-app only`);
      return;
    }

    const results = await Promise.allSettled(tokens.map((t) => getMessaging().send(fcmMessage(t, title, body, data, notificationId))));
    const stale = tokens.filter((_, i) => {
      const r = results[i];
      return r.status === "rejected" && STALE_TOKEN_CODES.has(String((r.reason as { code?: string })?.code));
    });
    if (stale.length > 0) {
      // An uninstalled app or a rotated token: forget it so future pushes don't keep failing.
      await db.runTransaction(async (tx) => {
        const current = (await tx.get(profileRef)).data();
        const update: Record<string, unknown> = { fcmTokens: FieldValue.arrayRemove(...stale) };
        if (typeof current?.fcmToken === "string" && stale.includes(current.fcmToken)) update.fcmToken = FieldValue.delete();
        tx.update(profileRef, update);
      });
      logger.info(`sendPushToUser: removed ${stale.length} stale device token(s) for ${uid}`);
    }
    const failed = results.filter((r, i) => r.status === "rejected" && !stale.includes(tokens[i]));
    if (failed.length > 0 && failed.length + stale.length === tokens.length) {
      throw (failed[0] as PromiseRejectedResult).reason;
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
