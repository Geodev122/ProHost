import { google } from "googleapis";

/**
 * Provider-agnostic Claude call for the DM agent: Anthropic Messages API (API key) or Claude on Vertex AI
 * (the functions' own service account, billed on the Google Cloud project). Both take the same body shape.
 */
export interface LlmMsg { role: "user" | "assistant"; content: string }
export interface AiConfig {
  provider: "vertex" | "anthropic" | "gemini";
  model: string;
  /** Vertex region ("global", "us-east5", "europe-west1", ...). */
  region: string;
  apiKey?: string;
  /** Anthropic API only: workspace id (wrkspc_…), sent as anthropic-workspace-id when the key is not tied to one workspace. Not a secret. */
  workspaceId?: string;
}
export interface LlmResult { text: string; inputTokens: number; outputTokens: number; cacheReadTokens: number; model: string }

type Fetch = typeof fetch;
const PROJECT = process.env.GCLOUD_PROJECT || "prohost-f766f";

export function vertexUrl(model: string, region: string, project = PROJECT): string {
  const host = region === "global" ? "aiplatform.googleapis.com" : `${region}-aiplatform.googleapis.com`;
  return `https://${host}/v1/projects/${project}/locations/${region}/publishers/anthropic/models/${model}:rawPredict`;
}

/** `knowledge` is the large, stable part of the prompt and is marked cacheable. */
export function buildBody(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens: number, tail = ""): Record<string, unknown> {
  // The stable prompt + knowledge come first (cacheable); the per-person context goes last so it never breaks the cache.
  const sys = [{ type: "text", text: system }, ...(knowledge ? [{ type: "text", text: knowledge, cache_control: { type: "ephemeral" } }] : []), ...(tail ? [{ type: "text", text: tail }] : [])];
  const body: Record<string, unknown> = { max_tokens: maxTokens, system: sys, messages, temperature: 0.3 };
  if (cfg.provider === "vertex") body.anthropic_version = "vertex-2023-10-16"; else body.model = cfg.model;
  return body;
}

async function vertexToken(): Promise<string> {
  const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  const t = await auth.getAccessToken();
  if (!t) throw new Error("no Google access token");
  return t;
}

export class LlmError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

/** Gemini API (Google AI Studio key): generateContent with JSON output, same prompt as the Claude adapters. */
export function buildGeminiBody(system: string, knowledge: string, messages: LlmMsg[], maxTokens: number, tail = ""): Record<string, unknown> {
  return {
    systemInstruction: { parts: [{ text: [system, knowledge, tail].filter(Boolean).join("\n\n") }] },
    contents: messages.map((m) => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content }] })),
    generationConfig: { maxOutputTokens: maxTokens, temperature: 0.3, responseMimeType: "application/json" },
  };
}

export const GEMINI_FALLBACK = "gemini-flash-lite-latest";

