/**
 * Email + password sign-in support (1.0.44): "Forgot password" and the one-off invite that lets
 * people who signed in with the retired email code / magic link choose their first password.
 *
 * Reset links are Firebase action links (generatePasswordResetLink → Firebase's hosted action
 * page), mailed with our branded template through the Trigger Email extension. A reset on an
 * account without a password simply sets one, so the same link also migrates code/link users.
 */
import { createHash, randomBytes, timingSafeEqual } from "crypto";
import * as logger from "firebase-functions/logger";
import { onRequest, HttpsError } from "firebase-functions/v2/https";
import { getAuth, UserRecord } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { maskEmail, sendEmail } from "../lib/email";
import { esc, passwordResetTemplate, passwordSetupInviteTemplate } from "../lib/emailTemplates";
import { isPlausibleEmail, takeEmailSendSlot } from "../lib/emailRateLimit";
import { passwordResetLink } from "../lib/authLinks";
import "../lib/admin";

export const INVITES = "auth_password_invites";
export const INVITE_TTL_MS = 30 * 24 * 3600 * 1000;
const TEST_ROBOT_DOMAIN = /cloudtestlabaccounts\.com$/i;

/**
 * Callable: sendPasswordResetLink({ email }) — unauthenticated, rate-limited (5/hour/address).
 * Always answers { ok: true } for a plausible address, whether or not an account exists, so the
 * endpoint can't be used to find out who has an account (email enumeration protection).
 */
export const sendPasswordResetLink = onCall({}, async (request) => {
  const email = (request.data?.email as string | undefined)?.toLowerCase().trim();
  if (!isPlausibleEmail(email)) {
    throw new HttpsError("invalid-argument", "A valid email address is required.");
  }
  await takeEmailSendSlot("pwreset", email);
  const outcome = await mailPasswordReset(email);
  logger.info("password_reset_requested", { email: maskEmail(email), outcome });
  if (outcome === "mail_failed") {
    throw new HttpsError("unavailable", "We couldn't send the email right now. Please try again in a minute.");
  }
  return { ok: true };
});

export type ResetOutcome = "sent" | "no_account" | "mail_failed";

export async function mailPasswordReset(email: string): Promise<ResetOutcome> {
  let link: string;
  try {
    link = await passwordResetLink(email);
  } catch (e) {
    if ((e as { code?: string })?.code === "auth/user-not-found" || (e as { code?: string })?.code === "auth/email-not-found") {
      return "no_account";
    }
    throw e;
  }
  const delivered = await sendEmail({ to: email, ...passwordResetTemplate(email, link) });
  return delivered ? "sent" : "mail_failed";
}

// ─── Invites for accounts that never had a password ─────────────────────────────

export type InviteCategory = "email_code" | "email_link";

export interface AuthUserFacts {
  email?: string;
  disabled?: boolean;
  providerIds: string[];
  hasPasswordHash: boolean;
}

/**
 * Which accounts need a "set your password" invite: an email, not disabled, not a Play
 * pre-launch robot, never Google, and either no provider at all (the 6-digit code created them
 * with a custom token) or the password provider without a password (magic-link sign-in).
 */
export function needsPasswordSetup(u: AuthUserFacts): InviteCategory | null {
  if (!u.email || u.disabled) return null;
  if (TEST_ROBOT_DOMAIN.test(u.email.split("@")[1] ?? "")) return null;
  if (u.providerIds.includes("google.com")) return null;
  const others = u.providerIds.filter((p) => p !== "password");
  if (others.length > 0) return null; // phone or other providers: not an email-only account
  if (u.providerIds.length === 0) return "email_code";
  return u.hasPasswordHash ? null : "email_link";
}

export function factsOf(u: UserRecord): AuthUserFacts {
  return {
    email: u.email,
    disabled: u.disabled,
    providerIds: (u.providerData || []).map((p) => p.providerId),
    hasPasswordHash: !!u.passwordHash,
  };
}

const sha256 = (v: string) => createHash("sha256").update(v).digest("hex");

export interface InviteDoc {
  tokenHash: string;
  email: string;
  category: InviteCategory;
  createdAt: number;
  expiresAt: number;
}

/** True when `token` matches the stored invite and it hasn't expired. */
export function inviteTokenValid(doc: Partial<InviteDoc> | undefined, token: string, now: number): boolean {
  if (!doc?.tokenHash || !token || typeof doc.expiresAt !== "number" || now > doc.expiresAt) return false;
  const a = Buffer.from(doc.tokenHash);
  const b = Buffer.from(sha256(token));
  return a.length === b.length && timingSafeEqual(a, b);
}

