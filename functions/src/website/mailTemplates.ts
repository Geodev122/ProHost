/**
 * Website / mailing-list emails: the reusable ProHost campaign template (with signature and unsubscribe),
 * the admin notification for contact-form requests. Brand: Steel Blue #2B5A8C, Carnation Orange #F25F4C.
 * Table-based, inline styles only (Gmail/Outlook strip <style> blocks in many cases).
 */
import { esc } from "../lib/emailTemplates";

export const SITE = "https://pro-host.tech";
export const ADMIN_EMAIL = "admin@pro-host.tech";
export const NOREPLY_FROM = "ProHost <noreply@pro-host.tech>";
export const GET_APP_URL = `${SITE}/get?utm_source=email&utm_medium=email&utm_campaign=newsletter`;

const BLUE = "#2B5A8C";
const ORANGE = "#F25F4C";
const INK = "#1F2A37";
const MUTED = "#5D6B7E";
const BG = "#EEF2F7";
const FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif";

export interface Campaign {
  subject: string;
  preheader?: string;
  heading?: string;
  body: string;          // plain text written by the admin (see formatBody)
  ctaLabel?: string;
  ctaUrl?: string;
}

/**
 * Admin-written text → safe HTML. Everything is escaped first; then:
 * blank line = new paragraph, "- " lines = bullet list, **bold**, and http(s) URLs become links.
 */
export function formatBody(text: string): string {
  const inline = (s: string) => esc(s)
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(https?:\/\/[^\s<]+[^\s<.,;:!?)])/g,
      `<a href="$1" style="color:${BLUE};text-decoration:underline">$1</a>`);
  const isBullet = (l: string) => /^\s*[-•]\s+/.test(l);
  const para = (ls: string[]) => `<p style="margin:0 0 16px;color:${INK};font:15px/1.65 ${FONT}">${ls.map(inline).join("<br>")}</p>`;
  const list = (ls: string[]) => `<ul style="margin:0 0 16px;padding-left:22px;color:${INK};font:15px/1.65 ${FONT}">` +
    ls.map((l) => `<li style="margin:0 0 6px">${inline(l.replace(/^\s*[-•]\s+/, ""))}</li>`).join("") + "</ul>";
  return String(text ?? "").replace(/\r\n/g, "\n").trim().split(/\n{2,}/).map((block) => {
    // consecutive "- " lines become one list; other lines stay paragraphs
    const out: string[] = [];
    let run: string[] = [];
    let bullets = false;
    const flush = () => { if (run.length) out.push(bullets ? list(run) : para(run)); run = []; };
    for (const l of block.split("\n")) {
      if (isBullet(l) !== bullets) { flush(); bullets = isBullet(l); }
      run.push(l);
    }
    flush();
    return out.join("");
  }).join("");
}

export function safeUrl(url: string | undefined): string | null {
  if (!url) return null;
  try {
    const u = new URL(url.trim());
    return u.protocol === "https:" || u.protocol === "http:" ? u.toString() : null;
  } catch {
    return null;
  }
}

function shell(inner: string, preheader: string, footer: string): string {
  const pre = preheader
    ? `<div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all">${esc(preheader)}${"&nbsp;&#8204;".repeat(40)}</div>`
    : "";
  return `<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><meta name="supported-color-schemes" content="light"><title>ProHost</title></head>
<body style="margin:0;padding:0;background:${BG}">${pre}
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:${BG}"><tr><td align="center" style="padding:28px 12px 40px">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#FFFFFF;border-radius:18px;overflow:hidden;box-shadow:0 8px 28px rgba(16,32,60,.10)">
<tr><td style="background:${BLUE};background-image:linear-gradient(135deg,#33608F,#1C2A40);padding:22px 32px">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
    <td style="padding-right:12px;vertical-align:middle"><img src="${SITE}/logo.png" width="42" height="42" alt="ProHost" style="display:block;border:0;border-radius:10px;background:#FFFFFF"></td>
    <td style="vertical-align:middle"><div style="color:#FFFFFF;font:800 22px/1 ${FONT};letter-spacing:-.4px">Pro<span style="color:${ORANGE}">Host</span></div>
      <div style="color:rgba(255,255,255,.72);font:600 11px/1.6 ${FONT};letter-spacing:1.2px;text-transform:uppercase;margin-top:4px">Flexible workspaces</div></td>
  </tr></table>
</td></tr>
<tr><td style="height:4px;background:${ORANGE};line-height:4px;font-size:0">&nbsp;</td></tr>
<tr><td style="padding:32px 32px 8px">${inner}</td></tr>
${footer}
</table></td></tr></table></body></html>`;
}