async function completeGemini(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens: number, f: Fetch, timeoutMs: number, tail = ""): Promise<LlmResult> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    const res = await f(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(cfg.model)}:generateContent`, {
      method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": cfg.apiKey ?? "" },
      body: JSON.stringify(buildGeminiBody(system, knowledge, messages, maxTokens, tail)), signal: ctl.signal,
    });
    const j = (await res.json().catch(() => ({}))) as {
      candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
      usageMetadata?: { promptTokenCount?: number; candidatesTokenCount?: number; cachedContentTokenCount?: number };
      error?: { message?: string }; modelVersion?: string;
    };
    if (!res.ok) throw new LlmError(res.status, (j.error?.message ?? `HTTP ${res.status}`).slice(0, 300));
    const text = (j.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? "").join("").trim();
    return { text, inputTokens: j.usageMetadata?.promptTokenCount ?? 0, outputTokens: j.usageMetadata?.candidatesTokenCount ?? 0, cacheReadTokens: j.usageMetadata?.cachedContentTokenCount ?? 0, model: j.modelVersion ?? cfg.model };
  } finally {
    clearTimeout(timer);
  }
}

export async function complete(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens = 350, f: Fetch = fetch, timeoutMs = 20000, tail = ""): Promise<LlmResult> {
  if (cfg.provider === "gemini") {
    // Newly released Gemini models are often overloaded (503) or slow: fall back once to the stable lite alias.
    const models = [cfg.model, ...(cfg.model === GEMINI_FALLBACK ? [] : [GEMINI_FALLBACK])];
    let last: unknown;
    for (const model of models) {
      try { return await completeGemini({ ...cfg, model }, system, knowledge, messages, maxTokens, f, Math.min(timeoutMs, 9000), tail); }
      catch (e) {
        last = e;
        const retryable = !(e instanceof LlmError) || e.status === 429 || e.status >= 500;
        if (!retryable) throw e;
      }
    }
    throw last;
  }
  const body = buildBody(cfg, system, knowledge, messages, maxTokens, tail);
  const url = cfg.provider === "vertex" ? vertexUrl(cfg.model, cfg.region) : "https://api.anthropic.com/v1/messages";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (cfg.provider === "vertex") headers.Authorization = `Bearer ${await vertexToken()}`;
  else { headers["x-api-key"] = cfg.apiKey ?? ""; headers["anthropic-version"] = "2023-06-01"; if (cfg.workspaceId) headers["anthropic-workspace-id"] = cfg.workspaceId; }
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    type Reply = {
      content?: Array<{ type: string; text?: string }>; usage?: { input_tokens?: number; output_tokens?: number; cache_read_input_tokens?: number };
      error?: { message?: string }; model?: string;
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
    return { text, inputTokens: json.usage?.input_tokens ?? 0, outputTokens: json.usage?.output_tokens ?? 0, cacheReadTokens: json.usage?.cache_read_input_tokens ?? 0, model: json.model ?? cfg.model };
  } finally {
    clearTimeout(timer);
  }
}

export interface ProbeRow { region: string; model: string; ok: boolean; detail: string }

/** Finds which Claude model/region this project can call on Vertex (used by Maintenance `aiprobe`). */
export async function probeVertex(f: Fetch = fetch): Promise<ProbeRow[]> {
  const models = ["claude-haiku-5-5", "claude-haiku-4-5@20251001", "claude-3-5-haiku@20241022", "claude-3-haiku@20240307"];
  const regions = ["global", "europe-west1", "us-east5"];
  const rows: ProbeRow[] = [];
  for (const model of models) {
    for (const region of regions) {
      try {
        const r = await complete({ provider: "vertex", model, region }, "Reply with the single word OK.", "", [{ role: "user", content: "ping" }], 8, f, 15000);
        rows.push({ region, model, ok: true, detail: `answered "${r.text.slice(0, 20)}" (in ${r.inputTokens}, out ${r.outputTokens})` });
      } catch (e) {
        rows.push({ region, model, ok: false, detail: `${e instanceof LlmError ? e.status : "err"} ${(e as Error).message.slice(0, 380)}` });
      }
    }
  }
  return rows;
}

export interface QuotaRow { quotaId: string; name: string; dimensions: Record<string, string>; value: string; unit: string; raw?: unknown }

const HAIKU = "anthropic-claude-haiku-4-5";
async function cloudToken(): Promise<string> {
  const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });
  return (await auth.getAccessToken()) ?? "";
}
const quotaBase = () => `https://cloudquotas.googleapis.com/v1/projects/${PROJECT}/locations/global`;

