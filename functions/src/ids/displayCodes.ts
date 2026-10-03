import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getFirestore, DocumentReference } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { isCodeOwnedBy, mintDisplayCode, DisplayCodeKind } from "../lib/displayCode";
import "../lib/admin";

type Data = Record<string, unknown>;

/** Gives a top-level doc a code unless it already has a registered one. */
async function ensureDocCode(ref: DocumentReference, data: Data, kind: DisplayCodeKind): Promise<boolean> {
  if (await isCodeOwnedBy(data.displayCode, ref.path)) return false;
  const code = await mintDisplayCode(kind, ref.path);
  await ref.set({ displayCode: code }, { merge: true });
  return true;
}

/**
 * Listing + every division in one write. The subdivisions array is replaced whole
 * by client saves, so division codes are re-checked on every write; a write that
 * changes nothing is skipped, which is what stops this trigger re-firing forever.
 */
async function ensureListingCodes(ref: DocumentReference, data: Data): Promise<boolean> {
  // Mint outside the transaction (minting reserves the code in its own transaction).
  const listingCode = (await isCodeOwnedBy(data.displayCode, ref.path))
    ? null
    : await mintDisplayCode("L", ref.path);
  const subs = Array.isArray(data.subdivisions) ? (data.subdivisions as Data[]) : [];
  const subCodes: Record<string, string> = {};
  for (const sub of subs) {
    const id = String(sub.id ?? "");
    if (id && !(await isCodeOwnedBy(sub.displayCode, `${ref.path}#${id}`))) {
      subCodes[id] = await mintDisplayCode("D", `${ref.path}#${id}`);
    }
  }
  if (!listingCode && Object.keys(subCodes).length === 0) return false;

  // The host may have saved a newer version while the codes were being minted, and the
  // subdivisions array is rewritten whole — so re-read it here and fill in only the
  // codes that are still missing, by room id, instead of writing back a stale copy.
  return getFirestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return false;
    const current = snap.data() ?? {};
    const update: Data = {};
    if (listingCode && !current.displayCode) update.displayCode = listingCode;
    const currentSubs = Array.isArray(current.subdivisions) ? (current.subdivisions as Data[]) : [];
    let subsChanged = false;
    const nextSubs = currentSubs.map((sub) => {
      const id = String(sub.id ?? "");
      if (id && subCodes[id] && !sub.displayCode) {
        subsChanged = true;
        return { ...sub, displayCode: subCodes[id] };
      }
      return sub;
    });
    if (subsChanged) update.subdivisions = nextSubs;
    if (Object.keys(update).length === 0) return false;
    tx.set(ref, update, { merge: true });
    return true;
  });
}

export const onUserProfileCreatedAssignCode = onDocumentCreated("user_profiles/{uid}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  await ensureDocCode(snap.ref, snap.data(), "U");
});

export const onBookingCreatedAssignCode = onDocumentCreated("booking_requests/{bookingId}", async (event) => {
  const snap = event.data;
  if (!snap) return;
  await ensureDocCode(snap.ref, snap.data(), "B");
});

export const onListingWrittenAssignCodes = onDocumentWritten("workspace_listings/{spaceId}", async (event) => {
  const after = event.data?.after;
  if (!after?.exists) return;
  await ensureListingCodes(after.ref, after.data() ?? {});
});

/**
 * Admin-only one-off: assigns codes to every document created before codes
 * existed. Safe to run repeatedly — documents that already have a registered
 * code are left alone.
 */
export const backfillDisplayCodes = onCall({ timeoutSeconds: 540, memory: "512MiB" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (request.auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can run the display-code backfill.");
  }
  const db = getFirestore();
  const counts = { users: 0, listings: 0, bookings: 0 };

  for (const doc of (await db.collection("user_profiles").get()).docs) {
    if (await ensureDocCode(doc.ref, doc.data(), "U")) counts.users++;
  }
  for (const doc of (await db.collection("workspace_listings").get()).docs) {
    if (await ensureListingCodes(doc.ref, doc.data())) counts.listings++;
  }
  for (const doc of (await db.collection("booking_requests").get()).docs) {
    if (await ensureDocCode(doc.ref, doc.data(), "B")) counts.bookings++;
  }
  logger.info("backfillDisplayCodes done", counts);
  return counts;
});
