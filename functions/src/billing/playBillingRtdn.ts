import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions/v2";
import { google } from "googleapis";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import { setClaimsThenFirestore } from "../lib/roles";
import "../lib/admin";

// Must match applicationId in app/build.gradle.kts
const PACKAGE_NAME = "app.geonajjar.prohost";

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

async function getPlayPublisher() {
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return google.androidpublisher({ version: "v3", auth });
}

/** Fetches live subscription details from Play Developer API. */
async function queryPlaySubscription(productId: string, token: string) {
  const publisher = await getPlayPublisher();
  const { data } = await publisher.purchases.subscriptions.get({
    packageName: PACKAGE_NAME,
    subscriptionId: productId,
    token,
  });
  return data;
}

/**
 * Grants (or extends) the Pro Host subscription for [uid] in Firestore.
 * Uses Play's own [expiryTimeMillis] as the canonical validity source so the
 * backend stays in sync with what Google actually charged for.
 */
async function grantSubscription(
  uid: string,
  planId: string,
  expiryTimeMillis: number,
  orderId: string
): Promise<void> {
  const db = getFirestore();
  const auth = getAuth();
  const now = Date.now();

  const userRef = db.collection("user_profiles").doc(uid);
  const userSnap = await userRef.get();
  const userData = userSnap.data();

  // Keep the later of: Play's expiry vs any still-valid current expiry on the
  // same plan — prevents a RENEWED notification from shrinking an already-extended term.
  const currentExpiry = userData?.ownerPackageExpiryMillis as number | undefined;
  const isSameActivePlan =
    userData?.ownerPackageId === planId &&
    typeof currentExpiry === "number" &&
    currentExpiry > now;
  const newExpiry = isSameActivePlan ? Math.max(expiryTimeMillis, currentExpiry) : expiryTimeMillis;

  await userRef.set(
    { ownerPackageId: planId, ownerPackageExpiryMillis: newExpiry, expiryWarningSent: false, updatedAt: now },
    { merge: true }
  );

  // Promote SPECIALIST → PRO_HOST if needed (idempotent)
  const authUser = await auth.getUser(uid);
  const currentRole = authUser.customClaims?.role;
  if (currentRole !== "ADMIN" && currentRole !== "PRO_HOST") {
    await auth.setCustomUserClaims(uid, { ...authUser.customClaims, role: "PRO_HOST" });
    await userRef.set({ role: "PRO_HOST" }, { merge: true });
    await recordAuditLog({
      actionType: "ROLE_PROMOTED_PRO_HOST",
      details: `uid=${uid} promoted to PRO_HOST via Google Play subscription (order ${orderId}).`,
      actorEmail: "play-billing@system.prohost.app",
      severity: "SECURE",
    });
  }

  // Un-hide any listings that were hidden when a prior package lapsed
  const lapsedListings = await db
    .collection("workspace_listings")
    .where("ownerId", "==", uid)
    .where("isOwnerPackageLapsed", "==", true)
    .get();
  if (!lapsedListings.empty) {
    const bw = db.bulkWriter();
    lapsedListings.docs.forEach((doc) => bw.set(doc.ref, { isOwnerPackageLapsed: false }, { merge: true }));
    await bw.close();
    logger.info(`playBillingRtdn: restored ${lapsedListings.size} lapsed listing(s) for uid=${uid}`);
  }

  await recordAuditLog({
    actionType: "PLAY_BILLING_SUBSCRIPTION_GRANTED",
    details: `Plan ${planId} granted for uid=${uid}, order=${orderId}, expires=${new Date(newExpiry).toISOString()}.`,
    actorEmail: "play-billing@system.prohost.app",
    severity: "SECURE",
  });
}

/**
 * Revokes Pro Host access immediately (refund, hard expiry, or hold).
 * Skips if [uid]'s current plan is already different — prevents an RTDN for
 * an old subscription from revoking a freshly-purchased upgrade.
 */
