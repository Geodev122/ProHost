import { test } from "node:test";
import assert from "node:assert/strict";
import * as fs from "node:fs";
import * as path from "node:path";
import {
  bookedSlotsProblem, dayView, maxMonthlyMonths, monthlyProblem, monthlyTerm, monthOffered, occupiedOn, offeredSlots, weekdayOf,
} from "./dateSlots";

const schedule = { openingHour: "09:00", closingHour: "18:00", operatingDays: ["Mon", "Tue", "Wed", "Thu", "Fri"], isSundayOperating: false };
const hourly = { strategyType: "HOURLY", hourly: { cellPrices: { "Tue|9": 12, "Tue|10": 12, "Tue|11": 15, "Thu|15": 12, "Sat|9": 12, "Tue|12": 0 } } };
const shifts = {
  strategyType: "SHIFT_BASED",
  shiftBased: {
    shifts: [
      { name: "MORNING", startHour: 8, endHour: 12, isUnavailable: false, price: 40 },
      { name: "EVENING", startHour: 16, endHour: 20, isUnavailable: false, price: 45 },
      { name: "NIGHT", startHour: 20, endHour: 23, isUnavailable: true, price: 30 },
    ],
    distribution: { Wed: ["MORNING", "EVENING", "NIGHT"], Thu: ["MORNING"] },
  },
};
const daily = { strategyType: "DAY_BASED", dayBased: { useFacilityHours: true, distribution: { Tue: { price: 75 }, Fri: { price: 0 } } } };
const monthly = { strategyType: "MONTHLY", monthly: { rateUsd: 450, fromMonth: 11, fromYear: 2026, isIndefinite: false, toMonth: 6, toYear: 2027, excludedRanges: [{ fromMonth: 4, fromYear: 2027, toMonth: 4, toYear: 2027 }] } };
const TODAY = "2026-11-14"; // a Saturday

test("weekday of a date", () => {
  assert.equal(weekdayOf("2026-11-14"), "Sat");
  assert.equal(weekdayOf("2026-11-17"), "Tue");
  assert.equal(weekdayOf("2028-02-29"), "Tue");
});

test("hourly: only priced hours of an operating weekday, inside opening hours", () => {
  assert.deepEqual(offeredSlots(hourly, schedule, "2026-11-17").map((s) => [s.key, s.priceUsd]), [["9", 12], ["10", 12], ["11", 15]]);
  assert.deepEqual(offeredSlots(hourly, schedule, "2026-11-21"), []); // Saturday is not an operating day
  assert.deepEqual(offeredSlots(hourly, schedule, "2026-11-18"), []); // Wednesday has no priced hour
});

test("shift and day rooms", () => {
  assert.deepEqual(offeredSlots(shifts, schedule, "2026-11-18").map((s) => [s.key, s.startHour, s.endHour, s.priceUsd]), [["MORNING", 8, 12, 40], ["EVENING", 16, 20, 45]]);
  assert.deepEqual(offeredSlots(daily, schedule, "2026-11-17"), [{ key: "DAY", startHour: 9, endHour: 18, shiftName: null, priceUsd: 75 }]);
  assert.deepEqual(offeredSlots(daily, schedule, "2026-11-20"), []); // price 0 = not offered
  assert.deepEqual(offeredSlots(monthly, schedule, "2026-11-17"), []);
});

test("day view: past, beyond the window, closed, not offered, open with taken slots", () => {
  const base = { today: TODAY, pricing: hourly, schedule };
  assert.equal(dayView({ ...base, date: "2026-11-13" }).state, "past");
  assert.equal(dayView({ ...base, date: "2027-02-16" }).state, "beyond"); // default window = 3 months
  assert.equal(dayView({ ...base, date: "2027-02-09" }).state, "open");
  assert.equal(dayView({ ...base, pricing: { ...hourly, bookingHorizonMonths: 1 }, date: "2026-12-15" }).state, "beyond");
  assert.equal(dayView({ ...base, date: "2026-11-17", closedDates: ["2026-11-17"] }).state, "closed");
  assert.equal(dayView({ ...base, date: "2026-11-18" }).state, "not_offered");
  const dated = { bookedSlots: [{ date: "2026-11-17", key: "10", startHour: 10, endHour: 11, priceUsd: 12 }] };
  assert.deepEqual(dayView({ ...base, date: "2026-11-17", bookings: [dated] }).slots.map((s) => s.taken), [false, true, false]);
  assert.deepEqual(dayView({ ...base, date: "2026-11-24", bookings: [dated] }).slots.map((s) => s.taken), [false, false, false]); // next Tuesday is free
});

