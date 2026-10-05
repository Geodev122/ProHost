import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendPushToUser } from "../lib/push";
import { sendEmail } from "../lib/email";
import { subscriptionActivatedTemplate, subscriptionRenewedTemplate, UserContext } from "../lib/emailTemplates";
import { recordAuditLog } from "../lib/auditLog";
import { PACKAGE_NAME, queryPlaySubscription, acknowledgeIfNeeded } from "./billingHelpers";
import { PLAY_PRODUCT_ID, grantsAccess, isSupportedProduct, planInterval, planLabel, statusFor } from "./playCatalog";
import { logPurchaseOnce, syncSubscription } from "./subscriptionService";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";
import { sendGa4Event } from "../lib/ga4";
import { adminOverrideUid, resolvePurchaseUid } from "./purchaseLinks";
import { classifyPlayError, PlaySubscription } from "./playSubscription";
import { parkPendingActivation, resolvePendingActivation } from "./activatePurchase";
import "../lib/admin";

// Google Play subscription notification types (DeveloperNotification spec)
const SUBSCRIPTION_RECOVERED = 1;     // payment recovered after a hold
const SUBSCRIPTION_RENEWED = 2;       // auto-renewed for another term
const SUBSCRIPTION_CANCELED = 3;      // canceled; access continues until expiry
const SUBSCRIPTION_PURCHASED = 4;     // new subscription started
const SUBSCRIPTION_ON_HOLD = 5;       // payment failed; Google retrying
const SUBSCRIPTION_IN_GRACE_PERIOD = 6; // payment failed; grace period
const SUBSCRIPTION_RESTARTED = 7;     // canceled then re-subscribed
const SUBSCRIPTION_PAUSED = 10;       // user paused
const SUBSCRIPTION_REVOKED = 12;      // refunded/revoked by Google
const SUBSCRIPTION_EXPIRED = 13;      // fully expired
const SUBSCRIPTION_PENDING_PURCHASE_CANCELED = 20; // pending payment never completed

/**
 * Google Play Real-Time Developer Notification handler.
 *
 * Setup in Google Play Console: Monetize → Subscriptions → Real-time developer
 * notifications → Set a Pub/Sub topic named "play-billing-rtdn". The topic must
 * exist in the same Google Cloud project.
 *
 * One product, package_pro_mrr, with base plans pro-montly / pro-yearly (playCatalog.ts).
 * Every notification re-reads the subscription from Google (subscriptionsv2) and applies
 * it through subscriptionService.syncSubscription; this handler only adds the pushes,
 * emails, acknowledgement and analytics that belong to each notification type.
 */
