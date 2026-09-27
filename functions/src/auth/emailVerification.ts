import { onRequest, HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { defineSecret } from "firebase-functions/params";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { createHmac } from "crypto";
import * as logger from "firebase-functions/logger";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { emailVerificationTemplate, emailVerificationResendTemplate, UserContext } from "../lib/emailTemplates";
import "../lib/admin";

/**
 * HMAC-SHA256 signed JWT for email verification links.
 * Stored as Firebase Secret — never in source code.
 * To set: echo "<random-hex>" | npx firebase functions:secrets:set email_verification_secret
 */
export const emailVerificationSecret = defineSecret("EMAIL_VERIFICATION_SECRET");

// Cloud Run URL of the verifyEmailLink function (europe-west1).
// Derived from the project hash visible in deploy output; stable per project.
const VERIFY_LINK_BASE = "https://verifyemaillink-t4c7d7bhka-ew.a.run.app";
const TOKEN_TTL_SECONDS = 60 * 60 * 24; // 24 hours
const MAX_RESENDS_PER_DAY = 3;

// ─── JWT helpers ─────────────────────────────────────────────────────────────

function b64url(input: string | Buffer): string {
  const buf = typeof input === "string" ? Buffer.from(input) : input;
  return buf.toString("base64").replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function signToken(payload: object, secret: string): string {
  const header = b64url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = b64url(JSON.stringify(payload));
  const sig = b64url(
    createHmac("sha256", secret).update(`${header}.${body}`).digest()
  );
  return `${header}.${body}.${sig}`;
}

interface VerificationPayload { uid: string; email: string; exp: number }

function verifyToken(token: string, secret: string): VerificationPayload {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("malformed");
  const [header, body, sig] = parts;
  const expectedSig = b64url(
    createHmac("sha256", secret).update(`${header}.${body}`).digest()
  );
  if (sig !== expectedSig) throw new Error("invalid signature");
  const payload = JSON.parse(Buffer.from(body, "base64url").toString()) as VerificationPayload;
  if (!payload.uid || !payload.email || !payload.exp) throw new Error("malformed payload");
  if (payload.exp < Math.floor(Date.now() / 1000)) throw new Error("expired");
  return payload;
}

// ─── sendEmailVerificationInternal — called by assignInitialRole on first registration
//
// Uses Firebase Admin SDK generateEmailVerificationLink() — Firebase owns the
// token lifecycle; HMAC-JWT is used only by the legacy resendEmailVerification /
// verifyEmailLink endpoints below (kept for backward compatibility until deprecated).

const VERIFICATION_CONTINUE_URL = "https://prohost-f766f.web.app/emaillink";

export async function sendEmailVerificationInternal(uid: string): Promise<void> {
  const db = getFirestore();
  const profileSnap = await db.collection("user_profiles").doc(uid).get();
  const profileData = profileSnap.data();
  if (!profileData?.email || profileData.emailVerified === true) return;

  try {
    const link = await getAuth().generateEmailVerificationLink(profileData.email, {
      url: VERIFICATION_CONTINUE_URL,
    });
    const userCtx: UserContext = {
      fullName: profileData.fullName ?? "Member",
      email: profileData.email,
      role: (profileData.role ?? "SPECIALIST") as UserContext["role"],
    };
    const tpl = emailVerificationTemplate(userCtx, link);
    await sendEmail({ to: profileData.email, ...tpl });
    logger.info("email_verification_sent", { uid });
  } catch (e) {
    // Degrade gracefully — don't block registration if verification email fails
    logger.warn("send_email_verification_failed", { uid, error: String(e) });
  }
}

// ─── Legacy helpers (used only by resendEmailVerification + verifyEmailLink below)
// DEPRECATED: will be removed once all clients use sendVerificationEmailLink.

async function dispatchVerificationEmailLegacy(uid: string, db: FirebaseFirestore.Firestore, isResend: boolean): Promise<void> {
  const profileSnap = await db.collection("user_profiles").doc(uid).get();
  const profileData = profileSnap.data();
  if (!profileData?.email) {
    throw new HttpsError("failed-precondition", "No email address on this account.");
  }
  if (profileData.emailVerified === true) {
    throw new HttpsError("failed-precondition", "Email is already verified.");
  }

  const secret = emailVerificationSecret.value();
  if (!secret) {
    logger.warn("email_verification_secret_missing");
    return; // degrade gracefully — don't block registration
  }

  const exp = Math.floor(Date.now() / 1000) + TOKEN_TTL_SECONDS;
  const token = signToken({ uid, email: profileData.email, exp }, secret);
  const verifyUrl = `${VERIFY_LINK_BASE}?token=${encodeURIComponent(token)}`;

  const userCtx: UserContext = {
    fullName: profileData.fullName ?? "Member",
    email: profileData.email,
    role: (profileData.role ?? "SPECIALIST") as UserContext["role"],
  };
  const tpl = isResend
    ? emailVerificationResendTemplate(userCtx, verifyUrl)
    : emailVerificationTemplate(userCtx, verifyUrl);

  await sendEmail({ to: profileData.email, ...tpl });
  logger.info("email_verification_sent_legacy", { uid, isResend });
}

// ─── resendEmailVerification callable — rate-limited, for the profile screen ─

export const resendEmailVerification = onCall(
  { secrets: [hostingerSmtpSecret, emailVerificationSecret] },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }
    const uid = request.auth.uid;
    const db = getFirestore();
    const profileSnap = await db.collection("user_profiles").doc(uid).get();
    const data = profileSnap.data();

    // Rate limit: max MAX_RESENDS_PER_DAY in any rolling 24-hour window
    const recentResends = (data?.emailVerificationResendCount as number | undefined) ?? 0;
    const lastResendAt = (data?.emailVerificationLastResendAt as number | undefined) ?? 0;
    const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;
    const effectiveCount = lastResendAt < oneDayAgo ? 0 : recentResends;

    if (effectiveCount >= MAX_RESENDS_PER_DAY) {
      throw new HttpsError(
        "resource-exhausted",
        `You can request at most ${MAX_RESENDS_PER_DAY} verification emails per day. Please check your spam folder.`
      );
    }

    await db.collection("user_profiles").doc(uid).update({
      emailVerificationResendCount: lastResendAt < oneDayAgo ? 1 : recentResends + 1,
      emailVerificationLastResendAt: Date.now(),
    });

    await dispatchVerificationEmailLegacy(uid, db, true);
    return { ok: true };
  }
);

