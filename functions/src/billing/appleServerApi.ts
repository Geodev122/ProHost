/**
 * App Store Server API (In-App Purchase key, owner 22 Nov 2026): the daily re-sync of App Store subscriptions, the
 * counterpart of Play's `billingSyncJob`. App Store Server Notifications V2 stay the primary signal; this catches what a
 * missed notification would leave stale. The key lives in the server-only doc `app_config/apple_iap` (keyId, issuerId,
 * privateKey), written by Maintenance task `applekey` from the GitHub secrets APPLE_IAP_KEY_ID / APPLE_IAP_KEY_P8 /
 * ASC_ISSUER_ID; it is never logged. Without the doc the job does nothing.
 */
import { logger } from "firebase-functions/v2";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { APIException, AppStoreServerAPIClient, Environment } from "@apple/app-store-server-library";
import { runAs } from "../lib/runtimeIdentity";
import { APPLE_BUNDLE_ID, isAppleProduct } from "./appleCatalog";
import { factsOf, syncAppleSubscription, verifyAppleRenewalJws, verifyAppleTransactionJws } from "./appleSubscription";
import { AppleSubscriptionFacts } from "./appleCatalog";
import { SUBSCRIPTIONS } from "./subscriptionService";
import "../lib/admin";

export const APPLE_IAP_CONFIG = "apple_iap";

interface AppleKey { keyId: string; issuerId: string; privateKey: string }

async function appleKey(): Promise<AppleKey | null> {
  const d = (await getFirestore().collection("app_config").doc(APPLE_IAP_CONFIG).get()).data();
  if (!d || typeof d.keyId !== "string" || typeof d.issuerId !== "string" || typeof d.privateKey !== "string") return null;
  return { keyId: d.keyId, issuerId: d.issuerId, privateKey: d.privateKey };
}

function clientFor(key: AppleKey, environment: Environment): AppStoreServerAPIClient {
  return new AppStoreServerAPIClient(key.privateKey, key.keyId, key.issuerId, APPLE_BUNDLE_ID, environment);
}

/** The environment a recorded subscription was bought in ("Sandbox" for TestFlight), production otherwise. */
export function environmentOf(recorded: unknown): Environment {
  return String(recorded ?? "").toLowerCase() === "sandbox" ? Environment.SANDBOX : Environment.PRODUCTION;
}

/** Apple's current facts for one subscription, verified like a client-sent transaction; null when Apple has none. */
export async function fetchAppleFacts(key: AppleKey, originalTransactionId: string, environment: Environment): Promise<AppleSubscriptionFacts | null> {
  const res = await clientFor(key, environment).getAllSubscriptionStatuses(originalTransactionId);
  const items = (res.data ?? []).flatMap((g) => g.lastTransactions ?? []);
  const item = items.find((t) => t.originalTransactionId === originalTransactionId) ?? items[0];
  if (!item?.signedTransactionInfo) return null;
  const tx = await verifyAppleTransactionJws(item.signedTransactionInfo);
  const renewal = item.signedRenewalInfo ? await verifyAppleRenewalJws(item.signedRenewalInfo).catch(() => null) : null;
  return factsOf(tx, renewal);
}

export interface AppleSyncSummary { configured: boolean; checked: number; synced: number; skipped: number; failed: number; statuses: Record<string, number> }

/** Re-reads every recorded App Store subscription from Apple and applies it (grants, renewals, expiries, refunds). */
export async function runAppleSync(source: string): Promise<AppleSyncSummary> {
  const summary: AppleSyncSummary = { configured: false, checked: 0, synced: 0, skipped: 0, failed: 0, statuses: {} };
  const key = await appleKey();
  if (!key) {
    logger.info("appleSync: app_config/apple_iap not set (Maintenance applekey) — nothing to do");
    return summary;
  }
  summary.configured = true;
  const snap = await getFirestore().collection(SUBSCRIPTIONS).where("platform", "==", "ios").get();
  for (const doc of snap.docs) {
    const d = doc.data();
    const otid = typeof d.originalTransactionId === "string" ? d.originalTransactionId : "";
    const uid = typeof d.userId === "string" ? d.userId : "";
    summary.checked++;
    if (!otid || !uid || !isAppleProduct(String(d.productId ?? ""))) { summary.skipped++; continue; }
    try {
      const facts = await fetchAppleFacts(key, otid, environmentOf(d.environment));
      if (!facts || !isAppleProduct(facts.productId)) { summary.skipped++; continue; }
      const result = await syncAppleSubscription(uid, facts, { source });
      summary.synced++;
      summary.statuses[result.status] = (summary.statuses[result.status] ?? 0) + 1;
    } catch (e) {
      summary.failed++;
      const status = e instanceof APIException ? e.httpStatusCode : null;
      logger.warn("appleSync: subscription failed", { subscriptionId: doc.id, httpStatus: status, message: (e as Error).message?.slice(0, 200) });
    }
  }
  logger.info("appleSync: done", summary);
  return summary;
}

/** Daily, like Play's billingSyncJob (which leaves platform ios to this job). */
export const appleSyncJob = onSchedule({ ...runAs("billing"), schedule: "every 24 hours", timeoutSeconds: 540 }, async () => {
  await runAppleSync("apple_daily_sync");
});
