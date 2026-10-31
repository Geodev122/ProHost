import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getAuth } from "firebase-admin/auth";
import { cleanUpAccountData } from "../lib/accountCleanup";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * Self-service account deletion — required to exist in-app (not just via a
 * support email) by Apple Guideline 5.1.1(v) and the Google Play User Data
 * policy. Always targets the CALLER's own uid; there is no targetUid
 * parameter, so this can never be used to delete someone else's account —
 * only setAccountSuspended (Admin-only) touches another user's account state.
 *
 * Deliberately does NOT delete booking_requests the caller appears in (only
 * withdraws their still-PENDING requests) — those are the other party's record too (a host's proof a slot was booked,
 * a specialist's record of what they agreed to pay), so this removes the
 * account's own PII and listings while leaving the transaction ledger intact,
 * the same "PII goes, the ledger stays" split most marketplaces use.
 *
 * Idempotent and safe to retry. The Auth account is deleted last; a failed cleanup step is
 * logged and does not stop it (onAuthUserDeleted repeats the cleanup).
 */
export const deleteOwnAccount = onCall({ timeoutSeconds: 300, memory: "512MiB" }, async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const uid = auth.uid;
  const targetUser = await getAuth().getUser(uid).catch(() => null);
  const email = (targetUser?.email ?? "").toLowerCase();
  // A cleanup failure must never block the deletion the person asked for: the Auth account
  // is still deleted below, and onAuthUserDeleted runs the same (idempotent) cleanup again.
  let listings = 0;
  try {
    listings = (await cleanUpAccountData(uid, email)).listings;
  } catch (err) {
    console.error(`deleteOwnAccount: cleanup failed for ${uid}; deleting the account anyway`, err);
  }

  const bestEffort = async (label: string, step: () => Promise<unknown>) => {
    try {
      await step();
    } catch (err) {
      console.warn(`deleteOwnAccount: ${label} failed for ${uid}`, err);
    }
  };

  await bestEffort("audit log", () => recordAuditLog({
    actionType: "ACCOUNT_SELF_DELETED",
    details: `Account ${uid} (${email || targetUser?.phoneNumber || "unknown"}) deleted itself via in-app account deletion, including ${listings} owned listing(s).`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  }));

  // 6. Delete the Auth account last — every step above is safe to retry, so
  // this is the one irreversible action, done only once everything else
  // has actually succeeded. Already-deleted (a retried call) counts as done.
  await getAuth().deleteUser(uid).catch((err: { code?: string; message?: string }) => {
    if (err?.code === "auth/user-not-found") return;
    console.error(`deleteOwnAccount: deleteUser failed for ${uid}`, err);
    throw new HttpsError("internal", "We couldn't delete your account right now. Please try again in a minute.");
  });

  return { ok: true };
});
