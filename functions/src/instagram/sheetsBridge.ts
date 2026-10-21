import { createHash } from "node:crypto";
import { getFirestore } from "firebase-admin/firestore";
import { google, sheets_v4 } from "googleapis";
import * as logger from "firebase-functions/logger";
import { onSchedule } from "firebase-functions/v2/scheduler";
import "../lib/admin";
import { clearConfigCache, loadConfig } from "./config";
import { CONTACT_COLUMNS, LOG_COLUMNS, RULE_COLUMNS, isoTime, logRow, parseRulesSheet, settingsFromStatus } from "./rulesSheet";
import { daysLeft } from "./tokenRefresh";

/**
 * The artifact can't call Cloud Functions, so it edits a Google Sheet ("ProHost IG Automations") through the
 * Sheets connector and this job mirrors it every minute:
 *   Rules    (artifact → backend)  validated rows replace ig_rules; errors are written to Status
 *   Status   (both)                artifact writes mode + kill_switch, backend writes health lines
 *   Log      (backend → artifact)  new ig_events appended (capped at LOG_KEEP rows)
 *   Contacts (backend → artifact)  most recent people
 * The sheet must be shared (Editor) with the functions' service account.
 */
const LOG_KEEP = 2000;
const CONTACTS_KEEP = 500;

async function sheetsClient(): Promise<sheets_v4.Sheets> {
  const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/spreadsheets"] });
  return google.sheets({ version: "v4", auth });
}

export const TABS = ["Rules", "Log", "Contacts", "Status"] as const;

/** Creates missing tabs and header rows; safe to run repeatedly. Used by `igsetup`. */
export async function ensureTabs(sheets: sheets_v4.Sheets, sheetId: string): Promise<void> {
  const meta = await sheets.spreadsheets.get({ spreadsheetId: sheetId, fields: "sheets.properties.title" });
  const have = new Set((meta.data.sheets ?? []).map((s) => s.properties?.title));
  const missing = TABS.filter((t) => !have.has(t));
  if (missing.length) {
    await sheets.spreadsheets.batchUpdate({ spreadsheetId: sheetId, requestBody: { requests: missing.map((title) => ({ addSheet: { properties: { title } } })) } });
  }
  const headers: Record<string, readonly string[]> = { Rules: RULE_COLUMNS, Log: LOG_COLUMNS, Contacts: CONTACT_COLUMNS, Status: ["key", "value"] };
  for (const tab of missing) {
    await sheets.spreadsheets.values.update({ spreadsheetId: sheetId, range: `${tab}!A1`, valueInputOption: "RAW", requestBody: { values: [[...headers[tab]]] } });
  }
}

async function readTab(sheets: sheets_v4.Sheets, sheetId: string, range: string): Promise<string[][]> {
  const r = await sheets.spreadsheets.values.get({ spreadsheetId: sheetId, range });
  return (r.data.values ?? []).map((row) => row.map((c) => String(c ?? "")));
}

