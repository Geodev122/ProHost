/**
 * App Store subscriptions (StoreKit 2): verify Apple's signed data and apply it through the same EntitlementManager as
 * Google Play, recorded in subscriptions/apple_{originalTransactionId} (platform "ios", server-only like Play's).
 *
 * Verification uses Apple's official library against Apple Root CA - G3. The root certificate is downloaded from
 * apple.com once per instance and accepted only when its SHA-256 fingerprint equals [APPLE_ROOT_G3_SHA256]; any mismatch
 * or failure makes verification fail closed (nothing is granted).
 */
import { createHash } from "crypto";
import { logger } from "firebase-functions/v2";
import { getFirestore } from "firebase-admin/firestore";
import {
  Environment,
  JWSRenewalInfoDecodedPayload,
  JWSTransactionDecodedPayload,
  SignedDataVerifier,
} from "@apple/app-store-server-library";
import {
  APPLE_APP_ID,
  APPLE_BUNDLE_ID,
  AppleSubscriptionFacts,
  appleAccessUntil,
  appleStatusFor,
  appleSubscriptionDocId,
} from "./appleCatalog";
import { BillingStatus, adminEventFor, grantsAccess } from "./playCatalog";
import { grantProHost, removeProHost } from "./entitlementManager";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";
import { SUBSCRIPTIONS, SyncResult } from "./subscriptionService";
import "../lib/admin";

const APPLE_ROOT_G3_URL = "https://www.apple.com/certificateauthority/AppleRootCA-G3.cer";
/** SHA-256 of the DER certificate "Apple Root CA - G3" (apple.com/certificateauthority). */
const APPLE_ROOT_G3_SHA256 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179";

let rootCert: Promise<Buffer> | null = null;

function appleRoot(): Promise<Buffer> {
  if (!rootCert) {
    rootCert = (async () => {
      const res = await fetch(APPLE_ROOT_G3_URL);
      if (!res.ok) throw new Error(`Apple root certificate download failed: HTTP ${res.status}`);
      const der = Buffer.from(await res.arrayBuffer());
      const fingerprint = createHash("sha256").update(der).digest("hex");
      if (fingerprint !== APPLE_ROOT_G3_SHA256) {
        throw new Error(`Apple root certificate fingerprint mismatch: ${fingerprint}`);
      }
      return der;
    })().catch((e) => {
      rootCert = null; // retry on the next call
      throw e;
    });
  }
  return rootCert;
}

/** Production first; TestFlight and Xcode purchases are signed for the sandbox. */
async function verifiers(): Promise<SignedDataVerifier[]> {
  const root = await appleRoot();
  return [
    new SignedDataVerifier([root], true, Environment.PRODUCTION, APPLE_BUNDLE_ID, APPLE_APP_ID),
    new SignedDataVerifier([root], true, Environment.SANDBOX, APPLE_BUNDLE_ID),
  ];
}

async function firstVerified<T>(run: (v: SignedDataVerifier) => Promise<T>): Promise<T> {
  let last: unknown = null;
  for (const v of await verifiers()) {
    try {
      return await run(v);
    } catch (e) {
      last = e;
    }
  }
  throw last instanceof Error ? last : new Error(`Apple verification failed: ${String(last)}`);
}

export function verifyAppleTransactionJws(jws: string): Promise<JWSTransactionDecodedPayload> {
  return firstVerified((v) => v.verifyAndDecodeTransaction(jws));
}

export function verifyAppleRenewalJws(jws: string): Promise<JWSRenewalInfoDecodedPayload> {
  return firstVerified((v) => v.verifyAndDecodeRenewalInfo(jws));
}

export function verifyAppleNotificationJws(jws: string) {
  return firstVerified((v) => v.verifyAndDecodeNotification(jws));
}

