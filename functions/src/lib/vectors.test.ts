/**
 * Runs the shared test vectors in tests/vectors/*.json (scripts/migration/export_vectors.js writes them from these
 * implementations). The Kotlin app runs the same files in VectorsTest.kt, so Kotlin and TypeScript cannot drift
 * silently. If a test here fails after an intended TypeScript change, regenerate the vectors
 * (`node scripts/migration/export_vectors.js`) AND check the Kotlin side before committing.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs";
import * as path from "node:path";
import { bookingTerm, hasEnded, termsOverlap } from "../bookings/bookingTerms";
import { lowestPriceFor, roomPrice } from "../listings/listingPrices";
import { pricingFromLegacyFormula } from "../listings/legacyPricing";

// Compiled to functions/lib/lib/vectors.test.js → the repository root is three levels up.
const DIR = path.resolve(__dirname, "..", "..", "..", "tests", "vectors");
const load = (name: string) => JSON.parse(fs.readFileSync(path.join(DIR, name), "utf8"));

test("booking term vectors", () => {
  const file = load("booking-terms.json");
  assert.ok(file.term.length >= 15);
  for (const c of file.term) assert.deepEqual(bookingTerm(c.input), c.expected, c.name);
  for (const c of file.hasEnded) assert.equal(hasEnded(c.input, c.today), c.expected, c.name);
  for (const c of file.termsOverlap) assert.equal(termsOverlap(c.a, c.b), c.expected, c.name);
});

test("listing price vectors", () => {
  const file = load("listing-prices.json");
  for (const c of file.lowestPriceFor) assert.deepEqual(lowestPriceFor(c.pricing), c.expected, c.name);
  for (const c of file.roomPrice) assert.deepEqual(roomPrice(c.sub), c.expected, c.name);
});

type Data = Record<string, unknown>;
const sorted = <T>(o: Record<string, T>) => Object.fromEntries(Object.keys(o).sort().map((k) => [k, o[k]]));

function digest(p: Data): Data {
  const hourly = p.hourly as { cellPrices: Record<string, number> };
  const shiftBased = p.shiftBased as { shifts: Array<Data>; distribution: Record<string, string[]> };
  const dayBased = p.dayBased as { useFacilityHours: boolean; customStartHour: number; customEndHour: number; distribution: Record<string, { price: number }> };
  switch (p.strategyType) {
    case "HOURLY": return { strategyType: "HOURLY", cellPrices: sorted(hourly.cellPrices) };
    case "SHIFT_BASED": return {
      strategyType: "SHIFT_BASED",
      shifts: shiftBased.shifts.map((s) => [s.name, s.isUnavailable, s.price, s.startHour, s.endHour]),
      distribution: sorted(shiftBased.distribution),
    };
    case "DAY_BASED": return {
      strategyType: "DAY_BASED",
      useFacilityHours: dayBased.useFacilityHours,
      customStartHour: dayBased.customStartHour,
      customEndHour: dayBased.customEndHour,
      distribution: sorted(Object.fromEntries(Object.entries(dayBased.distribution).map(([d, v]) => [d, v.price]))),
    };
    default: return { strategyType: "MONTHLY", rateUsd: (p.monthly as { rateUsd: number }).rateUsd };
  }
}

test("legacy pricing vectors", () => {
  for (const c of load("legacy-pricing.json").cases) {
    assert.deepEqual(digest(pricingFromLegacyFormula(c.formula)), c.expected, c.name);
  }
});
