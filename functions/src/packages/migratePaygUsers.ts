import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * ADMIN-ONLY, RE-RUNNABLE. Force-migrates every user_profiles doc still on the
 * legacy PAYG state ("ownerPackageTier" == "PAY_AS_YOU_GO") to the new
 * "no active package" baseline: ownerPackageId=null, ownerPackageExpiryMillis=null,
 * paygCategoryCredits/paygListingsBoughtCount/ownerPackageTier deleted.
 *
 * Idempotent by design rather than a one-shot flag (unlike bootstrapSuperAdmin,
 * which self-closes because no Admin exists yet at the moment it must run —
 * that constraint doesn't apply here, an Admin already exists): a user already
 * migrated has no "ownerPackageTier" field left to match the query, so re-running
 * this after an interruption (a function timeout, a partial batch failure) is
 * always safe and simply migrates whoever is left.
 *
 * Deliberately does NOT touch workspace_listings — a PAYG host's already-
 * published ACTIVE listings stay exactly as they are; they simply can't
 * publish anything NEW until they buy an admin-defined package. Nothing else
 * in this app force-unpublishes a listing on a downgrade, and this migration
 * must not be the first exception to that rule.
 *
 * Meant to be triggered once, manually, via a temporary admin-only control
 * (removed again once confirmed run) — not wired into any regular user flow.
 */
export const migratePaygUsers = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admin only.");
  }

  const db = getFirestore();
  const legacySnap = await db
    .collection("user_profiles")
    .where("ownerPackageTier", "==", "PAY_AS_YOU_GO")
    .get();

  let migrated = 0;
  if (!legacySnap.empty) {
    const bulkWriter = db.bulkWriter();
    legacySnap.docs.forEach((doc) => {
      bulkWriter.set(
        doc.ref,
        {
          ownerPackageId: null,
          ownerPackageExpiryMillis: null,
          ownerPackageTier: FieldValue.delete(),
          paygCategoryCredits: FieldValue.delete(),
          paygListingsBoughtCount: FieldValue.delete(),
        },
        { merge: true }
      );
      migrated++;
    });
    await bulkWriter.close();
  }

  await recordAuditLog({
    actionType: "PAYG_USERS_MIGRATED",
    details: `One-time PAYG migration: ${migrated} user(s) reset to no-active-package baseline; paygCategoryCredits/paygListingsBoughtCount deleted; workspace_listings left untouched.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { migrated };
});
