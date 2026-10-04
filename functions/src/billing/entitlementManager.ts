/**
 * EntitlementManager: the only code that turns billing state into the Pro Host role.
 *
 * specialist → prohost when Google Play reports an active subscription (via
 * subscriptionService.syncSubscription) or an admin forces the upgrade; prohost →
 * specialist when Play reports it expired, revoked, refunded, on hold or paused.
 * The client never sets a role.
 *
 * user_profiles mirror (server-only; firestore.rules protects them):
 *   ownerPackageId / ownerPackageExpiryMillis — read by firestore.rules hasActivePackage(),
 *     listing gating and every screen; the base plan id ("pro-montly"/"pro-yearly") or
 *     "admin_forced" (lifetime expiry).
 *   entitlementSource, billingStatus, subscriptionExpiry, subscriptionPlatform,
 *   subscriptionId (subscriptions doc id), lastPurchaseToken.
 */
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions/v2";
import { recordAuditLog } from "../lib/auditLog";
import { setClaimsThenFirestore } from "../lib/roles";
import { validateListingForPublish, WorkspaceListingDoc } from "../listings/publishValidation";
import {
  ADMIN_FORCED_PLAN_ID,
  BillingStatus,
  EntitlementSource,
  LEGACY_UNLIMITED_GRANT_PLAN_ID,
  LIFETIME_EXPIRY_MILLIS,
  grantsAccess,
  planLabel,
} from "./playCatalog";
import "../lib/admin";

export interface GrantParams {
  source: EntitlementSource;
  planId: string;
  expiryMillis: number;
  orderId: string;
  status?: BillingStatus;
  subscriptionId?: string | null;
  purchaseToken?: string | null;
}

function isForced(data: FirebaseFirestore.DocumentData | undefined): boolean {
  return data?.entitlementSource === "admin_forced" ||
    data?.ownerPackageId === ADMIN_FORCED_PLAN_ID ||
    data?.ownerPackageId === LEGACY_UNLIMITED_GRANT_PLAN_ID;
}

/**
 * Grants (or extends) Pro Host for [uid]: entitlement fields, PRO_HOST claim + role,
 * listings restored from a lapse, and the draft parked before checkout auto-published.
 * Re-syncing an unchanged subscription only refreshes the billing mirror.
 */
export async function grantProHost(uid: string, p: GrantParams): Promise<void> {
  const db = getFirestore();
  const auth = getAuth();
  const now = Date.now();
  const userRef = db.collection("user_profiles").doc(uid);
  const userData = (await userRef.get()).data();

  const billingMirror = p.source === "admin_forced"
    ? { entitlementSource: "admin_forced", billingStatus: "ACTIVE", subscriptionPlatform: "admin", subscriptionExpiry: LIFETIME_EXPIRY_MILLIS }
    : {
      entitlementSource: "google_play",
      billingStatus: p.status ?? "ACTIVE",
      subscriptionPlatform: "android",
      subscriptionExpiry: p.expiryMillis,
      subscriptionId: p.subscriptionId ?? null,
      ...(p.purchaseToken ? { lastPurchaseToken: p.purchaseToken } : {}),
    };

  // A forced upgrade outranks a Play purchase: never shorten it to an expiring plan.
  if (p.source === "google_play" && isForced(userData)) {
    logger.info(`grantProHost: uid=${uid} holds a forced upgrade; recording Play state only`);
    await userRef.set({ lastPurchaseToken: p.purchaseToken ?? null, updatedAt: now }, { merge: true });
    return;
  }

  const authUser = await auth.getUser(uid);
  const currentRole = authUser.customClaims?.role;
  const unchanged = currentRole === "PRO_HOST" &&
    userData?.ownerPackageId === p.planId &&
    userData?.ownerPackageExpiryMillis === p.expiryMillis &&
    userData?.entitlementSource === p.source;
  await userRef.set(
    { ownerPackageId: p.planId, ownerPackageExpiryMillis: p.expiryMillis, ...billingMirror, updatedAt: now,
      ...(unchanged ? {} : { expiryWarningSent: false }) },
    { merge: true }
  );
  if (unchanged) return;

  if (currentRole !== "ADMIN" && currentRole !== "PRO_HOST") {
    await auth.setCustomUserClaims(uid, { ...authUser.customClaims, role: "PRO_HOST" });
    await userRef.set({ role: "PRO_HOST", proHostUpgradedAtMillis: now }, { merge: true });
    await recordAuditLog({
      actionType: "ROLE_PROMOTED_PRO_HOST",
      details: `uid=${uid} promoted to PRO_HOST via ${p.source === "admin_forced" ? "admin Force Upgrade" : `Google Play (order ${p.orderId})`}.`,
      actorEmail: "play-billing@system.prohost.app",
      severity: "SECURE",
    });
  }

  // Listings hidden by a lapse (or legacy docs without the field) come back.
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
    if (restoredCount > 0) logger.info(`grantProHost: restored ${restoredCount} listing(s) for uid=${uid}`);
  }

  // The client parks pendingPlayPublishDraftId before the Play sheet opens.
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
            logger.warn(`grantProHost: draft ${pendingDraftId} NOT auto-published for uid=${uid} — missing: ${problems.join(", ")}`);
          } else {
            await draftRef.set(
              { status: "ACTIVE", isOwnerPackageLapsed: false, publishBlockedReasons: [], updatedAt: now },
              { merge: true }
            );
            logger.info(`grantProHost: auto-published draft ${pendingDraftId} for uid=${uid}`);
          }
          await userRef.set({ pendingPlayPublishDraftId: null }, { merge: true });
        }
      }
    } catch (e) {
      logger.warn(`grantProHost: failed to auto-publish draft for uid=${uid}:`, e);
    }
  }

  await recordAuditLog({
    actionType: p.source === "admin_forced" ? "PRO_HOST_FORCE_UPGRADE" : "PLAY_BILLING_SUBSCRIPTION_GRANTED",
    details: `${planLabel(p.planId)} (${p.planId}) for uid=${uid}, order=${p.orderId}, expires=${new Date(p.expiryMillis).toISOString()}.`,
    actorEmail: "play-billing@system.prohost.app",
    severity: "SECURE",
  });
}

