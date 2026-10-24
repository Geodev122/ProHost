import { test } from "node:test";
import assert from "node:assert/strict";
import { STORED_TURNS, ago, aliasId, appendTurns, cleanMemory, importHistory, inConversation, seedFromComment } from "./aiMemory";

test("turns are kept for good up to 40, empty ones dropped, texts capped", () => {
  const many = Array.from({ length: 50 }, (_, i) => ({ r: "u" as const, t: `m${i}` }));
  const t = appendTurns(many, [{ r: "a", t: "x".repeat(900) }, { r: "u", t: "  " }]);
  assert.equal(t.length, STORED_TURNS); assert.equal(t[t.length - 1].t.length, 600); assert.equal(t[0].t, "m11");
});

test("memory is one capped paragraph; non-strings are ignored", () => {
  assert.equal(cleanMemory(" a\n\n b  "), "a b");
  assert.equal(cleanMemory("x".repeat(900)).length, 700);
  assert.equal(cleanMemory(42), "");
});

test("Instagram history becomes oldest-first turns, ours as the agent, the message being answered skipped", () => {
  const msgs = [
    { id: "3", fromId: "u1", fromUsername: "rana", text: "and today?", created: 300 },
    { id: "2", fromId: "178", fromUsername: "prohost.app", text: "Hi Rana, here is the app", created: 200 },
    { id: "1", fromId: "u1", fromUsername: "rana", text: "I need a room", created: 100 },
    { id: "0", fromId: "u1", fromUsername: "rana", text: "", created: 50 },
  ];
  const t = importHistory(msgs, "178", "prohost.app", ["3"]);
  assert.deepEqual(t.map((x) => [x.r, x.t]), [["u", "I need a room"], ["a", "Hi Rana, here is the app"]]);
  assert.deepEqual(importHistory(msgs, "", "ProHost.App").map((x) => x.r), ["u", "a", "u"]);
});

test("a live conversation is an agent message within 24 h of a chat that is not closed", () => {
  const now = 100 * 3_600_000;
  assert.equal(inConversation({ lastAgentAt: now - 3_600_000 }, now), true);
  assert.equal(inConversation({ at: now - 23 * 3_600_000 }, now), true);
  assert.equal(inConversation({ lastAgentAt: now - 25 * 3_600_000 }, now), false);
  assert.equal(inConversation({ lastAgentAt: now, closed: true }, now), false);
  assert.equal(inConversation(undefined, now), false);
});

test("a private reply to a comment opens the chat history", () => {
  const t = seedFromComment([{ r: "u", t: "older" }], "price?", "Here is how it works", 5);
  assert.deepEqual(t.map((x) => x.r), ["u", "u", "a"]);
  assert.ok(/public comment/.test(t[1].t) && /price\?/.test(t[1].t));
  assert.equal(aliasId("Rana.K"), "u:rana.k");
});

test("gaps read naturally", () => {
  assert.equal(ago(30_000), "1 minute"); assert.equal(ago(5 * 60_000), "5 minutes");
  assert.equal(ago(3 * 3_600_000), "3 hours"); assert.equal(ago(3 * 86_400_000), "3 days");
});

test("the memory never keeps an email or a phone number", () => {
  const m = cleanMemory("Karim (karim.test@example.com, +961 70 123 456 / 0096170123456) is a psychologist in Beirut, wants 2 rooms in 2026.");
  assert.ok(!m.includes("@") && !m.includes("123 456") && !m.includes("0096170123456"), m);
  assert.ok(m.includes("[email]") && m.includes("[phone]") && m.includes("2 rooms in 2026"), m);
});
