import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { google } from "googleapis";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import { PACKAGE_NAME, getPlayPublisher, queryPlaySubscription } from "./billingHelpers";
import { classifyPlayError } from "./playSubscription";
import { PENDING_COLLECTION, activatePlayPurchase, parkPendingActivation, resolvePendingActivation } from "./activatePurchase";
import { linkKey, setAdminOverride } from "./purchaseLinks";
import { notifyAdminsOfSubscriptionChange } from "./adminBillingAlerts";
import { healthFromProbe, hoursUntilRefund, rescueHint } from "./billingRescueLogic";
import "../lib/admin";
import { isSupportedProduct } from "./playCatalog";

/**
 * Admin › Packages: billing health check and "Payments needing attention".
 *  - billingHealthCheck     can the server use the Play Developer API, and does RTDN arrive?
 *  - adminBillingPending    paid purchases that are not active yet (parked or unlinked)
 *  - adminActivatePurchase  Retry / Activate for this user: verify with Play, grant,
 *                           acknowledge (before Play's 3-day refund), notify
 * Purchase tokens never leave the server; rows are addressed by their document id.
 */

function requireAdmin(request: CallableRequest<unknown>): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (request.auth.token.role !== "ADMIN") throw new HttpsError("permission-denied", "Admins only.");
  return request.auth.uid;
}

async function describeUser(uid: string | undefined): Promise<{ uid: string | null; name: string; code: string }> {
  if (!uid) return { uid: null, name: "Not linked", code: "" };
  const d = (await getFirestore().collection("user_profiles").doc(uid).get()).data();
  return {
    uid,
    name: (d?.fullName as string | undefined)?.trim() || (d?.email as string | undefined) || "Member",
    code: (d?.displayCode as string | undefined) ?? "",
  };
}

export const billingHealthCheck = onCall(async (request) => {
  requireAdmin(request);
  let serviceAccount: string | null = null;
  try {
    const creds = await new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/androidpublisher"],
    }).getCredentials();
    serviceAccount = creds.client_email ?? null;
  } catch {
    // Unknown on some runtimes; the probe result below still tells the story.
  }
  let kind: ReturnType<typeof classifyPlayError>["kind"] | null = null;
  let httpStatus: number | null = null;
  let message = "Google Play answered.";
  try {
    const publisher = await getPlayPublisher();
    await publisher.purchases.subscriptionsv2.get({ packageName: PACKAGE_NAME, token: "prohost-health-probe" });
  } catch (e) {
    const err = classifyPlayError(e);
    kind = err.kind;
    httpStatus = err.status;
    message = err.message;
  }
  const playApi = healthFromProbe(kind);
  const health = (await getFirestore().doc("app_config/billing_health").get()).data() ?? {};
  const db = getFirestore();
  const [pendingSnap, unlinkedSnap] = await Promise.all([
    db.collection(PENDING_COLLECTION).where("resolved", "==", false).count().get(),
    db.collection("play_billing_unresolved").where("resolved", "==", false).count().get(),
  ]);
  return {
    playApi,
    httpStatus,
    message: playApi === "ok" ? "The server can verify and acknowledge Google Play purchases." : message,
    serviceAccount,
    lastRtdnAt: typeof health.lastRtdnAt === "number" ? health.lastRtdnAt : null,
    lastSelfTestAt: typeof health.lastSelfTestAt === "number" ? health.lastSelfTestAt : null,
    pendingCount: pendingSnap.data().count,
    unlinkedCount: unlinkedSnap.data().count,
  };
});

