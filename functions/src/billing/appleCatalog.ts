/**
 * App Store (StoreKit 2) side of ProHost Premium: product ids, the lifecycle status of an Apple subscription in the same
 * BillingStatus terms as Google Play, and the per-account `appAccountToken` the app attaches to every purchase.
 * Pure (unit-tested in appleCatalog.test.ts); the Firestore and Apple-verification parts live in appleSubscription.ts.
 */
import { createHash } from "crypto";
import { BillingStatus } from "./playCatalog";

/**
 * The auto-renewable subscriptions of the "ProHost Premium" group in App Store Connect: Unlimited (ranked first) and the
 * single-listing tier (ranked below it, so moving between them is an Apple upgrade / downgrade).
 */
export const APPLE_PRODUCT_IDS = [
  "prohost.premium.monthly", "prohost.premium.yearly", "prohost.single.monthly", "prohost.single.yearly",
] as const;
export const APPLE_BUNDLE_ID = "app.geonajjar.prohost";
/** App Store Connect app id (needed by Apple's verifier in production). */
export const APPLE_APP_ID = 6831983164;

export function isAppleProduct(productId: string | null | undefined): boolean {
  return !!productId && (APPLE_PRODUCT_IDS as readonly string[]).includes(productId);
}

/**
 * The UUID the app passes as StoreKit `appAccountToken` for [uid]: SHA-256("prohost-account:" + uid), first 16 bytes,
 * version 4 / RFC 4122 variant bits. The app computes the same value (shared vectors), so a purchase proves its account.
 */
export function appAccountTokenFor(uid: string): string {
  const b = Buffer.from(createHash("sha256").update(`prohost-account:${uid}`).digest().subarray(0, 16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = b.toString("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** The fields of a verified Apple transaction + renewal info that decide access. */
export interface AppleSubscriptionFacts {
  productId: string;
  originalTransactionId: string;
  transactionId: string;
  expiresMillis: number;
  revocationMillis: number | null;
  /** autoRenewStatus === 1 (renewal info); null when Apple sent no renewal info. */
  autoRenews: boolean | null;
  inBillingRetry: boolean;
  gracePeriodExpiresMillis: number | null;
  appAccountToken: string | null;
  environment: string | null;
}

/**
 * Apple's lifecycle in Play's terms: refunded/revoked → REVOKED; paid period running → ACTIVE (CANCELED when auto-renew
 * is off, still access until expiry); billing grace → GRACE_PERIOD; billing retry without grace → ON_HOLD; else EXPIRED.
 */
export function appleStatusFor(f: AppleSubscriptionFacts, now = Date.now()): BillingStatus {
  if (f.revocationMillis !== null) return "REVOKED";
  if (f.expiresMillis > now) return f.autoRenews === false ? "CANCELED" : "ACTIVE";
  if (f.gracePeriodExpiresMillis !== null && f.gracePeriodExpiresMillis > now) return "GRACE_PERIOD";
  if (f.inBillingRetry) return "ON_HOLD";
  return "EXPIRED";
}

/** Until when access lasts for [f]: the paid period, or the billing grace period when Apple grants one. */
export function appleAccessUntil(f: AppleSubscriptionFacts): number {
  return Math.max(f.expiresMillis, f.gracePeriodExpiresMillis ?? 0);
}

/** subscriptions/{id} for an Apple subscription (Play uses sha256 of the purchase token; Apple's id is not secret). */
export function appleSubscriptionDocId(originalTransactionId: string): string {
  return `apple_${originalTransactionId}`;
}
