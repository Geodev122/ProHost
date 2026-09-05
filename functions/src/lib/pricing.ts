import { getFirestore } from "firebase-admin/firestore";

/**
 * Mirrors the defaults in AdminPricingState (app/src/main/java/com/example/data/model/DataModels.kt).
 * Read from the system_metadata/pricing Firestore doc, which the client now persists on every
 * admin pricing change (see FirestoreService.savePricingState) — these hardcoded values are only
 * the fallback for a project where an admin has never touched pricing yet.
 */
const DEFAULTS = {
  monthlySubscriptionFeeUsd: 1.8,
  paygPrivateOfficeUsd: 1.5,
  paygCenterUsd: 3.5,
  paygPolyclinicUsd: 2.8,
  paygCoworkingUsd: 1.8,
  package2MonthlyFeeUsd: 3.99,
  package3MonthlyFeeUsd: 8.99,
};

export type PaygSpaceType = "PRIVATE_OFFICE" | "CENTER" | "POLYCLINIC" | "COWORKING_SPACE";
export type OwnerPackageTier = "PAY_AS_YOU_GO" | "LIMITED_3_TIER" | "UNLIMITED_TIER";

export async function getPricingState(): Promise<typeof DEFAULTS> {
  const snap = await getFirestore().doc("system_metadata/pricing").get();
  if (!snap.exists) return DEFAULTS;
  const data = snap.data() ?? {};
  return { ...DEFAULTS, ...data };
}

export function getPaygFeeForType(pricing: typeof DEFAULTS, type: PaygSpaceType): number {
  switch (type) {
    case "PRIVATE_OFFICE":
      return pricing.paygPrivateOfficeUsd;
    case "CENTER":
      return pricing.paygCenterUsd;
    case "POLYCLINIC":
      return pricing.paygPolyclinicUsd;
    case "COWORKING_SPACE":
      return pricing.paygCoworkingUsd;
  }
}

export function getPackageFee(pricing: typeof DEFAULTS, tier: OwnerPackageTier): number {
  switch (tier) {
    case "PAY_AS_YOU_GO":
      return pricing.monthlySubscriptionFeeUsd;
    case "LIMITED_3_TIER":
      return pricing.package2MonthlyFeeUsd;
    case "UNLIMITED_TIER":
      return pricing.package3MonthlyFeeUsd;
  }
}
