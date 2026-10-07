/**
 * Slot price changes and their effect on existing bookings. Mirrors
 * app/src/main/java/com/example/ui/util/PriceChange.kt — keep both identical.
 * Pure, unit-tested (priceChange.test.ts).
 *
 * A slot is identified by SlotRef: the room (or the space, when it has no rooms), the pricing
 * kind and a key inside the stored pricing map:
 *   MONTHLY → ""        (pricing.monthly.rateUsd)
 *   HOURLY  → "Mon|9"   (pricing.hourly.cellPrices[key])
 *   SHIFT   → "MORNING" (pricing.shiftBased.shifts[name == key].price)
 *   DAY     → "Mon"     (pricing.dayBased.distribution[key].price)
 *   TIER    → tier id   (subdivision.attendeeTiers[id == key].pricePerAttendeeUsd)
 *
 * Modes for a booking that uses the slot:
 *   KEEP      → total unchanged.
 *   NOW       → every unit on/after today is re-priced (a pending request that starts later: all units).
 *   NEXT_TERM → units from the next billing period: the 1st of next month for monthly leases,
 *               next Monday for dated bookings (shift / day). Undated units (hourly, tiers) are
 *               not re-priced; the new price applies when the booking is renewed.
 */
import { bookingTerm } from "./bookingTerms";

export type SlotKind = "MONTHLY" | "HOURLY" | "SHIFT" | "DAY" | "TIER";
export type PriceMode = "KEEP" | "NOW" | "NEXT_TERM";

export interface SlotRef {
  scopeId: string;
  kind: SlotKind;
  key: string;
}

export interface PricingDoc {
  strategyType?: string;
  monthly?: { rateUsd?: number } | null;
  hourly?: { cellPrices?: Record<string, number> } | null;
  shiftBased?: {
    shifts?: Array<{ name?: string; startHour?: number; endHour?: number; isUnavailable?: boolean; price?: number }>;
    distribution?: Record<string, string[]>;
  } | null;
  dayBased?: { distribution?: Record<string, { price?: number }> } | null;
}

export interface TierDoc { id?: string; name?: string; pricePerAttendeeUsd?: number; isEnabled?: boolean }

