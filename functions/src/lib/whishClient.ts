import { createHash } from "crypto";
import * as logger from "firebase-functions/logger";

// Production host. This is the ONLY place in the whole system, client included,
// that talks to Whish's API directly as of Phase 5 — the app no longer calls
// Whish itself. If you need to test against Whish's sandbox instead, swap this to
// https://api.sandbox.whish.money/itel-service/api/ (and use a sandbox secret via
// firebase functions:secrets:set WHISH_SECRET_KEY) before redeploying.
const WHISH_BASE_URL = "https://api.whish.money/itel-service/api/";
export const WHISH_CHANNEL_ID = "15462415";
// The merchant's actual registered website — the `websiteUrl` header below used to be
// set to the merchant's email address instead of a URL (an old bug carried over from
// the original client code, which also pointed its callback URLs at a malformed
// "https://ceo@hopebearer-award.com/success" address). hopebearer-award.com is the
// real, working site tied to this Whish merchant account.
const WHISH_WEBSITE_URL = "https://hopebearer-award.com";

interface WhishHeaders {
  channel: string;
  httpchannel: string;
  secret: string;
  websiteUrl: string;
  "User-Agent": string;
  "Content-Type": string;
}

function headers(secret: string): WhishHeaders {
  return {
    channel: WHISH_CHANNEL_ID,
    httpchannel: WHISH_CHANNEL_ID,
    secret,
    websiteUrl: WHISH_WEBSITE_URL,
    "User-Agent": "Whish/1.0 (https://whish.money; support@whish.money)",
    "Content-Type": "application/json",
  };
}

export function generateSignature(amount: number, currency: string, orderId: string, secret: string): string {
  const formattedAmount = amount.toFixed(2);
  const raw = `${WHISH_CHANNEL_ID}|${formattedAmount}|${currency}|${orderId}|${secret}`;
  return createHash("sha256").update(raw, "utf8").digest("hex");
}

export interface InitiatePaymentParams {
  amount: number;
  currency: string;
  invoice: string;
  externalId: number;
  successCallbackUrl: string;
  failureCallbackUrl: string;
  successRedirectUrl: string;
  failureRedirectUrl: string;
}

export async function initiatePayment(
  params: InitiatePaymentParams,
  secret: string
): Promise<{ collectUrl: string }> {
  const res = await fetch(`${WHISH_BASE_URL}payment/whish`, {
    method: "POST",
    headers: headers(secret) as unknown as HeadersInit,
    body: JSON.stringify({
      amount: params.amount.toFixed(0),
      currency: params.currency,
      invoice: params.invoice,
      externalId: params.externalId,
      successCallbackUrl: params.successCallbackUrl,
      failureCallbackUrl: params.failureCallbackUrl,
      successRedirectUrl: params.successRedirectUrl,
      failureRedirectUrl: params.failureRedirectUrl,
    }),
  });
  const body = (await res.json()) as {
    status: boolean;
    dialog?: { message?: string };
    data?: { collectUrl?: string };
  };
  if (!res.ok || !body.status || !body.data?.collectUrl) {
    // The one place this specific HTTP/API-level detail (status code, Whish's own
    // rejection message, the externalId it was rejected for) ever exists — the
    // callers above only ever see the thrown Error's message, so without this a
    // rejected payment initiation is nearly undebuggable after the fact.
    logger.warn("whish_client_initiate_payment_failed", {
      externalId: params.externalId,
      httpStatus: res.status,
      apiStatus: body.status,
      dialogMessage: body.dialog?.message ?? null,
    });
    throw new Error(body.dialog?.message ?? `Whish payment initiation failed (HTTP ${res.status})`);
  }
  return { collectUrl: body.data.collectUrl };
}

export type WhishCollectStatus = "success" | "failed" | "pending";

export async function getCollectStatus(
  currency: string,
  externalId: number,
  secret: string
): Promise<{ status: WhishCollectStatus; payerPhoneNumber?: string }> {
  const res = await fetch(`${WHISH_BASE_URL}payment/collect/status`, {
    method: "POST",
    headers: headers(secret) as unknown as HeadersInit,
    body: JSON.stringify({ currency, externalId }),
  });
  const body = (await res.json()) as {
    status: boolean;
    dialog?: { message?: string };
    data?: { collectStatus?: string; payerPhoneNumber?: string };
  };
  if (!res.ok || !body.status || !body.data?.collectStatus) {
    logger.warn("whish_client_get_status_failed", {
      externalId,
      httpStatus: res.status,
      apiStatus: body.status,
      dialogMessage: body.dialog?.message ?? null,
    });
    throw new Error(body.dialog?.message ?? `Whish status check failed (HTTP ${res.status})`);
  }
  const raw = body.data.collectStatus.toLowerCase();
  const normalized: WhishCollectStatus = raw === "success" ? "success" : raw === "failed" ? "failed" : "pending";
  return { status: normalized, payerPhoneNumber: body.data.payerPhoneNumber };
}
