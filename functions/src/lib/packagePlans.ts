import { getFirestore } from "firebase-admin/firestore";

/**
 * An admin-defined, purchasable Pro Host package — mirrors the Kotlin PackagePlan
 * (app/src/main/java/com/example/data/model/DataModels.kt). Replaces the old closed
 * OwnerPackageTier union + PAYG credit system entirely. Stored keyed by id as a map
 * inside package_plans/main (see PackagePlanCatalog's Kotlin doc comment for why a
 * map, not a list). Every plan is unlimited in listings; price and billing period
 * come from Google Play. Admins control only display order (sortOrder) and isFeatured.
 */
export interface PackagePlan {
  id: string;
  name: string;
  description?: string;
  badgeName?: string;
  priceUsd: number;
  validityDays: number;
  isEnabled: boolean;
  sortOrder?: number;
  /** Admin-grant only: never offered for purchase (see UNLIMITED_GRANT_PLAN_ID). */
  isGrantOnly?: boolean;
  /** Play Console product id when it differs from [id]; blank means "same as id". */
  googlePlayProductId?: string;
}

/**
 * Reserved catalog entry for admin "Unlimited" grants. It lives in package_plans/main
 * like any plan so every client screen that resolves packages[ownerPackageId]
 * shows it as a real plan.
 */
export const UNLIMITED_GRANT_PLAN_ID = "admin_unlimited_grant";

/**
 * Expiry written for grants that never lapse. A real timestamp rather than null:
 * several clients read a null expiry as "no active subscription". Mirrored as
 * PackagePlan.LIFETIME_EXPIRY_MILLIS in DataModels.kt.
 */
export const LIFETIME_EXPIRY_MILLIS = Date.UTC(2100, 0, 1);

export const UNLIMITED_GRANT_PLAN: PackagePlan = {
  id: UNLIMITED_GRANT_PLAN_ID,
  name: "Lifetime Pro Host",
  description: "Complimentary lifetime Pro Host access granted by an administrator",
  badgeName: "Lifetime",
  priceUsd: 0,
  validityDays: 36500,
  isEnabled: true,
  sortOrder: 999,
  isGrantOnly: true,
};

/** Creates (or re-enables) the reserved unlimited plan so a grant never points at a missing plan. */
export async function ensureUnlimitedGrantPlan(): Promise<void> {
  await getFirestore()
    .doc("package_plans/main")
    .set({ packages: { [UNLIMITED_GRANT_PLAN_ID]: UNLIMITED_GRANT_PLAN } }, { merge: true });
}

export async function getPackagePlans(): Promise<Record<string, PackagePlan>> {
  const snap = await getFirestore().doc("package_plans/main").get();
  return (snap.data()?.packages as Record<string, PackagePlan> | undefined) ?? {};
}

/**
 * Resolves a Google Play product id to the catalog plan id stored in ownerPackageId.
 * Clients look plans up by id, so a product whose id differs from its plan's must
 * be mapped back, or the purchase grants a plan no screen can find.
 */
export async function planIdForPlayProduct(productId: string): Promise<string> {
  const plans = await getPackagePlans();
  const match = Object.values(plans).find((p) => p.googlePlayProductId === productId);
  return match?.id ?? productId;
}

export async function getPackagePlan(id: string): Promise<PackagePlan | undefined> {
  const plans = await getPackagePlans();
  return plans[id];
}
