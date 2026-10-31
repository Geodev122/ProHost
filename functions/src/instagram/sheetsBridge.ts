import { createHash } from "node:crypto";
import { parseWhatsAppConfig, whatsAppMissing } from "../lib/whatsappAlerts";
import { DocumentReference, getFirestore } from "firebase-admin/firestore";
import { google, sheets_v4 } from "googleapis";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { IgConfig, clearConfigCache, loadAllConfigs, loadConfig } from "./config";
import { LEAD_COLUMNS, isoTime, leadRow, logRow, aiFromStatus, parseRulesSheet, settingsFromStatus } from "./rulesSheet";
import { parseButtonsTab, parsePlaybooksTab, parseProfileTab } from "./registryTabs";
import { BuilderDeps, buildKnowledgeDraft, composeKnowledge, parseSources } from "./knowledgeBuilder";
import { complete } from "./aiLlm";
import { syncRegistry } from "./registrySync";
import { range, readTab, ensureTabs, q } from "./sheetTabs";
import { brandCol, brandConfigRef, brandKnowledgeRef, brandRef, LEGACY_BRAND } from "./store";
import { daysLeft } from "./tokenRefresh";

/**
 * The artifact can't call Cloud Functions, so it edits a Google Sheet ("ProHost IG Automations") through the
 * Sheets connector and this job mirrors it every minute. One spreadsheet holds every brand: ProHost keeps the original
 * tab names, every other brand's tabs carry its prefix ("acme_Rules").
 *   Brands / Platforms / Secrets (registry)  new brands and platform accounts; secrets are taken in and blanked (registrySync.ts)
 *   Rules    (artifact → backend)  validated rows replace the brand's rules; errors are written to Status
 *   Status   (both)                artifact writes mode + kill_switch + settings, backend writes health lines
 *   Profile / Buttons / Playbooks  (artifact → backend)  who the brand is, its links and its agent modes
 *   Knowledge (artifact → agent) / Learn, Leads, Log, Contacts (backend → artifact)
 * The sheet must be shared (Editor) with the functions' service account.
 */
const LOG_KEEP = 2000;
const CONTACTS_KEEP = 500;

async function sheetsClient(): Promise<sheets_v4.Sheets> {
  const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/spreadsheets"] });
  return google.sheets({ version: "v4", auth });
}

export { TABS, ensureTabs } from "./sheetTabs";

/** Replaces the given fields of a document (arrays and maps are replaced whole); creates the document if it does not exist yet. */
async function putFields(ref: DocumentReference, fields: Record<string, unknown>): Promise<void> {
  try { await ref.update(fields); } catch { await ref.set(fields, { merge: true }); }
}

const sha = (v: unknown) => createHash("sha256").update(JSON.stringify(v)).digest("hex");

