/**
 * Booking on real dates (owner decision 14 Nov 2026, plan section 28). The host still describes a normal WEEK
 * (weekday → hours / shifts / day price); this module projects that week onto a calendar DATE and says which slots
 * are offered, taken or unavailable there, and whether a monthly term can start on a date.
 *
 * Mirror of the app's `com.example.ui.util.DateSlots` (shared Kotlin). Both run tests/vectors/date-slots.json: keep
 * them identical. Pure: no Firestore, no clock (callers pass `today`).
 *
 * Slot keys: hourly = the start hour ("9"); shift = the shift name ("MORNING"); day = "DAY".
 * A booked slot occupies [startHour, endHour) on its date; two bookings clash when the ranges intersect on one date.
 */
import { addDays, addMonths, bookingTerm } from "./bookingTerms";

type Data = Record<string, unknown>;
const obj = (v: unknown): Data => (v && typeof v === "object" && !Array.isArray(v) ? (v as Data) : {});
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const str = (v: unknown): string => (typeof v === "string" ? v : "");

export const DEFAULT_HORIZON_MONTHS = 3;
export const MAX_MONTHLY_MONTHS = 12;
export const MAX_DATES_PER_REQUEST = 31;
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export interface Slot { key: string; startHour: number; endHour: number; shiftName: string | null; priceUsd: number }
export interface BookedSlot { date: string; key: string; startHour: number; endHour: number; shiftName: string | null; priceUsd: number }
export type DayState = "past" | "beyond" | "closed" | "not_offered" | "open";
export interface DayView { state: DayState; slots: Array<Slot & { taken: boolean }> }

/** "Mon" … "Sun" for an ISO date (the keys the host's weekly pattern uses). */
export function weekdayOf(iso: string): string {
  return WEEKDAYS[new Date(`${iso}T00:00:00Z`).getUTCDay()];
}

function parseHour(v: unknown): number | null {
  const n = parseInt(str(v).split(":")[0].trim(), 10);
  return Number.isInteger(n) && n >= 0 && n <= 24 ? n : null;
}

function operatingDays(schedule: Data): string[] {
  const days = arr(schedule.operatingDays).filter((d): d is string => typeof d === "string");
  if (schedule.isSundayOperating === true && !days.some((d) => d.toLowerCase() === "sun")) days.push("Sun");
  return days;
}

/** The slots the host offers on [date] (ignores bookings, closed dates and the horizon). Monthly rooms offer none here. */
export function offeredSlots(pricing: unknown, schedule: unknown, date: string): Slot[] {
  const p = obj(pricing);
  const s = obj(schedule);
  const day = weekdayOf(date);
  if (!operatingDays(s).includes(day)) return [];
  const open = parseHour(s.openingHour) ?? 0;
  const close = parseHour(s.closingHour) ?? 24;
  switch (p.strategyType) {
    case "HOURLY": {
      const cells = obj(obj(p.hourly).cellPrices);
      const out: Slot[] = [];
      for (let h = open; h < close; h++) {
        const price = num(cells[`${day}|${h}`]);
        if (price !== null && price > 0) out.push({ key: String(h), startHour: h, endHour: h + 1, shiftName: null, priceUsd: price });
      }
      return out;
    }
    case "SHIFT_BASED": {
      const sb = obj(p.shiftBased);
      const byName = new Map(arr(sb.shifts).map((x) => [str(obj(x).name), obj(x)]));
      const out: Slot[] = [];
      for (const name of arr(obj(sb.distribution)[day])) {
        const sh = byName.get(str(name));
        if (!sh || sh.isUnavailable === true) continue;
        const start = num(sh.startHour);
        const end = num(sh.endHour);
        const price = num(sh.price);
        if (start === null || end === null || price === null || price <= 0) continue;
        out.push({ key: str(name), startHour: start, endHour: end, shiftName: str(name), priceUsd: price });
      }
      return out;
    }
    case "DAY_BASED": {
      const db = obj(p.dayBased);
      const price = num(obj(obj(db.distribution)[day]).price);
      if (price === null || price <= 0) return [];
      const facility = db.useFacilityHours !== false;
      const start = facility ? open : num(db.customStartHour) ?? open;
      const end = facility ? close : num(db.customEndHour) ?? close;
      return [{ key: "DAY", startHour: start, endHour: end, shiftName: null, priceUsd: price }];
    }
    default:
      return [];
  }
}

