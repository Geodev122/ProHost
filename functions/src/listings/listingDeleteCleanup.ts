import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";

/**
 * Deleting a listing (ProHostRepository.deleteSpaceListing) only ever removed
 * the workspace_listings document itself. Two things it never touched:
 *
 * 1. Every OTHER user's AppUser.savedSpaceIds that referenced this listing —
 *    a dangling id that persists forever, since only the favoriting user's
 *    own client ever writes that field and nothing prompts them to un-save a
 *    listing that no longer exists.
 * 2. Any still-open booking_requests (PENDING or ACCEPTED) against this
 *    listing — deleting it left a specialist with a "confirmed" booking for
 *    a space that silently vanished, with no cancellation and no notice.
 *
 * This trigger is the real backstop for both, run with the Admin SDK (a
 * deleting host/Admin isn't authorized to write another user's profile
 * directly) — same trigger-based-cleanup pattern already used by
 * favoritesSync.ts and bookingConflictGuard.ts.
 */
export const onWorkspaceListingDeletedCleanup = onDocumentDeleted(
  "workspace_listings/{spaceId}",
  async (event) => {
    const spaceId = event.params.spaceId;
    const listing = event.data?.data();
    const db = getFirestore();

    // 1. Un-favorite for every user who had saved this listing.
    const favoritedBy = await db
      .collection("user_profiles")
      .where("savedSpaceIds", "array-contains", spaceId)
      .get();
    if (!favoritedBy.empty) {
      const batch = db.batch();
      favoritedBy.docs.forEach((doc) => {
        batch.set(doc.ref, { savedSpaceIds: FieldValue.arrayRemove(spaceId) }, { merge: true });
      });
      await batch.commit();
    }

    // 2. Cancel every still-open booking against this listing and notify the
    // specialist — never REJECTED (that implies the host reviewed and
    // declined it), always CANCELLED with a reason that names what happened.
    const openBookings = await db
      .collection("booking_requests")
      .where("spaceId", "==", spaceId)
      .where("status", "in", ["PENDING", "ACCEPTED"])
      .get();

    if (!openBookings.empty) {
      const batch = db.batch();
      openBookings.docs.forEach((doc) => {
        batch.set(
          doc.ref,
          { status: "CANCELLED", rejectionReason: "The listing this request was for has been deleted." },
          { merge: true }
        );
      });
      await batch.commit();

      await Promise.all(
        openBookings.docs.map((doc) => {
          const practitionerId = doc.data().practitionerId as string | undefined;
          if (!practitionerId) return Promise.resolve();
          return sendPushToUser(
            practitionerId,
            "Listing Removed",
            `"${listing?.title ?? "A workspace listing"}" was removed by its host, so your booking request has been cancelled.`,
            {
              category: "BOOKING_REQUEST",
              targetTab: "pro_rentals",
              bookingId: doc.id,
            }
          );
        })
      );
    }

    // 3. Purge associated storage files (photos and ownership docs) from the bucket.
    try {
      const bucket = getStorage().bucket();
      await bucket.deleteFiles({ prefix: `listings/${spaceId}/` });
      await bucket.deleteFiles({ prefix: `listing_ownership_docs/${spaceId}/` });
    } catch (err) {
      console.error(`Failed to delete storage files for listing ${spaceId}:`, err);
    }

    await recordAuditLog({
      actionType: "LISTING_DELETE_CLEANUP",
      details:
        `Cleaned up after deleting listing ${spaceId} (${listing?.title ?? "unknown"}): ` +
        `removed from ${favoritedBy.size} user(s)' favorites, cancelled ${openBookings.size} open booking(s), and purged storage bucket files.`,
      actorEmail: "system@prohost.app",
      severity: "SECURE",
    });
  }
);