test("what older bookings occupy", () => {
  const month = { formula: { type: "FULL_MONTH" }, startDate: "2026-11-10", durationMonths: 1 };
  assert.deepEqual(occupiedOn(month, "2026-11-17"), [[0, 24]]);
  assert.deepEqual(occupiedOn(month, "2026-12-10"), []);
  const legacyDates = { formula: { type: "SHIFT", startHour: "08:00", endHour: "12:00" }, selectedCalendarDates: ["2026-11-18"] };
  assert.deepEqual(occupiedOn(legacyDates, "2026-11-18"), [[8, 12]]);
  assert.deepEqual(occupiedOn(legacyDates, "2026-11-25"), []);
  const legacyWeekly = { formula: { type: "HOURLY", startHour: "10:00", endHour: "12:00", daysOfWeek: ["Tue"] }, startDate: "2026-11-10", durationMonths: 1 };
  assert.deepEqual(occupiedOn(legacyWeekly, "2026-11-17"), [[10, 12]]);
  assert.deepEqual(occupiedOn(legacyWeekly, "2026-11-19"), []);
  assert.deepEqual(occupiedOn(legacyWeekly, "2026-12-15"), []); // after its term
  assert.deepEqual(occupiedOn({ formula: { type: "HOURLY", daysOfWeek: ["Tue"] }, startDate: "Next Monday" }, "2027-06-01"), [[0, 24]]); // unknown term = ongoing
});

test("a request's dated slots are checked one by one", () => {
  const base = { today: TODAY, pricing: hourly, schedule, checkPrices: true };
  const slot = { date: "2026-11-17", key: "10", startHour: 10, endHour: 11, shiftName: null, priceUsd: 12 };
  assert.equal(bookedSlotsProblem({ ...base, slots: [slot] }), null);
  assert.match(bookedSlotsProblem({ ...base, slots: [] }) ?? "", /at least one/);
  assert.match(bookedSlotsProblem({ ...base, slots: [slot, slot] }) ?? "", /twice/);
  assert.match(bookedSlotsProblem({ ...base, slots: [{ ...slot, priceUsd: 5 }] }) ?? "", /price changed/);
  assert.equal(bookedSlotsProblem({ ...base, checkPrices: false, slots: [{ ...slot, priceUsd: 1 }] }), null);
  assert.match(bookedSlotsProblem({ ...base, slots: [{ ...slot, date: "2026-11-18" }] }) ?? "", /no longer offered/);
  assert.match(bookedSlotsProblem({ ...base, closedDates: ["2026-11-17"], slots: [slot] }) ?? "", /closed/);
  assert.match(bookedSlotsProblem({ ...base, bookings: [{ bookedSlots: [slot] }], slots: [slot] }) ?? "", /just booked/);
  assert.match(bookedSlotsProblem({ ...base, slots: [{ ...slot, date: "2026-11-10" }] }) ?? "", /passed/);
});

