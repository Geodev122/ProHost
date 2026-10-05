import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import { DISPLAY_CODES_COLLECTION } from "./displayCode";
import "./admin";

/**
 * Removes everything an account owns except the booking ledger: its listings (and their
 * files), pending booking requests (withdrawn), favorites on other listings, per-user
 * auth/rate-limit records, profile picture and profile. Shared by deleteOwnAccount
 * (in-app deletion) and onAuthUserDeleted (an account deleted in the Firebase console),
 * so a deletion outside the app never leaves the profile and its PII behind.
 * Every step is delete-if-exists, so it is safe to run twice.
 */
export async function cleanUpAccountData(uid: string, email: string): Promise<{ listings: number }> {
  const db = getFirestore();
  const bucket = getStorage().bucket();

  const deleteStoragePrefix = async (prefix: string) => {
    try {
      await bucket.deleteFiles({ prefix, force: true });
    } catch (err) {
      // Best-effort — nothing was necessarily ever uploaded under this
      // prefix, and cleanup must not get stuck on a no-op failure.
      logger.warn(`account cleanup: storage cleanup failed for prefix ${prefix}`, err);
    }
  };

  // 1. Remove every listing this account owns. Deleting each Firestore doc
  // fires onWorkspaceListingDeleted (listingCountTracker.ts), which already
  // keeps activeListingCount in sync — that bookkeeping isn't duplicated here.
  const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
  for (const doc of ownedListings.docs) {
    await deleteStoragePrefix(`listings/${doc.id}/`);
    await deleteStoragePrefix(`listing_verification_docs/${doc.id}/`);
  }
  if (!ownedListings.empty) {
    const bulkWriter = db.bulkWriter();
    ownedListings.docs.forEach((doc) => bulkWriter.delete(doc.ref));
    await bulkWriter.close();
  }

  // Secondary cleanup is best-effort: none of it may stand between the user and
  // the deletion they asked for, which is required to always work.
  const bestEffort = async (label: string, step: () => Promise<unknown>) => {
    try {
      await step();
    } catch (err) {
      logger.warn(`account cleanup: ${label} cleanup failed`, err);
    }
  };

  const profileSnap = await db.collection("user_profiles").doc(uid).get();
  const profile = profileSnap.data() ?? {};
  email = (email || (profile.email as string | undefined) || "").toLowerCase();

  // 2. Withdraw this user's still-pending booking requests so hosts aren't left
  // answering requests from an account that no longer exists.
  await bestEffort("pending bookings", async () => {
    const pending = await db.collection("booking_requests")
      .where("practitionerId", "==", uid)
      .where("status", "==", "PENDING")
      .get();
    if (pending.empty) return;
    const batch = db.batch();
    pending.docs.forEach((doc) => batch.update(doc.ref, {
      status: "CANCELLED",
      cancellationReasonCode: "ACCOUNT_DELETED",
      cancelledByRole: "SPECIALIST",
      updatedAt: Date.now(),
    }));
    await batch.commit();
  });

  // 3. Undo this user's favorites on other listings (favoritesSync only reacts to
  // profile updates, not deletes).
  await bestEffort("favorites", async () => {
    const saved = (profile.savedSpaceIds as string[] | undefined) ?? [];
    const ownedIds = new Set(ownedListings.docs.map((d) => d.id));
    const targets = saved.filter((id) => !ownedIds.has(id));
    if (targets.length === 0) return;
    // update() so a deleted listing is skipped instead of being recreated as a ghost doc.
    await Promise.all(targets.map((id) =>
      db.collection("workspace_listings").doc(id)
        .update({ favoriteCount: FieldValue.increment(-1) })
        .catch((err: { code?: number | string }) => {
          if (err?.code !== 5 && err?.code !== "not-found") throw err;
        })
    ));
  });

  // 4. Remove per-user auth/rate-limit records and the profile picture.
  await bestEffort("rate limits", () => db.collection("inquiry_rate_limits").doc(uid).delete());
  if (email) {
    const otpKey = email.replace(/[^a-z0-9@._-]/g, "_").slice(0, 200);
    await bestEffort("email otp", () => db.collection("email_otps").doc(otpKey).delete());
  }
  await deleteStoragePrefix(`profile_pictures/${uid}/`);

  // 5. Free the person's display code, then remove the Firestore profile.
  if (typeof profile.displayCode === "string" && profile.displayCode) {
    await bestEffort("display code", () => db.collection(DISPLAY_CODES_COLLECTION).doc(profile.displayCode).delete());
  }
  await db.collection("user_profiles").doc(uid).delete();
  return { listings: ownedListings.size };
}

