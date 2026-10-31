import { drive_v3 } from "googleapis";
import { AiConfig, complete } from "./aiLlm";
import { BrandProfile } from "./brands";
import { IgConfig } from "./config";

/**
 * "Build knowledge": the Sources tab lists where a brand's business knowledge lives (website pages, public GitHub files, Drive
 * documents / PDFs shared with the service account, pasted text). The brand's AI drafts a structured knowledge document into the
 * KnowledgeDraft tab; the owner reviews it and sets rows to "approved", which promotes them into the live knowledge base.
 * Nothing unreviewed ever reaches the agent.
 */
export type SourceType = "url" | "repo" | "drive" | "text";
export interface SourceRow { type: SourceType; ref: string; note: string; row: number }
export interface Gathered { row: number; label: string; text: string; status: string }

const MAX_PER_SOURCE = 20_000;
const MAX_TOTAL = 70_000;

export function parseSources(values: string[][]): { sources: SourceRow[]; errors: string[] } {
  const h = (values[0] ?? []).map((x) => String(x).trim().toLowerCase());
  const sources: SourceRow[] = [];
  const errors: string[] = [];
  values.slice(1).forEach((r, i) => {
    const row = i + 2;
    const type = String(r[h.indexOf("type")] ?? "").trim().toLowerCase();
    const ref = String(r[h.indexOf("url_or_text")] ?? "").trim();
    if (!type && !ref) return;
    if (!["url", "repo", "drive", "text"].includes(type)) { errors.push(`Sources row ${row}: type must be url, repo, drive or text`); return; }
    if (!ref) { errors.push(`Sources row ${row}: url_or_text is empty`); return; }
    if (type !== "text" && !/^https:\/\//i.test(ref)) { errors.push(`Sources row ${row}: ${type} sources must be https links`); return; }
    sources.push({ type: type as SourceType, ref, note: String(r[h.indexOf("note")] ?? "").trim(), row });
  });
  return { sources, errors };
}

/** Pages are fetched by the server, so private addresses and non-web hosts are refused (no probing of internal services). */
export function safePublicUrl(raw: string): URL | null {
  let u: URL;
  try { u = new URL(raw); } catch { return null; }
  if (u.protocol !== "https:" || u.username || u.password || (u.port && u.port !== "443")) return null;
  const host = u.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".local") || host.endsWith(".internal") || !host.includes(".")) return null;
  if (/^\d+\.\d+\.\d+\.\d+$/.test(host) || host.includes(":") || host.startsWith("[")) return null;
  return u;
}

export function htmlToText(html: string): string {
  return html
    .replace(/<(script|style|noscript|svg|template|nav|footer)[\s\S]*?<\/\1>/gi, " ")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<\/(p|div|section|article|li|h[1-6]|tr|br)>|<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/[ \t\f\v]+/g, " ").replace(/ ?\n ?/g, "\n").replace(/\n{3,}/g, "\n\n").trim();
}

