/**
 * Owner rule (28 Nov 2026): a host may switch a slot of a published listing off (hour, shift, day, month, a closed date) only
 * when no booking and no active request uses it. `keepUsedSlots` takes the listing as saved and puts back every slot that a
 * pending or accepted booking whose term hasn't ended still uses. Pure; mirror of shared/.../ui/util/SlotUsage.kt (shared
 * vectors tests/vectors/slot-usage.json — keep identical). Which booking uses which slot is `bookingUses` from
 * bookings/priceChange.ts, the matcher Change price already shares with the app.
 */
import { bookingUses, PriceBookingDoc, PricingDoc, SlotRef, weekdayOf } from "../bookings/priceChange";
import { bookingTerm, hasEnded } from "../bookings/bookingTerms";

interface MonthYearRange { fromMonth?: number; fromYear?: number; toMonth?: number; toYear?: number }
interface MonthlyDoc {
  rateUsd?: number; fromMonth?: number; fromYear?: number; isIndefinite?: boolean;
  toMonth?: number | null; toYear?: number | null; excludedRanges?: MonthYearRange[];
}
export type SlotPricingDoc = Omit<PricingDoc, "monthly"> & { monthly?: MonthlyDoc | null };
interface ScheduleDoc { closedDates?: string[]; [k: string]: unknown }
interface SubdivisionDoc { id?: string; pricing?: SlotPricingDoc; scheduleOverride?: ScheduleDoc | null; [k: string]: unknown }
export interface SlotListingDoc {
  pricing?: SlotPricingDoc;
  subdivisions?: SubdivisionDoc[];
  schedule?: ScheduleDoc;
  [k: string]: unknown;
}
export type SlotBookingDoc = PriceBookingDoc & { id?: string; displayCode?: string; rejectedBySystem?: boolean };

export interface KeepResult {
  /** Only the fields that changed, ready for an update (pricing / subdivisions / schedule). */
  patch: Partial<SlotListingDoc>;
  kept: string[];
}

export function isActive(b: SlotBookingDoc, today: string): boolean {
  return (b.status === "PENDING" || b.status === "ACCEPTED") && b.rejectedBySystem !== true && !hasEnded(b, today);
}

/** The priced, offered slots of a pricing (same set as PriceChange.slotsOfPricing in Kotlin, refs only). */
export function offeredSlots(scopeId: string, p: SlotPricingDoc | null | undefined): SlotRef[] {
  if (!p) return [];
  switch (p.strategyType) {
    case "MONTHLY":
      return (p.monthly?.rateUsd ?? 0) > 0 ? [{ scopeId, kind: "MONTHLY", key: "" }] : [];
    case "HOURLY":
      return Object.entries(p.hourly?.cellPrices ?? {})
        .filter(([key, price]) => price > 0 && Number.isFinite(Number(key.split("|")[1])))
        .map(([key]) => ({ scopeId, kind: "HOURLY", key }));
    case "SHIFT_BASED": {
      const dist = p.shiftBased?.distribution ?? {};
      return (p.shiftBased?.shifts ?? [])
        .filter((s) => !s.isUnavailable && (s.price ?? 0) > 0 && Object.values(dist).some((names) => (names ?? []).includes(String(s.name))))
        .map((s) => ({ scopeId, kind: "SHIFT", key: String(s.name) }));
    }
    case "DAY_BASED":
      return Object.entries(p.dayBased?.distribution ?? {})
        .filter(([, d]) => (d?.price ?? 0) > 0)
        .map(([day]) => ({ scopeId, kind: "DAY", key: day }));
    default:
      return [];
  }
}

const same = (a: SlotRef, b: SlotRef) => a.scopeId === b.scopeId && a.kind === b.kind && a.key === b.key;
const code = (b: SlotBookingDoc) => b.displayCode || b.id || "";

