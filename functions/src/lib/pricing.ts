import { getFirestore } from "firebase-admin/firestore";

/**
 * Mirrors the defaults in AdminPricingState (app/src/main/java/com/example/data/model/DataModels.kt).
 * Read from the system_metadata/pricing Firestore doc, which the client persists on every
 * admin pricing change (see FirestoreService.savePricingState) — these hardcoded values are only
 * the fallback for a project where an admin has never touched pricing yet. Package
 * price/limit/validity live in package_plans/main now (see packagePlans.ts), not here.
 */
const DEFAULTS = {
  monthlySubscriptionFeeUsd: 1.8,
};

export async function getPricingState(): Promise<typeof DEFAULTS> {
  const snap = await getFirestore().doc("system_metadata/pricing").get();
  if (!snap.exists) return DEFAULTS;
  const data = snap.data() ?? {};
  return { ...DEFAULTS, ...data };
}
