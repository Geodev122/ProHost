import { test } from "node:test";
import assert from "node:assert/strict";
import { campaignEmail, contactNotificationEmail, formatBody, listUnsubscribeHeaders, safeUrl } from "./mailTemplates";

test("formatBody escapes HTML and formats paragraphs, bullets, bold and links", () => {
  const html = formatBody("Hello <script>x</script>\n\n- one\n- **two**\n\nSee https://pro-host.tech/get?a=1&b=2.");
  assert.ok(!html.includes("<script>"));
  assert.ok(html.includes("&lt;script&gt;"));
  assert.ok(html.includes("<ul"));
  assert.ok(html.includes("<strong>two</strong>"));
  assert.ok(html.includes('href="https://pro-host.tech/get?a=1&amp;b=2"'));
  assert.ok(html.includes("</a>.</p>"), "the trailing full stop stays outside the link");
});

test("campaign email carries greeting, CTA, signature and the personal unsubscribe link", () => {
  const m = campaignEmail(
    { subject: " Big news ", heading: "Hi there", body: "Body text", ctaLabel: "Get the app", ctaUrl: "https://pro-host.tech/get" },
    { name: "Rana Haddad" }, "https://pro-host.tech/unsubscribe?t=abc");
  assert.equal(m.subject, "Big news");
  assert.ok(m.html.includes("Hi Rana,"));
  assert.ok(m.html.includes("The ProHost team"));
  assert.ok(m.html.includes("https://pro-host.tech/unsubscribe?t=abc"));
  assert.ok(m.html.includes(">Get the app<"));
  assert.ok(m.text.includes("Unsubscribe: https://pro-host.tech/unsubscribe?t=abc"));
});

test("unsafe CTA links are dropped", () => {
  assert.equal(safeUrl("javascript:alert(1)"), null);
  const m = campaignEmail({ subject: "S", body: "Body text", ctaLabel: "Click", ctaUrl: "javascript:alert(1)" }, {}, "https://x/u");
  assert.ok(!m.html.includes("javascript:"));
});

test("one-click unsubscribe headers", () => {
  const h = listUnsubscribeHeaders("https://pro-host.tech/unsubscribe?t=abc");
  assert.equal(h["List-Unsubscribe-Post"], "List-Unsubscribe=One-Click");
  assert.ok(h["List-Unsubscribe"].startsWith("<https://pro-host.tech/unsubscribe?t=abc>"));
});

test("contact notification escapes visitor input", () => {
  const m = contactNotificationEmail({ name: "<b>Eve</b>", email: "e@x.com", message: "<img src=x onerror=1>", subscribed: true });
  assert.ok(!m.html.includes("<img src=x"));
  assert.ok(m.html.includes("&lt;b&gt;Eve&lt;/b&gt;"));
});
