import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";

/**
 * Mirrors SpaceCalculationUtils.hoursOverlap/bookingScope/bookingDays/
 * findAcceptConflict (Kotlin, app/src/main/java/com/example/ui/util/
 * SpaceCalculationUtils.kt) — the same conflict rule, ported so it can be
 * enforced server-side too. That Kotlin version is real, but it only runs
 * inside ProHostRepository.acceptBookingRequest — a client-writable code
 * path. firestore.rules' booking_requests update rule now blocks a
 * practitioner from self-accepting, but nothing there stops the OWNER's own
 * account (or a raw Firestore SDK write, or a modified client) from writing
 * status: ACCEPTED on a request that overlaps one they already accepted —
 * checking "does any other document conflict" needs a collection query,
 * which security rules can't express. This trigger is the real backstop.
 */

interface BookingFormula {
  type?: string;
  startHour?: string;
  endHour?: string;
  daysOfWeek?: string[];
}

interface BookingDoc {
  id?: string;
  spaceId?: string;
  subdivisionId?: string;
  status?: string;
  replacesBookingId?: string;
  selectedDays?: string[];
  formula?: BookingFormula;
  ownerId?: string;
  ownerName?: string;
  practitionerId?: string;
  practitionerName?: string;
  spaceTitle?: string;
}

function parseHour(value: string | undefined): number | null {
  if (!value) return null;
  const n = Number(value.split(":")[0]);
  return Number.isFinite(n) ? n : null;
}

function hoursOverlap(aStart?: string, aEnd?: string, bStart?: string, bEnd?: string): boolean {
  const s1 = parseHour(aStart);
  const e1 = parseHour(aEnd);
  const s2 = parseHour(bStart);
  const e2 = parseHour(bEnd);
  if (s1 === null || e1 === null || s2 === null || e2 === null) return false;
  return s1 < e2 && s2 < e1;
}

function bookingScope(booking: BookingDoc): string | undefined {
  return booking.subdivisionId ?? booking.spaceId;
}

function bookingDays(booking: BookingDoc): string[] {
  if (booking.selectedDays && booking.selectedDays.length > 0) return booking.selectedDays;
  return booking.formula?.daysOfWeek ?? [];
}

/**
 * The already-ACCEPTED booking [candidateId]/[candidate] conflicts with, or
 * undefined when clear — same scoping/overlap rule as the Kotlin original.
 */
function findConflict(
  candidateId: string,
  candidate: BookingDoc,
  others: Array<{ id: string; data: BookingDoc }>
): { id: string; data: BookingDoc } | undefined {
  return others.find(({ id, data: other }) => {
    if (id === candidateId) return false;
    if (id === candidate.replacesBookingId) return false;
    if (other.status !== "ACCEPTED") return false;
    if (other.spaceId !== candidate.spaceId) return false;
    if (bookingScope(other) !== bookingScope(candidate)) return false;
    if (other.formula?.type === "FULL_MONTH" || candidate.formula?.type === "FULL_MONTH") return true;
    const daysOverlap = bookingDays(other).some((d) => bookingDays(candidate).includes(d));
    if (!daysOverlap) return false;
    return hoursOverlap(other.formula?.startHour, other.formula?.endHour, candidate.formula?.startHour, candidate.formula?.endHour);
  });
}

/**
 * Fires on every booking_requests write; only acts on a transition INTO
 * ACCEPTED (before.status !== "ACCEPTED" && after.status === "ACCEPTED") —
 * this both scopes the collection query to just the moment it matters and
 * prevents a loop: the revert below writes status back to PENDING, which
 * fails that same guard on the write it just made.
 */
export const onBookingAcceptConflictGuard = onDocumentWritten(
  "booking_requests/{bookingId}",
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;
    const data = after.data() as BookingDoc;
    if (data.status !== "ACCEPTED") return;
    const before = event.data?.before?.data() as BookingDoc | undefined;
    if (before?.status === "ACCEPTED") return;

    const spaceId = data.spaceId;
    if (!spaceId) return;

    const db = getFirestore();
    const conflictQuery = db
      .collection("booking_requests")
      .where("spaceId", "==", spaceId)
      .where("status", "==", "ACCEPTED");

    // A plain query-then-write here raced: two overlapping bookings accepted
    // at nearly the same moment could each run their query snapshot before
    // the other's ACCEPTED write had landed, so neither ever saw the other as
    // a conflict and both stayed ACCEPTED — the exact double-booking this
    // trigger exists to catch, just delayed past the query instead of
    // prevented. Wrapping the read and the conditional revert in one Firestore
    // transaction closes that: if this booking and a concurrently-accepted
    // one both read the same query, Firestore serializes their commits and
    // retries the loser with a fresh read, so the second one to actually
    // commit always sees the first's ACCEPTED status. Side effects (audit
    // log, push) run once after the transaction settles, not inside it —
    // Firestore can retry a transaction's callback on contention, and neither
    // of those is safe to fire more than once for the same revert.
    const conflict = await db.runTransaction(async (tx) => {
      const snap = await tx.get(conflictQuery);
      const others = snap.docs
        .filter((d) => d.id !== event.params.bookingId)
        .map((d) => ({ id: d.id, data: d.data() as BookingDoc }));

      const found = findConflict(event.params.bookingId, data, others);
      if (!found) return undefined;

      // Reverted to PENDING, not REJECTED — this wasn't a real decision the
      // host made about the request itself, just an accept that can't stand;
      // PENDING puts it back in the host's queue needing a real decision.
      tx.set(
        after.ref,
        { status: "PENDING", rejectionReason: `Auto-reverted: overlaps accepted booking ${found.id}.` },
        { merge: true }
      );
      return found;
    });
    if (!conflict) return;

    await recordAuditLog({
      actionType: "BOOKING_ACCEPT_REVERTED_CONFLICT",
      details:
        `Booking ${event.params.bookingId} (${data.practitionerName ?? "specialist"}) was accepted but overlaps ` +
        `already-accepted booking ${conflict.id} (${conflict.data.practitionerName ?? "specialist"}) for space ` +
        `${spaceId} — reverted to PENDING server-side.`,
      actorEmail: "system@prohost.app",
      severity: "SECURE",
    });

    if (data.ownerId) {
      await sendPushToUser(
        data.ownerId,
        "Accept Reverted — Booking Conflict",
        `Accepting "${data.spaceTitle ?? "this booking"}" for ${data.practitionerName ?? "a specialist"} overlapped an already-accepted booking, so it's back to Pending. Please review.`,
        {
          category: "BOOKING_REQUEST",
          targetTab: "owner_requests",
          bookingId: event.params.bookingId,
        }
      );
    }
  }
);
