import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { setClaimsThenFirestore } from "../lib/roles";
import "../lib/admin";

interface GrantAdminRoleData {
  targetUid?: string;
  targetEmail?: string;
}

/**
 * The ONLY path by which the ADMIN role can be granted after the one-time
 * bootstrapSuperAdmin call. Succeeds only if the CALLER's own ID token
 * already carries role == "ADMIN" — there is no other way in.
 */
export const grantAdminRole = onCall<GrantAdminRoleData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an existing Admin can grant the Admin role.");
  }

  const { targetUid, targetEmail } = request.data ?? {};
  if (!targetUid && !targetEmail) {
    throw new HttpsError("invalid-argument", "Provide targetUid or targetEmail.");
  }

  const adminAuth = getAuth();
  const targetUser = targetUid
    ? await adminAuth.getUser(targetUid)
    : await adminAuth.getUserByEmail(targetEmail!);

  const db = getFirestore();
  try {
    await setClaimsThenFirestore(
      adminAuth,
      targetUser.uid,
      targetUser.customClaims,
      { ...targetUser.customClaims, role: "ADMIN" },
      async () => {
        await db.collection("user_profiles").doc(targetUser.uid).set(
          { role: "ADMIN", updatedAt: Date.now() },
          { merge: true }
        );
      }
    );
  } catch (err) {
    throw new HttpsError("internal", err instanceof Error ? err.message : "Failed to grant Admin role.");
  }

  await recordAuditLog({
    actionType: "ADMIN_ROLE_GRANTED",
    details: `Admin ${auth.token.email ?? auth.uid} granted ADMIN role to ${targetUser.email ?? targetUser.uid}`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { grantedTo: targetUser.uid, email: targetUser.email ?? null };
});
