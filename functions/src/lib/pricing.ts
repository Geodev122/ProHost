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

const LEGACY_SCHEMA_ID_TO_TYPE: Record<string, PaygSpaceType> = {
  "ST-01": "PRIVATE_OFFICE",
  "ST-02": "CENTER",
  "ST-03": "POLYCLINIC",
  "ST-04": "COWORKING_SPACE",
};

/**
 * Real source of truth for PAYG pricing once the admin-defined Space Category system
 * is in use — looks up categoryId (a SchemaItem.id, category "SPACE_TYPE") in
 * schema_architecture/main and reads its admin-set priceUsd. Falls back to the
 * original closed 4-value switch above for a category with no priceUsd set yet, or
 * for a legacy client still sending a bare SpaceType name as targetId. Throws for
 * anything else — never silently charges $0 for an unrecognized category.
 */
export async function getPaygFeeForCategory(pricing: typeof DEFAULTS, categoryId: string): Promise<number> {
  const schemaSnap = await getFirestore().doc("schema_architecture/main").get();
  const spaceTypes = (schemaSnap.data()?.spaceTypes as Array<{ id?: string; priceUsd?: number }> | undefined) ?? [];
  const match = spaceTypes.find((item) => item.id === categoryId);
  if (typeof match?.priceUsd === "number") {
    return match.priceUsd;
  }

  const legacyType = (["PRIVATE_OFFICE", "CENTER", "POLYCLINIC", "COWORKING_SPACE"].includes(categoryId)
    ? (categoryId as PaygSpaceType)
    : LEGACY_SCHEMA_ID_TO_TYPE[categoryId]);
  if (legacyType) {
    return getPaygFeeForType(pricing, legacyType);
  }

  throw new Error(`No PAYG price configured for category "${categoryId}".`);
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