/** The dated slots a booking carries (new bookings), cleaned. */
export function bookedSlotsOf(booking: unknown): BookedSlot[] {
  const out: BookedSlot[] = [];
  for (const raw of arr(obj(booking).bookedSlots)) {
    const b = obj(raw);
    const date = str(b.date);
    const start = num(b.startHour);
    const end = num(b.endHour);
    if (!ISO_DAY.test(date) || start === null || end === null || end <= start) continue;
    out.push({ date, key: str(b.key), startHour: start, endHour: end, shiftName: str(b.shiftName) || null, priceUsd: num(b.priceUsd) ?? 0 });
  }
  return out;
}

/**
 * The hour ranges [start, end) a booking occupies on [date]; empty when it does not touch that date.
 *  - dated slots (new bookings): exactly those of that date;
 *  - a monthly booking (formula FULL_MONTH): the whole day for every date of its term;
 *  - legacy, explicit calendar dates: its hour span on those dates;
 *  - legacy, weekday pattern: its hour span on its weekdays inside its term (unknown term = ongoing).
 */
export function occupiedOn(booking: unknown, date: string): Array<[number, number]> {
  const b = obj(booking);
  const dated = bookedSlotsOf(b);
  if (dated.length) return dated.filter((s) => s.date === date).map((s) => [s.startHour, s.endHour] as [number, number]);
  const formula = obj(b.formula);
  const term = bookingTerm({
    startDate: str(b.startDate), durationMonths: num(b.durationMonths) ?? undefined,
    selectedCalendarDates: arr(b.selectedCalendarDates).filter((d): d is string => typeof d === "string"),
  });
  const inTerm = term === null || (term.start <= date && date < term.end);
  if (formula.type === "FULL_MONTH") return inTerm ? [[0, 24]] : [];
  const span: [number, number] = [
    parseHour(b.selectedStartHour) ?? parseHour(formula.startHour) ?? 0,
    parseHour(b.selectedEndHour) ?? parseHour(formula.endHour) ?? 24,
  ];
  if (span[1] <= span[0]) span[1] = 24;
  const dates = arr(b.selectedCalendarDates).filter((d): d is string => typeof d === "string" && ISO_DAY.test(d));
  if (dates.length) return dates.includes(date) ? [span] : [];
  if (!inTerm) return [];
  const days = [...arr(b.selectedDays), ...arr(formula.daysOfWeek)].map((d) => str(d).slice(0, 3).toLowerCase());
  return days.length === 0 || days.includes(weekdayOf(date).toLowerCase()) ? [span] : [];
}

const intersects = (a: [number, number], b: [number, number]) => a[0] < b[1] && b[0] < a[1];

export interface DayInput {
  date: string;
  today: string;
  pricing: unknown;
  schedule: unknown;
  /** Union of the listing's and the room's closed dates. */
  closedDates?: unknown;
  /** Bookings that hold this room (accepted; callers decide whether pending ones count). */
  bookings?: unknown[];
}

export function horizonEnd(today: string, pricing: unknown): string {
  const months = num(obj(pricing).bookingHorizonMonths);
  return addMonths(today, months !== null && months >= 1 && months <= 24 ? Math.floor(months) : DEFAULT_HORIZON_MONTHS);
}

/** What a calendar date offers for an hourly / shift / day room. */
export function dayView(input: DayInput): DayView {
  const { date, today } = input;
  if (!ISO_DAY.test(date) || date < today) return { state: "past", slots: [] };
  if (date > horizonEnd(today, input.pricing)) return { state: "beyond", slots: [] };
  if (arr(input.closedDates).includes(date)) return { state: "closed", slots: [] };
  const offered = offeredSlots(input.pricing, input.schedule, date);
  if (!offered.length) return { state: "not_offered", slots: [] };
  const busy = (input.bookings ?? []).flatMap((b) => occupiedOn(b, date));
  return { state: "open", slots: offered.map((s) => ({ ...s, taken: busy.some((r) => intersects(r, [s.startHour, s.endHour])) })) };
}

/**
 * Why a request's dated slots can't be accepted as sent, or null. Every slot must be offered on its date at the price
 * sent ([checkPrices] is false for per-attendee rooms, whose slot prices are markers), on an open, bookable date, and free.
 * [today] should be the earliest date that can still be "today" anywhere (the server passes yesterday in UTC).
 */
