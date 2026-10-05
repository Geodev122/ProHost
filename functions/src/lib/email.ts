import * as logger from "firebase-functions/logger";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import "./admin";

/**
 * Outgoing email goes through the Firebase "Trigger Email" extension
 * (firebase/firestore-send-email, see firebase.json › extensions): a document in
 * `mail/{id}` is picked up and delivered by the extension with the provider configured
 * in its SMTP_CONNECTION_URI secret param. There is no SMTP client in these functions
 * any more (the Hostinger mailbox was retired). The extension writes the delivery
 * outcome back onto the same document (`delivery.state`, `delivery.error`).
 *
 * `mail` is server-only (firestore.rules deny), so only functions queue mail.
 */
export const MAIL_COLLECTION = "mail";

export interface EmailPayload {
  to: string;
  subject: string;
  html: string;
  text?: string;
  replyTo?: string;
}

/** "a***@example.com" — never log full addresses. */
export function maskEmail(email: string | null | undefined): string {
  if (!email) return "";
  const at = email.indexOf("@");
  return at <= 0 ? "***" : `${email[0]}***${email.slice(at)}`;
}

/**
 * Queues an email for the Trigger Email extension. Returns true once queued, false if
 * the queue write failed. Never throws.
 */
export async function sendEmail(payload: EmailPayload): Promise<boolean> {
  try {
    const ref = await getFirestore().collection(MAIL_COLLECTION).add({
      to: payload.to,
      ...(payload.replyTo ? { replyTo: payload.replyTo } : {}),
      message: {
        subject: payload.subject,
        html: payload.html,
        text: payload.text ?? stripHtml(payload.html),
      },
      createdAt: FieldValue.serverTimestamp(),
    });
    logger.info("email_queued", { to: maskEmail(payload.to), subject: payload.subject, id: ref.id });
    return true;
  } catch (e) {
    logger.error("email_queue_failed", {
      to: maskEmail(payload.to),
      subject: payload.subject,
      error: e instanceof Error ? e.message : String(e),
    });
    return false;
  }
}

function stripHtml(html: string): string {
  return html.replace(/<[^>]+>/g, " ").replace(/\s{2,}/g, " ").trim()
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, "\"").replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&");
}
