/**
 * A booking's term: the dates it actually occupies. Mirrors SpaceCalculationUtils.bookingTerm /
 * hasEnded / termsOverlap (Kotlin). Pure, unit-tested (bookingTerms.test.ts).
 *
 * - Calendar-date bookings (Shift / Day picks): first date → day after the last date.
 * - Otherwise startDate (YYYY-MM-DD or YYYY-MM) + durationMonths (≥ 1).
 * - No parseable term (legacy docs saved a label like "Next Monday"): unknown → treated as
 *   ongoing, so old bookings stay conservative.
 * End dates are exclusive; dates compare as ISO strings.
 */
export interface TermFields {
  startDate?: string;
  durationMonths?: number;
  selectedCalendarDates?: string[];
}

export interface Term { start: string; end: string }

const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const ISO_MONTH = /^\d{4}-\d{2}$/;

export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/**
 * [iso] + [months], clamped to the last day of the target month (2026-01-31 + 1 month = 2026-02-28), exactly like
 * the app's java.util.Calendar.add(MONTH). Date.setUTCMonth would overflow into the following month instead
 * (→ 2026-03-03) and make the server believe a term ends days after the app shows it; tests/vectors pins both sides.
 */
export function addMonths(iso: string, months: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  const total = d.getUTCFullYear() * 12 + d.getUTCMonth() + months;
  const year = Math.floor(total / 12);
  const month = total - year * 12;
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return new Date(Date.UTC(year, month, Math.min(d.getUTCDate(), lastDay))).toISOString().slice(0, 10);
}

export function bookingTerm(b: TermFields): Term | null {
  const dates = (b.selectedCalendarDates ?? []).filter((d) => ISO_DAY.test(d)).sort();
  if (dates.length > 0) return { start: dates[0], end: addDays(dates[dates.length - 1], 1) };
  const raw = (b.startDate ?? "").trim().slice(0, 10);
  const start = ISO_DAY.test(raw) ? raw : ISO_MONTH.test(raw.slice(0, 7)) && raw.length === 7 ? `${raw}-01` : null;
  if (!start) return null;
  return { start, end: addMonths(start, Math.max(1, Math.floor(b.durationMonths ?? 1))) };
}

/** True once the booking's whole term is in the past (unknown terms never end). */
export function hasEnded(b: TermFields, todayIso: string): boolean {
  const t = bookingTerm(b);
  return t !== null && t.end <= todayIso;
}

/** Whether two bookings' terms share at least one day (unknown terms overlap everything). */
export function termsOverlap(a: TermFields, b: TermFields): boolean {
  const ta = bookingTerm(a);
  const tb = bookingTerm(b);
  if (!ta || !tb) return true;
  return ta.start < tb.end && tb.start < ta.end;
}

export function todayIso(now = Date.now()): string {
  return new Date(now).toISOString().slice(0, 10);
}