// ─── verifyEmailLink HTTP function — handles the link click ──────────────────

const SUCCESS_DEEP_LINK = "prohost://verify-email/success";

const successPage = (name: string) => `<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Email Verified — ProHost</title>
<style>
  body{margin:0;background:#F5F5F5;font-family:'Helvetica Neue',Arial,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh}
  .card{background:#fff;border-radius:16px;padding:40px 32px;max-width:420px;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,.08)}
  h1{margin:0 0 12px;color:#FF6B35;font-size:24px}
  p{margin:0 0 24px;color:#555;font-size:15px;line-height:1.6}
  a{display:inline-block;padding:12px 28px;background:#FF6B35;color:#fff;border-radius:8px;text-decoration:none;font-weight:600}
</style>
</head>
<body>
<div class="card">
  <h1>✓ Email Verified</h1>
  <p>Hi ${name}, your email address has been confirmed. You can close this page and return to ProHost.</p>
  <a href="${SUCCESS_DEEP_LINK}">Open ProHost</a>
</div>
</body>
</html>`;

const errorPage = (message: string) => `<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"/><title>Verification Failed — ProHost</title>
<style>body{margin:0;background:#F5F5F5;font-family:'Helvetica Neue',Arial,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh}.card{background:#fff;border-radius:16px;padding:40px 32px;max-width:420px;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,.08)}h1{margin:0 0 12px;color:#D32F2F;font-size:22px}p{margin:0;color:#555;font-size:15px}</style>
</head>
<body><div class="card"><h1>Verification Failed</h1><p>${message}</p></div></body>
</html>`;

export const verifyEmailLink = onRequest(
  { secrets: [emailVerificationSecret] },
  async (req, res) => {
    const token = req.query.token as string | undefined;
    if (!token) {
      res.status(400).send(errorPage("Missing verification token. Please use the link from your email."));
      return;
    }

    const secret = emailVerificationSecret.value();
    if (!secret) {
      res.status(500).send(errorPage("Verification service is temporarily unavailable. Please try again later."));
      return;
    }

    let payload: VerificationPayload;
    try {
      payload = verifyToken(token, secret);
    } catch (e) {
      const msg = (e as Error).message;
      const friendly = msg === "expired"
        ? "This verification link has expired. Please request a new one from the ProHost app."
        : "This verification link is invalid or has already been used.";
      res.status(400).send(errorPage(friendly));
      return;
    }

    const db = getFirestore();
    const profileSnap = await db.collection("user_profiles").doc(payload.uid).get();
    const profileData = profileSnap.data();

    if (!profileData) {
      res.status(404).send(errorPage("Account not found."));
      return;
    }

    // Idempotent — already verified is fine
    if (profileData.emailVerified !== true) {
      await db.collection("user_profiles").doc(payload.uid).update({
        emailVerified: true,
        emailVerifiedAt: Date.now(),
        updatedAt: Date.now(),
      });
      logger.info("email_verified", { uid: payload.uid });
    }

    const name = (profileData.fullName as string | undefined) ?? "there";
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.status(200).send(successPage(name));
  }
);
