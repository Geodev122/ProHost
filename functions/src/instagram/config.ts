import "../lib/admin";
import { DEFAULT_SETTINGS } from "./engine";
import { BrandProfile, PROHOST_PROFILE, blankProfile, parseProfile } from "./brands";
import { LEGACY_BRAND, brandConfigRef, brandRef, brandSecretsRef, isLegacyBrand, listBrandIds } from "./store";
import { EngineSettings } from "./types";

/**
 * app_config/instagram (server-only; written by Maintenance task `igsetup` and the sheet bridge):
 *   appSecret, verifyToken, accessToken, igUserId, username, tokenExpiresAt (ms)
 *   mode: "dry-run" | "live"   killSwitch: boolean (true = never send)
 *   settings: partial EngineSettings (optOutWords, handoffWords, hourlyCap, activeFromHour, ...)
 *   adminEmail
 */
export interface WhatsAppPlatform {
  /** WhatsApp Business Cloud API: the number the brand talks from, its business account, the Meta app (may differ from Instagram's). */
  phoneNumberId: string;
  wabaId: string;
  appId: string;
  appSecret: string;
  accessToken: string;
  verifyToken: string;
  /** Approved template used outside the 24 h window (name + language). */
  templateName: string;
  templateLang: string;
  enabled: boolean;
}
export const BLANK_WHATSAPP: WhatsAppPlatform = { phoneNumberId: "", wabaId: "", appId: "", appSecret: "", accessToken: "", verifyToken: "", templateName: "", templateLang: "en", enabled: false };

export interface IgConfig {
  /** Which brand this configuration belongs to ("prohost" = the original single account). */
  brandId: string;
  /** False = the brand is switched off in the console: no webhook handling, polling or follow-ups. */
  enabled: boolean;
  brand: BrandProfile;
  whatsapp: WhatsAppPlatform;
  /** Instagram app id (informational; the shared Meta app by default). */
  appId: string;
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
  /** Only for provider "anthropic" (Vertex needs no key: the functions' service account is used). */
  anthropicKey: string;
  /** Gemini API key from Google AI Studio (provider "gemini"). */
  geminiKey: string;
  ai: AiSettings;
}

export interface AiSettings {
  enabled: boolean;
  provider: "vertex" | "anthropic" | "gemini";
  model: string;
  region: string;
  /** Anthropic workspace id for keys that are not scoped to one workspace (not a secret; Status ai_workspace_id). */
  workspaceId: string;
  dailyCap: number;
  perPersonPerHour: number;
  /** Answer public comments by intent (thanks / question / negative). */
  comments: boolean;
  /** Positive comments also get ONE opening DM. */
  commentDm: boolean;
}
export const DEFAULT_AI: AiSettings = { enabled: false, provider: "vertex", model: "claude-haiku-4-5@20251001", region: "global", workspaceId: "", dailyCap: 300, perPersonPerHour: 12, comments: true, commentDm: true };

const TTL_MS = 60 * 1000;
/** ProHost's engine defaults (its links, its wording); every other brand starts with no links and its own name in the hand-over line. */
export function baseSettings(brand: BrandProfile): EngineSettings {
  if (brand.id === PROHOST_PROFILE.id) return DEFAULT_SETTINGS;
  return { ...DEFAULT_SETTINGS, vars: {}, handoffReply: `Thanks! I'm ${brand.assistantName}, so I've passed this to the ${brand.name} team and a person will reply here soon.` };
}

const cache = new Map<string, { config: IgConfig; at: number }>();

export function parseConfig(d: Record<string, unknown> | undefined, brand: BrandProfile = PROHOST_PROFILE, wa: Partial<WhatsAppPlatform> = {}): IgConfig {
  const s = (d?.settings ?? {}) as Partial<EngineSettings>;
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  return {
    brandId: brand.id, enabled: d?.enabled !== false, brand, whatsapp: { ...BLANK_WHATSAPP, ...wa }, appId: str(d?.appId),
    appSecret: str(d?.appSecret), verifyToken: str(d?.verifyToken), accessToken: str(d?.accessToken),
    igUserId: str(d?.igUserId), username: str(d?.username), tokenExpiresAt: Number(d?.tokenExpiresAt) || 0,
    mode: d?.mode === "live" ? "live" : "dry-run",
    killSwitch: Boolean(d?.killSwitch),
    adminEmail: str(d?.adminEmail) || brand.contactEmail || "admin@pro-host.tech", sheetId: str(d?.sheetId), anthropicKey: str(d?.anthropicKey), geminiKey: str(d?.geminiKey), ai: { ...DEFAULT_AI, ...((d?.ai ?? {}) as Partial<AiSettings>) },
    settings: { ...baseSettings(brand), ...s, vars: { ...baseSettings(brand).vars, ...(s.vars ?? {}) } },
  };
}

