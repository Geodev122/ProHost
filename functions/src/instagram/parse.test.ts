import { test } from "node:test";
import assert from "node:assert/strict";
import { parseWebhook } from "./parse";
import { sign, verifySignature } from "./signature";

const SELF = "17841400000000000";

test("comment change becomes a comment event; our own comments are ignored", () => {
  const body = { object: "instagram", entry: [{ id: SELF, time: 1760000000, changes: [
    { field: "comments", value: { id: "c1", text: "link please", from: { id: "u1", username: "rana" }, media: { id: "m1" } } },
    { field: "comments", value: { id: "c2", text: "thanks", from: { id: SELF, username: "prohost.app" }, media: { id: "m1" } } },
  ] }] };
  const ev = parseWebhook(body);
  assert.equal(ev.length, 1);
  assert.deepEqual([ev[0].kind, ev[0].senderId, ev[0].mediaId, ev[0].commentId, ev[0].username], ["comment", "u1", "m1", "c1", "rana"]);
});

test("dm, story reply, story mention and quick reply", () => {
  const msg = (message: unknown) => ({ entry: [{ id: SELF, time: 1, messaging: [{ sender: { id: "u1" }, recipient: { id: SELF }, timestamp: 1760000000000, message }] }] });
  assert.equal(parseWebhook(msg({ mid: "a", text: "hi" }))[0].kind, "dm");
  assert.equal(parseWebhook(msg({ mid: "b", text: "🔥", reply_to: { story: { id: "s1", url: "x" } } }))[0].kind, "story_reply");
  assert.equal(parseWebhook(msg({ mid: "c", attachments: [{ type: "story_mention", payload: { url: "x" } }] }))[0].kind, "story_mention");
  assert.equal(parseWebhook(msg({ mid: "d", text: "Host", quick_reply: { payload: "HOST" } }))[0].payload, "HOST");
});

test("echoes, read receipts and junk are ignored", () => {
  const msg = (m: Record<string, unknown>) => ({ entry: [{ id: SELF, messaging: [{ sender: { id: "u1" }, timestamp: 1, ...m }] }] });
  assert.equal(parseWebhook(msg({ message: { mid: "a", text: "x", is_echo: true } })).length, 0);
  assert.equal(parseWebhook(msg({ read: { mid: "a" } })).length, 0);
  assert.equal(parseWebhook(null).length, 0);
  assert.equal(parseWebhook({ entry: "nope" }).length, 0);
});

test("signature verifies the raw body and rejects tampering or missing parts", () => {
  const raw = Buffer.from('{"a":1}');
  const header = sign(raw, "secret");
  assert.ok(verifySignature(raw, header, "secret"));
  assert.ok(!verifySignature(Buffer.from('{"a":2}'), header, "secret"));
  assert.ok(!verifySignature(raw, header, "other"));
  assert.ok(!verifySignature(raw, undefined, "secret"));
  assert.ok(!verifySignature(raw, header, undefined));
  assert.ok(!verifySignature(raw, "sha256=short", "secret"));
});
