/**
 * When an account's email counts as verified. Email-link and email-code sign-ins prove the
 * address (Firebase sets `email_verified`; the code path sets it on the Auth user), and
 * Google accounts are verified by Google. Pure, so it is unit-tested.
 */
export interface EmailTokenFacts {
  email?: string;
  email_verified?: boolean;
  firebase?: { sign_in_provider?: string };
}

export function emailVerifiedByToken(token: EmailTokenFacts): boolean {
  if (!token.email) return false;
  return token.email_verified === true || token.firebase?.sign_in_provider === "google.com";
}

/** Same rule for an Auth user record (used by the one-time backfill). */
export function emailVerifiedByUserRecord(user: {
  email?: string;
  emailVerified: boolean;
  providerData: Array<{ providerId: string }>;
}): boolean {
  if (!user.email) return false;
  return user.emailVerified || user.providerData.some((p) => p.providerId === "google.com");
}
