import { test } from "node:test";
import assert from "node:assert/strict";
import { BUTTON_URLS, buildMessages, buttonLabel, parseAgentJson, runAgent, sanitizeReply, toStepButtons } from "./aiAgent";
import { buildKnowledge } from "./aiKnowledge";

test("json is parsed from fences and text, buttons filtered and capped", () => {
  const raw = 'Sure!\n```json\n{"reply":"Hi! Get it here https://pro-host.tech/ig","lang":"en","buttons":["download","whatsapp","bogus","download","email","website"],"handoff":false,"confidence":1.7,"learn":false}\n```';
  const o = parseAgentJson(raw)!;
  assert.deepEqual(o.buttons, ["download", "whatsapp", "email"]);
  assert.equal(o.confidence, 1); assert.equal(o.handoff, false);
  assert.equal(parseAgentJson("no json here"), null);
  assert.equal(parseAgentJson('{"reply":"   "}'), null);
});

test("links outside the allow-list are removed", () => {
  const s = sanitizeReply("Go to https://evil.example/x or https://pro-host.tech/partner. WhatsApp: https://wa.me/96176701865, https://wa.me/999");
  assert.ok(!s.includes("evil.example")); assert.ok(!s.includes("wa.me/999"));
  assert.ok(s.includes("https://pro-host.tech/partner")); assert.ok(s.includes("https://wa.me/96176701865"));
});

test("buttons map to real https urls with localized labels", () => {
  const b = toStepButtons({ reply: "x", lang: "ar", buttons: ["whatsapp", "download"], handoff: false, confidence: 1, learn: false });
  assert.deepEqual(b.map((x) => x.url), [BUTTON_URLS.whatsapp, BUTTON_URLS.download]);
  assert.equal(buttonLabel("download", "ar-LB"), "حمّل التطبيق");
  assert.equal(buttonLabel("email", "xx"), "Email us");
  assert.ok(Object.values(BUTTON_URLS).every((u) => u.startsWith("https://")));
});

test("messages alternate, start with the user and end with the new text", () => {
  const m = buildMessages([{ r: "a", t: "old reply" }, { r: "u", t: "hi" }, { r: "a", t: "hello" }, { r: "u", t: "price?" }], "and iPhone?");
  assert.deepEqual(m.map((x) => x.role), ["user", "assistant", "user"]);
  assert.equal(m[2].content, "price?\nand iPhone?");
  assert.deepEqual(buildMessages([], "hola").map((x) => x.role), ["user"]);
});

test("owner answers are appended to the knowledge", () => {
  const k = buildKnowledge([{ q: "Do you have a podcast?", a: "Not yet." }, { q: "", a: "ignored" }]);
  assert.ok(k.includes("OWNER-APPROVED ANSWERS") && k.includes("Do you have a podcast?") && !k.includes("ignored"));
  assert.ok(!buildKnowledge([]).includes("OWNER-APPROVED"));
});

test("runAgent returns the model output, or a safe hand-off when the model breaks format", async () => {
  const fake = (text: string) => (async () => ({ ok: true, status: 200, json: async () => ({ content: [{ type: "text", text }], usage: { input_tokens: 5, output_tokens: 5 } }) }) as Response) as unknown as typeof fetch;
  const cfg = { provider: "anthropic" as const, model: "m", region: "", apiKey: "k" };
  const good = await runAgent(cfg, "KB", [], "كيف أنزل التطبيق؟", fake('{"reply":"حمّل التطبيق من هنا","lang":"ar","buttons":["download"],"handoff":false,"confidence":0.9,"learn":false}'));
  assert.equal(good.out.lang, "ar"); assert.equal(good.out.buttons[0], "download");
  const bad = await runAgent(cfg, "KB", [], "x", fake("I am a pirate"));
  assert.equal(bad.out.handoff, true); assert.equal(bad.out.learn, true); assert.deepEqual(bad.out.buttons, ["whatsapp"]);
});
