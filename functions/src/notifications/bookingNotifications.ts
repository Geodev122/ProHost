import { logger } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { sendPushToUser } from "../lib/push";
import { sendEmail } from "../lib/email";
import {
  newBookingRequestTemplate,
  bookingAcceptedTemplate,
  bookingRejectedTemplate,
  UserContext,
  BookingContext,
} from "../lib/emailTemplates";
import {
  isPerAttendee,
  quote,
  describeBooking,
  AttendeeSubdivisionDoc,
  AttendeeTierDoc,
} from "../lib/attendeePricing";
import "../lib/admin";
import { sendGa4Event } from "../lib/ga4";

/**
 * Server check for per-attendee bookings (the client computes the total, so it must
 * not be trusted): the count must fit the room's limits and tiers, and the stored
 * per-person price and total must match the room's current pricing. Returns a
 * user-facing reason when the request must be rejected, else null.
 */
async function attendeeBookingProblem(booking: FirebaseFirestore.DocumentData): Promise<string | null> {
  const db = getFirestore();
  const count = Number(booking.attendeeCount ?? 0);
  if (!booking.subdivisionId) {
    return count > 0 ? "This space isn't priced per attendee." : null;
  }
  const listingSnap = await db.collection("workspace_listings").doc(String(booking.spaceId)).get();
  const subs = (listingSnap.data()?.subdivisions ?? []) as AttendeeSubdivisionDoc[];
  const sub = subs.find((s) => s.id === booking.subdivisionId);
  if (!isPerAttendee(sub)) {
    return count > 0 ? "This room isn't priced per attendee." : null;
  }
  const schemaSnap = await db.doc("schema_architecture/main").get();
  const fallback = (schemaSnap.data()?.attendeePackages ?? []) as AttendeeTierDoc[];
  const q = sub ? quote(sub, count, fallback) : null;
  if (!q) return "The number of attendees is outside this room's limits.";
  const total = Number(booking.totalAmountUsd ?? 0);
  const perPerson = Number(booking.attendeePackagePriceUsd ?? 0);
  if (Math.abs(total - q.totalUsd) > 0.01 || Math.abs(perPerson - (q.tier.pricePerAttendeeUsd ?? 0)) > 0.01) {
    return "The price no longer matches this room's attendee pricing. Please send a new request.";
  }
  return null;
}

/**
 * Real cross-device push the moment a Specialist fires a booking request — the
 * client-side "FCM Alert" tray (ProSpaceRepository.addFCMAlert) only ever updates
 * the device that called it, so without this the Pro Host's own phone never heard
 * about a new request unless they happened to have the app open with a live
 * Firestore listener. This is the server side that was missing.
 */
