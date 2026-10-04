/**
 * SubscriptionService: records every verified Google Play subscription in
 * subscriptions/{sha256(purchaseToken)} and applies its lifecycle status to the user
 * through the EntitlementManager. The single path used by verifyAndRestorePurchase,
 * the retry job, RTDN, expirePackages and the daily billingSyncJob — so a purchase,
 * renewal, cancellation, refund or expiry always produces the same role.
 * Server-only collection (firestore.rules denies clients; it holds purchase tokens).
 */
import { getFirestore } from "firebase-admin/firestore";
import { BillingStatus, grantsAccess, planInterval, statusFor } from "./playCatalog";
import { sendGa4Event, transactionIdFor } from "../lib/ga4";
import { PlaySubscription } from "./playSubscription";
import { grantProHost, removeProHost } from "./entitlementManager";
import { linkKey } from "./purchaseLinks";
import "../lib/admin";

export const SUBSCRIPTIONS = "subscriptions";

export interface SyncResult {
  status: BillingStatus;
  subscriptionId: string;
  /** True when the user holds Pro Host because of this subscription after the sync. */
  hasAccess: boolean;
  /** True when this sync removed Pro Host. */
  removed: boolean;
}

const REMOVAL_MESSAGES: Partial<Record<BillingStatus, string>> = {
  EXPIRED: "Your ProHost Premium subscription has expired. Subscribe again to restore Pro Host access and your listings.",
  REVOKED: "Your ProHost Premium subscription was refunded or revoked by Google Play. Your Pro Host access has ended.",
  REFUNDED: "Your ProHost Premium subscription was refunded. Your Pro Host access has ended.",
  ON_HOLD: "Your ProHost Premium payment is on hold. Update your payment method in Google Play to restore Pro Host access.",
  PAUSED: "Your ProHost Premium subscription is paused. Resume it in Google Play to restore your Pro Host access and listings.",
};

export async function syncSubscription(
  uid: string,
  purchaseToken: string,
  sub: PlaySubscription,
  opts: { source: string; override?: "REVOKED" | "REFUNDED" }
): Promise<SyncResult> {
  const db = getFirestore();
  const now = Date.now();
  const status = statusFor(sub, opts.override);
  const subscriptionId = linkKey(purchaseToken);
  const ref = db.collection(SUBSCRIPTIONS).doc(subscriptionId);
  const existing = await ref.get();

  await ref.set(
    {
      userId: uid,
      platform: "android",
      productId: sub.productId,
      basePlanId: sub.basePlanId,
      purchaseToken,
      orderId: sub.orderId,
      status,
      expiryDate: sub.expiryMillis || null,
      autoRenewing: sub.autoRenewing,
      linkedPurchaseToken: sub.linkedPurchaseToken,
      lastSyncSource: opts.source,
      updatedAt: now,
      ...(existing.exists ? {} : { createdAt: now, startDate: now }),
    },
    { merge: true }
  );

  if (status === "PENDING") return { status, subscriptionId, hasAccess: false, removed: false };

  if (grantsAccess(status, sub.expiryMillis, now)) {
    await grantProHost(uid, {
      source: "google_play",
      planId: sub.basePlanId || sub.productId,
      expiryMillis: sub.expiryMillis,
      orderId: sub.orderId ?? sub.productId,
      status,
      subscriptionId,
      purchaseToken,
    });
    return { status, subscriptionId, hasAccess: true, removed: false };
  }

  const removed = await removeProHost(uid, {
    status,
    reason: `Google Play reports ${status} (${opts.source})`,
    pushMessage: REMOVAL_MESSAGES[status] ?? REMOVAL_MESSAGES.EXPIRED!,
    subscriptionId,
  });
  return { status, subscriptionId, hasAccess: false, removed };
}

/**
 * premium_purchase_success exactly once per subscription, whichever server path sees the
 * new purchase first (the checkout callable or RTDN SUBSCRIPTION_PURCHASED).
 */
export async function logPurchaseOnce(uid: string, purchaseToken: string, sub: PlaySubscription): Promise<void> {
  const ref = getFirestore().collection(SUBSCRIPTIONS).doc(linkKey(purchaseToken));
  const first = await getFirestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (snap.data()?.purchaseLoggedAt) return false;
    tx.set(ref, { purchaseLoggedAt: Date.now() }, { merge: true });
    return true;
  });
  if (!first) return;
  const micros = sub.priceMicros ?? 0;
  await sendGa4Event(uid, "premium_purchase_success", {
    plan: planInterval(sub.basePlanId) ?? sub.basePlanId ?? undefined,
    product_id: sub.productId,
    base_plan_id: sub.basePlanId ?? undefined,
    currency: sub.currency ?? undefined,
    value: micros > 0 ? micros / 1_000_000 : undefined,
    transaction_id: sub.orderId ? transactionIdFor(sub.orderId) : undefined,
  });
}
