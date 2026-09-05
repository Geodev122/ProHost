import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { SELF_SERVICE_UPGRADABLE_ROLES, AppRole } from "../lib/roles";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

interface RequestRoleUpgradeData {
  targetRole?: string;
}

/**
 * The only self-service role change a signed-in user can request. The set of
 * roles this accepts (SELF_SERVICE_UPGRADABLE_ROLES) deliberately excludes
 * ADMIN — there is no code path here, or anywhere else reachable by a
 * regular client, that can grant ADMIN. See grantAdminRole.ts for the only
 * way ADMIN is ever assigned after the one-time bootstrap.
 */
export const requestRoleUpgrade = onCall<RequestRoleUpgradeData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const targetRole = request.data?.targetRole;
  if (!SELF_SERVICE_UPGRADABLE_ROLES.includes(targetRole as AppRole)) {
    throw new HttpsError(
      "invalid-argument",
      `targetRole must be one of: ${SELF_SERVICE_UPGRADABLE_ROLES.join(", ")}`
    );
  }

  await getAuth().setCustomUserClaims(auth.uid, { role: targetRole });

  const db = getFirestore();
  await db.collection("user_profiles").doc(auth.uid).set(
    { role: targetRole, updatedAt: Date.now() },
    { merge: true }
  );

  await recordAuditLog({
    actionType: "ROLE_SELF_UPGRADE",
    details: `User ${auth.uid} (${auth.token.email ?? "no email"}) self-upgraded to ${targetRole}`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "INFO",
  });

  return { role: targetRole };
});
