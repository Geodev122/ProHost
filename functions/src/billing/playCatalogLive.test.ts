import { test } from "node:test";
import assert from "node:assert/strict";
import { describeTargeting, formatMoney, summarizeBasePlan, summarizeOffer } from "./playCatalogLive";

test("formatMoney renders units + nanos", () => {
  assert.equal(formatMoney({ currencyCode: "USD", units: "4", nanos: 990000000 }), "4.99 USD");
  assert.equal(formatMoney({ currencyCode: "USD", units: "49" }), "49.00 USD");
  assert.equal(formatMoney(undefined), null);
});

test("eligibility is described the way Play Console words it", () => {
  assert.equal(describeTargeting(undefined), "developer determined (app decides; always returned)");
  assert.equal(describeTargeting({ acquisitionRule: { scope: { thisSubscription: {} } } }), "new customers: never had this subscription");
  assert.equal(describeTargeting({ acquisitionRule: { scope: { anySubscriptionInApp: {} } } }), "new customers: never had any subscription in this app");
  assert.match(describeTargeting({ upgradeRule: { scope: { thisSubscription: {} }, oncePerUser: true } }), /^upgrade: .*once per user$/);
});

test("offer phases summarise free, percent, absolute and fixed prices per region", () => {
  const o = summarizeOffer({
    offerId: "launch-50", state: "ACTIVE", offerTags: [{ tag: "launch" }],
    targeting: { acquisitionRule: { scope: { thisSubscription: {} } } },
    regionalConfigs: [{ regionCode: "LB", newSubscriberAvailability: true }],
    phases: [
      { duration: "P1M", recurrenceCount: 1, regionalConfigs: [{ regionCode: "LB", relativeDiscount: 0.5 }, { regionCode: "US", free: {} }] },
      { duration: "P1M", recurrenceCount: 2, regionalConfigs: [{ regionCode: "LB", price: { currencyCode: "USD", units: "3" } }] },
    ],
  });
  assert.equal(o.offerId, "launch-50");
  assert.deepEqual(o.tags, ["launch"]);
  assert.deepEqual(o.availableIn, { LB: true, US: false });
  assert.deepEqual(o.phases[0].pricing, { LB: "-50%", US: "free" });
  assert.deepEqual(o.phases[1], { duration: "P1M", recurrenceCount: 2, pricing: { LB: "3.00 USD", US: "n/a" } });
});

test("base plan summary carries period, regional prices and its offers", () => {
  const bp = summarizeBasePlan({
    basePlanId: "pro-yearly", state: "ACTIVE", autoRenewingBasePlanType: { billingPeriodDuration: "P1Y" },
    regionalConfigs: [{ regionCode: "LB", price: { currencyCode: "USD", units: "99" }, newSubscriberAvailability: true }],
  }, []);
  assert.equal(bp.period, "P1Y");
  assert.deepEqual(bp.prices, { LB: "99.00 USD", US: "n/a" });
  assert.deepEqual(bp.availableIn, { LB: true, US: false });
});