export const playBillingRtdn = onMessagePublished(
  // retry: a throw makes Pub/Sub redeliver (with backoff). Only Play API failures that can
  // clear throw; a malformed or unknown-token message returns so it never loops.
  { topic: "play-billing-rtdn", retry: true },
  async (event) => {
    // 1. Decode the DeveloperNotification envelope
    let notification: Record<string, unknown>;
    try {
      const raw = event.data.message.data ?? "";
      notification = JSON.parse(Buffer.from(raw, "base64").toString("utf8"));
    } catch (e) {
      logger.error("playBillingRtdn: failed to decode Pub/Sub message", e);
      return;
    }

    // Heartbeat for Admin › Packages' billing health check. A self-test (billingRtdnSelfTest,
    // published by the server itself) only proves topic → function; lastRtdnAt is set only by
    // messages Google Play published, so it proves Play Console → topic as well.
    const heartbeat = notification.selfTest === true
      ? { lastSelfTestAt: Date.now() }
      : { lastRtdnAt: Date.now(), lastRtdnPackage: String(notification.packageName ?? "") };
    await getFirestore().doc("app_config/billing_health").set(heartbeat, { merge: true }).catch(() => undefined);

    // Test notifications (from Play Console's "Send test notification") have no
    // subscriptionNotification — just ignore them after a log line.
    const subNote = notification.subscriptionNotification as Record<string, unknown> | undefined;
    if (!subNote) {
      logger.info("playBillingRtdn: testNotification or unknown type, ignoring");
      return;
    }

    if (notification.packageName !== PACKAGE_NAME) {
      logger.warn(`playBillingRtdn: unexpected packageName "${notification.packageName}", ignoring`);
      return;
    }

    const hintProductId = (subNote.subscriptionId as string | undefined) ?? "";
    const purchaseToken = subNote.purchaseToken as string;
    const notificationType = subNote.notificationType as number;

    logger.info(`playBillingRtdn: type=${notificationType} product=${hintProductId}`);

    // 2. Verify via Play Developer API (subscriptionsv2) and extract the user UID
    let purchase: PlaySubscription;
    try {
      purchase = await queryPlaySubscription(purchaseToken, hintProductId || undefined);
    } catch (e) {
      const err = classifyPlayError(e);
      if (err.kind === "invalid") {
        logger.error(`playBillingRtdn: Play does not know this purchase product=${hintProductId}: ${err.message}`);
        return;
      }
      // config (401/403) or transient: throw so Pub/Sub redelivers. Dropping it here is what
      // left paid purchases unactivated and unacknowledged until Play refunded them.
      logger.error(`playBillingRtdn: Play API call failed [${err.kind}] product=${hintProductId}: ${err.message}`);
      throw err;
    }
    const productId = purchase.productId || hintProductId;

    // Purchases made in the app's sheet carry the uid; ones started in the Play Store
    // (promo-code redemption, resubscribe) don't, and resolve via play_purchase_links.
    const uid = await resolvePurchaseUid(
      purchase.obfuscatedAccountId, purchaseToken, purchase.linkedPurchaseToken
    );
    if (!uid && notificationType === SUBSCRIPTION_PENDING_PURCHASE_CANCELED) {
      logger.info(`playBillingRtdn: unlinked pending purchase canceled product=${productId}`);
      return;
    }
    if (!uid) {
      // Acknowledge now: Play cancels and refunds anything unacknowledged for 3 days,
      // and nobody may open the app that soon. The owner claims it on their next app
      // open (verifyAndRestorePurchase); retrying this message would never succeed.
      if (
        (notificationType === SUBSCRIPTION_PURCHASED ||
          notificationType === SUBSCRIPTION_RESTARTED ||
          notificationType === SUBSCRIPTION_RECOVERED) &&
        !purchase.isPending &&
        // A retired plan is never acknowledged automatically (Play refunds it unless an admin acts).
        isSupportedProduct(productId)
      ) {
        const acked = await acknowledgeIfNeeded(productId, purchaseToken, purchase.acknowledged, "playBillingRtdn");
        // Unacknowledged = refunded in 3 days, and no account to park it under: redeliver.
        if (!acked) throw new Error(`playBillingRtdn: acknowledge failed for unlinked purchase product=${productId}`);
      }
      logger.warn(
        `playBillingRtdn: purchase not linked to an account yet product=${productId} type=${notificationType} — waiting for in-app restore`
      );
      if (notificationType === SUBSCRIPTION_PURCHASED) {
        await notifyAdminsOfSubscriptionChange(null, "UNLINKED_PURCHASE", {
          planId: purchase.basePlanId || productId,
          note: "started in the Play Store (e.g. promo code) — activates when its owner opens the app",
        });
      }
      await getFirestore().collection("play_billing_unresolved").add({
        purchaseToken,
        productId,
        orderId: purchase.orderId,
        notificationType,
        packageName: notification.packageName,
        timestamp: Date.now(),
        resolved: false,
      });
      return;
    }

    // Only package_pro_mrr grants on its own; a retired plan counts only once an admin has
    // activated it (adminOverride on this token or the one it replaced).
    const adminApproved = !!(await adminOverrideUid(purchaseToken)) ||
      (!!purchase.linkedPurchaseToken && !!(await adminOverrideUid(purchase.linkedPurchaseToken)));
    if (!isSupportedProduct(productId) && !adminApproved) {
      logger.warn(`playBillingRtdn: retired product ${productId} (not ${PLAY_PRODUCT_ID}) uid=${uid} type=${notificationType} — not granted`);
      if (!purchase.isPending && grantsAccess(statusFor(purchase), purchase.expiryMillis)) {
        await parkPendingActivation(
          uid, purchaseToken, productId,
          `unsupported_product: ${productId} is a retired plan (only ${PLAY_PRODUCT_ID} grants Pro Host)`,
          "playBillingRtdn",
          { needsAdmin: true }
        );
      }
      return;
    }
    const orderId = purchase.orderId ?? productId;
    const planName = planLabel(purchase.basePlanId);

    // 3. Apply Google's state: SubscriptionService records it and the EntitlementManager
    //    grants or removes Pro Host. REVOKED is the one state only the notification knows.
    const result = await syncSubscription(uid, purchaseToken, purchase, {
      source: `rtdn:${notificationType}`,
      override: notificationType === SUBSCRIPTION_REVOKED ? "REVOKED" : undefined,
    });
    logger.info(`playBillingRtdn: uid=${uid} type=${notificationType} status=${result.status} access=${result.hasAccess}`);

    // 4. Side effects per notification type (the role is already settled above).
    const emailTo = async (build: (ctx: UserContext) => { subject: string; html: string; text?: string }) => {
      try {
        const userData = (await getFirestore().collection("user_profiles").doc(uid).get()).data();
        if (!userData?.email) return;
        const ctx: UserContext = {
          fullName: userData.fullName ?? "Member",
          email: userData.email,
          role: "PRO_HOST",
          activeListingCount: (userData.activeListingCount ?? 0) as number,
        };
        await sendEmail({ to: userData.email, ...build(ctx) });
      } catch (_) { /* email is best-effort */ }
    };
    switch (notificationType) {
      case SUBSCRIPTION_PURCHASED:
        if (result.hasAccess) {
          await sendPushToUser(uid, "ProHost Premium is active", "Welcome! Your Pro Host access is active — start publishing workspace listings.", {
            category: "PACKAGE_ACTIVATED",
            // owner_subscriptions is reachable even while the device still holds the
            // pre-upgrade SPECIALIST claim; manage_listings is not.
            targetTab: "owner_subscriptions",
          });
          await emailTo((ctx) => subscriptionActivatedTemplate(ctx, planName));
        }
        break;
      case SUBSCRIPTION_RENEWED:
        if (result.hasAccess) {
          await sendPushToUser(uid, "Subscription Renewed", "Your ProHost Premium subscription has renewed — your access continues uninterrupted.", {
            category: "PACKAGE_RENEWED",
            targetTab: "owner_subscriptions",
          });
          const expiryDate = new Date(purchase.expiryMillis).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
          await emailTo((ctx) => subscriptionRenewedTemplate(ctx, planName, expiryDate));
        }
        break;
      case SUBSCRIPTION_CANCELED: {
        // Access continues until expiry (CANCELED grants until then).
        const until = new Date(purchase.expiryMillis).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
        await sendPushToUser(
          uid,
          "Subscription canceled",
          `ProHost Premium won't renew. You keep Pro Host access until ${until}. Changed your mind? Resubscribe from ProHost Premium.`,
          { category: "PACKAGE_EXPIRED", targetTab: "owner_subscriptions" }
        );
        await recordAuditLog({
          actionType: "PLAY_BILLING_SUBSCRIPTION_CANCELED",
          details: `uid=${uid} canceled ${productId}/${purchase.basePlanId ?? "?"} (order ${orderId}). Access until ${new Date(purchase.expiryMillis).toISOString()}.`,
          actorEmail: "play-billing@system.prohost.app",
          severity: "INFO",
        });
        break;
      }
      case SUBSCRIPTION_RECOVERED:
      case SUBSCRIPTION_RESTARTED:
        if (result.hasAccess) {
          await sendPushToUser(uid, "Welcome back to ProHost Premium", "Your subscription is active again — your Pro Host access and listings are restored.", {
            category: "PACKAGE_ACTIVATED",
            targetTab: "owner_subscriptions",
          });
        }
        break;
      case SUBSCRIPTION_IN_GRACE_PERIOD:
        await sendPushToUser(
          uid,
          "Payment Issue — Action Needed",
          "Your ProHost Premium payment failed. Update your payment method in Google Play to keep your Pro Host access.",
          { category: "PAYMENT_REMINDER", targetTab: "owner_subscriptions" }
        );
        break;
      case SUBSCRIPTION_PENDING_PURCHASE_CANCELED:
        logger.info(`playBillingRtdn: pending purchase canceled uid=${uid} product=${productId}`);
        break;
      default:
        // RECOVERED, RESTARTED, DEFERRED, ON_HOLD, PAUSED, REVOKED, EXPIRED: the sync above
        // granted or removed access (removeProHost sends its own notice).
        break;
    }

    // 5. Acknowledge after the entitlement is granted (Play: verify → grant → acknowledge),
    //    and never while payment is still pending — the 3-day window starts at PURCHASED.
    if (
      (notificationType === SUBSCRIPTION_PURCHASED ||
        notificationType === SUBSCRIPTION_RESTARTED ||
        notificationType === SUBSCRIPTION_RECOVERED) &&
      !purchase.isPending
    ) {
      const acked = await acknowledgeIfNeeded(productId, purchaseToken, purchase.acknowledged, "playBillingRtdn");
      // The grant already happened, so don't redeliver (that repeats pushes and emails):
      // hand the acknowledgement to retryPendingPlayActivations instead.
      if (acked) await resolvePendingActivation(purchaseToken, "granted_by_rtdn");
      else await parkPendingActivation(uid, purchaseToken, productId, "acknowledge failed", "playBillingRtdn");
    }

    // 6. GA4 (consent-gated, never throws).
    if (notificationType === SUBSCRIPTION_PURCHASED && result.hasAccess) {
      await logPurchaseOnce(uid, purchaseToken, purchase);
      return;
    }
    const gaEvents: Record<number, string> = {
      [SUBSCRIPTION_RENEWED]: "subscription_renewed",
      [SUBSCRIPTION_EXPIRED]: "subscription_expired",
      [SUBSCRIPTION_RECOVERED]: "subscription_recovered",
      [SUBSCRIPTION_RESTARTED]: "subscription_restarted",
      [SUBSCRIPTION_REVOKED]: "subscription_revoked",
      [SUBSCRIPTION_ON_HOLD]: "subscription_on_hold",
      [SUBSCRIPTION_PAUSED]: "subscription_paused",
      [SUBSCRIPTION_CANCELED]: "subscription_cancelled",
      [SUBSCRIPTION_IN_GRACE_PERIOD]: "subscription_grace_period",
    };
    const gaEvent = gaEvents[notificationType];
    if (gaEvent) {
      const micros = purchase.priceMicros ?? 0;
      await sendGa4Event(uid, gaEvent, {
        plan: planInterval(purchase.basePlanId) ?? undefined,
        product_id: productId,
        currency: gaEvent === "subscription_renewed" ? purchase.currency ?? undefined : undefined,
        value: gaEvent === "subscription_renewed" && micros > 0 ? micros / 1_000_000 : undefined,
      });
    }
  }
);
