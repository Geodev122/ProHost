import { test } from "node:test";
import assert from "node:assert/strict";
import * as fs from "fs";
import * as path from "path";
import { keepUsedSlots, offeredSlots, SlotBookingDoc, SlotListingDoc, SlotPricingDoc } from "./slotUsage";

const TODAY = "2026-12-01";
const SPACE = "S1";

const hourly: SlotPricingDoc = { strategyType: "HOURLY", hourly: { cellPrices: { "Tue|9": 12, "Tue|10": 12, "Wed|9": 15 } } };
const shifts: SlotPricingDoc = {
  strategyType: "SHIFT_BASED",
  shiftBased: {
    shifts: [
      { name: "MORNING", startHour: 8, endHour: 12, isUnavailable: false, price: 40 },
      { name: "EVENING", startHour: 16, endHour: 20, isUnavailable: false, price: 45 },
    ],
    distribution: { Wed: ["MORNING", "EVENING"], Thu: ["MORNING"] },
  },
};
const days: SlotPricingDoc = { strategyType: "DAY_BASED", dayBased: { useFacilityHours: true, distribution: { Tue: { price: 75 }, Fri: { price: 60 } } } } as SlotPricingDoc;
const monthly: SlotPricingDoc = { strategyType: "MONTHLY", monthly: { rateUsd: 900, fromMonth: 1, fromYear: 2026, isIndefinite: true, excludedRanges: [] } };

const datedHour = (date: string, hour: number, status = "PENDING"): SlotBookingDoc =>
  ({ spaceId: SPACE, status, dateModel: 2, startDate: date, bookedSlots: [{ date, key: String(hour), startHour: hour, endHour: hour + 1, priceUsd: 12 }],
    selectedCalendarDates: [date], formula: { type: "HOURLY", startHour: `${hour}:00`, endHour: `${hour + 1}:00`, daysOfWeek: [] } } as unknown as SlotBookingDoc);
const datedShift = (date: string, name: string): SlotBookingDoc =>
  ({ spaceId: SPACE, status: "ACCEPTED", dateModel: 2, startDate: date, bookedSlots: [{ date, key: name, startHour: 8, endHour: 12, shiftName: name, priceUsd: 40 }],
    selectedCalendarDates: [date], formula: { type: "SHIFT", startHour: "08:00", endHour: "12:00", daysOfWeek: [] } } as unknown as SlotBookingDoc);
const datedDay = (date: string): SlotBookingDoc =>
  ({ spaceId: SPACE, status: "ACCEPTED", dateModel: 2, startDate: date, bookedSlots: [{ date, key: "DAY", startHour: 9, endHour: 18, priceUsd: 75 }],
    selectedCalendarDates: [date], formula: { type: "DAY_PER_WEEK", startHour: "09:00", endHour: "18:00", daysOfWeek: [] } } as unknown as SlotBookingDoc);
const month = (start: string, n: number, status = "ACCEPTED"): SlotBookingDoc =>
  ({ spaceId: SPACE, status, startDate: start, durationMonths: n, formula: { type: "FULL_MONTH", startHour: "08:00", endHour: "18:00", daysOfWeek: [] } } as unknown as SlotBookingDoc);

interface Case { name: string; before: SlotListingDoc; after: SlotListingDoc; bookings: SlotBookingDoc[] }
const L = (pricing: SlotPricingDoc, closedDates: string[] = []): SlotListingDoc => ({ pricing, subdivisions: [], schedule: { closedDates } });
const without = (p: SlotPricingDoc, key: string): SlotPricingDoc => {
  const cells = { ...(p.hourly?.cellPrices ?? {}) };
  delete cells[key];
  return { ...p, hourly: { cellPrices: cells } };
};

