import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import "./admin";

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
    const profileSnap = await db.collection("user_profiles").doc(uid).get();
    const token = profileSnap.data()?.fcmToken as string | undefined;
    if (!token) {
      logger.info(`sendPushToUser: no fcmToken on file for ${uid}, skipping push`);
      return;
    }

    await getMessaging().send({
      token,
      data: { title, body, ...data },
      android: { priority: "high" },
    });
  } catch (e) {
    logger.error(`sendPushToUser failed for ${uid}: ${(e as Error).message}`);
  }
}
