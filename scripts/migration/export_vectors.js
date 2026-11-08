#!/usr/bin/env node
/**
 * Shared test vectors for logic that exists twice (Kotlin app + TypeScript functions).
 *
 *   node scripts/migration/export_vectors.js [--lib <compiled functions lib dir>] [--check]
 *
 * Inputs are curated here; `expected` is computed from the TypeScript implementation (functions/lib after
 * `npm run build`, or any tsc output with the same layout) and written to tests/vectors/*.json. Both sides then
 * run the same file:
 *   - TypeScript: functions/src/lib/vectors.test.ts (recomputes and compares, so a TS change that alters a result
 *     fails until the vectors are regenerated on purpose);
 *   - Kotlin: app/src/test/.../VectorsTest.kt (reads the same JSON).
 * A case may carry `kotlinExpected` when the Kotlin side is KNOWN to differ today. Those are findings recorded in
 * docs/migration/DECISIONS.md ("Known Kotlin/TypeScript divergences"); they are never fixed silently because
 * fixing one changes behaviour (owner rule 1).
 *   --check  fail (exit 1) if the committed files differ from what the implementation produces.
 */
const fs = require("fs");
const path = require("path");

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const opt = (name, dflt) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : dflt; };
const ROOT = path.resolve(__dirname, "..", "..");
const LIB = path.resolve(opt("--lib", path.join(ROOT, "functions", "lib")));
const OUT = path.join(ROOT, "tests", "vectors");

const terms = require(path.join(LIB, "bookings", "bookingTerms.js"));
const prices = require(path.join(LIB, "listings", "listingPrices.js"));
const legacy = require(path.join(LIB, "listings", "legacyPricing.js"));

// ---------------------------------------------------------------- booking terms
const TERM_CASES = [
  ["calendar dates, unsorted", { selectedCalendarDates: ["2026-10-09", "2026-10-02"] }],
  ["single calendar date", { selectedCalendarDates: ["2026-10-31"] }],
  ["invalid calendar dates are ignored", { selectedCalendarDates: ["bad", "2026-10-05", "2026-1-5"] }],
  ["only invalid calendar dates fall back to startDate", { selectedCalendarDates: ["bad"], startDate: "2026-10-01", durationMonths: 2 }],
  ["calendar dates win over startDate", { selectedCalendarDates: ["2026-10-09"], startDate: "2026-01-01", durationMonths: 6 }],
  ["startDate + 3 months", { startDate: "2026-10-01", durationMonths: 3 }],
  ["month-only startDate", { startDate: "2026-11", durationMonths: 1 }],
  ["startDate with a time suffix", { startDate: "2026-10-01T10:00:00Z", durationMonths: 1 }],
  ["startDate with spaces", { startDate: "  2026-10-01  ", durationMonths: 1 }],
  ["durationMonths defaults to 1", { startDate: "2026-10-01" }],
  ["durationMonths 0 counts as 1", { startDate: "2026-10-01", durationMonths: 0 }],
  ["negative durationMonths counts as 1", { startDate: "2026-10-01", durationMonths: -4 }],
  ["year rollover", { startDate: "2026-12-15", durationMonths: 2 }],
  ["leap day is kept", { startDate: "2028-01-29", durationMonths: 1 }],
  ["12 months", { startDate: "2026-03-01", durationMonths: 12 }],
  ["label start date has no term", { startDate: "Next Monday", durationMonths: 1 }],
  ["empty start date has no term", { startDate: "", durationMonths: 1 }],
  ["no fields has no term", {}],
  // Month-end starts: the end date is clamped to the last day of the target month on BOTH sides (server fixed 8 Nov 2026;
  // it used to overflow: 2026-01-31 + 1 month = 2026-03-03 on the server vs 2026-02-28 in the app).
  ["month-end start clamps (Jan 31 + 1 month)", { startDate: "2026-01-31", durationMonths: 1 }],
  ["month-end start clamps (Mar 31 + 1 month)", { startDate: "2026-03-31", durationMonths: 1 }],
  ["Jan 30 + 1 month clamps to Feb 28", { startDate: "2026-01-30", durationMonths: 1 }],
  ["Jan 29 + 1 month clamps to Feb 28 (non-leap)", { startDate: "2026-01-29", durationMonths: 1 }],
  ["leap year: Jan 31 + 1 month is Feb 29", { startDate: "2028-01-31", durationMonths: 1 }],
  ["Aug 31 + 1 month clamps to Sep 30", { startDate: "2026-08-31", durationMonths: 1 }],
  ["Dec 31 + 2 months crosses the year, clamps to Feb 28", { startDate: "2026-12-31", durationMonths: 2 }],
  ["Jan 31 + 12 months keeps the 31st", { startDate: "2026-01-31", durationMonths: 12 }],
  ["Mar 31 + 11 months clamps to Feb 28", { startDate: "2026-03-31", durationMonths: 11 }],
  ["Mar 31 + 13 months clamps to Apr 30", { startDate: "2026-03-31", durationMonths: 13 }],
  ["Oct 31 + 4 months clamps to Feb 28", { startDate: "2026-10-31", durationMonths: 4 }],
];

