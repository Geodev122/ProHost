import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { sendEmail, hostingerSmtpSecret } from "../lib/email";
import { signInLinkTemplate, emailVerificationTemplate, UserContext } from "../lib/emailTemplates";
import "../lib/admin";

const CONTINUE_URL = "https://prohost-f766f.web.app/emaillink";
const MAX_VERIFICATION_RESENDS_PER_DAY = 3;

/**
 * Callable: sendSignInEmailLink({ email })
 *
 * Generates a Firebase Auth sign-in link via the Admin SDK (server-side token
 * generation — more secure than client-side sendSignInLinkToEmail) and delivers
 * it via Hostinger SMTP with a fully branded ProHost template.
 *
 * Replaces the sendEmailOtp / verifyEmailOtp / clickEmailOtpLink pipeline.
 * The client completes sign-in by calling auth.signInWithEmailLink(email, link)
 * after tapping the link and handling the deep link intent.
 */
export const sendSignInEmailLink = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    const email = (request.data?.email as string | undefined)?.toLowerCase().trim();
    if (!email || !email.includes("@")) {
      throw new HttpsError("invalid-argument", "A valid email address is required.");
    }

    let link: string;
    try {
      link = await getAuth().generateSignInWithEmailLink(email, {
        url: CONTINUE_URL,
        handleCodeInApp: true,
        android: {
          packageName: "app.geonajjar.prohost",
          installApp: true,
          minimumVersion: "24",
        },
      });
    } catch (e) {
      logger.error("generate_sign_in_link_failed", { email, error: String(e) });
      throw new HttpsError("internal", "Failed to generate sign-in link. Please try again.");
    }

    const tpl = signInLinkTemplate(email, link);
    const delivered = await sendEmail({ to: email, ...tpl });

    if (!delivered) {
      logger.error("sign_in_link_email_failed", { email });
      return { ok: false, error: "Email delivery failed. Please try again or contact support." };
    }

    logger.info("sign_in_link_sent", { email });
    return { ok: true };
  }
);

/**
 * Callable: sendVerificationEmailLink()
 *
 * Generates a Firebase Auth email-verification link via Admin SDK and delivers it
 * via SMTP with a branded template. Rate-limited to 3/day per user.
 *
 * Replaces the resendEmailVerification + verifyEmailLink pipeline. Firebase Auth
 * itself marks emailVerified=true when the user clicks the link; the client reads
 * currentUser.reload() + isEmailVerified to confirm.
 */
export const sendVerificationEmailLink = onCall(
  { secrets: [hostingerSmtpSecret] },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }
    const uid = request.auth.uid;

    const fbUser = await getAuth().getUser(uid);
    if (fbUser.emailVerified) {
      return { ok: true, alreadyVerified: true };
    }
    if (!fbUser.email) {
      throw new HttpsError("failed-precondition", "No email address on this account.");
    }

    const db = getFirestore();
    const profileSnap = await db.collection("user_profiles").doc(uid).get();
    const data = profileSnap.data() ?? {};

    // Rate limit: max MAX_VERIFICATION_RESENDS_PER_DAY in any rolling 24-hour window
    const recentResends = (data.emailVerificationResendCount as number | undefined) ?? 0;
    const lastResendAt = (data.emailVerificationLastResendAt as number | undefined) ?? 0;
    const oneDayAgo = Date.now() - 86_400_000;
    const effectiveCount = lastResendAt < oneDayAgo ? 0 : recentResends;
    if (effectiveCount >= MAX_VERIFICATION_RESENDS_PER_DAY) {
      throw new HttpsError(
        "resource-exhausted",
        `Max ${MAX_VERIFICATION_RESENDS_PER_DAY} verification emails per day. Please check your spam folder.`
      );
    }

    let link: string;
    try {
      link = await getAuth().generateEmailVerificationLink(fbUser.email, {
        url: CONTINUE_URL,
      });
    } catch (e) {
      logger.error("generate_verification_link_failed", { uid, error: String(e) });
      throw new HttpsError("internal", "Failed to generate verification link. Please try again.");
    }

    const userCtx: UserContext = {
      fullName: (data.fullName as string | undefined) ?? "Member",
      email: fbUser.email,
      role: ((data.role as string | undefined) ?? "SPECIALIST") as UserContext["role"],
    };
    const tpl = emailVerificationTemplate(userCtx, link);
    const delivered = await sendEmail({ to: fbUser.email, ...tpl });

    await db.collection("user_profiles").doc(uid).update({
      emailVerificationResendCount: lastResendAt < oneDayAgo ? 1 : recentResends + 1,
      emailVerificationLastResendAt: Date.now(),
    });

    if (!delivered) {
      logger.error("verification_email_failed", { uid });
      return { ok: false, error: "Email delivery failed. Please try again." };
    }

    logger.info("verification_email_sent", { uid });
    return { ok: true };
  }
);
