/**
 * Pending requests whose dates have already passed are closed by the system (owner debug round, 28 Nov 2026): a host
 * could otherwise accept a booking for yesterday, and the specialist's request stayed "pending" for ever.
 * Pure rule `isStalePending` (unit-tested) + a job every 6 hours.
 */
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { runAs } from "../lib/runtimeIdentity";
import { bookingTerm, todayIso, TermFields } from "./bookingTerms";

export const STALE_REASON = "The requested dates have passed.";

/** A PENDING request whose first booked day is before [today] (yyyy-MM-dd). Undated legacy requests are never stale. */
export function isStalePending(b: TermFields & { status?: string; bookedSlots?: Array<{ date?: string }> }, today: string): boolean {
  if (b.status !== "PENDING") return false;
  const dates = (b.bookedSlots ?? []).map((s) => s.date).filter((d): d is string => typeof d === "string" && d.length >= 10);
  const first = dates.length ? dates.sort()[0] : bookingTerm(b)?.start ?? null;
  if (!first || !/^\d{4}-\d{2}-\d{2}/.test(first)) return false;
  return first.slice(0, 10) < today;
}

export const expireStaleBookingRequests = onSchedule({ ...runAs("triggers"), schedule: "every 6 hours", timeoutSeconds: 300 }, async () => {
  const db = getFirestore();
  const today = todayIso();
  const snap = await db.collection("booking_requests").where("status", "==", "PENDING").get();
  const stale = snap.docs.filter((d) => isStalePending(d.data(), today));
  for (let i = 0; i < stale.length; i += 400) {
    const batch = db.batch();
    for (const d of stale.slice(i, i + 400)) {
      batch.update(d.ref, { status: "REJECTED", rejectedBySystem: true, rejectionReason: STALE_REASON, reviewedAt: Date.now() });
    }
    await batch.commit();
  }
  logger.info("stale_requests_expired", { checked: snap.size, expired: stale.length });
});
