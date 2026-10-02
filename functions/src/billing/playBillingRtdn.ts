import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendPushToUser } from "../lib/push";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { subscriptionActivatedTemplate, subscriptionRenewedTemplate, UserContext } from "../lib/emailTemplates";
import { recordAuditLog } from "../lib/auditLog";
import {
  PACKAGE_NAME,
  queryPlaySubscription,
  acknowledgeIfNeeded,
  grantSubscription,
  revokeSubscription,
} from "./billingHelpers";
import "../lib/admin";

// Google Play subscription notification types (DeveloperNotification spec)
const SUBSCRIPTION_RECOVERED = 1;     // payment recovered after a hold
const SUBSCRIPTION_RENEWED = 2;       // auto-renewed for another term
const SUBSCRIPTION_CANCELED = 3;      // canceled; access continues until expiry
const SUBSCRIPTION_PURCHASED = 4;     // new subscription started
const SUBSCRIPTION_ON_HOLD = 5;       // payment failed; Google retrying
const SUBSCRIPTION_IN_GRACE_PERIOD = 6; // payment failed; grace period
const SUBSCRIPTION_RESTARTED = 7;     // canceled then re-subscribed
const SUBSCRIPTION_DEFERRED = 9;      // expiry deferred (promotional)
const SUBSCRIPTION_PAUSED = 10;       // user paused
const SUBSCRIPTION_REVOKED = 12;      // refunded/revoked by Google
const SUBSCRIPTION_EXPIRED = 13;      // fully expired

/**
 * Google Play Real-Time Developer Notification handler.
 *
 * Setup in Google Play Console: Monetize → Subscriptions → Real-time developer
 * notifications → Set a Pub/Sub topic named "play-billing-rtdn". The topic must
 * exist in the same Google Cloud project.
 *
 * Subscription product IDs in Google Play Console MUST match the Firestore
 * package_plans document keys exactly (the plan's "id" field) so the RTDN
 * handler can find the right plan without an extra lookup.
 */
