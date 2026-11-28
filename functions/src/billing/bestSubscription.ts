/**
 * Which of an account's store subscriptions sets its entitlement (pure, unit-tested).
 *
 * Switching between ProHost Premium Unlimited and "1 listing" leaves two subscriptions active for a while (Play
 * DEFERRED / Apple downgrade at renewal: the old plan runs to the end of its paid period). Applying whichever one was
 * synced last made the listing limit flip with every re-sync and demoted the account when the old plan ended, although
 * the new one was paid. The rule: among subscriptions that grant access now and were not revoked by an admin, Unlimited
 * outranks 1 listing, then the later expiry wins.
 */
import { BillingStatus, grantsAccess, tierOf } from "./playCatalog";

export interface StoredSubscription {
  id: string;
  platform?: string | null;
  productId?: string | null;
  basePlanId?: string | null;
  status?: string | null;
  expiryDate?: number | null;
  adminRevoked?: boolean | null;
  purchaseToken?: string | null;
  orderId?: string | null;
  latestTransactionId?: string | null;
  originalTransactionId?: string | null;
}

export function planIdOf(s: StoredSubscription): string {
  return String(s.basePlanId || s.productId || "");
}

export function bestActiveSubscription(subs: StoredSubscription[], now = Date.now()): StoredSubscription | null {
  const live = subs.filter((s) =>
    s.adminRevoked !== true &&
    !!planIdOf(s) &&
    grantsAccess((s.status ?? "") as BillingStatus, Number(s.expiryDate ?? 0), now)
  );
  if (!live.length) return null;
  const rank = (s: StoredSubscription) => (tierOf(planIdOf(s)) === "UNLIMITED" ? 1 : 0);
  return live.slice().sort((a, b) =>
    rank(b) - rank(a) || Number(b.expiryDate ?? 0) - Number(a.expiryDate ?? 0) || a.id.localeCompare(b.id)
  )[0] ?? null;
}
