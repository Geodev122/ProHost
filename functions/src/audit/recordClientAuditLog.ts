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
 * The only remaining path for routine, non-admin audit log entries (booking
 * created/accepted, document removed, profile edited, etc.) now that
 * audit_security_logs denies every direct client write. Unlike the old
 * client-side ProSpaceRepository.addAuditLog, actorEmail is NOT accepted from
 * the caller — it's always the caller's own verified token email, closing the
 * exact gap the original audit flagged (AuditSecurityLog.actorEmail being a
 * client-supplied, spoofable value). Money- and verification-status-changing
 * actions have their own dedicated functions (admin/pricing.ts,
 * admin/verification.ts, admin/listings.ts, payments/*) which write their own
 * audit entries directly and don't go through this generic path.
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

  await recordAuditLog({
    actionType: actionType.slice(0, 100),
    details: details.slice(0, 2000),
    actorEmail: auth.token.email ?? auth.uid,
    severity: ALLOWED_SEVERITIES.includes(severity ?? "") ? (severity as "INFO" | "WARN" | "SECURE") : "INFO",
  });

  return { ok: true };
});