/** The GLOBAL Haiku 4.5 quota rows (requests / input tokens / output tokens per minute), with Google's raw limit data. */
export async function listClaudeQuotas(f: Fetch = fetch): Promise<{ rows: QuotaRow[]; note: string }> {
  const token = await cloudToken();
  const rows: QuotaRow[] = [];
  let pageToken = "";
  let scanned = 0;
  for (let page = 0; page < 20; page++) {
    const url = `${quotaBase()}/services/aiplatform.googleapis.com/quotaInfos?pageSize=500${pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : ""}`;
    const res = await f(url, { headers: { Authorization: `Bearer ${token}` } });
    const j = (await res.json().catch(() => ({}))) as { quotaInfos?: Array<Record<string, unknown>>; nextPageToken?: string; error?: { message?: string } };
    if (!res.ok) return { rows, note: `Cloud Quotas API ${res.status}: ${(j.error?.message ?? "").slice(0, 300)}` };
    for (const q of j.quotaInfos ?? []) {
      scanned++;
      if (!/global/i.test(String(q.quotaId))) continue;
      const infos = (q.dimensionsInfos as Array<{ dimensions?: Record<string, string>; details?: { value?: string } }> | undefined) ?? [];
      for (const di of infos) {
        if (di.dimensions?.base_model !== HAIKU) continue;
        rows.push({ quotaId: String(q.quotaId), name: String(q.quotaDisplayName ?? q.metricDisplayName ?? ""), dimensions: di.dimensions ?? {}, value: String(di.details?.value ?? "?"), unit: String(q.unit ?? ""), raw: JSON.stringify(di).slice(0, 400) });
      }
    }
    pageToken = j.nextPageToken ?? "";
    if (!pageToken) break;
  }
  return { rows, note: `scanned ${scanned} quota definitions` };
}

/** Files quota increase requests for the global Haiku 4.5 rows. Returns Google's answer per quota. */
export async function requestClaudeQuota(f: Fetch = fetch): Promise<{ results: string[] }> {
  const { rows, note } = await listClaudeQuotas(f);
  if (!rows.length) return { results: [`no global ${HAIKU} quota rows found (${note})`] };
  const token = await cloudToken();
  const results: string[] = [];
  for (const r of rows) {
    const preferred = /request/i.test(r.quotaId) ? 60 : /input/i.test(r.quotaId) ? 120000 : /output/i.test(r.quotaId) ? 30000 : 0;
    if (!preferred) { results.push(`${r.quotaId}: skipped (not a requests/tokens quota)`); continue; }
    const id = `prohost-${r.quotaId.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-haiku45`.slice(0, 60);
    const res = await f(`${quotaBase()}/quotaPreferences?quotaPreferenceId=${encodeURIComponent(id)}`, {
      method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        service: "aiplatform.googleapis.com", quotaId: r.quotaId, dimensions: r.dimensions, quotaConfig: { preferredValue: String(preferred) },
        contactEmail: "admin@pro-host.tech", justification: "Instagram customer-support assistant for the ProHost app: a few hundred short replies per day.",
      }),
    });
    const j = (await res.json().catch(() => ({}))) as { error?: { message?: string }; quotaConfig?: Record<string, string>; reconciling?: boolean };
    results.push(res.ok ? `${r.quotaId}: requested ${preferred} (${JSON.stringify(j.quotaConfig ?? {})}${j.reconciling ? ", pending review" : ""})` : `${r.quotaId}: ${res.status} ${(j.error?.message ?? "").slice(0, 260)}`);
  }
  return { results };
}

/** Quota requests we filed, with Google's own state and reason (`stateDetail`) — why one was granted or denied. */
export async function listQuotaPreferences(f: Fetch = fetch): Promise<{ lines: string[] }> {
  const token = await cloudToken();
  const res = await f(`${quotaBase()}/quotaPreferences?pageSize=50`, { headers: { Authorization: `Bearer ${token}` } });
  const j = (await res.json().catch(() => ({}))) as { quotaPreferences?: Array<Record<string, unknown>>; error?: { message?: string } };
  if (!res.ok) return { lines: [`Cloud Quotas API ${res.status}: ${(j.error?.message ?? "").slice(0, 300)}`] };
  const lines = (j.quotaPreferences ?? []).map((p) => {
    const qc = (p.quotaConfig ?? {}) as Record<string, unknown>;
    return `${String(p.name ?? "").split("/").pop()} | quota=${String(p.quotaId)} | dims=${JSON.stringify(p.dimensions ?? {})} | preferred=${String(qc.preferredValue)} granted=${String(qc.grantedValue)} | reconciling=${String(p.reconciling)} | stateDetail=${String(qc.stateDetail ?? "")} | annotations=${JSON.stringify(qc.annotations ?? {})} | updated=${String(p.updateTime ?? "")}`;
  });
  return { lines: lines.length ? lines : ["no quota preferences found"] };
}
