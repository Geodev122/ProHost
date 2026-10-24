/**
 * pro-host.tech/admin: the code-locked mailing-list console (address book, Excel export, campaigns).
 *
 * Access: the code is checked here, never in the page. Its salted scrypt hash lives in the server-only doc
 * `app_config/mailing_admin` (set by the Maintenance workflow task `mailingcode`); a correct code returns a
 * random session token (12 h) whose sha256 is stored in `mailing_admin_sessions`. Wrong codes are limited per
 * IP and globally, so a 6-digit code can't be brute-forced.
 *
 * Campaigns go to `mailing_list` entries with subscribed == true, one `mail/{id}` document per recipient
 * (personal unsubscribe link + List-Unsubscribe headers), from noreply@pro-host.tech, reply-to admin. The admin
 * gets one BCC copy per campaign. Progress = the Trigger Email extension's `delivery.state` on those docs.
 */
import { createHash, randomBytes, scryptSync, timingSafeEqual } from "crypto";
import * as logger from "firebase-functions/logger";
import { HttpsError } from "firebase-functions/v2/https";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { MAIL_COLLECTION } from "../lib/email";
import { takeEmailSendSlot } from "../lib/emailRateLimit";
import { LIST, MESSAGES, clip, clipText, iso } from "./addressBook";
import { ADMIN_EMAIL, NOREPLY_FROM, SITE, Campaign, campaignEmail, listUnsubscribeHeaders, safeUrl } from "./mailTemplates";
import { WA_DOC, parseWhatsAppConfig, sendAdminWhatsApp, whatsAppMissing } from "../lib/whatsappAlerts";

const SESSIONS = "mailing_admin_sessions";
const CAMPAIGNS = "mail_campaigns";
const SESSION_MS = 12 * 60 * 60 * 1000;
const db = () => getFirestore();

export function hashAdminCode(code: string, salt: string): string {
  return scryptSync(code.trim(), salt, 32).toString("hex");
}

const sha = (s: string) => createHash("sha256").update(s).digest("hex");

async function requireSession(token: unknown): Promise<void> {
  if (typeof token !== "string" || token.length < 32) throw new HttpsError("unauthenticated", "Please enter the admin code.");
  const snap = await db().collection(SESSIONS).doc(sha(token)).get();
  const exp = snap.get("expiresAt");
  if (!snap.exists || !(exp instanceof Timestamp) || exp.toMillis() < Date.now()) {
    throw new HttpsError("unauthenticated", "Your admin session expired. Please enter the code again.");
  }
}

/** mailingAdminLogin({ code }) → { token, expiresAt } */
export const mailingAdminLogin = onCall({ cors: true }, async (request) => {
  const code = String(request.data?.code ?? "");
  const ip = String(request.rawRequest?.headers?.["x-forwarded-for"] ?? request.rawRequest?.ip ?? "?").split(",")[0].trim();
  await takeEmailSendSlot("mailing_admin_ip", ip, 8, 15 * 60 * 1000);
  const failRef = db().collection("email_send_limits").doc("mailing_admin_failures");
  const fails = await failRef.get();
  const recent = fails.exists && Date.now() - (fails.get("windowStart") ?? 0) < 60 * 60 * 1000 ? (fails.get("count") ?? 0) : 0;
  if (recent >= 40) throw new HttpsError("resource-exhausted", "Too many wrong codes. Admin access is paused for an hour.");

  const cfg = (await db().collection("app_config").doc("mailing_admin").get()).data();
  if (!cfg?.hash || !cfg?.salt) throw new HttpsError("failed-precondition", "Admin access isn't set up yet.");
  const ok = /^\d{4,12}$/.test(code.trim()) &&
    timingSafeEqual(Buffer.from(hashAdminCode(code, cfg.salt), "hex"), Buffer.from(cfg.hash, "hex"));
  if (!ok) {
    await failRef.set(recent ? { windowStart: fails.get("windowStart"), count: recent + 1 } : { windowStart: Date.now(), count: 1 });
    logger.warn("mailing_admin_login_failed", { ip: sha(ip).slice(0, 12) });
    throw new HttpsError("permission-denied", "Wrong code.");
  }
  const token = randomBytes(32).toString("hex");
  const expiresAt = Timestamp.fromMillis(Date.now() + SESSION_MS);
  await db().collection(SESSIONS).doc(sha(token)).set({ createdAt: FieldValue.serverTimestamp(), expiresAt });
  logger.info("mailing_admin_login", { ip: sha(ip).slice(0, 12) });
  return { token, expiresAt: expiresAt.toMillis() };
});

