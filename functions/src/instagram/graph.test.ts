import { test } from "node:test";
import assert from "node:assert/strict";
import { GRAPH, GraphError, messagePayload, sendStep, whoAmI } from "./graph";

const creds = { igUserId: "178", token: "T" };
type Call = { url: string; init: RequestInit };
function fakeFetch(responses: Array<{ status: number; body: unknown }>) {
  const calls: Call[] = [];
  const f = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    const r = responses.shift() ?? { status: 200, body: {} };
    return { ok: r.status < 400, status: r.status, json: async () => r.body } as Response;
  }) as unknown as typeof fetch;
  return { f, calls };
}

test("comment reply goes to /{comment}/replies with a bearer token", async () => {
  const { f, calls } = fakeFetch([{ status: 200, body: { id: "r1" } }]);
  assert.equal(await sendStep(creds, { type: "comment_reply", commentId: "c1", text: "Hi" }, f), "r1");
  assert.equal(calls[0].url, `${GRAPH}/c1/replies`);
  assert.equal((calls[0].init.headers as Record<string, string>).Authorization, "Bearer T");
  assert.deepEqual(JSON.parse(String(calls[0].init.body)), { message: "Hi" });
});

test("private reply targets the comment id, DM targets the person", async () => {
  const { f, calls } = fakeFetch([{ status: 200, body: { message_id: "m1" } }, { status: 200, body: { message_id: "m2" } }]);
  await sendStep(creds, { type: "private_reply", commentId: "c1", text: "Hello" }, f);
  await sendStep(creds, { type: "dm", recipientId: "u9", text: "Hello" }, f);
  assert.equal(calls[0].url, `${GRAPH}/178/messages`);
  assert.deepEqual(JSON.parse(String(calls[0].init.body)).recipient, { comment_id: "c1" });
  assert.deepEqual(JSON.parse(String(calls[1].init.body)).recipient, { id: "u9" });
});

test("button and quick-reply payload shapes", () => {
  const b = messagePayload({ type: "dm", recipientId: "u", text: "Get it", buttonLabel: "Open the app with a very long label", buttonUrl: "https://x.y" }) as { attachment: { payload: { buttons: Array<{ title: string; url: string }> } } };
  assert.equal(b.attachment.payload.buttons[0].title.length, 20);
  const q = messagePayload({ type: "dm", recipientId: "u", text: "Who are you?", quickReplies: ["Host", "Professional"] }) as { quick_replies: Array<{ payload: string }> };
  assert.equal(q.quick_replies[1].payload, "PROFESSIONAL");
});

test("non-retryable errors fail at once with Meta's message; 5xx retries", async () => {
  const bad = fakeFetch([{ status: 400, body: { error: { message: "Cannot message this user", code: 100 } } }]);
  await assert.rejects(() => sendStep(creds, { type: "dm", recipientId: "u", text: "x" }, bad.f), (e: GraphError) => e.code === 100 && /Cannot message/.test(e.message));
  assert.equal(bad.calls.length, 1);
  const flaky = fakeFetch([{ status: 500, body: {} }, { status: 200, body: { message_id: "ok" } }]);
  assert.equal(await sendStep(creds, { type: "dm", recipientId: "u", text: "x" }, flaky.f), "ok");
  assert.equal(flaky.calls.length, 2);
});

test("whoAmI reads the account id and username", async () => {
  const { f } = fakeFetch([{ status: 200, body: { user_id: "178", username: "prohost.app" } }]);
  assert.deepEqual(await whoAmI("T", f), { id: "178", username: "prohost.app" });
});
