import { test } from "node:test";
import assert from "node:assert/strict";
import { parseWhatsAppConfig, templateText, whatsAppMissing, whatsAppPayload } from "./whatsappAlerts";

test("WhatsApp alerts stay off until everything is configured", () => {
  assert.deepEqual(whatsAppMissing(parseWhatsAppConfig(undefined)), ["switched off", "phone number ID", "access token", "admin WhatsApp number"]);
  const c = parseWhatsAppConfig({ enabled: true, phoneNumberId: "1234 5678", accessToken: " EAAG… ", adminNumber: "+961 76 701 865" });
  assert.deepEqual(whatsAppMissing(c), []);
  assert.equal(c.phoneNumberId, "12345678");
  assert.equal(c.adminNumber, "96176701865");
  assert.equal(c.templateLang, "en");
});

test("alerts use the approved template (one body parameter) or plain text without one", () => {
  const base = parseWhatsAppConfig({ enabled: true, phoneNumberId: "1", accessToken: "t", adminNumber: "96176701865" });
  const text = whatsAppPayload(base, "New office lead\nKarim") as { type: string; text: { body: string } };
  assert.equal(text.type, "text");
  assert.equal(text.text.body, "New office lead\nKarim");
  const tpl = whatsAppPayload({ ...base, templateName: "new_lead_alert", templateLang: "ar" }, "New office lead\nKarim") as {
    type: string; to: string; template: { name: string; language: { code: string }; components: Array<{ parameters: Array<{ text: string }> }> } };
  assert.equal(tpl.type, "template");
  assert.equal(tpl.to, "96176701865");
  assert.equal(tpl.template.name, "new_lead_alert");
  assert.equal(tpl.template.language.code, "ar");
  assert.equal(tpl.template.components[0].parameters[0].text, "New office lead · Karim");
});

test("template parameters never carry new lines, tabs or long space runs", () => {
  assert.equal(templateText("a\n\nb\tc     d"), "a · b · c d");
  assert.ok(templateText("x".repeat(2000)).length <= 1000);
});
