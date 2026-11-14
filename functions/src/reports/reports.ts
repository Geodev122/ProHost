import { HttpsError } from "firebase-functions/v2/https";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToAdmins } from "../lib/push";
import "../lib/admin";
import {
  ADMIN_REPORT_ACTIONS,
  AdminReportAction,
  cleanReport,
  isBlockedPair,
  MAX_BLOCKED,
  MAX_REPORTS_PER_DAY,
  ReportInput,
  REVIEW_WINDOW_MS,
  statusAfter,
} from "./reportRules";

/**
 * Report / block (Apple Guideline 1.2). Everything is written here, never by a client:
 *  - `reports/{id}`: admin-readable queue (rules: admin read, no client write). The reported person never learns who reported.
 *  - `user_profiles/{uid}.blockedUserIds` (server-only field, capped) + `user_profiles/{uid}/blocks/{otherUid}` (the owner's list with names).
 * A blocked pair exchanges no booking requests (`blockedBookingProblem`, used by onBookingRequestCreated); the app hides a blocked
 * host's listings from the blocker. Existing bookings are untouched and nobody is notified of a block.
 */

const profile = (uid: string) => getFirestore().collection("user_profiles").doc(uid);

function requireUid(request: { auth?: { uid: string } | null }): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  return uid;
}

async function addBlock(uid: string, targetUid: string): Promise<void> {
  if (uid === targetUid) throw new HttpsError("invalid-argument", "You can't block yourself.");
  const db = getFirestore();
  const [meSnap, targetSnap] = await Promise.all([profile(uid).get(), profile(targetUid).get()]);
  if (!meSnap.exists) throw new HttpsError("failed-precondition", "Finish your profile first.");
  if (!targetSnap.exists) throw new HttpsError("not-found", "This account no longer exists.");
  const current: unknown[] = Array.isArray(meSnap.data()?.blockedUserIds) ? meSnap.data()?.blockedUserIds : [];
  if (current.includes(targetUid)) return;
  if (current.length >= MAX_BLOCKED) throw new HttpsError("resource-exhausted", "Your blocked list is full. Unblock someone first.");
  const target = targetSnap.data() ?? {};
  const batch = db.batch();
  batch.update(profile(uid), { blockedUserIds: FieldValue.arrayUnion(targetUid) });
  batch.set(profile(uid).collection("blocks").doc(targetUid), {
    displayName: typeof target.fullName === "string" ? target.fullName : "",
    displayCode: typeof target.displayCode === "string" ? target.displayCode : "",
    blockedAtMillis: Date.now(),
  });
  await batch.commit();
}

/** The uid a block request is about: given directly, or the owner of a listing. */
async function resolveTargetUid(data: { targetUid?: unknown; listingId?: unknown }): Promise<string> {
  if (typeof data.targetUid === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(data.targetUid)) return data.targetUid;
  if (typeof data.listingId === "string" && /^[A-Za-z0-9_-]{1,128}$/.test(data.listingId)) {
    const snap = await getFirestore().collection("workspace_listings").doc(data.listingId).get();
    const ownerId = snap.data()?.ownerId;
    if (typeof ownerId === "string" && ownerId) return ownerId;
    throw new HttpsError("not-found", "This listing no longer exists.");
  }
  throw new HttpsError("invalid-argument", "Who do you want to block?");
}

export const blockUser = onCall<{ targetUid?: string; listingId?: string }>(async (request) => {
  const uid = requireUid(request);
  await addBlock(uid, await resolveTargetUid(request.data ?? {}));
  return { ok: true };
});

export const unblockUser = onCall<{ targetUid?: string }>(async (request) => {
  const uid = requireUid(request);
  const targetUid = request.data?.targetUid;
  if (typeof targetUid !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(targetUid)) {
    throw new HttpsError("invalid-argument", "Who do you want to unblock?");
  }
  const meSnap = await profile(uid).get();
  if (!meSnap.exists) return { ok: true };
  const batch = getFirestore().batch();
  batch.update(profile(uid), { blockedUserIds: FieldValue.arrayRemove(targetUid) });
  batch.delete(profile(uid).collection("blocks").doc(targetUid));
  await batch.commit();
  return { ok: true };
});

