/**
 * Shared Play Billing helpers used by both the RTDN Pub/Sub handler
 * (playBillingRtdn.ts) and the client-callable restore flow
 * (verifyAndRestorePurchase.ts). Keeping them here avoids circular imports
 * and ensures both paths use identical grant/revoke semantics.
 */
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions/v2";
import { google } from "googleapis";
import { recordAuditLog } from "../lib/auditLog";
import { setClaimsThenFirestore } from "../lib/roles";
import { validateListingForPublish, WorkspaceListingDoc } from "../listings/publishValidation";
import { UNLIMITED_GRANT_PLAN_ID } from "../lib/packagePlans";
import "../lib/admin";

export const PACKAGE_NAME = "app.geonajjar.prohost";

export async function getPlayPublisher() {
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return google.androidpublisher({ version: "v3", auth });
}

/** Fetches live subscription details from the Play Developer API. */
export async function queryPlaySubscription(productId: string, token: string) {
  const publisher = await getPlayPublisher();
  const { data } = await publisher.purchases.subscriptions.get({
    packageName: PACKAGE_NAME,
    subscriptionId: productId,
    token,
  });
  return data;
}

/**
 * Acknowledges a purchase server-side. Play auto-refunds purchases left
 * unacknowledged for 3 days; the app also acknowledges, but may be killed
 * right after purchase or never opened (e.g. resubscribing from Play Store).
 */
export async function acknowledgeIfNeeded(
  productId: string,
  token: string,
  acknowledgementState: number | null | undefined,
  logTag = "billing"
) {
  if (acknowledgementState !== 0) return;
  try {
    const publisher = await getPlayPublisher();
    await publisher.purchases.subscriptions.acknowledge({
      packageName: PACKAGE_NAME,
      subscriptionId: productId,
      token,
      requestBody: {},
    });
    logger.info(`${logTag}: acknowledged product=${productId}`);
  } catch (e) {
    logger.warn(`${logTag}: acknowledge failed for product=${productId}`, e);
  }
}

/**
 * Grants (or extends) the Pro Host subscription for [uid] in Firestore.
 * Uses Play's own [expiryTimeMillis] as the canonical validity source so the
 * backend stays in sync with what Google actually charged for.
 *
 * Also stamps isOwnerPackageLapsed=false on ALL owner listings — not just those
 * with the field already set to true — so legacy documents created before the
 * field was introduced are also fixed and appear in Discovery after a renewal.
 */
