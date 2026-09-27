import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
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
 * Deliberately does NOT delete booking_requests the caller appears in —
 * those are the other party's record too (a host's proof a slot was booked,
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

  // 2. Remove this account's own documents — unlike booking_requests, an ID
  // document and profile picture belong solely to this account.
  await deleteStoragePrefix(`id_documents/${uid}/`);
  await deleteStoragePrefix(`profile_pictures/${uid}/`);

  // 3. Remove the Firestore profile.
  await db.collection("user_profiles").doc(uid).delete();

  const targetUser = await getAuth().getUser(uid).catch(() => null);

  await recordAuditLog({
    actionType: "ACCOUNT_SELF_DELETED",
    details: `Account ${uid} (${targetUser?.email ?? targetUser?.phoneNumber ?? "unknown"}) deleted itself via in-app account deletion, including ${ownedListings.size} owned listing(s).`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  // 4. Delete the Auth account last — every step above is safe to retry, so
  // this is the one irreversible action, done only once everything else
  // has actually succeeded.
  await getAuth().deleteUser(uid);

  return { ok: true };
});
