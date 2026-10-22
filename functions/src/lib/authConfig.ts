import { HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import "./admin";

/**
 * app_config/auth (server-only). `legacyEmailSignIn: false` retires the old email sign-in
 * (6-digit code + magic link) once the email + password app is live: the old callables then
 * answer with LEGACY_EMAIL_RETIRED_MESSAGE, which 1.0.43 shows as-is. Missing doc = still on.
 */
export const AUTH_CONFIG_DOC = "app_config/auth";

export const LEGACY_EMAIL_RETIRED_MESSAGE =
  "Email codes were replaced by passwords. Update ProHost from Google Play, then sign in with your " +
  "email and password (tap Forgot password to set one).";

export function legacyEmailSignInFrom(data: Record<string, unknown> | undefined): boolean {
  return data?.legacyEmailSignIn !== false;
}

let cache: { at: number; on: boolean } | null = null;
const CACHE_MS = 60 * 1000;

export async function legacyEmailSignInEnabled(now = Date.now()): Promise<boolean> {
  if (cache && now - cache.at < CACHE_MS) return cache.on;
  const snap = await getFirestore().doc(AUTH_CONFIG_DOC).get();
  cache = { at: now, on: legacyEmailSignInFrom(snap.data()) };
  return cache.on;
}

/** Throws the "update the app" error when the legacy email sign-in is retired. */
export async function assertLegacyEmailSignIn(): Promise<void> {
  if (!(await legacyEmailSignInEnabled())) {
    throw new HttpsError("failed-precondition", LEGACY_EMAIL_RETIRED_MESSAGE);
  }
}
