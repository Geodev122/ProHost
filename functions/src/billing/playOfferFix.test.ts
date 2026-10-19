import { test } from "node:test";
import assert from "node:assert/strict";
import { copyOfferFor, isAnyAppScope, liveMonthlyPlan } from "./playOfferFix";

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
