/**
 * SubscriptionService: records every verified Google Play subscription in
 * subscriptions/{sha256(purchaseToken)} and applies its lifecycle status to the user
 * through the EntitlementManager. The single path used by verifyAndRestorePurchase,
 * the retry job, RTDN, expirePackages and the daily billingSyncJob — so a purchase,
 * renewal, cancellation, refund or expiry always produces the same role.
 * Server-only collection (firestore.rules denies clients; it holds purchase tokens).
 */
import { logger } from "firebase-functions/v2";
import { getFirestore } from "firebase-admin/firestore";
import { BillingStatus, adminEventFor, grantsAccess, planInterval, statusFor } from "./playCatalog";
import { sendGa4Event, transactionIdFor } from "../lib/ga4";
import { PlaySubscription } from "./playSubscription";
import { grantProHost, removeProHost } from "./entitlementManager";
import { linkKey } from "./purchaseLinks";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";
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
  const prev = existing.data();

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

  let result: SyncResult;
  if (status === "PENDING") {
    result = { status, subscriptionId, hasAccess: false, removed: false };
  } else if (prev?.adminRevoked === true && grantsAccess(status, sub.expiryMillis, now)) {
    // An admin revoked Pro Host for this subscription: Play still bills it, but it doesn't grant.
    logger.info(`syncSubscription: ${subscriptionId} was revoked by an admin — not re-granted (${opts.source})`);
    result = { status, subscriptionId, hasAccess: false, removed: false };
  } else if (grantsAccess(status, sub.expiryMillis, now)) {
    await grantProHost(uid, {
      source: "google_play",
      planId: sub.basePlanId || sub.productId,
      expiryMillis: sub.expiryMillis,
      orderId: sub.orderId ?? sub.productId,
      status,
      subscriptionId,
      purchaseToken,
    });
    result = { status, subscriptionId, hasAccess: true, removed: false };
  } else {
    const removed = await removeProHost(uid, {
      status,
      reason: `Google Play reports ${status} (${opts.source})`,
      pushMessage: REMOVAL_MESSAGES[status] ?? REMOVAL_MESSAGES.EXPIRED!,
      subscriptionId,
    });
    result = { status, subscriptionId, hasAccess: false, removed };
  }

  // Admins hear about every real change (not re-syncs of an unchanged subscription, and not
  // the one-time migration of existing subscribers).
  if (opts.source !== "migration") {
    const event = adminEventFor(prev, status, sub);
    if (event) {
      await notifyAdminsOfSubscriptionChange(uid, event, {
        planId: sub.basePlanId || sub.productId,
        status,
        previousStatus: (prev?.status as string | undefined) ?? null,
        expiryMillis: sub.expiryMillis,
      });
    }
  }
  return result;
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
