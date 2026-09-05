import { getFirestore, FieldValue } from "firebase-admin/firestore";
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
      break;
    }
    case "PAYG_LISTING": {
      await db.collection("user_profiles").doc(tx.userId).set(
        { paygListingsBoughtCount: FieldValue.increment(1), updatedAt: now },
        { merge: true }
      );
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
