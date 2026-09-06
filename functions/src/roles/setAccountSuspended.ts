import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
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

  await adminAuth.setCustomUserClaims(targetUid, { ...targetUser.customClaims, suspended });
  if (suspended) {
    // Invalidates any refresh token this account currently holds, so a
    // suspended user can't silently keep minting fresh ID tokens forever —
    // their next API call that verifies the token (checkRevoked) fails, and
    // any client trying to sign in again is rejected outright.
    await adminAuth.revokeRefreshTokens(targetUid);
  }

  const db = getFirestore();
  await db.collection("user_profiles").doc(targetUid).set(
    { isSuspended: suspended, updatedAt: Date.now() },
    { merge: true }
  );

  await recordAuditLog({
    actionType: suspended ? "ACCOUNT_SUSPENDED" : "ACCOUNT_REACTIVATED",
    details: `Account ${targetUid} (${targetUser.email ?? "no email"}) ${suspended ? "suspended" : "reactivated"} by admin ${auth.token.email ?? auth.uid}.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { ok: true, targetUid, suspended };
});
