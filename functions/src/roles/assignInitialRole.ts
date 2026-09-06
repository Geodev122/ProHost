import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { DEFAULT_ROLE, isAppRole } from "../lib/roles";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * Called by the client on EVERY sign-in (see AuthFlow.kt's resolveVerifiedRole,
 * which always awaits this — not just when the caller has no role claim yet).
 * Assigns the default SPECIALIST role via a custom claim if the caller
 * doesn't already have one. Idempotent — the role-assignment half is a no-op
 * once a role is already set.
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
 *
 * `lastSignInAtMillis` is stamped on every call, since this function now runs
 * on every sign-in; `createdAtMillis` is stamped exactly once — only on the
 * branch that assigns the default role for the very first time, i.e. the
 * caller's actual account-creation moment. Both are Admin SDK writes only —
 * no client-supplied value is ever trusted for either (see the matching
 * protected-fields list in firestore.rules' user_profiles update rule). This
 * is the audit trail behind Admin Console's Users Directory export.
 */
export const assignInitialRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const isVerified = Boolean(auth.token.phone_number);
  const db = getFirestore();
  const now = Date.now();

  const existingRole = auth.token.role;
  if (isAppRole(existingRole)) {
    await db.collection("user_profiles").doc(auth.uid).set(
      { isVerified, lastSignInAtMillis: now, updatedAt: now },
      { merge: true }
    );
    return { role: existingRole, assigned: false };
  }

  await getAuth().setCustomUserClaims(auth.uid, { role: DEFAULT_ROLE });

  await db.collection("user_profiles").doc(auth.uid).set(
    {
      role: DEFAULT_ROLE,
      isVerified,
      createdAtMillis: now,
      lastSignInAtMillis: now,
      updatedAt: now,
    },
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
