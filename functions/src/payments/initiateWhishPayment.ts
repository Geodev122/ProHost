import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { getFirestore } from "firebase-admin/firestore";
import { initiatePayment, generateSignature, WHISH_CHANNEL_ID } from "../lib/whishClient";
import { getPackagePlan } from "../lib/packagePlans";
import { WhishPurpose } from "../lib/entitlements";
import * as logger from "firebase-functions/logger";
import "../lib/admin";

export const whishSecret = defineSecret("WHISH_SECRET_KEY");

// Stable Cloud Functions URL (not the per-deploy Cloud Run URL, which changes) —
// see functions/src/payments/whishWebhook.ts.
const WEBHOOK_URL = "https://europe-west1-prohost-f766f.cloudfunctions.net/whishWebhook";
const MERCHANT_SOURCE_EMAIL = "ceo@hopebearer-award.com";

interface InitiateWhishPaymentData {
  purpose?: string;
  targetId?: string;
  payerName?: string;
  payerPhone?: string;
  successRedirectUrl?: string;
  failureRedirectUrl?: string;
  // Set only when this payment is resolving a package-limit block on a specific
  // Draft — see entitlements.ts's autoPublishDraftIfNeeded.
  draftListingId?: string;
}

/**
 * Starts a Whish payment. Looks up the real amount SERVER-SIDE for every purpose —
 * never trusts a client-supplied amount. Returns only a collectUrl for the client to
 * open; the client never sees the merchant secret and never decides whether payment
 * succeeded (see whishWebhook.ts / checkWhishStatus.ts).
 */
export const initiateWhishPayment = onCall<InitiateWhishPaymentData>(
  { secrets: [whishSecret] },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }

    const { purpose, targetId, payerName, payerPhone, draftListingId } = request.data ?? {};
    if (!purpose || !payerName || !payerPhone) {
      throw new HttpsError("invalid-argument", "purpose, payerName, and payerPhone are required.");
    }
    if (purpose !== "OWNER_PACKAGE") {
      throw new HttpsError("invalid-argument", `Unknown purpose: ${purpose}`);
    }
    if (!targetId) {
      throw new HttpsError("invalid-argument", "targetId is required.");
    }

    const db = getFirestore();
    let amountUsd: number;
    let invoiceLabel: string;
    let spaceIdForRecord = "";
    let spaceTitleForRecord = "";
    let daysGranted: number;

    switch (purpose as WhishPurpose) {
      case "OWNER_PACKAGE": {
        // targetId is an admin-defined PackagePlan id (package_plans/main, keyed by
        // id) — validated + priced live against the real, currently-enabled catalog,
        // never a hardcoded 2-value allowlist and never a client-supplied amount.
        const plan = await getPackagePlan(targetId);
        if (!plan || !plan.isEnabled) {
          throw new HttpsError("invalid-argument", "This package is not available for purchase.");
        }
        amountUsd = plan.priceUsd;
        invoiceLabel = `ProHost package upgrade: ${plan.name}`;
        spaceIdForRecord = `OWNER-PKG-${targetId}`;
        spaceTitleForRecord = `ProHost Package Subscription: ${plan.name}`;
        daysGranted = plan.validityDays;
        break;
      }
    }

    const externalId = Date.now();
    const orderId = `ORD-${purpose}-${externalId}`;
    const txId = `TX-${externalId}`;
    const secret = whishSecret.value();
    const signatureHash = generateSignature(amountUsd, "USD", orderId, secret);

    const callbackUrl = `${WEBHOOK_URL}?externalId=${externalId}`;

    let collectUrl: string;
    try {
      ({ collectUrl } = await initiatePayment(
        {
          amount: amountUsd,
          currency: "USD",
          invoice: invoiceLabel,
          externalId,
          successCallbackUrl: callbackUrl,
          failureCallbackUrl: callbackUrl,
          successRedirectUrl: request.data.successRedirectUrl ?? "https://hopebearer-award.com/payment/success",
          failureRedirectUrl: request.data.failureRedirectUrl ?? "https://hopebearer-award.com/payment/failure",
        },
        secret
      ));
    } catch (e) {
      // whishClient already logged the HTTP/API-level detail; this ties it to
      // the business context (who, for what, how much) before it's lost —
      // no whish_transactions doc is ever created for a failed initiation, so
      // this log is the only record this attempt ever happened.
      logger.error("whish_initiate_payment_request_failed", {
        uid: auth.uid,
        purpose,
        targetId,
        amountUsd,
        orderId,
        error: e instanceof Error ? e.message : String(e),
      });
      throw new HttpsError("unavailable", e instanceof Error ? e.message : "Could not start the payment with Whish.");
    }

    await db.collection("whish_transactions").doc(txId).set({
      id: txId,
      orderId,
      amountUsd,
      currency: "USD",
      status: "PENDING",
      timestamp: Date.now(),
      payerName,
      payerPhone,
      channelId: WHISH_CHANNEL_ID,
      sourceEmail: MERCHANT_SOURCE_EMAIL,
      signatureHash,
      spaceId: spaceIdForRecord,
      spaceTitle: spaceTitleForRecord,
      daysGranted,
      userId: auth.uid,
      purpose,
      targetId: targetId ?? null,
      externalId,
      ...(draftListingId ? { draftListingId } : {}),
    });

    logger.info("whish_payment_initiated", {
      txId,
      orderId,
      uid: auth.uid,
      purpose,
      targetId,
      amountUsd,
    });

    return { collectUrl, txId, orderId };
  }
);
