/**
 * The one Pro Host subscription sold on Google Play, and the rules that turn its state
 * into access. Google Play is the only billing authority: there is no admin catalog,
 * no stored prices and no admin-defined plans. Mirrored in the app's
 * data/billing/PlayCatalog.kt. Pure (no firebase-admin) so it is unit-tested.
 */
import type { PlaySubscription } from "./playSubscription";

export const PLAY_PRODUCT_ID = "package_pro_mrr";
/** Known base plan ids (the original monthly id is spelled "montly" in Play Console). */
export const BASE_PLAN_MONTHLY = "pro-montly";
export const BASE_PLAN_YEARLY = "pro-yearly";
const MONTHLY_IDS = new Set([BASE_PLAN_MONTHLY, "pro-monthly"]);
const YEARLY_IDS = new Set([BASE_PLAN_YEARLY]);

/**
 * Only [PLAY_PRODUCT_ID] exists. Plans sold by older app versions (package_growth_mrr,
 * package_enterprise_mrr) are retired: every path ignores them before any ownership check,
 * record, grant, acknowledgement, parking or admin alert.
 */
export function isSupportedProduct(productId: string | null | undefined): boolean {
  return productId === PLAY_PRODUCT_ID;
}

/** ownerPackageId for an admin "Force Upgrade → ProHost": permanent until revoked. */
export const ADMIN_FORCED_PLAN_ID = "admin_forced";
/** Legacy admin lifetime grant id; migrated to [ADMIN_FORCED_PLAN_ID]. */
export const LEGACY_UNLIMITED_GRANT_PLAN_ID = "admin_unlimited_grant";

/**
 * Expiry written for entitlements that never lapse. A real timestamp rather than null:
 * several clients read a null expiry as "no active subscription". Mirrored as
 * PlayCatalog.LIFETIME_EXPIRY_MILLIS in the app.
 */
export const LIFETIME_EXPIRY_MILLIS = Date.UTC(2100, 0, 1);

export type EntitlementSource = "google_play" | "admin_forced";

/** Mirrors Google Play's subscription lifecycle (subscriptions.status, user billingStatus). */
export type BillingStatus =
  | "ACTIVE"
  | "GRACE_PERIOD"
  | "ON_HOLD"
  | "PAUSED"
  | "CANCELED"
  | "EXPIRED"
  | "REVOKED"
  | "REFUNDED"
  | "PENDING";

/** Lifecycle status for a Play subscription; [override] carries what only RTDN knows. */
export function statusFor(sub: PlaySubscription, override?: "REVOKED" | "REFUNDED"): BillingStatus {
  if (override) return override;
  switch (sub.state) {
    case "ACTIVE": return "ACTIVE";
    case "PENDING": return "PENDING";
    case "IN_GRACE_PERIOD": return "GRACE_PERIOD";
    case "ON_HOLD": return "ON_HOLD";
    case "PAUSED": return "PAUSED";
    case "CANCELED": return "CANCELED";
    case "EXPIRED": return "EXPIRED";
    case "PENDING_PURCHASE_CANCELED": return "EXPIRED";
    default:
      return sub.expiryMillis > Date.now() ? "ACTIVE" : "EXPIRED";
  }
}

/**
 * Who is Pro Host: ACTIVE and GRACE_PERIOD; CANCELED until its paid period ends.
 * ON_HOLD / PAUSED (Google: no access while payment is held or paused), EXPIRED,
 * REVOKED and REFUNDED are specialist. PENDING never grants.
 */
export function grantsAccess(status: BillingStatus, expiryMillis: number, now = Date.now()): boolean {
  if (status === "ACTIVE" || status === "GRACE_PERIOD") return true;
  if (status === "CANCELED") return expiryMillis > now;
  return false;
}

/** Display name for an entitlement's ownerPackageId (a base plan id or a forced upgrade). */
export function planLabel(planId: string | null | undefined): string {
  if (planId === ADMIN_FORCED_PLAN_ID || planId === LEGACY_UNLIMITED_GRANT_PLAN_ID) return "Pro Host (granted)";
  const interval = planInterval(planId);
  if (interval === "monthly") return "Pro Host Monthly";
  if (interval === "yearly") return "Pro Host Yearly";
  return "Pro Host";
}

/**
 * "monthly" / "yearly" for a base plan id, whatever its exact spelling in Play Console
 * (known ids first, then the words in the id); null for grants and anything else.
 */
export function planInterval(basePlanId: string | null | undefined): "monthly" | "yearly" | null {
  const id = basePlanId?.trim().toLowerCase();
  if (!id || id === ADMIN_FORCED_PLAN_ID || id === LEGACY_UNLIMITED_GRANT_PLAN_ID) return null;
  if (MONTHLY_IDS.has(id)) return "monthly";
  if (YEARLY_IDS.has(id)) return "yearly";
  if (id.includes("year") || id.includes("annual")) return "yearly";
  if (id.includes("month") || id.includes("mont")) return "monthly";
  return null;
}

export type AdminBillingEvent =
  | "NEW_SUBSCRIPTION"
  | "RENEWED"
  | "PLAN_CHANGED"
  | "STATUS_CHANGED"
  | "FORCED_UPGRADE"
  | "ADMIN_REVOKED"
  | "EXPIRED"
  | "UNLINKED_PURCHASE"
  | "ACTIVATION_AT_RISK"
  | "OWNERSHIP_MISMATCH";

/**
 * What changed between the stored subscriptions record ([prev]) and Google's current state,
 * for the admin push; null when nothing did (an unchanged daily re-sync).
 */
export function adminEventFor(
  prev: { status?: unknown; basePlanId?: unknown; expiryDate?: unknown } | undefined,
  status: BillingStatus,
  sub: Pick<PlaySubscription, "basePlanId" | "expiryMillis">
): AdminBillingEvent | null {
  if (!prev) return status === "ACTIVE" || status === "GRACE_PERIOD" ? "NEW_SUBSCRIPTION" : "STATUS_CHANGED";
  if (prev.status !== status) return "STATUS_CHANGED";
  if (typeof prev.basePlanId === "string" && sub.basePlanId && prev.basePlanId !== sub.basePlanId) return "PLAN_CHANGED";
  const prevExpiry = typeof prev.expiryDate === "number" ? prev.expiryDate : 0;
  if (status === "ACTIVE" && prevExpiry > 0 && sub.expiryMillis > prevExpiry + 60_000) return "RENEWED";
  return null;
}
