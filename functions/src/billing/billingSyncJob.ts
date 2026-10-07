import { onSchedule } from "firebase-functions/v2/scheduler";
import { HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { queryPlaySubscription } from "./billingHelpers";
import { PlayApiError, classifyPlayError } from "./playSubscription";
import { SUBSCRIPTIONS, syncSubscription } from "./subscriptionService";
import { ADMIN_FORCED_PLAN_ID, LEGACY_UNLIMITED_GRANT_PLAN_ID, LIFETIME_EXPIRY_MILLIS, isSupportedProduct } from "./playCatalog";
import "../lib/admin";

const OPEN_STATUSES = ["ACTIVE", "GRACE_PERIOD", "ON_HOLD", "PAUSED", "CANCELED", "PENDING"];

export interface BillingSyncReport {
  checked: number;
  changed: number;
  failed: number;
  migratedPlay: number;
  migratedForced: number;
  stoppedOnConfigError: boolean;
}

/**
 * BillingSyncJob: self-healing for missed RTDN deliveries.
 *  1. Every subscription not yet in a terminal state is re-read from Google Play and
 *     re-applied (renewals, cancellations, refunds, expiries, holds).
 *  2. One-time, idempotent migration of entitlements from before Google Play became the
 *     only authority: a profile with ownerPackageId but no entitlementSource is synced from
 *     its Play purchase when it has one, otherwise kept as an admin-forced upgrade, so no
 *     Pro Host loses access.
 */
export async function runBillingSyncOnce(): Promise<BillingSyncReport> {
  const db = getFirestore();
  const report: BillingSyncReport = {
    checked: 0, changed: 0, failed: 0, migratedPlay: 0, migratedForced: 0, stoppedOnConfigError: false,
  };

  // 1. Reconcile open subscriptions (paged by document id).
  const openDocs: FirebaseFirestore.QueryDocumentSnapshot[] = [];
  let openCursor: FirebaseFirestore.QueryDocumentSnapshot | null = null;
  for (let page = 0; page < 50; page++) {
    let q = db.collection(SUBSCRIPTIONS).where("status", "in", OPEN_STATUSES).orderBy("__name__").limit(200);
    if (openCursor) q = q.startAfter(openCursor);
    const snap = await q.get();
    openDocs.push(...snap.docs);
    if (snap.size < 200) break;
    openCursor = snap.docs[snap.docs.length - 1];
  }
  for (const doc of openDocs) {
    const d = doc.data();
    const uid = d.userId as string | undefined;
    const token = d.purchaseToken as string | undefined;
    if (!uid || !token) continue;
    // Retired plans (growth, enterprise) are ignored.
    if (!isSupportedProduct(d.productId as string | undefined)) {
      logger.info(`billingSync: ${doc.id} is a retired product — ignored`);
      continue;
    }
    report.checked++;
    try {
      const sub = await queryPlaySubscription(token, d.productId as string | undefined);
      const result = await syncSubscription(uid, token, sub, { source: "billing_sync" });
      if (result.status !== d.status) report.changed++;
    } catch (e) {
      const err = e instanceof PlayApiError ? e : classifyPlayError(e);
      report.failed++;
      logger.error(`billingSync: ${doc.id} [${err.kind}] ${err.message}`);
      // Without Play access nothing else can succeed; don't hammer the API.
      if (err.kind === "config") { report.stoppedOnConfigError = true; break; }
    }
  }

  // 2. Migrate legacy entitlements (each profile is migrated once).
  const legacy = await db.collection("user_profiles").where("ownerPackageId", "!=", null).get();
  const now = Date.now();
  for (const doc of legacy.docs) {
    const d = doc.data();
    if (d.entitlementSource) continue;
    const expiry = d.ownerPackageExpiryMillis as number | null | undefined;
    if (typeof expiry === "number" && expiry <= now) continue; // expirePackages handles lapsed ones
    const token = (d.lastPurchaseToken ?? d.activePurchaseToken) as string | undefined;
    const wasAdminGrant = d.ownerPackageId === LEGACY_UNLIMITED_GRANT_PLAN_ID || d.ownerPackageId === ADMIN_FORCED_PLAN_ID;
    if (token && !wasAdminGrant && !report.stoppedOnConfigError) {
      try {
        const sub = await queryPlaySubscription(token);
        if (!sub.obfuscatedAccountId || sub.obfuscatedAccountId === doc.id) {
          // Retired plans (growth, enterprise) are ignored: nothing granted, parked or alerted.
          if (!isSupportedProduct(sub.productId)) continue;
          await syncSubscription(doc.id, token, sub, { source: "migration" });
          report.migratedPlay++;
          continue;
        }
      } catch (e) {
        const err = classifyPlayError(e);
        if (err.kind !== "invalid") {
          // Try again on the next run rather than turning a paying user into a forced one.
          logger.warn(`billingSync migration: ${doc.id} deferred [${err.kind}] ${err.message}`);
          continue;
        }
      }
    }
    await doc.ref.set(
      {
        ownerPackageId: ADMIN_FORCED_PLAN_ID,
        ownerPackageExpiryMillis: LIFETIME_EXPIRY_MILLIS,
        entitlementSource: "admin_forced",
        billingStatus: "ACTIVE",
        subscriptionPlatform: "admin",
        subscriptionExpiry: LIFETIME_EXPIRY_MILLIS,
        migratedFromPackageId: d.ownerPackageId ?? null,
        updatedAt: now,
      },
      { merge: true }
    );
    report.migratedForced++;
  }

  logger.info("billingSync: done", report);
  if (report.changed || report.failed || report.migratedPlay || report.migratedForced) {
    await recordAuditLog({
      actionType: "BILLING_SYNC",
      details: `Checked ${report.checked} subscription(s): ${report.changed} changed, ${report.failed} failed` +
        `${report.stoppedOnConfigError ? " (stopped: Play API access denied — check Play Console permissions)" : ""}. ` +
        `Migrated ${report.migratedPlay} Play and ${report.migratedForced} admin-granted Pro Host(s).`,
      actorEmail: "play-billing@system.prohost.app",
      severity: report.failed || report.stoppedOnConfigError ? "WARN" : "INFO",
    });
  }
  return report;
}

export const billingSyncJob = onSchedule({ schedule: "every 24 hours", timeoutSeconds: 540 }, async () => {
  await runBillingSyncOnce();
});

/** Admin: run the sync (and migration) now, e.g. right after a deploy. */
export const runBillingSync = onCall({ timeoutSeconds: 540 }, async (request) => {
  if (request.auth?.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can run the billing sync.");
  }
  return runBillingSyncOnce();
});
