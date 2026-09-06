import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

interface RevokeProHostRoleData {
  targetUid?: string;
}

/**
 * The downgrade path PRO_HOST never had — once granted (exclusively via a
 * settled Whish OWNER_PACKAGE/PAYG_LISTING payment, see grantEntitlement()),
 * a host previously only ever fell back to SPECIALIST passively, by letting
 * ownerPackageExpiryMillis lapse; there was no explicit Admin action to
 * revoke it outright (e.g. for a policy violation). Admin-only.
 *
 * Also resets the entitlement fields back to the free tier (so a revoked
 * host can't keep publishing under their old package) and marks every
 * listing they've already published as inactive/expired
 * (isActiveSubscription: false) — the same flag setListingSubscriptionActive
 * uses, so it renders with the existing "Expired" badge and is excluded by
 * the specialist's own "active subscription only" Discovery filter, though
 * (unlike account suspension's isOwnerSuspended) it doesn't hard-hide the
 * listing from a default Discovery browse — see AdminConsoleScreen's revoke
 * confirmation copy for the exact wording shown to the admin. Does NOT
 * delete the listings — same "kept on file, not erased" reasoning as account
 * suspension.
 */
export const revokeProHostRole = onCall<RevokeProHostRoleData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can revoke the Pro Host role.");
  }

  const { targetUid } = request.data ?? {};
  if (!targetUid) {
    throw new HttpsError("invalid-argument", "targetUid is required.");
  }

  const adminAuth = getAuth();
  const targetUser = await adminAuth.getUser(targetUid);
  if (targetUser.customClaims?.role !== "PRO_HOST") {
    throw new HttpsError("failed-precondition", "This account does not currently hold the Pro Host role.");
  }

  await adminAuth.setCustomUserClaims(targetUid, { ...targetUser.customClaims, role: "SPECIALIST" });

  const db = getFirestore();
  await db.collection("user_profiles").doc(targetUid).set(
    {
      role: "SPECIALIST",
      ownerPackageTier: "PAY_AS_YOU_GO",
      ownerPackageExpiryMillis: null,
      updatedAt: Date.now(),
    },
    { merge: true }
  );

  const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", targetUid).get();
  if (!ownedListings.empty) {
    const batch = db.batch();
    ownedListings.docs.forEach((doc) => {
      batch.set(doc.ref, { isActiveSubscription: false }, { merge: true });
    });
    await batch.commit();
  }

  await recordAuditLog({
    actionType: "PRO_HOST_ROLE_REVOKED",
    details: `Pro Host role revoked from ${targetUid} (${targetUser.email ?? "no email"}) by admin ${auth.token.email ?? auth.uid}; ${ownedListings.size} listing(s) deactivated.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { ok: true, targetUid };
});
