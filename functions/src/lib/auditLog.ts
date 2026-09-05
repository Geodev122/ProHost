import { getFirestore } from "firebase-admin/firestore";
import { randomUUID } from "crypto";

/**
 * Server-side audit log writer. Uses the Admin SDK, which bypasses Firestore
 * security rules — this is intentionally the ONLY way audit_security_logs
 * documents get written once Phase 7's rules lock that collection down to
 * "write: if false" for every client.
 */
export async function recordAuditLog(params: {
  actionType: string;
  details: string;
  actorEmail: string;
  severity?: "INFO" | "WARN" | "SECURE";
}): Promise<void> {
  const db = getFirestore();
  const id = "LOG-" + randomUUID().slice(0, 8).toUpperCase();
  await db.collection("audit_security_logs").doc(id).set({
    id,
    timestamp: Date.now(),
    actionType: params.actionType,
    details: params.details,
    actorEmail: params.actorEmail,
    severity: params.severity ?? "INFO",
    ipAddress: "cloud-function",
  });
}
