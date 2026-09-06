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
    }
  }
);