export interface PriceBookingDoc {
  spaceId?: string;
  subdivisionId?: string | null;
  status?: string;
  startDate?: string;
  durationMonths?: number;
  selectedDays?: string[];
  selectedCalendarDates?: string[];
  totalAmountUsd?: number;
  attendeeCount?: number;
  selectedAttendeePackageId?: string | null;
  formula?: { type?: string; startHour?: string; endHour?: string; daysOfWeek?: string[] };
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export function round2(v: number): number {
  return Math.round(v * 100) / 100;
}

function parseHour(value: string | undefined): number | null {
  if (!value) return null;
  const n = Number(value.split(":")[0]);
  return Number.isFinite(n) ? n : null;
}

export function weekdayOf(iso: string): string {
  return WEEKDAYS[new Date(`${iso.slice(0, 10)}T00:00:00Z`).getUTCDay()];
}

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** The price stored for [ref] in [pricing] (or the room's tiers), or null when the slot doesn't exist. */
export function priceOf(pricing: PricingDoc | null | undefined, tiers: TierDoc[] | undefined, ref: SlotRef): number | null {
  switch (ref.kind) {
  case "MONTHLY": {
    const v = pricing?.monthly?.rateUsd;
    return typeof v === "number" ? v : null;
  }
  case "HOURLY": {
    const v = pricing?.hourly?.cellPrices?.[ref.key];
    return typeof v === "number" ? v : null;
  }
  case "SHIFT": {
    const s = (pricing?.shiftBased?.shifts ?? []).find((x) => x.name === ref.key);
    return s && typeof s.price === "number" ? s.price : null;
  }
  case "DAY": {
    const v = pricing?.dayBased?.distribution?.[ref.key]?.price;
    return typeof v === "number" ? v : null;
  }
  case "TIER": {
    const t = (tiers ?? []).find((x) => x.id === ref.key);
    return t && typeof t.pricePerAttendeeUsd === "number" ? t.pricePerAttendeeUsd : null;
  }
  }
  return null;
}

/** A copy of [pricing] with exactly the slot [ref] set to [price] (TIER refs live outside pricing). */
export function withSlotPrice(pricing: PricingDoc, ref: SlotRef, price: number): PricingDoc {
  const p: PricingDoc = JSON.parse(JSON.stringify(pricing ?? {}));
  switch (ref.kind) {
  case "MONTHLY":
    p.monthly = { ...(p.monthly ?? {}), rateUsd: price };
    break;
  case "HOURLY":
    p.hourly = { ...(p.hourly ?? {}), cellPrices: { ...(p.hourly?.cellPrices ?? {}), [ref.key]: price } };
    break;
  case "SHIFT":
    if (p.shiftBased?.shifts) {
      p.shiftBased.shifts = p.shiftBased.shifts.map((s) => s.name === ref.key ? { ...s, price } : s);
    }
    break;
  case "DAY":
    if (p.dayBased) {
      p.dayBased.distribution = { ...(p.dayBased.distribution ?? {}), [ref.key]: { ...(p.dayBased.distribution?.[ref.key] ?? {}), price } };
    }
    break;
  case "TIER":
    break;
  }
  return p;
}

/** A copy of [tiers] with tier [ref.key] priced at [price]. */
export function withTierPrice(tiers: TierDoc[], ref: SlotRef, price: number): TierDoc[] {
  return tiers.map((t) => t.id === ref.key ? { ...t, pricePerAttendeeUsd: price } : t);
}

/** Days of the week a slot is offered on (null = not day-bound: monthly, tiers). */
export function slotDays(pricing: PricingDoc | null | undefined, ref: SlotRef): string[] | null {
  switch (ref.kind) {
  case "HOURLY": return [ref.key.split("|")[0]];
  case "DAY": return [ref.key];
  case "SHIFT":
    return Object.entries(pricing?.shiftBased?.distribution ?? {})
      .filter(([, names]) => (names ?? []).includes(ref.key))
      .map(([day]) => day);
  default: return null;
  }
}

function shiftHours(pricing: PricingDoc | null | undefined, key: string): { start: number; end: number } | null {
  const s = (pricing?.shiftBased?.shifts ?? []).find((x) => x.name === key);
  if (!s || typeof s.startHour !== "number" || typeof s.endHour !== "number") return null;
  return { start: s.startHour, end: s.endHour };
}

function bookingDays(b: PriceBookingDoc): string[] {
  if (b.selectedDays && b.selectedDays.length > 0) return b.selectedDays;
  return b.formula?.daysOfWeek ?? [];
}

/** Whether booking [b] (of space [spaceId]) is charged for slot [ref]. */
export function bookingUses(b: PriceBookingDoc, spaceId: string, pricing: PricingDoc | null | undefined, ref: SlotRef): boolean {
  if (b.spaceId !== spaceId) return false;
  if ((b.subdivisionId || spaceId) !== ref.scopeId) return false;
  const type = b.formula?.type;
  const fs = parseHour(b.formula?.startHour);
  const fe = parseHour(b.formula?.endHour);
  switch (ref.kind) {
  case "MONTHLY":
    return type === "FULL_MONTH";
  case "TIER":
    return !!b.selectedAttendeePackageId && b.selectedAttendeePackageId === ref.key;
  case "HOURLY": {
    if (type !== "HOURLY") return false;
    const [day, hourStr] = ref.key.split("|");
    const hour = Number(hourStr);
    if (!bookingDays(b).includes(day) || fs === null || fe === null) return false;
    return hour >= fs && hour < fe;
  }
  case "SHIFT": {
    if (type !== "SHIFT") return false;
    const h = shiftHours(pricing, ref.key);
    if (!h || fs === null || fe === null) return false;
    if (!(h.start >= fs && h.end <= fe)) return false;
    const days = slotDays(pricing, ref) ?? [];
    const dates = b.selectedCalendarDates ?? [];
    return dates.length > 0 ? dates.some((d) => days.includes(weekdayOf(d))) : bookingDays(b).some((d) => days.includes(d));
  }
  case "DAY": {
    if (type !== "DAY_PER_WEEK") return false;
    const dates = b.selectedCalendarDates ?? [];
    return dates.length > 0 ? dates.some((d) => weekdayOf(d) === ref.key) : bookingDays(b).includes(ref.key);
  }
  }
  return false;
}

function monthIndex(iso: string): number {
  return Number(iso.slice(0, 4)) * 12 + Number(iso.slice(5, 7)) - 1;
}

/**
 * How many times booking [b] is charged for slot [ref] on or after [fromIso]; null = the
 * units aren't dated (hourly cells, attendee tiers), so only NOW can re-price them.
 */
export function unitsFrom(b: PriceBookingDoc, pricing: PricingDoc | null | undefined, ref: SlotRef, fromIso: string): number | null {
  switch (ref.kind) {
  case "MONTHLY": {
    const term = bookingTerm(b);
    const months = Math.max(1, Math.floor(b.durationMonths ?? 1));
    if (!term) return months;
    const startM = monthIndex(term.start);
    const endM = startM + months; // exclusive
    const fromM = Math.max(startM, monthIndex(fromIso));
    return Math.max(0, endM - fromM);
  }
  case "SHIFT":
  case "DAY": {
    const days = slotDays(pricing, ref) ?? [];
    const dates = b.selectedCalendarDates ?? [];
    if (dates.length === 0) return null;
    return dates.filter((d) => d >= fromIso && days.includes(weekdayOf(d))).length;
  }
  case "HOURLY":
  case "TIER":
    return null;
  }
  return null;
}

/** The first day of the next billing period after [todayIso] for this kind of slot. */
export function nextTermStart(ref: SlotRef, todayIso: string): string {
  if (ref.kind === "MONTHLY") {
    const d = new Date(`${todayIso}T00:00:00Z`);
    return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1)).toISOString().slice(0, 10);
  }
  const dow = new Date(`${todayIso}T00:00:00Z`).getUTCDay(); // 0 = Sun
  const untilMonday = ((8 - dow) % 7) || 7;
  return addDays(todayIso, untilMonday);
}