/**
 * A non-legacy brand keeps its settings in `ig_brands/{id}` (platforms.instagram / platforms.whatsapp for the account ids)
 * and its secrets in `ig_brands/{id}/private/secrets`; this flattens them into the shape `parseConfig` reads.
 */
export function flattenBrand(doc: Record<string, unknown> | undefined, secrets: Record<string, unknown> | undefined): { flat: Record<string, unknown>; wa: Partial<WhatsAppPlatform> } {
  const d = doc ?? {};
  const platforms = (d.platforms ?? {}) as Record<string, Record<string, unknown>>;
  const ig = platforms.instagram ?? {};
  const w = platforms.whatsapp ?? {};
  const sec = (secrets ?? {}) as Record<string, Record<string, unknown> | string>;
  const sIg = (sec.instagram ?? {}) as Record<string, unknown>;
  const sWa = (sec.whatsapp ?? {}) as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  return {
    flat: {
      ...d, appId: ig.appId, igUserId: ig.igUserId, username: ig.username, tokenExpiresAt: ig.tokenExpiresAt,
      appSecret: sIg.appSecret, accessToken: sIg.accessToken, verifyToken: sIg.verifyToken,
      anthropicKey: str(sec.anthropicKey), geminiKey: str(sec.geminiKey),
    },
    wa: {
      phoneNumberId: str(w.phoneNumberId), wabaId: str(w.wabaId), appId: str(w.appId), templateName: str(w.templateName), templateLang: str(w.templateLang) || "en", enabled: w.enabled === true,
      appSecret: str(sWa.appSecret), accessToken: str(sWa.accessToken), verifyToken: str(sWa.verifyToken),
    },
  };
}

export async function loadConfig(brandId: string = LEGACY_BRAND, force = false): Promise<IgConfig> {
  const hit = cache.get(brandId);
  if (!force && hit && Date.now() - hit.at < TTL_MS) return hit.config;
  let config: IgConfig;
  if (isLegacyBrand(brandId)) {
    const [snap, profile, wa] = await Promise.all([brandConfigRef(brandId).get(), brandRef(LEGACY_BRAND).get(), brandSecretsRef(LEGACY_BRAND).get()]);
    // ProHost: the original flat document for Instagram, plus the optional registry overlay for profile and WhatsApp.
    const platforms = ((profile.data()?.platforms ?? {}) as Record<string, Record<string, unknown>>);
    const fl = flattenBrand({ platforms: { whatsapp: platforms.whatsapp } }, wa.data());
    config = parseConfig(snap.data(), parseProfile(PROHOST_PROFILE, profile.data()), fl.wa);
  } else {
    const [doc, secrets] = await Promise.all([brandRef(brandId).get(), brandSecretsRef(brandId).get()]);
    const { flat, wa } = flattenBrand(doc.data(), secrets.data());
    const name = typeof doc.data()?.name === "string" ? (doc.data()?.name as string) : brandId;
    config = parseConfig(flat, parseProfile(blankProfile(brandId, name), doc.data()), wa);
  }
  cache.set(brandId, { config, at: Date.now() });
  return config;
}

/** Every brand's configuration (ProHost first). A brand that fails to load is skipped, never fatal for the others. */
export async function loadAllConfigs(force = false): Promise<IgConfig[]> {
  const out: IgConfig[] = [];
  for (const id of await listBrandIds()) {
    try {
      const c = await loadConfig(id, force);
      if (c.enabled) out.push(c);
    } catch { /* skipped */ }
  }
  return out;
}

export function clearConfigCache(brandId?: string): void { if (brandId) cache.delete(brandId); else cache.clear(); }

/** Sends only when live, not killed, and fully configured. Everything else is a dry run. */
export function canSend(c: IgConfig, channel: "instagram" | "whatsapp" = "instagram"): boolean {
  if (c.mode !== "live" || c.killSwitch) return false;
  return channel === "whatsapp" ? c.whatsapp.enabled && !!c.whatsapp.accessToken && !!c.whatsapp.phoneNumberId : !!c.accessToken && !!c.igUserId;
}