async function fetchText(url: URL, f: typeof fetch, accept = "text/html,text/plain"): Promise<string> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), 15_000);
  try {
    const res = await f(url.toString(), { headers: { Accept: accept, "User-Agent": "ProHostKnowledgeBuilder/1.0" }, signal: ctl.signal, redirect: "follow" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const type = res.headers.get("content-type") ?? "";
    if (!/text\/|json|xml|markdown/i.test(type)) throw new Error(`unsupported content type ${type.split(";")[0] || "unknown"} (put PDFs in Drive)`);
    const body = (await res.text()).slice(0, 1_500_000);
    return /html/i.test(type) ? htmlToText(body) : body;
  } finally { clearTimeout(timer); }
}

/** "https://github.com/owner/repo" or ".../owner/repo#docs/FAQ.md" → README or that file from a PUBLIC repo. */
export function parseRepoRef(ref: string): { owner: string; repo: string; path: string } | null {
  const m = /^https:\/\/github\.com\/([\w.-]+)\/([\w.-]+?)(?:\.git)?(?:\/(?:tree|blob)\/[\w.-]+)?(?:\/?#(.+))?\/?$/.exec(ref.trim());
  return m ? { owner: m[1], repo: m[2], path: (m[3] ?? "").replace(/^\/+/, "") } : null;
}

export function driveFileId(ref: string): string | null {
  const m = /\/d\/([\w-]{20,})|[?&]id=([\w-]{20,})/.exec(ref);
  return m ? (m[1] ?? m[2]) : null;
}

export interface BuilderDeps {
  fetch: typeof fetch;
  /** Google Drive client using the functions' service account (files must be shared with it). */
  drive: () => drive_v3.Drive;
  complete: typeof complete;
}

async function driveText(ref: string, drive: drive_v3.Drive): Promise<string> {
  const id = driveFileId(ref);
  if (!id) throw new Error("could not find the file id in this Drive link");
  const meta = (await drive.files.get({ fileId: id, fields: "id,name,mimeType", supportsAllDrives: true })).data;
  const mime = meta.mimeType ?? "";
  const exp = async (fileId: string, to: string) => String((await drive.files.export({ fileId, mimeType: to }, { responseType: "text" })).data ?? "");
  if (mime === "application/vnd.google-apps.document") return exp(id, "text/plain");
  if (mime === "application/vnd.google-apps.spreadsheet") return exp(id, "text/csv");
  if (mime === "application/vnd.google-apps.presentation") return exp(id, "text/plain");
  if (mime.startsWith("text/")) return String((await drive.files.get({ fileId: id, alt: "media", supportsAllDrives: true }, { responseType: "text" })).data ?? "");
  if (mime === "application/pdf" || mime.includes("wordprocessingml") || mime === "application/msword") {
    // Drive converts PDFs (with OCR) and Word files to a Google Doc; read it, then delete the temporary copy.
    const copy = (await drive.files.copy({ fileId: id, requestBody: { name: `knowledge-build-${Date.now()}`, mimeType: "application/vnd.google-apps.document" }, supportsAllDrives: true })).data;
    if (!copy.id) throw new Error("Drive could not convert this file");
    try { return await exp(copy.id, "text/plain"); } finally { await drive.files.delete({ fileId: copy.id, supportsAllDrives: true }).catch(() => undefined); }
  }
  throw new Error(`unsupported Drive file type ${mime || "unknown"}`);
}

/** Reads every source; one failing source never stops the others. */
export async function gather(sources: SourceRow[], deps: Pick<BuilderDeps, "fetch" | "drive">): Promise<Gathered[]> {
  const out: Gathered[] = [];
  let total = 0;
  for (const s of sources) {
    try {
      let text = "";
      if (s.type === "text") text = s.ref;
      else if (s.type === "url") {
        const u = safePublicUrl(s.ref);
        if (!u) throw new Error("this address is not a public https page");
        text = await fetchText(u, deps.fetch);
      } else if (s.type === "repo") {
        const r = parseRepoRef(s.ref);
        if (!r) throw new Error("expected https://github.com/owner/repo or ...#path/to/file.md");
        const url = r.path
          ? new URL(`https://raw.githubusercontent.com/${r.owner}/${r.repo}/HEAD/${r.path.split("/").map(encodeURIComponent).join("/")}`)
          : new URL(`https://api.github.com/repos/${r.owner}/${r.repo}/readme`);
        text = await fetchText(url, deps.fetch, r.path ? "text/plain" : "application/vnd.github.raw");
      } else text = await driveText(s.ref, deps.drive());
      text = text.trim().slice(0, Math.min(MAX_PER_SOURCE, Math.max(0, MAX_TOTAL - total)));
      if (!text) throw new Error("nothing readable");
      total += text.length;
      out.push({ row: s.row, label: s.note || s.ref.slice(0, 80), text, status: `read ${text.length} characters` });
    } catch (e) {
      out.push({ row: s.row, label: s.note || s.ref.slice(0, 80), text: "", status: `FAILED: ${(e as Error).message.slice(0, 140)}` });
    }
  }
  return out;
}

export interface DraftSection { section: string; text: string; sources: string }

export function draftPrompt(brand: Pick<BrandProfile, "name" | "languages">, items: Gathered[]): { system: string; user: string } {
  return {
    system: `You write the business knowledge base of a customer-facing AI assistant for "${brand.name}". Use ONLY facts stated in the SOURCES. Never invent prices, dates, features, policies, contact details or links; if the sources do not say it, leave it out. Do not copy marketing fluff: keep concrete facts, numbers, steps, policies, contact details and links that appear in the sources. Write in the language of the sources. Organise the answer in 4 to 12 short sections such as ABOUT, PRODUCTS AND SERVICES, HOW IT WORKS, PRICING (only as stated), FAQ, CONTACT, POLICIES, NOT KNOWN. Return ONLY JSON: {"sections":[{"section":"ABOUT","text":"...","sources":"which source labels this came from"}]}`,
    user: items.filter((i) => i.text).map((i, n) => `SOURCE ${n + 1}: ${i.label}\n${i.text}`).join("\n\n=====\n\n"),
  };
}

export function parseDraft(raw: string): DraftSection[] {
  const a = raw.indexOf("{"), b = raw.lastIndexOf("}");
  if (a < 0 || b <= a) return [];
  try {
    const j = JSON.parse(raw.slice(a, b + 1)) as { sections?: Array<Record<string, unknown>> };
    return (Array.isArray(j.sections) ? j.sections : []).map((s) => ({ section: String(s.section ?? "").trim().slice(0, 60), text: String(s.text ?? "").trim().slice(0, 6000), sources: String(s.sources ?? "").trim().slice(0, 200) })).filter((s) => s.section && s.text).slice(0, 14);
  } catch { return []; }
}

/** Approved rows of the KnowledgeDraft tab → the text the agent reads. */
export function composeKnowledge(rows: string[][]): { text: string; applied: number[] } {
  const parts: string[] = [];
  const applied: number[] = [];
  rows.forEach((r, i) => {
    if (String(r[3] ?? "").trim().toLowerCase() !== "approved") return;
    const section = String(r[0] ?? "").trim();
    const text = String(r[1] ?? "").trim();
    if (!section || !text) return;
    parts.push(`${section.toUpperCase()}\n${text}`);
    applied.push(i + 2);
  });
  return { text: parts.join("\n\n"), applied };
}

export function aiConfigFor(config: IgConfig): AiConfig {
  const ai = config.ai;
  return { provider: ai.provider, model: ai.model, region: ai.region, apiKey: ai.provider === "gemini" ? config.geminiKey : ai.provider === "anthropic" ? config.anthropicKey : "" };
}

/** Gathers the sources and asks the brand's AI for a draft. Returns the draft and what happened to each source. */
export async function buildKnowledgeDraft(config: IgConfig, sources: SourceRow[], deps: BuilderDeps): Promise<{ sections: DraftSection[]; gathered: Gathered[]; error: string }> {
  const gathered = await gather(sources, deps);
  if (!gathered.some((g) => g.text)) return { sections: [], gathered, error: "none of the sources could be read" };
  const cfg = aiConfigFor(config);
  if (cfg.provider !== "vertex" && !cfg.apiKey) return { sections: [], gathered, error: `no ${cfg.provider} key: paste it in the Secrets tab` };
  const { system, user } = draftPrompt(config.brand, gathered);
  try {
    const res = await deps.complete(cfg, system, "", [{ role: "user", content: user }], 4000, deps.fetch, 120_000);
    const sections = parseDraft(res.text);
    return { sections, gathered, error: sections.length ? "" : "the model returned no usable sections, try again" };
  } catch (e) {
    return { sections: [], gathered, error: `AI unavailable: ${(e as Error).message.slice(0, 120)}` };
  }
}
