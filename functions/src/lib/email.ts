import * as logger from "firebase-functions/logger";
import { defineSecret } from "firebase-functions/params";
import * as nodemailer from "nodemailer";

// Stored in Google Cloud Secret Manager. IMPORTANT: Firebase does NOT
// auto-uppercase or otherwise normalize secret names — whatever name you pass
// to `firebase functions:secrets:set` becomes the literal Secret Manager
// secret name, case-sensitive. Setting it as e.g. "hostinger_smtp_api_key"
// (lowercase) creates a DIFFERENT secret than the "HOSTINGER_SMTP_API_KEY"
// this code looks up below, so sendEmail() silently no-ops (see the
// "no SMTP secret" warn log) with no exception ever thrown. Always set it
// with the exact name below:
// To set: echo '<key>' | npx firebase functions:secrets:set HOSTINGER_SMTP_API_KEY
// After changing the secret's value, redeploy functions so they pick up the
// latest version — an existing deployment keeps using the version it was
// deployed with.
export const hostingerSmtpSecret = defineSecret("HOSTINGER_SMTP_API_KEY");

const FROM_ADDRESS = "ProHost <admin@pro-host.tech>";
const SMTP_HOST = "smtp.hostinger.com";
const SMTP_PORT = 465;
const SMTP_USER = "admin@pro-host.tech";

export interface EmailPayload {
  to: string;
  subject: string;
  html: string;
  text?: string;
  replyTo?: string;
}

/**
 * Sends an email via Hostinger SMTP using the stored API key as the SMTP password.
 * Never throws — email delivery failures are logged but do not propagate.
 */
export async function sendEmail(payload: EmailPayload): Promise<void> {
  const smtpKey = hostingerSmtpSecret.value();
  if (!smtpKey) {
    logger.warn("email_send_skipped", { reason: "no SMTP secret", to: payload.to, subject: payload.subject });
    return;
  }

  const transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: SMTP_PORT,
    secure: true, // port 465 = SSL
    auth: { user: SMTP_USER, pass: smtpKey },
  });

  try {
    const info = await transporter.sendMail({
      from: FROM_ADDRESS,
      to: payload.to,
      subject: payload.subject,
      html: payload.html,
      text: payload.text ?? stripHtml(payload.html),
      replyTo: payload.replyTo,
    });
    logger.info("email_sent", { to: payload.to, subject: payload.subject, messageId: info.messageId });
  } catch (e) {
    logger.error("email_send_failed", {
      to: payload.to,
      subject: payload.subject,
      error: e instanceof Error ? e.message : String(e),
    });
  }
}

function stripHtml(html: string): string {
  return html.replace(/<[^>]+>/g, " ").replace(/\s{2,}/g, " ").trim();
}
