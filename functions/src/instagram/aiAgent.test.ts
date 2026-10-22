import { test } from "node:test";
import assert from "node:assert/strict";
import { AgentOutput, BUTTON_URLS, salvage, NEW_STATE, advance, buildMessages, buttonLabel, cleanLead, contextBlock, parseAgentJson, runAgent, sanitizeReply, toStepButtons } from "./aiAgent";
import { buildKnowledge } from "./aiKnowledge";

const OUT: AgentOutput = { reply: "x", messages: ["x"], awaitReply: false, askEmail: false, lang: "en", buttons: [], handoff: false, confidence: 1, learn: false, stage: "answer", askConsent: false, consentGiven: null, lead: {} };

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
  const b = toStepButtons({ ...OUT, lang: "ar", buttons: ["whatsapp", "download"] });
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

test("runAgent returns the model output, retries once on broken output, salvages prose, else hands off safely", async () => {
  const seq = (texts: string[]) => { const calls: number[] = []; const f = (async () => { calls.push(1); const text = texts[Math.min(calls.length - 1, texts.length - 1)]; return { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text }], usage: { input_tokens: 5, output_tokens: 5 } }) } as Response; }) as unknown as typeof fetch; return { f, calls }; };
  const cfg = { provider: "anthropic" as const, model: "m", region: "", apiKey: "k" };
  const good = seq(['{"reply":"حمّل التطبيق من هنا","lang":"ar","buttons":["download"],"handoff":false,"confidence":0.9,"learn":false}']);
  const g = await runAgent(cfg, "KB", [], "كيف أنزل التطبيق؟", good.f);
  assert.equal(g.out.lang, "ar"); assert.equal(g.out.buttons[0], "download"); assert.equal(good.calls.length, 1);
  const retry = seq(['{"reply":"cut off', '{"reply":"Second try works","lang":"en"}']);
  assert.equal((await runAgent(cfg, "KB", [], "x", retry.f)).out.reply, "Second try works"); assert.equal(retry.calls.length, 2);
  const prose = seq(["Sure, I can help you list your training center on ProHost, it takes a few minutes."]);
  const p = await runAgent(cfg, "KB", [], "x", prose.f);
  assert.ok(p.out.reply.startsWith("Sure, I can help")); assert.equal(p.out.handoff, false);
  const bad = seq(["I am a pirate"]);
  const b = await runAgent(cfg, "KB", [], "x", bad.f);
  assert.equal(b.out.handoff, true); assert.equal(b.out.learn, true); assert.deepEqual(b.out.buttons, ["whatsapp"]);
  assert.equal(salvage('{"reply":'), null);
});

test("lead fields are validated and cleaned", () => {
  assert.deepEqual(cleanLead({ name: "  Nada  <b>El-Haj</b> ", email: "NADA@Example.COM ", role: "Physiotherapist", need: "a room 2 mornings a week" }),
    { name: "Nada bEl-Hajb", email: "nada@example.com", role: "Physiotherapist", need: "a room 2 mornings a week" });
  assert.deepEqual(cleanLead({ email: "not-an-email", name: "12345" }), {});
  assert.deepEqual(cleanLead(null), {});
});

test("model output carries stage, consent flags (tri-state) and lead", () => {
  const o = parseAgentJson('{"reply":"May I save your name and email?","lang":"en","stage":"capture","askConsent":true,"consentGiven":null,"lead":{"name":"Nada","role":"physio"}}')!;
  assert.deepEqual([o.stage, o.askConsent, o.consentGiven, o.lead.name], ["capture", true, null, "Nada"]);
  assert.equal(parseAgentJson('{"reply":"ok","consentGiven":false}')!.consentGiven, false);
  assert.equal(parseAgentJson('{"reply":"ok","stage":"bogus"}')!.stage, "answer");
});

test("consent: nothing is persisted until a yes to a question asked in an earlier turn", () => {
  // turn 1: the person says their name; no consent yet -> nothing to persist
  let r = advance(NEW_STATE, { ...OUT, stage: "discover", lead: { name: "Nada", role: "physio" } });
  assert.equal(r.persist, null); assert.equal(r.state.exchanges, 1);
  // turn 2: the agent asks; even if the model wrongly marks consentGiven in the same turn it does not count
  r = advance(r.state, { ...OUT, askConsent: true, consentGiven: true });
  assert.equal(r.persist, null); assert.equal(r.state.consentAsked, true); assert.equal(r.state.consent, false);
  // turn 3: they say yes and give an email -> persisted
  r = advance(r.state, { ...OUT, consentGiven: true, lead: { email: "nada@example.com" } });
  assert.deepEqual(r.persist, { name: "Nada", role: "physio", email: "nada@example.com" }); assert.equal(r.state.consent, true);
  // later turns keep persisting new facts without asking again
  r = advance(r.state, { ...OUT, lead: { need: "a room two mornings" } });
  assert.equal(r.persist?.need, "a room two mornings");
});

test("declining is final and never persists", () => {
  let r = advance(NEW_STATE, { ...OUT, askConsent: true, lead: { name: "Sam" } });
  r = advance(r.state, { ...OUT, consentGiven: false, lead: { email: "sam@example.com" } });
  assert.equal(r.persist, null); assert.equal(r.state.declined, true);
  r = advance(r.state, { ...OUT, consentGiven: true });
  assert.equal(r.persist, null); assert.equal(r.state.consent, false);
  assert.ok(/declined/.test(contextBlock(r.state)));
});

test("context block tells the model what the server knows", () => {
  assert.ok(/NOT been asked/.test(contextBlock(NEW_STATE)));
  assert.ok(/name=Nada/.test(contextBlock({ ...NEW_STATE, lead: { name: "Nada" }, exchanges: 3 })) && /connect ProHost/.test(contextBlock({ ...NEW_STATE, exchanges: 3 })));
});

test("multi-message answers keep order, cap at 3 and fall back to a single reply", () => {
  const o = parseAgentJson('{"messages":["Hey Sam","Let me check this for you. Email you registered with?","third","fourth"],"stage":"assist","askEmail":true,"awaitReply":true,"lang":"en"}')!;
  assert.deepEqual(o.messages, ["Hey Sam", "Let me check this for you. Email you registered with?", "third"]);
  assert.equal(o.askEmail, true); assert.equal(o.awaitReply, true); assert.equal(o.stage, "assist");
  assert.deepEqual(parseAgentJson('{"reply":"only one"}')!.messages, ["only one"]);
});

test("state remembers what the last message waits for and clears it on close", () => {
  assert.equal(advance(NEW_STATE, { ...OUT, askEmail: true, awaitReply: true }).state.awaiting, "email");
  assert.equal(advance(NEW_STATE, { ...OUT, awaitReply: true }).state.awaiting, "reply");
  assert.equal(advance({ ...NEW_STATE, awaiting: "email" }, { ...OUT, stage: "close", awaitReply: true }).state.awaiting, "");
  const c = contextBlock({ ...NEW_STATE, awaiting: "email", name: "Sam" }, "LIVE X");
  assert.ok(/name|Sam/.test(c) && /registration email/.test(c) && /LIVE X/.test(c));
});
