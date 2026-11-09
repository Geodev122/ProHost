/**
 * The agent's window onto the app it serves (migration plan P9 step 2, docs/migration/AGENT_ADAPTER.md).
 *
 * The AI agent must not import the host app's modules: a host app registers one implementation (setAppAdapter) and the agent
 * calls it. For ProHost the implementation is functions/src/adapter/prohostAdapter.ts (loaded by index.ts). Moving the agent to
 * its own project later means replacing that file with an HTTPS client of the same interface (OIDC-authenticated).
 * Seams are added here one at a time as the matching import is removed from the agent (`AGENT_SEAMS` in boundary.test.ts shrinks).
 */

/** One pricing phase of a subscription offer (free, percent off, or a fixed price) per audited region. */
export interface OfferPhase { duration: string; recurrenceCount: number; pricing: Record<string, string> }

export interface OfferData {
  offerId: string; state: string; eligibility: string; tags: string[]; phases: OfferPhase[];
  availableIn: Record<string, boolean>; regionCount: number; otherRegions: boolean;
}

export interface OfferBasePlan {
  basePlanId: string; state: string; period: string | null; prices: Record<string, string>;
  availableIn: Record<string, boolean>; regionCount: number; offers: OfferData[];
}

export interface AdminMail { to: string; subject: string; html: string; headers?: Record<string, string> }

export interface AppAdapter {
  /** The app's live subscription catalogue (authoritative prices and offers); throws when unavailable. */
  readOffers(): Promise<{ basePlans: OfferBasePlan[] }>;
  /** Queues an email through the app's mail pipeline (alerts to the app's admin). */
  sendAdminMail(mail: AdminMail): Promise<void>;
  /** HTML-escapes user-supplied text for mail bodies. */
  escapeHtml(text: string): string;
}

let current: AppAdapter | null = null;

/** Called once at start-up by the host app's adapter module. */
export function setAppAdapter(adapter: AppAdapter): void { current = adapter; }

/** The registered adapter; throws a clear error when the host app forgot to register one. */
export function appAdapter(): AppAdapter {
  if (!current) throw new Error("No AppAdapter registered: import adapter/prohostAdapter (functions/src/index.ts does) before using the agent.");
  return current;
}
