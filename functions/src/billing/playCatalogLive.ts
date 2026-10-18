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
import { WEBSITE_OFFER_CLAIMS } from "./offerClaims";

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

/** ISO 8601 period → months (P1M = 1, P1Y = 12, P7D ≈ 0.23); 0 when unparseable. */
export function periodMonths(iso: string | null | undefined): number {
  const m = /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$/.exec(iso ?? "");
  if (!m) return 0;
  const [, y, mo, w, d] = m.map((x) => Number(x ?? 0));
  return y * 12 + mo + (w * 7 + d) / 30.4375;
}

/** Percent off the base price for one phase in one region; null when it can't be told. */
export function phaseDiscountPercent(pricing: string, basePrice: string): number | null {
  if (pricing === "free") return 100;
  const pct = /^-(\d+)%$/.exec(pricing);
  if (pct) return Number(pct[1]);
  const amount = (s: string) => { const x = /^-?(\d+(?:\.\d+)?) ([A-Z]{3})$/.exec(s); return x ? { v: Number(x[1]), cur: x[2] } : null; };
  const base = amount(basePrice);
  const off = /^-/.test(pricing) ? amount(pricing) : null;
  const fixed = off ? null : amount(pricing);
  if (!base || base.v <= 0) return null;
  if (off && off.cur === base.cur) return Math.round((off.v / base.v) * 100);
  if (fixed && fixed.cur === base.cur) return Math.round((1 - fixed.v / base.v) * 100);
  return null;
}

/** The Play offer tags that configure the app's promo card (see the app's PlayPromo). */
export const PROMO_TAG = "promo";

/**
 * Pure: strict-Play sanity checks on the live catalog, so a mistake in Play Console (the source of truth
 * for every price, offer and promo) is caught by the Maintenance task `playcatalog` instead of by buyers.
 */
export function lintOffers(basePlans: LiveBasePlan[], nowMillis: number): string[] {
  const out: string[] = [];
  const promos: string[] = [];
  for (const bp of basePlans) {
    if (bp.state === "ACTIVE" && bp.prices.LB === "n/a") out.push(`${bp.basePlanId}: ACTIVE but has no Lebanon (LB) price`);
    for (const o of bp.offers) {
      if (o.state !== "ACTIVE") continue;
      const where = `${bp.basePlanId}/${o.offerId}`;
      o.phases.forEach((p, i) => {
        for (const region of AUDIT_REGIONS) {
          const pct = phaseDiscountPercent(p.pricing[region], bp.prices[region]);
          const months = periodMonths(p.duration) * p.recurrenceCount;
          if (pct === 100 && months > WEBSITE_OFFER_CLAIMS.monthlyFreeMonths) out.push(`${where}: phase ${i + 1} is free for ${months.toFixed(1)} months in ${region}`);
          else if (pct !== null && pct >= 90 && pct < 100) out.push(`${where}: phase ${i + 1} is ${pct}% off for ${p.recurrenceCount}x${p.duration} in ${region} — check the discount`);
        }
      });
      const tags = o.tags.map((t) => t.toLowerCase());
      if (!tags.includes(PROMO_TAG)) continue;
      promos.push(where);
      if (!tags.some((t) => /^code-[a-z0-9]+$/.test(t))) out.push(`${where}: promo offer without a code-… tag (the app hides it)`);
      const ends = tags.find((t) => t.startsWith("ends-"));
      const m = ends ? /^ends-(\d{4})(\d{2})(\d{2})$/.exec(ends) : null;
      if (!ends) out.push(`${where}: promo offer without an ends-YYYYMMDD tag (no countdown)`);
      else if (!m) out.push(`${where}: malformed ${ends} (use ends-YYYYMMDD)`);
      // End of that day in Beirut; +02:00 never ends it early.
      else if (Date.parse(`${m[1]}-${m[2]}-${m[3]}T23:59:59+02:00`) < nowMillis) {
        out.push(`${where}: promo ended (${ends}) but the offer is still ACTIVE — deactivate it in Play Console`);
      }
      if (!o.phases.some((p) => AUDIT_REGIONS.some((r) => (phaseDiscountPercent(p.pricing[r], bp.prices[r]) ?? 0) > 0))) {
        out.push(`${where}: promo offer has no free or discounted phase`);
      }
      if (bp.state !== "ACTIVE") out.push(`${where}: promo offer on a ${bp.state} base plan`);
    }
  }
  const byPeriod = new Map<string, string[]>();
  for (const bp of basePlans.filter((b) => b.state === "ACTIVE" && b.period)) {
    byPeriod.set(bp.period as string, [...(byPeriod.get(bp.period as string) ?? []), bp.basePlanId]);
  }
  for (const [period, ids] of byPeriod) {
    if (ids.length > 1) out.push(`${ids.length} ACTIVE base plans bill every ${period} (${ids.join(", ")}): deactivate the retired one so buyers see one plan`);
  }
  if (promos.length > 1) out.push(`more than one ACTIVE promo offer (${promos.join(", ")}): the app shows only the first`);
  // The website, email templates and media (offerClaims.ts) promise: monthly = first N months free, yearly = X% off year one.
  for (const bp of basePlans.filter((b) => b.state === "ACTIVE")) {
    const kind = periodMonths(bp.period) >= 12 ? "yearly" : "monthly";
    const live = bp.offers.filter((o) => o.state === "ACTIVE" && !o.tags.map((t) => t.toLowerCase()).includes(PROMO_TAG) && o.phases.length > 0);
    const kept = live.some((o) => {
      const p = o.phases[0];
      const pct = phaseDiscountPercent(p.pricing.LB, bp.prices.LB);
      const months = periodMonths(p.duration) * p.recurrenceCount;
      return kind === "monthly"
        ? pct === 100 && months >= WEBSITE_OFFER_CLAIMS.monthlyFreeMonths
        : pct === WEBSITE_OFFER_CLAIMS.yearlyDiscountPercent && months >= 12;
    });
    if (!kept) {
      out.push(kind === "monthly"
        ? `website promises the first ${WEBSITE_OFFER_CLAIMS.monthlyFreeMonths} months free on the monthly plan, but ${bp.basePlanId} has no ACTIVE offer with that free phase in LB`
        : `website promises ${WEBSITE_OFFER_CLAIMS.yearlyDiscountPercent}% off the first year on the yearly plan, but ${bp.basePlanId} has no ACTIVE offer with that discount in LB`);
    }
  }
  return out;
}
