/**
 * The website address book: `mailing_list/{id}` (server-only, firestore.rules deny), one document per email
 * address, id = sha256(email)[0:32] so ids carry no PII. Contact requests are also kept one by one in
 * `mailing_messages/{id}`. Campaigns go only to entries with `subscribed == true`.
 */
import { createHash, randomBytes } from "crypto";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import "../lib/admin";

export const LIST = "mailing_list";
export const MESSAGES = "mailing_messages";

export const ROLES = [
  "Hotel", "Medical center", "Clinic", "Training center", "Coworking / business center", "Studio",
  "Office owner", "Property owner", "Professional (renting space)", "Other",
] as const;

export function entryId(email: string): string {
  return createHash("sha256").update(email.toLowerCase().trim()).digest("hex").slice(0, 32);
}

export const clip = (v: unknown, max: number): string => String(v ?? "").replace(/\s+/g, " ").trim().slice(0, max);
export const clipText = (v: unknown, max: number): string =>
  String(v ?? "").replace(/\r\n/g, "\n").replace(/\n{3,}/g, "\n\n").trim().slice(0, max);

export interface Contact {
  email: string;
  name?: string;
  phone?: string;
  organization?: string;
  role?: string;
  message?: string;
  subscribe: boolean;     // true = opt in now; false = leave the current choice as it is
  source: "contact" | "subscribe" | "partner" | "insta_story" | "instagram_dm";
  /** Admin-side labels kept on the address-book entry and on the stored message, e.g. ["partner", "strategic-sponsor"]. */
  tags?: string[];
}

/** Creates or updates the address-book entry; never downgrades an existing subscription. */
export async function upsertContact(c: Contact): Promise<{ id: string; subscribed: boolean }> {
  const db = getFirestore();
  const email = c.email.toLowerCase().trim();
  const id = entryId(email);
  const ref = db.collection(LIST).doc(id);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const cur = snap.data() ?? {};
    const now = FieldValue.serverTimestamp();
    const subscribed = c.subscribe || cur.subscribed === true;
    const update: Record<string, unknown> = {
      email,
      updatedAt: now,
      sources: FieldValue.arrayUnion(c.source),
      ...(c.tags?.length ? { tags: FieldValue.arrayUnion(...c.tags) } : {}),
      subscribed,
      unsubToken: cur.unsubToken ?? randomBytes(18).toString("base64url"),
    };
    if (!snap.exists) update.createdAt = now;
    for (const k of ["name", "phone", "organization", "role"] as const) if (c[k]) update[k] = c[k];
    if (c.subscribe && cur.subscribed !== true) {
      update.subscribedAt = now;
      update.unsubscribedAt = FieldValue.delete();
    }
    if (c.message) {
      update.lastMessage = c.message;
      update.lastMessageAt = now;
      update.messagesCount = FieldValue.increment(1);
      tx.set(db.collection(MESSAGES).doc(), {
        entryId: id, email, name: c.name ?? "", phone: c.phone ?? "", organization: c.organization ?? "",
        role: c.role ?? "", message: c.message, createdAt: now, source: c.source, tags: c.tags ?? [],
      });
    }
    tx.set(ref, update, { merge: true });
    return { id, subscribed };
  });
}

/** Unsubscribes by the per-entry token. Returns the masked address, or null for an unknown token. */
export async function unsubscribeByToken(token: string): Promise<string | null> {
  if (!/^[A-Za-z0-9_-]{16,64}$/.test(token)) return null;
  const db = getFirestore();
  const q = await db.collection(LIST).where("unsubToken", "==", token).limit(1).get();
  if (q.empty) return null;
  const doc = q.docs[0];
  if (doc.data().subscribed !== false) {
    await doc.ref.update({ subscribed: false, unsubscribedAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  }
  const email = String(doc.data().email ?? "");
  const at = email.indexOf("@");
  return at > 0 ? `${email[0]}***${email.slice(at)}` : "your address";
}

export const iso = (v: unknown): string => (v instanceof Timestamp ? v.toDate().toISOString() : "");