export const onBookingRequestCreated = onDocumentCreated(
  { document: "booking_requests/{bookingId}" },
  async (event) => {
    const booking = event.data?.data();
    if (!booking) return;

    // Fail closed: a per-attendee booking whose price can't be checked never reaches the
    // host (one retry for a transient read error first).
    let problem: string | null;
    try {
      problem = await attendeeBookingProblem(booking)
        .catch(() => attendeeBookingProblem(booking));
    } catch (e) {
      logger.error("onBookingRequestCreated: couldn't verify the attendee price", e);
      problem = "We couldn't confirm the price for this request. Please send it again.";
    }
    if (problem) {
      // Never reaches the host; the status change notifies the specialist with the reason.
      // Only while still PENDING, and never recreating a booking deleted meanwhile.
      const ref = event.data?.ref;
      const rejected = ref ? await getFirestore().runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (!snap.exists || snap.data()?.status !== "PENDING") return false;
        tx.update(ref, { status: "REJECTED", rejectionReason: problem, rejectedBySystem: true, reviewedAt: Date.now() });
        return true;
      }) : false;
      if (!rejected) return;
      if (typeof booking.practitionerId === "string") {
        await sendGa4Event(booking.practitionerId, "booking_auto_rejected", { reason: "attendee_price_mismatch" });
      }
      return;
    }

    const attendeeSummary = describeBooking(booking);
    await sendPushToUser(
      booking.ownerId,
      "New Booking Request",
      `${booking.practitionerName ?? "A specialist"} requested "${booking.spaceTitle ?? "your workspace"}" — ${booking.selectedDateTimeRange ?? ""}${attendeeSummary ? ` · ${attendeeSummary}` : ""}`.trim(),
      {
        category: "BOOKING_REQUEST",
        targetTab: "owner_requests",
        bookingId: event.params.bookingId,
      }
    );

    try {
      const db = getFirestore();
      const ownerSnap = await db.collection("user_profiles").doc(booking.ownerId).get();
      const ownerData = ownerSnap.data();
      if (ownerData?.email) {
        const ownerCtx: UserContext = {
          fullName: ownerData.fullName ?? "Host",
          email: ownerData.email,
          role: (ownerData.role ?? "PRO_HOST") as UserContext["role"],
        };
        const bookingCtx: BookingContext = {
          bookingId: event.params.bookingId,
          listingTitle: booking.spaceTitle ?? "your workspace",
          specialistName: booking.practitionerName ?? "A specialist",
          ownerName: booking.ownerName ?? ownerData.fullName ?? "Host",
          dateRange: booking.selectedDateTimeRange ?? "",
          totalUsd: Number(booking.totalAmountUsd ?? 0),
          attendeeSummary,
        };
        const tpl = newBookingRequestTemplate(ownerCtx, bookingCtx);
        await sendEmail({ to: ownerData.email, ...tpl });
      }
    } catch (_) { /* email is best-effort */ }
  }
);

/**
 * Same fix, the other direction: the Specialist's phone should hear about an
 * accept/reject in real time too, not just whenever they next open the app.
 */
