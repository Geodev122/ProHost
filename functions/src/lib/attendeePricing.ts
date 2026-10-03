/**
 * Server mirror of app/src/main/java/com/example/ui/util/AttendeePricing.kt — keep in sync.
 *
 * A PER_ATTENDEE subdivision prices a booking as attendees × the per-person price of
 * the tier the attendee count falls in, once for the whole booking (never per hour,
 * shift or day). Monthly rooms can't be per-attendee. The room's slot "prices" are
 * availability markers only.
 */

export interface AttendeeTierDoc {
  id?: string;
  name?: string;
  pricePerAttendeeUsd?: number;
  minAttendees?: number;
  maxAttendees?: number | null;
  isEnabled?: boolean;
}

export interface AttendeeSubdivisionDoc {
  id?: string;
  pricingMode?: string;
  pricing?: { strategyType?: string } | null;
  capacity?: number | null;
  minAttendees?: number | null;
  attendeeTiers?: AttendeeTierDoc[];
}

export interface AttendeeQuote {
  tier: AttendeeTierDoc;
  attendees: number;
  totalUsd: number;
}

export function isPerAttendee(sub: AttendeeSubdivisionDoc | undefined | null): boolean {
  return !!sub && sub.pricingMode === "PER_ATTENDEE" && (sub.pricing?.strategyType ?? "MONTHLY") !== "MONTHLY";
}

function usable(list: AttendeeTierDoc[] | undefined): AttendeeTierDoc[] {
  return (list ?? []).filter((t) => t.isEnabled !== false && (t.pricePerAttendeeUsd ?? 0) > 0);
}

/** The room's own tiers; [fallback] (admin templates) only for rooms saved before tiers existed. */
export function tiersFor(sub: AttendeeSubdivisionDoc, fallback: AttendeeTierDoc[] = []): AttendeeTierDoc[] {
  const own = usable(sub.attendeeTiers);
  return (own.length > 0 ? own : usable(fallback))
    .slice()
    .sort((a, b) => (a.minAttendees ?? 1) - (b.minAttendees ?? 1));
}

export function minAttendees(sub: AttendeeSubdivisionDoc, tiers: AttendeeTierDoc[]): number {
  const tierMin = tiers.length > 0 ? Math.min(...tiers.map((t) => t.minAttendees ?? 1)) : 1;
  return Math.max(sub.minAttendees ?? 1, tierMin, 1);
}

/** Null = no upper limit. The room's capacity always caps it. */
export function maxAttendees(sub: AttendeeSubdivisionDoc, tiers: AttendeeTierDoc[]): number | null {
  const open = tiers.length === 0 || tiers.some((t) => t.maxAttendees == null);
  const tierMax = open ? null : Math.max(...tiers.map((t) => t.maxAttendees ?? 0));
  const caps = [sub.capacity, tierMax].filter((v): v is number => typeof v === "number");
  return caps.length > 0 ? Math.min(...caps) : null;
}

export function quote(sub: AttendeeSubdivisionDoc, count: number, fallback: AttendeeTierDoc[] = []): AttendeeQuote | null {
  if (!Number.isInteger(count)) return null;
  const tiers = tiersFor(sub, fallback);
  if (count < minAttendees(sub, tiers)) return null;
  const max = maxAttendees(sub, tiers);
  if (max != null && count > max) return null;
  const tier = tiers.find((t) => count >= (t.minAttendees ?? 1) && (t.maxAttendees == null || count <= t.maxAttendees));
  if (!tier) return null;
  return { tier, attendees: count, totalUsd: count * (tier.pricePerAttendeeUsd ?? 0) };
}

/** "20 attendees × $8/person (Standard)" for emails and pushes. */
export function describeBooking(booking: { attendeeCount?: number; attendeePackagePriceUsd?: number; attendeePackageName?: string | null }): string | null {
  const count = booking.attendeeCount ?? 0;
  if (count <= 0) return null;
  const price = booking.attendeePackagePriceUsd ?? 0;
  const perPerson = price > 0 ? ` × $${Number.isInteger(price) ? price : price.toFixed(2)}/person` : "";
  const tier = booking.attendeePackageName ? ` (${booking.attendeePackageName})` : "";
  return `${count} attendee${count === 1 ? "" : "s"}${perPerson}${tier}`;
}
