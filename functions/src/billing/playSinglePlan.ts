/**
 * Maintenance `singleplan` (owner, 22 Nov 2026): creates the Google Play subscription of the single-listing tier,
 * "ProHost Premium · 1 listing" (`package_single_mrr`), with a monthly ($4.49) and a yearly ($44.99) base plan, sold in
 * the same countries as Unlimited. Prices per country come from Play's own conversion (`convertRegionPrices`), the
 * renewal settings (grace period, resubscribe, proration) are copied from Unlimited's live monthly plan. Dry run unless
 * `apply`; an existing product is never overwritten (only its inactive base plans are activated).
 */
import type { androidpublisher_v3 } from "googleapis";
import { PACKAGE_NAME, getPlayPublisher } from "./billingHelpers";
import { PLAY_PRODUCT_ID, PLAY_SINGLE_PRODUCT_ID, SINGLE_BASE_PLAN_MONTHLY, SINGLE_BASE_PLAN_YEARLY } from "./playCatalog";
import { liveMonthlyPlan } from "./playOfferFix";

type BasePlan = androidpublisher_v3.Schema$BasePlan;
type Money = androidpublisher_v3.Schema$Money;

/** The tier's prices in USD (owner's choice); every other currency is Play's conversion of these. */
export const SINGLE_PLAN_PRICES: Array<{ basePlanId: string; period: "P1M" | "P1Y"; usd: Money }> = [
  { basePlanId: SINGLE_BASE_PLAN_MONTHLY, period: "P1M", usd: { currencyCode: "USD", units: "4", nanos: 490_000_000 } },
  { basePlanId: SINGLE_BASE_PLAN_YEARLY, period: "P1Y", usd: { currencyCode: "USD", units: "44", nanos: 990_000_000 } },
];

/** Play's subscription listing (title ≤ 55, benefits ≤ 4 × 40, description ≤ 80 characters). */
export const SINGLE_PLAN_LISTING = {
  title: "ProHost Premium · 1 listing",
  benefits: ["Publish one space", "Receive booking requests", "Manage availability and prices"],
  description: "Pro Host tools for one published listing. Upgrade to Unlimited at any time.",
};

/** The base plan to create from [template] (Unlimited's live monthly plan): its regions, the converted prices. */
export function singleBasePlan(
  template: BasePlan,
  basePlanId: string,
  period: string,
  converted: Record<string, { price?: Money | null }>,
  otherRegions: { usdPrice?: Money | null; eurPrice?: Money | null } | null
): BasePlan {
  const regions = (template.regionalConfigs ?? []).map((r) => r.regionCode).filter((r): r is string => !!r);
  return {
    basePlanId,
    autoRenewingBasePlanType: {
      ...(template.autoRenewingBasePlanType ?? {}),
      billingPeriodDuration: period,
      legacyCompatible: false,
      legacyCompatibleSubscriptionOfferId: undefined,
    },
    regionalConfigs: regions
      .filter((r) => converted[r]?.price)
      .map((r) => ({ regionCode: r, newSubscriberAvailability: true, price: converted[r].price ?? undefined })),
    ...(template.otherRegionsConfig && otherRegions?.usdPrice && otherRegions?.eurPrice
      ? { otherRegionsConfig: { usdPrice: otherRegions.usdPrice, eurPrice: otherRegions.eurPrice, newSubscriberAvailability: true } }
      : {}),
  };
}

export interface SinglePlanStep { step: string; action: string; result: string }

export async function playSinglePlan(apply: boolean, regionsVersion = "2022/02"): Promise<{ apply: boolean; steps: SinglePlanStep[] }> {
  const publisher = await getPlayPublisher();
  const steps: SinglePlanStep[] = [];

  let existing: androidpublisher_v3.Schema$Subscription | null = null;
  try {
    existing = (await publisher.monetization.subscriptions.get({ packageName: PACKAGE_NAME, productId: PLAY_SINGLE_PRODUCT_ID })).data;
  } catch (e) {
    const status = (e as { code?: number; response?: { status?: number } }).response?.status ?? (e as { code?: number }).code;
    if (status !== 404) throw e;
  }

  if (existing) {
    for (const bp of existing.basePlans ?? []) {
      const id = bp.basePlanId ?? "?";
      if (bp.state === "ACTIVE") {
        steps.push({ step: `basePlan ${id}`, action: "none", result: `ACTIVE, ${(bp.regionalConfigs ?? []).length} regions` });
      } else if (apply) {
        await publisher.monetization.subscriptions.basePlans.activate({
          packageName: PACKAGE_NAME, productId: PLAY_SINGLE_PRODUCT_ID, basePlanId: id, requestBody: {},
        });
        steps.push({ step: `basePlan ${id}`, action: "activate", result: `was ${bp.state}, now ACTIVE` });
      } else {
        steps.push({ step: `basePlan ${id}`, action: "plan", result: `${bp.state}: would activate` });
      }
    }
    return { apply, steps };
  }

  const pro = (await publisher.monetization.subscriptions.get({ packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID })).data;
  const template = liveMonthlyPlan(pro.basePlans ?? []);
  if (!template) {
    steps.push({ step: "template", action: "none", result: "Unlimited has no single ACTIVE monthly plan to copy regions from — nothing created" });
    return { apply, steps };
  }

  const basePlans: BasePlan[] = [];
  for (const p of SINGLE_PLAN_PRICES) {
    const conv = (await publisher.monetization.convertRegionPrices({ packageName: PACKAGE_NAME, requestBody: { price: p.usd } })).data;
    const plan = singleBasePlan(
      template, p.basePlanId, p.period,
      (conv.convertedRegionPrices ?? {}) as Record<string, { price?: Money | null }>,
      conv.convertedOtherRegionsPrice ?? null
    );
    basePlans.push(plan);
    const us = plan.regionalConfigs?.find((r) => r.regionCode === "US")?.price;
    steps.push({
      step: `basePlan ${p.basePlanId}`, action: apply ? "create" : "plan",
      result: `${p.period}, ${plan.regionalConfigs?.length ?? 0} regions, US ${us?.units ?? "?"}.${String(us?.nanos ?? 0).padStart(9, "0").slice(0, 2)}`,
    });
  }

  const languages = (pro.listings ?? []).map((l) => l.languageCode).filter((l): l is string => !!l);
  const listings = (languages.length ? languages : ["en-US"]).map((languageCode) => ({ languageCode, ...SINGLE_PLAN_LISTING }));
  steps.push({ step: "listing", action: apply ? "create" : "plan", result: `${listings.map((l) => l.languageCode).join(", ")}: "${SINGLE_PLAN_LISTING.title}"` });

  if (!apply) return { apply, steps };

  await publisher.monetization.subscriptions.create({
    packageName: PACKAGE_NAME,
    productId: PLAY_SINGLE_PRODUCT_ID,
    "regionsVersion.version": regionsVersion,
    requestBody: { packageName: PACKAGE_NAME, productId: PLAY_SINGLE_PRODUCT_ID, listings, basePlans },
  });
  for (const p of SINGLE_PLAN_PRICES) {
    await publisher.monetization.subscriptions.basePlans.activate({
      packageName: PACKAGE_NAME, productId: PLAY_SINGLE_PRODUCT_ID, basePlanId: p.basePlanId, requestBody: {},
    });
  }
  steps.push({ step: "activate", action: "activate", result: `${PLAY_SINGLE_PRODUCT_ID}: both base plans ACTIVE` });
  return { apply, steps };
}
