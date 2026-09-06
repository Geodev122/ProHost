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
 * sign-in; the role-assignment half is a no-op once a role is already set.
 *
 * This is the ONLY place a brand-new user's role gets decided, and it never
 * takes a role as input from the client — that's the whole point.
 *
 * Also keeps `user_profiles.isVerified` in sync with the ID token's own
 * `phone_number` claim on every call, regardless of whether the role branch
 * above is a no-op — there is no separate admin-reviewed "accreditation"
 * concept anymore; isVerified means only "this account completed Firebase
 * Phone Auth SMS verification," derived from the trusted token, never a
 * manually-toggled flag.
 */
export const assignInitialRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const isVerified = Boolean(auth.token.phone_number);
  const db = getFirestore();

  const existingRole = auth.token.role;
  if (isAppRole(existingRole)) {
    await db.collection("user_profiles").doc(auth.uid).set(
      { isVerified, updatedAt: Date.now() },
      { merge: true }
    );
    return { role: existingRole, assigned: false };
  }

  await getAuth().setCustomUserClaims(auth.uid, { role: DEFAULT_ROLE });

  await db.collection("user_profiles").doc(auth.uid).set(
    { role: DEFAULT_ROLE, isVerified, updatedAt: Date.now() },
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
