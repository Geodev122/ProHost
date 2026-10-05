/**
 * Play Developer API access shared by the billing functions: the publisher client,
 * subscription lookups and acknowledgement. Entitlement changes live in
 * entitlementManager.ts; subscription records in subscriptionService.ts.
 */
import { logger } from "firebase-functions/v2";
import { google } from "googleapis";
import "../lib/admin";
import { classifyPlayError, fetchPlaySubscription, PlaySubscription } from "./playSubscription";

export const PACKAGE_NAME = "app.geonajjar.prohost";

export async function getPlayPublisher() {
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return google.androidpublisher({ version: "v3", auth });
}

/** Live subscription details (subscriptionsv2, v1 fallback). Throws PlayApiError only. */
export async function queryPlaySubscription(token: string, productIdHint?: string): Promise<PlaySubscription> {
  let publisher: Awaited<ReturnType<typeof getPlayPublisher>>;
  try {
    publisher = await getPlayPublisher();
  } catch (e) {
    throw classifyPlayError(e);
  }
  return fetchPlaySubscription(publisher, PACKAGE_NAME, token, productIdHint);
}

/**
 * Acknowledges a subscription server-side, after it was granted. Play auto-refunds
 * purchases left unacknowledged for 3 days. Returns false when the call failed, so the
 * caller can park the purchase for the retry job instead of losing the acknowledgement.
 */
export async function acknowledgeIfNeeded(
  productId: string,
  token: string,
  alreadyAcknowledged: boolean,
  logTag = "billing"
): Promise<boolean> {
  if (alreadyAcknowledged) return true;
  try {
    const publisher = await getPlayPublisher();
    await publisher.purchases.subscriptions.acknowledge({
      packageName: PACKAGE_NAME,
      subscriptionId: productId,
      token,
      requestBody: {},
    });
    logger.info(`${logTag}: acknowledged product=${productId}`);
    return true;
  } catch (e) {
    const err = classifyPlayError(e);
    // Already acknowledged (e.g. by a concurrent RTDN) comes back as a 400: re-read Play
    // before reporting a failure, so a race never parks a healthy purchase.
    if (err.kind === "invalid") {
      try {
        const fresh = await queryPlaySubscription(token, productId);
        if (fresh.acknowledged) {
          logger.info(`${logTag}: product=${productId} was already acknowledged`);
          return true;
        }
      } catch { /* fall through to the failure below */ }
    }
    logger.error(`${logTag}: acknowledge failed for product=${productId} [${err.kind}] ${err.message}`);
    return false;
  }
}
