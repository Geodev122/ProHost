import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { createHash, randomInt, timingSafeEqual } from "crypto";
import { onRequest, HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { otpSignInTemplate } from "../lib/emailTemplates";
import { takeEmailSendSlot, isPlausibleEmail } from "../lib/emailRateLimit";

const db = admin.firestore();

// Deep-link scheme used by the Android app for one-click email OTP verification.
const APP_OTP_SCHEME = "prohost://emailotp/verified";

function sanitizeEmailKey(email: string): string {
  return email.toLowerCase().replace(/[^a-z0-9@._-]/g, "_").slice(0, 200);
}

function hashCode(email: string, code: string): string {
  return createHash("sha256").update(`${email}:${code}`).digest("hex");
}

function safeEqualHex(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

/**
 * Callable: sendEmailOtp({ email })
 * Generates a 6-digit OTP, stores only its hash in Firestore, and sends it via Hostinger SMTP.
 * Rate-limited to 5 sends per email per hour (email_send_limits), and each code allows
 * 5 wrong guesses, so one address can't be brute-forced or inbox-flooded.
 */
export const sendEmailOtp = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    const email = (request.data?.email as string | undefined)?.toLowerCase().trim();
    if (!isPlausibleEmail(email)) {
      throw new HttpsError("invalid-argument", "A valid email address is required.");
    }
    await takeEmailSendSlot("otp", email);

    const code = randomInt(100000, 1000000).toString();
    const expiresAt = Date.now() + 10 * 60 * 1000; // 10 minutes
    const docKey = sanitizeEmailKey(email);

    await db.collection("email_otps").doc(docKey).set({
      codeHash: hashCode(email, code),
      expiresAt,
      email,
      attempts: 0,
      createdAt: Date.now(),
    });

    const encodedEmail = encodeURIComponent(email);
    const encodedCode = encodeURIComponent(code);
    const clickUrl = `https://europe-west1-prohost-f766f.cloudfunctions.net/clickEmailOtpLink?email=${encodedEmail}&code=${encodedCode}`;

    const tpl = otpSignInTemplate(email, code, clickUrl);
    const delivered = await sendEmail({ to: email, ...tpl });

    if (!delivered) {
      return { ok: false, error: "Email delivery failed. Please try again or contact support." };
    }

    logger.info("email_otp_sent", { email });
    return { ok: true };
  }
);

/**
 * Callable: verifyEmailOtp({ email, code })
 * Validates the OTP and returns a Firebase custom token.
 */
export const verifyEmailOtp = onCall(async (request) => {
  const email = (request.data?.email as string | undefined)?.toLowerCase().trim();
  const code = (request.data?.code as string | undefined)?.trim();
  if (!email || !code) {
    throw new HttpsError("invalid-argument", "Email and code are required.");
  }

  const customToken = await validateAndConsumeOtp(email, code);
  return { customToken };
});

/**
 * HTTP GET: clickEmailOtpLink?email=...&code=...
 * One-click sign-in from email — validates OTP then redirects to the app deep link.
 */
export const clickEmailOtpLink = onRequest(
  { secrets: [hostingerSmtpSecret] },
  async (req, res) => {
    const email = (req.query.email as string | undefined)?.toLowerCase().trim();
    const code = (req.query.code as string | undefined)?.trim();

    if (!email || !code) {
      res.status(400).send("Missing parameters.");
      return;
    }

    try {
      const customToken = await validateAndConsumeOtp(email, code);
      const encodedToken = encodeURIComponent(customToken);
      res.redirect(`${APP_OTP_SCHEME}?token=${encodedToken}`);
    } catch (err) {
      logger.warn("click_email_otp_failed", { email, error: String(err) });
      res.status(400).send("This sign-in link has expired or already been used.");
    }
  }
);

async function validateAndConsumeOtp(email: string, code: string): Promise<string> {
  const docKey = sanitizeEmailKey(email);
  const docRef = db.collection("email_otps").doc(docKey);

  // One transaction so two concurrent attempts can't both redeem the same code, and
  // the attempt counter can't be raced past its limit. It returns an outcome instead
  // of throwing: a throw would roll back the counter update / expired-doc delete.
  type Outcome = "ok" | "missing" | "expired" | "locked" | "wrong";
  const outcome = await db.runTransaction<Outcome>(async (tx) => {
    const snap = await tx.get(docRef);
    if (!snap.exists) return "missing";
    const data = snap.data() as Record<string, any>;
    if (Date.now() > data.expiresAt) {
      tx.delete(docRef);
      return "expired";
    }
    const attempts: number = data.attempts ?? 0;
    if (attempts >= 5) {
      tx.delete(docRef);
      return "locked";
    }
    // codeHash for new codes; plain `code` only for ones issued before hashing shipped.
    const matches = typeof data.codeHash === "string"
      ? safeEqualHex(data.codeHash, hashCode(email, code))
      : typeof data.code === "string" && safeEqualHex(data.code, code);
    if (!matches) {
      tx.update(docRef, { attempts: attempts + 1 });
      return "wrong";
    }
    tx.delete(docRef);
    return "ok";
  });
  if (outcome === "missing") throw new HttpsError("failed-precondition", "No sign-in code found. Please request a new one.");
  if (outcome === "expired") throw new HttpsError("failed-precondition", "This code has expired. Please request a new one.");
  if (outcome === "locked") throw new HttpsError("resource-exhausted", "Too many incorrect attempts. Please request a new code.");
  if (outcome === "wrong") throw new HttpsError("invalid-argument", "Incorrect code. Please try again.");

  // Look up or create the Firebase Auth user
  // Entering the emailed code proves the address: the Auth user is marked verified, so
  // the ID token carries email_verified and assignInitialRole mirrors it to the profile.
  let uid: string;
  try {
    const user = await admin.auth().getUserByEmail(email);
    uid = user.uid;
    if (!user.emailVerified) await admin.auth().updateUser(uid, { emailVerified: true });
  } catch {
    // New user — create a minimal Firebase Auth account
    const newUser = await admin.auth().createUser({ email, emailVerified: true });
    uid = newUser.uid;
  }

  return admin.auth().createCustomToken(uid);
}
