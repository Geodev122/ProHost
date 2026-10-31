/**
 * Admin push for every user subscription change: new subscription, renewal, plan switch,
 * grace period, hold, pause, cancel, expiry, revoke/refund, forced upgrade, revoke by an
 * admin, unlinked Play-Store purchases and activations at risk. Sent to every ADMIN device
 * (lib/push sendPushToAdmins) and opens the Admin Console. Never throws.
 */
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { sendPushToAdmins } from "../lib/push";
import { AdminBillingEvent, planLabel } from "./playCatalog";

export type { AdminBillingEvent };
import "../lib/admin";

const TITLES: Record<AdminBillingEvent, string> = {
  NEW_SUBSCRIPTION: "New ProHost Premium subscriber",
  RENEWED: "Subscription renewed",
  PLAN_CHANGED: "Subscription plan changed",
  STATUS_CHANGED: "Subscription status changed",
  FORCED_UPGRADE: "Pro Host force-upgraded",
  ADMIN_REVOKED: "Pro Host revoked by admin",
  EXPIRED: "Pro Host access expired",
  UNLINKED_PURCHASE: "Play purchase awaiting its account",
  ACTIVATION_AT_RISK: "Paid subscription not activated",
  OWNERSHIP_MISMATCH: "Paid purchase tagged for another account",
};

const STATUS_WORDS: Record<string, string> = {
  ACTIVE: "active",
  GRACE_PERIOD: "in grace period (payment failed)",
  ON_HOLD: "on hold (payment failed, access paused)",
  PAUSED: "paused",
  CANCELED: "canceled (access until expiry)",
  EXPIRED: "expired",
  REVOKED: "revoked / refunded",
  REFUNDED: "refunded",
  PENDING: "pending payment",
};

/** "Jane Doe (U-ABC123)" — admins see names; never the raw uid when a code exists. */
async function describeUser(uid: string | null): Promise<string> {
  if (!uid) return "Unlinked account";
  try {
    const d = (await getFirestore().collection("user_profiles").doc(uid).get()).data();
    const name = (d?.fullName as string | undefined)?.trim() || (d?.email as string | undefined) || "Member";
    const code = (d?.displayCode as string | undefined) ?? uid.slice(0, 8);
    return `${name} (${code})`;
  } catch {
    return uid.slice(0, 8);
  }
}

export async function notifyAdminsOfSubscriptionChange(
  uid: string | null,
  event: AdminBillingEvent,
  details: { planId?: string | null; status?: string | null; previousStatus?: string | null; expiryMillis?: number | null; note?: string } = {}
): Promise<void> {
  try {
    const who = await describeUser(uid);
    const plan = details.planId ? planLabel(details.planId) : null;
    const status = details.status ? STATUS_WORDS[details.status] ?? details.status.toLowerCase() : null;
    const until = details.expiryMillis && details.expiryMillis < Date.UTC(2099, 0, 1)
      ? new Date(details.expiryMillis).toISOString().slice(0, 10)
      : null;
    const parts = [
      who,
      plan,
      status && details.previousStatus && details.previousStatus !== details.status
        ? `${STATUS_WORDS[details.previousStatus] ?? details.previousStatus.toLowerCase()} → ${status}`
        : status,
      until ? `until ${until}` : null,
      details.note ?? null,
    ].filter((p): p is string => !!p);
    await sendPushToAdmins(TITLES[event], parts.join(" · "), {
      category: "ADMIN_SUBSCRIPTION",
      targetTab: "admin_console",
    });
  } catch (e) {
    logger.warn(`notifyAdminsOfSubscriptionChange failed (${event})`, e);
  }
}
