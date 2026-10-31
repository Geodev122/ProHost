import { createHash } from "node:crypto";
import { DocumentReference } from "firebase-admin/firestore";
import { sheets_v4 } from "googleapis";
import { loadConfig, clearConfigCache } from "./config";
import { PROHOST_PROFILE, blankProfile } from "./brands";
import { subscribeApp, whoAmI } from "./graph";
import { PLATFORM_COLUMNS, SECRET_KEYS, SecretEntry, maskedTail, parseBrands, parsePlatforms, parseSecrets } from "./registryTabs";
import { isoTime } from "./rulesSheet";
import { ensureTabs, q, readTab } from "./sheetTabs";
import { LEGACY_BRAND, brandConfigRef, brandCol, brandRef, brandSecretsRef, isLegacyBrand } from "./store";

const sha = (v: unknown) => createHash("sha256").update(JSON.stringify(v)).digest("hex");
const WA_GRAPH = "https://graph.facebook.com/v23.0";
const SIXTY_DAYS = 60 * 86_400_000;

export interface SecretResult { ok: boolean; message: string; patch?: { igUserId?: string; username?: string; tokenExpiresAt?: number } }
export interface RegistryDeps {
  whoAmI: typeof whoAmI;
  subscribeApp: typeof subscribeApp;
  fetch: typeof fetch;
}
const DEFAULT_DEPS: RegistryDeps = { whoAmI, subscribeApp, fetch };

async function putFields(ref: DocumentReference, fields: Record<string, unknown>): Promise<void> {
  try { await ref.update(fields); } catch { await ref.set(fields, { merge: true }); }
}

/** Checks a pasted value where Meta can answer (Instagram token → whose account; WhatsApp token → the phone number), otherwise only its shape. */
export async function checkSecret(e: SecretEntry, accountId: string, deps: RegistryDeps = DEFAULT_DEPS): Promise<SecretResult> {
  if (e.platform === "instagram" && e.key === "access_token") {
    try {
      const me = await deps.whoAmI(e.value);
      if (!me.id) return { ok: false, message: "Meta did not return an account for this token" };
      if (accountId && accountId !== me.id) return { ok: false, message: `token belongs to account ${me.id} (@${me.username}), not ${accountId}` };
      let sub = "";
      try { sub = (await deps.subscribeApp({ igUserId: me.id, token: e.value })) ? " · webhook subscribed" : " · webhook subscription not confirmed"; } catch { sub = " · webhook subscription failed (check the callback URL in the Meta app)"; }
      return { ok: true, message: `valid, @${me.username}${sub}`, patch: { igUserId: me.id, username: me.username, tokenExpiresAt: Date.now() + SIXTY_DAYS } };
    } catch (err) { return { ok: false, message: `Meta rejected the token: ${(err as Error).message.slice(0, 120)}` }; }
  }
  if (e.platform === "whatsapp" && e.key === "access_token") {
    if (!accountId) return { ok: false, message: "fill phone_number_id on the Platforms tab first, then paste the token" };
    try {
      const r = await deps.fetch(`${WA_GRAPH}/${encodeURIComponent(accountId)}?fields=verified_name,display_phone_number`, { headers: { Authorization: `Bearer ${e.value}` } });
      const j = (await r.json().catch(() => ({}))) as { error?: { message?: string }; verified_name?: string; display_phone_number?: string };
      if (!r.ok || j.error) return { ok: false, message: `Meta rejected the token: ${j.error?.message?.slice(0, 120) ?? `HTTP ${r.status}`}` };
      return { ok: true, message: `valid, ${j.verified_name ?? ""} ${j.display_phone_number ?? ""}`.trim() };
    } catch (err) { return { ok: false, message: `could not reach Meta: ${(err as Error).message.slice(0, 80)}` }; }
  }
  return { ok: true, message: "stored" };
}

