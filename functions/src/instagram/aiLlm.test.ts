import { test } from "node:test";
import assert from "node:assert/strict";
import { buildBody, buildGeminiBody, complete, vertexUrl } from "./aiLlm";

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

test("gemini call: key header, system prompt, roles mapped, text + usage parsed", async () => {
  let seen: { url: string; headers: Record<string, string>; body: Record<string, unknown> } | undefined;
  const ok = (async (url: string, init: RequestInit) => { seen = { url, headers: init.headers as Record<string, string>, body: JSON.parse(String(init.body)) }; return { ok: true, status: 200, json: async () => ({ candidates: [{ content: { parts: [{ text: " {\"reply\":\"hi\"} " }] } }], usageMetadata: { promptTokenCount: 11, candidatesTokenCount: 3, cachedContentTokenCount: 5 }, modelVersion: "gemini-x" }) } as Response; }) as unknown as typeof fetch;
  const r = await complete({ provider: "gemini", model: "gemini-x", region: "", apiKey: "G" }, "sys", "KB", [{ role: "user", content: "a" }, { role: "assistant", content: "b" }, { role: "user", content: "c" }], 100, ok);
  assert.deepEqual([r.text, r.inputTokens, r.outputTokens, r.cacheReadTokens], ['{"reply":"hi"}', 11, 3, 5]);
  assert.equal(seen?.url, "https://generativelanguage.googleapis.com/v1beta/models/gemini-x:generateContent");
  assert.equal(seen?.headers["x-goog-api-key"], "G");
  assert.deepEqual((seen?.body.contents as Array<{ role: string }>).map((c) => c.role), ["user", "model", "user"]);
  assert.ok(JSON.stringify(buildGeminiBody("s", "K", [], 1)).includes("responseMimeType"));
});

test("gemini falls back to the stable lite alias when the first model is overloaded, but not on a bad key", async () => {
  const urls: string[] = [];
  const busy = (async (url: string) => { urls.push(url); return url.includes("gemini-new") ? { ok: false, status: 503, json: async () => ({ error: { message: "high demand" } }) } as Response : { ok: true, status: 200, json: async () => ({ candidates: [{ content: { parts: [{ text: "{}" }] } }] }) } as Response; }) as unknown as typeof fetch;
  const r = await complete({ provider: "gemini", model: "gemini-new", region: "", apiKey: "G" }, "s", "", [{ role: "user", content: "x" }], 50, busy);
  assert.equal(r.text, "{}"); assert.deepEqual(urls.map((u) => u.split("/models/")[1].split(":")[0]), ["gemini-new", "gemini-flash-lite-latest"]);
  const denied = (async () => ({ ok: false, status: 400, json: async () => ({ error: { message: "API key not valid" } }) }) as Response) as unknown as typeof fetch;
  await assert.rejects(() => complete({ provider: "gemini", model: "gemini-new", region: "", apiKey: "bad" }, "s", "", [{ role: "user", content: "x" }], 50, denied), (e: { status: number }) => e.status === 400);
});

test("the Anthropic API call carries the workspace id header only when one is set", async () => {
  const seen: Array<Record<string, string>> = [];
  const f = (async (_u: string, init: RequestInit) => {
    seen.push(init.headers as Record<string, string>);
    return { ok: true, status: 200, json: async () => ({ content: [{ type: "text", text: "{}" }], usage: { input_tokens: 1, output_tokens: 1 }, model: "m" }) };
  }) as unknown as typeof fetch;
  const base = { provider: "anthropic" as const, model: "claude-x", region: "", apiKey: "k" };
  await complete(base, "sys", "", [{ role: "user", content: "hi" }], 10, f);
  await complete({ ...base, workspaceId: "wrkspc_01ABCDEFGHIJKL" }, "sys", "", [{ role: "user", content: "hi" }], 10, f);
  assert.equal(seen[0]["anthropic-workspace-id"], undefined);
  assert.equal(seen[1]["anthropic-workspace-id"], "wrkspc_01ABCDEFGHIJKL");
});
