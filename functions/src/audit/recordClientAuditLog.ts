import { onCall, HttpsError } from "firebase-functions/v2/https";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

interface RecordClientAuditLogData {
  actionType?: string;
  details?: string;
  severity?: "INFO" | "WARN" | "SECURE";
}

const ALLOWED_SEVERITIES = ["INFO", "WARN", "SECURE"];

/**
 * Every actionType the app's own client code actually sends here — mirrors
 * ProHostRepository.addAuditLog's real call sites (app/src/main/java/com/
 * example/data/repository/ProHostRepository.kt), plus the few from
 * ProHostViewModel.kt/AdminViewModel.kt/ProHostMessagingService.kt. Deliberately
 * does NOT include actionTypes that are only ever written by a dedicated
 * Cloud Function directly (setListingVerification's VERIFICATION_OVERRIDE,
 * grantAdminRole's ADMIN_ROLE_GRANTED, setAccountSuspended's ACCOUNT_SUSPENDED,
 * WHISH_PAYMENT_SUCCESS, etc.) — without this allowlist, any signed-in user
 * could call this function directly (bypassing the UI entirely) with one of
 * those exact strings and insert a fabricated entry into the real audit log
 * that's, at a glance, indistinguishable from a genuine Admin/server action
 * (actorEmail is still correctly their own — see below — but the actionType
 * and details are otherwise free text). Extending this app's audit-logged
 * actions means adding the new string here too, not just in the Kotlin call
 * site.
 */
const ALLOWED_CLIENT_ACTION_TYPES = new Set([
  "OFFLINE_TX_RECOVERED",
  "LISTING_CREATED",
  "LISTING_DRAFT_SAVED",
  "LISTING_UPDATED",
  "LISTING_DELETED",
  "LISTING_BLOCKED_PACKAGE_LIMIT",
  "LISTING_BLOCKED_PAYG_CREDIT",
  "USER_UPDATED",
  "USER_DELETED",
  "SCHEMA_ITEM_ADDED",
  "SCHEMA_ITEM_TOGGLED",
  "SCHEMA_ITEM_DELETED",
  "SCHEMA_RESET_DEFAULTS",
  "HOST_SELF_VERIFICATION",
  "RENTAL_REQUEST_EDIT_SUBMITTED",
  "RENTAL_REQUEST_SUBMITTED",
  "RENTAL_REQUEST_ACCEPTED",
  "RENTAL_REQUEST_DECLINED",
  "RENTAL_REQUEST_CANCELLED",
  "ACCEPTED_BOOKING_CANCELLED",
  "SCHEDULE_BLACKOUT_ADDED",
  "OPERATING_SCHEDULE_UPDATED",
  "RENTAL_FORMULA_ADDED",
  "SUBDIVISION_ADDED",
  "SUBDIVISION_REMOVED",
  "MEMBER_REGISTRATION",
  "USER_LOGIN_SUCCESS",
  "USER_LOGOUT",
  "WHATSAPP_INQUIRY_SPECIALIST_TO_HOST",
  "WHATSAPP_INQUIRY_HOST_TO_SPECIALIST",
  "FCM_TOKEN_REGISTERED",
]);

/**
 * The only remaining path for routine, non-admin audit log entries (booking
 * created/accepted, document removed, profile edited, etc.) now that
 * audit_security_logs denies every direct client write. Unlike the old
 * client-side ProSpaceRepository.addAuditLog, actorEmail is NOT accepted from
 * the caller — it's always the caller's own verified token email, closing the
 * exact gap the original audit flagged (AuditSecurityLog.actorEmail being a
 * client-supplied, spoofable value). Money- and role-changing actions have
 * their own dedicated functions (admin/pricing.ts, admin/listings.ts,
 * roles/*, payments/*) which write their own audit entries directly and
 * don't go through this generic path — actionType is now allowlisted
 * (ALLOWED_CLIENT_ACTION_TYPES) so this path can't be used to impersonate one
 * of those either.
 */
export const recordClientAuditLog = onCall<RecordClientAuditLogData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }

  const { actionType, details, severity } = request.data ?? {};
  if (!actionType || typeof actionType !== "string" || !details || typeof details !== "string") {
    throw new HttpsError("invalid-argument", "actionType and details are required strings.");
  }
  if (!ALLOWED_CLIENT_ACTION_TYPES.has(actionType)) {
    throw new HttpsError("invalid-argument", `Unknown actionType: ${actionType}`);
  }

  await recordAuditLog({
    actionType: actionType.slice(0, 100),
    details: details.slice(0, 2000),
    actorEmail: auth.token.email ?? auth.uid,
    severity: ALLOWED_SEVERITIES.includes(severity ?? "") ? (severity as "INFO" | "WARN" | "SECURE") : "INFO",
  });

  return { ok: true };
});
