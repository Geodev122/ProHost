import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PricingDoc, SlotRef, bookingUses, nextTermStart, priceOf, reprice, unitsFrom, weekdayOf, withSlotPrice, withTierPrice,
} from "./priceChange";

// Mirrored case-for-case in app/src/test/.../PriceChangeTest.kt.
const TODAY = "2026-10-07"; // a Wednesday

const shiftPricing: PricingDoc = {
  strategyType: "SHIFT_BASED",
  shiftBased: {
    shifts: [
      { name: "MORNING", startHour: 8, endHour: 12, price: 20 },
      { name: "EVENING", startHour: 16, endHour: 20, price: 30 },
    ],
    distribution: { Mon: ["MORNING"], Wed: ["MORNING", "EVENING"], Fri: ["EVENING"] },
  },
};
const monthlyPricing: PricingDoc = { strategyType: "MONTHLY", monthly: { rateUsd: 500 } };
const dayPricing: PricingDoc = { strategyType: "DAY_BASED", dayBased: { distribution: { Mon: { price: 50 }, Tue: { price: 60 } } } };
const hourlyPricing: PricingDoc = { strategyType: "HOURLY", hourly: { cellPrices: { "Mon|9": 10, "Mon|10": 10 } } };

test("weekdays and next billing periods", () => {
  assert.equal(weekdayOf(TODAY), "Wed");
  assert.equal(nextTermStart({ scopeId: "s", kind: "SHIFT", key: "MORNING" }, TODAY), "2026-10-12");
  assert.equal(nextTermStart({ scopeId: "s", kind: "SHIFT", key: "MORNING" }, "2026-10-12"), "2026-10-19");
  assert.equal(nextTermStart({ scopeId: "s", kind: "MONTHLY", key: "" }, TODAY), "2026-11-01");
  assert.equal(nextTermStart({ scopeId: "s", kind: "MONTHLY", key: "" }, "2026-12-15"), "2027-01-01");
});

test("prices are read and replaced for exactly one slot", () => {
  const morning: SlotRef = { scopeId: "SUB-1", kind: "SHIFT", key: "MORNING" };
  assert.equal(priceOf(shiftPricing, [], morning), 20);
  const next = withSlotPrice(shiftPricing, morning, 25);
  assert.equal(priceOf(next, [], morning), 25);
  assert.equal(priceOf(next, [], { ...morning, key: "EVENING" }), 30);
  assert.equal(priceOf(shiftPricing, [], morning), 20, "original untouched");
  assert.equal(priceOf(withSlotPrice(monthlyPricing, { scopeId: "x", kind: "MONTHLY", key: "" }, 650), [], { scopeId: "x", kind: "MONTHLY", key: "" }), 650);
  assert.equal(priceOf(withSlotPrice(dayPricing, { scopeId: "x", kind: "DAY", key: "Tue" }, 70), [], { scopeId: "x", kind: "DAY", key: "Tue" }), 70);
  assert.equal(priceOf(withSlotPrice(hourlyPricing, { scopeId: "x", kind: "HOURLY", key: "Mon|9" }, 12), [], { scopeId: "x", kind: "HOURLY", key: "Mon|10" }), 10);
  const tiers = withTierPrice([{ id: "t1", pricePerAttendeeUsd: 5 }, { id: "t2", pricePerAttendeeUsd: 8 }], { scopeId: "x", kind: "TIER", key: "t2" }, 9);
  assert.equal(priceOf(null, tiers, { scopeId: "x", kind: "TIER", key: "t2" }), 9);
  assert.equal(priceOf(null, tiers, { scopeId: "x", kind: "TIER", key: "t1" }), 5);
  assert.equal(priceOf(shiftPricing, [], { scopeId: "x", kind: "SHIFT", key: "NIGHT" }), null);
});

test("bookings match a slot by room, kind, hours and days", () => {
  const morning: SlotRef = { scopeId: "SUB-1", kind: "SHIFT", key: "MORNING" };
  const b = {
    spaceId: "SP", subdivisionId: "SUB-1", formula: { type: "SHIFT", startHour: "08:00", endHour: "12:00" },
    selectedCalendarDates: ["2026-10-12", "2026-10-14"],
  };
  assert.equal(bookingUses(b, "SP", shiftPricing, morning), true);
  assert.equal(bookingUses({ ...b, subdivisionId: "SUB-2" }, "SP", shiftPricing, morning), false);
  assert.equal(bookingUses(b, "SP", shiftPricing, { ...morning, key: "EVENING" }), false);
  // Space without rooms: the scope is the space itself.
  assert.equal(bookingUses({ spaceId: "SP", formula: { type: "FULL_MONTH" } }, "SP", monthlyPricing, { scopeId: "SP", kind: "MONTHLY", key: "" }), true);
  assert.equal(bookingUses({ spaceId: "SP", formula: { type: "HOURLY", startHour: "09:00", endHour: "11:00" }, selectedDays: ["Mon"] }, "SP", hourlyPricing, { scopeId: "SP", kind: "HOURLY", key: "Mon|10" }), true);
  assert.equal(bookingUses({ spaceId: "SP", formula: { type: "HOURLY", startHour: "09:00", endHour: "10:00" }, selectedDays: ["Mon"] }, "SP", hourlyPricing, { scopeId: "SP", kind: "HOURLY", key: "Mon|10" }), false);
  assert.equal(bookingUses({ spaceId: "SP", formula: { type: "DAY_PER_WEEK" }, selectedCalendarDates: ["2026-10-13"] }, "SP", dayPricing, { scopeId: "SP", kind: "DAY", key: "Tue" }), true);
  assert.equal(bookingUses({ spaceId: "SP", formula: { type: "DAY_PER_WEEK" }, selectedCalendarDates: ["2026-10-13"] }, "SP", dayPricing, { scopeId: "SP", kind: "DAY", key: "Mon" }), false);
  assert.equal(bookingUses({ spaceId: "SP", subdivisionId: "R", selectedAttendeePackageId: "t1" }, "SP", null, { scopeId: "R", kind: "TIER", key: "t1" }), true);
});

