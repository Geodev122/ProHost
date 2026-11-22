/**
 * The App Store entry points:
 *  - `verifyAppleTransaction` (callable): the iOS app sends StoreKit 2's signed transaction (and renewal info) after a
 *    purchase, a restore and on every resume; the server verifies it with Apple's keys and applies it.
 *  - `appStoreNotifications` (HTTPS): App Store Server Notifications V2 (renewals, expiry, billing retry, refunds),
 *    verified the same way. Its URL goes into App Store Connect (production and sandbox).
 * Both end in `syncAppleSubscription`, so a purchase, renewal or refund always produces the same role as on Android.
 */
import { HttpsError, onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { onCall } from "../lib/callable";
import { runAs } from "../lib/runtimeIdentity";
import { appAccountTokenFor, isAppleProduct } from "./appleCatalog";
import {
  appleSubscriptionOwner,
  factsOf,
  syncAppleSubscription,
  verifyAppleNotificationJws,
  verifyAppleRenewalJws,
  verifyAppleTransactionJws,
} from "./appleSubscription";

export const verifyAppleTransaction = onCall<{ signedTransaction: string; signedRenewalInfo?: string }>(
  runAs("billing"),
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
    const { signedTransaction, signedRenewalInfo } = request.data ?? {};
    if (!signedTransaction || typeof signedTransaction !== "string") {
      throw new HttpsError("invalid-argument", "signedTransaction is required.");
    }

    let facts;
    try {
      const tx = await verifyAppleTransactionJws(signedTransaction);
      const renewal = typeof signedRenewalInfo === "string" && signedRenewalInfo
        ? await verifyAppleRenewalJws(signedRenewalInfo).catch(() => null)
        : null;
      facts = factsOf(tx, renewal);
    } catch (e) {
      logger.warn(`verifyAppleTransaction: verification failed: ${(e as Error).message}`);
      throw new HttpsError("invalid-argument", "This App Store purchase couldn't be verified.");
    }
    if (!isAppleProduct(facts.productId)) {
      throw new HttpsError("invalid-argument", "This purchase isn't ProHost Premium.");
    }
    // The app tags every purchase with this account's token; an untagged one (offer code redeemed in the App Store)
    // goes to the first account that verifies it, like Play's unlinked purchases.
    if (facts.appAccountToken && facts.appAccountToken !== appAccountTokenFor(uid)) {
      throw new HttpsError("permission-denied", "This App Store purchase belongs to another ProHost account.");
    }
    const owner = await appleSubscriptionOwner(facts.originalTransactionId);
    if (owner && owner !== uid) {
      throw new HttpsError("permission-denied", "This App Store purchase belongs to another ProHost account.");
    }

    const result = await syncAppleSubscription(uid, facts, { source: "app_verify" });
    return { status: result.status, hasAccess: result.hasAccess };
  }
);

export const appStoreNotifications = onRequest({ ...runAs("billing") }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("POST only");
    return;
  }
  const signedPayload = (req.body as { signedPayload?: unknown } | undefined)?.signedPayload;
  if (typeof signedPayload !== "string" || !signedPayload) {
    res.status(400).send("signedPayload missing");
    return;
  }
  let notification;
  try {
    notification = await verifyAppleNotificationJws(signedPayload);
  } catch (e) {
    logger.warn(`appStoreNotifications: verification failed: ${(e as Error).message}`);
    res.status(400).send("not verified");
    return;
  }
  const type = String(notification.notificationType ?? "");
  const data = notification.data;
  if (type === "TEST" || !data?.signedTransactionInfo) {
    logger.info(`appStoreNotifications: ${type || "notification"} without a transaction`);
    res.status(200).send("ok");
    return;
  }
  try {
    const tx = await verifyAppleTransactionJws(data.signedTransactionInfo);
    const renewal = data.signedRenewalInfo ? await verifyAppleRenewalJws(data.signedRenewalInfo).catch(() => null) : null;
    const facts = factsOf(tx, renewal);
    if (!isAppleProduct(facts.productId)) {
      res.status(200).send("ignored");
      return;
    }
    const uid = await appleSubscriptionOwner(facts.originalTransactionId);
    if (!uid) {
      // Not linked yet (the app's verify call links it on its next resume).
      logger.info(`appStoreNotifications: ${type} for an unlinked subscription`);
      res.status(200).send("unlinked");
      return;
    }
    const result = await syncAppleSubscription(uid, facts, { source: `assn_${type.toLowerCase()}` });
    logger.info(`appStoreNotifications: ${type} → ${result.status}`);
    res.status(200).send("ok");
  } catch (e) {
    // Apple retries a non-2xx notification; a transient failure gets another chance.
    logger.error(`appStoreNotifications: ${type} failed: ${(e as Error).message}`);
    res.status(500).send("retry");
  }
});
