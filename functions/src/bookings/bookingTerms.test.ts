import { test } from "node:test";
import assert from "node:assert/strict";
import { bookingTerm, hasEnded, termsOverlap } from "./bookingTerms";

test("terms come from calendar dates, then startDate + months", () => {
  assert.deepEqual(bookingTerm({ selectedCalendarDates: ["2026-10-09", "2026-10-02"] }), { start: "2026-10-02", end: "2026-10-10" });
  assert.deepEqual(bookingTerm({ startDate: "2026-10-01", durationMonths: 3 }), { start: "2026-10-01", end: "2027-01-01" });
  assert.deepEqual(bookingTerm({ startDate: "2026-11", durationMonths: 1 }), { start: "2026-11-01", end: "2026-12-01" });
  assert.equal(bookingTerm({ startDate: "Next Monday", durationMonths: 1 }), null);
});

test("ended bookings stop locking; unknown terms never end", () => {
  assert.equal(hasEnded({ startDate: "2026-01-01", durationMonths: 1 }, "2026-10-07"), true);
  assert.equal(hasEnded({ startDate: "2026-10-01", durationMonths: 1 }, "2026-10-07"), false);
  assert.equal(hasEnded({ startDate: "Immediate (Tomorrow)" }, "2030-01-01"), false);
});

test("terms overlap only when they share a day", () => {
  const oct = { startDate: "2026-10-01", durationMonths: 1 };
  assert.equal(termsOverlap(oct, { startDate: "2026-11-01", durationMonths: 1 }), false);
  assert.equal(termsOverlap(oct, { startDate: "2026-10-31", durationMonths: 1 }), true);
  assert.equal(termsOverlap(oct, { startDate: "label" }), true);
  assert.equal(termsOverlap({ selectedCalendarDates: ["2026-10-05"] }, oct), true);
});

test("month arithmetic clamps to the last day of the target month, like the app (Calendar.add(MONTH))", () => {
  assert.deepEqual(bookingTerm({ startDate: "2026-01-31", durationMonths: 1 }), { start: "2026-01-31", end: "2026-02-28" });
  assert.deepEqual(bookingTerm({ startDate: "2026-03-31", durationMonths: 1 }), { start: "2026-03-31", end: "2026-04-30" });
  assert.deepEqual(bookingTerm({ startDate: "2028-01-31", durationMonths: 1 }), { start: "2028-01-31", end: "2028-02-29" });
  assert.deepEqual(bookingTerm({ startDate: "2026-12-31", durationMonths: 2 }), { start: "2026-12-31", end: "2027-02-28" });
  assert.deepEqual(bookingTerm({ startDate: "2026-01-31", durationMonths: 12 }), { start: "2026-01-31", end: "2027-01-31" });
  // a month-end booking ends on its clamped end day, so a booking that starts that day does not overlap it
  assert.equal(hasEnded({ startDate: "2026-01-31", durationMonths: 1 }, "2026-02-28"), true);
  assert.equal(hasEnded({ startDate: "2026-01-31", durationMonths: 1 }, "2026-02-27"), false);
  assert.equal(termsOverlap({ startDate: "2026-01-31", durationMonths: 1 }, { startDate: "2026-02-28", durationMonths: 1 }), false);
});
