/**
 * Play Developer API subscription lookups, normalised to one shape.
 *
 * Uses purchases.subscriptionsv2.get (token only). The v1 purchases.subscriptions.get
 * needs the product id and does not cover base plans that aren't backward compatible,
 * which is how new Play subscriptions are set up; it is kept only as a fallback for a
 * legacy token v2 doesn't know. Pure helpers (no firebase-admin) so they're unit-tested.
 */
import type { androidpublisher_v3 } from "googleapis";

export type PlayErrorKind = "config" | "invalid" | "transient";

/** A Play Developer API failure, classified so callers can react correctly. */
export class PlayApiError extends Error {
  constructor(readonly kind: PlayErrorKind, readonly status: number | null, message: string) {
    super(message);
    this.name = "PlayApiError";
  }
}

/** HTTP status of a googleapis (gaxios) error, or null for network-level failures. */
export function playErrorStatus(e: unknown): number | null {
  const err = e as { status?: unknown; code?: unknown; response?: { status?: unknown } } | null;
  for (const v of [err?.response?.status, err?.status, err?.code]) {
    const n = typeof v === "string" ? parseInt(v, 10) : v;
    if (typeof n === "number" && Number.isFinite(n) && n >= 100 && n < 600) return n;
  }
  return null;
}

/**
 * 401/403 = the functions service account can't use the Play Developer API (not invited
 * in Play Console › Users and permissions, or the API is disabled): a setup problem that
 * retries won't fix until someone changes it. 400/404/410 = Play doesn't know this
 * token/product. Anything else (5xx, 429, network) is transient.
 */
export function classifyPlayError(e: unknown): PlayApiError {
  if (e instanceof PlayApiError) return e;
  const status = playErrorStatus(e);
  const detail = e instanceof Error ? e.message : String(e);
  if (status === 401 || status === 403) {
    return new PlayApiError(
      "config",
      status,
      `Play Developer API denied access (${status}): ${detail}. Invite the Cloud Functions service ` +
        "account in Play Console › Users and permissions (View financial data + Manage orders and " +
        "subscriptions) and enable the Google Play Android Developer API in Google Cloud."
    );
  }
  if (status === 400 || status === 404 || status === 410) {
    return new PlayApiError("invalid", status, `Play does not recognise this purchase (${status}): ${detail}`);
  }
  return new PlayApiError("transient", status, `Play Developer API unavailable (${status ?? "network"}): ${detail}`);
}

export type PlaySubscriptionState =
  | "ACTIVE"
  | "PENDING"
  | "IN_GRACE_PERIOD"
  | "ON_HOLD"
  | "PAUSED"
  | "CANCELED"
  | "EXPIRED"
  | "PENDING_PURCHASE_CANCELED"
  | "UNSPECIFIED";

export interface PlaySubscription {
  productId: string;
  expiryMillis: number;
  state: PlaySubscriptionState;
  /** Payment not settled yet: never grant or acknowledge. */
  isPending: boolean;
  acknowledged: boolean;
  obfuscatedAccountId: string | null;
  linkedPurchaseToken: string | null;
  orderId: string | null;
  priceMicros: number | null;
  currency: string | null;
}

/** Maps a subscriptionsv2 resource to [PlaySubscription]. */
export function fromV2(data: androidpublisher_v3.Schema$SubscriptionPurchaseV2, productIdHint?: string): PlaySubscription {
  const items = data.lineItems ?? [];
  const latest = items.reduce<androidpublisher_v3.Schema$SubscriptionPurchaseLineItem | null>((best, item) => {
    const t = Date.parse(item.expiryTime ?? "") || 0;
    const b = best ? Date.parse(best.expiryTime ?? "") || 0 : -1;
    return t > b ? item : best;
  }, null);
  const rawState = (data.subscriptionState ?? "").replace(/^SUBSCRIPTION_STATE_/, "");
  const known: PlaySubscriptionState[] = [
    "ACTIVE", "PENDING", "IN_GRACE_PERIOD", "ON_HOLD", "PAUSED", "CANCELED", "EXPIRED", "PENDING_PURCHASE_CANCELED",
  ];
  const state = (known as string[]).includes(rawState) ? (rawState as PlaySubscriptionState) : "UNSPECIFIED";
  // Newer API fields our googleapis typings (v144) don't declare yet.
  const extra = latest as (typeof latest & {
    autoRenewingPlan?: { recurringPrice?: { currencyCode?: string | null; units?: string | null; nanos?: number | null } };
    latestSuccessfulOrderId?: string | null;
  }) | null;
  const price = extra?.autoRenewingPlan?.recurringPrice;
  const priceMicros = price
    ? Number(price.units ?? 0) * 1_000_000 + Math.round(Number(price.nanos ?? 0) / 1000)
    : null;
  return {
    productId: latest?.productId ?? productIdHint ?? "",
    expiryMillis: Date.parse(latest?.expiryTime ?? "") || 0,
    state,
    isPending: state === "PENDING",
    acknowledged: data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    obfuscatedAccountId: data.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
    linkedPurchaseToken: data.linkedPurchaseToken ?? null,
    orderId: data.latestOrderId ?? extra?.latestSuccessfulOrderId ?? null,
    priceMicros: priceMicros && priceMicros > 0 ? priceMicros : null,
    currency: price?.currencyCode ?? null,
  };
}

/** Maps a legacy v1 purchases.subscriptions resource to [PlaySubscription]. */
export function fromV1(data: androidpublisher_v3.Schema$SubscriptionPurchase, productId: string): PlaySubscription {
  const expiryMillis = parseInt(data.expiryTimeMillis ?? "0", 10) || 0;
  // paymentState: 0 pending, 1 received, 2 free trial, 3 pending deferred upgrade.
  const isPending = data.paymentState === 0;
  return {
    productId,
    expiryMillis,
    state: isPending ? "PENDING" : expiryMillis > Date.now() ? "ACTIVE" : "EXPIRED",
    isPending,
    acknowledged: data.acknowledgementState === 1,
    obfuscatedAccountId: data.obfuscatedExternalAccountId ?? null,
    linkedPurchaseToken: data.linkedPurchaseToken ?? null,
    orderId: data.orderId ?? null,
    priceMicros: data.priceAmountMicros ? Number(data.priceAmountMicros) : null,
    currency: data.priceCurrencyCode ?? null,
  };
}

/**
 * Live subscription details for [token]. v2 first; a v2 "unknown token" falls back to v1
 * when the product id is known. Throws [PlayApiError] only.
 */
export async function fetchPlaySubscription(
  publisher: androidpublisher_v3.Androidpublisher,
  packageName: string,
  token: string,
  productIdHint?: string
): Promise<PlaySubscription> {
  try {
    const { data } = await publisher.purchases.subscriptionsv2.get({ packageName, token });
    return fromV2(data, productIdHint);
  } catch (e) {
    const err = classifyPlayError(e);
    if (err.kind !== "invalid" || !productIdHint) throw err;
    try {
      const { data } = await publisher.purchases.subscriptions.get({
        packageName,
        subscriptionId: productIdHint,
        token,
      });
      return fromV1(data, productIdHint);
    } catch (e1) {
      throw classifyPlayError(e1);
    }
  }
}
