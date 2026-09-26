import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { setClaimsThenFirestore } from "../lib/roles";
import "../lib/admin";

interface GrantPackageData {
  targetUserId?: string;
  packageId?: string;
  durationDays?: number;
}

/**
 * Admin-only: grants a package plan to a user, upgrading them to PRO_HOST.
 * Sets ownerPackageId, ownerPackageExpiryMillis, and role on the user document.
 * Also reactivates any lapsed listings (isOwnerPackageLapsed: false).
 */
export const grantPackageToUser = onCall<GrantPackageData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can grant packages.");
  }

  const { targetUserId, packageId, durationDays } = request.data ?? {};
  if (!targetUserId || !packageId || !durationDays || durationDays <= 0) {
    throw new HttpsError("invalid-argument", "targetUserId, packageId, and a positive durationDays are required.");
  }

  const adminAuth = getAuth();
  const db = getFirestore();

  let targetUser;
  try {
    targetUser = await adminAuth.getUser(targetUserId);
  } catch {
    // Try looking up by email if the UID lookup fails
    try {
      targetUser = await adminAuth.getUserByEmail(targetUserId);
    } catch {
      throw new HttpsError("not-found", `No user found for '${targetUserId}'.`);
    }
  }

  const expiryMillis = Date.now() + durationDays * 24 * 60 * 60 * 1000;

  try {
    await setClaimsThenFirestore(
      adminAuth,
      targetUser.uid,
      targetUser.customClaims ?? {},
      { ...(targetUser.customClaims ?? {}), role: "PRO_HOST" },
      async () => {
        await db.collection("user_profiles").doc(targetUser.uid).set(
          {
            role: "PRO_HOST",
            ownerPackageId: packageId,
            ownerPackageExpiryMillis: expiryMillis,
            updatedAt: Date.now(),
          },
          { merge: true }
        );

        // Reactivate any previously lapsed listings for this owner
        const ownedListings = await db
          .collection("workspace_listings")
          .where("ownerId", "==", targetUser.uid)
          .get();
        if (!ownedListings.empty) {
          const bulkWriter = db.bulkWriter();
          ownedListings.docs.forEach((doc) => {
            bulkWriter.set(doc.ref, { isOwnerPackageLapsed: false }, { merge: true });
          });
          await bulkWriter.close();
        }
      }
    );
  } catch (err) {
    throw new HttpsError("internal", err instanceof Error ? err.message : "Failed to grant package.");
  }

  await recordAuditLog({
    actionType: "PACKAGE_GRANTED",
    details: `Package '${packageId}' granted to ${targetUser.uid} (${targetUser.email ?? "no email"}) for ${durationDays} days (expires ${new Date(expiryMillis).toISOString()}) by admin ${auth.token.email ?? auth.uid}.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { success: true, targetUid: targetUser.uid, expiryMillis };
});
