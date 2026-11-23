/**
 * Maintenance `unlimitedprice` (owner, 23 Nov 2026: "double the price of Unlimited on both stores"): sets the Unlimited
 * subscription (`package_pro_mrr`) live monthly base plan to $17.99 and the yearly one to $159.99, every other country at
 * Play's own conversion (`convertRegionPrices`). Offers whose phases carry an absolute price (the yearly "50 % off the
 * first year") are re-priced in the same proportion, so the discount stays 50 %; free trials and relative discounts
 * follow the base price by themselves. Play keeps current subscribers on the price they pay (their legacy price cohort)
 * until a price migration is started in Play Console, which this task never does. Dry run unless `apply`; idempotent.
 */
import type { androidpublisher_v3 } from "googleapis";
import { PACKAGE_NAME, getPlayPublisher } from "./billingHelpers";
import { PLAY_PRODUCT_ID } from "./playCatalog";
import { liveMonthlyPlan } from "./playOfferFix";

type BasePlan = androidpublisher_v3.Schema$BasePlan;
type Money = androidpublisher_v3.Schema$Money;
type Offer = androidpublisher_v3.Schema$SubscriptionOffer;
type Converted = Record<string, { price?: Money | null }>;

/** New USD prices (owner's choice); every other currency is Play's conversion. */
export const UNLIMITED_PRICES = { monthly: usd("17.99"), yearly: usd("159.99") };

export function usd(text: string): Money {
  const [units, cents = "0"] = text.split(".");
  return { currencyCode: "USD", units, nanos: Number(cents.padEnd(2, "0").slice(0, 2)) * 10_000_000 };
}

export function amount(m?: Money | null): number {
  return Number(m?.units ?? 0) + Number(m?.nanos ?? 0) / 1e9;
}

/** Pure: [plan] with each region's price replaced by Play's conversion (regions Play didn't convert keep theirs). */
export function repricedBasePlan(plan: BasePlan, converted: Converted, other: { usdPrice?: Money | null; eurPrice?: Money | null } | null): BasePlan {
  return {
    ...plan,
    regionalConfigs: (plan.regionalConfigs ?? []).map((r) => {
      const price = r.regionCode ? converted[r.regionCode]?.price : null;
      return price ? { ...r, price } : r;
    }),
    ...(plan.otherRegionsConfig && other?.usdPrice && other?.eurPrice
      ? { otherRegionsConfig: { ...plan.otherRegionsConfig, usdPrice: other.usdPrice, eurPrice: other.eurPrice } }
      : {}),
  };
}

/** Pure: the US price of an absolute-price phase scaled by [factor], keeping a ".99" ending ($39.99 × 2 → $79.99). */
export function scaledUsd(m: Money, factor: number): Money {
  const raw = amount(m) * factor;
  const endsIn99 = Math.round(amount(m) * 100) % 100 === 99;
  const cents = endsIn99 ? Math.round(raw) * 100 - 1 : Math.round(raw * 100);
  return { currencyCode: "USD", units: String(Math.floor(cents / 100)), nanos: (cents % 100) * 10_000_000 };
}

export interface PriceStep { step: string; action: string; result: string }

const usPrice = (bp: BasePlan) => bp.regionalConfigs?.find((r) => r.regionCode === "US")?.price ?? null;
const fmt = (m?: Money | null) => (m ? `${m.currencyCode ?? ""} ${amount(m).toFixed(2)}` : "?");

