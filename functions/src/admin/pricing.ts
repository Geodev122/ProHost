import { HttpsError } from "firebase-functions/v2/https";
import { onCall } from "../lib/callable";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

interface UpdatePricingData {
  governanceTag?: string;
}

/**
 * The only path that may write system_metadata/pricing, which now holds just the admin
 * governance tag. Prices are never stored or managed here: Google Play is the only
 * billing authority (billing/playCatalog.ts). Firestore rules deny every client write
 * to system_metadata, so this keeps a guaranteed audit trail.
 */
export const updatePricing = onCall<UpdatePricingData>(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only an Admin can change pricing.");
  }

  const data = request.data ?? {};
  const patch: Record<string, unknown> = {};
  const changes: string[] = [];

  if (data.governanceTag !== undefined) {
    if (typeof data.governanceTag !== "string" || data.governanceTag.length > 200) {
      throw new HttpsError("invalid-argument", "governanceTag must be a string under 200 characters.");
    }
    patch.governanceTag = data.governanceTag;
    changes.push(`governanceTag=${data.governanceTag}`);
  }

  if (Object.keys(patch).length === 0) {
    throw new HttpsError("invalid-argument", "No recognized pricing fields provided.");
  }

  patch.updatedAt = Date.now();
  await getFirestore().doc("system_metadata/pricing").set(patch, { merge: true });

  await recordAuditLog({
    actionType: "PRICING_ADJUSTMENT",
    details: `Admin ${auth.token.email ?? auth.uid} updated pricing: ${changes.join(", ")}`,
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "WARN",
  });

  return { updated: Object.keys(patch).filter((k) => k !== "updatedAt") };
});