/** One brand's pass. Returns short notes of what changed. */
export async function syncBrand(sheets: sheets_v4.Sheets, sid: string, config: IgConfig, now: number): Promise<string[]> {
  const bid = config.brandId;
  const prefix = config.brand.tabPrefix;
  const T = (base: string, a1: string) => range(prefix, base, a1);
  const stats = brandCol(bid, "stats");
  const notes: string[] = [];

  // 1. Status (artifact → backend): mode + kill switch + settings.
  const status = new Map(await readTab(sheets, sid, T("Status", "A2:B100")).then((rows) => rows.filter((r) => (r[0] ?? "").trim()).map((r) => [r[0].trim().toLowerCase(), (r[1] ?? "").trim()] as [string, string])));
  const wantMode = status.get("mode")?.toLowerCase() === "live" ? "live" : "dry-run";
  const wantKill = /^(true|yes|1|on)$/i.test(status.get("kill_switch") ?? "");
  const wantSettings = settingsFromStatus(status);
  const wantAi = aiFromStatus(status);
  const settingsHash = sha([wantSettings, wantAi]);
  const settingsRef = stats.doc("settings");
  const settingsChanged = (await settingsRef.get()).data()?.hash !== settingsHash;
  if (wantMode !== config.mode || wantKill !== config.killSwitch || settingsChanged) {
    // settings is replaced as a whole (not merged) so removing a value in the sheet restores the default.
    await putFields(brandConfigRef(bid), { mode: wantMode, killSwitch: wantKill, settings: wantSettings, ai: wantAi });
    await settingsRef.set({ hash: settingsHash, at: now });
    clearConfigCache(bid);
    notes.push(`mode=${wantMode} kill=${wantKill}${settingsChanged ? " settings" : ""}`);
  }

  // 1b. Profile, Buttons, Playbooks (artifact → backend): who the brand is. Only fields present in the sheet overlay the defaults.
  try {
    const [profileV, buttonsV, playbooksV] = await Promise.all([
      readTab(sheets, sid, T("Profile", "A1:B60")), readTab(sheets, sid, T("Buttons", "A1:K40")), readTab(sheets, sid, T("Playbooks", "A1:C40")),
    ]);
    const fields: Record<string, unknown> = parseProfileTab(profileV);
    const { buttons, errors: bErrors } = parseButtonsTab(buttonsV);
    if (Object.keys(buttons).length) fields.buttons = buttons;
    const playbooks = parsePlaybooksTab(playbooksV);
    if (playbooks.length) fields.playbooks = playbooks;
    const pHash = sha(fields);
    const pRef = stats.doc("profile");
    if (Object.keys(fields).length && (await pRef.get()).data()?.hash !== pHash) {
      await putFields(brandRef(bid), fields);
      await pRef.set({ hash: pHash, at: now, errors: bErrors.join(" • ").slice(0, 500) });
      clearConfigCache(bid);
      notes.push("profile");
    }
  } catch (e) {
    notes.push(`profile tabs unavailable (${(e as Error).message.slice(0, 60)})`);
  }

  // 1c. Knowledge builder: "Build knowledge" (Status build_knowledge = request) reads the Sources tab and writes a draft;
  // rows the owner sets to "approved" in KnowledgeDraft become the brand's knowledge base.
  try {
    notes.push(...(await knowledgeStep(sheets, sid, config, status, now)));
  } catch (e) {
    notes.push(`knowledge builder failed (${(e as Error).message.slice(0, 80)})`);
  }

  // 2. Rules (artifact → backend), only when the sheet content changed.
  const ruleValues = await readTab(sheets, sid, T("Rules", "A1:Q500"));
  const { rules, errors } = parseRulesSheet(ruleValues);
  const hash = sha(rules);
  const hashRef = stats.doc("rules");
  if ((await hashRef.get()).data()?.hash !== hash) {
    const db = getFirestore();
    const existing = await brandCol(bid, "rules").get();
    const batch = db.batch();
    const ids = new Set(rules.map((r) => r.id));
    existing.docs.filter((d) => !ids.has(d.id)).forEach((d) => batch.delete(d.ref));
    rules.forEach((r) => batch.set(brandCol(bid, "rules").doc(r.id), JSON.parse(JSON.stringify(r))));
    batch.set(hashRef, { hash, count: rules.length, at: now });
    await batch.commit();
    notes.push(`rules=${rules.length}`);
  }

  // 3. Log (backend → artifact).
  // Equality-only query (no orderBy) so it needs no composite index; sorted in memory.
  const pending = await brandCol(bid, "events").where("synced", "==", false).limit(200).get();
  if (!pending.empty) {
    const ordered = pending.docs.slice().sort((a, b) => Number(a.get("createdAt")) - Number(b.get("createdAt")));
    await sheets.spreadsheets.values.append({
      spreadsheetId: sid, range: T("Log", "A1"), valueInputOption: "RAW", insertDataOption: "INSERT_ROWS",
      requestBody: { values: ordered.map((d) => logRow(d.id, d.data())) },
    });
    const batch = getFirestore().batch();
    pending.docs.forEach((d) => batch.update(d.ref, { synced: true }));
    await batch.commit();
    notes.push(`log+${pending.size}`);
    await trimLog(sheets, sid, prefix + "Log");
  }

  // 3b. Knowledge (artifact → agent): Q/A rows the owner approved; Learn (agent → artifact): questions it could not answer.
  const kRows = await readTab(sheets, sid, T("Knowledge", "A2:B200")).catch(() => [] as string[][]);
  const items = kRows.filter((r) => (r[0] ?? "").trim() && (r[1] ?? "").trim()).map((r) => ({ q: r[0].trim().slice(0, 300), a: r[1].trim().slice(0, 900) }));
  const kHash = sha(items);
  const kRef = stats.doc("knowledge");
  if ((await kRef.get()).data()?.hash !== kHash) {
    await brandKnowledgeRef(bid).set({ items, updatedAt: now });
    await kRef.set({ hash: kHash, count: items.length, at: now });
    notes.push(`knowledge=${items.length}`);
  }
  const learnPending = await brandCol(bid, "learn").where("synced", "==", false).limit(50).get();
  if (!learnPending.empty) {
    try {
      await sheets.spreadsheets.values.append({
        spreadsheetId: sid, range: T("Learn", "A1"), valueInputOption: "RAW", insertDataOption: "INSERT_ROWS",
        requestBody: { values: learnPending.docs.map((d) => [isoTime(Number(d.get("at")) || now), String(d.get("lang") ?? ""), String(d.get("question") ?? ""), String(d.get("asked") ?? "1"), "open", d.id]) },
      });
      const lb = getFirestore().batch();
      learnPending.docs.forEach((d) => lb.update(d.ref, { synced: true }));
      await lb.commit();
      notes.push(`learn+${learnPending.size}`);
    } catch (e) {
      notes.push(`learn tab unavailable (${(e as Error).message.slice(0, 60)})`);
    }
  }

  // 3c. Leads (agent → artifact): people who agreed to be contacted. Updated rows are appended again (latest wins by id).
  const leadPending = await brandCol(bid, "leads").where("synced", "==", false).limit(50).get();
  if (!leadPending.empty) {
    try {
      await sheets.spreadsheets.values.append({
        spreadsheetId: sid, range: T("Leads", "A1"), valueInputOption: "RAW", insertDataOption: "INSERT_ROWS",
        requestBody: { values: leadPending.docs.map((d) => leadRow(d.id, d.data(), now)) },
      });
      // Older sheets were set up before the whatsapp / source / tags columns: keep the header row current.
      await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: T("Leads", "A1"), valueInputOption: "RAW", requestBody: { values: [[...LEAD_COLUMNS]] } });
      const lb2 = getFirestore().batch();
      leadPending.docs.forEach((d) => lb2.update(d.ref, { synced: true }));
      await lb2.commit();
      notes.push(`leads+${leadPending.size}`);
    } catch (e) {
      notes.push(`leads tab unavailable (${(e as Error).message.slice(0, 60)})`);
    }
  }

  // 4. Contacts (backend → artifact): rewritten as a snapshot.
  const people = await brandCol(bid, "contacts").orderBy("lastSeenAt", "desc").limit(CONTACTS_KEEP).get();
  if (!people.empty && !pending.empty) {
    const rows = people.docs.map((d) => {
      const c = d.data();
      return [d.id, String(c.username ?? ""), isoTime(Number(c.lastSeenAt) || 0), String(c.lastText ?? ""), ((c.tags as string[]) ?? []).join(", "), c.optedOut ? "TRUE" : "", c.handoffUntil && Number(c.handoffUntil) > now ? isoTime(Number(c.handoffUntil)) : ""];
    });
    await sheets.spreadsheets.values.clear({ spreadsheetId: sid, range: T("Contacts", "A2:G2000") });
    await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: T("Contacts", "A2"), valueInputOption: "RAW", requestBody: { values: rows } });
  }

  // 5. Health lines for the Dashboard (rows 40+: rows 2-39 belong to the artifact settings).
  const errDoc = (await stats.doc("errors").get()).data();
  const hourSent = Number((await stats.doc("hour_" + Math.floor(now / 3_600_000)).get()).data()?.sent) || 0;
  const dayKey = new Date(now).toISOString().slice(0, 10).replace(/-/g, "");
  const aiDay = (await stats.doc("ai_" + dayKey).get()).data();
  const poll = (await stats.doc("comment_poll").get()).data();
  const dmPoll = (await stats.doc("dm_poll").get()).data();
  const wa = bid === LEGACY_BRAND ? (await getFirestore().collection("app_config").doc("whatsapp").get()).data() : undefined;
  const pHealth = (await stats.doc("profile").get()).data();
  const health: string[][] = [
    ["health_synced_at", isoTime(now)],
    ["health_account", config.username ? "@" + config.username : ""],
    ["health_token_days_left", config.tokenExpiresAt ? String(daysLeft(config.tokenExpiresAt, now)) : ""],
    ["health_rules_loaded", String(rules.length)],
    ["health_rule_errors", errors.join(" • ").slice(0, 900)],
    ["health_sent_this_hour", String(hourSent)],
    ["health_last_api_error", errDoc ? `${isoTime(Number(errDoc.lastAt))} ${String(errDoc.lastError ?? "")}`.slice(0, 300) : ""],
    ["health_comment_poll", poll ? (poll.lastError ? `FAILING: ${String(poll.lastError).slice(0, 200)}` : `ok, last run ${isoTime(Number(poll.lastRun))}, ${Number(poll.processed) || 0} comments handled`) : "not run yet"],
    ["health_dm_poll", dmPoll ? (dmPoll.lastError ? `FAILING: ${String(dmPoll.lastError).slice(0, 200)}` : `ok, last run ${isoTime(Number(dmPoll.lastRun))}, ${Number(dmPoll.processed) || 0} DMs handled`) : "not run yet"],
    ["health_ai_replies_today", String(Number(aiDay?.calls) || 0)],
    ["health_ai_tokens_today", aiDay ? `${Number(aiDay.inputTokens) || 0} in / ${Number(aiDay.outputTokens) || 0} out / ${Number(aiDay.cacheReadTokens) || 0} cached` : ""],
    // WhatsApp lead alerts for ProHost: configured only in pro-host.tech/admin (the token never goes into this sheet).
    ["health_whatsapp_alerts", bid === LEGACY_BRAND ? waStatus(wa) : ""],
    ["health_effective_mode", config.mode === "live" && !config.killSwitch ? "LIVE" : "dry-run"],
    ["health_profile_errors", String(pHealth?.errors ?? "")],
    ["health_platforms", `instagram: ${config.accessToken && config.igUserId ? "connected" : "not connected"} · whatsapp: ${config.whatsapp.enabled && config.whatsapp.accessToken ? "connected" : "not connected"}`],
  ];
  await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: T("Status", "A40"), valueInputOption: "RAW", requestBody: { values: health } });
  return notes;
}

