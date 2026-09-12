import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { setClaimsThenFirestore } from "../lib/roles";
import "../lib/admin";

interface SetAccountSuspendedData {
  targetUid?: string;
  suspended?: boolean;
}

/**
 * The suspend/reactivate state between "exists" and "deleted" that Admin
 * never had before — a suspended account can't sign back in (see
 * completeVerifiedLogin's post-login check) and can't create new listings or
 * booking requests (see firestore.rules' isSuspended() helper, which re-reads
 * the live user_profiles document rather than the caller's current ID token,
 * so a suspension takes effect on the very next write attempt rather than
 * waiting for that token to refresh).
 *
 * Admin-only, and an Admin account itself can never be suspended through this
 * path — demote it first via a fresh grantAdminRole-style flow if that's ever
 * genuinely needed (there is no such flow today, by design: Admin accounts
 * are meant to be few and trusted).
 */
export const setAccountSuspended = onCall<SetAccountSuspendedData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can suspend or reactivate an account.");
  }

  const { targetUid, suspended } = request.data ?? {};
  if (!targetUid || typeof suspended !== "boolean") {
    throw new HttpsError("invalid-argument", "targetUid and a boolean suspended are required.");
  }

  const adminAuth = getAuth();
  const targetUser = await adminAuth.getUser(targetUid);
  if (targetUser.customClaims?.role === "ADMIN") {
    throw new HttpsError("failed-precondition", "An Admin account cannot be suspended.");
  }

  const db = getFirestore();
  let ownedListingsCount = 0;
  try {
    await setClaimsThenFirestore(
      adminAuth,
      targetUid,
      targetUser.customClaims,
      { ...targetUser.customClaims, suspended },
      async () => {
        await db.collection("user_profiles").doc(targetUid).set(
          { isSuspended: suspended, updatedAt: Date.now() },
          { merge: true }
        );

        // Suspending a host used to only block *new* actions (creating a
        // listing or booking request) — every listing they'd already
        // published stayed fully visible and bookable in Discovery, so a
        // suspended host could keep generating traffic and inbound requests
        // indefinitely. Mirror the suspension onto each of their listings (a
        // denormalized flag, same reasoning as activeListingCount in
        // listingCountTracker.ts — Firestore rules can't join across
        // collections at read time) so firestore.rules can hide them from
        // public discovery while the owner and Admin can still see them (a
        // suspended host should be able to see their own listings are
        // hidden, not have them vanish from their own view). bulkWriter (not
        // a plain batch()) since a single WriteBatch is capped at 500
        // operations — a host with more listings than that would otherwise
        // throw on commit(), leaving the account-level suspension applied
        // but the per-listing mirror only partially done. bulkWriter chunks/
        // paces automatically with no such cap and retries transient
        // failures on its own.
        const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", targetUid).get();
        ownedListingsCount = ownedListings.size;
        if (!ownedListings.empty) {
          const bulkWriter = db.bulkWriter();
          ownedListings.docs.forEach((doc) => {
            bulkWriter.set(doc.ref, { isOwnerSuspended: suspended }, { merge: true });
          });
          await bulkWriter.close();
        }
      }
    );
  } catch (err) {
    throw new HttpsError("internal", err instanceof Error ? err.message : "Failed to update account suspension.");
  }

  // Best-effort, done only after the claim + Firestore change both landed —
  // see revokeProHostRole.ts's identical reasoning for why this isn't part
  // of the rollback above. Invalidates any refresh token this account
  // currently holds, so a suspended user can't silently keep minting fresh
  // ID tokens forever — their next API call that verifies the token
  // (checkRevoked) fails, and any client trying to sign in again is
  // rejected outright.
  if (suspended) {
    try {
      await adminAuth.revokeRefreshTokens(targetUid);
    } catch (err) {
      console.warn(`setAccountSuspended: revokeRefreshTokens failed for ${targetUid}`, err);
    }
  }

  await recordAuditLog({
    actionType: suspended ? "ACCOUNT_SUSPENDED" : "ACCOUNT_REACTIVATED",
    details: `Account ${targetUid} (${targetUser.email ?? "no email"}) ${suspended ? "suspended" : "reactivated"} by admin ${auth.token.email ?? auth.uid}; ${ownedListingsCount} listing(s) mirrored.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { ok: true, targetUid, suspended };
});
