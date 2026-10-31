import { getFirestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { onCall } from "../lib/callable";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import { grantProHost } from "../billing/entitlementManager";
import { ADMIN_FORCED_PLAN_ID, LIFETIME_EXPIRY_MILLIS } from "../billing/playCatalog";
import { notifyAdminsOfSubscriptionChange } from "../billing/adminBillingAlerts";
import "../lib/admin";

/**
 * Admin → Packages → Force Upgrade → ProHost: the only admin billing action. For VIP
 * access, customer-service recovery and internal testing. Permanent until an admin
 * revokes it (Users tab → Revoke Pro Host, revokeProHostRole.ts); Google Play sync and
 * expiry never touch it (entitlementSource "admin_forced").
 */
export const forceProHostUpgrade = onCall<{ targetUid?: string }>(async (request) => {
  const auth = request.auth;
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can force a Pro Host upgrade.");
  }
  const targetUid = request.data?.targetUid?.trim();
  if (!targetUid) throw new HttpsError("invalid-argument", "targetUid is required.");

  let target;
  try {
    target = await getAuth().getUser(targetUid);
  } catch {
    throw new HttpsError("not-found", "That account no longer exists.");
  }
  if (target.customClaims?.role === "ADMIN") {
    throw new HttpsError("failed-precondition", "Admins already have full access.");
  }
  // grantProHost skips accounts without a profile; report that instead of a false success.
  if (!(await getFirestore().collection("user_profiles").doc(targetUid).get()).exists) {
    throw new HttpsError("failed-precondition", "This account hasn't finished signing up yet (no profile).");
  }

  await grantProHost(targetUid, {
    source: "admin_forced",
    planId: ADMIN_FORCED_PLAN_ID,
    expiryMillis: LIFETIME_EXPIRY_MILLIS,
    orderId: `admin:${auth.uid}`,
  });
  await recordAuditLog({
    actionType: "PRO_HOST_FORCE_UPGRADE",
    details: `Admin ${auth.token.email ?? auth.uid} forced a Pro Host upgrade for ${targetUid} (${target.email ?? "no email"}).`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });
  await sendPushToUser(targetUid, "You're a Pro Host", "An administrator upgraded your account to Pro Host. You can publish workspace listings now.", {
    category: "PACKAGE_ACTIVATED",
    targetTab: "owner_subscriptions",
  });
  await notifyAdminsOfSubscriptionChange(targetUid, "FORCED_UPGRADE", {
    planId: ADMIN_FORCED_PLAN_ID,
    note: `by ${auth.token.email ?? "an admin"}`,
  });
  return { ok: true, targetUid };
});