export async function runSheetSync(now = Date.now()): Promise<string> {
  const config = await loadConfig(true);
  if (!config.sheetId) return "no sheetId configured";
  const db = getFirestore();
  const sheets = await sheetsClient();
  const sid = config.sheetId;
  const notes: string[] = [];

  // 1. Status (artifact → backend): mode + kill switch.
  const status = new Map(await readTab(sheets, sid, "Status!A2:B100").then((rows) => rows.filter((r) => (r[0] ?? "").trim()).map((r) => [r[0].trim().toLowerCase(), (r[1] ?? "").trim()] as [string, string])));
  const wantMode = status.get("mode")?.toLowerCase() === "live" ? "live" : "dry-run";
  const wantKill = /^(true|yes|1|on)$/i.test(status.get("kill_switch") ?? "");
  const wantSettings = settingsFromStatus(status);
  const settingsHash = createHash("sha256").update(JSON.stringify(wantSettings)).digest("hex");
  const settingsRef = db.collection("ig_stats").doc("settings");
  const settingsChanged = (await settingsRef.get()).data()?.hash !== settingsHash;
  if (wantMode !== config.mode || wantKill !== config.killSwitch || settingsChanged) {
    // settings is replaced as a whole (not merged) so removing a value in the sheet restores the default.
    await db.collection("app_config").doc("instagram").update({ mode: wantMode, killSwitch: wantKill, settings: wantSettings });
    await settingsRef.set({ hash: settingsHash, at: now });
    clearConfigCache();
    notes.push(`mode=${wantMode} kill=${wantKill}${settingsChanged ? " settings" : ""}`);
  }

  // 2. Rules (artifact → backend), only when the sheet content changed.
  const ruleValues = await readTab(sheets, sid, "Rules!A1:P500");
  const { rules, errors } = parseRulesSheet(ruleValues);
  const hash = createHash("sha256").update(JSON.stringify(rules)).digest("hex");
  const hashRef = db.collection("ig_stats").doc("rules");
  if ((await hashRef.get()).data()?.hash !== hash) {
    const existing = await db.collection("ig_rules").get();
    const batch = db.batch();
    const ids = new Set(rules.map((r) => r.id));
    existing.docs.filter((d) => !ids.has(d.id)).forEach((d) => batch.delete(d.ref));
    rules.forEach((r) => batch.set(db.collection("ig_rules").doc(r.id), JSON.parse(JSON.stringify(r))));
    batch.set(hashRef, { hash, count: rules.length, at: now });
    await batch.commit();
    notes.push(`rules=${rules.length}`);
  }

  // 3. Log (backend → artifact).
  // Equality-only query (no orderBy) so it needs no composite index; sorted in memory.
  const pending = await db.collection("ig_events").where("synced", "==", false).limit(200).get();
  if (!pending.empty) {
    const ordered = pending.docs.slice().sort((a, b) => Number(a.get("createdAt")) - Number(b.get("createdAt")));
    await sheets.spreadsheets.values.append({
      spreadsheetId: sid, range: "Log!A1", valueInputOption: "RAW", insertDataOption: "INSERT_ROWS",
      requestBody: { values: ordered.map((d) => logRow(d.id, d.data())) },
    });
    const batch = db.batch();
    pending.docs.forEach((d) => batch.update(d.ref, { synced: true }));
    await batch.commit();
    notes.push(`log+${pending.size}`);
    await trimLog(sheets, sid);
  }

  // 4. Contacts (backend → artifact): rewritten as a snapshot.
  const people = await db.collection("ig_contacts").orderBy("lastSeenAt", "desc").limit(CONTACTS_KEEP).get();
  if (!people.empty && !pending.empty) {
    const rows = people.docs.map((d) => {
      const c = d.data();
      return [d.id, String(c.username ?? ""), isoTime(Number(c.lastSeenAt) || 0), String(c.lastText ?? ""), ((c.tags as string[]) ?? []).join(", "), c.optedOut ? "TRUE" : "", c.handoffUntil && Number(c.handoffUntil) > now ? isoTime(Number(c.handoffUntil)) : ""];
    });
    await sheets.spreadsheets.values.clear({ spreadsheetId: sid, range: "Contacts!A2:G2000" });
    await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: "Contacts!A2", valueInputOption: "RAW", requestBody: { values: rows } });
  }

  // 5. Health lines for the Dashboard (rows 40+: rows 2-39 belong to the artifact settings).
  const errDoc = (await db.collection("ig_stats").doc("errors").get()).data();
  const hourSent = Number((await db.collection("ig_stats").doc("hour_" + Math.floor(now / 3_600_000)).get()).data()?.sent) || 0;
  const health: string[][] = [
    ["health_synced_at", isoTime(now)],
    ["health_account", config.username ? "@" + config.username : ""],
    ["health_token_days_left", config.tokenExpiresAt ? String(daysLeft(config.tokenExpiresAt, now)) : ""],
    ["health_rules_loaded", String(rules.length)],
    ["health_rule_errors", errors.join(" • ").slice(0, 900)],
    ["health_sent_this_hour", String(hourSent)],
    ["health_last_api_error", errDoc ? `${isoTime(Number(errDoc.lastAt))} ${String(errDoc.lastError ?? "")}`.slice(0, 300) : ""],
    ["health_effective_mode", config.mode === "live" && !config.killSwitch ? "LIVE" : "dry-run"],
  ];
  await sheets.spreadsheets.values.update({ spreadsheetId: sid, range: "Status!A40", valueInputOption: "RAW", requestBody: { values: health } });
  return notes.join(" ") || "no changes";
}

async function trimLog(sheets: sheets_v4.Sheets, sheetId: string): Promise<void> {
  const meta = await sheets.spreadsheets.get({ spreadsheetId: sheetId, fields: "sheets.properties" });
  const log = meta.data.sheets?.find((s) => s.properties?.title === "Log")?.properties;
  const rows = log?.gridProperties?.rowCount ?? 0;
  if (!log || rows <= LOG_KEEP + 200 || log.sheetId == null) return;
  await sheets.spreadsheets.batchUpdate({ spreadsheetId: sheetId, requestBody: { requests: [{ deleteDimension: { range: { sheetId: log.sheetId, dimension: "ROWS", startIndex: 1, endIndex: rows - LOG_KEEP } } }] } });
}

export const igSheetSync = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 120, memory: "256MiB" }, async () => {
  const stats = getFirestore().collection("ig_stats").doc("sync");
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
