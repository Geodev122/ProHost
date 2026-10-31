import { PROHOST_KNOWLEDGE_BASE } from "./aiKnowledge";
import { PROHOST_PLAYBOOKS } from "./prohostPlaybooks";

/**
 * A brand's identity as the agent sees it: who it is, how it talks, what it links to, which ProHost-only data providers are on.
 * ProHost's profile is the built-in default (it reproduces the agent exactly as it was before brands existed); the registry
 * document `ig_brands/{id}` overlays it field by field, and new brands start from the blank profile below.
 */
export type Platform = "instagram" | "whatsapp";

export interface BrandButton { url: string; labels: Record<string, string> }
export interface Playbook { name: string; enabled: boolean; text: string }
export interface Integrations {
  /** Premium offers read live from Google Play (ProHost only). */
  offers: boolean;
  /** Live listings catalogue and the space finder (ProHost only). */
  listings: boolean;
  /** Read-only account lookup by registration email (needs the brand's own app database; ProHost only). */
  accountLookup: boolean;
  /** "What's new" notes generated from docs/release (ProHost only). */
  release: boolean;
}
export interface BrandProfile {
  id: string;
  name: string;
  /** One sentence for the opening of the prompt: what the assistant helps with. */
  purpose: string;
  /** Shown in alerts and canned lines: "ProHost's automated assistant". */
  assistantName: string;
  /** How the brand sells and sounds (the "SELLING" part of the prompt). Cannot override the locked core rules. */
  persona: string;
  /** Business knowledge the agent answers from. */
  knowledgeBase: string;
  buttons: Record<string, BrandButton>;
  playbooks: Playbook[];
  integrations: Integrations;
  /** Prefix of this brand's sheet tabs ("" = the original tab names). */
  tabPrefix: string;
  /** Contact shown to people (also used in the fallback reply). */
  contactEmail: string;
  languages: string[];
}

export const PROHOST_BUTTONS: Record<string, BrandButton> = {
  download: { url: "https://pro-host.tech/ig", labels: { en: "Get the app", ar: "حمّل التطبيق", fr: "Télécharger", es: "Descargar" } },
  whatsapp: { url: "https://wa.me/96176701865", labels: { en: "WhatsApp us", ar: "واتساب", fr: "WhatsApp", es: "WhatsApp" } },
  email: { url: "https://pro-host.tech/#contact", labels: { en: "Email us", ar: "راسلنا بالبريد", fr: "Nous écrire", es: "Escríbenos" } },
  website: { url: "https://pro-host.tech/", labels: { en: "Visit website", ar: "زور الموقع", fr: "Voir le site", es: "Ver el sitio" } },
  partner: { url: "https://pro-host.tech/partner", labels: { en: "Partner with us", ar: "كن شريكاً", fr: "Devenir partenaire", es: "Ser socio" } },
  office: { url: "https://pro-host.tech/list-your-office?utm_source=instagram&utm_medium=dm&utm_campaign=office_sourcing", labels: { en: "List your office", ar: "سجّل مكتبك", fr: "Inscrire mon cabinet", es: "Registrar mi consulta" } },
};

/** Where ProHost's business knowledge lives (public pages only; its app repository is private). Seeded into the Sources tab. */
export const PROHOST_SOURCES: string[][] = [
  ["url", "https://pro-host.tech/", "Home: what ProHost is, hosts and professionals, FAQ", ""],
  ["url", "https://pro-host.tech/list-your-space", "Hosts: how to list a space", ""],
  ["url", "https://pro-host.tech/clinic-room-rental", "Clinic rooms by the hour", ""],
  ["url", "https://pro-host.tech/therapy-room-rental", "Therapy rooms", ""],
  ["url", "https://pro-host.tech/meeting-room-rental", "Meeting rooms", ""],
  ["url", "https://pro-host.tech/training-room-rental", "Training rooms", ""],
  ["url", "https://pro-host.tech/studio-rental", "Studios", ""],
  ["url", "https://pro-host.tech/partner", "Strategic partners", ""],
  ["url", "https://pro-host.tech/list-your-office", "Office-owner offer", ""],
];

export const BLANK_INTEGRATIONS: Integrations = { offers: false, listings: false, accountLookup: false, release: false };