/** mailingAdminData({ token }) → address book, contact messages and recent campaigns. */
export const mailingAdminData = onCall({ cors: true, memory: "512MiB" }, async (request) => {
  await requireSession(request.data?.token);
  const [list, msgs, camps] = await Promise.all([
    db().collection(LIST).orderBy("updatedAt", "desc").limit(20000).get(),
    db().collection(MESSAGES).orderBy("createdAt", "desc").limit(20000).get(),
    db().collection(CAMPAIGNS).orderBy("createdAt", "desc").limit(20).get(),
  ]);
  return {
    contacts: list.docs.map((d) => {
      const x = d.data();
      return {
        id: d.id, createdAt: iso(x.createdAt), updatedAt: iso(x.updatedAt), name: x.name ?? "", email: x.email ?? "",
        phone: x.phone ?? "", organization: x.organization ?? "", role: x.role ?? "", lastMessage: x.lastMessage ?? "",
        lastMessageAt: iso(x.lastMessageAt), messagesCount: x.messagesCount ?? 0, subscribed: x.subscribed === true,
        subscribedAt: iso(x.subscribedAt), unsubscribedAt: iso(x.unsubscribedAt), sources: x.sources ?? [], tags: x.tags ?? [],
      };
    }),
    messages: msgs.docs.map((d) => {
      const x = d.data();
      return { createdAt: iso(x.createdAt), name: x.name, email: x.email, phone: x.phone, organization: x.organization, role: x.role, message: x.message, tags: x.tags ?? [] };
    }),
    campaigns: camps.docs.map((d) => ({ id: d.id, subject: d.get("subject"), total: d.get("total"), createdAt: iso(d.get("createdAt")), test: d.get("test") === true })),
  };
});

/** mailingAdminSetSubscribed({ token, id, subscribed }) — manual opt-out / re-opt-in from the console. */
export const mailingAdminSetSubscribed = onCall({ cors: true }, async (request) => {
  await requireSession(request.data?.token);
  const id = String(request.data?.id ?? "");
  if (!/^[a-f0-9]{32}$/.test(id)) throw new HttpsError("invalid-argument", "Unknown contact.");
  const on = request.data?.subscribed === true;
  await db().collection(LIST).doc(id).update({
    subscribed: on, updatedAt: FieldValue.serverTimestamp(),
    ...(on ? { subscribedAt: FieldValue.serverTimestamp(), unsubscribedAt: FieldValue.delete() } : { unsubscribedAt: FieldValue.serverTimestamp() }),
  });
  return { ok: true };
});

function readCampaign(d: Record<string, unknown>): Campaign {
  const c: Campaign = {
    subject: clip(d.subject, 150), preheader: clip(d.preheader, 160), heading: clip(d.heading, 120),
    body: clipText(d.body, 20000), ctaLabel: clip(d.ctaLabel, 40), ctaUrl: clip(d.ctaUrl, 500),
  };
  if (!c.subject) throw new HttpsError("invalid-argument", "Add a subject.");
  if (c.body.length < 10) throw new HttpsError("invalid-argument", "Write the email body.");
  if (c.ctaLabel && !safeUrl(c.ctaUrl)) throw new HttpsError("invalid-argument", "The button needs a full link (https://…).");
  return c;
}

/** mailingAdminPreview({ token, campaign }) → { html } rendered exactly as recipients get it. */
export const mailingAdminPreview = onCall({ cors: true }, async (request) => {
  await requireSession(request.data?.token);
  const c = readCampaign(request.data?.campaign ?? {});
  return { html: campaignEmail(c, { name: "Rana Haddad" }, `${SITE}/unsubscribe?t=preview`).html };
});

/**
 * mailingAdminSend({ token, campaign, testOnly }) → { campaignId, total }.
 * testOnly sends one copy to admin@pro-host.tech. Otherwise one email per subscribed address.
 */
export const mailingAdminSend = onCall({ cors: true, timeoutSeconds: 540, memory: "512MiB" }, async (request) => {
  await requireSession(request.data?.token);
  const c = readCampaign(request.data?.campaign ?? {});
  const testOnly = request.data?.testOnly === true;
  const recipients = testOnly
    ? [{ email: ADMIN_EMAIL, name: "", unsubToken: "test" }]
    : (await db().collection(LIST).where("subscribed", "==", true).get()).docs
      .map((d) => ({ email: String(d.get("email") ?? ""), name: String(d.get("name") ?? ""), unsubToken: String(d.get("unsubToken") ?? "") }))
      .filter((r) => r.email && r.unsubToken);
  if (!recipients.length) throw new HttpsError("failed-precondition", "There are no subscribed contacts yet.");

  const campRef = db().collection(CAMPAIGNS).doc();
  await campRef.set({ ...c, total: recipients.length, test: testOnly, createdAt: FieldValue.serverTimestamp() });
  let batch = db().batch();
  let n = 0;
  for (const [i, r] of recipients.entries()) {
    const unsub = `${SITE}/unsubscribe?t=${encodeURIComponent(r.unsubToken)}`;
    const mail = campaignEmail(c, r, unsub);
    batch.set(db().collection(MAIL_COLLECTION).doc(), {
      to: r.email,
      from: NOREPLY_FROM,
      replyTo: ADMIN_EMAIL,
      ...(i === 0 && !testOnly ? { bcc: ADMIN_EMAIL } : {}), // the admin's copy of the campaign
      campaignId: campRef.id,
      message: { subject: mail.subject, html: mail.html, text: mail.text, headers: listUnsubscribeHeaders(unsub) },
      createdAt: FieldValue.serverTimestamp(),
    });
    if (++n % 400 === 0) {
      await batch.commit();
      batch = db().batch();
    }
  }
  await batch.commit();
  logger.info("mailing_campaign_queued", { campaignId: campRef.id, total: recipients.length, testOnly });
  return { campaignId: campRef.id, total: recipients.length };
});

