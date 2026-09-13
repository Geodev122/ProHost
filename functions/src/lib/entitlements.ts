import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { recordAuditLog } from "./auditLog";
import { validateListingForPublish } from "../listings/publishValidation";
import { getPackagePlan } from "./packagePlans";

export type WhishPurpose = "OWNER_PACKAGE";

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
  targetId: string | null;
  externalId: number;
  // Set only when this payment was triggered by a package-limit rejection inside
  // CreateListingDialog's Publish flow — the specific Draft (same workspace_listings
  // id CreateListingDialog already reuses between Save-as-Draft and Publish) that
  // should flip to ACTIVE the moment this payment settles, instead of making the
  // host re-open the wizard and hit Publish a second time. See
  // autoPublishDraftIfNeeded below.
  draftListingId?: string;
}

/**
 * Promotes a SPECIALIST to PRO_HOST — the ONLY way this role is ever granted (no
 * self-service/free upgrade path exists). Called from grantEntitlement() below the
 * moment a real OWNER_PACKAGE Whish payment settles. Never downgrades an ADMIN, and
 * is a no-op if the user is already PRO_HOST — safe to call on every such payment.
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
 * Flips a Draft straight to ACTIVE the moment the package-limit payment that was
 * blocking it settles — the other half of ProHostViewModel.createNewSpaceListing's
 * PackageLimitReached flow: the host's in-progress wizard is saved as this exact
 * Draft (same listingId CreateListingDialog already reuses between Save-as-Draft
 * and Publish) before they're sent to pay, so nothing is lost and they don't have
 * to re-open the wizard and hit Publish a second time. Deliberately conservative:
 * only ever touches a Draft this same user owns, and does nothing if it's already
 * left the Draft state (already published or deleted) or doesn't exist — never
 * resurrects or hijacks a listing.
 *
 * Validates BEFORE flipping the Draft to ACTIVE — an incomplete Draft flipped to
 * ACTIVE would just get demoted straight back to Draft a moment later by
 * onWorkspaceListingPublishValidation, leaving the host having paid for nothing to
 * show for it. Skip the transition entirely when the Draft still fails validation;
 * the host can finish the listing and publish it for real once it's complete.
 */
async function autoPublishDraftIfNeeded(tx: WhishTransactionDoc): Promise<void> {
  if (!tx.draftListingId) return;
  const db = getFirestore();
  const draftRef = db.collection("workspace_listings").doc(tx.draftListingId);
  const snap = await draftRef.get();
  if (!snap.exists) return;
  const draft = snap.data();
  if (draft?.ownerId !== tx.userId || draft?.status !== "DRAFT") return;

  const problems = validateListingForPublish(draft as any);
  if (problems.length > 0) {
    await draftRef.set({ publishBlockedReasons: problems, updatedAt: Date.now() }, { merge: true });
    await recordAuditLog({
      actionType: "LISTING_AUTO_PUBLISH_SKIPPED_INCOMPLETE",
      details: `Draft ${tx.draftListingId} NOT auto-published after ${tx.purpose} payment (order ${tx.orderId}) settled — still missing: ${problems.join(" ")}. Credit preserved for a later publish.`,
      actorEmail: tx.payerName,
      severity: "WARN",
    });
    return;
  }

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
    case "OWNER_PACKAGE": {
      // Real, admin-defined validity — replaces the old hardcoded 30-day constant
      // that was never actually tied to what the host bought. initiateWhishPayment.ts
      // already validated targetId resolves to a real, enabled PackagePlan before
      // this payment was ever allowed to start; a plan missing/disabled here only
      // happens if an admin deleted/disabled it in the brief window between checkout
      // and settlement — the payment already happened, so fall back to 30 days
      // rather than leaving the host with nothing to show for it.
      const plan = tx.targetId ? await getPackagePlan(tx.targetId) : undefined;
      if (!plan) {
        await recordAuditLog({
          actionType: "PACKAGE_PLAN_MISSING_AT_GRANT",
          details: `OWNER_PACKAGE payment (order ${tx.orderId}) settled for targetId "${tx.targetId}", but no matching package_plans/main entry was found at grant time — falling back to a 30-day validity.`,
          actorEmail: tx.payerName,
          severity: "WARN",
        });
      }
      const validityMs = (plan?.validityDays ?? 30) * 24 * 60 * 60 * 1000;
      await db.collection("user_profiles").doc(tx.userId).set(
        { ownerPackageId: tx.targetId, ownerPackageExpiryMillis: now + validityMs, updatedAt: now },
        { merge: true }
      );
      await grantProHostRoleIfNeeded(tx.userId);
      await autoPublishDraftIfNeeded(tx);
      break;
    }
  }

  await recordAuditLog({
    actionType: "WHISH_PAYMENT_SUCCESS",
    details: `Order ${tx.orderId} ($${tx.amountUsd.toFixed(2)}) settled for purpose ${tx.purpose} (target ${tx.targetId ?? "n/a"}). Entitlement granted.`,
    actorEmail: tx.payerName,
    severity: "SECURE",
  });
}
