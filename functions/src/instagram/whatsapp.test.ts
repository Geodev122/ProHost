import { test } from "node:test";
import assert from "node:assert/strict";
import { DEFAULT_SETTINGS, decide } from "./engine";
import { WaError, entryPhoneNumberId, isWhatsAppId, parseWhatsAppEntry, sendWhatsApp, whatsappPayload } from "./whatsapp";
import { parseRulesSheet, RULE_COLUMNS } from "./rulesSheet";
import { Rule } from "./types";

const entry = {
  id: "WABA1",
  changes: [{
    field: "messages",
    value: {
      messaging_product: "whatsapp", metadata: { display_phone_number: "15550001111", phone_number_id: "3003" },
      contacts: [{ profile: { name: "Sara Khoury" }, wa_id: "96170111222" }],
      messages: [
        { from: "96170111222", id: "wamid.A", timestamp: "1760000000", type: "text", text: { body: "Hi, how do I book?" } },
        { from: "96170111222", id: "wamid.B", timestamp: "1760000005", type: "interactive", interactive: { type: "button_reply", button_reply: { id: "MORE_INFO", title: "More info" } } },
        { from: "96170111222", id: "wamid.C", timestamp: "1760000009", type: "image", image: { id: "x" } },
        { from: "3003", id: "wamid.SELF", timestamp: "1760000010", type: "text", text: { body: "our own echo" } },
      ],
    },
  }, { field: "message_template_status_update", value: {} }],
};

test("inbound WhatsApp messages become events keyed by wa:<number>", () => {
  assert.equal(entryPhoneNumberId(entry), "3003");
  const events = parseWhatsAppEntry(entry, "3003");
  assert.equal(events.length, 2, "text and button reply; image and own echo ignored");
  assert.equal(events[0].id, "wm_wamid.A");
  assert.equal(events[0].channel, "whatsapp");
  assert.equal(events[0].senderId, "wa:96170111222");
  assert.equal(events[0].username, "Sara Khoury");
  assert.equal(events[0].kind, "dm");
  assert.equal(events[0].timestamp, 1760000000000);
  assert.equal(events[1].kind, "postback");
  assert.equal(events[1].payload, "MORE_INFO");
  assert.ok(isWhatsAppId(events[0].senderId));
  assert.deepEqual(parseWhatsAppEntry(entry, "9999"), [], "another number's events are not ours");
});

test("payloads: plain text, one link button, several links, reply buttons", () => {
  const to = "96170111222";
  assert.equal(whatsappPayload({ type: "dm", recipientId: "wa:" + to, text: "Hello" }, to).type, "text");
  const one = whatsappPayload({ type: "dm", recipientId: "wa:" + to, text: "Book here", buttons: [{ label: "Book", url: "https://acme.test/book" }] }, to) as { type: string; interactive: { type: string; action: { parameters: { url: string } } } };
  assert.equal(one.type, "interactive");
  assert.equal(one.interactive.type, "cta_url");
  assert.equal(one.interactive.action.parameters.url, "https://acme.test/book");
  const many = whatsappPayload({ type: "dm", recipientId: "wa:" + to, text: "Links", buttons: [{ label: "A", url: "https://a.test" }, { label: "B", url: "https://b.test" }] }, to) as { type: string; text: { body: string } };
  assert.equal(many.type, "text");
  assert.match(many.text.body, /A: https:\/\/a\.test\nB: https:\/\/b\.test/);
  const quick = whatsappPayload({ type: "dm", recipientId: "wa:" + to, text: "Pick", quickReplies: ["Yes", "No", "Maybe", "Later"] }, to) as { interactive: { action: { buttons: unknown[] } } };
  assert.equal(quick.interactive.action.buttons.length, 3);
});

test("sending uses the brand's phone number id and token; API errors surface", async () => {
  let seen: { url: string; auth: string; body: Record<string, unknown> } | undefined;
  const ok = (async (url: string, init: RequestInit) => {
    seen = { url, auth: String((init.headers as Record<string, string>).Authorization), body: JSON.parse(String(init.body)) };
    return { ok: true, status: 200, json: async () => ({ messages: [{ id: "wamid.OUT" }] }) };
  }) as unknown as typeof fetch;
  const id = await sendWhatsApp({ phoneNumberId: "3003", token: "tok" }, { type: "dm", recipientId: "wa:96170111222", text: "Hi" }, ok);
  assert.equal(id, "wamid.OUT");
  assert.match(seen!.url, /\/3003\/messages$/);
  assert.equal(seen!.auth, "Bearer tok");
  assert.equal(seen!.body.to, "96170111222");
  assert.equal(await sendWhatsApp({ phoneNumberId: "3003", token: "tok" }, { type: "comment_reply", commentId: "c", text: "x" }, ok), "", "comment steps do not exist on WhatsApp");
  const bad = (async () => ({ ok: false, status: 401, json: async () => ({ error: { code: 190, message: "Invalid OAuth access token" } }) })) as unknown as typeof fetch;
  await assert.rejects(() => sendWhatsApp({ phoneNumberId: "3003", token: "x" }, { type: "dm", recipientId: "wa:1", text: "Hi" }, bad), (e: unknown) => e instanceof WaError && e.code === 190);
});

test("a rule limited to one platform answers only there", () => {
  const rule = (platform?: Rule["platform"]): Rule => ({ id: "r", name: "r", enabled: true, trigger: "dm_keyword", keywords: ["price"], match: "word", commentReplies: [], dm: "Prices are in the app", quickReplies: [], cooldownHours: 0, tags: [], priority: 0, ...(platform ? { platform } : {}) });
  const ev = (channel?: "whatsapp") => ({ id: "e", kind: "dm" as const, senderId: "u", text: "price?", timestamp: 1, ...(channel ? { channel } : {}) });
  const run = (r: Rule, channel?: "whatsapp") => decide(ev(channel), [r], {}, DEFAULT_SETTINGS, Date.now()).kind;
  assert.equal(run(rule()), "replied");
  assert.equal(run(rule("all"), "whatsapp"), "replied");
  assert.equal(run(rule("instagram"), "whatsapp"), "ignored");
  assert.equal(run(rule("whatsapp"), "whatsapp"), "replied");
  assert.equal(run(rule("whatsapp")), "ignored");
});

test("the Rules tab reads the optional platform column", () => {
  const row = (platform: string) => ["r1", "TRUE", "n", "dm_keyword", "price", "word", "", "", "", "hi", "", "", "", "24", "", "0", platform];
  const { rules } = parseRulesSheet([[...RULE_COLUMNS], row("WhatsApp"), row("").map((c, i) => (i === 0 ? "r2" : c)), row("telegram").map((c, i) => (i === 0 ? "r3" : c))]);
  assert.equal(rules[0].platform, "whatsapp");
  assert.equal(rules[1].platform, undefined);
  assert.equal(rules[2].platform, undefined, "an unknown platform means all");
});