const ENDED_CASES = [
  ["old booking has ended", { startDate: "2026-01-01", durationMonths: 1 }, "2026-10-07"],
  ["running booking has not ended", { startDate: "2026-10-01", durationMonths: 1 }, "2026-10-07"],
  ["end date is exclusive: ended on the end day", { startDate: "2026-10-01", durationMonths: 1 }, "2026-11-01"],
  ["day before the end: not ended", { startDate: "2026-10-01", durationMonths: 1 }, "2026-10-31"],
  ["month-end start: ended on the clamped end day", { startDate: "2026-01-31", durationMonths: 1 }, "2026-02-28"],
  ["month-end start: not ended the day before", { startDate: "2026-01-31", durationMonths: 1 }, "2026-02-27"],
  ["unknown term never ends", { startDate: "Immediate (Tomorrow)" }, "2030-01-01"],
  ["calendar dates in the past", { selectedCalendarDates: ["2026-10-02", "2026-10-09"] }, "2026-10-10"],
  ["calendar dates, last date still today", { selectedCalendarDates: ["2026-10-02", "2026-10-09"] }, "2026-10-09"],
];

const OVERLAP_CASES = [
  ["back to back months do not overlap", { startDate: "2026-10-01", durationMonths: 1 }, { startDate: "2026-11-01", durationMonths: 1 }],
  ["share the last day", { startDate: "2026-10-01", durationMonths: 1 }, { startDate: "2026-10-31", durationMonths: 1 }],
  ["unknown term overlaps everything", { startDate: "2026-10-01", durationMonths: 1 }, { startDate: "label" }],
  ["both unknown", { startDate: "a" }, { startDate: "b" }],
  ["calendar date inside a month", { selectedCalendarDates: ["2026-10-05"] }, { startDate: "2026-10-01", durationMonths: 1 }],
  ["calendar date outside", { selectedCalendarDates: ["2026-12-05"] }, { startDate: "2026-10-01", durationMonths: 1 }],
  ["two calendar sets, disjoint", { selectedCalendarDates: ["2026-10-01", "2026-10-02"] }, { selectedCalendarDates: ["2026-10-03"] }],
  ["two calendar sets, touching end", { selectedCalendarDates: ["2026-10-01", "2026-10-02"] }, { selectedCalendarDates: ["2026-10-02"] }],
  ["month-only dates", { startDate: "2026-10", durationMonths: 2 }, { startDate: "2026-11-15", durationMonths: 1 }],
  ["month-end term still covers the day before its clamped end", { startDate: "2026-01-31", durationMonths: 1 }, { startDate: "2026-02-27", durationMonths: 1 }],
  ["month-end term and a booking starting on its end day do not overlap", { startDate: "2026-01-31", durationMonths: 1 }, { startDate: "2026-02-28", durationMonths: 1 }],
];

