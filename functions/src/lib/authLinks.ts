import { getAuth, ActionCodeSettings } from "firebase-admin/auth";
import * as logger from "firebase-functions/logger";
import "./admin";

/**
 * Where Firebase's action page (reset password / verify email) sends people afterwards: the
 * smart install/open link on pro-host.tech. It must be an authorized domain (Maintenance
 * `authconfig apply` adds it); if it isn't, the link is generated without a continue URL.
 */
export const AUTH_CONTINUE_URL = "https://pro-host.tech/get?utm_source=auth_email";

const settings: ActionCodeSettings = { url: AUTH_CONTINUE_URL };

function isContinueUriError(e: unknown): boolean {
  const code = (e as { code?: string })?.code ?? "";
  return code === "auth/unauthorized-continue-uri" || code === "auth/invalid-continue-uri";
}

async function withContinueFallback(kind: string, make: (s?: ActionCodeSettings) => Promise<string>): Promise<string> {
  try {
    return await make(settings);
  } catch (e) {
    if (!isContinueUriError(e)) throw e;
    logger.warn("auth_link_without_continue_url", { kind, error: String(e) });
    return make(undefined);
  }
}

/** Password reset link (also sets a first password for accounts that never had one). */
export function passwordResetLink(email: string): Promise<string> {
  return withContinueFallback("reset", (s) => getAuth().generatePasswordResetLink(email, s));
}

export function emailVerificationLink(email: string): Promise<string> {
  return withContinueFallback("verify", (s) => getAuth().generateEmailVerificationLink(email, s));
}