/** Where a secret goes. ProHost's Instagram secrets stay in the original flat document; everything else in the brand's secrets document. */
async function storeSecret(e: SecretEntry, result: SecretResult): Promise<void> {
  const legacyIg = isLegacyBrand(e.brandId) && e.platform === "instagram";
  const legacyAi = isLegacyBrand(e.brandId) && e.platform === "ai";
  const camel = (k: string) => k.replace(/_([a-z])/g, (_m, c: string) => c.toUpperCase());
  if (legacyIg || legacyAi) {
    const field = legacyAi ? camel(e.key) : camel(e.key); // access_token → accessToken, app_secret → appSecret, anthropic_key → anthropicKey
    await putFields(brandConfigRef(e.brandId), { [field]: e.value, ...(legacyIg && result.patch ? result.patch : {}) });
    return;
  }
  const nested = e.platform === "ai" ? { [camel(e.key)]: e.value } : { [e.platform]: { [camel(e.key)]: e.value } };
  await brandSecretsRef(e.brandId).set(nested, { merge: true });
  if (e.platform === "instagram" && result.patch) await brandRef(e.brandId).set({ platforms: { instagram: result.patch } }, { merge: true });
}

/**
 * Brands, Platforms and Secrets tabs → the registry. Secrets are validated, stored server-side and then BLANKED in the sheet
 * (only a "set …1234" status stays), so a token pasted in the console does not linger in the spreadsheet.
 */
