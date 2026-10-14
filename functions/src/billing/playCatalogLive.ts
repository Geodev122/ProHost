/**
 * The live ProHost Premium catalog exactly as Google Play holds it: base plans with their
 * regional prices, and every offer with its phases, eligibility (targeting), tags and regional
 * availability. Read with the functions' own service account (the one invited in Play Console).
 * Used by Admin › Packages › Billing health and the Maintenance task `playcatalog`.
 *
 * The app never reads this: prices shown to buyers come only from the Billing SDK. This exists
 * to explain what the SDK will return — e.g. an offer limited to "new customers" is not
 * returned to an account that already had the subscription.
 */
import type { androidpublisher_v3 } from "googleapis";
import { PACKAGE_NAME, getPlayPublisher } from "./billingHelpers";
import { PLAY_PRODUCT_ID } from "./playCatalog";

type Money = androidpublisher_v3.Schema$Money;

/** Regions summarised in detail (Lebanon is ProHost's market; US is Play's reference price). */
export const AUDIT_REGIONS = ["LB", "US"] as const;

export interface LivePhase {
  duration: string;
  recurrenceCount: number;
  /** Per audited region: "free", "-50%", "-$2.00 USD", "4.99 USD" or "n/a" when not configured there. */
  pricing: Record<string, string>;
}

export interface LiveOffer {
  offerId: string;
  state: string;
  eligibility: string;
  tags: string[];
  phases: LivePhase[];
  /** Regions where new subscribers can get this offer (audited regions only), plus the total count. */
  availableIn: Record<string, boolean>;
  regionCount: number;
  otherRegions: boolean;
}

export interface LiveBasePlan {
  basePlanId: string;
  state: string;
  period: string | null;
  prices: Record<string, string>;
  availableIn: Record<string, boolean>;
  regionCount: number;
  offers: LiveOffer[];
}

export function formatMoney(m: Money | undefined | null): string | null {
  if (!m || !m.currencyCode) return null;
  const units = Number(m.units ?? 0);
  const cents = Math.round((m.nanos ?? 0) / 1e7);
  return `${(units + cents / 100).toFixed(2)} ${m.currencyCode}`;
}

/** Plain-language eligibility, as Play Console words it. */
export function describeTargeting(t: androidpublisher_v3.Schema$SubscriptionOfferTargeting | undefined | null): string {
  if (!t) return "developer determined (app decides; always returned)";
  if (t.acquisitionRule) {
    const s = t.acquisitionRule.scope;
    if (s?.thisSubscription) return "new customers: never had this subscription";
    if (s?.anySubscriptionInApp) return "new customers: never had any subscription in this app";
    if (s?.specificSubscriptionInApp) return `new customers: never had ${s.specificSubscriptionInApp}`;
    return "new customers";
  }
  if (t.upgradeRule) {
    const u = t.upgradeRule;
    const from = u.scope?.specificSubscriptionInApp ?? (u.scope?.thisSubscription ? "this subscription" : "any subscription");
    return `upgrade: current subscribers of ${from}${u.billingPeriodDuration ? ` (${u.billingPeriodDuration})` : ""}${u.oncePerUser ? ", once per user" : ""}`;
  }
  return "developer determined (app decides; always returned)";
}

export function summarizePhase(p: androidpublisher_v3.Schema$SubscriptionOfferPhase): LivePhase {
  const pricing: Record<string, string> = {};
  for (const region of AUDIT_REGIONS) {
    const rc = (p.regionalConfigs ?? []).find((c) => c.regionCode === region);
    if (!rc) { pricing[region] = "n/a"; continue; }
    if (rc.free) pricing[region] = "free";
    else if (typeof rc.relativeDiscount === "number") pricing[region] = `-${Math.round(rc.relativeDiscount * 100)}%`;
    else if (rc.absoluteDiscount) pricing[region] = `-${formatMoney(rc.absoluteDiscount)}`;
    else pricing[region] = formatMoney(rc.price) ?? "n/a";
  }
  return { duration: p.duration ?? "?", recurrenceCount: p.recurrenceCount ?? 1, pricing };
}

export function summarizeOffer(o: androidpublisher_v3.Schema$SubscriptionOffer): LiveOffer {
  const availableIn: Record<string, boolean> = {};
  for (const region of AUDIT_REGIONS) {
    availableIn[region] = (o.regionalConfigs ?? []).some((c) => c.regionCode === region && c.newSubscriberAvailability !== false);
  }
  return {
    offerId: o.offerId ?? "?",
    state: o.state ?? "UNKNOWN",
    eligibility: describeTargeting(o.targeting),
    tags: (o.offerTags ?? []).map((t) => t.tag ?? "").filter(Boolean),
    phases: (o.phases ?? []).map(summarizePhase),
    availableIn,
    regionCount: (o.regionalConfigs ?? []).length,
    otherRegions: o.otherRegionsConfig?.otherRegionsNewSubscriberAvailability === true,
  };
}

export function summarizeBasePlan(bp: androidpublisher_v3.Schema$BasePlan, offers: LiveOffer[]): LiveBasePlan {
  const prices: Record<string, string> = {};
  const availableIn: Record<string, boolean> = {};
  for (const region of AUDIT_REGIONS) {
    const rc = (bp.regionalConfigs ?? []).find((c) => c.regionCode === region);
    prices[region] = formatMoney(rc?.price) ?? "n/a";
    availableIn[region] = !!rc && rc.newSubscriberAvailability !== false;
  }
  return {
    basePlanId: bp.basePlanId ?? "",
    state: bp.state ?? "UNKNOWN",
    period: bp.autoRenewingBasePlanType?.billingPeriodDuration ?? bp.prepaidBasePlanType?.billingPeriodDuration ?? null,
    prices,
    availableIn,
    regionCount: (bp.regionalConfigs ?? []).length,
    offers,
  };
}

/** Reads package_pro_mrr and all its offers (any base plan) from the Play Developer API. */
export async function readPlayCatalog(): Promise<{ productId: string; basePlans: LiveBasePlan[]; listingTitle: string | null }> {
  const publisher = await getPlayPublisher();
  const sub = (await publisher.monetization.subscriptions.get({ packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID })).data;
  const offers: androidpublisher_v3.Schema$SubscriptionOffer[] = [];
  let pageToken: string | undefined;
  do {
    const res = await publisher.monetization.subscriptions.basePlans.offers.list({
      packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: "-", pageSize: 100, pageToken,
    });
    offers.push(...(res.data.subscriptionOffers ?? []));
    pageToken = res.data.nextPageToken ?? undefined;
  } while (pageToken);
  return {
    productId: PLAY_PRODUCT_ID,
    listingTitle: sub.listings?.[0]?.title ?? null,
    basePlans: (sub.basePlans ?? []).map((bp) =>
      summarizeBasePlan(bp, offers.filter((o) => o.basePlanId === bp.basePlanId).map(summarizeOffer))),
  };
}
