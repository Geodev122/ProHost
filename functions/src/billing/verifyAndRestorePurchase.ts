import { HttpsError } from "firebase-functions/v2/https";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";
import { onCall } from "../lib/callable";
import { logger } from "firebase-functions/v2";
import "../lib/admin";
import { sendGa4Event } from "../lib/ga4";
import { activatePlayPurchase, parkPendingActivation, resolvePendingActivation } from "./activatePurchase";
import { logPurchaseOnce } from "./subscriptionService";
import { planInterval } from "./playCatalog";

/** Shown whenever a paid purchase could not be confirmed yet: it is parked and retried. */
const PAYMENT_SAFE_MESSAGE =
  "Your payment is safe. We couldn't confirm it with Google Play just now — your Pro Host plan " +
  "will activate automatically within a few minutes. You don't need to pay again.";

/**
 * Called by the Android client for every purchase from checkout, and for active Google Play
 * subscriptions found on the device (Restore Purchases, app resume). Verifies the token
 * against the Play Developer API (subscriptionsv2), checks it belongs to the caller, grants,
 * then acknowledges (see activatePurchase.ts → acknowledgeIfNeeded).
 *
 * When Play can't be reached or denies access, the purchase is parked for
 * retryPendingPlayActivations instead of being dropped, and the caller is told the payment
 * is safe — never a generic "can't reach the server".
 */
export const verifyAndRestorePurchase = onCall<{
  purchaseToken: string;
  productId: string;
  /** Informational only: the base plan is always read from Google's response. */
  basePlanId?: string;
  /** True for the purchase the app's checkout just returned (vs. restore / app resume). */
  fromCheckout?: boolean;
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
  const outcome = await activatePlayPurchase(uid, cleanToken, cleanProductId, "verifyAndRestorePurchase");

  switch (outcome.status) {
    case "granted": {
      if (outcome.acknowledged) {
        await resolvePendingActivation(cleanToken, "granted");
      } else {
        // Granted, but Play still needs the acknowledgement or it refunds in 3 days.
        await parkPendingActivation(uid, cleanToken, outcome.productId, "acknowledge failed", "verifyAndRestorePurchase");
      }
      logger.info(
        `verifyAndRestorePurchase: restored uid=${uid} product=${outcome.productId} ` +
          `expiry=${new Date(outcome.expiryMillis).toISOString()} acknowledged=${outcome.acknowledged}`
      );
      if (request.data?.fromCheckout === true) {
        await logPurchaseOnce(uid, cleanToken, outcome.purchase);
      } else {
        await sendGa4Event(uid, "subscription_restored", {
          plan: planInterval(outcome.basePlanId) ?? undefined,
          product_id: outcome.productId,
        });
      }
      return { success: true, expiryMillis: outcome.expiryMillis, planId: outcome.planId, basePlanId: outcome.basePlanId };
    }
    case "play_error":
      if (outcome.error.kind === "invalid") {
        throw new HttpsError(
          "invalid-argument",
          "Google Play doesn't recognise this purchase. If you were charged, contact admin@pro-host.tech with your Google Play order number."
        );
      }
      await parkPendingActivation(
        uid, cleanToken, cleanProductId, `${outcome.error.kind}: ${outcome.error.message}`, "verifyAndRestorePurchase"
      );
      throw new HttpsError(outcome.error.kind === "config" ? "failed-precondition" : "unavailable", PAYMENT_SAFE_MESSAGE);
    case "pending_payment":
      throw new HttpsError(
        "failed-precondition",
        "Your payment is still being processed by Google Play. Your plan activates as soon as it completes."
      );
    case "inactive":
      throw new HttpsError(
        "failed-precondition",
        "This subscription is on hold or paused in Google Play. Fix the payment method or resume it in Google Play › Payments & subscriptions."
      );
    case "expired":
      throw new HttpsError("failed-precondition", "This subscription has already expired.");
    case "unsupported_product":
      // Retired plans (growth, enterprise) are not offered any more: nothing is parked or
      // pushed to admins. Current apps never send them; this only answers old versions.
      throw new HttpsError(
        "failed-precondition",
        "This Google Play purchase is for an older ProHost plan that is no longer offered. " +
          "Subscribe to ProHost Premium instead, or cancel the old plan in Google Play › Payments & subscriptions."
      );
    case "owned_by_other":
      // Paid but tagged for (or claimed by) another ProHost account: Play refunds it in 3 days
      // unless someone acts, so park it where admins see it (Admin › Packages) and alert them.
      if (await parkPendingActivation(
        uid, cleanToken, cleanProductId,
        outcome.reason === "account"
          ? "owned_by_other: bought while another ProHost account was signed in on this device"
          : "owned_by_other: already linked to another ProHost account",
        "verifyAndRestorePurchase",
        { needsAdmin: true }
      ) === "new") {
        await notifyAdminsOfSubscriptionChange(uid, "OWNERSHIP_MISMATCH", {
          note: "the person signed in now says they paid — open Admin › Packages › Payments needing attention",
        });
      }
      throw new HttpsError(
        "permission-denied",
        outcome.reason === "account"
          ? "This Google Play purchase was made while a different ProHost account was signed in on this device. " +
            "We've alerted our team to move it to this account — no need to pay again."
          : "This Google Play purchase is linked to a different ProHost account. " +
            "We've alerted our team to review it — no need to pay again."
      );
  }
});
