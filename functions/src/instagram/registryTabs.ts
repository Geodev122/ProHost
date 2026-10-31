import { BRAND_ID, BrandProfile, Playbook } from "./brands";

/**
 * The console's sheet tabs that describe brands (parsed here, applied by sheetsBridge.ts / registrySync.ts).
 *   Registry tabs (one set per spreadsheet): Brands, Platforms, Secrets
 *   Per-brand tabs (name = brand prefix + base name): Profile (key/value), Buttons, Playbooks, Sources
 */
export const BRAND_COLUMNS = ["brand_id", "name", "enabled", "sheet_id", "note"] as const;
export const PLATFORM_COLUMNS = ["brand_id", "platform", "account_id", "app_id", "waba_id", "phone_number_id", "template_name", "template_lang", "enabled", "status"] as const;
export const SECRET_COLUMNS = ["brand_id", "platform", "key", "value", "status"] as const;
export const BUTTON_COLUMNS = ["id", "url", "label_en", "label_ar", "label_fr", "label_es"] as const;
export const PLAYBOOK_COLUMNS = ["name", "enabled", "text"] as const;
export const SOURCE_COLUMNS = ["type", "url_or_text", "note", "status"] as const;

export const SECRET_KEYS: Record<string, readonly string[]> = {
  instagram: ["access_token", "app_secret", "verify_token"],
  whatsapp: ["access_token", "app_secret", "verify_token"],
  ai: ["anthropic_key", "gemini_key"],
};
export const PLATFORMS = ["instagram", "whatsapp"] as const;

const truthy = (v: string) => /^(true|yes|y|1|on|✓|✔)$/i.test(v.trim());
const falsy = (v: string) => /^(false|no|n|0|off)$/i.test(v.trim());
const header = (values: string[][]) => (values[0] ?? []).map((h) => String(h).trim().toLowerCase());
const cell = (row: string[], h: string[], name: string) => String(row[h.indexOf(name)] ?? "").trim();
const blank = (row: string[]) => row.every((c) => !String(c ?? "").trim());

export interface BrandRow { id: string; name: string; enabled: boolean; sheetId: string }
export function parseBrands(values: string[][]): { brands: BrandRow[]; errors: string[] } {
  const h = header(values);
  const brands: BrandRow[] = [];
  const errors: string[] = [];
  const seen = new Set<string>();
  values.slice(1).forEach((row, i) => {
    if (blank(row)) return;
    const id = cell(row, h, "brand_id").toLowerCase();
    if (!BRAND_ID.test(id)) { errors.push(`Brands row ${i + 2}: brand_id must be 2-20 lowercase letters/digits starting with a letter ("${id}")`); return; }
    if (seen.has(id)) { errors.push(`Brands row ${i + 2}: duplicate brand_id "${id}"`); return; }
    seen.add(id);
    brands.push({ id, name: cell(row, h, "name") || id, enabled: !falsy(cell(row, h, "enabled")), sheetId: cell(row, h, "sheet_id") });
  });
  return { brands, errors };
}

export interface PlatformRow { brandId: string; platform: "instagram" | "whatsapp"; accountId: string; appId: string; wabaId: string; phoneNumberId: string; templateName: string; templateLang: string; enabled: boolean; row: number }
export function parsePlatforms(values: string[][]): { platforms: PlatformRow[]; errors: string[] } {
  const h = header(values);
  const platforms: PlatformRow[] = [];
  const errors: string[] = [];
  values.slice(1).forEach((row, i) => {
    if (blank(row)) return;
    const brandId = cell(row, h, "brand_id").toLowerCase();
    const platform = cell(row, h, "platform").toLowerCase();
    if (!BRAND_ID.test(brandId)) { errors.push(`Platforms row ${i + 2}: brand_id "${brandId}" is not valid`); return; }
    if (platform !== "instagram" && platform !== "whatsapp") { errors.push(`Platforms row ${i + 2}: platform must be instagram or whatsapp`); return; }
    const accountId = cell(row, h, "account_id").replace(/\s+/g, "");
    if (accountId && !/^\d{5,25}$/.test(accountId)) { errors.push(`Platforms row ${i + 2}: account_id must be digits only`); return; }
    platforms.push({
      brandId, platform, accountId, appId: cell(row, h, "app_id").replace(/\s+/g, ""), wabaId: cell(row, h, "waba_id").replace(/\s+/g, ""),
      phoneNumberId: cell(row, h, "phone_number_id").replace(/\s+/g, ""), templateName: cell(row, h, "template_name"), templateLang: cell(row, h, "template_lang") || "en",
      enabled: !falsy(cell(row, h, "enabled")), row: i + 2,
    });
  });
  return { platforms, errors };
}

