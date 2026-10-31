import { test } from "node:test";
import assert from "node:assert/strict";
import { newMessages, toDmEvent } from "./dmPoller";
import { parseConversations, parseMessages } from "./graph";

const self = { id: "178", username: "prohost.app" };
const NOW = 10 * 86_400_000;
const m = (id: string, fromId: string, text: string, created: number, fromUsername = fromId === "178" ? "prohost.app" : "rana") => ({ id, fromId, fromUsername, text, created });

test("new messages: theirs only, after the cursor, inside 24 h, not seen, oldest first", () => {
  const msgs = [m("4", "u1", "price?", NOW - 1_000), m("3", "u1", "hi", NOW - 2_000), m("2", "178", "Hey! Here is how…", NOW - 3_000), m("1", "u1", "old", NOW - 5_000_000)];
  assert.deepEqual(newMessages(msgs, NOW - 10_000, NOW, self, new Set()).map((x) => x.id), ["3", "4"]);
  assert.deepEqual(newMessages(msgs, NOW - 10_000, NOW, self, new Set(["3"])).map((x) => x.id), ["4"]);
  assert.deepEqual(newMessages([m("9", "u1", "x", NOW - 25 * 3_600_000)], 0, NOW, self, new Set()), []);
  assert.deepEqual(newMessages([m("9", "u1", "   ", NOW)], 0, NOW, self, new Set()), [], "likes and stickers have no text");
});

test("a team member who already answered by hand means nothing to do", () => {
  const msgs = [m("6", "178", "Sure, I sent it", NOW - 500), m("5", "u1", "send me the link", NOW - 1_000)];
  assert.deepEqual(newMessages(msgs, 0, NOW, self, new Set()), []);
});

test("several quick messages become one event named after the newest (same id as the webhook's)", () => {
  const ev = toDmEvent([m("3", "u1", "hi", 1), m("4", "u1", "price?", 2)], { id: "c1", updated: 2, participants: [{ id: "u1", username: "rana.k" }, { id: "178", username: "prohost.app" }] });
  assert.deepEqual(ev, { id: "m_4", kind: "dm", senderId: "u1", username: "rana.k", text: "hi\nprice?", timestamp: 2 });
});

test("Conversations API answers are parsed", () => {
  const c = parseConversations({ data: [{ id: "c1", updated_time: "2026-10-24T10:00:00+0000", participants: { data: [{ id: "u1", username: "rana" }] } }, { updated_time: "x" }] });
  assert.equal(c.length, 1); assert.equal(c[0].participants[0].username, "rana"); assert.equal(c[0].updated, Date.parse("2026-10-24T10:00:00+0000"));
  const msgs = parseMessages({ messages: { data: [{ id: "mid1", created_time: "2026-10-24T10:00:00+0000", from: { id: "u1", username: "rana" }, message: "hello" }] } });
  assert.deepEqual(msgs[0], { id: "mid1", fromId: "u1", fromUsername: "rana", text: "hello", created: Date.parse("2026-10-24T10:00:00+0000") });
  assert.deepEqual(parseMessages({}), []);
});
