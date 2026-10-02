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
  const update: Data = {};
  if (!(await isCodeOwnedBy(data.displayCode, ref.path))) {
    update.displayCode = await mintDisplayCode("L", ref.path);
  }
  const subs = Array.isArray(data.subdivisions) ? (data.subdivisions as Data[]) : [];
  let subsChanged = false;
  const nextSubs: Data[] = [];
  for (const sub of subs) {
    const subPath = `${ref.path}#${String(sub.id ?? "")}`;
    if (sub.id && !(await isCodeOwnedBy(sub.displayCode, subPath))) {
      nextSubs.push({ ...sub, displayCode: await mintDisplayCode("D", subPath) });
      subsChanged = true;
    } else {
      nextSubs.push(sub);
    }
  }
  if (subsChanged) update.subdivisions = nextSubs;
  if (Object.keys(update).length === 0) return false;
  await ref.set(update, { merge: true });
  return true;
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
