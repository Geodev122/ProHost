import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser, sendPushToAdmins } from "../lib/push";
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

  const listing = snap.data();
  const ownerId = listing?.ownerId;

  await ref.set({ isVerified: verified, updatedAt: Date.now() }, { merge: true });

  if (ownerId) {
    const title = verified ? "Listing Verified!" : "Verification Status Updated";
    const body = verified
      ? `Your workspace listing "${listing?.title ?? "Workspace"}" has been officially verified by ProHost Admin.`
      : `Your workspace listing "${listing?.title ?? "Workspace"}" verification status was updated.`;
    await sendPushToUser(ownerId, title, body, {
      category: "LISTING_VERIFICATION",
      targetTab: "manage_listings",
      spaceId: spaceId,
    });
  }

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

interface RequestListingVerificationData {
  spaceId?: string;
}

/**
 * Host-callable, self-service sibling to setListingVerification above. The
 * listing's PRO_HOST owner uploads a qualifying document (a signed re-rental
 * authorization, or proof of self-ownership — see
 * SpaceListing.verificationDocUrl's doc comment) then calls this to submit it
 * for review. This does NOT grant the badge itself — only an Admin can, via
 * setListingVerification, after actually looking at the document (Admin
 * Console's Listings Catalog tab, "Pending Review" filter). This function's
 * only real job is validating the submission and notifying every Admin that
 * one is waiting — the grant is a genuine manual review step, not an
 * auto-approval.
 */
export const requestListingVerification = onCall<RequestListingVerificationData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "PRO_HOST" && auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only a Pro Host can request verification for their own listing.");
  }

  const { spaceId } = request.data ?? {};
  if (!spaceId) {
    throw new HttpsError("invalid-argument", "spaceId is required.");
  }

  const ref = getFirestore().collection("workspace_listings").doc(spaceId);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Workspace listing not found.");
  }

  const data = snap.data() ?? {};
  if (auth.token.role !== "ADMIN" && data.ownerId !== auth.uid) {
    throw new HttpsError("permission-denied", "You can only request verification for your own listing.");
  }
  if (!data.verificationDocUrl) {
    throw new HttpsError(
      "failed-precondition",
      "Upload a re-rental authorization statement or proof of ownership first — see 'Get Listing Verified' on your listing."
    );
  }

  await sendPushToAdmins(
    "New Listing Verification Request",
    `${data.title ?? "A listing"} (host: ${data.ownerName ?? auth.token.email ?? auth.uid}) submitted a document for the Listing Verified badge — review it in Admin Console.`,
    {
      category: "LISTING_VERIFICATION_REQUEST",
      targetTab: "admin_console",
      spaceId: spaceId,
    }
  );

  await recordAuditLog({
    actionType: "LISTING_VERIFICATION_REQUESTED",
    details: `${auth.token.email ?? auth.uid} submitted listing #${spaceId} for verification review (${data.verificationDocType ?? "unknown"} document on file).`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "INFO",
  });

  return { ok: true };
});
