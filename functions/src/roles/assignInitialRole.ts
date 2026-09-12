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
 *
 * Also the sign-in-time enforcement point for account suspension
 * (setAccountSuspended.ts): a suspended account is rejected here before any
 * role/isVerified/timestamp bookkeeping runs, so a suspended user never
 * completes sign-in even if their presented ID token predates the
 * suspension's custom claim (this reads the live user_profiles document, not
 * just the token, for exactly that reason — same approach as
 * firestore.rules' isSuspended() helper).
 *
 * Registration format validation: `completeVerifiedRegistration` (client)
 * calls this with an optional `registration` payload BEFORE it writes the
 * actual profile document — there is no admin review left to catch a
 * malformed submission after the fact, so this is the one real gate. Only
 * cheap format checks (never uniqueness/business rules, which would need a
 * read this function has no reason to do on every plain sign-in call too).
 */
interface RegistrationDraft {
  fullName?: unknown;
  email?: unknown;
  idDocumentUrl?: unknown;
  tosAccepted?: unknown;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Mirrors LegalContent.EFFECTIVE_DATE (Kotlin) at the moment this was written —
// Cloud Functions can't import the Kotlin object directly, so this is a small,
// manually-kept-in-sync literal, same reasoning as lib/roles.ts's AppRole
// mirroring UserRole. Bump this alongside EFFECTIVE_DATE on any material legal
// document change so newly recorded acceptances reflect the version actually
// shown.
const CURRENT_CONSENT_VERSION = "September 6, 2026";

function validateRegistrationDraft(draft: RegistrationDraft): void {
  const fullName = typeof draft.fullName === "string" ? draft.fullName.trim() : "";
  if (fullName.length < 2 || fullName.length > 100) {
    throw new HttpsError("invalid-argument", "Full name must be between 2 and 100 characters.");
  }

  const email = typeof draft.email === "string" ? draft.email.trim() : "";
  if (!EMAIL_RE.test(email) || email.length > 200) {
    throw new HttpsError("invalid-argument", "A valid email address is required.");
  }

  // idDocumentUrl is optional at the type level but required by the registration
  // form's own UI gating — if present, it must actually be a Firebase Storage
  // download URL, not an arbitrary client-supplied string.
  if (draft.idDocumentUrl !== undefined && draft.idDocumentUrl !== null) {
    const idDocumentUrl = typeof draft.idDocumentUrl === "string" ? draft.idDocumentUrl : "";
    if (!/^https:\/\/firebasestorage\.googleapis\.com\//.test(idDocumentUrl)) {
      throw new HttpsError("invalid-argument", "ID document must be a real uploaded file.");
    }
  }

  // The registration screen's Terms of Use / Privacy Policy checkbox used to be
  // purely cosmetic — nothing recorded whether it was ever actually checked, or
  // enforced that it was. Required now: a real explicit `true`, not merely
  // "not false" (a missing/undefined field must fail closed, not pass).
  if (draft.tosAccepted !== true) {
    throw new HttpsError("invalid-argument", "You must accept the Terms of Use and Privacy Policy to register.");
  }
}

export const assignInitialRole = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const registration = (request.data as { registration?: RegistrationDraft } | undefined)?.registration;
  if (registration) {
    validateRegistrationDraft(registration);
  }

  const db = getFirestore();
  const profileSnap = await db.collection("user_profiles").doc(auth.uid).get();
  if (auth.token.suspended === true || profileSnap.data()?.isSuspended === true) {
    throw new HttpsError("permission-denied", "This account has been suspended. Contact support for help.");
  }

  const isVerified = Boolean(auth.token.phone_number);
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
      // Only stamped when a registration draft actually arrived (and, by this
      // point, already passed validateRegistrationDraft's tosAccepted check) —
      // left unset rather than fabricated for the rare path where an account
      // gets its first role claim with no registration payload at all.
      ...(registration ? { tosAcceptedAtMillis: now, consentVersion: CURRENT_CONSENT_VERSION } : {}),
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