/** mailingAdminCampaign({ token, campaignId }) → delivery counts and the failed addresses. */
export const mailingAdminCampaign = onCall({ cors: true, memory: "512MiB" }, async (request) => {
  await requireSession(request.data?.token);
  const id = String(request.data?.campaignId ?? "");
  if (!/^[A-Za-z0-9]{10,40}$/.test(id)) throw new HttpsError("invalid-argument", "Unknown campaign.");
  const docs = (await db().collection(MAIL_COLLECTION).where("campaignId", "==", id).get()).docs;
  const counts: Record<string, number> = { PENDING: 0, PROCESSING: 0, RETRY: 0, SUCCESS: 0, ERROR: 0 };
  const failed: { email: string; error: string }[] = [];
  for (const d of docs) {
    const state = String(d.get("delivery.state") ?? "PENDING");
    counts[state] = (counts[state] ?? 0) + 1;
    if (state === "ERROR") failed.push({ email: String(d.get("to") ?? ""), error: String(d.get("delivery.error") ?? "").slice(0, 200) });
  }
  return { total: docs.length, counts, failed };
});

/** mailingAdminRetry({ token, campaignId }) — hands failed deliveries back to the extension. */
export const mailingAdminRetry = onCall({ cors: true }, async (request) => {
  await requireSession(request.data?.token);
  const id = String(request.data?.campaignId ?? "");
  const docs = (await db().collection(MAIL_COLLECTION).where("campaignId", "==", id).where("delivery.state", "==", "ERROR").get()).docs;
  let batch = db().batch();
  for (const [i, d] of docs.entries()) {
    batch.update(d.ref, { "delivery.state": "RETRY" });
    if ((i + 1) % 400 === 0) {
      await batch.commit();
      batch = db().batch();
    }
  }
  await batch.commit();
  return { retried: docs.length };
});

/**
 * mailingAdminWhatsApp({ token, action: "get" | "set" | "test", config? }) — the "WhatsApp alerts" panel of /admin.
 * set: { enabled, phoneNumberId, adminNumber, templateName, templateLang, accessToken? } (a blank token keeps the stored
 * one); the token is write-only: get returns only its last 4 characters. test sends one alert through
 * lib/whatsappAlerts.ts. Stored in the server-only doc app_config/whatsapp.
 */
export const mailingAdminWhatsApp = onCall({ cors: true }, async (request) => {
  await requireSession(request.data?.token);
  const ref = db().collection("app_config").doc(WA_DOC);
  const action = String(request.data?.action ?? "get");
  if (action === "set") {
    const c = (request.data?.config ?? {}) as Record<string, unknown>;
    const token = clip(c.accessToken, 600);
    await ref.set({
      enabled: c.enabled === true, phoneNumberId: clip(c.phoneNumberId, 40).replace(/\D/g, ""),
      adminNumber: clip(c.adminNumber, 30).replace(/\D/g, ""), templateName: clip(c.templateName, 120).replace(/[^a-z0-9_]/g, ""),
      templateLang: clip(c.templateLang, 10) || "en", updatedAt: FieldValue.serverTimestamp(),
      ...(token ? { accessToken: token } : {}),
    }, { merge: true });
    logger.info("whatsapp_alerts_saved", { enabled: c.enabled === true, tokenChanged: !!token });
  }
  if (action === "test") {
    const result = await sendAdminWhatsApp("ProHost test alert: WhatsApp alerts are working. New office leads will arrive here.");
    return { result };
  }
  const d = (await ref.get()).data() ?? {};
  const cfg = parseWhatsAppConfig(d);
  return {
    enabled: cfg.enabled, phoneNumberId: cfg.phoneNumberId, adminNumber: cfg.adminNumber, templateName: cfg.templateName,
    templateLang: cfg.templateLang, tokenSet: !!cfg.accessToken, tokenHint: cfg.accessToken ? "••••" + cfg.accessToken.slice(-4) : "",
    missing: whatsAppMissing(cfg), lastResult: typeof d.lastResult === "string" ? d.lastResult : "",
  };
});