export interface SecretEntry { brandId: string; platform: string; key: string; value: string; row: number }
/** Rows of the Secrets tab that hold a value to take in (rows with a blank value are only status lines). */
export function parseSecrets(values: string[][]): { entries: SecretEntry[]; errors: Array<{ row: number; message: string }> } {
  const h = header(values);
  const entries: SecretEntry[] = [];
  const errors: Array<{ row: number; message: string }> = [];
  values.slice(1).forEach((r, i) => {
    const row = i + 2;
    const value = String(r[h.indexOf("value")] ?? "").trim();
    if (!value) return;
    const brandId = cell(r, h, "brand_id").toLowerCase();
    const platform = cell(r, h, "platform").toLowerCase();
    const key = cell(r, h, "key").toLowerCase();
    if (!BRAND_ID.test(brandId)) { errors.push({ row, message: "unknown brand_id" }); return; }
    if (!SECRET_KEYS[platform]) { errors.push({ row, message: "platform must be instagram, whatsapp or ai" }); return; }
    if (!SECRET_KEYS[platform].includes(key)) { errors.push({ row, message: `key must be one of ${SECRET_KEYS[platform].join(", ")}` }); return; }
    if (value.length < 8 || value.length > 1000) { errors.push({ row, message: "value looks wrong (8-1000 characters)" }); return; }
    entries.push({ brandId, platform, key, value, row });
  });
  return { entries, errors };
}

/** "set …1234" without revealing the value. */
export const maskedTail = (v: string): string => `…${v.slice(-4)}`;

// ---- per-brand profile tabs -------------------------------------------------------------------------------------------

export const PROFILE_KEYS = ["name", "assistant_name", "persona", "contact_email", "languages", "knowledge_base", "integration_offers", "integration_listings", "integration_account_lookup", "integration_release"] as const;

/** Profile tab (key, value) → fields of the registry document. Only keys present in the sheet are returned. */
export function parseProfileTab(values: string[][]): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const kv = new Map<string, string>();
  for (const r of values.slice(1)) {
    const k = String(r[0] ?? "").trim().toLowerCase();
    if (k) kv.set(k, String(r[1] ?? ""));
  }
  const text = (k: string) => kv.get(k)?.trim();
  if (text("name")) out.name = text("name");
  if (text("assistant_name")) out.assistantName = text("assistant_name");
  if (text("purpose")) out.purpose = text("purpose");
  if (kv.has("persona")) out.persona = (kv.get("persona") ?? "").trim();
  if (text("contact_email")) out.contactEmail = text("contact_email");
  if (text("languages")) out.languages = (text("languages") as string).split(/\s*,\s*/).map((l) => l.toLowerCase().slice(0, 5)).filter(Boolean).slice(0, 8);
  if (kv.has("knowledge_base")) out.knowledgeBase = (kv.get("knowledge_base") ?? "").trim();
  const integ: Record<string, boolean> = {};
  for (const [key, field] of [["integration_offers", "offers"], ["integration_listings", "listings"], ["integration_account_lookup", "accountLookup"], ["integration_release", "release"]] as const) {
    const v = kv.get(key);
    if (v !== undefined && v.trim()) integ[field] = truthy(v);
  }
  if (Object.keys(integ).length) out.integrations = integ;
  return out;
}

export function profileRows(p: BrandProfile): string[][] {
  return [
    ["name", p.name], ["assistant_name", p.assistantName], ["purpose", p.purpose], ["persona", p.persona], ["contact_email", p.contactEmail], ["languages", p.languages.join(", ")],
    ["knowledge_base", p.knowledgeBase],
    ["integration_offers", p.integrations.offers ? "TRUE" : "FALSE"], ["integration_listings", p.integrations.listings ? "TRUE" : "FALSE"],
    ["integration_account_lookup", p.integrations.accountLookup ? "TRUE" : "FALSE"], ["integration_release", p.integrations.release ? "TRUE" : "FALSE"],
  ];
}

export function parseButtonsTab(values: string[][]): { buttons: Record<string, { url: string; labels: Record<string, string> }>; errors: string[] } {
  const h = header(values);
  const buttons: Record<string, { url: string; labels: Record<string, string> }> = {};
  const errors: string[] = [];
  values.slice(1).forEach((row, i) => {
    if (blank(row)) return;
    const id = cell(row, h, "id").toLowerCase();
    const url = cell(row, h, "url");
    if (!/^[a-z][a-z0-9_]{0,23}$/.test(id)) { errors.push(`Buttons row ${i + 2}: id must be lowercase letters/digits/underscore`); return; }
    if (!/^https:\/\//i.test(url)) { errors.push(`Buttons row ${i + 2} (${id}): url must start with https://`); return; }
    const labels: Record<string, string> = {};
    h.forEach((name, idx) => { if (name.startsWith("label_") && String(row[idx] ?? "").trim()) labels[name.slice(6)] = String(row[idx]).trim().slice(0, 20); });
    buttons[id] = { url, labels: Object.keys(labels).length ? labels : { en: id } };
  });
  return { buttons, errors };
}

export function buttonRows(b: BrandProfile["buttons"]): string[][] {
  return Object.entries(b).map(([id, v]) => [id, v.url, v.labels.en ?? "", v.labels.ar ?? "", v.labels.fr ?? "", v.labels.es ?? ""]);
}

export function parsePlaybooksTab(values: string[][]): Playbook[] {
  const h = header(values);
  return values.slice(1).filter((r) => !blank(r)).map((r) => ({ name: cell(r, h, "name"), enabled: !falsy(cell(r, h, "enabled")), text: String(r[h.indexOf("text")] ?? "").trim() })).filter((p) => p.name && p.text).slice(0, 12);
}

export function playbookRows(p: Playbook[]): string[][] {
  return p.map((x) => [x.name, x.enabled ? "TRUE" : "FALSE", x.text]);
}
