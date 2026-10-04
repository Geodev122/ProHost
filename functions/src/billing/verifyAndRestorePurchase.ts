import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { logger } from "firebase-functions/v2";
import {
  queryPlaySubscription,
  acknowledgeIfNeeded,
  grantSubscription,
} from "./billingHelpers";
import { planIdForPlayProduct } from "../lib/packagePlans";
import "../lib/admin";
import { sendGa4Event } from "../lib/ga4";
import { claimPurchaseToken } from "./purchaseLinks";

/**
 * Called by the Android client when it detects an active Google Play subscription
 * that has no matching Firestore entitlement — for example because the original
 * RTDN Pub/Sub delivery was dropped, or because the app was reinstalled and the
 * user taps "Restore Purchases".
 *
 * Verifies the token against the Play Developer API, checks it belongs to the
 * caller, and calls grantSubscription so the entitlement is restored exactly
 * as if the RTDN had fired normally.
 */
export const verifyAndRestorePurchase = onCall<{
  purchaseToken: string;
  productId: string;
}>(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const { purchaseToken, productId } = request.data ?? {};
  if (!purchaseToken || typeof purchaseToken !== "string" || purchaseToken.trim().length === 0) {
    throw new HttpsError("invalid-argument", "purchaseToken is required.");
  }
  if (!productId || typeof productId !== "string" || productId.trim().length === 0) {
    throw new HttpsError("invalid-argument", "productId is required.");
  }

  const cleanToken = purchaseToken.trim();
  const cleanProductId = productId.trim();

  let purchase: Awaited<ReturnType<typeof queryPlaySubscription>>;
  try {
    purchase = await queryPlaySubscription(cleanProductId, cleanToken);
  } catch (e) {
    logger.error(`verifyAndRestorePurchase: Play API failed uid=${uid} product=${cleanProductId}`, e);
    throw new HttpsError("unavailable", "Could not reach Google Play. Try again in a moment.");
  }

  // Ownership: a purchase made in the app's sheet carries the buyer's uid as
  // obfuscatedExternalAccountId. One started in the Play Store (promo-code redemption,
  // resubscribe) carries none — the first account whose device holds the token claims it,
  // and a token another account already claimed is never re-assigned.
  if (purchase.obfuscatedExternalAccountId) {
    if (purchase.obfuscatedExternalAccountId !== uid) {
      logger.warn(`verifyAndRestorePurchase: uid mismatch uid=${uid} obfuscated=${purchase.obfuscatedExternalAccountId} product=${cleanProductId}`);
      throw new HttpsError("permission-denied", "This purchase belongs to a different ProHost account.");
    }
  } else {
    const claim = await claimPurchaseToken(uid, cleanToken, cleanProductId, purchase.linkedPurchaseToken);
    if (claim === "owned_by_other") {
      logger.warn(`verifyAndRestorePurchase: token already linked to another account uid=${uid} product=${cleanProductId}`);
      throw new HttpsError("permission-denied", "This purchase is already linked to a different ProHost account.");
    }
    logger.info(`verifyAndRestorePurchase: linked unattributed purchase (${claim}) uid=${uid} product=${cleanProductId}`);
  }

  const expiryMs = parseInt(purchase.expiryTimeMillis ?? "0", 10);
  if (expiryMs <= Date.now()) {
    throw new HttpsError("failed-precondition", "This subscription has already expired.");
  }

  // paymentState: 0 = pending, 1 = received, 2 = free trial, 3 = pending deferred upgrade.
  // Only grant for states where money has settled or a free trial is active.
  const paymentState = purchase.paymentState ?? 1;
  if (paymentState === 0) {
    throw new HttpsError("failed-precondition", "Payment is still pending — please wait a moment and try again.");
  }

  // Play's order: verify (above) → grant → acknowledge.
  await grantSubscription(uid, await planIdForPlayProduct(cleanProductId), expiryMs, purchase.orderId ?? cleanProductId);
  await acknowledgeIfNeeded(cleanProductId, cleanToken, purchase.acknowledgementState, "verifyAndRestorePurchase");

  logger.info(`verifyAndRestorePurchase: restored uid=${uid} product=${cleanProductId} expiry=${new Date(expiryMs).toISOString()}`);
  await sendGa4Event(uid, "subscription_restored", { item_id: cleanProductId });
  return { success: true, expiryMillis: expiryMs };
});
