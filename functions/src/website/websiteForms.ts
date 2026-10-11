/**
 * Public pro-host.tech forms (no sign-in): contact and newsletter subscribe, plus the unsubscribe link.
 * The callables are called from the website with plain fetch (callable protocol: POST {data}).
 * Abuse controls: a hidden honeypot field, and per-IP + per-address limits (takeEmailSendSlot).
 */
import * as logger from "firebase-functions/logger";
import { HttpsError, onRequest } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { sendEmail, maskEmail } from "../lib/email";
import { isPlausibleEmail, takeEmailSendSlot } from "../lib/emailRateLimit";
import { esc } from "../lib/emailTemplates";
import { ROLES, clip, clipText, unsubscribeByToken, upsertContact } from "./addressBook";
import { ADMIN_EMAIL, contactNotificationEmail } from "./mailTemplates";

function clientIp(raw: { ip?: string; headers?: Record<string, unknown> } | undefined): string {
  const fwd = String(raw?.headers?.["x-forwarded-for"] ?? "").split(",")[0].trim();
  return fwd || raw?.ip || "unknown";
}

function role(v: unknown): string {
  const r = clip(v, 60);
  return (ROLES as readonly string[]).includes(r) ? r : (r ? "Other" : "");
}

/** websiteContact({ name, email, phone, organization, role, message, subscribe, website }) */
export const websiteContact = onCall({ cors: true }, async (request) => {
  const d = request.data ?? {};
  if (clip(d.website, 100)) return { ok: true }; // honeypot: bots fill every field
  const email = clip(d.email, 254).toLowerCase();
  const name = clip(d.name, 100);
  const message = clipText(d.message, 4000);
  if (!isPlausibleEmail(email)) throw new HttpsError("invalid-argument", "Please enter a valid email address.");
  if (!name) throw new HttpsError("invalid-argument", "Please tell us your name.");
  if (message.length < 5) throw new HttpsError("invalid-argument", "Please write a short message.");
  await takeEmailSendSlot("web_contact_ip", clientIp(request.rawRequest), 8);
  await takeEmailSendSlot("web_contact_email", email, 5);

  const entry = {
    email, name, message, phone: clip(d.phone, 40), organization: clip(d.organization, 120), role: role(d.role),
  };
  const { subscribed } = await upsertContact({ ...entry, subscribe: d.subscribe === true, source: "contact" });
  const mail = contactNotificationEmail({ ...entry, subscribed });
  const queued = await sendEmail({ to: ADMIN_EMAIL, replyTo: email, ...mail });
  if (!queued) {
    // The request is saved in the address book either way; tell the visitor the truth.
    throw new HttpsError("unavailable", "We saved your message but couldn't notify the team. Please email admin@pro-host.tech.");
  }
  logger.info("website_contact", { email: maskEmail(email), role: entry.role, subscribed });
  return { ok: true };
});

/** websiteSubscribe({ email, name, role, organization, website }) */
export const websiteSubscribe = onCall({ cors: true }, async (request) => {
  const d = request.data ?? {};
  if (clip(d.website, 100)) return { ok: true };
  const email = clip(d.email, 254).toLowerCase();
  if (!isPlausibleEmail(email)) throw new HttpsError("invalid-argument", "Please enter a valid email address.");
  await takeEmailSendSlot("web_subscribe_ip", clientIp(request.rawRequest), 10);
  await takeEmailSendSlot("web_subscribe_email", email, 3);
  await upsertContact({
    email, name: clip(d.name, 100), role: role(d.role), organization: clip(d.organization, 120),
    subscribe: true, source: "subscribe",
  });
  logger.info("website_subscribe", { email: maskEmail(email) });
  return { ok: true };
});

const page = (title: string, body: string) => `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex"><title>${esc(title)} · ProHost</title>
<link rel="icon" href="/logo.png"><link rel="stylesheet" href="/site.css"></head>
<body class="center-page"><nav class="topbar"><a class="home" href="/"><img src="/logo.png" alt="ProHost"><img class="wordmark" src="/logo-wordmark.png" alt=""></a></nav>
<main><div class="card narrow">${body}</div></main></body></html>`;

/**
 * pro-host.tech/unsubscribe?t=TOKEN (hosting rewrite). GET shows a confirm button (mail scanners open every
 * link, so GET never changes anything); POST unsubscribes, including RFC 8058 one-click POSTs from Gmail/Yahoo.
 */
export const mailingUnsubscribe = onRequest({ cors: false }, async (req, res) => {
  const token = String((req.query.t as string | undefined) ?? (req.body?.t as string | undefined) ?? "");
  res.set("Cache-Control", "no-store");
  if (req.method === "POST") {
    const masked = await unsubscribeByToken(token).catch(() => null);
    if (String(req.headers["content-type"] ?? "").includes("form-urlencoded") && req.body?.["List-Unsubscribe"]) {
      res.status(masked ? 200 : 404).send(masked ? "Unsubscribed" : "Unknown link");
      return;
    }
    res.status(masked ? 200 : 404).send(page("Unsubscribe", masked
      ? `<h1>You're unsubscribed</h1><p>${esc(masked)} won't receive ProHost news any more.</p><a class="btn btn-secondary" href="/">Back to pro-host.tech</a>`
      : `<h1>Link not recognised</h1><p>This unsubscribe link is invalid or was already used. Email <a href="mailto:admin@pro-host.tech">admin@pro-host.tech</a> and we'll remove you.</p>`));
    return;
  }
  res.status(200).send(page("Unsubscribe", `<h1>Unsubscribe from ProHost news?</h1>
<p>You'll stop receiving our newsletters and offers. Account and booking emails from the app are not affected.</p>
<form method="POST" action="/unsubscribe?t=${encodeURIComponent(token)}"><input type="hidden" name="t" value="${esc(token)}">
<button class="btn btn-primary" style="width:100%;border:0;font:inherit;font-weight:700;cursor:pointer">Unsubscribe</button></form>
<a class="btn btn-secondary" href="/">Keep me subscribed</a>`));
});
