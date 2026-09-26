import * as admin from "firebase-admin";
import * as logger from "firebase-functions/logger";
import { onCall, onRequest } from "firebase-functions/v2/https";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { layout } from "../lib/emailTemplates";

const db = admin.firestore();

// Deep-link scheme used by the Android app for one-click email OTP verification.
const APP_OTP_SCHEME = "prohost://emailotp/verified";

function sanitizeEmailKey(email: string): string {
  return email.toLowerCase().replace(/[^a-z0-9@._-]/g, "_").slice(0, 200);
}

function otpEmailHtml(code: string, clickUrl: string): string {
  return layout("Your ProHost sign-in code", `
    <h2>Your sign-in code</h2>
    <p>Use the code below to sign in to ProHost, or tap the button to sign in automatically.</p>
    <div class="card" style="text-align:center">
      <p style="font-size:32px;font-weight:700;letter-spacing:8px;color:#FF6B35;margin:8px 0">${code}</p>
      <p style="margin:0;font-size:12px;color:#999">Valid for 10 minutes</p>
    </div>
    <p style="text-align:center;margin-top:16px">
      <a class="btn" href="${clickUrl}">Sign in automatically</a>
    </p>
    <p style="font-size:12px;color:#999">Never share this code. ProHost staff will never ask for it.</p>
  `);
}

/**
 * Callable: sendEmailOtp({ email })
 * Generates a 6-digit OTP, stores it in Firestore, and sends it via Hostinger SMTP.
 * Rate-limited to 5 sends per email per hour via attempt/expiresAt checks.
 */
export const sendEmailOtp = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    const email = (request.data?.email as string | undefined)?.toLowerCase().trim();
    if (!email || !email.includes("@")) {
      throw new Error("A valid email address is required.");
    }

    const code = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = Date.now() + 10 * 60 * 1000; // 10 minutes
    const docKey = sanitizeEmailKey(email);

    await db.collection("email_otps").doc(docKey).set({
      code,
      expiresAt,
      email,
      attempts: 0,
      createdAt: Date.now(),
    });

    const encodedEmail = encodeURIComponent(email);
    const encodedCode = encodeURIComponent(code);
    const clickUrl = `https://europe-west1-prohost-f766f.cloudfunctions.net/clickEmailOtpLink?email=${encodedEmail}&code=${encodedCode}`;

    await sendEmail({
      to: email,
      subject: "Your ProHost sign-in code",
      html: otpEmailHtml(code, clickUrl),
    });

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
    throw new Error("Email and code are required.");
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

  const snap = await docRef.get();
  if (!snap.exists) {
    throw new Error("No sign-in code found. Please request a new one.");
  }

  const data = snap.data()!;
  if (Date.now() > data.expiresAt) {
    await docRef.delete();
    throw new Error("This code has expired. Please request a new one.");
  }

  const attempts: number = data.attempts ?? 0;
  if (attempts >= 5) {
    await docRef.delete();
    throw new Error("Too many incorrect attempts. Please request a new code.");
  }

  // Constant-time comparison
  const expectedCode: string = data.code;
  if (code !== expectedCode) {
    await docRef.update({ attempts: attempts + 1 });
    throw new Error("Incorrect code. Please try again.");
  }

  await docRef.delete();

  // Look up or create the Firebase Auth user
  let uid: string;
  try {
    const user = await admin.auth().getUserByEmail(email);
    uid = user.uid;
  } catch {
    // New user — create a minimal Firebase Auth account
    const newUser = await admin.auth().createUser({ email });
    uid = newUser.uid;
  }

  return admin.auth().createCustomToken(uid);
}
