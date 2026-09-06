import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { DEFAULT_ROLE, isAppRole } from "../lib/roles";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * Called once, right after a user's first successful Firebase Auth sign-in.
 * Assigns the default SPECIALIST role via a custom claim if the caller
 * doesn't already have a role claim. Idempotent — safe to call on every
 * sign-in; it's a no-op once a role is already set.
 *
 * This is the ONLY place a brand-new user's role gets decided, and it never
 * takes a role as input from the client — that's the whole point.
 */
export const assignInitialRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const existingRole = auth.token.role;
  if (isAppRole(existingRole)) {
    return { role: existingRole, assigned: false };
  }

  await getAuth().setCustomUserClaims(auth.uid, { role: DEFAULT_ROLE });

  const db = getFirestore();
  await db.collection("user_profiles").doc(auth.uid).set(
    { role: DEFAULT_ROLE, updatedAt: Date.now() },
    { merge: true }
  );

  await recordAuditLog({
    actionType: "INITIAL_ROLE_ASSIGNED",
    details: `New user ${auth.uid} (${auth.token.email ?? "no email"}) assigned default role ${DEFAULT_ROLE}`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "INFO",
  });

  return { role: DEFAULT_ROLE, assigned: true };
});