export const onBookingRequestStatusChanged = onDocumentUpdated(
  { document: "booking_requests/{bookingId}" },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;
    if (before.status === after.status) return;

    if (after.status === "ACCEPTED") {
      await sendPushToUser(
        after.practitionerId,
        "Booking Request Accepted",
        `${after.ownerName ?? "The host"} accepted your request for "${after.spaceTitle ?? "the workspace"}".`,
        {
          category: "BOOKING_UPDATE",
          targetTab: "pro_rentals",
          bookingId: event.params.bookingId,
        }
      );
      try {
        const db = getFirestore();
        const practSnap = await db.collection("user_profiles").doc(after.practitionerId).get();
        const practData = practSnap.data();
        if (practData?.email) {
          const specialistCtx: UserContext = {
            fullName: practData.fullName ?? "Specialist",
            email: practData.email,
            role: (practData.role ?? "SPECIALIST") as UserContext["role"],
          };
          const bookingCtx: BookingContext = {
            bookingId: event.params.bookingId,
            listingTitle: after.spaceTitle ?? "the workspace",
            specialistName: practData.fullName ?? after.practitionerName ?? "Specialist",
            ownerName: after.ownerName ?? "Host",
            dateRange: after.selectedDateTimeRange ?? "",
            totalUsd: Number(after.totalAmountUsd ?? 0),
            attendeeSummary: describeBooking(after),
            rejectionReason: after.rejectionReason ?? null,
          };
          const tpl = bookingAcceptedTemplate(specialistCtx, bookingCtx);
          await sendEmail({ to: practData.email, ...tpl });
        }
      } catch (_) { /* email is best-effort */ }
    } else if (after.status === "REJECTED") {
      await sendPushToUser(
        after.practitionerId,
        after.rejectedBySystem ? "Booking Request Not Sent" : "Booking Request Declined",
        after.rejectedBySystem
          ? `Your request for "${after.spaceTitle ?? "the workspace"}" couldn't be sent: ${after.rejectionReason ?? "please try again."}`
          : `${after.ownerName ?? "The host"} declined your request for "${after.spaceTitle ?? "the workspace"}"${after.rejectionReason ? `: ${after.rejectionReason}` : "."}`,
        {
          category: "BOOKING_UPDATE",
          targetTab: "pro_rentals",
          bookingId: event.params.bookingId,
        }
      );
      try {
        const db = getFirestore();
        const practSnap = await db.collection("user_profiles").doc(after.practitionerId).get();
        const practData = practSnap.data();
        if (practData?.email) {
          const specialistCtx: UserContext = {
            fullName: practData.fullName ?? "Specialist",
            email: practData.email,
            role: (practData.role ?? "SPECIALIST") as UserContext["role"],
          };
          const bookingCtx: BookingContext = {
            bookingId: event.params.bookingId,
            listingTitle: after.spaceTitle ?? "the workspace",
            specialistName: practData.fullName ?? after.practitionerName ?? "Specialist",
            ownerName: after.ownerName ?? "Host",
            dateRange: after.selectedDateTimeRange ?? "",
            totalUsd: Number(after.totalAmountUsd ?? 0),
            attendeeSummary: describeBooking(after),
            rejectionReason: after.rejectionReason ?? null,
          };
          const tpl = bookingRejectedTemplate(specialistCtx, bookingCtx);
          await sendEmail({ to: practData.email, ...tpl });
        }
      } catch (_) { /* email is best-effort */ }
    } else if (after.status === "CANCELLED" && before.status === "ACCEPTED") {
      // Early termination (ProSpaceRepository.cancelAcceptedBooking) — notify
      // whichever side didn't initiate it. cancelledByRole is stamped by that
      // call, distinguishing this from a superseded-by-edit cancellation
      // (acceptBookingRequest's own CANCELLED write for the replaced booking,
      // which never sets cancelledByRole and isn't a real termination event
      // worth pushing about — the practitioner already knows, they just got
      // their edit accepted).
      if (!after.cancelledByRole) return;
      const cancelledByHost = after.cancelledByRole === "PRO_HOST" || after.cancelledByRole === "ADMIN";
      const recipientId = cancelledByHost ? after.practitionerId : after.ownerId;
      const initiatorLabel = cancelledByHost ? (after.ownerName ?? "The host") : (after.practitionerName ?? "The specialist");
      const targetTab = cancelledByHost ? "pro_rentals" : "owner_progress";
      await sendPushToUser(
        recipientId,
        "Booking Cancelled",
        `${initiatorLabel} ended the accepted booking for "${after.spaceTitle ?? "the workspace"}" early${after.cancellationReasonCode ? ` (${String(after.cancellationReasonCode).replace(/_/g, " ").toLowerCase()})` : ""}.`,
        {
          category: "BOOKING_UPDATE",
          targetTab,
          bookingId: event.params.bookingId,
        }
      );
    }
  }
);

/**
 * Notifies both parties when either side marks the lease payment as acknowledged.
 * - Host marks paid → notifies the specialist (pro_rentals tab).
 * - Specialist marks paid → notifies the host (owner_progress tab).
 */
export const onBookingPaymentAcknowledged = onDocumentUpdated(
  "booking_requests/{bookingId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    if (!before.paymentAcknowledgedByHost && after.paymentAcknowledgedByHost) {
      await sendPushToUser(
        after.practitionerId,
        "Payment Acknowledged",
        `The host (${after.ownerName ?? "Host"}) has marked your lease payment for "${after.spaceTitle ?? "workspace"}" as paid.`,
        {
          category: "PAYMENT_REMINDER",
          targetTab: "pro_rentals",
          bookingId: event.params.bookingId,
        }
      );
    }

    if (!before.paymentAcknowledgedBySpecialist && after.paymentAcknowledgedBySpecialist) {
      // Legacy booking docs created before ownerId was a required field may
      // not carry it; skip silently rather than crashing the trigger.
      const ownerId = after.ownerId as string | undefined;
      if (ownerId) {
        await sendPushToUser(
          ownerId,
          "Specialist Marked Payment as Paid",
          `${after.practitionerName ?? "The specialist"} has marked their lease payment for "${after.spaceTitle ?? "workspace"}" as paid.`,
          {
            category: "PAYMENT_REMINDER",
            targetTab: "owner_progress",
            bookingId: event.params.bookingId,
          }
        );
      }
    }
  }
);

