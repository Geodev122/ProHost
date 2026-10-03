import { createHash } from "crypto";
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import "./admin";

/**
 * Per-address send limiter for the unauthenticated sign-in email callables, which
 * otherwise let anyone flood any inbox through our domain. Keyed by a hash of the
 * address (the doc id must not carry PII). Server-only collection: no client rule
 * matches it, so Firestore denies all client access.
 */
export async function takeEmailSendSlot(
  kind: string,
  email: string,
  max = 5,
  windowMs = 60 * 60 * 1000
): Promise<void> {
  const db = getFirestore();
  const key = `${kind}_${createHash("sha256").update(email.toLowerCase().trim()).digest("hex").slice(0, 40)}`;
  const ref = db.collection("email_send_limits").doc(key);
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data();
    const inWindow = data && typeof data.windowStart === "number" && now - data.windowStart < windowMs;
    const count = inWindow ? (data?.count as number) : 0;
    if (count >= max) {
      throw new HttpsError(
        "resource-exhausted",
        "Too many emails requested for this address. Please wait a while and try again."
      );
    }
    tx.set(ref, { windowStart: inWindow ? data?.windowStart : now, count: count + 1 });
  });
}

export function isPlausibleEmail(email: string | undefined): email is string {
  return !!email && email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}