export const adminBillingPending = onCall(async (request) => {
  requireAdmin(request);
  const db = getFirestore();
  const now = Date.now();
  const [pending, unlinked] = await Promise.all([
    db.collection(PENDING_COLLECTION).where("resolved", "==", false).limit(50).get(),
    db.collection("play_billing_unresolved").where("resolved", "==", false).limit(50).get(),
  ]);
  // Play's 3-day refund clock runs from the purchase, and stops once it is acknowledged:
  // read both from Play (best effort — fall back to when the row was parked).
  const playClock = async (token: string | undefined, productId: string | undefined, fallback: number) => {
    if (!token) return { since: fallback, acknowledged: false };
    try {
      const sub = await queryPlaySubscription(token, productId || undefined);
      return { since: sub.startMillis ?? fallback, acknowledged: sub.acknowledged };
    } catch {
      return { since: fallback, acknowledged: false };
    }
  };
  const rows = [];
  for (const doc of pending.docs) {
    const d = doc.data();
    const createdAt = typeof d.createdAt === "number" ? d.createdAt : now;
    const clock = await playClock(d.purchaseToken as string | undefined, d.productId as string | undefined, createdAt);
    rows.push({
      kind: "pending",
      id: doc.id,
      user: await describeUser(d.uid as string | undefined),
      productId: d.productId ?? "",
      lastError: String(d.lastError ?? ""),
      attempts: d.attempts ?? 0,
      needsAdmin: d.needsAdmin === true,
      createdAt,
      hoursLeft: clock.acknowledged ? null : hoursUntilRefund(clock.since, now),
      hint: rescueHint(d.lastError as string | undefined, d.needsAdmin === true),
    });
  }
  for (const doc of unlinked.docs) {
    const d = doc.data();
    const createdAt = typeof d.timestamp === "number" ? d.timestamp : now;
    const clock = await playClock(d.purchaseToken as string | undefined, d.productId as string | undefined, createdAt);
    rows.push({
      kind: "unlinked",
      id: doc.id,
      user: await describeUser(undefined),
      productId: d.productId ?? "",
      orderId: d.orderId ?? "",
      lastError: "Bought in the Play Store (e.g. promo code) — not linked to an account yet",
      attempts: 0,
      needsAdmin: true,
      createdAt,
      // RTDN acknowledges supported unlinked purchases; retired plans keep the refund clock.
      hoursLeft: clock.acknowledged ? null : hoursUntilRefund(clock.since, now),
      hint: "Ask the buyer which ProHost account it is for, then Activate for that account.",
    });
  }
  rows.sort((a, b) => (a.createdAt as number) - (b.createdAt as number));
  return { rows };
});

