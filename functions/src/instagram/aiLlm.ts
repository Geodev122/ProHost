import { google } from "googleapis";

/**
 * Provider-agnostic Claude call for the DM agent: Anthropic Messages API (API key) or Claude on Vertex AI
 * (the functions' own service account, billed on the Google Cloud project). Both take the same body shape.
 */
export interface LlmMsg { role: "user" | "assistant"; content: string }
export interface AiConfig {
  provider: "vertex" | "anthropic";
  model: string;
  /** Vertex region ("global", "us-east5", "europe-west1", ...). */
  region: string;
  apiKey?: string;
}
export interface LlmResult { text: string; inputTokens: number; outputTokens: number; cacheReadTokens: number; model: string }

type Fetch = typeof fetch;
const PROJECT = process.env.GCLOUD_PROJECT || "prohost-f766f";

export function vertexUrl(model: string, region: string, project = PROJECT): string {
  const host = region === "global" ? "aiplatform.googleapis.com" : `${region}-aiplatform.googleapis.com`;
  return `https://${host}/v1/projects/${project}/locations/${region}/publishers/anthropic/models/${model}:rawPredict`;
}

/** `knowledge` is the large, stable part of the prompt and is marked cacheable. */
export function buildBody(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens: number): Record<string, unknown> {
  const sys = [{ type: "text", text: system }, ...(knowledge ? [{ type: "text", text: knowledge, cache_control: { type: "ephemeral" } }] : [])];
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

export async function complete(cfg: AiConfig, system: string, knowledge: string, messages: LlmMsg[], maxTokens = 350, f: Fetch = fetch, timeoutMs = 20000): Promise<LlmResult> {
  const body = buildBody(cfg, system, knowledge, messages, maxTokens);
  const url = cfg.provider === "vertex" ? vertexUrl(cfg.model, cfg.region) : "https://api.anthropic.com/v1/messages";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (cfg.provider === "vertex") headers.Authorization = `Bearer ${await vertexToken()}`;
  else { headers["x-api-key"] = cfg.apiKey ?? ""; headers["anthropic-version"] = "2023-06-01"; }
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    const res = await f(url, { method: "POST", headers, body: JSON.stringify(body), signal: ctl.signal });
    const json = (await res.json().catch(() => ({}))) as {
      content?: Array<{ type: string; text?: string }>; usage?: { input_tokens?: number; output_tokens?: number; cache_read_input_tokens?: number };
      error?: { message?: string }; model?: string;
    };
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
