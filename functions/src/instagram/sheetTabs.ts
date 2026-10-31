import { sheets_v4 } from "googleapis";
import { BrandProfile, PROHOST_PROFILE, blankProfile } from "./brands";
import { BRAND_COLUMNS, BUTTON_COLUMNS, PLATFORM_COLUMNS, PLAYBOOK_COLUMNS, SECRET_COLUMNS, SOURCE_COLUMNS, buttonRows, playbookRows, profileRows } from "./registryTabs";
import { CONTACT_COLUMNS, LEAD_COLUMNS, LOG_COLUMNS, RULE_COLUMNS } from "./rulesSheet";

/** A1 notation needs quotes around tab names that contain anything but letters: always quote. */
export const q = (title: string): string => `'${title.replace(/'/g, "''")}'`;
export const range = (prefix: string, base: string, a1: string): string => `${q(prefix + base)}!${a1}`;

/** Tabs every brand has (names are prefixed with the brand's tabPrefix; ProHost's prefix is empty). */
export const TABS = ["Rules", "Log", "Contacts", "Status", "Knowledge", "Learn", "Leads", "Profile", "Buttons", "Playbooks", "Sources", "KnowledgeDraft"] as const;
export const REGISTRY_TABS = ["Brands", "Platforms", "Secrets"] as const;

const HEADERS: Record<string, readonly string[]> = {
  Rules: RULE_COLUMNS, Log: LOG_COLUMNS, Contacts: CONTACT_COLUMNS, Status: ["key", "value"], Knowledge: ["question", "answer"],
  Learn: ["time", "lang", "question", "times", "status", "id"], Leads: LEAD_COLUMNS,
  Profile: ["key", "value"], Buttons: BUTTON_COLUMNS, Playbooks: PLAYBOOK_COLUMNS, Sources: SOURCE_COLUMNS,
  KnowledgeDraft: ["section", "text", "sources", "status"],
  Brands: BRAND_COLUMNS, Platforms: PLATFORM_COLUMNS, Secrets: SECRET_COLUMNS,
};

/** Rows written under the header when a tab is first created, so the console shows the real starting values. */
function seed(base: string, profile: BrandProfile): string[][] {
  if (base === "Profile") return profileRows(profile);
  if (base === "Buttons") return buttonRows(profile.buttons);
  if (base === "Playbooks") return playbookRows(profile.playbooks);
  return [];
}

export interface EnsureOptions { prefix?: string; registry?: boolean; profile?: BrandProfile; brandId?: string }

/** Creates missing tabs and header rows; safe to run repeatedly. `igsetup` calls it with defaults (ProHost's original tabs). */
export async function ensureTabs(sheets: sheets_v4.Sheets, sheetId: string, opts: EnsureOptions | string = {}): Promise<string[]> {
  const o: EnsureOptions = typeof opts === "string" ? { prefix: opts } : opts;
  const prefix = o.prefix ?? "";
  const profile = o.profile ?? (prefix === "" ? PROHOST_PROFILE : blankProfile(o.brandId ?? prefix.replace(/_$/, ""), o.brandId ?? prefix.replace(/_$/, "")));
  const meta = await sheets.spreadsheets.get({ spreadsheetId: sheetId, fields: "sheets.properties.title" });
  const have = new Set((meta.data.sheets ?? []).map((s) => s.properties?.title));
  const wanted: Array<{ base: string; title: string }> = [
    ...TABS.map((base) => ({ base, title: prefix + base })),
    ...(o.registry ? REGISTRY_TABS.map((base) => ({ base, title: base })) : []),
  ];
  const missing = wanted.filter((w) => !have.has(w.title));
  if (missing.length) {
    await sheets.spreadsheets.batchUpdate({ spreadsheetId: sheetId, requestBody: { requests: missing.map((w) => ({ addSheet: { properties: { title: w.title } } })) } });
  }
  for (const w of missing) {
    await sheets.spreadsheets.values.update({
      spreadsheetId: sheetId, range: `${q(w.title)}!A1`, valueInputOption: "RAW",
      requestBody: { values: [[...HEADERS[w.base]], ...seed(w.base, profile)] },
    });
  }
  return missing.map((w) => w.title);
}

export async function readTab(sheets: sheets_v4.Sheets, sheetId: string, a1: string): Promise<string[][]> {
  const r = await sheets.spreadsheets.values.get({ spreadsheetId: sheetId, range: a1 });
  return (r.data.values ?? []).map((row) => row.map((c) => String(c ?? "")));
}