test("monthly: offered months, term and problems", () => {
  assert.equal(monthOffered(monthly.monthly, 2026, 10), false);
  assert.equal(monthOffered(monthly.monthly, 2026, 11), true);
  assert.equal(monthOffered(monthly.monthly, 2027, 4), false); // excluded
  assert.equal(monthOffered(monthly.monthly, 2027, 7), false);
  assert.deepEqual(monthlyTerm("2026-12-14", 2), { start: "2026-12-14", end: "2027-02-14" });
  assert.deepEqual(monthlyTerm("2027-01-31", 1), { start: "2027-01-31", end: "2027-02-28" });
  const base = { today: TODAY, pricing: monthly };
  assert.equal(monthlyProblem({ ...base, start: "2026-12-14", months: 2 }), null);
  assert.match(monthlyProblem({ ...base, start: "2026-11-13", months: 1 }) ?? "", /today onward/);
  assert.match(monthlyProblem({ ...base, start: "2026-12-14", months: 13 }) ?? "", /between 1 and 12/);
  assert.match(monthlyProblem({ ...base, start: "2027-03-15", months: 1 }) ?? "", /doesn't rent/); // runs into excluded April
  assert.equal(monthlyProblem({ ...base, start: "2027-03-01", months: 1 }), null); // 1-31 March only
  const other = { formula: { type: "FULL_MONTH" }, startDate: "2026-11-10", durationMonths: 1 }; // until 9 Dec
  assert.match(monthlyProblem({ ...base, start: "2026-12-09", months: 1, bookings: [other] }) ?? "", /already booked/);
  assert.equal(monthlyProblem({ ...base, start: "2026-12-10", months: 1, bookings: [other] }), null);
  assert.match(monthlyProblem({ ...base, start: "2026-12-14", months: 1, bookings: [{ bookedSlots: [{ date: "2026-12-20", key: "DAY", startHour: 9, endHour: 18 }] }] }) ?? "", /already booked/);
  assert.equal(maxMonthlyMonths({ ...base, start: "2026-12-01" }), 4); // Dec-Mar, April excluded
  assert.equal(maxMonthlyMonths({ ...base, start: "2027-04-10" }), 0);
});

// The same cases the Kotlin engine runs (tests/vectors/date-slots.json): regenerated here when UPDATE_VECTORS=1, compared otherwise.
test("shared vectors", () => {
  const file = path.resolve(__dirname, "../../../tests/vectors/date-slots.json");
  const bookings = [
    { bookedSlots: [{ date: "2026-11-17", key: "10", startHour: 10, endHour: 11, priceUsd: 12 }] },
    { formula: { type: "HOURLY", startHour: "15:00", endHour: "16:00", daysOfWeek: ["Thu"] }, startDate: "2026-11-01", durationMonths: 1 },
  ];
  const dates = ["2026-11-13", "2026-11-14", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-21", "2026-11-24", "2026-12-03", "2027-02-14", "2027-02-16"];
  const rooms: Record<string, unknown> = { hourly, shifts, daily };
  const dayCases = Object.entries(rooms).flatMap(([room, pricing]) => dates.map((date) => ({
    room, date, expected: dayView({ date, today: TODAY, pricing, schedule, closedDates: ["2026-11-24"], bookings }),
  })));
  const monthlyBookings = [{ formula: { type: "FULL_MONTH" }, startDate: "2026-11-10", durationMonths: 1 }];
  const monthlyCases = [["2026-11-13", 1], ["2026-12-09", 1], ["2026-12-10", 1], ["2026-12-14", 2], ["2027-01-31", 1], ["2027-03-01", 1], ["2027-03-15", 1], ["2027-05-01", 2], ["2027-05-01", 3], ["2026-12-14", 13]]
    .map(([start, months]) => ({
      start, months,
      term: monthlyTerm(start as string, months as number),
      problem: monthlyProblem({ start: start as string, months: months as number, today: TODAY, pricing: monthly, bookings: monthlyBookings }),
      maxMonths: maxMonthlyMonths({ start: start as string, today: TODAY, pricing: monthly, bookings: monthlyBookings }),
    }));
  const doc = {
    description: "Booking on real dates: what each calendar date offers and when a monthly term may start. Generated from functions/src/bookings/dateSlots.ts (UPDATE_VECTORS=1 npm test); the Kotlin engine com.example.ui.util.DateSlots must produce the same.",
    today: TODAY, schedule, rooms, monthly, closedDates: ["2026-11-24"], bookings, monthlyBookings, dayCases, monthlyCases,
  };
  if (process.env.UPDATE_VECTORS === "1" || !fs.existsSync(file)) fs.writeFileSync(file, JSON.stringify(doc, null, 1) + "\n");
  assert.deepEqual(JSON.parse(fs.readFileSync(file, "utf8")), JSON.parse(JSON.stringify(doc)));
});
