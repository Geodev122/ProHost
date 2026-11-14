import { test } from "node:test";
import assert from "node:assert/strict";
import { findConflict } from "./bookingConflictGuard";
import { occupancyFields } from "./occupancyProjection";

const slot = (date: string, startHour: number, endHour: number) => ({ date, key: String(startHour), startHour, endHour, priceUsd: 12 });
const dated = (id: string, slots: ReturnType<typeof slot>[], extra: Record<string, unknown> = {}) => ({
  id,
  data: {
    spaceId: "s1", subdivisionId: "r1", status: "ACCEPTED", formula: { type: "HOURLY", startHour: "09:00", endHour: "18:00" },
    bookedSlots: slots, selectedCalendarDates: [...new Set(slots.map((s) => s.date))], startDate: slots[0].date, ...extra,
  } as never,
});
const TODAY = "2026-11-14";

test("dated bookings clash only on the same date and overlapping hours", () => {
  const held = dated("a", [slot("2026-11-17", 10, 11), slot("2026-11-19", 15, 16)]);
  assert.equal(findConflict("b", dated("b", [slot("2026-11-17", 10, 11)]).data, [held], TODAY)?.id, "a");
  assert.equal(findConflict("b", dated("b", [slot("2026-11-17", 11, 12)]).data, [held], TODAY), undefined); // next hour
  assert.equal(findConflict("b", dated("b", [slot("2026-11-24", 10, 11)]).data, [held], TODAY), undefined); // next week
  assert.equal(findConflict("b", dated("b", [slot("2026-11-19", 14, 16)]).data, [held], TODAY)?.id, "a");
  assert.equal(findConflict("b", dated("b", [slot("2026-11-17", 10, 11)], { subdivisionId: "r2" }).data, [held], TODAY), undefined); // other room
});

test("a dated request against older bookings", () => {
  const weekly = { id: "w", data: { spaceId: "s1", subdivisionId: "r1", status: "ACCEPTED", formula: { type: "HOURLY", startHour: "10:00", endHour: "12:00", daysOfWeek: ["Tue"] }, startDate: "2026-11-10", durationMonths: 1 } as never };
  assert.equal(findConflict("b", dated("b", [slot("2026-11-17", 11, 12)]).data, [weekly], TODAY)?.id, "w"); // a Tuesday in its term
  assert.equal(findConflict("b", dated("b", [slot("2026-11-17", 12, 13)]).data, [weekly], TODAY), undefined);
  assert.equal(findConflict("b", dated("b", [slot("2026-11-18", 11, 12)]).data, [weekly], TODAY), undefined); // a Wednesday
  const month = { id: "m", data: { spaceId: "s1", subdivisionId: "r1", status: "ACCEPTED", formula: { type: "FULL_MONTH" }, startDate: "2026-11-10", durationMonths: 1 } as never };
  assert.equal(findConflict("b", dated("b", [slot("2026-11-17", 9, 10)]).data, [month], TODAY)?.id, "m");
  assert.equal(findConflict("b", dated("b", [slot("2026-12-15", 9, 10)]).data, [month], TODAY), undefined);
});

test("the occupancy projection carries the dated slots without person or price", () => {
  const f = occupancyFields({ spaceId: "s1", formula: { type: "HOURLY" }, bookedSlots: [slot("2026-11-17", 10, 11)], practitionerName: "Omar" });
  assert.deepEqual(f.bookedSlots, [{ date: "2026-11-17", key: "10", startHour: 10, endHour: 11 }]);
  assert.equal(JSON.stringify(f).includes("Omar"), false);
  assert.deepEqual(occupancyFields({ spaceId: "s1" }).bookedSlots, []);
});
