/**
 * Server-side half of Maintenance workflow tasks that need the functions' own credentials
 * (the CI service account is not invited in Play Console, so it gets 403 from the Play API).
 * The workflow writes `maintenance_requests/{id}` { task } with the Admin SDK; this trigger runs
 * the task and writes `{ status, result }` back (JSON string). The collection is server-only.
 *
 * Tasks:
 *  - playCatalog        base plans, regional prices and every offer (phases, eligibility, regions) + lintOffers warnings
 *  - playOfferFix       moves a promo offer stranded on an inactive base plan onto the live monthly plan and
 *                       widens claimed offers limited to "any subscription in this app" (dry run unless arg "apply")
 *  - aiProbe            which Claude model/region this project can call on Vertex AI (Instagram DM agent)
 *  - playSubscriptions  every recorded Play subscription as Play sees it now: plan, offer, state,
 *                       bought in the app (tagged) or outside it (Play Store / promo code),
 *                       promotion type/code. Never returns purchase tokens or uids.
 */
import * as logger from "firebase-functions/logger";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import "../lib/admin";
import { lintOffers, readPlayCatalog } from "../billing/playCatalogLive";
import { playOfferFix } from "../billing/playOfferFix";
import { PACKAGE_NAME, getPlayPublisher } from "../billing/billingHelpers";
import { classifyPlayError } from "../billing/playSubscription";
import { listClaudeQuotas, probeVertex, requestClaudeQuota } from "../instagram/aiLlm";

async function playSubscriptions(): Promise<unknown> {
  const db = getFirestore();
  const publisher = await getPlayPublisher();
  const snap = await db.collection("subscriptions").limit(100).get();
  const rows = [];
  for (const doc of snap.docs) {
    const d = doc.data();
    const token = typeof d.purchaseToken === "string" ? d.purchaseToken : "";
    const owner = typeof d.userId === "string" ? (await db.collection("user_profiles").doc(d.userId).get()).get("displayCode") : null;
    const row: Record<string, unknown> = {
      id: doc.id.slice(0, 10), owner: owner ?? null, recordedPlan: d.basePlanId ?? null,
      recordedStatus: d.status ?? null, adminRevoked: d.adminRevoked === true,
    };
    if (!token) { rows.push({ ...row, play: "no token stored" }); continue; }
    try {
      const v2 = (await publisher.purchases.subscriptionsv2.get({ packageName: PACKAGE_NAME, token })).data;
      const item = v2.lineItems?.[0];
      Object.assign(row, {
        state: v2.subscriptionState, ack: v2.acknowledgementState, startTime: v2.startTime,
        productId: item?.productId, basePlanId: item?.offerDetails?.basePlanId, offerId: item?.offerDetails?.offerId ?? null,
        offerTags: item?.offerDetails?.offerTags ?? [], expiry: item?.expiryTime, autoRenew: item?.autoRenewingPlan?.autoRenewEnabled ?? null,
        boughtInApp: !!v2.externalAccountIdentifiers?.obfuscatedExternalAccountId,
        region: v2.regionCode, testPurchase: !!v2.testPurchase,
      });
      if (item?.productId) {
        try {
          const v1 = (await publisher.purchases.subscriptions.get({ packageName: PACKAGE_NAME, subscriptionId: item.productId, token })).data;
          // promotionType 0 = one-time code, 1 = vanity (custom) code; absent = no promo code.
          row.promotionType = v1.promotionType ?? null;
          row.promotionCode = v1.promotionCode ?? null;
        } catch {
          row.promotionType = "unreadable";
        }
      }
    } catch (e) {
      row.play = `${classifyPlayError(e).kind}: ${classifyPlayError(e).message.slice(0, 120)}`;
    }
    rows.push(row);
  }
  return { count: rows.length, rows };
}

export const onMaintenanceRequest = onDocumentCreated(
  { document: "maintenance_requests/{id}", timeoutSeconds: 300, memory: "512MiB" },
  async (event) => {
    const ref = event.data?.ref;
    const task = event.data?.get("task");
    if (!ref) return;
    try {
      const result = task === "playCatalog" ? await readPlayCatalog().then((c) => ({ ...c, lint: lintOffers(c.basePlans, Date.now()) }))
        : task === "playSubscriptions" ? await playSubscriptions()
        : task === "aiProbe" ? await probeVertex()
        : task === "aiQuota" ? (event.data?.get("arg") === "request" ? await requestClaudeQuota() : await listClaudeQuotas())
        : task === "playOfferFix" ? await playOfferFix(event.data?.get("arg") === "apply")
        : { error: `unknown task ${String(task)}` };
      await ref.update({ status: "done", result: JSON.stringify(result), finishedAt: Date.now() });
    } catch (e) {
      const err = classifyPlayError(e);
      logger.warn("maintenance_request_failed", { task, kind: err.kind, status: err.status });
      await ref.update({ status: "error", result: JSON.stringify({ kind: err.kind, status: err.status, message: err.message.slice(0, 300) }), finishedAt: Date.now() });
    }
  }
);