function restore(p: SlotPricingDoc, old: SlotPricingDoc, ref: SlotRef): SlotPricingDoc {
  switch (ref.kind) {
    case "HOURLY": {
      const price = old.hourly?.cellPrices?.[ref.key];
      if (price === undefined) return p;
      return { ...p, hourly: { cellPrices: { ...(p.hourly?.cellPrices ?? {}), [ref.key]: price } } };
    }
    case "SHIFT": {
      const oldCfg = old.shiftBased;
      const oldShift = oldCfg?.shifts?.find((s) => String(s.name) === ref.key);
      if (!oldCfg || !oldShift) return p;
      const cfg = p.shiftBased ?? { shifts: [], distribution: {} };
      const shifts = (cfg.shifts ?? []).some((s) => String(s.name) === ref.key)
        ? (cfg.shifts ?? []).map((s) => (String(s.name) === ref.key ? oldShift : s))
        : [...(cfg.shifts ?? []), oldShift];
      const dist: Record<string, string[]> = { ...(cfg.distribution ?? {}) };
      for (const [day, names] of Object.entries(oldCfg.distribution ?? {})) {
        if ((names ?? []).includes(ref.key)) dist[day] = Array.from(new Set([...(dist[day] ?? []), ref.key]));
      }
      return { ...p, shiftBased: { ...cfg, shifts, distribution: dist } };
    }
    case "DAY": {
      const oldDay = old.dayBased?.distribution?.[ref.key];
      const cfg = p.dayBased ?? (old.dayBased ? { ...old.dayBased, distribution: {} } : null);
      if (!oldDay || !cfg) return p;
      return { ...p, dayBased: { ...cfg, distribution: { ...(cfg.distribution ?? {}), [ref.key]: oldDay } } };
    }
    case "MONTHLY": {
      const cfg = p.monthly ?? old.monthly;
      if (!cfg) return p;
      return { ...p, monthly: { ...cfg, rateUsd: (cfg.rateUsd ?? 0) > 0 ? cfg.rateUsd : (old.monthly?.rateUsd ?? 0) } };
    }
    default:
      return p;
  }
}

const monthIndex = (month: number, year: number) => year * 12 + month - 1;

export function bookedMonths(b: SlotBookingDoc): number[] {
  const m = /^(\d{4})-(\d{2})/.exec(String(b.startDate ?? ""));
  if (!m) return [];
  const first = monthIndex(Number(m[2]), Number(m[1]));
  const n = Math.max(1, Math.floor(Number(b.durationMonths ?? 1)));
  return Array.from({ length: n }, (_, i) => first + i);
}

export function monthOffered(cfg: MonthlyDoc | null | undefined, m: number): boolean {
  if (!cfg || (cfg.rateUsd ?? 0) <= 0) return false;
  if (m < monthIndex(cfg.fromMonth ?? 1, cfg.fromYear ?? 2026)) return false;
  if (cfg.isIndefinite === false && cfg.toMonth != null && cfg.toYear != null && m > monthIndex(cfg.toMonth, cfg.toYear)) return false;
  return !(cfg.excludedRanges ?? []).some((r) =>
    m >= monthIndex(r.fromMonth ?? 1, r.fromYear ?? 0) && m <= monthIndex(r.toMonth ?? 12, r.toYear ?? 9999));
}

export function keepPricing(
  spaceId: string, scopeId: string, old: SlotPricingDoc | undefined, edited: SlotPricingDoc | undefined, bookings: SlotBookingDoc[],
): { pricing: SlotPricingDoc | undefined; kept: string[] } {
  if (!old || !edited || bookings.length === 0 || old.strategyType !== edited.strategyType) return { pricing: edited, kept: [] };
  let p: SlotPricingDoc = edited;
  const kept: string[] = [];
  for (const ref of offeredSlots(scopeId, old)) {
    const stillOffered = offeredSlots(scopeId, p).some((r) => same(r, ref));
    const dropped = bookings.filter((b) => bookingUses(b, spaceId, old as PricingDoc, ref) &&
      (!stillOffered || !bookingUses(b, spaceId, p as PricingDoc, ref)));
    if (dropped.length === 0) continue;
    p = restore(p, old, ref);
    kept.push(`${ref.kind}${ref.key ? " " + ref.key : ""} · ${code(dropped[0])}`);
  }
  if (old.strategyType === "MONTHLY" && old.monthly && p.monthly) {
    const nm = p.monthly;
    const om = old.monthly;
    const outside = bookings.filter((b) => b.formula?.type === "FULL_MONTH")
      .find((b) => bookedMonths(b).some((m) => !monthOffered(nm, m)) && bookedMonths(b).every((m) => monthOffered(om, m)));
    if (outside) {
      p = { ...p, monthly: { ...nm, fromMonth: om.fromMonth, fromYear: om.fromYear, isIndefinite: om.isIndefinite,
        toMonth: om.toMonth, toYear: om.toYear, excludedRanges: om.excludedRanges } };
      kept.push(`MONTHLY window · ${code(outside)}`);
    }
  }
  return { pricing: p, kept };
}

