import { onRequest } from "firebase-functions/v2/https";
import { reconcileTransaction } from "./reconcile";
import { whishSecret } from "./initiateWhishPayment";
import * as logger from "firebase-functions/logger";
import "../lib/admin";

/**
 * Whish's success/failure callback lands here (both successCallbackUrl and
 * failureCallbackUrl point at this same URL — see initiateWhishPayment.ts). We don't
 * have a documented signature scheme to verify this payload's authenticity against,
 * so it is treated purely as a trigger: it never decides payment status by itself.
 * On receipt we independently ask Whish's own status endpoint what really happened
 * (reconcileTransaction), using the server-held secret. This makes the callback safe
 * to receive even if it were spoofed — a forged callback just causes an extra
 * (harmless) status check against Whish, it can't force an entitlement grant on its
 * own.
 */
export const whishWebhook = onRequest({ secrets: [whishSecret] }, async (req, res) => {
  const externalIdRaw = (req.query.externalId as string) ?? (req.body?.externalId as string);
  const externalId = Number(externalIdRaw);
  if (!externalId || Number.isNaN(externalId)) {
    res.status(400).send("Missing or invalid externalId");
    return;
  }

  const txId = `TX-${externalId}`;
  logger.info("whish_webhook_received", { txId, externalId });
  try {
    const status = await reconcileTransaction(txId, whishSecret.value());
    res.status(200).json({ txId, status });
  } catch (e) {
    // Never fail loudly to Whish's caller — ack regardless (retry happens via
    // checkWhishStatus polling from the client if this attempt didn't resolve
    // it) — but this used to swallow the error with no log at all, meaning a
    // webhook that threw here left zero trace anywhere. Log it before acking.
    logger.error("whish_webhook_reconcile_failed", {
      txId,
      externalId,
      error: e instanceof Error ? e.message : String(e),
    });
    res.status(200).json({ txId, status: "PENDING", note: "reconciliation deferred" });
  }
});
