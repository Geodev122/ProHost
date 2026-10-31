import { test } from "node:test";
import assert from "node:assert/strict";
import { copyOfferFor, freePhaseFrom, isAnyAppScope, isNearlyFreeClaimPhase, liveMonthlyPlan, withoutRegion } from "./playOfferFix";

test("liveMonthlyPlan picks the single ACTIVE monthly base plan", () => {
  const plans = [
    { basePlanId: "pro-monthly", state: "INACTIVE", autoRenewingBasePlanType: { billingPeriodDuration: "P1M" } },
    { basePlanId: "pro-monthly1", state: "ACTIVE", autoRenewingBasePlanType: { billingPeriodDuration: "P1M" } },
    { basePlanId: "pro-yearly", state: "ACTIVE", autoRenewingBasePlanType: { billingPeriodDuration: "P1Y" } },
  ];
  assert.equal(liveMonthlyPlan(plans)?.basePlanId, "pro-monthly1");
  assert.equal(liveMonthlyPlan([...plans, { ...plans[1], basePlanId: "dup" }]), null);
});

test("copyOfferFor keeps phases, tags, regions and targeting, drops state", () => {
  const src = {
    packageName: "p", productId: "package_pro_mrr", basePlanId: "pro-monthly", offerId: "prohost2026", state: "INACTIVE",
    phases: [{ duration: "P4M", recurrenceCount: 1 }], offerTags: [{ tag: "promo" }], regionalConfigs: [{ regionCode: "LB" }],
    targeting: { acquisitionRule: { scope: { anySubscriptionInApp: {} } } },
  };
  const copy = copyOfferFor(src, "pro-monthly1", "prohost2026");
  assert.equal(copy.basePlanId, "pro-monthly1");
  assert.equal(copy.state, undefined);
  assert.deepEqual(copy.phases, src.phases);
  assert.deepEqual(copy.offerTags, src.offerTags);
  assert.ok(isAnyAppScope(copy));
  assert.ok(!isAnyAppScope({ targeting: { acquisitionRule: { scope: { thisSubscription: {} } } } }));
});

test("a 99%-off claim-length phase is turned into the promised free phase; real discounts are left alone", () => {
  const nearly = { duration: "P4M", recurrenceCount: 1, regionalConfigs: [{ regionCode: "LB", relativeDiscount: 0.99 }, { regionCode: "US", relativeDiscount: 0.99 }], otherRegionsConfig: { relativeDiscount: 0.99 } };
  assert.ok(isNearlyFreeClaimPhase(nearly));
  assert.ok(!isNearlyFreeClaimPhase({ ...nearly, regionalConfigs: [{ regionCode: "LB", free: {} }] }));
  assert.ok(!isNearlyFreeClaimPhase({ ...nearly, regionalConfigs: [{ regionCode: "LB", relativeDiscount: 0.5 }] }));
  assert.ok(!isNearlyFreeClaimPhase({ ...nearly, duration: "P2W" }), "shorter than the promised free month");
  const free = freePhaseFrom(nearly);
  assert.equal(free.duration, "P4M");
  assert.deepEqual(free.regionalConfigs, [{ regionCode: "LB", free: {} }, { regionCode: "US", free: {} }]);
  assert.deepEqual(free.otherRegionsConfig, { free: {} });
});

test("withoutRegion drops a region Play no longer bills from the offer and every phase", () => {
  const o = withoutRegion({
    regionalConfigs: [{ regionCode: "LB" }, { regionCode: "ET" }],
    phases: [{ duration: "P4M", regionalConfigs: [{ regionCode: "LB", free: {} }, { regionCode: "ET", free: {} }] }],
  }, "ET");
  assert.deepEqual(o.regionalConfigs, [{ regionCode: "LB" }]);
  assert.deepEqual(o.phases?.[0].regionalConfigs, [{ regionCode: "LB", free: {} }]);
});
