import { test } from "node:test";
import * as assert from "node:assert/strict";
import { isPerAttendee, quote, minAttendees, maxAttendees, tiersFor, describeBooking } from "./attendeePricing";

const tiers = [
  { id: "T2", name: "Large", pricePerAttendeeUsd: 6, minAttendees: 21, maxAttendees: 60 },
  { id: "T1", name: "Small", pricePerAttendeeUsd: 8, minAttendees: 1, maxAttendees: 20 },
];
const room = { pricingMode: "PER_ATTENDEE", pricing: { strategyType: "HOURLY" }, capacity: 50, minAttendees: 5, attendeeTiers: tiers };

test("per-attendee needs the mode and a non-monthly strategy", () => {
  assert.equal(isPerAttendee(room), true);
  assert.equal(isPerAttendee({ ...room, pricing: { strategyType: "MONTHLY" } }), false);
  assert.equal(isPerAttendee({ ...room, pricingMode: "STRATEGY_BASED" }), false);
  assert.equal(isPerAttendee(undefined), false);
});

test("one per-person price for the whole booking, from the matching tier", () => {
  assert.deepEqual(quote(room, 10)?.totalUsd, 80);
  assert.equal(quote(room, 20)?.tier.id, "T1");
  assert.equal(quote(room, 21)?.tier.id, "T2");
  assert.equal(quote(room, 30)?.totalUsd, 180);
});

test("limits: room min, capacity cap, tier coverage, integers only", () => {
  assert.equal(minAttendees(room, tiersFor(room)), 5);
  assert.equal(maxAttendees(room, tiersFor(room)), 50);
  assert.equal(quote(room, 4), null);
  assert.equal(quote(room, 51), null);
  assert.equal(quote(room, 10.5), null);
  const gap = { ...room, capacity: null, minAttendees: null, attendeeTiers: [{ pricePerAttendeeUsd: 5, minAttendees: 1, maxAttendees: 10 }, { pricePerAttendeeUsd: 4, minAttendees: 20, maxAttendees: null }] };
  assert.equal(quote(gap, 15), null);
  assert.equal(quote(gap, 500)?.totalUsd, 2000);
  assert.equal(maxAttendees(gap, tiersFor(gap)), null);
});

test("unpriced or disabled tiers are ignored; admin templates only as fallback", () => {
  const legacy = { ...room, attendeeTiers: [{ pricePerAttendeeUsd: 0, minAttendees: 1 }, { pricePerAttendeeUsd: 9, minAttendees: 1, isEnabled: false }] };
  assert.equal(quote(legacy, 10), null);
  assert.equal(quote(legacy, 10, [{ pricePerAttendeeUsd: 3, minAttendees: 1 }])?.totalUsd, 30);
  assert.equal(quote(room, 10, [{ pricePerAttendeeUsd: 3, minAttendees: 1 }])?.totalUsd, 80);
});

test("booking summary text", () => {
  assert.equal(describeBooking({ attendeeCount: 20, attendeePackagePriceUsd: 8, attendeePackageName: "Small" }), "20 attendees × $8/person (Small)");
  assert.equal(describeBooking({ attendeeCount: 0 }), null);
});
