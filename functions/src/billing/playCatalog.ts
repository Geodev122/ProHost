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
/** "pro-month1" replaced the original monthly plan (Oct 2026); the older ids stay readable for old purchases. */
const MONTHLY_IDS = new Set([BASE_PLAN_MONTHLY, "pro-monthly", "pro-month1"]);
const YEARLY_IDS = new Set([BASE_PLAN_YEARLY]);

/**
 * ProHost Premium · 1 listing (owner, 22 Nov 2026): the same Pro Host role, limited to one published listing
 * ($4.49 / month, $44.99 / year, prices set in Play Console / App Store Connect only). Its base plans name their
 * period like the unlimited ones, so kind detection is unchanged.
 */
export const PLAY_SINGLE_PRODUCT_ID = "package_single_mrr";
export const SINGLE_BASE_PLAN_MONTHLY = "single-monthly";
export const SINGLE_BASE_PLAN_YEARLY = "single-yearly";
/** App Store products of the single-listing tier (same subscription group as Unlimited, ranked below it). */
export const APPLE_SINGLE_PRODUCT_IDS = ["prohost.single.monthly", "prohost.single.yearly"] as const;
/** Every product ProHost sells on Google Play. */
export const PLAY_PRODUCT_IDS = [PLAY_PRODUCT_ID, PLAY_SINGLE_PRODUCT_ID] as const;

/**
 * [PLAY_PRODUCT_IDS] only. Plans sold by older app versions (package_growth_mrr,
 * package_enterprise_mrr) are retired: every path ignores them before any ownership check,
 * record, grant, acknowledgement, parking or admin alert.
 */
export function isSupportedProduct(productId: string | null | undefined): boolean {
  return productId === PLAY_PRODUCT_ID || productId === PLAY_SINGLE_PRODUCT_ID;
}

export type PlanTier = "UNLIMITED" | "SINGLE";

/** The tier of a product id, base plan id or App Store product id (stored ownerPackageId). Grants are unlimited. */
export function tierOf(id: string | null | undefined): PlanTier {
  const v = id?.trim().toLowerCase() ?? "";
  if (v === PLAY_SINGLE_PRODUCT_ID || v.startsWith("single-") || (APPLE_SINGLE_PRODUCT_IDS as readonly string[]).includes(v)) {
    return "SINGLE";
  }
  return "UNLIMITED";
}

/** How many listings the tier may publish at once: 1 for the single-listing plan, null = unlimited. */
export function listingLimitFor(id: string | null | undefined): number | null {
  return tierOf(id) === "SINGLE" ? 1 : null;
}

/** Whether publishing one more listing would exceed [limit] when [othersPublished] are already live. */
export function exceedsListingLimit(limit: unknown, othersPublished: number): boolean {
  return typeof limit === "number" && limit >= 0 && othersPublished >= limit;
}

/** Published listings beyond [limit], the most recently updated kept: the ids to move back to Draft. */
export function listingsOverLimit(
  listings: Array<{ id: string; status?: unknown; updatedAt?: unknown }>,
  limit: number
): string[] {
  const active = listings
    .filter((l) => l.status === undefined || l.status === "ACTIVE")
    .sort((a, b) => Number(b.updatedAt ?? 0) - Number(a.updatedAt ?? 0));
  return active.slice(Math.max(0, limit)).map((l) => l.id);
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

export type EntitlementSource = "google_play" | "app_store" | "admin_forced";

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
  const tier = tierOf(planId) === "SINGLE" ? " (1 listing)" : "";
  if (interval === "monthly") return `Pro Host Monthly${tier}`;
  if (interval === "yearly") return `Pro Host Yearly${tier}`;
  return `Pro Host${tier}`;
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
