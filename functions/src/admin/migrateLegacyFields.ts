import { HttpsError } from "firebase-functions/v2/https";
import { FieldPath, FieldValue, getFirestore } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { pricingFromLegacyFormula } from "../listings/legacyPricing";
import "../lib/admin";

/**
 * One-time (idempotent) admin migration off the retired fields:
 *  - workspace_listings.rentalFormulas → a listing that has no structured `pricing` gets
 *    the pricing the app already read it as (legacyPricing.ts); the legacy list is deleted;
 *  - workspace_listings.subscriptionExpiryMillis and user_profiles.subscriptionExpiryMillis
 *    (never read; Play entitlements live on ownerPackageExpiryMillis) are deleted.
 * update() only, skipping deleted docs, so nothing is ever recreated.
 */
export const migrateLegacyListingFields = onCall({ timeoutSeconds: 540 }, async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");

  const db = getFirestore();
  let listingsUpdated = 0;
  let pricingWritten = 0;
  let profilesUpdated = 0;

  for (const collection of ["workspace_listings", "user_profiles"] as const) {
    let last: string | null = null;
    for (;;) {
      let q = db.collection(collection).orderBy(FieldPath.documentId()).limit(400);
      if (last) q = q.startAfter(last);
      const snap = await q.get();
      for (const doc of snap.docs) {
        const data = doc.data();
        const update: Record<string, unknown> = {};
        if (collection === "workspace_listings") {
          if (!data.ownerId) continue; // ownerless ghost docs are ignored everywhere
          if ("rentalFormulas" in data) {
            const formulas = Array.isArray(data.rentalFormulas) ? data.rentalFormulas : [];
            if (data.pricing === undefined && formulas.length > 0) {
              update.pricing = pricingFromLegacyFormula(formulas[0]);
              pricingWritten++;
            }
            update.rentalFormulas = FieldValue.delete();
          }
        }
        if ("subscriptionExpiryMillis" in data) update.subscriptionExpiryMillis = FieldValue.delete();
        if (Object.keys(update).length === 0) continue;
        try {
          await doc.ref.update(update);
          if (collection === "workspace_listings") listingsUpdated++;
          else profilesUpdated++;
        } catch {
          // Deleted meanwhile — never recreate it.
        }
      }
      if (snap.size < 400) break;
      last = snap.docs[snap.docs.length - 1].id;
    }
  }

  await recordAuditLog({
    actionType: "LEGACY_FIELDS_MIGRATED",
    details: `Listings updated ${listingsUpdated} (pricing written ${pricingWritten}), profiles updated ${profilesUpdated}`,
    actorEmail: auth.token.email ?? "admin",
  });
  return { listingsUpdated, pricingWritten, profilesUpdated };
});