function functionsBase(): string {
  const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || "prohost-f766f";
  return process.env.FUNCTIONS_EMULATOR === "true"
    ? `http://127.0.0.1:5001/${project}/europe-west1`
    : `https://europe-west1-${project}.cloudfunctions.net`;
}

export function inviteUrl(uid: string, token: string, base = functionsBase()): string {
  return `${base}/passwordSetupLink?u=${encodeURIComponent(uid)}&t=${encodeURIComponent(token)}`;
}

/**
 * Writes a fresh invite (new token, 30 days) and mails it. Used by Maintenance
 * `passwordinvites send` and the e2e test. Returns whether the mail was queued.
 */
export async function sendPasswordSetupInvite(
  uid: string,
  email: string,
  category: InviteCategory,
  now = Date.now(),
  base = functionsBase()
): Promise<boolean> {
  const token = randomBytes(24).toString("base64url");
  const doc: InviteDoc = { tokenHash: sha256(token), email, category, createdAt: now, expiresAt: now + INVITE_TTL_MS };
  await getFirestore().collection(INVITES).doc(uid).set({ ...doc, sentAt: now, uses: 0 });
  const queued = await sendEmail({ to: email, ...passwordSetupInviteTemplate(email, inviteUrl(uid, token, base)) });
  logger.info("password_invite_sent", { uid, email: maskEmail(email), category, queued });
  return queued;
}

/**
 * HTTP: passwordSetupLink?u=<uid>&t=<token>. GET shows a button (mail scanners open every link);
 * POST checks the invite and redirects to a fresh Firebase reset link (those only last an hour,
 * the invite lasts 30 days).
 */
export const passwordSetupLink = onRequest({}, async (req, res) => {
  const source = req.method === "POST" ? (req.body ?? {}) : req.query;
  const uid = String(source.u ?? "").trim();
  const token = String(source.t ?? "").trim();
  res.set("Cache-Control", "no-store");
  if (!uid || !token) {
    res.status(400).send(page("This link is incomplete.", "Open ProHost and tap <strong>Forgot password</strong> on the sign-in screen."));
    return;
  }
  if (req.method !== "POST") {
    res.status(200).send(confirmPage(uid, token));
    return;
  }
  const ref = getFirestore().collection(INVITES).doc(uid);
  const snap = await ref.get();
  const doc = snap.data() as Partial<InviteDoc> | undefined;
  if (!inviteTokenValid(doc, token, Date.now())) {
    res.status(410).send(page("This link has expired.", "Open ProHost and tap <strong>Forgot password</strong> on the sign-in screen to get a new one."));
    return;
  }
  try {
    const user = await getAuth().getUser(uid);
    if (!user.email) throw new Error("account has no email");
    const link = await passwordResetLink(user.email);
    await ref.update({ uses: (snap.get("uses") ?? 0) + 1, lastUsedAt: Date.now() });
    res.redirect(303, link);
  } catch (e) {
    logger.warn("password_setup_link_failed", { uid, error: String(e) });
    res.status(410).send(page("We couldn't open this link.", "Open ProHost and tap <strong>Forgot password</strong> on the sign-in screen."));
  }
});

function page(title: string, body: string): string {
  return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/><meta name="robots" content="noindex"/>
<title>ProHost</title>
<style>body{margin:0;background:#F5F5F5;font-family:'Helvetica Neue',Arial,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh}
.card{background:#fff;border-radius:16px;padding:32px 28px;max-width:420px;width:90%;text-align:center;box-shadow:0 4px 24px rgba(0,0,0,.08)}
h1{font-size:20px;color:#111;margin:0 0 8px}p{font-size:14px;color:#555;line-height:1.6}
button{background:#E84C00;color:#fff;border:0;border-radius:10px;padding:14px 28px;font-size:16px;font-weight:700;cursor:pointer;width:100%}</style>
</head><body><div class="card"><h1>${title}</h1>${body}</div></body></html>`;
}

function confirmPage(uid: string, token: string): string {
  return page("Set your ProHost password", `<p>Tap the button to choose the password you'll use to sign in to ProHost.</p>
<p dir="rtl" lang="ar">اضغط الزر لاختيار كلمة المرور التي ستسجّل بها الدخول إلى ProHost.</p>
<form method="POST"><input type="hidden" name="u" value="${esc(uid)}"/><input type="hidden" name="t" value="${esc(token)}"/>
<button type="submit">Set my password · عيّن كلمة المرور</button></form>`);
}