export const adminActivatePurchase = onCall<{
  kind?: "pending" | "unlinked";
  id?: string;
  targetUid?: string;
  confirmReassign?: boolean;
}>(async (request) => {
  const adminUid = requireAdmin(request);
  const { kind, id } = request.data ?? {};
  if ((kind !== "pending" && kind !== "unlinked") || !id) {
    throw new HttpsError("invalid-argument", "kind and id are required.");
  }
  const db = getFirestore();
  const ref = db.collection(kind === "pending" ? PENDING_COLLECTION : "play_billing_unresolved").doc(id);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "That payment is no longer listed.");
  const row = snap.data() ?? {};
  const token = row.purchaseToken as string | undefined;
  const productId = (row.productId as string | undefined) ?? "";
  const targetUid = (request.data?.targetUid?.trim() || (row.uid as string | undefined) || "").trim();
  if (!token) throw new HttpsError("failed-precondition", "This record has no purchase token.");
  if (!targetUid) throw new HttpsError("invalid-argument", "Choose the ProHost account to activate it for.");
  if (!(await db.collection("user_profiles").doc(targetUid).get()).exists) {
    throw new HttpsError("not-found", "That ProHost account doesn't exist.");
  }

  // An explicit admin activation undoes an earlier admin revoke of this subscription.
  await db.collection("subscriptions").doc(linkKey(token))
    .update({ adminRevoked: false })
    .catch(() => undefined);

  let purchase;
  try {
    purchase = await queryPlaySubscription(token, productId);
  } catch (e) {
    const err = classifyPlayError(e);
    if (kind === "pending") await ref.update({ lastError: `${err.kind}: ${err.message}`.slice(0, 500), updatedAt: Date.now() });
    return { status: "play_error", kind: err.kind, message: err.message };
  }

  const taggedFor = purchase.obfuscatedAccountId;
  const reassigning = (taggedFor && taggedFor !== targetUid) || (row.needsAdmin === true) || kind === "unlinked";
  if (taggedFor && taggedFor !== targetUid && request.data?.confirmReassign !== true) {
    const owner = await describeUser(taggedFor);
    return { status: "needs_confirm", taggedFor: owner };
  }
  // Activating a retired plan (package_growth_mrr, …) is the admin's explicit approval of it.
  const retired = !isSupportedProduct(purchase.productId || productId);
  if (reassigning || retired) await setAdminOverride(targetUid, token, purchase.productId || productId, adminUid);

  const outcome = await activatePlayPurchase(targetUid, token, purchase.productId || productId, "adminActivatePurchase");
  if (outcome.status === "granted") {
    if (!outcome.acknowledged) {
      await parkPendingActivation(targetUid, token, outcome.productId, "acknowledge failed", "adminActivatePurchase");
      return { status: "granted_not_acknowledged" };
    }
    if (kind === "pending") await resolvePendingActivation(token, "granted_by_admin");
    // RTDN adds one unresolved row per notification: close every row for this token.
    const sameToken = await db.collection("play_billing_unresolved")
      .where("purchaseToken", "==", token).where("resolved", "==", false).get();
    await Promise.all([ref, ...sameToken.docs.map((d) => d.ref)].map((r) =>
      r.update({ resolved: true, resolvedUid: targetUid, resolvedAt: Date.now() }).catch(() => undefined)));
    // Any parked copy under the same token is done too.
    await db.collection(PENDING_COLLECTION).doc(linkKey(token)).update({ resolved: true, outcome: "granted_by_admin", resolvedAt: Date.now() })
      .catch(() => undefined);
    await sendPushToUser(targetUid, "Your Pro Host plan is active", "Your Google Play payment is confirmed — your Pro Host plan is now active.", {
      category: "PACKAGE_ACTIVATED",
      targetTab: "owner_subscriptions",
    });
    await recordAuditLog({
      actionType: "PLAY_BILLING_ADMIN_ACTIVATED",
      details: `Admin ${request.auth?.token.email ?? adminUid} activated ${outcome.planId} for uid=${targetUid}` +
        (taggedFor && taggedFor !== targetUid ? ` (purchase was tagged for uid=${taggedFor})` : ""),
      actorEmail: request.auth?.token.email ?? "admin",
      severity: "SECURE",
    });
    await notifyAdminsOfSubscriptionChange(targetUid, "NEW_SUBSCRIPTION", {
      planId: outcome.planId, expiryMillis: outcome.expiryMillis, note: "activated by an admin",
    });
    return { status: "granted", planId: outcome.planId, expiryMillis: outcome.expiryMillis };
  }
  if (outcome.status === "play_error") return { status: "play_error", kind: outcome.error.kind, message: outcome.error.message };
  return { status: outcome.status };
});

/**
 * Admin › Packages › Billing health › "Send test": publishes a synthetic Play test
 * notification (selfTest: true) to the RTDN topic. playBillingRtdn records it as
 * app_config/billing_health.lastSelfTestAt, proving topic → function independently of
 * Play Console (which still has to publish to the same topic — lastRtdnAt).
 */
export const billingRtdnSelfTest = onCall(async (request) => {
  requireAdmin(request);
  const projectId = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || "prohost-f766f";
  const auth = new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/pubsub"] });
  const pubsub = google.pubsub({ version: "v1", auth });
  const message = {
    version: "1.0",
    packageName: PACKAGE_NAME,
    eventTimeMillis: String(Date.now()),
    selfTest: true,
    testNotification: { version: "1.0" },
  };
  try {
    await pubsub.projects.topics.publish({
      topic: `projects/${projectId}/topics/play-billing-rtdn`,
      requestBody: { messages: [{ data: Buffer.from(JSON.stringify(message)).toString("base64") }] },
    });
  } catch (e) {
    throw new HttpsError("unavailable", "Couldn't publish the test notification. Try again in a minute.");
  }
  return { published: true };
});