const BUILDER_DEPS: BuilderDeps = { fetch, drive: () => google.drive({ version: "v3", auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/drive"] }) }), complete };

async function knowledgeStep(sheets: sheets_v4.Sheets, sid: string, config: IgConfig, status: Map<string, string>, now: number, deps: BuilderDeps = BUILDER_DEPS): Promise<string[]> {
  const notes: string[] = [];
  const bid = config.brandId;
  const prefix = config.brand.tabPrefix;
  const T = (base: string, a1: string) => range(prefix, base, a1);
  if (/^request/i.test(status.get("build_knowledge") ?? "")) {
    // Claim the request first so a slow build is never started twice.
    const statusRows = await readTab(sheets, sid, T("Status", "A2:B100"));
    const at = statusRows.findIndex((r) => (r[0] ?? "").trim().toLowerCase() === "build_knowledge");
    const mark = (text: string) => sheets.spreadsheets.values.update({ spreadsheetId: sid, range: T("Status", `B${at + 2}`), valueInputOption: "RAW", requestBody: { values: [[text]] } });
    if (at >= 0) await mark(`working · ${isoTime(now)}`);
    const { sources, errors } = parseSources(await readTab(sheets, sid, T("Sources", "A1:D40")));
    const result = sources.length ? await buildKnowledgeDraft(config, sources, deps) : { sections: [], gathered: [], error: "add at least one source on the Sources tab" };
    if (result.gathered.length) {
      await sheets.spreadsheets.values.batchUpdate({ spreadsheetId: sid, requestBody: { valueInputOption: "RAW", data: result.gathered.map((g) => ({ range: T("Sources", `D${g.row}`), values: [[g.status]] })) } });
    }
    if (result.sections.length) {
      await sheets.spreadsheets.values.clear({ spreadsheetId: sid, range: T("KnowledgeDraft", "A2:D100") });
      await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: T("KnowledgeDraft", "A2"), valueInputOption: "RAW", requestBody: { values: result.sections.map((x) => [x.section, x.text, x.sources, "draft"]) } });
    }
    if (at >= 0) await mark(result.error ? `FAILED · ${result.error}${errors.length ? " · " + errors[0] : ""}` : `done · ${isoTime(now)} · ${result.sections.length} sections to review`);
    notes.push(result.error ? `knowledge build failed (${result.error.slice(0, 60)})` : `knowledge draft ${result.sections.length} sections`);
  }
  const draft = await readTab(sheets, sid, T("KnowledgeDraft", "A1:D100")).catch(() => [] as string[][]);
  const { text, applied } = composeKnowledge(draft.slice(1));
  if (applied.length && text) {
    await putFields(brandRef(bid), { knowledgeBase: text });
    // Keep the Profile tab in step, so the next profile sync does not overwrite the approved text.
    const profile = await readTab(sheets, sid, T("Profile", "A1:B60"));
    const kb = profile.findIndex((r) => (r[0] ?? "").trim().toLowerCase() === "knowledge_base");
    if (kb >= 0) await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: T("Profile", `B${kb + 1}`), valueInputOption: "RAW", requestBody: { values: [[text.slice(0, 45000)]] } });
    await sheets.spreadsheets.values.batchUpdate({ spreadsheetId: sid, requestBody: { valueInputOption: "RAW", data: applied.map((r) => ({ range: T("KnowledgeDraft", `D${r}`), values: [["applied"]] })) } });
    clearConfigCache(bid);
    notes.push(`knowledge approved (${applied.length} sections)`);
  }
  return notes;
}

