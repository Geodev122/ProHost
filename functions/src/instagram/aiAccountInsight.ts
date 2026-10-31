import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

/**
 * Read-only picture of a ProHost account, looked up ONLY after the person gave their registration email in
 * reply to a question that asked permission for exactly this. The email is self-declared and unverified, so the
 * result steers the agent's guidance and is never recited back to the person.
 */
export interface Insight {
  found: boolean;
  role?: "SPECIALIST" | "PRO_HOST" | "ADMIN" | string;
  premiumActive?: boolean;
  premiumLapsed?: boolean;
  listings: number;
  published: number;
  drafts: number;
  rentals: number;
  lastRentalStatus?: string;
  hasPhoto?: boolean;
  hasWhatsApp?: boolean;
  hasLocation?: boolean;
  daysSinceSignIn?: number;
}

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

const days = (ms: number) => Math.floor(ms / 86_400_000);

export async function lookupInsight(email: string, now: number): Promise<Insight> {
  try {
    const user = await getAuth().getUserByEmail(email.trim().toLowerCase());
    const db = getFirestore();
    const [profileSnap, listSnap, bookSnap] = await Promise.all([
      db.collection("user_profiles").doc(user.uid).get(),
      db.collection("workspace_listings").where("ownerId", "==", user.uid).limit(50).get(),
      db.collection("booking_requests").where("practitionerId", "==", user.uid).limit(20).get(),
    ]);
    const p = profileSnap.data();
    if (!p || p.isDemo === true) return NO_INSIGHT;
    const statuses = listSnap.docs.map((d) => String(d.data().status ?? ""));
    const rentals = bookSnap.docs.map((d) => d.data()).sort((a, b) => Number(b.createdAtMillis ?? 0) - Number(a.createdAtMillis ?? 0));
    const billing = String(p.billingStatus ?? "");
    const lastSignIn = Date.parse(user.metadata.lastSignInTime) || 0;
    return {
      found: true,
      role: String(p.role ?? "SPECIALIST"),
      premiumActive: p.role === "PRO_HOST" && p.isOwnerPackageLapsed !== true && !["EXPIRED", "ON_HOLD", "PAUSED", "REVOKED", "REFUNDED"].includes(billing),
      premiumLapsed: p.isOwnerPackageLapsed === true || ["EXPIRED", "ON_HOLD", "PAUSED"].includes(billing),
      listings: statuses.length,
      published: statuses.filter((s) => s === "ACTIVE").length,
      drafts: statuses.filter((s) => s === "DRAFT").length,
      rentals: rentals.length,
      lastRentalStatus: rentals.length ? String(rentals[0].status ?? "").toLowerCase() : undefined,
      hasPhoto: !!String(p.profilePictureUrl ?? "").trim(),
      hasWhatsApp: /^\+\d{7,15}$/.test(String(p.phone ?? "").replace(/[\s()-]/g, "")),
      hasLocation: !!String(p.country ?? "").trim() && !!String(p.city ?? "").trim(),
      daysSinceSignIn: lastSignIn ? days(now - lastSignIn) : undefined,
    };
  } catch {
    return NO_INSIGHT;
  }
}
