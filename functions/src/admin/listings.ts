import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

interface SetListingVerificationData {
  spaceId?: string;
  verified?: boolean;
}

/**
 * Admin-only override of a listing's verified badge. Firestore rules deny
 * every client write to workspace_listings.isVerified, so this replaces
 * ProSpaceRepository.toggleListingVerification's local-only mutation, which
 * never actually reached Firestore at all.
 */
export const setListingVerification = onCall<SetListingVerificationData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can change a listing's verified status.");
  }

  const { spaceId, verified } = request.data ?? {};
  if (!spaceId || typeof verified !== "boolean") {
    throw new HttpsError("invalid-argument", "spaceId and verified (boolean) are required.");
  }

  const ref = getFirestore().collection("workspace_listings").doc(spaceId);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Workspace listing not found.");
  }

  await ref.set({ isVerified: verified, updatedAt: Date.now() }, { merge: true });
  await recordAuditLog({
    actionType: "VERIFICATION_OVERRIDE",
    details: `Admin ${auth.token.email ?? auth.uid} set workspace #${spaceId} verified status to ${verified}`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { ok: true };
});

interface SetListingSubscriptionActiveData {
  spaceId?: string;
  active?: boolean;
}

/**
 * Admin-only override of a listing's subscription-active flag — e.g. to
 * suspend a listing for a policy violation independent of its actual billing
 * state. The billing-driven path (a real subscription payment) still goes
 * through initiateWhishPayment/grantEntitlement, which also write this same
 * field via the Admin SDK; this is the manual override path, not a second
 * source of truth for real payments.
 */
export const setListingSubscriptionActive = onCall<SetListingSubscriptionActiveData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can change a listing's subscription status.");
  }

  const { spaceId, active } = request.data ?? {};
  if (!spaceId || typeof active !== "boolean") {
    throw new HttpsError("invalid-argument", "spaceId and active (boolean) are required.");
  }

  const ref = getFirestore().collection("workspace_listings").doc(spaceId);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Workspace listing not found.");
  }

  await ref.set({ isActiveSubscription: active, updatedAt: Date.now() }, { merge: true });
  await recordAuditLog({
    actionType: "SUBSCRIPTION_STATUS_TOGGLE",
    details: `Admin ${auth.token.email ?? auth.uid} set listing #${spaceId} subscription active status to ${active}`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "WARN",
  });

  return { ok: true };
});
