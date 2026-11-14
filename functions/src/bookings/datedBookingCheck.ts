import { getFirestore } from "firebase-admin/firestore";
import { isPerAttendee } from "../lib/attendeePricing";
import { listingPricing } from "../listings/listingPrices";
import { addDays } from "./bookingTerms";
import { bookedSlotsOf, bookedSlotsProblem, monthlyProblem } from "./dateSlots";

type Data = Record<string, unknown>;
const obj = (v: unknown): Data => (v && typeof v === "object" && !Array.isArray(v) ? (v as Data) : {});
const list = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);

/** Requests from apps that book on real dates carry `dateModel: 2`; older apps' requests are left to the legacy checks. */
export const DATE_MODEL = 2;

/**
 * Server check for a dated booking request (the app computes dates, slots and the total, so none of it is trusted):
 * every slot must be offered on its date at the price sent, on an open date inside the room's booking window, and not
 * already held by an accepted booking; a monthly request must fit the host's months and not overlap anything.
 * Returns a user-facing reason when the request must be auto-rejected, else null.
 * "Today" is yesterday in UTC, so a request sent late in the evening west of UTC is never called "past".
 */
export async function datedBookingProblem(bookingId: string, booking: FirebaseFirestore.DocumentData): Promise<string | null> {
  if (booking.dateModel !== DATE_MODEL) return null;
  const db = getFirestore();
  const spaceId = String(booking.spaceId ?? "");
  const listing = (await db.collection("workspace_listings").doc(spaceId).get()).data();
  if (!listing) return "This listing is no longer available.";
  const subId = typeof booking.subdivisionId === "string" && booking.subdivisionId ? booking.subdivisionId : null;
  const sub = subId ? (Array.isArray(listing.subdivisions) ? listing.subdivisions : []).map(obj).find((s) => s.id === subId) : null;
  if (subId && !sub) return "This room is no longer available.";
  const pricing = sub ? obj(sub.pricing) : listingPricing(listing);
  const listingSchedule = obj(listing.schedule);
  const schedule = sub && sub.scheduleOverride ? obj(sub.scheduleOverride) : listingSchedule;
  const closedDates = [...list(listingSchedule.closedDates), ...list(obj(sub?.scheduleOverride).closedDates)];
  const perAttendee = sub ? isPerAttendee(sub as never) : false;

  const acceptedSnap = await db.collection("booking_requests").where("spaceId", "==", spaceId).where("status", "==", "ACCEPTED").get();
  const scope = subId ?? spaceId;
  const bookings = acceptedSnap.docs
    .filter((d) => d.id !== bookingId && d.id !== booking.replacesBookingId)
    .map((d) => d.data())
    .filter((d) => (d.subdivisionId || d.spaceId) === scope);

  const today = addDays(new Date().toISOString().slice(0, 10), -1);
  const total = Number(booking.totalAmountUsd ?? 0);

  if (obj(booking.formula).type === "FULL_MONTH" || (pricing.strategyType ?? "MONTHLY") === "MONTHLY") {
    const months = Number(booking.durationMonths ?? 0);
    const problem = monthlyProblem({ start: String(booking.startDate ?? ""), months, today, pricing, bookings });
    if (problem) return problem;
    const rate = Number(obj(pricing.monthly).rateUsd ?? 0);
    return Math.abs(total - rate * months) > 0.01 ? "The price changed. Please send a new request." : null;
  }

  const slots = bookedSlotsOf(booking);
  const problem = bookedSlotsProblem({ slots, today, pricing, schedule, closedDates, bookings, checkPrices: !perAttendee });
  if (problem) return problem;
  if (!perAttendee && Math.abs(total - slots.reduce((sum, s) => sum + s.priceUsd, 0)) > 0.01) {
    return "The total doesn't match the chosen times. Please send a new request.";
  }
  return null;
}
