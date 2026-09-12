import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { reconcileTransaction } from "./reconcile";
import { whishSecret } from "./initiateWhishPayment";
import * as logger from "firebase-functions/logger";
import "../lib/admin";

interface CheckWhishStatusData {
  txId?: string;
}

/**
 * Client-callable polling fallback for when the webhook doesn't fire promptly
 * (common on emulators, flaky networks, or if the user backgrounds the app before
 * the redirect completes). Same reconciliation logic as the webhook — independently
 * re-checks with Whish, never trusts anything the client claims about its own
 * payment.
 */
export const checkWhishStatus = onCall<CheckWhishStatusData>(
  { secrets: [whishSecret] },
  async (request) => {
    const auth = request.auth;
    if (!auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }
    const txId = request.data?.txId;
    if (!txId) {
      throw new HttpsError("invalid-argument", "txId is required.");
    }

    const db = getFirestore();
    const snap = await db.collection("whish_transactions").doc(txId).get();
    if (!snap.exists) {
      throw new HttpsError("not-found", "Transaction not found.");
    }
    const tx = snap.data()!;
    if (tx.userId !== auth.uid && auth.token.role !== "ADMIN") {
      logger.warn("whish_check_status_permission_denied", { txId, requestedBy: auth.uid, ownedBy: tx.userId });
      throw new HttpsError("permission-denied", "You can only check your own transactions.");
    }

    const status = await reconcileTransaction(txId, whishSecret.value());
    return { status };
  }
);