/** The ProHost signature used at the end of every campaign. */
export function signature(): string {
  return `<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px;border-top:1px solid #E5EAF1;width:100%"><tr><td style="padding-top:18px">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
    <td style="padding-right:14px;vertical-align:top"><img src="${SITE}/logo.png" width="40" height="40" alt="" style="display:block;border:0;border-radius:9px"></td>
    <td style="vertical-align:top;font:14px/1.55 ${FONT};color:${INK}">
      <strong>The ProHost team</strong><br>
      <span style="color:${MUTED}">Rent and book clinics, offices, studios and meeting rooms, by the hour, shift, day or month.</span><br>
      <a href="${SITE}" style="color:${BLUE};text-decoration:none;font-weight:600">pro-host.tech</a>
      <span style="color:#B7C2D0">&nbsp;·&nbsp;</span>
      <a href="mailto:${ADMIN_EMAIL}" style="color:${BLUE};text-decoration:none;font-weight:600">${ADMIN_EMAIL}</a>
      <span style="color:#B7C2D0">&nbsp;·&nbsp;</span>
      <a href="${GET_APP_URL}" style="color:${ORANGE};text-decoration:none;font-weight:700">Get the app</a>
    </td>
  </tr></table>
</td></tr></table>`;
}

function button(label: string, url: string, color = ORANGE): string {
  return `<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:8px 0 24px"><tr>
<td style="border-radius:12px;background:${color}"><a href="${esc(url)}" style="display:inline-block;padding:14px 30px;font:700 16px/1 ${FONT};color:#FFFFFF;text-decoration:none;border-radius:12px">${esc(label)}</a></td></tr></table>`;
}

/**
 * Campaign email for one recipient. `unsubscribeUrl` is that recipient's personal link
 * (also sent as the List-Unsubscribe header, see listUnsubscribeHeaders).
 */
export function campaignEmail(c: Campaign, recipient: { name?: string }, unsubscribeUrl: string) {
  const first = String(recipient.name ?? "").trim().split(/\s+/)[0];
  const greeting = first ? `<p style="margin:0 0 16px;color:${INK};font:15px/1.65 ${FONT}">Hi ${esc(first)},</p>` : "";
  const heading = c.heading?.trim()
    ? `<h1 style="margin:0 0 18px;color:${INK};font:800 26px/1.2 ${FONT};letter-spacing:-.5px">${esc(c.heading.trim())}</h1>` : "";
  const cta = c.ctaLabel?.trim() && safeUrl(c.ctaUrl) ? button(c.ctaLabel.trim(), safeUrl(c.ctaUrl)!) : "";
  const footer = `<tr><td style="background:#F6F8FB;padding:22px 32px 26px;text-align:center;font:12.5px/1.6 ${FONT};color:${MUTED}">
  You're receiving this because you subscribed to ProHost news on pro-host.tech or contacted us.<br>
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:12px auto 10px"><tr>
    <td style="border-radius:10px;border:1px solid #C9D3E0;background:#FFFFFF"><a href="${esc(unsubscribeUrl)}" style="display:inline-block;padding:9px 20px;font:600 13px/1 ${FONT};color:${INK};text-decoration:none">Unsubscribe</a></td>
  </tr></table>
  © ${new Date().getFullYear()} ProHost · <a href="${SITE}/privacy" style="color:${MUTED}">Privacy</a> · <a href="${SITE}/terms" style="color:${MUTED}">Terms</a>
</td></tr>`;
  const html = shell(`${heading}${greeting}${formatBody(c.body)}${cta}${signature()}`, c.preheader ?? "", footer);
  const text = [
    c.heading?.trim(), first ? `Hi ${first},` : "", String(c.body ?? "").replace(/\*\*/g, ""),
    c.ctaLabel?.trim() && safeUrl(c.ctaUrl) ? `${c.ctaLabel.trim()}: ${safeUrl(c.ctaUrl)}` : "",
    "—\nThe ProHost team\npro-host.tech · admin@pro-host.tech\nGet the app: " + GET_APP_URL,
    `Unsubscribe: ${unsubscribeUrl}`,
  ].filter(Boolean).join("\n\n");
  return { subject: c.subject.trim(), html, text };
}

/** RFC 2369 + RFC 8058 one-click unsubscribe (Gmail/Yahoo bulk-sender requirement). */
export function listUnsubscribeHeaders(unsubscribeUrl: string): Record<string, string> {
  return {
    "List-Unsubscribe": `<${unsubscribeUrl}>, <mailto:${ADMIN_EMAIL}?subject=Unsubscribe>`,
    "List-Unsubscribe-Post": "List-Unsubscribe=One-Click",
  };
}

export interface ContactRequest {
  name: string; email: string; phone?: string; organization?: string; role?: string; message: string; subscribed: boolean;
}

/** Tags carried by every partner enquiry: in the subject, in X-ProHost-* headers and on the address-book entry. */
export const PARTNER_TAGS = ["partner", "strategic-sponsor", "usd-24000"] as const;
export const PARTNER_SUBJECT_TAG = "[ProHost Partner]";

export interface PartnerRequest extends ContactRequest { markets?: string; timeline?: string }

