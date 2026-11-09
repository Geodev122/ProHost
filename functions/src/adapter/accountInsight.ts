import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { Insight } from "../instagram/appAdapter";

/** ProHost side of AppAdapter.lookupInsight (moved out of instagram/aiAccountInsight.ts, P9 step 2). Read-only. */
const NOT_FOUND: Insight = { found: false, listings: 0, published: 0, drafts: 0, rentals: 0 };

const days = (ms: number) => Math.floor(ms / 86_400_000);

export async function lookupAccountInsight(email: string, now: number): Promise<Insight> {
  try {
    const user = await getAuth().getUserByEmail(email.trim().toLowerCase());
    const db = getFirestore();
    const [profileSnap, listSnap, bookSnap] = await Promise.all([
      db.collection("user_profiles").doc(user.uid).get(),
      db.collection("workspace_listings").where("ownerId", "==", user.uid).limit(50).get(),
      db.collection("booking_requests").where("practitionerId", "==", user.uid).limit(20).get(),
    ]);
    const p = profileSnap.data();
    if (!p || p.isDemo === true) return NOT_FOUND;
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
    return NOT_FOUND;
  }
}
