import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * ADMIN-ONLY, RE-RUNNABLE. Stamps isOwnerSuspended: false onto every
 * workspace_listings document that doesn't already have the field.
 *
 * isOwnerSuspended was deliberately never written by the client (so a
 * suspended host couldn't un-hide their own listing by echoing it back
 * false) — only setAccountSuspended.ts ever wrote it, and only for a host
 * who has actually been suspended. That meant almost every listing in the
 * database simply never had the field at all. FirestoreService's non-admin
 * Discovery listener needs a real .where("isOwnerSuspended", "==", false)
 * filter to satisfy firestore.rules' workspace_listings read rule (an
 * unconstrained collection listener can't be proven safe for a non-admin,
 * non-owner caller — see that rule's own comment) — but a Firestore
 * equality filter never matches a document where the field is absent, only
 * one where it's explicitly false. Without this backfill, every
 * pre-existing listing would silently vanish from non-admin Discovery
 * despite never having been suspended.
 *
 * SpaceListing.toFirestoreMap() now writes isOwnerSuspended on every
 * create/update going forward (echoing back the object's own current
 * value, always synced from the live listener — safe, same pattern
 * already used for isVerified/isActiveSubscription), so this backfill
 * only needs to cover documents that predate that change. Idempotent: a
 * document already carrying the field (true or false) is skipped, so
 * re-running after an interruption only touches whoever is still missing
 * it.
 *
 * Meant to be triggered once, manually, via a temporary admin-only
 * control (removed again once confirmed run) — not wired into any
 * regular user flow.
 */
export const backfillWorkspaceListingDefaults = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admin only.");
  }

  const db = getFirestore();
  const allSnap = await db.collection("workspace_listings").get();

  const missing = allSnap.docs.filter((doc) => doc.data().isOwnerSuspended === undefined);

  let backfilled = 0;
  if (missing.length > 0) {
    const bulkWriter = db.bulkWriter();
    missing.forEach((doc) => {
      bulkWriter.set(doc.ref, { isOwnerSuspended: false }, { merge: true });
      backfilled++;
    });
    await bulkWriter.close();
  }

  await recordAuditLog({
    actionType: "WORKSPACE_LISTINGS_BACKFILLED",
    details: `One-time backfill: stamped isOwnerSuspended:false onto ${backfilled} listing(s) that predated the field, out of ${allSnap.size} total.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { backfilled, total: allSnap.size };
});