/** Notification to admin@pro-host.tech for a /partner enquiry. Filter on the subject tag or the X-ProHost-Form header. */
export function partnerNotificationEmail(r: PartnerRequest) {
  const row = (k: string, v?: string) => v ? `<tr><td style="padding:6px 14px 6px 0;color:${MUTED};font:600 13px/1.5 ${FONT};white-space:nowrap;vertical-align:top">${k}</td>
<td style="padding:6px 0;color:${INK};font:14px/1.5 ${FONT}">${esc(v)}</td></tr>` : "";
  const inner = `<p style="margin:0 0 8px"><span style="display:inline-block;background:${ORANGE};color:#fff;border-radius:99px;padding:3px 12px;font:700 12px/1.6 ${FONT};letter-spacing:.04em">PARTNER ENQUIRY</span></p>
<h1 style="margin:0 0 6px;color:${INK};font:800 22px/1.25 ${FONT}">Strategic partner enquiry</h1>
<p style="margin:0 0 18px;color:${MUTED};font:14px/1.6 ${FONT}">From the pro-host.tech/partner form (USD 24,000 package). Reply to this email to answer ${esc(r.name)} directly.</p>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 18px">
${row("Name", r.name)}${row("Email", r.email)}${row("Phone", r.phone)}${row("Organization", r.organization)}${row("Role", r.role)}${row("Markets of interest", r.markets)}${row("Timeline", r.timeline)}${row("Tags", PARTNER_TAGS.join(", "))}
</table>
<div style="background:#F6F8FB;border-left:4px solid ${ORANGE};border-radius:0 10px 10px 0;padding:14px 18px;margin:0 0 24px;color:${INK};font:15px/1.65 ${FONT};white-space:pre-wrap">${esc(r.message)}</div>
${button("Open the address book", `${SITE}/admin`, BLUE)}`;
  const footer = `<tr><td style="background:#F6F8FB;padding:16px 32px;text-align:center;font:12px/1.6 ${FONT};color:${MUTED}">ProHost website · partner form · ${PARTNER_TAGS.join(" · ")}</td></tr>`;
  return {
    subject: `${PARTNER_SUBJECT_TAG} ${r.name}${r.organization ? ` (${r.organization})` : ""}`.slice(0, 140),
    html: shell(inner, `Partner enquiry from ${r.name}: ${r.message.slice(0, 80)}`, footer),
    text: `${PARTNER_SUBJECT_TAG} Strategic partner enquiry\n\nName: ${r.name}\nEmail: ${r.email}\nPhone: ${r.phone ?? ""}\nOrganization: ${r.organization ?? ""}\nRole: ${r.role ?? ""}\nMarkets: ${r.markets ?? ""}\nTimeline: ${r.timeline ?? ""}\nTags: ${PARTNER_TAGS.join(", ")}\n\n${r.message}\n`,
    headers: { "X-ProHost-Form": "partner", "X-ProHost-Tags": PARTNER_TAGS.join(", "), "X-Priority": "2", Importance: "high" } as Record<string, string>,
  };
}

/** Notification to admin@pro-host.tech for a website contact request (reply goes straight to the visitor). */
export function contactNotificationEmail(r: ContactRequest) {
  const row = (k: string, v?: string) => v ? `<tr><td style="padding:6px 14px 6px 0;color:${MUTED};font:600 13px/1.5 ${FONT};white-space:nowrap;vertical-align:top">${k}</td>
<td style="padding:6px 0;color:${INK};font:14px/1.5 ${FONT}">${esc(v)}</td></tr>` : "";
  const inner = `<h1 style="margin:0 0 6px;color:${INK};font:800 22px/1.25 ${FONT}">New contact request</h1>
<p style="margin:0 0 18px;color:${MUTED};font:14px/1.6 ${FONT}">From the pro-host.tech contact form. Reply to this email to answer ${esc(r.name)} directly.</p>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 18px">
${row("Name", r.name)}${row("Email", r.email)}${row("Phone", r.phone)}${row("Organization", r.organization)}${row("Role", r.role)}
${row("Newsletter", r.subscribed ? "Subscribed" : "Not subscribed")}
</table>
<div style="background:#F6F8FB;border-left:4px solid ${ORANGE};border-radius:0 10px 10px 0;padding:14px 18px;margin:0 0 24px;color:${INK};font:15px/1.65 ${FONT};white-space:pre-wrap">${esc(r.message)}</div>
${button("Open the address book", `${SITE}/admin`, BLUE)}`;
  const footer = `<tr><td style="background:#F6F8FB;padding:16px 32px;text-align:center;font:12px/1.6 ${FONT};color:${MUTED}">ProHost website · contact form</td></tr>`;
  return {
    subject: `New contact: ${r.name}${r.organization ? ` (${r.organization})` : ""}`.slice(0, 140),
    html: shell(inner, `${r.name}: ${r.message.slice(0, 80)}`, footer),
    text: `New contact request\n\nName: ${r.name}\nEmail: ${r.email}\nPhone: ${r.phone ?? ""}\nOrganization: ${r.organization ?? ""}\nRole: ${r.role ?? ""}\nNewsletter: ${r.subscribed ? "yes" : "no"}\n\n${r.message}`,
  };
}
