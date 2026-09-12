import { getFirestore } from "firebase-admin/firestore";
import { getCollectStatus } from "../lib/whishClient";
import { grantEntitlement, WhishTransactionDoc } from "../lib/entitlements";
import { recordAuditLog } from "../lib/auditLog";
import * as logger from "firebase-functions/logger";

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
 *
 * This function is reached from two independent triggers by design — the client
 * polling checkWhishStatus AND Whish's own webhook — so it's routine for two calls
 * to be in flight for the same txId at once. The status-transition write below is
 * wrapped in a Firestore transaction specifically so only one of them ever "wins"
 * the PENDING -> SUCCESS/FAILED claim and calls grantEntitlement; without that, both
 * could independently observe PENDING, both get "success" back from Whish, and both
 * grant the entitlement (double PAYG credit, double subscription renewal, etc).
 */
export async function reconcileTransaction(
  txId: string,
  secret: string
): Promise<"PENDING" | "SUCCESS" | "FAILED" | "NOT_FOUND"> {
  const db = getFirestore();
  const ref = db.collection("whish_transactions").doc(txId);
  const snap = await ref.get();
  if (!snap.exists) {
    logger.warn("whish_reconcile_transaction_not_found", { txId });
    return "NOT_FOUND";
  }

  const tx = snap.data() as WhishTransactionDoc;
  if (tx.status !== "PENDING") {
    // Not logged — this is the routine, expected path for every poll/webhook hit
    // after a transaction already settled, which would otherwise flood logs.
    return tx.status;
  }

  let result;
  try {
    result = await getCollectStatus(tx.currency, tx.externalId, secret);
  } catch (e) {
    // Transient Whish API error — leave PENDING, caller can retry later.
    // whishClient's own log already captured the HTTP/API-level detail; this
    // ties it back to the specific transaction for anyone searching by txId.
    logger.warn("whish_reconcile_status_check_failed", {
      txId,
      orderId: tx.orderId,
      purpose: tx.purpose,
      error: e instanceof Error ? e.message : String(e),
    });
    return "PENDING";
  }

  if (result.status !== "success" && result.status !== "failed") {
    return "PENDING";
  }
  const newStatus: "SUCCESS" | "FAILED" = result.status === "success" ? "SUCCESS" : "FAILED";

  // Atomically claim the PENDING -> newStatus transition. If a concurrent call
  // already claimed it (between our plain read above and now), this one backs off
  // instead of also granting the entitlement / recording a second failure log.
  const claimed = await db.runTransaction(async (t) => {
    const freshSnap = await t.get(ref);
    if (freshSnap.data()?.status !== "PENDING") return false;
    t.set(ref, { status: newStatus }, { merge: true });
    return true;
  });

  if (!claimed) {
    logger.info("whish_reconcile_claim_lost", { txId });
    const finalSnap = await ref.get();
    return (finalSnap.data()?.status as "SUCCESS" | "FAILED" | undefined) ?? "PENDING";
  }

  if (newStatus === "SUCCESS") {
    try {
      await grantEntitlement({ ...tx, status: "SUCCESS" });
    } catch (e) {
      // The transaction doc is already marked SUCCESS at this point — the
      // payment genuinely settled — but the entitlement grant itself threw
      // (a bug, a Firestore blip, an Auth SDK error). This is the single most
      // important line in this whole file to have logged: without it, a
      // customer who paid and got nothing looks identical, from the outside,
      // to a customer who never paid, and support has no way to find the
      // stuck transaction by txId/orderId/uid. Rethrown unchanged — this adds
      // visibility, it doesn't change what happens next.
      logger.error("whish_reconcile_entitlement_grant_failed", {
        txId,
        orderId: tx.orderId,
        purpose: tx.purpose,
        targetId: tx.targetId,
        userId: tx.userId,
        amountUsd: tx.amountUsd,
        error: e instanceof Error ? e.message : String(e),
      });
      throw e;
    }
    logger.info("whish_reconcile_success", {
      txId,
      orderId: tx.orderId,
      purpose: tx.purpose,
      targetId: tx.targetId,
      userId: tx.userId,
      amountUsd: tx.amountUsd,
    });
    return "SUCCESS";
  }

  logger.info("whish_reconcile_payment_failed", {
    txId,
    orderId: tx.orderId,
    purpose: tx.purpose,
    targetId: tx.targetId,
    userId: tx.userId,
  });
  await recordAuditLog({
    actionType: "WHISH_PAYMENT_FAILED",
    details: `Order ${tx.orderId} for purpose ${tx.purpose} (target ${tx.targetId}) did not settle.`,
    actorEmail: tx.payerName,
    severity: "WARN",
  });
  return "FAILED";
}
