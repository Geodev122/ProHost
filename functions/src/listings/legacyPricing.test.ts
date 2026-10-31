import { test } from "node:test";
import * as assert from "node:assert/strict";
import { pricingFromLegacyFormula } from "./legacyPricing";

test("SHIFT formula → SHIFT_BASED with only the morning shift offered on its days", () => {
  const p = pricingFromLegacyFormula({ type: "SHIFT", rateUsd: 350, daysOfWeek: ["Mon", "Wed"], startHour: "08:00", endHour: "14:00" });
  assert.equal(p.strategyType, "SHIFT_BASED");
  const sb = p.shiftBased as { shifts: Array<Record<string, unknown>>; distribution: Record<string, string[]> };
  assert.deepEqual(sb.shifts.map((s) => [s.name, s.isUnavailable, s.price, s.startHour, s.endHour]), [
    ["MORNING", false, 350, 8, 14], ["MID", true, 350, 8, 14], ["EVENING", true, 350, 8, 14], ["NIGHT", true, 350, 8, 14],
  ]);
  assert.deepEqual(sb.distribution, { Mon: ["MORNING"], Wed: ["MORNING"] });
});

test("HOURLY formula → one cell per day and hour in [start, end)", () => {
  const p = pricingFromLegacyFormula({ type: "HOURLY", rateUsd: 40, daysOfWeek: ["Tue"], startHour: "09:00", endHour: "12:00" });
  assert.deepEqual((p.hourly as { cellPrices: Record<string, number> }).cellPrices, { "Tue|9": 40, "Tue|10": 40, "Tue|11": 40 });
});

test("DAY_PER_WEEK formula → DAY_BASED with custom hours", () => {
  const p = pricingFromLegacyFormula({ type: "DAY_PER_WEEK", rateUsd: 600, daysOfWeek: ["Tue", "Thu"], startHour: "08:00", endHour: "18:00" });
  assert.deepEqual(p.dayBased, {
    useFacilityHours: false, customStartHour: 8, customEndHour: 18,
    distribution: { Tue: { price: 600 }, Thu: { price: 600 } },
  });
});

test("FULL_MONTH / unknown / missing fields fall back like the app", () => {
  assert.equal((pricingFromLegacyFormula({ type: "FULL_MONTH", rateUsd: 450 }).monthly as Record<string, unknown>).rateUsd, 450);
  assert.equal((pricingFromLegacyFormula({}).monthly as Record<string, unknown>).rateUsd, 300);
  assert.equal((pricingFromLegacyFormula(null).monthly as Record<string, unknown>).rateUsd, 0);
});
