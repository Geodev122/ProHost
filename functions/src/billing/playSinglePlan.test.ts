import { test } from "node:test";
import * as assert from "node:assert/strict";
import { SINGLE_PLAN_LISTING, SINGLE_PLAN_PRICES, singleBasePlan } from "./playSinglePlan";

const template = {
  basePlanId: "pro-month1",
  state: "ACTIVE",
  autoRenewingBasePlanType: { billingPeriodDuration: "P1M", gracePeriodDuration: "P7D", legacyCompatible: true },
  regionalConfigs: [{ regionCode: "US" }, { regionCode: "LB" }, { regionCode: "XX" }],
  otherRegionsConfig: { newSubscriberAvailability: true },
};

test("the single-listing base plan copies Unlimited's regions and renewal settings with converted prices", () => {
  const usd = { currencyCode: "USD", units: "44", nanos: 990000000 };
  const plan = singleBasePlan(template, "single-yearly", "P1Y", { US: { price: usd }, LB: { price: usd } }, { usdPrice: usd, eurPrice: usd });
  assert.equal(plan.basePlanId, "single-yearly");
  assert.equal(plan.autoRenewingBasePlanType?.billingPeriodDuration, "P1Y");
  assert.equal(plan.autoRenewingBasePlanType?.gracePeriodDuration, "P7D");
  assert.equal(plan.autoRenewingBasePlanType?.legacyCompatible, false);
  assert.deepEqual(plan.regionalConfigs?.map((r) => r.regionCode), ["US", "LB"]); // XX had no converted price
  assert.ok(plan.otherRegionsConfig?.usdPrice);
});

test("prices and listing text match the owner's choice and Play's limits", () => {
  assert.deepEqual(SINGLE_PLAN_PRICES.map((p) => `${p.usd.units}.${String(p.usd.nanos).slice(0, 2)}`), ["4.49", "44.99"]);
  assert.ok(SINGLE_PLAN_LISTING.title.length <= 55);
  assert.ok(SINGLE_PLAN_LISTING.description.length <= 80);
  assert.ok(SINGLE_PLAN_LISTING.benefits.every((b) => b.length <= 40) && SINGLE_PLAN_LISTING.benefits.length <= 4);
});