export async function syncRegistry(sheets: sheets_v4.Sheets, sid: string, now: number, deps: RegistryDeps = DEFAULT_DEPS): Promise<string[]> {
  const notes: string[] = [];
  const created = await ensureTabs(sheets, sid, { registry: true, prefix: "", profile: PROHOST_PROFILE });
  const regStats = brandCol(LEGACY_BRAND, "stats").doc("registry");
  const [brandsV, platformsV, secretsV] = await Promise.all([readTab(sheets, sid, "Brands!A1:E60"), readTab(sheets, sid, "Platforms!A1:J200"), readTab(sheets, sid, "Secrets!A1:E300")]);

  // First run: show ProHost as brand #1 so the console has something real to edit.
  if (brandsV.length <= 1) {
    const cfg = await loadConfig(LEGACY_BRAND);
    await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: "Brands!A2", valueInputOption: "RAW", requestBody: { values: [["prohost", "ProHost", "TRUE", "", "the original account"]] } });
    await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: "Platforms!A2", valueInputOption: "RAW", requestBody: { values: [
      ["prohost", "instagram", cfg.igUserId, cfg.appId, "", "", "", "", "TRUE", ""],
      ["prohost", "whatsapp", "", "", cfg.whatsapp.wabaId, cfg.whatsapp.phoneNumberId, cfg.whatsapp.templateName, cfg.whatsapp.templateLang, "FALSE", ""],
    ] } });
    notes.push("registry seeded");
    return notes;
  }

  // Brands tab → registry documents (+ their tabs).
  const { brands, errors: brandErrors } = parseBrands(brandsV);
  const { platforms, errors: platformErrors } = parsePlatforms(platformsV);
  const regHash = sha([brands, platforms]);
  const known = new Set(brands.map((b) => b.id));
  if (created.length || (await regStats.get()).data()?.hash !== regHash) {
    for (const b of brands) {
      const ref = brandRef(b.id);
      const exists = (await ref.get()).exists;
      await putFields(ref, { name: b.name, enabled: b.enabled, ...(b.id === LEGACY_BRAND ? {} : { tabPrefix: `${b.id}_` }), ...(b.sheetId ? { sheetId: b.sheetId } : {}), ...(exists || b.id === LEGACY_BRAND ? {} : { mode: "dry-run", killSwitch: false, createdAt: now }) });
      if (b.id !== LEGACY_BRAND) await ensureTabs(sheets, b.sheetId || sid, { prefix: `${b.id}_`, profile: blankProfile(b.id, b.name), brandId: b.id });
    }
    for (const p of platforms) {
      if (!known.has(p.brandId)) continue;
      const fields = p.platform === "instagram"
        ? { ...(p.accountId && p.brandId !== LEGACY_BRAND ? { igUserId: p.accountId } : {}), ...(p.appId ? { appId: p.appId } : {}) }
        : { phoneNumberId: p.phoneNumberId || p.accountId, wabaId: p.wabaId, appId: p.appId, templateName: p.templateName, templateLang: p.templateLang, enabled: p.enabled };
      if (p.brandId === LEGACY_BRAND && p.platform === "instagram") {
        if (p.appId) await putFields(brandConfigRef(LEGACY_BRAND), { appId: p.appId });
      } else {
        await brandRef(p.brandId).set({ platforms: { [p.platform]: fields } }, { merge: true });
      }
    }
    await regStats.set({ hash: regHash, at: now, errors: [...brandErrors, ...platformErrors].join(" • ").slice(0, 500) });
    clearConfigCache();
    notes.push(`registry: ${brands.length} brand(s), ${platforms.length} platform(s)`);
  }

  // Secrets tab → server-side storage, then blank the cell.
  const { entries, errors } = parseSecrets(secretsV);
  const updates: sheets_v4.Schema$ValueRange[] = [];
  const status = (row: number, text: string) => updates.push({ range: `${q("Secrets")}!D${row}:E${row}`, values: [["", text]] });
  for (const err of errors) status(err.row, `REJECTED: ${err.message} · ${isoTime(now)}`);
  for (const e of entries) {
    if (!known.has(e.brandId) && e.brandId !== LEGACY_BRAND) { status(e.row, `REJECTED: brand "${e.brandId}" is not on the Brands tab · ${isoTime(now)}`); continue; }
    const accountId = platforms.find((p) => p.brandId === e.brandId && p.platform === e.platform);
    const result = await checkSecret(e, e.platform === "whatsapp" ? (accountId?.phoneNumberId || accountId?.accountId || "") : accountId?.accountId ?? "", deps);
    if (result.ok) { await storeSecret(e, result); clearConfigCache(e.brandId); }
    status(e.row, result.ok ? `set ${maskedTail(e.value)} · ${result.message} · ${isoTime(now)}` : `REJECTED: ${result.message} · ${isoTime(now)}`);
    notes.push(`secret ${e.brandId}/${e.platform}/${e.key} ${result.ok ? "stored" : "rejected"}`);
  }
  // Keep a row per brand × platform × key so the console always has the fields to fill (value blank).
  const have = new Set(secretsV.slice(1).map((r) => `${(r[0] ?? "").toLowerCase()}|${(r[1] ?? "").toLowerCase()}|${(r[2] ?? "").toLowerCase()}`));
  const add: string[][] = [];
  for (const b of brands) for (const platform of Object.keys(SECRET_KEYS)) for (const key of SECRET_KEYS[platform]) {
    if (!have.has(`${b.id}|${platform}|${key}`)) add.push([b.id, platform, key, "", ""]);
  }
  if (add.length) await sheets.spreadsheets.values.append({ spreadsheetId: sid, range: "Secrets!A1", valueInputOption: "RAW", insertDataOption: "INSERT_ROWS", requestBody: { values: add } });
  if (updates.length) await sheets.spreadsheets.values.batchUpdate({ spreadsheetId: sid, requestBody: { valueInputOption: "RAW", data: updates } });

  // Platform status column.
  const platformStatus: sheets_v4.Schema$ValueRange[] = [];
  for (const p of platforms) {
    if (!known.has(p.brandId)) continue;
    const cfg = await loadConfig(p.brandId, true).catch(() => null);
    const text = !cfg ? "unknown brand"
      : p.platform === "instagram" ? (cfg.accessToken && cfg.igUserId ? `connected @${cfg.username}` : `missing: ${[!cfg.igUserId && "account", !cfg.accessToken && "access_token", !cfg.appSecret && "app_secret", !cfg.verifyToken && "verify_token"].filter(Boolean).join(", ")}`)
      : (cfg.whatsapp.phoneNumberId && cfg.whatsapp.accessToken ? `connected ${cfg.whatsapp.phoneNumberId}` : `missing: ${[!cfg.whatsapp.phoneNumberId && "phone_number_id", !cfg.whatsapp.accessToken && "access_token", !cfg.whatsapp.appSecret && "app_secret", !cfg.whatsapp.verifyToken && "verify_token"].filter(Boolean).join(", ")}`);
    platformStatus.push({ range: `Platforms!${String.fromCharCode(65 + PLATFORM_COLUMNS.indexOf("status"))}${p.row}`, values: [[text]] });
  }
  if (platformStatus.length) await sheets.spreadsheets.values.batchUpdate({ spreadsheetId: sid, requestBody: { valueInputOption: "RAW", data: platformStatus } });
  return notes;
}
