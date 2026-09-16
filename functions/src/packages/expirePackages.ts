import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import "../lib/admin";

/**
 * Real, enforced package expiry — the piece that never existed before this
 * change. ownerPackageExpiryMillis was previously written on every OWNER_PACKAGE
 * grant but never read/checked anywhere, so no package (PAYG or tiered) ever
 * actually lapsed. Runs hourly; queries user_profiles for any doc whose
 * ownerPackageExpiryMillis has passed (Firestore's `<=` filter naturally
 * excludes docs where the field is null, so only a genuinely-active-then-lapsed
 * package ever matches) and resets it to the "no active package" baseline.
 *
 * Deliberately narrow: keeps PRO_HOST role (a host with no active package is
 * already a valid, pre-existing state — nothing has ever demoted role on
 * expiry) and never touches workspace_listings (no unpublishing, no
 * isActiveSubscription flip — nothing else in this app force-unpublishes a
 * listing on a downgrade, and expiry must not be the first exception).
 * firestore.rules' withinListingLimit() also checks the live expiry timestamp
 * itself, so a package that's lapsed but not yet swept by this function is
 * still correctly treated as "no package" for gating purposes in the meantime.
 */
export const expirePackages = onSchedule("0 * * * *", async () => {
  const db = getFirestore();
  const now = Date.now();

  // 1. Proactive warning for packages expiring in <= 3 days (notified once)
  const warningWindowEnd = now + 3 * 24 * 60 * 60 * 1000;
  const warningSnap = await db
    .collection("user_profiles")
    .where("ownerPackageExpiryMillis", ">", now)
    .where("ownerPackageExpiryMillis", "<=", warningWindowEnd)
    .limit(500)
    .get();

  for (const doc of warningSnap.docs) {
    const data = doc.data();
    if (data.expiryWarningSent === true) continue;
    const userId = doc.id;
    const expiry = data.ownerPackageExpiryMillis as number;
    const daysLeft = Math.max(1, Math.ceil((expiry - now) / (24 * 60 * 60 * 1000)));
    await sendPushToUser(
      userId,
      "Package Expiring Soon",
      `Your ProHost subscription package is expiring in ${daysLeft} day(s). Renew now to maintain your active workspace listings.`,
      {
        category: "PAYMENT_REMINDER",
        targetTab: "owner_subscriptions",
      }
    );
    await doc.ref.set({ expiryWarningSent: true }, { merge: true });
  }

  const expiredSnap = await db
    .collection("user_profiles")
    .where("ownerPackageExpiryMillis", "<=", now)
    .limit(500)
    .get();

  if (expiredSnap.empty) return;

  const bulkWriter = db.bulkWriter();
  let count = 0;
  expiredSnap.docs.forEach((doc) => {
    bulkWriter.set(doc.ref, { ownerPackageId: null, ownerPackageExpiryMillis: null }, { merge: true });
    count++;
  });
  await bulkWriter.close();

  await recordAuditLog({
    actionType: "PACKAGES_EXPIRED_BATCH",
    details: `Scheduled sweep cleared ${count} expired owner package(s) back to the no-package baseline.`,
    actorEmail: "system@prohost.app",
    severity: "INFO",
  });
});
