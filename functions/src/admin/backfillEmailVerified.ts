import { HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { emailVerifiedByUserRecord } from "../auth/emailVerifiedRule";
import "../lib/admin";

/**
 * One-time (idempotent) admin fix: every profile whose Auth account signed up with a verified
 * email or Google gets `emailVerified: true`. Uses update() and skips missing profiles, so it
 * never recreates a deleted account's document.
 */
export const backfillEmailVerified = onCall({ timeoutSeconds: 540 }, async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");

  const db = getFirestore();
  let pageToken: string | undefined;
  let scanned = 0;
  let updated = 0;
  do {
    const page = await getAuth().listUsers(1000, pageToken);
    for (const user of page.users) {
      scanned++;
      if (!emailVerifiedByUserRecord(user)) continue;
      const ref = db.collection("user_profiles").doc(user.uid);
      const snap = await ref.get();
      if (!snap.exists || snap.data()?.emailVerified === true) continue;
      await ref.update({ emailVerified: true, emailVerifiedAt: Date.now() });
      updated++;
    }
    pageToken = page.pageToken;
  } while (pageToken);

  await recordAuditLog({
    actionType: "EMAIL_VERIFIED_BACKFILL",
    details: `Marked ${updated} of ${scanned} accounts email-verified`,
    actorEmail: auth.token.email ?? "admin",
  });
  return { scanned, updated };
});
