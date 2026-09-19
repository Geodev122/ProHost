import { onMessagePublished } from "firebase-functions/v2/pubsub";
import * as admin from "firebase-admin";

const db = admin.firestore();

/**
 * Google Play Billing Real-Time Developer Notification (RTDN) Pub/Sub Trigger.
 * Triggered whenever Google Play publishes subscription state events (purchase, renewal, cancellation, voided purchase, refund).
 */
export const playBillingRtdn = onMessagePublished("play-billing-rtdn", async (event) => {
  const message = event.data.message;
  if (!message || !message.data) {
    console.error("[RTDN] Empty message data received");
    return;
  }

  try {
    const jsonStr = Buffer.from(message.data, "base64").toString("utf-8");
    const payload = JSON.parse(jsonStr);

    console.log(`[RTDN] Received notification for package: ${payload.packageName}`);

    if (payload.subscriptionNotification) {
      await handleSubscriptionNotification(payload.subscriptionNotification, payload.packageName);
    } else if (payload.voidedPurchaseNotification) {
      await handleVoidedPurchaseNotification(payload.voidedPurchaseNotification, payload.packageName);
    }
  } catch (error) {
    console.error("[RTDN] Error processing Pub/Sub message:", error);
  }
});

interface SubscriptionNotification {
  version: string;
  notificationType: number;
  purchaseToken: string;
  subscriptionId: string;
}

interface VoidedPurchaseNotification {
  purchaseToken: string;
  orderId: string;
  productType: number;
  refundType?: number;
}

const SUBSCRIPTION_NOTIFICATION_TYPES: Record<number, string> = {
  1: "SUBSCRIPTION_RECOVERED",
  2: "SUBSCRIPTION_RENEWED",
  3: "SUBSCRIPTION_CANCELED",
  4: "SUBSCRIPTION_PURCHASED",
  5: "SUBSCRIPTION_ON_HOLD",
  6: "SUBSCRIPTION_IN_GRACE_PERIOD",
  7: "SUBSCRIPTION_RESTARTED",
  8: "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED",
  9: "SUBSCRIPTION_DEFERRED",
  10: "SUBSCRIPTION_PAUSED",
  11: "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED",
  12: "SUBSCRIPTION_REVOKED",
  13: "SUBSCRIPTION_EXPIRED",
};

async function handleSubscriptionNotification(
  notification: SubscriptionNotification,
  packageName: string
) {
  const typeName = SUBSCRIPTION_NOTIFICATION_TYPES[notification.notificationType] || `TYPE_${notification.notificationType}`;
  console.log(`[RTDN] Subscription event: ${typeName} for SKU ${notification.subscriptionId}`);

  // Query user_profiles holding this purchase token or active subscription
  const userQuery = await db.collection("user_profiles")
    .where("activePurchaseToken", "==", notification.purchaseToken)
    .limit(1)
    .get();

  let userId: string | null = null;
  if (!userQuery.empty) {
    userId = userQuery.docs[0].id;
  }

  // Audit record
  await db.collection("audit_security_logs").add({
    actionType: `PLAY_BILLING_${typeName}`,
    actorEmail: userId ? `user:${userId}` : "google-play-rtdn",
    timestamp: Date.now(),
    details: `Play Store Subscription Event: ${typeName} (SKU: ${notification.subscriptionId}, Package: ${packageName})`,
    severity: (notification.notificationType === 12 || notification.notificationType === 13) ? "WARN" : "INFO",
    ipAddress: "127.0.0.1"
  });

  if (!userId) {
    console.warn(`[RTDN] No user_profile found matching purchaseToken: ${notification.purchaseToken}`);
    return;
  }

  const userRef = db.collection("user_profiles").doc(userId);

  switch (notification.notificationType) {
    case 1: // RECOVERED
    case 2: // RENEWED
    case 4: // PURCHASED
    case 7: // RESTARTED
      await userRef.update({
        role: "PRO_HOST",
        packageId: notification.subscriptionId,
        subscriptionStatus: "ACTIVE",
        activePurchaseToken: notification.purchaseToken,
        updatedAtMillis: Date.now()
      });
      console.log(`[RTDN] Activated PRO_HOST subscription for user ${userId}`);
      break;

    case 5: // ON_HOLD
    case 6: // IN_GRACE_PERIOD
    case 10: // PAUSED
      await userRef.update({
        subscriptionStatus: typeName,
        updatedAtMillis: Date.now()
      });
      break;

    case 12: // REVOKED
    case 13: // EXPIRED
      await userRef.update({
        role: "SPECIALIST",
        subscriptionStatus: "EXPIRED",
        updatedAtMillis: Date.now()
      });
      console.log(`[RTDN] Downgraded user ${userId} to SPECIALIST due to ${typeName}`);
      break;

    case 3: // CANCELED
      await userRef.update({
        subscriptionAutoRenew: false,
        updatedAtMillis: Date.now()
      });
      break;

    default:
      console.log(`[RTDN] Event ${typeName} logged without structural mutation`);
      break;
  }
}

async function handleVoidedPurchaseNotification(
  notification: VoidedPurchaseNotification,
  packageName: string
) {
  console.log(`[RTDN] Voided/Refunded purchase order: ${notification.orderId} for package ${packageName}`);

  // Query user by purchaseToken
  const userQuery = await db.collection("user_profiles")
    .where("activePurchaseToken", "==", notification.purchaseToken)
    .limit(1)
    .get();

  if (!userQuery.empty) {
    const userId = userQuery.docs[0].id;
    await db.collection("user_profiles").doc(userId).update({
      role: "SPECIALIST",
      subscriptionStatus: "REFUNDED_VOIDED",
      updatedAtMillis: Date.now()
    });

    await db.collection("audit_security_logs").add({
      actionType: "PLAY_BILLING_VOIDED_PURCHASE",
      actorEmail: `user:${userId}`,
      timestamp: Date.now(),
      details: `Google Play Voided/Refunded Purchase for Order ID ${notification.orderId}`,
      severity: "WARN",
      ipAddress: "127.0.0.1"
    });
  }
}
