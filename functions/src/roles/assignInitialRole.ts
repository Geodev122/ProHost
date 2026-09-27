import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { DEFAULT_ROLE, isAppRole } from "../lib/roles";
import { recordAuditLog } from "../lib/auditLog";
import { enforcePlayIntegrity } from "../lib/playIntegrity";
import { hostingerSmtpSecret } from "../lib/email";
import { emailVerificationSecret, sendEmailVerificationInternal } from "../auth/emailVerification";
import * as logger from "firebase-functions/logger";
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
 * Also keeps `user_profiles.isVerified` in sync with the ID token on every
 * call, regardless of whether the role branch above is a no-op. isVerified is
 * true when EITHER the phone_number claim (phone OTP) OR the email_verified
 * claim (email sign-in link / Google One Tap) is present and set on the token.
 * Phone KYC for booking/listing features is a separate gate handled on the
 * client (KycScreen.kt) — it is not reflected in this isVerified flag.
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
 *
 * `integrityToken`, when present (release builds only), is enforced by
 * enforcePlayIntegrity (lib/playIntegrity.ts): user-bound nonce, freshness,
 * app recognition and licensing verdicts.
 */
interface RegistrationDraft {
  fullName?: unknown;
  email?: unknown;
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

async function validateRegistrationDraft(draft: RegistrationDraft, callerUid: string): Promise<void> {
  const fullName = typeof draft.fullName === "string" ? draft.fullName.trim() : "";
  if (fullName.length < 2 || fullName.length > 100) {
    throw new HttpsError("invalid-argument", "Full name must be between 2 and 100 characters.");
  }

  const email = typeof draft.email === "string" ? draft.email.trim() : "";
  if (!EMAIL_RE.test(email) || email.length > 200) {
    throw new HttpsError("invalid-argument", "A valid email address is required.");
  }

  // Email uniqueness: prevent two accounts from sharing the same email.
  try {
    const existing = await getAuth().getUserByEmail(email);
    if (existing.uid !== callerUid) {
      throw new HttpsError("already-exists", "An account with this email already exists.");
    }
  } catch (e: unknown) {
    // getUserByEmail throws "auth/user-not-found" when no account has this email — that's fine.
    if (e instanceof HttpsError) throw e;
    const code = (e as { code?: string })?.code;
    if (code !== "auth/user-not-found") throw e;
  }

  // The registration screen's Terms of Use / Privacy Policy checkbox used to be
  // purely cosmetic — nothing recorded whether it was ever actually checked, or
  // enforced that it was. Required now: a real explicit `true`, not merely
  // "not false" (a missing/undefined field must fail closed, not pass).
  if (draft.tosAccepted !== true) {
    throw new HttpsError("invalid-argument", "You must accept the Terms of Use and Privacy Policy to register.");
  }
}

export const assignInitialRole = onCall(
  { secrets: [hostingerSmtpSecret, emailVerificationSecret] },
  async (request) => {
    const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const data = request.data as { registration?: RegistrationDraft; integrityToken?: unknown } | undefined;
  const registration = data?.registration;
  if (registration) {
    await validateRegistrationDraft(registration, auth.uid);
  }

  // Integrity is enforced when a token is present: UNRECOGNIZED_VERSION and UNLICENSED
  // verdicts throw HttpsError("failed-precondition") so the sign-in is rejected.
  // A missing token (no Play Services, test devices) is still allowed so a transient
  // client-side failure doesn't permanently lock out real users.
  const integrityToken = typeof data?.integrityToken === "string" ? data.integrityToken : undefined;
  if (integrityToken) {
    await enforcePlayIntegrity(integrityToken, auth.uid);
  }

  const db = getFirestore();
  const profileSnap = await db.collection("user_profiles").doc(auth.uid).get();
  if (auth.token.suspended === true || profileSnap.data()?.isSuspended === true) {
    throw new HttpsError("permission-denied", "This account has been suspended. Contact support for help.");
  }

  // Track B: email/Google sign-in users are also considered "verified" for the
  // purposes of gaining initial app access — phone KYC is a separate step that
  // gates booking/listing features, handled on the client in KycScreen.kt and
  // persisted by linkPhoneCredentialToCurrentUser (FirebaseAuthService). An
  // email-verified account (Firebase's email_verified claim on the ID token, set
  // automatically for Google Sign-In and after a user follows a sign-in link) is
  // treated as equivalent to a phone-verified one for the isVerified flag here.
  const isVerified =
    Boolean(auth.token.phone_number) ||
    (Boolean(auth.token.email) && Boolean(auth.token.email_verified));
  const now = Date.now();

  const existingRole = auth.token.role;
  if (isAppRole(existingRole)) {
    // Seed email from the token if the profile doesn't have one yet — covers
    // the case where a Google/email user signs in after the profile was created
    // via an older phone-only flow that left email blank.
    const emailUpdate = auth.token.email
      ? { email: auth.token.email }
      : {};
    await db.collection("user_profiles").doc(auth.uid).set(
      { isVerified, lastSignInAtMillis: now, updatedAt: now, ...emailUpdate },
      { merge: true }
    );
    return { role: existingRole, assigned: false };
  }

  await getAuth().setCustomUserClaims(auth.uid, { role: DEFAULT_ROLE });

  await db.collection("user_profiles").doc(auth.uid).set(
    {
      role: DEFAULT_ROLE,
      isVerified,
      // Seed the email from the token for email/Google sign-in users, so
      // the profile is pre-populated without waiting for a registration draft.
      ...(auth.token.email ? { email: auth.token.email } : {}),
      createdAtMillis: now,
      lastSignInAtMillis: now,
      // Only stamped on the FIRST registration (when the field doesn't exist yet).
      // If completeVerifiedRegistration is retried after a network error, the
      // original timestamp is preserved — we never overwrite a recorded consent.
      ...(registration && !profileSnap.data()?.tosAcceptedAtMillis
        ? { tosAcceptedAtMillis: now, consentVersion: CURRENT_CONSENT_VERSION }
        : {}),
      ...(auth.token.email_verified === true
        ? { emailVerified: true, emailVerifiedAt: now }
        : {}),
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

  // Send email verification link on first-time registration for non-Google/unverified emails
  if (registration && auth.token.email_verified !== true) {
    sendEmailVerificationInternal(auth.uid).catch((e) =>
      logger.warn("email_verification_send_failed", { uid: auth.uid, error: String(e) })
    );
  }

  return { role: DEFAULT_ROLE, assigned: true };
  }
);