export async function runSheetSync(now = Date.now()): Promise<string> {
  const registry = await loadConfig(LEGACY_BRAND, true);
  if (!registry.sheetId) return "no sheetId configured";
  const sheets = await sheetsClient();
  const notes: string[] = [];
  // Registry first: new brands, platform accounts and secrets typed in the console become real before the per-brand passes.
  notes.push(...(await syncRegistry(sheets, registry.sheetId, now)));
  for (const config of await loadAllConfigs(true)) {
    const sid = config.sheetId || registry.sheetId;
    try {
      const brandNotes = await syncBrand(sheets, sid, config, now);
      notes.push(...brandNotes.map((n) => (config.brandId === LEGACY_BRAND ? n : `${config.brandId}: ${n}`)));
    } catch (e) {
      notes.push(`${config.brandId}: FAILED ${(e as Error).message.slice(0, 120)}`);
      logger.error("igSheetSync brand failed", { brand: config.brandId, message: (e as Error).message });
    }
  }
  return notes.join(" ") || "no changes";
}

/** The WhatsApp alerts line: off until set up at pro-host.tech/admin › WhatsApp alerts (lib/whatsappAlerts.ts). */
export function waStatus(d: Record<string, unknown> | undefined): string {
  const missing = whatsAppMissing(parseWhatsAppConfig(d));
  const last = typeof d?.lastResult === "string" && d.lastResult ? ` · last alert: ${d.lastResult.slice(0, 120)}` : "";
  return (missing.length ? `OFF (missing: ${missing.join(", ")}) · set up at pro-host.tech/admin › WhatsApp alerts` : "ON") + last;
}

