/**
 * Office-sourcing leads (1.0.46): practitioners (psychologists, life coaches, therapists…) who want to rent out their own
 * office by the hour. The form at pro-host.tech/list-your-office (linked from the Instagram story's link sticker and from
 * the "OFFICE" DM rule) calls `websiteOfficeLead`, which queues the lead for the "ProHost IG Automations" sheet's Leads
 * tab (`ig_leads/web_<hash>`, kind office_owner, synced by igSheetSync). `recordOfficeLead` (run once per lead by
 * officeLeadSync.ts) then
 *   1. saves the person in the website address book (`mailing_list`, /admin) tagged insta-leads-story / prospect / office-owner,
 *   2. alerts the owner: admin push (notification centre), an email to admin@pro-host.tech (reply-to the lead), an
 *      Instagram DM to the owner's personal account (OWNER_INSTAGRAM, only inside Meta's 24 h window) and WhatsApp.
 * WhatsApp alerts go through lib/whatsappAlerts.ts once the owner sets up the WhatsApp Business Cloud API in /admin.
 * Since the DM agent's intake (aiAgent mode F) both paths queue an `ig_leads` doc (kind office_owner) and
 * officeLeadSync.ts (onOfficeLeadSaved) calls recordOfficeLead() once per lead.
 * Pure parts (validation, tags, email) are unit-tested in officeLeads.test.ts.
 */