export const PROHOST_PROFILE: BrandProfile = {
  id: "prohost",
  name: "ProHost",
  assistantName: "ProHost's automated assistant",
  purpose: "You help people find the right space or list their own.",
  persona: `SELLING, HONESTLY
- Professionals (renters): the app is free; stress finding a room by the hour/shift/day on a live map and booking fast. Hosts (space owners): publishing needs Premium (LIVE PREMIUM OFFERS); explain what earns them money (rent each room by hour, shift, day, month or per attendee, live availability, no double bookings).
- Use the RELEASE section for "what's new". If ProHost is not a fit, say so kindly. Never pressure. One call to action per message.`,
  knowledgeBase: PROHOST_KNOWLEDGE_BASE,
  buttons: PROHOST_BUTTONS,
  playbooks: PROHOST_PLAYBOOKS,
  integrations: { offers: true, listings: true, accountLookup: true, release: true },
  tabPrefix: "",
  contactEmail: "admin@pro-host.tech",
  languages: ["en", "ar", "fr", "es"],
};

/** A new brand: nothing ProHost-specific, no data providers, one website button once the owner adds a link. */
export function blankProfile(id: string, name: string): BrandProfile {
  return {
    id, name, assistantName: `${name}'s automated assistant`, purpose: `You help people with ${name}: you answer their questions and point them to the right next step.`, persona: "", knowledgeBase: "", buttons: {}, playbooks: [],
    integrations: { ...BLANK_INTEGRATIONS }, tabPrefix: `${id}_`, contactEmail: "", languages: ["en"],
  };
}

const str = (v: unknown, max = 60000): string => (typeof v === "string" ? v.slice(0, max) : "");
const bool = (v: unknown, d: boolean): boolean => (typeof v === "boolean" ? v : d);

/** The registry document overlaid on a base profile; unknown or malformed fields keep the base value. */
export function parseProfile(base: BrandProfile, d: Record<string, unknown> | undefined): BrandProfile {
  if (!d) return base;
  const buttons: Record<string, BrandButton> = {};
  if (d.buttons && typeof d.buttons === "object") {
    for (const [id, raw] of Object.entries(d.buttons as Record<string, unknown>)) {
      const b = raw as { url?: unknown; labels?: unknown };
      const url = str(b?.url, 500);
      if (!/^[a-z][a-z0-9_]{0,23}$/.test(id) || !/^https:\/\//.test(url)) continue;
      const labels: Record<string, string> = {};
      for (const [l, t] of Object.entries((b.labels ?? {}) as Record<string, unknown>)) if (typeof t === "string" && t.trim()) labels[l.slice(0, 5)] = t.trim().slice(0, 20);
      buttons[id] = { url, labels: Object.keys(labels).length ? labels : { en: id } };
    }
  }
  const playbooks: Playbook[] = Array.isArray(d.playbooks)
    ? (d.playbooks as Array<Record<string, unknown>>).map((p) => ({ name: str(p.name, 60).trim(), enabled: p.enabled !== false, text: str(p.text, 6000).trim() })).filter((p) => p.name && p.text).slice(0, 12)
    : base.playbooks;
  const i = (d.integrations ?? {}) as Record<string, unknown>;
  return {
    id: base.id,
    name: str(d.name, 80).trim() || base.name,
    assistantName: str(d.assistantName, 120).trim() || (str(d.name, 80).trim() ? `${str(d.name, 80).trim()}'s automated assistant` : base.assistantName),
    purpose: str(d.purpose, 300).trim() || base.purpose,
    persona: d.persona !== undefined ? str(d.persona, 8000) : base.persona,
    knowledgeBase: d.knowledgeBase !== undefined ? str(d.knowledgeBase) : base.knowledgeBase,
    buttons: d.buttons !== undefined ? buttons : base.buttons,
    playbooks,
    integrations: { offers: bool(i.offers, base.integrations.offers), listings: bool(i.listings, base.integrations.listings), accountLookup: bool(i.accountLookup, base.integrations.accountLookup), release: bool(i.release, base.integrations.release) },
    tabPrefix: typeof d.tabPrefix === "string" ? d.tabPrefix.replace(/[^A-Za-z0-9_]/g, "").slice(0, 24) : base.tabPrefix,
    contactEmail: str(d.contactEmail, 120) || base.contactEmail,
    languages: Array.isArray(d.languages) ? (d.languages as unknown[]).filter((x): x is string => typeof x === "string").slice(0, 8) : base.languages,
  };
}

export const BRAND_ID = /^[a-z][a-z0-9]{1,19}$/;

/** "Acme Dental" → "acmedental" */
export function brandIdFrom(name: string): string {
  const id = name.toLowerCase().replace(/[^a-z0-9]/g, "").replace(/^[0-9]+/, "").slice(0, 20);
  return BRAND_ID.test(id) ? id : "";
}
