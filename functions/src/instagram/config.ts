import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { DEFAULT_SETTINGS } from "./engine";
import { EngineSettings } from "./types";

/**
 * app_config/instagram (server-only; written by Maintenance task `igsetup` and the sheet bridge):
 *   appSecret, verifyToken, accessToken, igUserId, username, tokenExpiresAt (ms)
 *   mode: "dry-run" | "live"   killSwitch: boolean (true = never send)
 *   settings: partial EngineSettings (optOutWords, handoffWords, hourlyCap, activeFromHour, ...)
 *   adminEmail
 */
export interface IgConfig {
  appSecret: string;
  verifyToken: string;
  accessToken: string;
  igUserId: string;
  username: string;
  tokenExpiresAt: number;
  mode: "dry-run" | "live";
  killSwitch: boolean;
  settings: EngineSettings;
  adminEmail: string;
  /** Google Sheet "ProHost IG Automations" shared with the functions service account. */
  sheetId: string;
}

const TTL_MS = 60 * 1000;
let cached: { config: IgConfig; at: number } | null = null;

export function parseConfig(d: Record<string, unknown> | undefined): IgConfig {
  const s = (d?.settings ?? {}) as Partial<EngineSettings>;
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  return {
    appSecret: str(d?.appSecret), verifyToken: str(d?.verifyToken), accessToken: str(d?.accessToken),
    igUserId: str(d?.igUserId), username: str(d?.username), tokenExpiresAt: Number(d?.tokenExpiresAt) || 0,
    mode: d?.mode === "live" ? "live" : "dry-run",
    killSwitch: Boolean(d?.killSwitch),
    adminEmail: str(d?.adminEmail) || "admin@pro-host.tech", sheetId: str(d?.sheetId),
    settings: { ...DEFAULT_SETTINGS, ...s, vars: { ...DEFAULT_SETTINGS.vars, ...(s.vars ?? {}) } },
  };
}

export async function loadConfig(force = false): Promise<IgConfig> {
  if (!force && cached && Date.now() - cached.at < TTL_MS) return cached.config;
  const snap = await getFirestore().collection("app_config").doc("instagram").get();
  const config = parseConfig(snap.data());
  cached = { config, at: Date.now() };
  return config;
}

export function clearConfigCache(): void { cached = null; }

/** Sends only when live, not killed, and fully configured. Everything else is a dry run. */
export function canSend(c: IgConfig): boolean {
  return c.mode === "live" && !c.killSwitch && !!c.accessToken && !!c.igUserId;
}
