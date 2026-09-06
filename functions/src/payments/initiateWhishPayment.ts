import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { getFirestore } from "firebase-admin/firestore";
import { initiatePayment, generateSignature, WHISH_CHANNEL_ID } from "../lib/whishClient";
import { getPricingState, getPaygFeeForType, getPackageFee, PaygSpaceType, OwnerPackageTier } from "../lib/pricing";
import { WhishPurpose } from "../lib/entitlements";
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

    const { purpose, targetId, payerName, payerPhone } = request.data ?? {};
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
        invoiceLabel = `Owner package upgrade: ${targetId}`;
        spaceIdForRecord = `OWNER-PKG-${targetId}`;
        spaceTitleForRecord = `Owner Package Subscription: ${targetId}`;
        break;
      }
      case "PAYG_LISTING": {
        const validTypes = ["PRIVATE_OFFICE", "CENTER", "POLYCLINIC", "COWORKING_SPACE"];
        if (!validTypes.includes(targetId)) {
          throw new HttpsError("invalid-argument", `targetId must be one of: ${validTypes.join(", ")}`);
        }
        const pricing = await getPricingState();
        amountUsd = getPaygFeeForType(pricing, targetId as PaygSpaceType);
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

    const { collectUrl } = await initiatePayment(
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
    );

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
    });

    return { collectUrl, txId, orderId };
  }
);
