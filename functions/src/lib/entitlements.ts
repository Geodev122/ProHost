import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { recordAuditLog } from "./auditLog";

export type WhishPurpose = "SUBSCRIPTION" | "OWNER_PACKAGE" | "PAYG_LISTING" | "BOOKING";

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
      await db.collection("user_profiles").doc(tx.userId).set(
        { paygListingsBoughtCount: FieldValue.increment(1), updatedAt: now },
        { merge: true }
      );
      await grantProHostRoleIfNeeded(tx.userId);
      break;
    }
    case "BOOKING": {
      await db.collection("booking_requests").doc(tx.targetId).set(
        { status: "ACCEPTED", isExternalPaymentSettled: true, reviewedAt: now },
        { merge: true }
      );
      break;
    }
  }

  await recordAuditLog({
    actionType: "WHISH_PAYMENT_SUCCESS",
    details: `Order ${tx.orderId} ($${tx.amountUsd.toFixed(2)}) settled for purpose ${tx.purpose} (target ${tx.targetId}). Entitlement granted.`,
    actorEmail: tx.payerName,
    severity: "SECURE",
  });
}
