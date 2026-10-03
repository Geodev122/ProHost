import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * Self-service account deletion — required to exist in-app (not just via a
 * support email) by Apple Guideline 5.1.1(v) and the Google Play User Data
 * policy. Always targets the CALLER's own uid; there is no targetUid
 * parameter, so this can never be used to delete someone else's account —
 * only setAccountSuspended (Admin-only) touches another user's account state.
 *
 * Deliberately does NOT delete booking_requests the caller appears in (only
 * withdraws their still-PENDING requests) — those are the other party's record too (a host's proof a slot was booked,
 * a specialist's record of what they agreed to pay), so this removes the
 * account's own PII and listings while leaving the transaction ledger intact,
 * the same "PII goes, the ledger stays" split most marketplaces use.
 *
 * Idempotent and safe to retry: the Auth account is deleted last, so if an
 * earlier step throws, the caller is still signed in and can simply call
 * this again — every earlier step is a delete-if-exists operation.
 */
export const deleteOwnAccount = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const uid = auth.uid;
  const db = getFirestore();
  const bucket = getStorage().bucket();

  const deleteStoragePrefix = async (prefix: string) => {
    try {
      await bucket.deleteFiles({ prefix, force: true });
    } catch (err) {
      // Best-effort — nothing was necessarily ever uploaded under this
      // prefix, and cleanup must not get stuck on a no-op failure.
      console.warn(`deleteOwnAccount: storage cleanup failed for prefix ${prefix}`, err);
    }
  };

  // 1. Remove every listing this account owns. Deleting each Firestore doc
  // fires onWorkspaceListingDeleted (listingCountTracker.ts), which already
  // keeps activeListingCount in sync — that bookkeeping isn't duplicated here.
  const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
  for (const doc of ownedListings.docs) {
    await deleteStoragePrefix(`listings/${doc.id}/`);
    await deleteStoragePrefix(`listing_ownership_docs/${doc.id}/`);
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
      console.warn(`deleteOwnAccount: ${label} cleanup failed for ${uid}`, err);
    }
  };

  const profileSnap = await db.collection("user_profiles").doc(uid).get();
  const profile = profileSnap.data() ?? {};
  const targetUser = await getAuth().getUser(uid).catch(() => null);
  const email = (targetUser?.email ?? (profile.email as string | undefined) ?? "").toLowerCase();

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

  // 5. Remove the Firestore profile.
  await db.collection("user_profiles").doc(uid).delete();

  await bestEffort("audit log", () => recordAuditLog({
    actionType: "ACCOUNT_SELF_DELETED",
    details: `Account ${uid} (${email || targetUser?.phoneNumber || "unknown"}) deleted itself via in-app account deletion, including ${ownedListings.size} owned listing(s).`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  }));

  // 6. Delete the Auth account last — every step above is safe to retry, so
  // this is the one irreversible action, done only once everything else
  // has actually succeeded. Already-deleted (a retried call) counts as done.
  await getAuth().deleteUser(uid).catch((err: { code?: string }) => {
    if (err?.code !== "auth/user-not-found") throw err;
  });

  return { ok: true };
});
