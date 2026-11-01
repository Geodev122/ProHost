/**
 * Claude call for the DM agent: the Anthropic Messages API with the brand's API key (no Google Cloud involved).
 */
export interface LlmMsg { role: "user" | "assistant"; content: string }
export interface AiConfig {
  provider: "anthropic";
  model: string;
  apiKey?: string;
  /** Anthropic API only: workspace id (wrkspc_…), sent as anthropic-workspace-id when the key is not tied to one workspace. Not a secret. */
  workspaceId?: string;
}
export interface LlmResult { stopReason?: string; text: string; inputTokens: number; outputTokens: number; cacheReadTokens: number; model: string }

type Fetch = typeof fetch;

/** `knowledge` is the large, stable part of the prompt and is marked cacheable. */
export function buildBody(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens: number, tail = ""): Record<string, unknown> {
  // The stable prompt + knowledge come first (cacheable); the per-person context goes last so it never breaks the cache.
  const sys = [{ type: "text", text: system }, ...(knowledge ? [{ type: "text", text: knowledge, cache_control: { type: "ephemeral" } }] : []), ...(tail ? [{ type: "text", text: tail }] : [])];
  const body: Record<string, unknown> = { max_tokens: maxTokens, system: sys, messages, temperature: 0.3 };
  body.model = cfg.model;
  return body;
}

export class LlmError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function complete(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens = 350, f: Fetch = fetch, timeoutMs = 20000, tail = ""): Promise<LlmResult> {
  const body = buildBody(cfg, system, knowledge, messages, maxTokens, tail);
  const url = "https://api.anthropic.com/v1/messages";
  const headers: Record<string, string> = { "Content-Type": "application/json", "x-api-key": cfg.apiKey ?? "", "anthropic-version": "2023-06-01" };
  if (cfg.workspaceId) headers["anthropic-workspace-id"] = cfg.workspaceId;
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    type Reply = {
      content?: Array<{ type: string; text?: string }>; usage?: { input_tokens?: number; output_tokens?: number; cache_read_input_tokens?: number };
      error?: { message?: string }; model?: string; stop_reason?: string;
    };
    let res = await f(url, { method: "POST", headers, body: JSON.stringify(body), signal: ctl.signal });
    let json = (await res.json().catch(() => ({}))) as Reply;
    // Newer Claude models refuse a `temperature`: send the request again without it (same answer shape).
    if (res.status === 400 && /temperature/i.test(json.error?.message ?? "") && body.temperature !== undefined) {
      delete body.temperature;
      res = await f(url, { method: "POST", headers, body: JSON.stringify(body), signal: ctl.signal });
      json = (await res.json().catch(() => ({}))) as Reply;
    }
    if (!res.ok) throw new LlmError(res.status, (json.error?.message ?? `HTTP ${res.status}`).slice(0, 300));
    const text = (json.content ?? []).filter((c) => c.type === "text").map((c) => c.text ?? "").join("").trim();
    return { text, inputTokens: json.usage?.input_tokens ?? 0, outputTokens: json.usage?.output_tokens ?? 0, cacheReadTokens: json.usage?.cache_read_input_tokens ?? 0, model: json.model ?? cfg.model, stopReason: json.stop_reason };
  } finally {
    clearTimeout(timer);
  }
}
