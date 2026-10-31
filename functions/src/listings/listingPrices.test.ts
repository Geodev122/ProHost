import { test } from "node:test";
import assert from "node:assert/strict";
import { listingPricing, lowestPriceFor, roomPrice, unitOfStrategy } from "./listingPrices";

// Same cases as SpaceCalculationUtils.lowestPriceFor / findLowestPriceForSubdivision (keep both identical).
test("lowest positive price per strategy, in that strategy's unit", () => {
  assert.deepEqual(lowestPriceFor({ strategyType: "HOURLY", hourly: { cellPrices: { "Mon|9": 25, "Mon|10": 0, "Tue|9": 20 } } }), { unit: "hour", amount: 20 });
  assert.deepEqual(lowestPriceFor({ strategyType: "SHIFT_BASED", shiftBased: { shifts: [{ name: "MORNING", price: 60 }, { name: "EVENING", price: 45 }, { price: 0 }] } }), { unit: "shift", amount: 45 });
  assert.deepEqual(lowestPriceFor({ strategyType: "DAY_BASED", dayBased: { distribution: { Mon: { price: 90 }, Tue: { price: 80 } } } }), { unit: "day", amount: 80 });
  assert.deepEqual(lowestPriceFor({ strategyType: "MONTHLY", monthly: { rateUsd: 400 } }), { unit: "month", amount: 400 });
  assert.equal(lowestPriceFor({ strategyType: "HOURLY", hourly: { cellPrices: {} } }), null);
  assert.equal(lowestPriceFor(undefined), null);
});

test("per-attendee rooms price per person from their tiers (slot prices are markers)", () => {
  const sub = { pricingMode: "PER_ATTENDEE", pricing: { strategyType: "HOURLY", hourly: { cellPrices: { "Mon|9": 1 } } },
    attendeeTiers: [{ pricePerAttendeeUsd: 12, minAttendees: 1 }, { pricePerAttendeeUsd: 9, minAttendees: 10 }, { pricePerAttendeeUsd: 5, isEnabled: false }] };
  assert.deepEqual(roomPrice(sub), { unit: "person", amount: 9 });
  assert.deepEqual(roomPrice({ pricing: { strategyType: "DAY_BASED", dayBased: { distribution: { Mon: { price: 70 } } } } }), { unit: "day", amount: 70 });
  // a monthly room can't be per attendee
  assert.deepEqual(roomPrice({ pricingMode: "PER_ATTENDEE", pricing: { strategyType: "MONTHLY", monthly: { rateUsd: 300 } } }), { unit: "month", amount: 300 });
});

test("units and legacy listings", () => {
  assert.equal(unitOfStrategy("SHIFT_BASED"), "shift"); assert.equal(unitOfStrategy(undefined), "month");
  assert.equal(listingPricing({ pricing: { strategyType: "HOURLY" } }).strategyType, "HOURLY");
  assert.equal(listingPricing({ rentalFormulas: [{ type: "HOURLY", rateUsd: 30 }] }).strategyType, "HOURLY");
  assert.deepEqual(listingPricing({}), {});
});