import { createHash } from "crypto";
import * as logger from "firebase-functions/logger";
import { HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { sendEmail, maskEmail } from "../lib/email";
import { isPlausibleEmail, takeEmailSendSlot } from "../lib/emailRateLimit";
import { esc } from "../lib/emailTemplates";
import { sendPushToAdmins } from "../lib/push";
import { sendAdminWhatsApp } from "../lib/whatsappAlerts";
import { canSend, loadConfig } from "../instagram/config";
import { sendStep } from "../instagram/graph";
import { clip, upsertContact } from "./addressBook";
import { ADMIN_EMAIL } from "./mailTemplates";
import { leadTags, normalizeWhatsApp, profession } from "../lib/officeLeadFields";
export { OFFICE_LEAD_TAGS, PROFESSIONS, leadTags, normalizeWhatsApp, profession } from "../lib/officeLeadFields";

export const OFFICE_LEAD_SOURCE = "insta_story";
export const OFFICE_SUBJECT_TAG = "[ProHost Office lead]";
/** The owner's personal Instagram account; DMs reach it only inside Meta's 24 h window. */
export const OWNER_INSTAGRAM = "geo_elnajjat";
const DAY_MS = 24 * 60 * 60 * 1000;

export interface OfficeLead {
  name: string; email: string; whatsapp: string; profession: string; city: string; message: string;
  lang: "en" | "ar"; utm: string;
  /** form = pro-host.tech/list-your-office, dm = the Instagram DM agent's intake (aiAgent mode F). */
  origin?: "form" | "dm"; instagram?: string;
}

/** Validates the form fields; throws HttpsError with a visitor-facing message. */
export function parseOfficeLead(d: Record<string, unknown>): OfficeLead {
  const email = clip(d.email, 254).toLowerCase();
  const name = clip(d.name, 100);
  if (!name) throw new HttpsError("invalid-argument", "Please tell us your name.");
  if (!isPlausibleEmail(email)) throw new HttpsError("invalid-argument", "Please enter a valid email address.");
  const whatsapp = normalizeWhatsApp(d.whatsapp);
  if (!whatsapp) throw new HttpsError("invalid-argument", "Please enter your WhatsApp number with the country code, e.g. +961 70 123 456.");
  if (d.consent !== true) throw new HttpsError("invalid-argument", "Please agree to be contacted about listing your office.");
  const utm = ["utm_source", "utm_medium", "utm_campaign"].map((k) => clip(d[k], 60)).filter(Boolean).join(" / ");
  return {
    name, email, whatsapp, profession: profession(d.profession), city: clip(d.city, 80), message: clip(d.message, 600),
    lang: d.lang === "ar" ? "ar" : "en", utm,
  };
}

/** Stable sheet row id per address, so a repeat submission updates the same lead. */
export function leadDocId(email: string): string {
  return "web_" + createHash("sha256").update(email.toLowerCase()).digest("hex").slice(0, 24);
}

/** The admin notification email (reply goes straight to the lead). */
export function officeLeadEmail(l: OfficeLead) {
  const wa = `https://wa.me/${l.whatsapp.slice(1)}`;
  const rows: Array<[string, string]> = [
    ["Name", l.name], ["Email", l.email], ["WhatsApp", l.whatsapp], ["Profession", l.profession], ["City", l.city],
    ["Language", l.lang], ["Instagram", l.instagram ? `@${l.instagram}` : ""],
    ["Came from", l.utm || (l.origin === "dm" ? "Instagram DM agent" : "pro-host.tech/list-your-office")], ["Tags", leadTags(l.profession).join(", ")],
  ];
  const table = rows.filter(([, v]) => v).map(([k, v]) =>
    `<tr><td style="padding:6px 14px 6px 0;color:#5B6B7F;font:600 13px/1.5 Arial,sans-serif;white-space:nowrap">${k}</td><td style="padding:6px 0;color:#14213D;font:14px/1.5 Arial,sans-serif">${esc(v)}</td></tr>`).join("");
  const html = `<div style="max-width:560px;margin:0 auto;font-family:Arial,sans-serif">
<p style="margin:0 0 8px"><span style="display:inline-block;background:#F28C28;color:#fff;border-radius:99px;padding:3px 12px;font:700 12px/1.6 Arial,sans-serif;letter-spacing:.04em">${l.origin === "dm" ? "INSTAGRAM DM LEAD" : "INSTAGRAM STORY LEAD"}</span></p>
<h1 style="margin:0 0 6px;color:#14213D;font:800 22px/1.25 Arial,sans-serif">${esc(l.name)} wants to list their office</h1>
<p style="margin:0 0 18px;color:#5B6B7F;font:14px/1.6 Arial,sans-serif">${l.origin === "dm" ? "Registered by the Instagram DM agent (they agreed to be contacted)." : "From the pro-host.tech/list-your-office form."} Reply to this email, or message them on WhatsApp.</p>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 18px">${table}</table>
${l.message ? `<div style="background:#F6F8FB;border-left:4px solid #F28C28;padding:14px 18px;margin:0 0 20px;color:#14213D;font:15px/1.6 Arial,sans-serif;white-space:pre-wrap">${esc(l.message)}</div>` : ""}
<p><a href="${wa}" style="display:inline-block;background:#25D366;color:#fff;text-decoration:none;border-radius:10px;padding:12px 20px;font:700 14px Arial,sans-serif">Message on WhatsApp</a>
&nbsp;<a href="https://pro-host.tech/admin" style="display:inline-block;background:#2B5A8C;color:#fff;text-decoration:none;border-radius:10px;padding:12px 20px;font:700 14px Arial,sans-serif">Open the address book</a></p></div>`;
  const text = `${OFFICE_SUBJECT_TAG} ${l.name} wants to list their office\n\n${rows.filter(([, v]) => v).map(([k, v]) => `${k}: ${v}`).join("\n")}\n\n${l.message}\n\nWhatsApp: ${wa}`;
  return {
    subject: `${OFFICE_SUBJECT_TAG} ${l.name}${l.profession ? ` · ${l.profession}` : ""}${l.city ? ` · ${l.city}` : ""}`.slice(0, 140),
    html, text,
    headers: { "X-ProHost-Form": "office-lead", "X-ProHost-Tags": leadTags(l.profession).join(", "), "X-Priority": "2", Importance: "high" } as Record<string, string>,
  };
}

/** The short DM / push text for the owner. */
export function ownerAlertText(l: OfficeLead): string {
  return `New office lead (${l.origin === "dm" ? "Instagram DM" : "Instagram story form"}): ${l.name}${l.profession ? `, ${l.profession}` : ""}${l.city ? ` in ${l.city}` : ""}. ` +
    `WhatsApp ${l.whatsapp} · ${l.email}`;
}

function clientIp(raw: { ip?: string; headers?: Record<string, unknown> } | undefined): string {
  const fwd = String(raw?.headers?.["x-forwarded-for"] ?? "").split(",")[0].trim();
  return fwd || raw?.ip || "unknown";
}

/** Best effort: DM the owner's Instagram account, only inside the 24 h window Meta allows. */
export async function dmOwner(text: string, now: number): Promise<string> {
  const config = await loadConfig();
  if (!canSend(config)) return "instagram not live";
  const q = await getFirestore().collection("ig_contacts").where("username", "==", OWNER_INSTAGRAM).limit(1).get();
  const owner = q.docs[0];
  if (!owner) return "owner never messaged the account";
  if (now - (Number(owner.get("lastSeenAt")) || 0) > DAY_MS) return "outside the 24 h window";
  await sendStep({ igUserId: config.igUserId, token: config.accessToken }, { type: "dm", recipientId: owner.id, text });
  return "sent";
}

/**
 * Records one office lead everywhere and alerts the owner (called once per lead by officeLeadSync.ts): the website
 * contact book, an admin push, the admin email, an Instagram DM to the owner (24 h window) and WhatsApp (when set up).
 */
export async function recordOfficeLead(lead: OfficeLead, now: number): Promise<Record<string, string>> {
  const tags = leadTags(lead.profession);
  const message = [`Wants to list their office on ProHost.`, lead.profession && `Profession: ${lead.profession}`,
    lead.city && `City: ${lead.city}`, `WhatsApp: ${lead.whatsapp}`, lead.instagram && `Instagram: @${lead.instagram}`,
    lead.utm && `From: ${lead.utm}`, lead.message].filter(Boolean).join("\n");
  await upsertContact({
    email: lead.email, name: lead.name, phone: lead.whatsapp, role: "Office owner", organization: lead.profession,
    message, subscribe: false, source: lead.origin === "dm" ? "instagram_dm" : OFFICE_LEAD_SOURCE, tags,
  });
  const alert = ownerAlertText(lead);
  const [queued, , dm, wa] = await Promise.all([
    sendEmail({ to: ADMIN_EMAIL, replyTo: lead.email, ...officeLeadEmail(lead) }),
    sendPushToAdmins("New office lead", alert, { category: "ADMIN_LEAD", targetTab: "admin_console" }),
    dmOwner(alert, now).catch((e: Error) => `failed: ${e.message.slice(0, 80)}`),
    sendAdminWhatsApp(alert),
  ]);
  const out = { adminEmail: queued ? "queued" : "not queued", instagramDm: dm, whatsapp: wa };
  logger.info("office_lead_recorded", { email: maskEmail(lead.email), origin: lead.origin ?? "form", profession: lead.profession, ...out });
  return out;
}

/**
 * websiteOfficeLead({ name, email, whatsapp, profession, city, message, consent, lang, utm_source, utm_medium,
 * utm_campaign, website }) — `website` is the honeypot. It only validates and queues the lead (`ig_leads`, kind
 * office_owner); officeLeadSync.ts records it in the contact book and alerts, exactly like a DM-agent lead.
 */
export const websiteOfficeLead = onCall({ cors: true }, async (request) => {
  const d = (request.data ?? {}) as Record<string, unknown>;
  if (clip(d.website, 100)) return { ok: true }; // honeypot
  const lead = parseOfficeLead(d);
  await takeEmailSendSlot("web_office_ip", clientIp(request.rawRequest), 8);
  await takeEmailSendSlot("web_office_email", lead.email, 5);
  const now = Date.now();
  await getFirestore().collection("ig_leads").doc(leadDocId(lead.email)).set({
    kind: "office_owner", name: lead.name, email: lead.email, whatsapp: lead.whatsapp, profession: lead.profession, city: lead.city,
    role: lead.profession || "Office owner", need: `list my office${lead.city ? ` (${lead.city})` : ""}`, message: lead.message,
    lang: lead.lang, instagram: "", consentAt: now, source: "insta_story_form", tags: leadTags(lead.profession), utm: lead.utm,
    updatedAt: now, synced: false,
  }, { merge: true });
  logger.info("website_office_lead", { email: maskEmail(lead.email), profession: lead.profession });
  return { ok: true };
});
