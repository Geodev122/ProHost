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
    const finalSnap = await ref.get();
    return (finalSnap.data()?.status as "SUCCESS" | "FAILED" | undefined) ?? "PENDING";
  }

  if (newStatus === "SUCCESS") {
    await grantEntitlement({ ...tx, status: "SUCCESS" });
    return "SUCCESS";
  }

  await recordAuditLog({
    actionType: "WHISH_PAYMENT_FAILED",
    details: `Order ${tx.orderId} for purpose ${tx.purpose} (target ${tx.targetId}) did not settle.`,
    actorEmail: tx.payerName,
    severity: "WARN",
  });
  return "FAILED";
}