export function bookedSlotsProblem(input: Omit<DayInput, "date"> & { slots: BookedSlot[]; checkPrices: boolean }): string | null {
  const { slots } = input;
  if (!slots.length) return "Choose at least one date.";
  if (new Set(slots.map((s) => s.date)).size > MAX_DATES_PER_REQUEST) return `Choose at most ${MAX_DATES_PER_REQUEST} dates in one request.`;
  const seen = new Set<string>();
  for (const s of slots) {
    const id = `${s.date}|${s.key}`;
    if (seen.has(id)) return "The same slot was chosen twice.";
    seen.add(id);
    const view = dayView({ ...input, date: s.date });
    if (view.state === "past") return `${s.date} has already passed.`;
    if (view.state === "beyond") return `${s.date} is too far ahead for this room.`;
    if (view.state === "closed") return `The host is closed on ${s.date}.`;
    const match = view.slots.find((o) => o.key === s.key && o.startHour === s.startHour && o.endHour === s.endHour);
    if (!match) return `That time is no longer offered on ${s.date}.`;
    if (match.taken) return `That time on ${s.date} was just booked by someone else.`;
    if (input.checkPrices && Math.abs(match.priceUsd - s.priceUsd) > 0.01) return "The price changed. Please send a new request.";
  }
  return null;
}

const monthIndex = (y: number, m: number) => y * 12 + (m - 1);

/** Whether the host rents the room in the calendar month [y]-[m] (inside the from/to window and not excluded). */
export function monthOffered(monthly: unknown, y: number, m: number): boolean {
  const c = obj(monthly);
  const at = monthIndex(y, m);
  const from = monthIndex(num(c.fromYear) ?? 0, num(c.fromMonth) ?? 1);
  if (at < from) return false;
  if (c.isIndefinite !== true) {
    const ty = num(c.toYear);
    const tm = num(c.toMonth);
    if (ty === null || tm === null || at > monthIndex(ty, tm)) return false;
  }
  return !arr(c.excludedRanges).some((r) => {
    const x = obj(r);
    return monthIndex(num(x.fromYear) ?? 0, num(x.fromMonth) ?? 1) <= at && at <= monthIndex(num(x.toYear) ?? 0, num(x.toMonth) ?? 1);
  });
}

/** The term [start, end) of a monthly booking: whole months from any start date, end clamped like bookingTerm. */
export function monthlyTerm(start: string, months: number): { start: string; end: string } {
  return { start, end: addMonths(start, Math.max(1, Math.floor(months))) };
}

/**
 * Why a monthly booking can't start on [start] for [months] months, or null: the start is not in the past, the length
 * is 1..12, every calendar month the term touches is offered, and no other booking holds the room on any day of it.
 */
export function monthlyProblem(input: { start: string; months: number; today: string; pricing: unknown; closedDates?: unknown; bookings?: unknown[] }): string | null {
  const { start, months, today } = input;
  if (!ISO_DAY.test(start) || start < today) return "Choose a start date from today onward.";
  if (!Number.isInteger(months) || months < 1 || months > MAX_MONTHLY_MONTHS) return `Choose between 1 and ${MAX_MONTHLY_MONTHS} months.`;
  const p = obj(input.pricing);
  if ((p.strategyType ?? "MONTHLY") !== "MONTHLY") return "This room isn't rented by the month.";
  const term = monthlyTerm(start, months);
  const last = addDays(term.end, -1);
  const [sy, sm] = start.split("-").map(Number);
  const [ey, em] = last.split("-").map(Number);
  for (let i = monthIndex(sy, sm); i <= monthIndex(ey, em); i++) {
    if (!monthOffered(p.monthly, Math.floor(i / 12), (i % 12) + 1)) return "The host doesn't rent this room for part of that period.";
  }
  for (const b of input.bookings ?? []) {
    const dated = bookedSlotsOf(b);
    if (dated.length) {
      if (dated.some((s) => term.start <= s.date && s.date < term.end)) return "Part of that period is already booked.";
      continue;
    }
    const o = obj(b);
    const dates = arr(o.selectedCalendarDates).filter((d): d is string => typeof d === "string" && ISO_DAY.test(d));
    if (dates.length) {
      if (dates.some((d) => term.start <= d && d < term.end)) return "Part of that period is already booked.";
      continue;
    }
    const t = bookingTerm({ startDate: str(o.startDate), durationMonths: num(o.durationMonths) ?? undefined });
    if (t === null || (t.start < term.end && term.start < t.end)) return "Part of that period is already booked.";
  }
  return null;
}

/** Latest number of months (0..12) a monthly booking starting on [start] can run. */
export function maxMonthlyMonths(input: { start: string; today: string; pricing: unknown; bookings?: unknown[] }): number {
  let ok = 0;
  for (let n = 1; n <= MAX_MONTHLY_MONTHS; n++) {
    if (monthlyProblem({ ...input, months: n }) !== null) break;
    ok = n;
  }
  return ok;
}