test("KEEP never changes the total", () => {
  const b = { spaceId: "SP", formula: { type: "FULL_MONTH" }, startDate: "2026-09-01", durationMonths: 6, totalAmountUsd: 3000 };
  assert.deepEqual(reprice(b, monthlyPricing, { scopeId: "SP", kind: "MONTHLY", key: "" }, 500, 600, "KEEP", TODAY),
    { newTotal: 3000, effectiveFrom: "", units: 0 });
});

test("monthly: NOW re-prices from this month, NEXT_TERM from next month", () => {
  const ref: SlotRef = { scopeId: "SP", kind: "MONTHLY", key: "" };
  // Sep..Feb (6 months); today is in October → Oct..Feb = 5 months remain.
  const b = { spaceId: "SP", formula: { type: "FULL_MONTH" }, startDate: "2026-09-01", durationMonths: 6, totalAmountUsd: 3000 };
  assert.equal(unitsFrom(b, monthlyPricing, ref, TODAY), 5);
  assert.deepEqual(reprice(b, monthlyPricing, ref, 500, 600, "NOW", TODAY), { newTotal: 3500, effectiveFrom: TODAY, units: 5 });
  assert.deepEqual(reprice(b, monthlyPricing, ref, 500, 600, "NEXT_TERM", TODAY), { newTotal: 3400, effectiveFrom: "2026-11-01", units: 4 });
  // A pending request that starts later is fully re-priced, from its own start.
  const later = { ...b, startDate: "2027-01-01", durationMonths: 3, totalAmountUsd: 1500 };
  assert.deepEqual(reprice(later, monthlyPricing, ref, 500, 450, "NOW", TODAY), { newTotal: 1350, effectiveFrom: "2027-01-01", units: 3 });
  // Last month of the lease: nothing left for the next term.
  const ending = { ...b, startDate: "2026-10-01", durationMonths: 1, totalAmountUsd: 500 };
  assert.deepEqual(reprice(ending, monthlyPricing, ref, 500, 600, "NEXT_TERM", TODAY), { newTotal: 500, effectiveFrom: "", units: 0 });
});

test("dated shifts: NOW counts remaining dates, NEXT_TERM from next Monday", () => {
  const ref: SlotRef = { scopeId: "SUB-1", kind: "SHIFT", key: "MORNING" };
  const b = {
    spaceId: "SP", subdivisionId: "SUB-1", formula: { type: "SHIFT", startHour: "08:00", endHour: "12:00" },
    // Mon 5 (past), Wed 7 (today), Fri 9 (not a morning day), Mon 12, Wed 14
    selectedCalendarDates: ["2026-10-05", "2026-10-07", "2026-10-09", "2026-10-12", "2026-10-14"],
    totalAmountUsd: 100,
  };
  assert.deepEqual(reprice(b, shiftPricing, ref, 20, 25, "NOW", TODAY), { newTotal: 115, effectiveFrom: TODAY, units: 3 });
  assert.deepEqual(reprice(b, shiftPricing, ref, 20, 25, "NEXT_TERM", TODAY), { newTotal: 110, effectiveFrom: "2026-10-12", units: 2 });
});

test("day-based dates and price decreases never go below zero", () => {
  const ref: SlotRef = { scopeId: "SP", kind: "DAY", key: "Tue" };
  const b = { spaceId: "SP", formula: { type: "DAY_PER_WEEK" }, selectedCalendarDates: ["2026-10-13", "2026-10-20"], totalAmountUsd: 120 };
  assert.deepEqual(reprice(b, dayPricing, ref, 60, 40, "NOW", TODAY), { newTotal: 80, effectiveFrom: "2026-10-13", units: 2 });
  assert.equal(reprice({ ...b, totalAmountUsd: 10 }, dayPricing, ref, 60, 0.5, "NOW", TODAY).newTotal, 0);
});

test("undated units: NOW re-prices, NEXT_TERM waits for renewal", () => {
  const cell: SlotRef = { scopeId: "SP", kind: "HOURLY", key: "Mon|9" };
  const h = { spaceId: "SP", formula: { type: "HOURLY", startHour: "09:00", endHour: "11:00" }, selectedDays: ["Mon"], startDate: "2026-10-01", totalAmountUsd: 20 };
  assert.deepEqual(reprice(h, hourlyPricing, cell, 10, 12.5, "NOW", TODAY), { newTotal: 22.5, effectiveFrom: TODAY, units: 1 });
  assert.deepEqual(reprice(h, hourlyPricing, cell, 10, 12.5, "NEXT_TERM", TODAY), { newTotal: 20, effectiveFrom: "", units: 0 });
  const tier: SlotRef = { scopeId: "R", kind: "TIER", key: "t1" };
  const t = { spaceId: "SP", subdivisionId: "R", selectedAttendeePackageId: "t1", attendeeCount: 4, startDate: "2026-10-20", totalAmountUsd: 40 };
  assert.deepEqual(reprice(t, null, tier, 10, 12, "NOW", TODAY), { newTotal: 48, effectiveFrom: "2026-10-20", units: 4 });
  assert.deepEqual(reprice(t, null, tier, 10, 12, "NEXT_TERM", TODAY), { newTotal: 40, effectiveFrom: "", units: 0 });
});