export interface Reprice {
  newTotal: number;
  /** ISO date the new price applies from, or "" when nothing on this booking changes now. */
  effectiveFrom: string;
  /** Units re-priced (0 when the change only applies on renewal). */
  units: number;
}

/** The new total for booking [b] when slot [ref] goes from [oldPrice] to [newPrice] under [mode]. */
export function reprice(
  b: PriceBookingDoc, pricing: PricingDoc | null | undefined, ref: SlotRef,
  oldPrice: number, newPrice: number, mode: PriceMode, todayIso: string
): Reprice {
  const total = b.totalAmountUsd ?? 0;
  if (mode === "KEEP") return { newTotal: round2(total), effectiveFrom: "", units: 0 };
  const term = bookingTerm(b);
  let from = mode === "NOW" ? todayIso : nextTermStart(ref, todayIso);
  if (term && term.start > from) from = term.start;
  const dated = unitsFrom(b, pricing, ref, from);
  let units: number;
  if (dated !== null) {
    units = dated;
  } else if (mode === "NOW") {
    // Undated units: every booked cell / attendee is re-priced.
    // An hourly booking pays each booked cell once; this cell is one unit.
    units = ref.kind === "TIER" ? Math.max(0, b.attendeeCount ?? 0) : 1;
  } else {
    units = 0;
  }
  if (units === 0) return { newTotal: round2(total), effectiveFrom: "", units: 0 };
  return { newTotal: round2(Math.max(0, total + (newPrice - oldPrice) * units)), effectiveFrom: from, units };
}