export const playBillingRtdn = onMessagePublished(
  { topic: "play-billing-rtdn", secrets: [hostingerSmtpSecret] },
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

    const productId = subNote.subscriptionId as string;
    const purchaseToken = subNote.purchaseToken as string;
    const notificationType = subNote.notificationType as number;

    logger.info(`playBillingRtdn: type=${notificationType} product=${productId}`);

    // 2. Verify via Play Developer API and extract the user UID
    let purchase: Awaited<ReturnType<typeof queryPlaySubscription>>;
    try {
      purchase = await queryPlaySubscription(productId, purchaseToken);
    } catch (e) {
      logger.error(`playBillingRtdn: Play API call failed for product=${productId}`, e);
      return;
    }

    const uid = purchase.obfuscatedExternalAccountId;
    if (!uid) {
      const unresolvedDoc = {
        purchaseToken,
        productId,
        orderId: purchase.orderId,
        notificationType,
        packageName: notification.packageName,
        timestamp: Date.now(),
        resolved: false,
      };
      if (notificationType === SUBSCRIPTION_PURCHASED || notificationType === SUBSCRIPTION_RECOVERED) {
        // These can be retried — throw so Pub/Sub redelivers rather than permanently ACKing.
        throw new Error(
          `playBillingRtdn: no obfuscatedExternalAccountId for product=${productId} type=${notificationType} — Pub/Sub will redeliver`
        );
      }
      // For revocation/expiry/hold/pause/cancel types: a retry won't help because
      // a purchase without a UID can't be mapped to a user. Write for manual resolution.
      logger.error(
        `playBillingRtdn: no obfuscatedExternalAccountId for product=${productId} type=${notificationType} — writing to play_billing_unresolved`
      );
      await getFirestore().collection("play_billing_unresolved").add(unresolvedDoc);
      return;
    }

    const orderId = purchase.orderId ?? productId;
    const expiryMs = parseInt(purchase.expiryTimeMillis ?? "0", 10);

    if (
      notificationType === SUBSCRIPTION_PURCHASED ||
      notificationType === SUBSCRIPTION_RESTARTED ||
      notificationType === SUBSCRIPTION_RECOVERED
    ) {
      await acknowledgeIfNeeded(productId, purchaseToken, purchase.acknowledgementState, "playBillingRtdn");
    }

    // 3. Dispatch by notification type
    switch (notificationType) {
      case SUBSCRIPTION_PURCHASED:
        if (expiryMs > 0) {
          await grantSubscription(uid, productId, expiryMs, orderId);
          await sendPushToUser(uid, "Pro Host Subscription Activated", "Welcome! Your Pro Host subscription is now active — start publishing workspace listings.", {
            category: "PACKAGE_ACTIVATED",
            // owner_subscriptions is reachable even while the device still holds the
            // pre-upgrade SPECIALIST claim; manage_listings is not.
            targetTab: "owner_subscriptions",
          });
          try {
            const db = getFirestore();
            const userSnap = await db.collection("user_profiles").doc(uid).get();
            const userData = userSnap.data();
            if (userData?.email) {
              const ctx: UserContext = {
                fullName: userData.fullName ?? "Member",
                email: userData.email,
                role: "PRO_HOST",
                activeListingCount: (userData.activeListingCount ?? 0) as number,
              };
              const tpl = subscriptionActivatedTemplate(ctx, productId);
              await sendEmail({ to: userData.email, ...tpl });
            }
          } catch (_) { /* email is best-effort */ }
        } else {
          logger.warn(`playBillingRtdn: type=${notificationType} has no expiryTimeMillis, skipping grant`);
        }
        break;

      case SUBSCRIPTION_RENEWED:
        if (expiryMs > 0) {
          await grantSubscription(uid, productId, expiryMs, orderId);
          await sendPushToUser(uid, "Subscription Renewed", "Your Pro Host subscription has renewed — your access continues uninterrupted.", {
            category: "PACKAGE_RENEWED",
            targetTab: "owner_subscriptions",
          });
          try {
            const db = getFirestore();
            const userSnap = await db.collection("user_profiles").doc(uid).get();
            const userData = userSnap.data();
            if (userData?.email) {
              const ctx: UserContext = {
                fullName: userData.fullName ?? "Member",
                email: userData.email,
                role: "PRO_HOST",
              };
              const expiryDate = new Date(expiryMs).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
              const tpl = subscriptionRenewedTemplate(ctx, productId, expiryDate);
              await sendEmail({ to: userData.email, ...tpl });
            }
          } catch (_) { /* email is best-effort */ }
        } else {
          logger.warn(`playBillingRtdn: type=${notificationType} has no expiryTimeMillis, skipping grant`);
        }
        break;

      case SUBSCRIPTION_RECOVERED:
      case SUBSCRIPTION_RESTARTED:
        if (expiryMs > 0) {
          await grantSubscription(uid, productId, expiryMs, orderId);
        } else {
          logger.warn(`playBillingRtdn: type=${notificationType} has no expiryTimeMillis, skipping grant`);
        }
        break;

      case SUBSCRIPTION_DEFERRED:
        // Promotional deferral — use grantSubscription() so it keeps the later
        // expiry, restores any lapsed listings, and auto-publishes pending drafts.
        if (expiryMs > 0) {
          await grantSubscription(uid, productId, expiryMs, orderId);
        }
        break;

      case SUBSCRIPTION_REVOKED:
        await revokeSubscription(
          uid, productId, orderId,
          "Your ProHost subscription was refunded or revoked by Google Play. Your Pro Host access has ended."
        );
        break;

      case SUBSCRIPTION_EXPIRED:
        // expirePackages.ts already handles this on its hourly sweep, but handle
        // it here too for instant effect on the RTDN event.
        await revokeSubscription(
          uid, productId, orderId,
          "Your Google Play subscription has expired. Renew in the app to restore Pro Host access."
        );
        break;

      case SUBSCRIPTION_ON_HOLD:
        await revokeSubscription(
          uid, productId, orderId,
          "Your ProHost subscription is on hold. Update your payment method in Google Play to restore Pro Host access."
        );
        break;

      case SUBSCRIPTION_PAUSED:
        // User-initiated pause: access should be suspended but listings stay hidden
        // gently (same as ON_HOLD) rather than hard-deleted — RESTARTED will restore
        // them. Use revokeSubscription so listings get isOwnerPackageLapsed=true, but
        // send a softer message.
        await revokeSubscription(
          uid, productId, orderId,
          "Your ProHost subscription is paused. Resume it in Google Play to restore your Pro Host access and listings."
        );
        break;

      case SUBSCRIPTION_CANCELED:
        // Access continues until expiry — just log; expirePackages sweeps it at term end.
        await recordAuditLog({
          actionType: "PLAY_BILLING_SUBSCRIPTION_CANCELED",
          details: `uid=${uid} canceled subscription ${productId} (order ${orderId}). Access until ${new Date(expiryMs).toISOString()}.`,
          actorEmail: "play-billing@system.prohost.app",
          severity: "INFO",
        });
        break;

      case SUBSCRIPTION_IN_GRACE_PERIOD:
        await sendPushToUser(
          uid,
          "Payment Issue — Action Needed",
          "Your ProHost subscription payment failed. Please update your payment method in Google Play to keep your Pro Host access.",
          { category: "PAYMENT_REMINDER", targetTab: "owner_subscriptions" }
        );
        break;

      default:
        logger.info(`playBillingRtdn: unhandled notificationType ${notificationType}`);
    }
  }
);
