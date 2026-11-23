import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { UNLIMITED_PRICES, amount, repricedBasePlan, scaledUsd, usd } from "./playUnlimitedPrice";

describe("playUnlimitedPrice", () => {
  it("new Unlimited prices are double the old ones", () => {
    assert.equal(amount(UNLIMITED_PRICES.monthly), 17.99);
    assert.equal(amount(UNLIMITED_PRICES.yearly), 159.99);
  });

  it("scales an absolute offer price keeping .99", () => {
    assert.equal(amount(scaledUsd(usd("39.99"), 159.99 / 79.99)), 79.99);
    assert.equal(amount(scaledUsd(usd("10.00"), 2)), 20);
  });

  it("replaces converted regions and keeps the others", () => {
    const plan = { basePlanId: "pro-yearly", regionalConfigs: [
      { regionCode: "US", newSubscriberAvailability: true, price: usd("79.99") },
      { regionCode: "LB", newSubscriberAvailability: true, price: usd("79.99") },
    ] };
    const next = repricedBasePlan(plan, { US: { price: usd("159.99") } }, null);
    assert.equal(amount(next.regionalConfigs?.[0].price), 159.99);
    assert.equal(next.regionalConfigs?.[0].newSubscriberAvailability, true);
    assert.equal(amount(next.regionalConfigs?.[1].price), 79.99);
  });
});
