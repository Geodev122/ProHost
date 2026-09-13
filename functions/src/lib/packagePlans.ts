import { getFirestore } from "firebase-admin/firestore";

/**
 * An admin-defined, purchasable Pro Host package — mirrors the Kotlin PackagePlan
 * (app/src/main/java/com/example/data/model/DataModels.kt). Replaces the old closed
 * OwnerPackageTier union + PAYG credit system entirely. Stored keyed by id as a map
 * inside package_plans/main (see PackagePlanCatalog's Kotlin doc comment for why a
 * map, not a list — the same reasoning applies to firestore.rules' withinListingLimit()).
 */
export interface PackagePlan {
  id: string;
  name: string;
  description?: string;
  badgeName?: string;
  priceUsd: number;
  listingLimit: number | null; // null == unlimited
  validityDays: number;
  isEnabled: boolean;
  sortOrder?: number;
}

export async function getPackagePlans(): Promise<Record<string, PackagePlan>> {
  const snap = await getFirestore().doc("package_plans/main").get();
  return (snap.data()?.packages as Record<string, PackagePlan> | undefined) ?? {};
}

export async function getPackagePlan(id: string): Promise<PackagePlan | undefined> {
  const plans = await getPackagePlans();
  return plans[id];
}
