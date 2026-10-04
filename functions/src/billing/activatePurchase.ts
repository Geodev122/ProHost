/**
 * One verify → grant → acknowledge path for a Play subscription, shared by the
 * verifyAndRestorePurchase callable and the retryPendingPlayActivations job.
 *
 * A purchase whose activation fails for a reason that can clear (Play API outage,
 * missing Play Console permission, failed acknowledgement) is parked in
 * play_billing_pending/{sha256(token)} and retried every 15 minutes, so a paid
 * subscription activates — and is acknowledged before Play's 3-day auto-refund —
 * even if the buyer never opens the app again. Server-only collection (rules deny).
 */
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { acknowledgeIfNeeded, queryPlaySubscription } from "./billingHelpers";
import { claimPurchaseToken, linkKey } from "./purchaseLinks";
import { PlayApiError, PlaySubscription, classifyPlayError } from "./playSubscription";
import { syncSubscription } from "./subscriptionService";
import "../lib/admin";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";

export const PENDING_COLLECTION = "play_billing_pending";

export type ActivationOutcome =
  | { status: "granted"; planId: string; productId: string; basePlanId: string | null; expiryMillis: number; acknowledged: boolean; purchase: PlaySubscription }
  | { status: "pending_payment" }
  | { status: "inactive"; state: string }
  | { status: "expired" }
  | { status: "owned_by_other"; reason: "account" | "link" }
  | { status: "play_error"; error: PlayApiError };

export async function activatePlayPurchase(
  uid: string,
  purchaseToken: string,
  productIdHint: string,
  logTag: string
): Promise<ActivationOutcome> {
  let purchase: PlaySubscription;
  try {
    purchase = await queryPlaySubscription(purchaseToken, productIdHint);
  } catch (e) {
    const error = classifyPlayError(e);
    logger.error(`${logTag}: Play lookup failed uid=${uid} product=${productIdHint} [${error.kind}] ${error.message}`);
    return { status: "play_error", error };
  }
  const productId = purchase.productId || productIdHint;

  // Ownership: a purchase made in the app's sheet carries the buyer's uid as
  // obfuscatedExternalAccountId. One started in the Play Store (promo-code redemption,
  // resubscribe) carries none — the first account whose device holds the token claims it,
  // and a token another account already claimed is never re-assigned.
  if (purchase.obfuscatedAccountId) {
    if (purchase.obfuscatedAccountId !== uid) {
      logger.warn(`${logTag}: uid mismatch uid=${uid} obfuscated=${purchase.obfuscatedAccountId} product=${productId}`);
      return { status: "owned_by_other", reason: "account" };
    }
  } else {
    const claim = await claimPurchaseToken(uid, purchaseToken, productId, purchase.linkedPurchaseToken);
    if (claim === "owned_by_other") {
      logger.warn(`${logTag}: token already linked to another account uid=${uid} product=${productId}`);
      return { status: "owned_by_other", reason: "link" };
    }
    logger.info(`${logTag}: linked unattributed purchase (${claim}) uid=${uid} product=${productId}`);
  }

  // Never grant or acknowledge before the money has settled.
  if (purchase.isPending) {
    await syncSubscription(uid, purchaseToken, purchase, { source: logTag });
    return { status: "pending_payment" };
  }

  // Play's order: verify (above) → grant (SubscriptionService → EntitlementManager) → acknowledge.
  const result = await syncSubscription(uid, purchaseToken, purchase, { source: logTag });
  if (!result.hasAccess) {
    if (result.status === "ON_HOLD" || result.status === "PAUSED") return { status: "inactive", state: result.status };
    return { status: "expired" };
  }
  const acknowledged = await acknowledgeIfNeeded(productId, purchaseToken, purchase.acknowledged, logTag);
  return {
    status: "granted",
    planId: purchase.basePlanId || productId,
    productId,
    basePlanId: purchase.basePlanId,
    expiryMillis: purchase.expiryMillis,
    acknowledged,
    purchase,
  };
}

/** Parks a purchase for the retry job (idempotent per token). */
export async function parkPendingActivation(
  uid: string,
  purchaseToken: string,
  productId: string,
  reason: string,
  source: string
): Promise<void> {
  try {
    const ref = getFirestore().collection(PENDING_COLLECTION).doc(linkKey(purchaseToken));
    const now = Date.now();
    const existing = await ref.get();
    if (existing.exists && existing.data()?.resolved === false) {
      await ref.update({ lastError: reason.slice(0, 500), updatedAt: now });
      return;
    }
    await ref.set({
      uid,
      purchaseToken,
      productId,
      source,
      attempts: 0,
      lastError: reason.slice(0, 500),
      createdAt: now,
      updatedAt: now,
      resolved: false,
      alerted: false,
    });
    logger.warn(`${source}: parked purchase for retry uid=${uid} product=${productId}: ${reason}`);
    // Money may have been taken without access: admins should know right away.
    await notifyAdminsOfSubscriptionChange(uid, "ACTIVATION_AT_RISK", {
      note: `parked for automatic retry — ${reason.slice(0, 80)}`,
    });
  } catch (e) {
    logger.error(`${source}: could not park purchase uid=${uid} product=${productId}`, e);
  }
}

/** Marks a parked purchase done (no-op when none is parked). */
export async function resolvePendingActivation(purchaseToken: string, outcome: string): Promise<void> {
  try {
    const ref = getFirestore().collection(PENDING_COLLECTION).doc(linkKey(purchaseToken));
    const snap = await ref.get();
    if (snap.exists && snap.data()?.resolved === false) {
      await ref.update({ resolved: true, outcome, resolvedAt: Date.now() });
    }
  } catch (e) {
    logger.warn("resolvePendingActivation failed", e);
  }
}