export async function playUnlimitedPrice(apply: boolean): Promise<{ apply: boolean; steps: PriceStep[] }> {
  const publisher = await getPlayPublisher();
  const steps: PriceStep[] = [];
  const sub = (await publisher.monetization.subscriptions.get({ packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID })).data;
  const plans = sub.basePlans ?? [];
  const monthly = liveMonthlyPlan(plans);
  const yearly = plans.find((bp) => bp.state === "ACTIVE" && bp.autoRenewingBasePlanType?.billingPeriodDuration === "P1Y") ?? null;
  const targets: Array<[BasePlan | null, Money, string]> = [[monthly, UNLIMITED_PRICES.monthly, "monthly"], [yearly, UNLIMITED_PRICES.yearly, "yearly"]];

  let regionsVersion: string | undefined;
  const factors: Record<string, number> = {};
  const updated: BasePlan[] = plans.map((bp) => ({ ...bp }));
  let changed = false;
  for (const [plan, price, label] of targets) {
    if (!plan?.basePlanId) { steps.push({ step: label, action: "none", result: "no ACTIVE base plan" }); continue; }
    const now = usPrice(plan);
    if (now && Math.abs(amount(now) - amount(price)) < 0.005) {
      steps.push({ step: `basePlan ${plan.basePlanId}`, action: "none", result: `already ${fmt(now)}` });
      continue;
    }
    const conv = (await publisher.monetization.convertRegionPrices({ packageName: PACKAGE_NAME, requestBody: { price } })).data;
    regionsVersion = regionsVersion ?? (conv as { regionVersion?: { version?: string | null } }).regionVersion?.version ?? undefined;
    const next = repricedBasePlan(plan, (conv.convertedRegionPrices ?? {}) as Converted, conv.convertedOtherRegionsPrice ?? null);
    const i = updated.findIndex((bp) => bp.basePlanId === plan.basePlanId);
    updated[i] = { ...next, state: undefined };
    factors[plan.basePlanId] = now ? amount(price) / amount(now) : 1;
    changed = true;
    steps.push({ step: `basePlan ${plan.basePlanId}`, action: apply ? "update" : "plan", result: `US ${fmt(now)} → ${fmt(price)}, ${next.regionalConfigs?.length ?? 0} regions (current subscribers keep their price)` });
  }

  // Offers on the re-priced base plans: absolute-price phases scale with the base price.
  const offerUpdates: Array<{ offer: Offer; phases: Offer["phases"] }> = [];
  for (const basePlanId of Object.keys(factors)) {
    const offers = (await publisher.monetization.subscriptions.basePlans.offers.list({
      packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId, pageSize: 100,
    })).data.subscriptionOffers ?? [];
    for (const o of offers) {
      const phases = o.phases ?? [];
      const absolute = phases.some((ph) => (ph.regionalConfigs ?? []).some((r) => r.price));
      if (!absolute) { steps.push({ step: `offer ${basePlanId}/${o.offerId}`, action: "none", result: "free or relative discount: follows the base price" }); continue; }
      const nextPhases: Offer["phases"] = [];
      for (const ph of phases) {
        const us = ph.regionalConfigs?.find((r) => r.regionCode === "US")?.price;
        if (!us) { nextPhases.push(ph); continue; }
        const target = scaledUsd(us, factors[basePlanId]);
        const conv = (await publisher.monetization.convertRegionPrices({ packageName: PACKAGE_NAME, requestBody: { price: target } })).data;
        const converted = (conv.convertedRegionPrices ?? {}) as Converted;
        nextPhases.push({
          ...ph,
          regionalConfigs: (ph.regionalConfigs ?? []).map((r) => {
            const price = r.price && r.regionCode ? converted[r.regionCode]?.price : null;
            return price ? { ...r, price } : r;
          }),
          ...(ph.otherRegionsConfig?.otherRegionsPrices?.usdPrice && conv.convertedOtherRegionsPrice?.usdPrice && conv.convertedOtherRegionsPrice?.eurPrice
            ? { otherRegionsConfig: { ...ph.otherRegionsConfig, otherRegionsPrices: { usdPrice: conv.convertedOtherRegionsPrice.usdPrice, eurPrice: conv.convertedOtherRegionsPrice.eurPrice } } }
            : {}),
        });
        steps.push({ step: `offer ${basePlanId}/${o.offerId}`, action: apply ? "update" : "plan", result: `phase ${ph.duration}: US ${fmt(us)} → ${fmt(target)}` });
      }
      offerUpdates.push({ offer: o, phases: nextPhases });
    }
  }

  if (!apply || !changed) return { apply, steps };
  const version = regionsVersion ?? "2025/03";
  await publisher.monetization.subscriptions.patch({
    packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, updateMask: "basePlans", "regionsVersion.version": version,
    requestBody: { ...sub, basePlans: updated },
  });
  for (const { offer, phases } of offerUpdates) {
    await publisher.monetization.subscriptions.basePlans.offers.patch({
      packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: offer.basePlanId ?? "", offerId: offer.offerId ?? "",
      updateMask: "phases", "regionsVersion.version": version, requestBody: { ...offer, phases, state: undefined },
    });
  }
  steps.push({ step: "applied", action: "patch", result: `regions version ${version}` });
  return { apply, steps };
}