/** The access-deciding facts of a verified transaction (+ renewal info when Apple sent it). */
export function factsOf(tx: JWSTransactionDecodedPayload, renewal?: JWSRenewalInfoDecodedPayload | null): AppleSubscriptionFacts {
  return {
    productId: tx.productId ?? "",
    originalTransactionId: tx.originalTransactionId ?? tx.transactionId ?? "",
    transactionId: tx.transactionId ?? "",
    expiresMillis: tx.expiresDate ?? 0,
    revocationMillis: tx.revocationDate ?? null,
    autoRenews: renewal?.autoRenewStatus === undefined || renewal === null ? null : renewal.autoRenewStatus === 1,
    inBillingRetry: renewal?.isInBillingRetryPeriod === true,
    gracePeriodExpiresMillis: renewal?.gracePeriodExpiresDate ?? null,
    appAccountToken: tx.appAccountToken ? tx.appAccountToken.toLowerCase() : null,
    environment: (tx.environment as string | undefined) ?? null,
  };
}

const REMOVAL_MESSAGES: Partial<Record<BillingStatus, string>> = {
  EXPIRED: "Your ProHost Premium subscription has expired. Subscribe again to restore Pro Host access and your listings.",
  REVOKED: "Your ProHost Premium subscription was refunded or revoked by the App Store. Your Pro Host access has ended.",
  ON_HOLD: "Your ProHost Premium payment didn't go through. Update your payment method in the App Store to restore Pro Host access.",
};

/** Records [f] for [uid] and applies it like `syncSubscription` does for Play. */
export async function syncAppleSubscription(uid: string, f: AppleSubscriptionFacts, opts: { source: string }): Promise<SyncResult> {
  const db = getFirestore();
  const now = Date.now();
  const status = appleStatusFor(f, now);
  const accessUntil = appleAccessUntil(f);
  const subscriptionId = appleSubscriptionDocId(f.originalTransactionId);
  const ref = db.collection(SUBSCRIPTIONS).doc(subscriptionId);
  const existing = await ref.get();
  const prev = existing.data();

  await ref.set(
    {
      userId: uid,
      platform: "ios",
      productId: f.productId,
      basePlanId: f.productId,
      originalTransactionId: f.originalTransactionId,
      latestTransactionId: f.transactionId,
      appAccountToken: f.appAccountToken,
      environment: f.environment,
      status,
      expiryDate: accessUntil || null,
      autoRenewing: f.autoRenews !== false,
      lastSyncSource: opts.source,
      updatedAt: now,
      ...(existing.exists ? {} : { createdAt: now, startDate: now }),
    },
    { merge: true }
  );

  let result: SyncResult;
  if (prev?.adminRevoked === true && grantsAccess(status, accessUntil, now)) {
    logger.info(`syncAppleSubscription: ${subscriptionId} was revoked by an admin — not re-granted (${opts.source})`);
    result = { status, subscriptionId, hasAccess: false, removed: false };
  } else if (grantsAccess(status, accessUntil, now)) {
    await grantProHost(uid, {
      source: "app_store",
      planId: f.productId,
      expiryMillis: accessUntil,
      orderId: f.transactionId || f.originalTransactionId,
      status,
      subscriptionId,
      purchaseToken: null,
    });
    result = { status, subscriptionId, hasAccess: true, removed: false };
  } else {
    const removed = await removeProHost(uid, {
      status,
      reason: `App Store reports ${status} (${opts.source})`,
      pushMessage: REMOVAL_MESSAGES[status] ?? REMOVAL_MESSAGES.EXPIRED!,
      subscriptionId,
    });
    result = { status, subscriptionId, hasAccess: false, removed };
  }

  const event = adminEventFor(prev, status, { basePlanId: f.productId, expiryMillis: accessUntil });
  if (event) {
    await notifyAdminsOfSubscriptionChange(uid, event, {
      planId: f.productId,
      status,
      previousStatus: (prev?.status as string | undefined) ?? null,
      expiryMillis: accessUntil,
    });
  }
  return result;
}

/** The account an Apple subscription belongs to: the recorded one, else null (the app links it on its next verify). */
export async function appleSubscriptionOwner(originalTransactionId: string): Promise<string | null> {
  const snap = await getFirestore().collection(SUBSCRIPTIONS).doc(appleSubscriptionDocId(originalTransactionId)).get();
  const uid = snap.data()?.userId;
  return typeof uid === "string" && uid ? uid : null;
}
