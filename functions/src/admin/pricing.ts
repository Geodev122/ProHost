import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

interface UpdatePricingData {
  monthlySubscriptionFeeUsd?: number;
  governanceTag?: string;
}

const NUMERIC_FIELDS: (keyof UpdatePricingData)[] = [
  "monthlySubscriptionFeeUsd",
];

/**
 * The only path that may write system_metadata/pricing. Package price/listing
 * limit/validity days no longer live here — they're admin-managed directly on
 * package_plans/main (add/edit/toggle/delete via ProHostRepository's
 * addPackagePlan/updatePackagePlan/togglePackagePlan/deletePackagePlan, same
 * direct-admin-write pattern as SchemaItem CRUD). This callable only remains
 * for the unrelated monthlySubscriptionFeeUsd/governanceTag fields. Firestore
 * rules deny every client write to system_metadata, so this is the sole way
 * an admin can change what's left here, with a guaranteed audit trail.
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

  for (const field of NUMERIC_FIELDS) {
    const value = data[field];
    if (value === undefined) continue;
    if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
      throw new HttpsError("invalid-argument", `${field} must be a non-negative number.`);
    }
    patch[field] = value;
    changes.push(`${field}=${value}`);
  }

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
