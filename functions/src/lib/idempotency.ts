import type { Firestore, Transaction } from "firebase-admin/firestore";

/**
 * Cloud Functions v2 Firestore triggers (onDocumentCreated/onDocumentUpdated)
 * are delivered at-least-once — Google's own documentation is explicit that a
 * transient failure can cause the same underlying event to be redelivered
 * with the same event.id. That's harmless for most trigger side effects
 * (re-writing the same denormalized field twice is a no-op), but
 * consumePaygCreditIfNeeded (listingCountTracker.ts) permanently spends one of
 * a host's paid PAYG credits — running it twice for a single real publish
 * would silently overcharge a credit for nothing, with no way for the host to
 * even notice why they ran out early.
 *
 * Records eventId in a small marker collection, checked and written inside
 * the SAME Firestore transaction as the real side effect it guards, so a
 * redelivery is detected and skipped atomically rather than racing the first
 * attempt's own write (checking-then-writing outside a transaction would
 * itself reintroduce exactly the kind of race this exists to close). Returns
 * true if this event was already processed (the caller should do nothing
 * else in this transaction), false if this is genuinely the first time.
 */
export async function alreadyProcessed(tx: Transaction, db: Firestore, eventId: string): Promise<boolean> {
  const ref = db.collection("processed_trigger_events").doc(eventId);
  const snap = await tx.get(ref);
  if (snap.exists) return true;
  tx.set(ref, { processedAt: Date.now() });
  return false;
}
