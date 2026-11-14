import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { runAs } from "../lib/runtimeIdentity";
import { bookedSlotsOf } from "./dateSlots";

/**
 * booking_occupancy/{bookingId}: the public, person-free projection of every ACCEPTED booking —
 * only what locks a slot (listing, room, schedule, term). Specialists may read only their own
 * booking_requests, so without this every slot another specialist holds looked free to them.
 * Readable by any signed-in user, written only here (firestore.rules). The app merges it into
 * its lock checks (SpaceCalculationUtils.isSlotLocked & co).
 */
export const OCCUPANCY_COLLECTION = "booking_occupancy";

export function occupancyFields(d: FirebaseFirestore.DocumentData): Record<string, unknown> {
  const f = (d.formula ?? {}) as Record<string, unknown>;
  return {
    spaceId: d.spaceId,
    subdivisionId: d.subdivisionId ?? null,
    status: "ACCEPTED",
    formula: {
      type: f.type ?? null,
      startHour: f.startHour ?? null,
      endHour: f.endHour ?? null,
      daysOfWeek: Array.isArray(f.daysOfWeek) ? f.daysOfWeek : [],
    },
    selectedDays: Array.isArray(d.selectedDays) ? d.selectedDays : [],
    selectedCalendarDates: Array.isArray(d.selectedCalendarDates) ? d.selectedCalendarDates : [],
    // Booking on real dates: the exact slots held (no person, no price), so other people's pickers lock them precisely.
    bookedSlots: bookedSlotsOf(d).map((x) => ({ date: x.date, key: x.key, startHour: x.startHour, endHour: x.endHour })),
    selectedStartHour: d.selectedStartHour ?? null,
    selectedEndHour: d.selectedEndHour ?? null,
    startDate: typeof d.startDate === "string" ? d.startDate : "",
    durationMonths: typeof d.durationMonths === "number" ? d.durationMonths : 1,
    updatedAt: Date.now(),
  };
}

export const onBookingOccupancySync = onDocumentWritten({ document: "booking_requests/{bookingId}", ...runAs("triggers") }, async (event) => {
  const after = event.data?.after?.data();
  const ref = getFirestore().collection(OCCUPANCY_COLLECTION).doc(event.params.bookingId);
  if (after && after.status === "ACCEPTED" && typeof after.spaceId === "string") {
    await ref.set(occupancyFields(after));
  } else {
    await ref.delete().catch((e) => logger.warn(`occupancy delete ${event.params.bookingId}: ${e}`));
  }
});