async function revokeSubscription(
  uid: string,
  planId: string,
  orderId: string,
  pushMessage: string
): Promise<void> {
  const db = getFirestore();
  const auth = getAuth();
  const now = Date.now();

  const userSnap = await db.collection("user_profiles").doc(uid).get();
  const userData = userSnap.data();
  if (userData?.ownerPackageId !== planId) {
    logger.info(`playBillingRtdn: revokeSubscription uid=${uid}: current plan (${userData?.ownerPackageId}) != ${planId}, skipping`);
    return;
  }

  const authUser = await auth.getUser(uid);
  const isProHost = authUser.customClaims?.role === "PRO_HOST";

  if (isProHost) {
    await setClaimsThenFirestore(
      auth,
      uid,
      authUser.customClaims,
      { ...authUser.customClaims, role: "SPECIALIST" },
      async () => {
        await db.collection("user_profiles").doc(uid).set(
          { role: "SPECIALIST", ownerPackageId: null, ownerPackageExpiryMillis: null, updatedAt: now },
          { merge: true }
        );
      }
    );

    const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
    if (!ownedListings.empty) {
      const bw = db.bulkWriter();
      ownedListings.docs.forEach((doc) => bw.set(doc.ref, { isOwnerPackageLapsed: true }, { merge: true }));
      await bw.close();
    }

    await sendPushToUser(uid, "Subscription Ended", pushMessage, {
      category: "PACKAGE_EXPIRED",
      targetTab: "owner_subscriptions",
    });
  } else {
    await db.collection("user_profiles").doc(uid).set(
      { ownerPackageId: null, ownerPackageExpiryMillis: null },
      { merge: true }
    );
  }

  await recordAuditLog({
    actionType: "PLAY_BILLING_SUBSCRIPTION_REVOKED",
    details: `Plan ${planId} revoked for uid=${uid}, order=${orderId}. Reason: ${pushMessage}`,
    actorEmail: "play-billing@system.prohost.app",
    severity: "WARN",
  });
}

/**
 * Google Play Real-Time Developer Notification handler.
 *
 * Setup in Google Play Console: Monetize → Subscriptions → Real-time developer
 * notifications → Set a Pub/Sub topic named "play-billing-rtdn". The topic must
 * exist in the same Google Cloud project. The service account running this function
 * (the App Engine default service account or a custom one) must have the
 * "Android Publisher" OAuth scope, which Application Default Credentials supply
 * automatically when the Cloud project is linked to Google Play Console.
 *
 * Subscription product IDs in Google Play Console MUST match the Firestore
 * package_plans document keys exactly (the plan's "id" field) so the RTDN
 * handler can find the right plan without an extra lookup.
 */
export const playBillingRtdn = onMessagePublished(
  { topic: "play-billing-rtdn" },
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
      logger.error(`playBillingRtdn: no obfuscatedExternalAccountId for product=${productId} — launch flow must set setObfuscatedAccountId(uid)`);
      return;
    }

    const orderId = purchase.orderId ?? productId;
    const expiryMs = parseInt(purchase.expiryTimeMillis ?? "0", 10);

    // 3. Dispatch by notification type
    switch (notificationType) {
      case SUBSCRIPTION_PURCHASED:
      case SUBSCRIPTION_RENEWED:
      case SUBSCRIPTION_RECOVERED:
      case SUBSCRIPTION_RESTARTED:
        if (expiryMs > 0) {
          await grantSubscription(uid, productId, expiryMs, orderId);
        } else {
          logger.warn(`playBillingRtdn: type=${notificationType} has no expiryTimeMillis, skipping grant`);
        }
        break;

      case SUBSCRIPTION_DEFERRED:
        // Promotional deferral — just update the expiry, no role changes
        if (expiryMs > 0) {
          await getFirestore().collection("user_profiles").doc(uid).set(
            { ownerPackageExpiryMillis: expiryMs, updatedAt: Date.now() },
            { merge: true }
          );
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
      case SUBSCRIPTION_PAUSED:
        await revokeSubscription(
          uid, productId, orderId,
          "Your ProHost subscription is on hold. Update your payment method in Google Play to restore Pro Host access."
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
