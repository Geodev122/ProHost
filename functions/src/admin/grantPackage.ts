import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getAuth, UserRecord } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { setClaimsThenFirestore } from "../lib/roles";
import {
  ensureUnlimitedGrantPlan,
  getPackagePlans,
  LIFETIME_EXPIRY_MILLIS,
  UNLIMITED_GRANT_PLAN_ID,
} from "../lib/packagePlans";
import "../lib/admin";

interface GrantPackageData {
  /** UID the admin verified via lookupUserForGrant. */
  targetUid?: string;
  /** Legacy (older admin builds): UID or email. */
  targetUserId?: string;
  packageId?: string;
  durationDays?: number;
  /** Unlimited listings, never expires — uses the reserved UNLIMITED_GRANT_PLAN_ID plan. */
  unlimited?: boolean;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_DURATION_DAYS = 3650;

async function resolveTarget(targetUid?: string, targetUserId?: string): Promise<UserRecord> {
  const adminAuth = getAuth();
  if (targetUid) {
    try {
      return await adminAuth.getUser(targetUid);
    } catch {
      throw new HttpsError("not-found", "That account no longer exists.");
    }
  }
  if (targetUserId) {
    try {
      return await adminAuth.getUser(targetUserId);
    } catch {
      try {
        return await adminAuth.getUserByEmail(targetUserId.trim().toLowerCase());
      } catch {
        throw new HttpsError("not-found", `No user found for '${targetUserId}'.`);
      }
    }
  }
  throw new HttpsError("invalid-argument", "targetUid is required.");
}

/**
 * Admin-only: grants a package and the PRO_HOST role (an ADMIN keeps ADMIN).
 * Writes the same entitlement fields as a Play purchase (ownerPackageId /
 * ownerPackageExpiryMillis), so firestore.rules' hasActivePackage(),
 * expirePackages and every client screen treat it identically, and restores
 * listings previously hidden by expiry or a Pro Host revocation.
 */
export const grantPackageToUser = onCall<GrantPackageData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can grant packages.");
  }

  const { targetUid, targetUserId, unlimited } = request.data ?? {};
  let packageId = request.data?.packageId;
  let expiryMillis: number;

  if (unlimited === true) {
    await ensureUnlimitedGrantPlan();
    packageId = UNLIMITED_GRANT_PLAN_ID;
    expiryMillis = LIFETIME_EXPIRY_MILLIS;
  } else {
    const durationDays = request.data?.durationDays;
    if (!packageId || typeof durationDays !== "number" || !Number.isInteger(durationDays) ||
        durationDays <= 0 || durationDays > MAX_DURATION_DAYS) {
      throw new HttpsError(
        "invalid-argument",
        `packageId and a whole-number durationDays between 1 and ${MAX_DURATION_DAYS} are required.`
      );
    }
    // Granting a plan that isn't in the catalog (or is disabled) would show as
    // granted while firestore.rules still blocks every publish.
    const plan = (await getPackagePlans())[packageId];
    if (!plan) {
      throw new HttpsError("not-found", `Package '${packageId}' does not exist in the catalog.`);
    }
    if (!plan.isEnabled) {
      throw new HttpsError("failed-precondition", `Package '${plan.name}' is disabled. Enable it first.`);
    }
    expiryMillis = Date.now() + durationDays * DAY_MS;
  }

  const targetUser = await resolveTarget(targetUid, targetUserId);
  if (targetUser.disabled) {
    throw new HttpsError("failed-precondition", "This account is disabled in Firebase Auth.");
  }

  const db = getFirestore();
  const profileRef = db.collection("user_profiles").doc(targetUser.uid);
  const profileSnap = await profileRef.get();
  if (!profileSnap.exists) {
    throw new HttpsError(
      "failed-precondition",
      "This user hasn't finished registration yet (no profile). Ask them to complete sign-up first."
    );
  }

  const previousClaims = targetUser.customClaims ?? {};
  const newRole = previousClaims.role === "ADMIN" ? "ADMIN" : "PRO_HOST";
  const isPromotion = newRole === "PRO_HOST" && previousClaims.role !== "PRO_HOST";
  const now = Date.now();
  let restoredListings = 0;

  try {
    await setClaimsThenFirestore(
      getAuth(),
      targetUser.uid,
      previousClaims,
      { ...previousClaims, role: newRole },
      async () => {
        await profileRef.set(
          {
            role: newRole,
            ownerPackageId: packageId,
            ownerPackageExpiryMillis: expiryMillis,
            expiryWarningSent: false,
            updatedAt: now,
            ...(isPromotion ? { proHostUpgradedAtMillis: now } : {}),
          },
          { merge: true }
        );

        const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", targetUser.uid).get();
        if (!ownedListings.empty) {
          const bulkWriter = db.bulkWriter();
          ownedListings.docs.forEach((doc) => {
            const d = doc.data();
            if (d.isOwnerPackageLapsed === true || d.isActiveSubscription === false) {
              restoredListings++;
              bulkWriter.set(doc.ref, { isOwnerPackageLapsed: false, isActiveSubscription: true }, { merge: true });
            }
          });
          await bulkWriter.close();
        }
      }
    );
  } catch (err) {
    throw new HttpsError("internal", err instanceof Error ? err.message : "Failed to grant package.");
  }

  if (isPromotion) {
    await recordAuditLog({
      actionType: "ROLE_PROMOTED_PRO_HOST",
      details: `uid=${targetUser.uid} promoted to PRO_HOST via admin grant by ${auth.token.email ?? auth.uid}.`,
      actorEmail: auth.token.email ?? "system@prohost.app",
      severity: "SECURE",
    });
  }

  const expiryText = unlimited === true ? "never expires" : `expires ${new Date(expiryMillis).toISOString()}`;
  await recordAuditLog({
    actionType: "PACKAGE_GRANTED",
    details: `Package '${packageId}' granted to ${targetUser.uid} (${targetUser.email ?? "no email"}), role ${newRole}, ${expiryText}, ${restoredListings} listing(s) restored, by admin ${auth.token.email ?? auth.uid}.`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return {
    success: true,
    targetUid: targetUser.uid,
    role: newRole,
    packageId,
    expiryMillis,
    restoredListings,
  };
});
