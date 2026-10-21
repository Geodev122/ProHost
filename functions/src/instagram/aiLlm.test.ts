import { test } from "node:test";
import assert from "node:assert/strict";
import { buildBody, complete, vertexUrl } from "./aiLlm";

test("vertex url for a region and for global", () => {
  assert.equal(vertexUrl("m@1", "europe-west1", "p"), "https://europe-west1-aiplatform.googleapis.com/v1/projects/p/locations/europe-west1/publishers/anthropic/models/m@1:rawPredict");
  assert.equal(vertexUrl("m", "global", "p"), "https://aiplatform.googleapis.com/v1/projects/p/locations/global/publishers/anthropic/models/m:rawPredict");
});

test("body marks the knowledge block cacheable and differs per provider", () => {
  const v = buildBody({ provider: "vertex", model: "m", region: "global" }, "sys", "KB", [{ role: "user", content: "hi" }], 100) as Record<string, unknown>;
  assert.equal(v.anthropic_version, "vertex-2023-10-16"); assert.equal(v.model, undefined);
  assert.deepEqual((v.system as Array<Record<string, unknown>>)[1].cache_control, { type: "ephemeral" });
  const a = buildBody({ provider: "anthropic", model: "claude-x", region: "" }, "sys", "", [{ role: "user", content: "hi" }], 100) as Record<string, unknown>;
  assert.equal(a.model, "claude-x"); assert.equal((a.system as unknown[]).length, 1);
});

test("anthropic call sends the key, parses text and usage; errors carry the status", async () => {
  let seen: { url: string; headers: Record<string, string> } | undefined;
  const ok = (async (url: string, init: RequestInit) => { seen = { url, headers: init.headers as Record<string, string> }; return { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text: " hello " }], usage: { input_tokens: 10, output_tokens: 2, cache_read_input_tokens: 8 }, model: "m" }) } as Response; }) as unknown as typeof fetch;
  const r = await complete({ provider: "anthropic", model: "m", region: "", apiKey: "K" }, "s", "", [{ role: "user", content: "x" }], 50, ok);
  assert.deepEqual([r.text, r.inputTokens, r.outputTokens, r.cacheReadTokens], ["hello", 10, 2, 8]);
  assert.equal(seen?.url, "https://api.anthropic.com/v1/messages"); assert.equal(seen?.headers["x-api-key"], "K");
  const bad = (async () => ({ ok: false, status: 403, json: async () => ({ error: { message: "denied" } }) }) as Response) as unknown as typeof fetch;
  await assert.rejects(() => complete({ provider: "anthropic", model: "m", region: "", apiKey: "K" }, "s", "", [{ role: "user", content: "x" }], 50, bad), (e: { status: number }) => e.status === 403);
});
