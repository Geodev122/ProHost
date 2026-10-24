import { test } from "node:test";
import assert from "node:assert/strict";
import { HttpsError } from "firebase-functions/v2/https";
import { leadDocId, leadTags, normalizeWhatsApp, officeLeadEmail, ownerAlertText, parseOfficeLead, profession } from "./officeLeads";
import { LEAD_COLUMNS, leadRow } from "../instagram/rulesSheet";

const form = { name: "Rana K", email: "Rana@Example.com", whatsapp: "00961 76 701 865", profession: "life coach", city: "Beirut", consent: true, utm_source: "instagram", utm_medium: "story" };

test("WhatsApp numbers are normalised to +E.164 or refused", () => {
  assert.equal(normalizeWhatsApp("+961 70-123 456"), "+96170123456");
  assert.equal(normalizeWhatsApp("0096176701865"), "+96176701865");
  assert.equal(normalizeWhatsApp("70123456"), null, "no country code");
  assert.equal(normalizeWhatsApp("+0123"), null);
});

test("the form is validated and tagged for the story campaign", () => {
  const l = parseOfficeLead(form);
  assert.equal(l.email, "rana@example.com");
  assert.equal(l.whatsapp, "+96176701865");
  assert.equal(l.profession, "Life coach");
  assert.equal(l.utm, "instagram / story");
  assert.deepEqual(leadTags(l.profession), ["insta-leads-story", "prospect", "office-owner", "life-coach"]);
  assert.equal(profession("Astrologer"), "Other");
  for (const bad of [{ ...form, consent: false }, { ...form, whatsapp: "123" }, { ...form, email: "x" }, { ...form, name: "" }]) {
    assert.throws(() => parseOfficeLead(bad), HttpsError);
  }
});

test("one sheet row id per address", () => {
  assert.equal(leadDocId("A@b.co"), leadDocId("a@b.co"));
  assert.match(leadDocId("a@b.co"), /^web_[0-9a-f]{24}$/);
});

test("the admin email escapes the visitor's text and links WhatsApp", () => {
  const l = parseOfficeLead({ ...form, name: "<b>Rana</b>", message: "<script>x</script>" });
  const m = officeLeadEmail(l);
  assert.ok(!m.html.includes("<script>") && !m.html.includes("<b>Rana"));
  assert.ok(m.html.includes("https://wa.me/96176701865"));
  assert.ok(m.subject.startsWith("[ProHost Office lead]"));
  assert.ok(ownerAlertText(l).includes("+96176701865"));
});

test("Leads rows carry WhatsApp, source and tags under the header", () => {
  const row = leadRow("web_x", { name: "Rana", email: "r@x.co", whatsapp: "+96176701865", source: "insta_story_form", tags: ["insta-leads-story", "prospect"], updatedAt: 0, consentAt: 0 }, 1);
  assert.equal(row.length, LEAD_COLUMNS.length);
  assert.equal(row[LEAD_COLUMNS.indexOf("whatsapp")], "+96176701865");
  assert.equal(row[LEAD_COLUMNS.indexOf("tags")], "insta-leads-story, prospect");
});
