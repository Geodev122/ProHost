import { test } from "node:test";
import assert from "node:assert/strict";
import { buildBody, complete } from "./aiLlm";

test("body marks the knowledge block cacheable and sets the model", () => {
  const v = buildBody({ provider: "anthropic", model: "m" }, "sys", "KB", [{ role: "user", content: "hi" }], 100) as Record<string, unknown>;
  assert.equal(v.anthropic_version, undefined);
  assert.deepEqual((v.system as Array<Record<string, unknown>>)[1].cache_control, { type: "ephemeral" });
  const a = buildBody({ provider: "anthropic", model: "claude-x" }, "sys", "", [{ role: "user", content: "hi" }], 100) as Record<string, unknown>;
  assert.equal(a.model, "claude-x"); assert.equal((a.system as unknown[]).length, 1);
});

test("anthropic call sends the key, parses text and usage; errors carry the status", async () => {
  let seen: { url: string; headers: Record<string, string> } | undefined;
  const ok = (async (url: string, init: RequestInit) => { seen = { url, headers: init.headers as Record<string, string> }; return { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text: " hello " }], usage: { input_tokens: 10, output_tokens: 2, cache_read_input_tokens: 8 }, model: "m" }) } as Response; }) as unknown as typeof fetch;
  const r = await complete({ provider: "anthropic", model: "m", apiKey: "K" }, "s", "", [{ role: "user", content: "x" }], 50, ok);
  assert.deepEqual([r.text, r.inputTokens, r.outputTokens, r.cacheReadTokens], ["hello", 10, 2, 8]);
  assert.equal(seen?.url, "https://api.anthropic.com/v1/messages"); assert.equal(seen?.headers["x-api-key"], "K");
  const bad = (async () => ({ ok: false, status: 403, json: async () => ({ error: { message: "denied" } }) }) as Response) as unknown as typeof fetch;
  await assert.rejects(() => complete({ provider: "anthropic", model: "m", apiKey: "K" }, "s", "", [{ role: "user", content: "x" }], 50, bad), (e: { status: number }) => e.status === 403);
});

test("the Anthropic API call carries the workspace id header only when one is set", async () => {
  const seen: Array<Record<string, string>> = [];
  const f = (async (_u: string, init: RequestInit) => {
    seen.push(init.headers as Record<string, string>);
    return { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text: "{}" }], usage: { input_tokens: 1, output_tokens: 1 }, model: "m" }) };
  }) as unknown as typeof fetch;
  const base = { provider: "anthropic" as const, model: "claude-x", apiKey: "k" };
  await complete(base, "sys", "", [{ role: "user", content: "hi" }], 10, f);
  await complete({ ...base, workspaceId: "wrkspc_01ABCDEFGHIJKL" }, "sys", "", [{ role: "user", content: "hi" }], 10, f);
  assert.equal(seen[0]["anthropic-workspace-id"], undefined);
  assert.equal(seen[1]["anthropic-workspace-id"], "wrkspc_01ABCDEFGHIJKL");
});

test("a model that rejects temperature is asked again without it", async () => {
  const bodies: Array<Record<string, unknown>> = [];
  const f = (async (_u: string, init: RequestInit) => {
    const b = JSON.parse(String(init.body)) as Record<string, unknown>;
    bodies.push(b);
    return b.temperature !== undefined
      ? { ok: false, status: 400, json: async () => ({ error: { message: "`temperature` is deprecated for this model." } }) }
      : { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text: "ok" }], usage: { input_tokens: 1, output_tokens: 1 }, model: "m" }) };
  }) as unknown as typeof fetch;
  const r = await complete({ provider: "anthropic", model: "claude-haiku-5-5", apiKey: "k" }, "s", "", [{ role: "user", content: "x" }], 10, f);
  assert.equal(r.text, "ok");
  assert.equal(bodies.length, 2);
  assert.equal(bodies[1].temperature, undefined);
});