export const reportContent = onCall<ReportInput>(async (request) => {
  const uid = requireUid(request);
  const clean = cleanReport(request.data);
  if (typeof clean === "string") throw new HttpsError("invalid-argument", clean);
  const db = getFirestore();
  const now = Date.now();

  const recent = await db.collection("reports")
    .where("reporterUid", "==", uid).where("createdAtMillis", ">", now - 24 * 60 * 60 * 1000).count().get();
  if (recent.data().count >= MAX_REPORTS_PER_DAY) {
    throw new HttpsError("resource-exhausted", "You've sent several reports today. Please try again tomorrow.");
  }

  let targetUid = clean.targetUid;
  let listingTitle = "";
  let listingCode = "";
  if (clean.listingId) {
    const listing = (await db.collection("workspace_listings").doc(clean.listingId).get()).data();
    if (!listing) throw new HttpsError("not-found", "This listing no longer exists.");
    listingTitle = typeof listing.title === "string" ? listing.title : "";
    listingCode = typeof listing.displayCode === "string" ? listing.displayCode : "";
    if (!targetUid && typeof listing.ownerId === "string") targetUid = listing.ownerId;
  }
  if (targetUid === uid) throw new HttpsError("invalid-argument", "You can't report yourself.");

  const [reporter, target] = await Promise.all([
    profile(uid).get(),
    targetUid ? profile(targetUid).get() : Promise.resolve(null),
  ]);
  const ref = db.collection("reports").doc();
  await ref.set({
    status: "NEW",
    targetType: clean.targetType,
    listingId: clean.listingId,
    listingTitle,
    listingCode,
    targetUid: targetUid ?? null,
    targetCode: target?.data()?.displayCode ?? "",
    bookingId: clean.bookingId,
    reason: clean.reason,
    details: clean.details,
    reporterUid: uid,
    reporterCode: reporter.data()?.displayCode ?? "",
    createdAtMillis: now,
    dueAtMillis: now + REVIEW_WINDOW_MS,
  });

  if (clean.alsoBlock && targetUid) {
    try {
      await addBlock(uid, targetUid);
    } catch (e) {
      logger.warn("reportContent: the report was saved but the block failed", { message: (e as Error).message });
    }
  }
  // No names or free text in the push: the queue in the web console has the detail.
  await sendPushToAdmins("New report", `A ${clean.targetType} was reported (${clean.reason.toLowerCase()}). Review within 24 hours.`, {
    category: "ADMIN_REPORT",
    targetTab: "admin_console",
    reportId: ref.id,
  });
  return { ok: true, reportId: ref.id };
});

/**
 * Records an admin's decision on a report. Hiding a listing and suspending a user stay the existing callables
 * (`setListingSubscriptionActive`, `setAccountSuspended`): the console calls them first, then this, so there is one audit trail.
 */
export const adminResolveReport = onCall<{ reportId?: string; action?: string; note?: string }>(async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Only an Admin can review reports.");
  const { reportId, action, note } = request.data ?? {};
  if (typeof reportId !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(reportId)) {
    throw new HttpsError("invalid-argument", "reportId is required.");
  }
  if (!ADMIN_REPORT_ACTIONS.includes(action as AdminReportAction)) throw new HttpsError("invalid-argument", "Unknown action.");
  const ref = getFirestore().collection("reports").doc(reportId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "This report no longer exists.");
  const status = statusAfter(action as AdminReportAction);
  await ref.update({
    status,
    lastAction: action,
    resolutionNote: typeof note === "string" ? note.trim().slice(0, 500) : "",
    reviewedAtMillis: Date.now(),
    reviewedByEmail: auth.token.email ?? "",
  });
  await recordAuditLog({
    actionType: "REPORT_REVIEWED",
    details: `Report ${reportId} (${snap.data()?.targetType ?? "?"}, ${snap.data()?.reason ?? "?"}) → ${action}`,
    actorEmail: auth.token.email ?? "",
    severity: "SECURE",
  });
  return { ok: true, status };
});

/** For onBookingRequestCreated: the reason a request between a blocked pair is refused, or null. Read errors never block a booking. */
export async function blockedBookingProblem(booking: Record<string, unknown>): Promise<string | null> {
  const specialist = booking.practitionerId;
  const host = booking.ownerId;
  if (typeof specialist !== "string" || typeof host !== "string" || !specialist || !host) return null;
  try {
    const [s, h] = await Promise.all([profile(specialist).get(), profile(host).get()]);
    return isBlockedPair(specialist, s.data()?.blockedUserIds, host, h.data()?.blockedUserIds)
      ? "This host isn't accepting requests from you."
      : null;
  } catch (e) {
    logger.error("blockedBookingProblem: couldn't read the block lists", e);
    return null;
  }
}