const CASES: Case[] = [
  { name: "free hour switched off stays off", before: L(hourly), after: L(without(hourly, "Wed|9")), bookings: [datedHour("2026-12-08", 9)] },
  { name: "booked hour (pending, dated) is put back", before: L(hourly), after: L(without(hourly, "Tue|9")), bookings: [datedHour("2026-12-08", 9)] },
  { name: "ended booking does not hold the hour", before: L(hourly), after: L(without(hourly, "Tue|9")), bookings: [datedHour("2026-11-24", 9)] },
  { name: "rejected request does not hold the hour", before: L(hourly), after: L(without(hourly, "Tue|9")), bookings: [datedHour("2026-12-08", 9, "REJECTED")] },
  { name: "price set to 0 on a booked hour is put back", before: L(hourly),
    after: L({ ...hourly, hourly: { cellPrices: { ...hourly.hourly!.cellPrices, "Tue|9": 0 } } }), bookings: [datedHour("2026-12-08", 9)] },
  { name: "booked shift marked unavailable is put back", before: L(shifts),
    after: L({ ...shifts, shiftBased: { ...shifts.shiftBased!, shifts: shifts.shiftBased!.shifts!.map((s) => s.name === "MORNING" ? { ...s, isUnavailable: true } : s) } }),
    bookings: [datedShift("2026-12-02", "MORNING")] },
  { name: "free shift marked unavailable stays off", before: L(shifts),
    after: L({ ...shifts, shiftBased: { ...shifts.shiftBased!, shifts: shifts.shiftBased!.shifts!.map((s) => s.name === "EVENING" ? { ...s, isUnavailable: true } : s) } }),
    bookings: [datedShift("2026-12-02", "MORNING")] },
  { name: "booked day removed is put back", before: L(days),
    after: L({ ...days, dayBased: { ...days.dayBased!, distribution: { Fri: { price: 60 } } } } as SlotPricingDoc), bookings: [datedDay("2026-12-08")] },
  { name: "free day removed stays off", before: L(days),
    after: L({ ...days, dayBased: { ...days.dayBased!, distribution: { Tue: { price: 75 } } } } as SlotPricingDoc), bookings: [datedDay("2026-12-08")] },
  { name: "monthly window cut under an accepted term is put back", before: L(monthly),
    after: L({ ...monthly, monthly: { ...monthly.monthly!, isIndefinite: false, toMonth: 12, toYear: 2026 } }), bookings: [month("2026-12-01", 3)] },
  { name: "monthly window cut after every term stays", before: L(monthly),
    after: L({ ...monthly, monthly: { ...monthly.monthly!, isIndefinite: false, toMonth: 6, toYear: 2027 } }), bookings: [month("2026-12-01", 3)] },
  { name: "closed date on a booked day reopens", before: L(hourly), after: L(hourly, ["2026-12-08"]), bookings: [datedHour("2026-12-08", 9)] },
  { name: "closed date on a free day stays closed", before: L(hourly), after: L(hourly, ["2026-12-09"]), bookings: [datedHour("2026-12-08", 9)] },
  { name: "closed date inside a monthly term reopens", before: L(monthly), after: L(monthly, ["2026-12-15"]), bookings: [month("2026-12-01", 1)] },
  { name: "no bookings: everything the host did stays", before: L(hourly), after: L(without(hourly, "Tue|9"), ["2026-12-08"]), bookings: [] },
];

function run(c: Case) {
  const r = keepUsedSlots(SPACE, c.before, c.after, c.bookings.map((b, i) => ({ id: `b${i}`, ...b })), TODAY);
  const result = { ...c.after, ...r.patch };
  return {
    offered: offeredSlots(SPACE, result.pricing).map((s) => `${s.kind}:${s.key}`).sort(),
    closedDates: [...(result.schedule?.closedDates ?? [])].sort(),
    monthlyIndefinite: result.pricing?.monthly?.isIndefinite ?? null,
    kept: r.kept.length,
  };
}

test("slot usage cases", () => {
  const byName = Object.fromEntries(CASES.map((c) => [c.name, run(c)]));
  assert.deepEqual(byName["free hour switched off stays off"].offered, ["HOURLY:Tue|10", "HOURLY:Tue|9"]);
  assert.ok(byName["booked hour (pending, dated) is put back"].offered.includes("HOURLY:Tue|9"));
  assert.ok(!byName["ended booking does not hold the hour"].offered.includes("HOURLY:Tue|9"));
  assert.ok(!byName["rejected request does not hold the hour"].offered.includes("HOURLY:Tue|9"));
  assert.ok(byName["price set to 0 on a booked hour is put back"].offered.includes("HOURLY:Tue|9"));
  assert.ok(byName["booked shift marked unavailable is put back"].offered.includes("SHIFT:MORNING"));
  assert.ok(!byName["free shift marked unavailable stays off"].offered.includes("SHIFT:EVENING"));
  assert.ok(byName["booked day removed is put back"].offered.includes("DAY:Tue"));
  assert.ok(!byName["free day removed stays off"].offered.includes("DAY:Fri"));
  assert.equal(byName["monthly window cut under an accepted term is put back"].monthlyIndefinite, true);
  assert.equal(byName["monthly window cut after every term stays"].monthlyIndefinite, false);
  assert.deepEqual(byName["closed date on a booked day reopens"].closedDates, []);
  assert.deepEqual(byName["closed date on a free day stays closed"].closedDates, ["2026-12-09"]);
  assert.deepEqual(byName["closed date inside a monthly term reopens"].closedDates, []);
  assert.equal(byName["no bookings: everything the host did stays"].kept, 0);
});

// The same cases the Kotlin rule runs (tests/vectors/slot-usage.json): regenerated here when UPDATE_VECTORS=1, compared otherwise.
test("shared vectors", () => {
  const file = path.resolve(__dirname, "../../../tests/vectors/slot-usage.json");
  const doc = {
    description: "Slots of a published listing that a pending or accepted booking still uses are kept on when the host switches them off. Generated from functions/src/listings/slotUsage.ts (UPDATE_VECTORS=1 npm test); the Kotlin rule com.example.ui.util.SlotUsage must produce the same.",
    today: TODAY, spaceId: SPACE,
    cases: CASES.map((c) => ({ ...c, bookings: c.bookings.map((b, i) => ({ id: `b${i}`, ...b })), expected: run(c) })),
  };
  if (process.env.UPDATE_VECTORS === "1" || !fs.existsSync(file)) fs.writeFileSync(file, JSON.stringify(doc, null, 1) + "\n");
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")), JSON.parse(JSON.stringify(doc)));
});
