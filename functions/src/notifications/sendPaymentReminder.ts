import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getFirestore } from "firebase-admin/firestore";
import { sendPushToUser } from "../lib/push";
import "../lib/admin";

interface SendPaymentReminderData {
  bookingId?: string;
}

/**
 * Real cross-device push for the Pro Host's "Send Payment Reminder" button
 * (Renting Requests screen) — this used to only post a local alert on the
 * host's own device (via ProSpaceRepository.addFCMAlert), never actually
 * reaching the specialist. Only the booking's own host (or an Admin) can
 * trigger this, and only for a booking that's actually ACCEPTED.
 */
export const sendPaymentReminder = onCall<SendPaymentReminderData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const { bookingId } = request.data ?? {};
  if (!bookingId) {
    throw new HttpsError("invalid-argument", "bookingId is required.");
  }

  const db = getFirestore();
  const snap = await db.collection("booking_requests").doc(bookingId).get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Booking not found.");
  }
  const booking = snap.data() as Record<string, any>;

  if (booking.ownerId !== auth.uid && auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only the host can send a payment reminder for this booking.");
  }
  if (booking.status !== "ACCEPTED") {
    throw new HttpsError("failed-precondition", "Can only send a payment reminder for an accepted booking.");
  }

  await sendPushToUser(
    String(booking.practitionerId ?? ""),
    "Payment Reminder",
    `Friendly reminder to settle payment for your booking of "${booking.spaceTitle ?? "the workspace"}" with host ${booking.ownerName ?? "your host"}.`,
    {
      category: "PAYMENT_REMINDER",
      targetTab: "pro_rentals",
      bookingId,
    }
  );

  return { ok: true };
});
