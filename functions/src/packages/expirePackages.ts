import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
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
