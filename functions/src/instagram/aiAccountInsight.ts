import { Insight, appAdapter } from "./appAdapter";
export type { Insight } from "./appAdapter";

/**
 * Read-only picture of a host-app account: the host app's adapter looks it up ONLY after the person gave their registration email in
 * reply to a question that asked permission for exactly this. The email is unverified, so the result steers the agent's guidance and is never recited back.
 */
export const NO_INSIGHT: Insight = { found: false, listings: 0, published: 0, drafts: 0, rentals: 0 };

/** Compact, trusted context block for the model. Never contains names, titles, dates or exact counts to repeat. */
export function formatInsight(i: Insight): string {
  if (!i.found || i.role === "ADMIN") return "ACCOUNT LOOKUP: no ProHost account is linked to that email (or it cannot be used). Treat them as new: say you could not match it, never reveal which is true, and guide them to download and register.";
  const bits: string[] = [`role ${i.role === "PRO_HOST" ? "Pro Host (can publish)" : "Specialist (rents rooms; cannot publish yet)"}`];
  bits.push(i.premiumActive ? "Premium active" : i.premiumLapsed ? "Premium lapsed (renewal needed to publish)" : "no Premium");
  bits.push(i.listings ? `has listings (${i.published ? "some published" : "none published"}${i.drafts ? ", a draft waiting to be finished" : ""})` : "no listings yet");
  bits.push(i.rentals ? `rents rooms as a tenant (latest request ${i.lastRentalStatus ?? "recorded"})` : "no rentals yet");
  const missing = [!i.hasPhoto && "profile photo", !i.hasWhatsApp && "WhatsApp number", !i.hasLocation && "country and city"].filter(Boolean);
  if (missing.length) bits.push(`profile missing: ${missing.join(", ")} (needed before publishing / sending requests)`);
  if (i.daysSinceSignIn !== undefined) bits.push(i.daysSinceSignIn > 30 ? "has not opened the app for a while" : "uses the app recently");
  return `ACCOUNT LOOKUP (private, from the server; the email is unverified so NEVER repeat these facts back, only use them to choose the right next step, in coarse friendly words):\n- ${bits.join("\n- ")}`;
}

/** Short tag stored on the thread for the owner; no personal data. */
export function insightTag(i: Insight): string {
  if (!i.found) return "no-account";
  return [i.role ?? "?", i.premiumActive ? "premium" : i.premiumLapsed ? "lapsed" : "free", i.listings ? "has-listing" : "no-listing", i.rentals ? "tenant" : "no-rentals"].join(",");
}

/** Looks the account up through the host app's adapter (read-only; the person gave the email in reply to a consent question). */
export async function lookupInsight(email: string, now: number): Promise<Insight> {
  try { return await appAdapter().lookupInsight(email, now); } catch { return NO_INSIGHT; }
}
