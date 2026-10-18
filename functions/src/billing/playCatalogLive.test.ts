import { test } from "node:test";
import assert from "node:assert/strict";
import { describeTargeting, formatMoney, lintOffers, LiveBasePlan, periodMonths, phaseDiscountPercent, summarizeBasePlan, summarizeOffer } from "./playCatalogLive";

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

test("period months and phase discounts", () => {
  assert.equal(periodMonths("P1M"), 1);
  assert.equal(periodMonths("P1Y"), 12);
  assert.ok(Math.abs(periodMonths("P7D") - 0.23) < 0.01);
  assert.equal(phaseDiscountPercent("free", "9.99 USD"), 100);
  assert.equal(phaseDiscountPercent("-50%", "9.99 USD"), 50);
  assert.equal(phaseDiscountPercent("4.99 USD", "9.99 USD"), 50);
  assert.equal(phaseDiscountPercent("-5.00 USD", "10.00 USD"), 50);
  assert.equal(phaseDiscountPercent("n/a", "9.99 USD"), null);
});

const plan = (id: string, period: string, offers: LiveBasePlan["offers"]): LiveBasePlan => ({
  basePlanId: id, state: "ACTIVE", period, prices: { LB: "9.99 USD", US: "9.99 USD" }, availableIn: { LB: true, US: true }, regionCount: 2, offers,
});
const offer = (offerId: string, tags: string[], phases: LiveBasePlan["offers"][number]["phases"]) => ({
  offerId, state: "ACTIVE", eligibility: "new customers", tags, phases, availableIn: { LB: true, US: true }, regionCount: 2, otherRegions: false,
});
const NOW = Date.parse("2026-10-14T12:00:00Z");

const FREE4 = [{ duration: "P4M", recurrenceCount: 1, pricing: { LB: "free", US: "free" } }];
const HALF_YEAR = [{ duration: "P1Y", recurrenceCount: 1, pricing: { LB: "-50%", US: "-50%" } }];

test("lintOffers is quiet for the live catalog: 4 months free monthly, 50% off year one, one promo", () => {
  const warnings = lintOffers([
    plan("pro-monthly1", "P1M", [
      offer("free-4-months", [], FREE4),
      offer("prohost2026", ["promo", "code-prohost2026", "ends-20261231"], [{ duration: "P1M", recurrenceCount: 1, pricing: { LB: "free", US: "free" } }]),
    ]),
    plan("pro-yearly", "P1Y", [offer("launch-50-first-year", [], HALF_YEAR)]),
  ], NOW);
  assert.deepEqual(warnings, []);
});

test("lintOffers flags the 99%-off slip, broken promos and a free phase beyond 4 months", () => {
  const warnings = lintOffers([
    plan("pro-monthly1", "P1M", [
      offer("slip", [], [{ duration: "P2M", recurrenceCount: 1, pricing: { LB: "-99%", US: "-99%" } }]),
      offer("old", ["promo", "ends-20260101"], [{ duration: "P1M", recurrenceCount: 1, pricing: { LB: "free", US: "free" } }]),
      offer("long", ["promo", "code-x1"], [{ duration: "P6M", recurrenceCount: 1, pricing: { LB: "free", US: "free" } }]),
    ]),
  ], NOW);
  const all = warnings.join("\n");
  assert.match(all, /slip: phase 1 is 99% off/);
  assert.match(all, /old: promo offer without a code-/);
  assert.match(all, /old: promo ended \(ends-20260101\)/);
  assert.match(all, /long: promo offer without an ends-/);
  assert.match(all, /long: phase 1 is free for 6\.0 months/);
  assert.match(all, /more than one ACTIVE promo offer/);
});

test("lintOffers warns when Play no longer matches what the website promises", () => {
  const all = lintOffers([
    plan("pro-monthly1", "P1M", [offer("short", [], [{ duration: "P2M", recurrenceCount: 1, pricing: { LB: "free", US: "free" } }])]),
    plan("pro-yearly", "P1Y", [offer("thirty", [], [{ duration: "P1Y", recurrenceCount: 1, pricing: { LB: "-30%", US: "-30%" } }])]),
  ], NOW).join("\n");
  assert.match(all, /website promises the first 4 months free on the monthly plan, but pro-monthly1/);
  assert.match(all, /website promises 50% off the first year on the yearly plan, but pro-yearly/);
});

test("lintOffers flags two ACTIVE base plans with the same period (a renamed plan left active)", () => {
  const warnings = lintOffers([
    plan("pro-montly", "P1M", [offer("free-4-months", [], FREE4)]),
    plan("pro-monthly1", "P1M", [offer("free-4-months", [], FREE4)]),
    plan("pro-yearly", "P1Y", [offer("launch-50-first-year", [], HALF_YEAR)]),
  ], NOW);
  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /2 ACTIVE base plans bill every P1M \(pro-montly, pro-monthly1\)/);
});
