import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { recordAuditLog } from "./auditLog";

export type WhishPurpose = "SUBSCRIPTION" | "OWNER_PACKAGE" | "PAYG_LISTING";

export interface WhishTransactionDoc {
  id: string;
  orderId: string;
  amountUsd: number;
  currency: string;
  status: "PENDING" | "SUCCESS" | "FAILED";
  timestamp: number;
  payerName: string;
  payerPhone: string;
  channelId: string;
  sourceEmail: string;
  signatureHash: string;
  spaceId: string;
  spaceTitle: string;
  daysGranted: number;
  userId: string;
  purpose: WhishPurpose;
  targetId: string;
  externalId: number;
  // Set only when this payment was triggered by a quota/PAYG-credit rejection inside
  // CreateListingDialog's Publish flow — the specific Draft (same workspace_listings
  // id CreateListingDialog already reuses between Save-as-Draft and Publish) that
  // should flip to ACTIVE the moment this payment settles, instead of making the
  // host re-open the wizard and hit Publish a second time. See
  // autoPublishDraftIfNeeded below.
  draftListingId?: string;
}

const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Promotes a SPECIALIST to PRO_HOST — the ONLY way this role is ever granted (no
 * self-service/free upgrade path exists). Called from grantEntitlement() below the
 * moment a real OWNER_PACKAGE or PAYG_LISTING Whish payment settles. Never downgrades
 * an ADMIN, and is a no-op if the user is already PRO_HOST — safe to call on every
 * such payment, including repeat PAYG purchases by an existing Pro Host.
 */
async function grantProHostRoleIfNeeded(uid: string): Promise<void> {
  const auth = getAuth();
  const user = await auth.getUser(uid);
  const currentRole = user.customClaims?.role;
  if (currentRole === "ADMIN" || currentRole === "PRO_HOST") {
    return;
  }

  await auth.setCustomUserClaims(uid, { ...user.customClaims, role: "PRO_HOST" });

  const db = getFirestore();
  await db.collection("user_profiles").doc(uid).set(
    { role: "PRO_HOST", updatedAt: Date.now() },
    { merge: true }
  );

  await recordAuditLog({
    actionType: "ROLE_PROMOTED_PRO_HOST",
    details: `User ${uid} promoted from SPECIALIST to PRO_HOST after a settled listing/package payment.`,
    actorEmail: user.email ?? "system@prohost.app",
    severity: "SECURE",
  });
}

/**
 * Flips a Draft straight to ACTIVE the moment the quota/PAYG-credit payment that was
 * blocking it settles — the other half of ProHostViewModel.createNewSpaceListing's
 * PackageLimitReached / PaygCategoryCreditRequired flow: the host's in-progress
 * wizard is saved as this exact Draft (same listingId CreateListingDialog already
 * reuses between Save-as-Draft and Publish) before they're sent to pay, so nothing
 * is lost and they don't have to re-open the wizard and hit Publish a second time.
 * Deliberately conservative: only ever touches a Draft this same user owns, and does
 * nothing if it's already left the Draft state (already published or deleted) or
 * doesn't exist — never resurrects or hijacks a listing.
 */
async function autoPublishDraftIfNeeded(tx: WhishTransactionDoc): Promise<void> {
  if (!tx.draftListingId) return;
  const db = getFirestore();
  const draftRef = db.collection("workspace_listings").doc(tx.draftListingId);
  const snap = await draftRef.get();
  if (!snap.exists) return;
  const draft = snap.data();
  if (draft?.ownerId !== tx.userId || draft?.status !== "DRAFT") return;

  await draftRef.set({ status: "ACTIVE", updatedAt: Date.now() }, { merge: true });
  await recordAuditLog({
    actionType: "LISTING_AUTO_PUBLISHED_AFTER_PAYMENT",
    details: `Draft ${tx.draftListingId} auto-published after ${tx.purpose} payment (order ${tx.orderId}) settled.`,
    actorEmail: tx.payerName,
    severity: "SECURE",
  });
}

/**
 * Applies the real-world effect of a successfully settled payment. Called ONLY after
 * whishClient.getCollectStatus() has independently confirmed "success" with Whish
 * directly (never based on a webhook payload's own claimed status) — see
 * reconcileTransaction in whishWebhook.ts / checkWhishStatus.ts.
 */
export async function grantEntitlement(tx: WhishTransactionDoc): Promise<void> {
  const db = getFirestore();
  const now = Date.now();

  switch (tx.purpose) {
    case "SUBSCRIPTION": {
      await db.collection("workspace_listings").doc(tx.targetId).set(
        { isActiveSubscription: true, subscriptionExpiryMillis: now + THIRTY_DAYS_MS, updatedAt: now },
        { merge: true }
      );
      break;
    }
    case "OWNER_PACKAGE": {
      await db.collection("user_profiles").doc(tx.userId).set(
        { ownerPackageTier: tx.targetId, ownerPackageExpiryMillis: now + THIRTY_DAYS_MS, updatedAt: now },
        { merge: true }
      );
      await grantProHostRoleIfNeeded(tx.userId);
      break;
    }
    case "PAYG_LISTING": {
      // targetId is the purchased SchemaItem category id (e.g. "ST-01") — recording
      // it as a credit here is what CreateListingDialog's category-lock and
      // listingCountTracker.ts's consumePaygCreditIfNeeded both key off. Previously
      // this only bumped the display-only paygListingsBoughtCount counter, which
      // discarded which category was actually bought — a purchase for one category
      // could silently be "spent" publishing a listing under a different, unpaid one.
      await db.collection("user_profiles").doc(tx.userId).set(
        {
          paygListingsBoughtCount: FieldValue.increment(1),
          [`paygCategoryCredits.${tx.targetId}`]: FieldValue.increment(1),
          updatedAt: now,
        },
        { merge: true }
      );
      await grantProHostRoleIfNeeded(tx.userId);
      break;
    }
  }

  // Only OWNER_PACKAGE (tier upgrade lifting the whole-listing cap) and PAYG_LISTING
  // (the specific category credit) can ever be the thing standing between a Draft and
  // Publish — a SUBSCRIPTION payment renews an already-ACTIVE listing's own
  // subscription, never a Draft, so it's excluded here.
  if (tx.purpose === "OWNER_PACKAGE" || tx.purpose === "PAYG_LISTING") {
    await autoPublishDraftIfNeeded(tx);
  }

  await recordAuditLog({
    actionType: "WHISH_PAYMENT_SUCCESS",
    details: `Order ${tx.orderId} ($${tx.amountUsd.toFixed(2)}) settled for purpose ${tx.purpose} (target ${tx.targetId}). Entitlement granted.`,
    actorEmail: tx.payerName,
    severity: "SECURE",
  });
}
