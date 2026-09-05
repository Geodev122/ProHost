import { getFirestore } from "firebase-admin/firestore";
import { getCollectStatus } from "../lib/whishClient";
import { grantEntitlement, WhishTransactionDoc } from "../lib/entitlements";
import { recordAuditLog } from "../lib/auditLog";

/**
 * The single place that decides whether a Whish payment actually succeeded. Always
 * calls Whish's own payment/collect/status endpoint with the server-held secret —
 * never trusts a webhook payload's own claimed status, since we don't have a
 * documented signature scheme for Whish's callbacks to verify against. A callback
 * hitting whishWebhook is treated purely as a hint to come check here; the client
 * calling checkWhishStatus is the same code path.
 *
 * Idempotent: once a transaction is SUCCESS or FAILED, calling this again is a no-op
 * that just returns the stored status — entitlements are never granted twice.
 */
export async function reconcileTransaction(
  txId: string,
  secret: string
): Promise<"PENDING" | "SUCCESS" | "FAILED" | "NOT_FOUND"> {
  const db = getFirestore();
  const ref = db.collection("whish_transactions").doc(txId);
  const snap = await ref.get();
  if (!snap.exists) return "NOT_FOUND";

  const tx = snap.data() as WhishTransactionDoc;
  if (tx.status !== "PENDING") {
    return tx.status;
  }

  let result;
  try {
    result = await getCollectStatus(tx.currency, tx.externalId, secret);
  } catch (e) {
    // Transient Whish API error — leave PENDING, caller can retry later.
    return "PENDING";
  }

  if (result.status === "success") {
    await ref.set({ status: "SUCCESS" }, { merge: true });
    await grantEntitlement({ ...tx, status: "SUCCESS" });
    return "SUCCESS";
  }

  if (result.status === "failed") {
    await ref.set({ status: "FAILED" }, { merge: true });
    await recordAuditLog({
      actionType: "WHISH_PAYMENT_FAILED",
      details: `Order ${tx.orderId} for purpose ${tx.purpose} (target ${tx.targetId}) did not settle.`,
      actorEmail: tx.payerName,
      severity: "WARN",
    });
    return "FAILED";
  }

  return "PENDING";
}
