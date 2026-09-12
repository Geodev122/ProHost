import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { getFirestore } from "firebase-admin/firestore";
import { initiatePayment, generateSignature, WHISH_CHANNEL_ID } from "../lib/whishClient";
import { getPricingState, getPaygFeeForCategory, getPackageFee, OwnerPackageTier } from "../lib/pricing";
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
  // Set only when this payment is resolving a quota/PAYG-credit block on a specific
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
    if (!purpose || !targetId || !payerName || !payerPhone) {
      throw new HttpsError("invalid-argument", "purpose, targetId, payerName, and payerPhone are required.");
    }
    if (!["SUBSCRIPTION", "OWNER_PACKAGE", "PAYG_LISTING"].includes(purpose)) {
      throw new HttpsError("invalid-argument", `Unknown purpose: ${purpose}`);
    }

    const db = getFirestore();
    let amountUsd: number;
    let invoiceLabel: string;
    let spaceIdForRecord = "";
    let spaceTitleForRecord = "";
    let daysGranted = 30;

    switch (purpose as WhishPurpose) {
      case "SUBSCRIPTION": {
        const spaceSnap = await db.collection("workspace_listings").doc(targetId).get();
        if (!spaceSnap.exists) {
          throw new HttpsError("not-found", "Workspace listing not found.");
        }
        const pricing = await getPricingState();
        amountUsd = pricing.monthlySubscriptionFeeUsd;
        invoiceLabel = `Listing subscription #${targetId}`;
        spaceIdForRecord = targetId;
        spaceTitleForRecord = (spaceSnap.data()?.title as string) ?? "ProHost Subscription";
        break;
      }
      case "OWNER_PACKAGE": {
        if (targetId !== "LIMITED_3_TIER" && targetId !== "UNLIMITED_TIER") {
          throw new HttpsError("invalid-argument", "targetId must be LIMITED_3_TIER or UNLIMITED_TIER.");
        }
        const pricing = await getPricingState();
        amountUsd = getPackageFee(pricing, targetId as OwnerPackageTier);
        invoiceLabel = `ProHost package upgrade: ${targetId}`;
        spaceIdForRecord = `OWNER-PKG-${targetId}`;
        spaceTitleForRecord = `ProHost Package Subscription: ${targetId}`;
        break;
      }
      case "PAYG_LISTING": {
        // targetId is now a Space Category id (a SchemaItem.id under
        // schema_architecture/main's spaceTypes, "ST-01" etc. — or, for backward
        // compatibility, one of the original 4 legacy SpaceType names). Real
        // per-category pricing lives on the admin schema now, not a closed switch —
        // see getPaygFeeForCategory. An unrecognized/unpriced category throws rather
        // than falling back to any default charge.
        const pricing = await getPricingState();
        try {
          amountUsd = await getPaygFeeForCategory(pricing, targetId);
        } catch (e) {
          logger.warn("whish_initiate_invalid_payg_category", {
            uid: auth.uid,
            targetId,
            error: e instanceof Error ? e.message : String(e),
          });
          throw new HttpsError("invalid-argument", e instanceof Error ? e.message : "Invalid PAYG category.");
        }
        invoiceLabel = `PAYG listing slot: ${targetId}`;
        spaceIdForRecord = `PAYG-SLOT-${targetId}`;
        spaceTitleForRecord = `PAYG Listing Slot (${targetId})`;
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
      targetId,
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