export interface RemoveParams {
  status: BillingStatus;
  reason: string;
  pushMessage: string;
  /** The subscription that ended; a different current subscription (upgrade) is left alone. */
  subscriptionId?: string | null;
}

/**
 * Drops [uid] to specialist after Play reports the subscription ended. Never touches a
 * forced upgrade, and never reacts to an old subscription after the user moved to a new
 * one (monthly → yearly replaces the purchase token).
 */
export async function removeProHost(uid: string, p: RemoveParams): Promise<boolean> {
  const db = getFirestore();
  const auth = getAuth();
  const now = Date.now();
  const userRef = db.collection("user_profiles").doc(uid);
  const userData = (await userRef.get()).data();

  if (isForced(userData)) {
    logger.info(`removeProHost uid=${uid}: forced upgrade, ignoring ${p.status}`);
    return false;
  }
  const current = userData?.subscriptionId as string | null | undefined;
  if (p.subscriptionId && current && current !== p.subscriptionId) {
    logger.info(`removeProHost uid=${uid}: ${p.status} is for a replaced subscription, skipping`);
    return false;
  }
  if (!userData?.ownerPackageId) {
    await userRef.set({ billingStatus: p.status, updatedAt: now }, { merge: true });
    return false;
  }

  const cleared = {
    ownerPackageId: null,
    ownerPackageExpiryMillis: null,
    billingStatus: p.status,
    updatedAt: now,
  };
  const authUser = await auth.getUser(uid);
  if (authUser.customClaims?.role === "PRO_HOST") {
    await setClaimsThenFirestore(
      auth,
      uid,
      authUser.customClaims,
      { ...authUser.customClaims, role: "SPECIALIST" },
      async () => {
        await userRef.set({ role: "SPECIALIST", ...cleared }, { merge: true });
      }
    );
    try {
      await auth.revokeRefreshTokens(uid);
    } catch (e) {
      logger.warn(`removeProHost: revokeRefreshTokens failed for ${uid}: ${(e as Error).message}`);
    }
    const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
    if (!ownedListings.empty) {
      const bw = db.bulkWriter();
      ownedListings.docs.forEach((doc) => bw.set(doc.ref, { isOwnerPackageLapsed: true }, { merge: true }));
      await bw.close();
    }
    const { sendPushToUser } = await import("../lib/push");
    const title = p.status === "ON_HOLD" ? "Payment problem — Pro Host paused"
      : p.status === "PAUSED" ? "ProHost Premium paused"
      : "Pro Host access ended";
    await sendPushToUser(uid, title, p.pushMessage, {
      category: "PACKAGE_EXPIRED",
      targetTab: "owner_subscriptions",
    });
  } else {
    await userRef.set(cleared, { merge: true });
  }

  await recordAuditLog({
    actionType: "PLAY_BILLING_SUBSCRIPTION_REVOKED",
    details: `Pro Host removed for uid=${uid} (${p.status}): ${p.reason}`,
    actorEmail: "play-billing@system.prohost.app",
    severity: "WARN",
  });
  return true;
}

/** Live answer from the stored entitlement (forced upgrades never expire). */
export async function isPremium(uid: string): Promise<boolean> {
  const data = (await getFirestore().collection("user_profiles").doc(uid).get()).data();
  if (isForced(data)) return true;
  const status = (data?.billingStatus as BillingStatus | undefined) ?? "ACTIVE";
  const expiry = (data?.ownerPackageExpiryMillis as number | null | undefined) ?? 0;
  return !!data?.ownerPackageId && grantsAccess(status, expiry);
}

export async function getSubscriptionStatus(uid: string): Promise<{
  source: EntitlementSource | null;
  status: BillingStatus | null;
  planId: string | null;
  expiryMillis: number | null;
}> {
  const data = (await getFirestore().collection("user_profiles").doc(uid).get()).data();
  return {
    source: (data?.entitlementSource as EntitlementSource | undefined) ?? null,
    status: (data?.billingStatus as BillingStatus | undefined) ?? null,
    planId: (data?.ownerPackageId as string | undefined) ?? null,
    expiryMillis: (data?.ownerPackageExpiryMillis as number | undefined) ?? null,
  };
}

export { ADMIN_FORCED_PLAN_ID, LIFETIME_EXPIRY_MILLIS };