function bookingTermsFile() {
  return {
    description: "Mirrors SpaceCalculationUtils.bookingTerm/hasEnded/termsOverlap (Kotlin) and functions/src/bookings/bookingTerms.ts. End dates are exclusive.",
    term: TERM_CASES.map(([name, input, kotlinExpected]) => {
      const expected = terms.bookingTerm(input);
      return kotlinExpected ? { name, input, expected, kotlinExpected } : { name, input, expected }; // kotlinExpected: a known divergence (none today)
    }),
    hasEnded: ENDED_CASES.map(([name, input, today]) => ({ name, input, today, expected: terms.hasEnded(input, today) })),
    termsOverlap: OVERLAP_CASES.map(([name, a, b]) => ({ name, a, b, expected: terms.termsOverlap(a, b) })),
  };
}

// ---------------------------------------------------------------- listing prices
const hourly = (cells) => ({ strategyType: "HOURLY", hourly: { cellPrices: cells } });
const shifts = (list) => ({ strategyType: "SHIFT_BASED", shiftBased: { shifts: list, distribution: {} } });
const days = (dist) => ({ strategyType: "DAY_BASED", dayBased: { useFacilityHours: true, distribution: dist } });
const monthly = (rate) => ({ strategyType: "MONTHLY", monthly: { rateUsd: rate } });

const PRICING_CASES = [
  ["hourly: lowest positive cell", hourly({ "Mon|9": 25, "Mon|10": 0, "Tue|9": 20 })],
  ["hourly: no cells is unpriced", hourly({})],
  ["hourly: all zero is unpriced", hourly({ "Mon|9": 0 })],
  ["shift: lowest positive shift", shifts([{ name: "MORNING", price: 60 }, { name: "EVENING", price: 45 }, { name: "NIGHT", price: 0 }])],
  ["shift: none priced", shifts([{ name: "MORNING", price: 0 }])],
  ["day: lowest positive day", days({ Mon: { price: 90 }, Tue: { price: 80 } })],
  ["day: one zero day ignored", days({ Mon: { price: 0 }, Tue: { price: 75.5 } })],
  ["monthly: rate", monthly(400)],
  ["monthly: zero rate is unpriced", monthly(0)],
  ["strategy type missing is monthly", { monthly: { rateUsd: 300 } }],
  ["fractional prices", hourly({ "Mon|9": 12.25, "Mon|10": 12.5 })],
];

const tier = (price, min, extra = {}) => ({ id: `T${price}`, name: `Tier ${price}`, pricePerAttendeeUsd: price, minAttendees: min, ...extra });
const ROOM_CASES = [
  ["per-attendee room prices per person from its cheapest enabled tier", {
    pricingMode: "PER_ATTENDEE", pricing: hourly({ "Mon|9": 1 }),
    attendeeTiers: [tier(12, 1), tier(9, 10), tier(5, 1, { isEnabled: false })],
  }],
  ["per-attendee tiers with a zero price are ignored", {
    pricingMode: "PER_ATTENDEE", pricing: hourly({ "Mon|9": 1 }), attendeeTiers: [tier(0, 1), tier(7, 1)],
  }],
  ["strategy room uses its own config (day)", { pricingMode: "STRATEGY_BASED", pricing: days({ Mon: { price: 70 } }) }],
  ["strategy room uses its own config (hour)", { pricingMode: "STRATEGY_BASED", pricing: hourly({ "Mon|9": 8, "Mon|10": 9 }) }],
  ["a monthly room cannot be per attendee", { pricingMode: "PER_ATTENDEE", pricing: monthly(300) }],
];

function listingPricesFile() {
  return {
    description: "Mirrors SpaceCalculationUtils.lowestPriceFor / findLowestPriceForSubdivision (Kotlin) and functions/src/listings/listingPrices.ts. unit: hour|shift|day|month|person ↔ /hr|/shift|/day|/mo|/person. Unpriced rooms are not vectored: TS returns null, Kotlin returns 0.0 (known representation difference).",
    lowestPriceFor: PRICING_CASES.map(([name, pricing]) => ({ name, pricing, expected: prices.lowestPriceFor(pricing) })),
    roomPrice: ROOM_CASES.map(([name, sub]) => ({ name, sub, expected: prices.roomPrice(sub) })),
  };
}

