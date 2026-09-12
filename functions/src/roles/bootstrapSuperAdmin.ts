import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

export const bootstrapSecret = defineSecret("BOOTSTRAP_SECRET");

const FLAG_DOC = "system_metadata/bootstrap_admin";

/**
 * ONE-TIME ONLY. Grants the ADMIN role to a single target account, gated by
 * a shared secret set via `firebase functions:secrets:set BOOTSTRAP_SECRET`.
 * A Firestore flag document ensures this can only ever succeed once, no
 * matter how many times it's called or by whom, after which every call
 * fails closed.
 *
 * This function is deleted for good in the production-cutover phase once
 * the first Admin claim is confirmed set — it should not exist long-term.
 *
 * POST body: { "secret": "...", "targetEmail": "..." }
 */
export const bootstrapSuperAdmin = onRequest(
  { secrets: [bootstrapSecret] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "POST only" });
      return;
    }

    const db = getFirestore();
    const flagRef = db.doc(FLAG_DOC);
    const flagSnap = await flagRef.get();
    if (flagSnap.exists && flagSnap.data()?.used === true) {
      res.status(410).json({ error: "Bootstrap already used. This path is permanently closed." });
      return;
    }

    const { secret, targetEmail } = req.body ?? {};
    if (typeof secret !== "string" || secret !== bootstrapSecret.value()) {
      res.status(403).json({ error: "Invalid secret." });
      return;
    }
    if (typeof targetEmail !== "string" || !targetEmail.includes("@")) {
      res.status(400).json({ error: "targetEmail is required." });
      return;
    }

    // Resolve the target account BEFORE claiming the one-time flag. This used
    // to claim the flag first and look the user up after — a typo in
    // targetEmail made getUserByEmail throw (unhandled, since nothing here
    // caught it), but the flag was already marked used, permanently closing
    // this bootstrap path with no admin ever actually granted. There is no
    // way to reopen it afterward short of a human editing Firestore directly
    // — exactly the kind of footgun this function exists to avoid needing.
    const adminAuth = getAuth();
    let targetUser;
    try {
      targetUser = await adminAuth.getUserByEmail(targetEmail);
    } catch (e) {
      res.status(404).json({
        error: `No account exists for ${targetEmail}. Nothing was changed — the bootstrap flag is still unused, so this can be retried with the correct email.`,
      });
      return;
    }

    // Claim the flag transactionally, now that the target is confirmed real,
    // so a race between two concurrent valid calls still can't both succeed.
    const claimed = await db.runTransaction(async (tx) => {
      const snap = await tx.get(flagRef);
      if (snap.exists && snap.data()?.used === true) {
        return false;
      }
      tx.set(flagRef, { used: true, usedAt: Date.now(), targetEmail, targetUid: targetUser.uid });
      return true;
    });

    if (!claimed) {
      res.status(410).json({ error: "Bootstrap already used. This path is permanently closed." });
      return;
    }

    await adminAuth.setCustomUserClaims(targetUser.uid, { role: "ADMIN" });

    await db.collection("user_profiles").doc(targetUser.uid).set(
      { role: "ADMIN", updatedAt: Date.now() },
      { merge: true }
    );

    await recordAuditLog({
      actionType: "SUPER_ADMIN_BOOTSTRAPPED",
      details: `One-time bootstrap granted ADMIN to ${targetEmail} (${targetUser.uid}). This path is now permanently closed.`,
      actorEmail: "system@prohost.app",
      severity: "SECURE",
    });

    res.status(200).json({ ok: true, uid: targetUser.uid, email: targetEmail });
  }
);

// Exported only so the production-cutover phase can find/verify this file
// exists before removing it — not used elsewhere.
export const BOOTSTRAP_FLAG_DOC_PATH = FLAG_DOC;