async function trimLog(sheets: sheets_v4.Sheets, sheetId: string, title: string): Promise<void> {
  const meta = await sheets.spreadsheets.get({ spreadsheetId: sheetId, fields: "sheets.properties" });
  const log = meta.data.sheets?.find((s) => s.properties?.title === title)?.properties;
  const rows = log?.gridProperties?.rowCount ?? 0;
  if (!log || rows <= LOG_KEEP + 200 || log.sheetId == null) return;
  await sheets.spreadsheets.batchUpdate({ spreadsheetId: sheetId, requestBody: { requests: [{ deleteDimension: { range: { sheetId: log.sheetId, dimension: "ROWS", startIndex: 1, endIndex: rows - LOG_KEEP } } }] } });
}

export const igSheetSync = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 300, memory: "512MiB" }, async () => {
  const stats = brandCol(LEGACY_BRAND, "stats").doc("sync");
  try {
    const out = await runSheetSync();
    await stats.set({ at: Date.now(), ok: true, note: out, error: "" }).catch(() => undefined);
    if (out !== "no changes" && out !== "no sheetId configured") logger.info("igSheetSync: " + out);
  } catch (e) {
    const message = (e as Error).message;
    logger.error("igSheetSync failed", { message });
    // Visible to Maintenance igsmoke, so a broken sync is diagnosable without the Cloud Logging console.
    await stats.set({ at: Date.now(), ok: false, error: message.slice(0, 400) }).catch(() => undefined);
  }
});

// Re-exported for tests and for Maintenance tasks.
export { q, ensureTabs as ensureBrandTabs };
