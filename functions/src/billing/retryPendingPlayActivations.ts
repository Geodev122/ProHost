import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import "../lib/admin";
import { PENDING_COLLECTION, activatePlayPurchase } from "./activatePurchase";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";
import { logPurchaseOnce } from "./subscriptionService";

const ALERT_AFTER_MS = 6 * 60 * 60 * 1000;
// Play refunds unacknowledged purchases after 3 days; past 4 nothing is left to save.
const GIVE_UP_AFTER_MS = 4 * 24 * 60 * 60 * 1000;

/**
 * Re-runs verify → grant → acknowledge for purchases parked in play_billing_pending
 * (Play API outage, missing Play Console permission, failed acknowledgement). Every
 * 15 minutes, so a paid subscription activates and is acknowledged before Play's
 * 3-day auto-refund even if the buyer never reopens the app.
 */
export const retryPendingPlayActivations = onSchedule({ schedule: "*/15 * * * *" }, async () => {
  const db = getFirestore();
  const now = Date.now();
  // Rows waiting for an admin can't be retried; close the ones Play has refunded by now
  // (4+ days) so they never pile up.
  const adminRows = await db.collection(PENDING_COLLECTION)
    .where("resolved", "==", false).where("needsAdmin", "==", true).limit(100).get();
  for (const doc of adminRows.docs) {
    const createdAt = (doc.data().createdAt as number | undefined) ?? now;
    if (now - createdAt > GIVE_UP_AFTER_MS) {
      await doc.ref.update({ resolved: true, outcome: "expired_unclaimed", resolvedAt: now });
    }
  }

  // Only retryable rows, oldest first (composite index in firestore.indexes.json).
  const snap = await db.collection(PENDING_COLLECTION)
    .where("resolved", "==", false)
    .where("needsAdmin", "==", false)
    .orderBy("createdAt")
    .limit(50)
    .get();
  if (snap.empty) return;

  for (const doc of snap.docs) {
    const d = doc.data();
    const uid = d.uid as string | undefined;
    const token = d.purchaseToken as string | undefined;
    const productId = (d.productId as string | undefined) ?? "";
    const createdAt = (d.createdAt as number | undefined) ?? now;
    const attempts = ((d.attempts as number | undefined) ?? 0) + 1;
    if (!uid || !token) {
      await doc.ref.update({ resolved: true, outcome: "malformed", resolvedAt: now });
      continue;
    }

    try {
      const outcome = await activatePlayPurchase(uid, token, productId, "retryPendingPlayActivations");
      if (outcome.status === "granted" && outcome.acknowledged) {
        await doc.ref.update({ resolved: true, outcome: "granted", attempts, resolvedAt: now });
        logger.info(`retryPendingPlayActivations: activated uid=${uid} product=${outcome.productId} after ${attempts} attempt(s)`);
        // GA4 purchase event, once per subscription (no-op if the app already logged it).
        await logPurchaseOnce(uid, token, outcome.purchase);
        // A row parked only because the acknowledgement failed was already granted (and
        // announced) — don't tell the person their plan is "now active" a second time.
        if (d.lastError !== "acknowledge failed") {
          await sendPushToUser(uid, "Your Pro Host plan is active", "Your payment was confirmed — your Pro Host plan is now active.", {
            category: "PACKAGE_ACTIVATED",
            targetTab: "owner_subscriptions",
          });
        }
        await recordAuditLog({
          actionType: "PLAY_BILLING_ACTIVATION_RECOVERED",
          details: `Parked purchase activated for uid=${uid}, product=${outcome.productId}, after ${attempts} attempt(s).`,
          actorEmail: "play-billing@system.prohost.app",
          severity: "SECURE",
        });
        continue;
      }
      if (outcome.status === "unsupported_product") {
        // Retired plan: retrying can't change that, an admin decides.
        await doc.ref.update({
          needsAdmin: true, attempts, updatedAt: now,
          lastError: `unsupported_product: ${outcome.productId} is a retired plan (only package_pro_mrr grants Pro Host)`,
        });
        await notifyAdminsOfSubscriptionChange(uid, "UNSUPPORTED_PRODUCT", {
          planId: outcome.productId,
          note: "open Admin › Packages › Payments needing attention to activate it, or let Google Play refund it",
        });
        continue;
      }
      if (outcome.status === "owned_by_other") {
        // Never drop a paid purchase silently: hand it to an admin to assign.
        await doc.ref.update({
          needsAdmin: true, attempts, updatedAt: now,
          lastError: "owned_by_other: tagged for or linked to another ProHost account",
        });
        await notifyAdminsOfSubscriptionChange(uid, "OWNERSHIP_MISMATCH", {
          note: "open Admin › Packages › Payments needing attention",
        });
        continue;
      }
      if (outcome.status === "expired" ||
        (outcome.status === "play_error" && outcome.error.kind === "invalid")) {
        await doc.ref.update({ resolved: true, outcome: outcome.status, attempts, resolvedAt: now });
        logger.warn(`retryPendingPlayActivations: dropped uid=${uid} product=${productId}: ${outcome.status}`);
        continue;
      }
      const lastError = outcome.status === "play_error"
        ? `${outcome.error.kind}: ${outcome.error.message}`
        : outcome.status === "granted" ? "acknowledge failed" : outcome.status;
      const update: Record<string, unknown> = { attempts, lastError: lastError.slice(0, 500), updatedAt: now };

      if (now - createdAt > GIVE_UP_AFTER_MS) {
        Object.assign(update, { resolved: true, outcome: "gave_up", resolvedAt: now });
        await notifyAdminsOfSubscriptionChange(uid, "ACTIVATION_AT_RISK", {
          note: `gave up after ${attempts} attempts — check Play Console › Order management`,
        });
        await recordAuditLog({
          actionType: "PLAY_BILLING_ACTIVATION_FAILED",
          details: `Gave up activating uid=${uid}, product=${productId} after ${attempts} attempts: ${lastError}. Check Play Console › Order management.`,
          actorEmail: "play-billing@system.prohost.app",
          severity: "WARN",
        });
      } else if (!d.alerted && now - createdAt > ALERT_AFTER_MS) {
        update.alerted = true;
        await notifyAdminsOfSubscriptionChange(uid, "ACTIVATION_AT_RISK", {
          note: `still not activated after 6 h (${attempts} attempts): ${lastError.slice(0, 80)}`,
        });
        await recordAuditLog({
          actionType: "PLAY_BILLING_ACTIVATION_STUCK",
          details: `Paid purchase for uid=${uid}, product=${productId} still not activated after 6 h (${attempts} attempts): ${lastError}. Play refunds it 3 days after purchase if it stays unacknowledged.`,
          actorEmail: "play-billing@system.prohost.app",
          severity: "WARN",
        });
      }
      await doc.ref.update(update);
    } catch (e) {
      logger.error(`retryPendingPlayActivations: attempt failed uid=${uid} product=${productId}`, e);
      await doc.ref.update({ attempts, lastError: String(e).slice(0, 500), updatedAt: now }).catch(() => undefined);
    }
  }
});
