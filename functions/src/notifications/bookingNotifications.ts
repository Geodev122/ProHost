import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { sendPushToUser } from "../lib/push";

/**
 * Real cross-device push the moment a Specialist fires a booking request — the
 * client-side "FCM Alert" tray (ProSpaceRepository.addFCMAlert) only ever updates
 * the device that called it, so without this the Pro Host's own phone never heard
 * about a new request unless they happened to have the app open with a live
 * Firestore listener. This is the server side that was missing.
 */
export const onBookingRequestCreated = onDocumentCreated(
  "booking_requests/{bookingId}",
  async (event) => {
    const booking = event.data?.data();
    if (!booking) return;

    await sendPushToUser(
      booking.ownerId,
      "New Booking Request",
      `${booking.practitionerName ?? "A specialist"} requested "${booking.spaceTitle ?? "your workspace"}" — ${booking.selectedDateTimeRange ?? ""}`.trim(),
      {
        category: "BOOKING_REQUEST",
        targetTab: "owner_requests",
        bookingId: event.params.bookingId,
      }
    );
  }
);

/**
 * Same fix, the other direction: the Specialist's phone should hear about an
 * accept/reject in real time too, not just whenever they next open the app.
 */
export const onBookingRequestStatusChanged = onDocumentUpdated(
  "booking_requests/{bookingId}",
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
          category: "BOOKING_ACCEPTANCE",
          targetTab: "pro_rentals",
          bookingId: event.params.bookingId,
        }
      );
    } else if (after.status === "REJECTED") {
      await sendPushToUser(
        after.practitionerId,
        "Booking Request Declined",
        `${after.ownerName ?? "The host"} declined your request for "${after.spaceTitle ?? "the workspace"}"${after.rejectionReason ? `: ${after.rejectionReason}` : "."}`,
        {
          category: "BOOKING_ACCEPTANCE",
          targetTab: "pro_rentals",
          bookingId: event.params.bookingId,
        }
      );
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
          category: "BOOKING_ACCEPTANCE",
          targetTab,
          bookingId: event.params.bookingId,
        }
      );
    }
  }
);

/**
 * Notifies the specialist when the host marks their lease payment as paid.
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
  }
);

