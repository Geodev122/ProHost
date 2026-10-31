import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { getFirestore } from "firebase-admin/firestore";
import { NOTIFICATIONS } from "../lib/push";
import "../lib/admin";

/**
 * Hourly: removes in-app notifications older than 48 h (their expireAt passed) from every
 * user's user_profiles/{uid}/notifications. The app also hides expired items, so an item
 * never shows longer than 48 h even between runs. Needs the collection-group index on
 * notifications.expireAt (firestore.indexes.json fieldOverrides).
 */
export const pruneExpiredNotifications = onSchedule({ schedule: "15 * * * *", timeoutSeconds: 300 }, async () => {
  const db = getFirestore();
  const now = Date.now();
  let deleted = 0;
  for (let page = 0; page < 50; page++) {
    const snap = await db.collectionGroup(NOTIFICATIONS).where("expireAt", "<", now).limit(400).get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    deleted += snap.size;
    if (snap.size < 400) break;
  }
  if (deleted > 0) logger.info(`pruneExpiredNotifications: deleted ${deleted}`);
});