// ---------------------------------------------------------------- legacy pricing
function digest(p) {
  const sorted = (o) => Object.fromEntries(Object.keys(o).sort().map((k) => [k, o[k]]));
  switch (p.strategyType) {
    case "HOURLY": return { strategyType: "HOURLY", cellPrices: sorted(p.hourly.cellPrices) };
    case "SHIFT_BASED": return {
      strategyType: "SHIFT_BASED",
      shifts: p.shiftBased.shifts.map((s) => [s.name, s.isUnavailable, s.price, s.startHour, s.endHour]),
      distribution: sorted(p.shiftBased.distribution),
    };
    case "DAY_BASED": return {
      strategyType: "DAY_BASED",
      useFacilityHours: p.dayBased.useFacilityHours,
      customStartHour: p.dayBased.customStartHour,
      customEndHour: p.dayBased.customEndHour,
      distribution: sorted(Object.fromEntries(Object.entries(p.dayBased.distribution).map(([d, v]) => [d, v.price]))),
    };
    default: return { strategyType: "MONTHLY", rateUsd: p.monthly.rateUsd };
  }
}

const F = (type, rateUsd, daysOfWeek, startHour, endHour) => ({ type, rateUsd, daysOfWeek, startHour, endHour });
const LEGACY_CASES = [
  ["SHIFT: only MORNING offered on its days", F("SHIFT", 350, ["Mon", "Wed"], "08:00", "14:00")],
  ["HOURLY: one cell per day and hour in [start, end)", F("HOURLY", 40, ["Tue"], "09:00", "12:00")],
  ["HOURLY: two days", F("HOURLY", 25.5, ["Mon", "Fri"], "10:00", "12:00")],
  ["HOURLY: start >= end gives no cells", F("HOURLY", 40, ["Tue"], "12:00", "09:00")],
  ["DAY_PER_WEEK: custom hours", F("DAY_PER_WEEK", 600, ["Tue", "Thu"], "08:00", "18:00")],
  ["FULL_MONTH: monthly rate", F("FULL_MONTH", 450, ["Mon"], "08:00", "18:00")],
  ["hour without minutes", F("HOURLY", 10, ["Mon"], "9", "11")],
  ["hour with minutes is truncated", F("HOURLY", 10, ["Mon"], "9:30", "11:45")],
  ["unparseable hours fall back to 8 and 18", F("HOURLY", 10, ["Mon"], "abc", "")],
  ["no days offered", F("SHIFT", 100, [], "08:00", "12:00")],
];

function legacyPricingFile() {
  return {
    description: "Mirrors RentalPricingConfig.fromLegacyFormula (Kotlin) and functions/src/listings/legacyPricing.ts. `expected` is a normalised digest of the structured pricing both sides must produce. formula=null is the 'missing formula' case (default MONTHLY, rate 0).",
    cases: [
      ...LEGACY_CASES.map(([name, formula]) => ({ name, formula, expected: digest(legacy.pricingFromLegacyFormula(formula)) })),
      { name: "missing formula is the default monthly config", formula: null, expected: digest(legacy.pricingFromLegacyFormula(null)) },
    ],
  };
}

// ---------------------------------------------------------------- write / check
const FILES = {
  "booking-terms.json": bookingTermsFile(),
  "listing-prices.json": listingPricesFile(),
  "legacy-pricing.json": legacyPricingFile(),
};

let drift = 0;
fs.mkdirSync(OUT, { recursive: true });
for (const [name, data] of Object.entries(FILES)) {
  const text = JSON.stringify(data, null, 2) + "\n";
  const file = path.join(OUT, name);
  if (flag("--check")) {
    const current = fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
    if (current !== text) { console.error(`DRIFT: ${path.relative(ROOT, file)} differs from the TypeScript implementation`); drift++; }
  } else {
    fs.writeFileSync(file, text);
    console.log(`wrote ${path.relative(ROOT, file)}`);
  }
}
if (drift) process.exit(1);