/** Whether booking [b] takes place on [date] (yyyy-MM-dd). */
export function needsDate(b: SlotBookingDoc, date: string): boolean {
  if ((b.bookedSlots ?? []).length > 0) return (b.bookedSlots ?? []).some((s) => s.date === date);
  if ((b.selectedCalendarDates ?? []).length > 0) return (b.selectedCalendarDates ?? []).includes(date);
  const term = bookingTerm(b);
  if (!term || date < term.start || date >= term.end) return false;
  if (b.formula?.type === "FULL_MONTH") return true;
  const days = (b.selectedDays ?? []).length > 0 ? b.selectedDays ?? [] : b.formula?.daysOfWeek ?? [];
  return days.includes(weekdayOf(date));
}

function keepOpenDates(schedule: ScheduleDoc | null | undefined, closedBefore: string[], bookings: SlotBookingDoc[]) {
  if (!schedule || bookings.length === 0) return { schedule, kept: [] as string[] };
  const added = (schedule.closedDates ?? []).filter((d) => !closedBefore.includes(d));
  const reopened = added.map((d) => ({ d, b: bookings.find((b) => needsDate(b, d)) })).filter((x) => x.b);
  if (reopened.length === 0) return { schedule, kept: [] as string[] };
  const drop = new Set(reopened.map((x) => x.d));
  return {
    schedule: { ...schedule, closedDates: (schedule.closedDates ?? []).filter((d) => !drop.has(d)) },
    kept: reopened.map((x) => `${x.d} · ${code(x.b!)}`),
  };
}

export function keepUsedSlots(
  spaceId: string, before: SlotListingDoc, after: SlotListingDoc, bookings: SlotBookingDoc[], today: string,
): KeepResult {
  const active = bookings.filter((b) => b.spaceId === spaceId && isActive(b, today));
  const patch: Partial<SlotListingDoc> = {};
  const kept: string[] = [];
  if (active.length === 0) return { patch, kept };

  const whole = active.filter((b) => !b.subdivisionId || b.subdivisionId === spaceId);
  const top = keepPricing(spaceId, spaceId, before.pricing, after.pricing, whole);
  if (top.kept.length) { patch.pricing = top.pricing; kept.push(...top.kept); }

  let subsChanged = false;
  const subs = (after.subdivisions ?? []).map((sub) => {
    const prev = (before.subdivisions ?? []).find((s) => s.id === sub.id);
    const inRoom = active.filter((b) => b.subdivisionId === sub.id);
    if (!prev || inRoom.length === 0) return sub;
    const r = keepPricing(spaceId, String(sub.id), prev.pricing, sub.pricing, inRoom);
    const c = keepOpenDates(sub.scheduleOverride, prev.scheduleOverride?.closedDates ?? [], inRoom);
    if (!r.kept.length && !c.kept.length) return sub;
    subsChanged = true;
    kept.push(...r.kept, ...c.kept);
    return { ...sub, pricing: r.pricing, scheduleOverride: c.schedule ?? sub.scheduleOverride };
  });
  if (subsChanged) patch.subdivisions = subs;

  const top2 = keepOpenDates(after.schedule, before.schedule?.closedDates ?? [], active);
  if (top2.kept.length) { patch.schedule = top2.schedule ?? undefined; kept.push(...top2.kept); }
  return { patch, kept };
}