export async function grantSubscription(
  uid: string,
  planId: string,
  expiryTimeMillis: number,
  orderId: string
): Promise<void> {
  const db = getFirestore();
  const auth = getAuth();
  const now = Date.now();

  const userRef = db.collection("user_profiles").doc(uid);
  const userSnap = await userRef.get();
  const userData = userSnap.data();

  // Keep the later of: Play's expiry vs any still-valid current expiry on the
  // same plan — prevents a RENEWED notification from shrinking an already-extended term.
  const currentExpiry = userData?.ownerPackageExpiryMillis as number | undefined;
  // An admin's complimentary unlimited grant outranks any paid plan; a Play
  // purchase/renewal must not downgrade it to a limited, expiring package.
  if (
    userData?.ownerPackageId === UNLIMITED_GRANT_PLAN_ID &&
    typeof currentExpiry === "number" &&
    currentExpiry > now
  ) {
    logger.info(`grantSubscription: uid=${uid} holds an admin unlimited grant; not replacing it with ${planId}`);
    return;
  }
  const isSameActivePlan =
    userData?.ownerPackageId === planId &&
    typeof currentExpiry === "number" &&
    currentExpiry > now;
  const newExpiry = isSameActivePlan ? Math.max(expiryTimeMillis, currentExpiry) : expiryTimeMillis;

  await userRef.set(
    { ownerPackageId: planId, ownerPackageExpiryMillis: newExpiry, expiryWarningSent: false, updatedAt: now },
    { merge: true }
  );

  // Promote SPECIALIST → PRO_HOST if needed (idempotent)
  const authUser = await auth.getUser(uid);
  const currentRole = authUser.customClaims?.role;
  if (currentRole !== "ADMIN" && currentRole !== "PRO_HOST") {
    await auth.setCustomUserClaims(uid, { ...authUser.customClaims, role: "PRO_HOST" });
    await userRef.set({ role: "PRO_HOST", proHostUpgradedAtMillis: Date.now() }, { merge: true });
    await recordAuditLog({
      actionType: "ROLE_PROMOTED_PRO_HOST",
      details: `uid=${uid} promoted to PRO_HOST via Google Play subscription (order ${orderId}).`,
      actorEmail: "play-billing@system.prohost.app",
      severity: "SECURE",
    });
  }

  // Stamp isOwnerPackageLapsed=false on ALL owner listings.
  // The condition `d.isOwnerPackageLapsed !== false` catches three cases:
  //   - true  → a previously lapsed listing being restored
  //   - undefined/absent → a legacy document written before this field existed
  //     (would have been excluded from Discovery by the whereEqualTo("isOwnerPackageLapsed", false) query)
  //   - false → already correct; skip to avoid a needless write
  const allListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
  if (!allListings.empty) {
    const bw = db.bulkWriter();
    let restoredCount = 0;
    allListings.docs.forEach((doc) => {
      if (doc.data().isOwnerPackageLapsed !== false) {
        restoredCount++;
        bw.set(doc.ref, { isOwnerPackageLapsed: false }, { merge: true });
      }
    });
    await bw.close();
    if (restoredCount > 0) {
      logger.info(`grantSubscription: stamped isOwnerPackageLapsed=false on ${restoredCount} listing(s) for uid=${uid}`);
    }
  }

  // Auto-publish a draft listing that was saved when the host was redirected to
  // the payment sheet — the client writes pendingPlayPublishDraftId to
  // user_profiles before the Play sheet opens so we can pick it up here.
  const pendingDraftId = userData?.pendingPlayPublishDraftId as string | undefined;
  if (pendingDraftId) {
    try {
      const draftRef = db.collection("workspace_listings").doc(pendingDraftId);
      const draftSnap = await draftRef.get();
      if (draftSnap.exists) {
        const draftData = draftSnap.data() ?? {};
        if (draftData.ownerId === uid && draftData.status === "DRAFT") {
          const problems = validateListingForPublish(draftData as WorkspaceListingDoc);
          if (problems.length > 0) {
            await draftRef.set({ publishBlockedReasons: problems, updatedAt: now }, { merge: true });
            logger.warn(`grantSubscription: draft ${pendingDraftId} NOT auto-published for uid=${uid} — missing: ${problems.join(", ")}`);
          } else {
            await draftRef.set(
              { status: "ACTIVE", isOwnerPackageLapsed: false, publishBlockedReasons: [], updatedAt: now },
              { merge: true }
            );
            logger.info(`grantSubscription: auto-published draft ${pendingDraftId} for uid=${uid}`);
          }
          await userRef.set({ pendingPlayPublishDraftId: null }, { merge: true });
        }
      }
    } catch (e) {
      logger.warn(`grantSubscription: failed to auto-publish draft for uid=${uid}:`, e);
    }
  }

  await recordAuditLog({
    actionType: "PLAY_BILLING_SUBSCRIPTION_GRANTED",
    details: `Plan ${planId} granted for uid=${uid}, order=${orderId}, expires=${new Date(newExpiry).toISOString()}.`,
    actorEmail: "play-billing@system.prohost.app",
    severity: "SECURE",
  });
}

/**
 * Revokes Pro Host access immediately (refund, hard expiry, or hold).
 * Skips if [uid]'s current plan is already different — prevents an RTDN for
 * an old subscription from revoking a freshly-purchased upgrade.
 */
export async function revokeSubscription(
  uid: string,
  planId: string,
  orderId: string,
  pushMessage: string
): Promise<void> {
  const db = getFirestore();
  const auth = getAuth();
  const now = Date.now();

  const userSnap = await db.collection("user_profiles").doc(uid).get();
  const userData = userSnap.data();
  if (userData?.ownerPackageId !== planId) {
    logger.info(`revokeSubscription uid=${uid}: current plan (${userData?.ownerPackageId}) != ${planId}, skipping`);
    return;
  }

  const authUser = await auth.getUser(uid);
  const isProHost = authUser.customClaims?.role === "PRO_HOST";

  if (isProHost) {
    await setClaimsThenFirestore(
      auth,
      uid,
      authUser.customClaims,
      { ...authUser.customClaims, role: "SPECIALIST" },
      async () => {
        await db.collection("user_profiles").doc(uid).set(
          { role: "SPECIALIST", ownerPackageId: null, ownerPackageExpiryMillis: null, updatedAt: now },
          { merge: true }
        );
      }
    );

    const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
    if (!ownedListings.empty) {
      const bw = db.bulkWriter();
      ownedListings.docs.forEach((doc) => bw.set(doc.ref, { isOwnerPackageLapsed: true }, { merge: true }));
      await bw.close();
    }

    const { sendPushToUser } = await import("../lib/push");
    await sendPushToUser(uid, "Subscription Ended", pushMessage, {
      category: "PACKAGE_EXPIRED",
      targetTab: "owner_subscriptions",
    });
  } else {
    await db.collection("user_profiles").doc(uid).set(
      { ownerPackageId: null, ownerPackageExpiryMillis: null },
      { merge: true }
    );
  }

  await recordAuditLog({
    actionType: "PLAY_BILLING_SUBSCRIPTION_REVOKED",
    details: `Plan ${planId} revoked for uid=${uid}, order=${orderId}. Reason: ${pushMessage}`,
    actorEmail: "play-billing@system.prohost.app",
    severity: "WARN",
  });
}
