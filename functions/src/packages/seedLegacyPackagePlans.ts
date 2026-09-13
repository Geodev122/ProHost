import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { recordAuditLog } from "../lib/auditLog";
import "../lib/admin";

/**
 * ADMIN-ONLY, IDEMPOTENT. Creates package_plans/main with the two legacy
 * tiered packages (ids matching the deleted OwnerPackageTier enum's names,
 * "LIMITED_3_TIER"/"UNLIMITED_TIER") IF THE DOCUMENT DOESN'T ALREADY EXIST —
 * this is the one-time server-side seed the Phase 15 design always depended
 * on ("seed package_plans/main ... before anything else"), which nothing
 * else in this codebase ever actually performs. Without it, every already-
 * migrated Pro Host's real ownerPackageId ("LIMITED_3_TIER"/"UNLIMITED_TIER")
 * has no matching PackagePlan document for withinListingLimit() to resolve,
 * so they'd be silently blocked from publishing until an admin manually
 * re-creates matching packages by hand.
 *
 * Never overwrites an existing document — if an admin has already created
 * any packages (including these two, under any price), this is a safe no-op.
 * Meant to be triggered once, manually, via a temporary admin-only control
 * (removed again once confirmed run) — not wired into any regular user flow.
 */
export const seedLegacyPackagePlans = onCall(async (request) => {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  if (auth.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Admin only.");
  }

  const db = getFirestore();
  const ref = db.doc("package_plans/main");
  const existing = await ref.get();

  if (existing.exists) {
    return { seeded: false };
  }

  await ref.set({
    packages: {
      LIMITED_3_TIER: {
        id: "LIMITED_3_TIER",
        name: "Package 2: Pro (3 Listings Limit)",
        description: "Host and operate up to 3 active workspaces under a bundled monthly fee",
        badgeName: "3-Listing Pro",
        priceUsd: 3.99,
        listingLimit: 3,
        validityDays: 30,
        isEnabled: true,
        sortOrder: 0,
      },
      UNLIMITED_TIER: {
        id: "UNLIMITED_TIER",
        name: "Package 3: Enterprise (All-In Unlimited)",
        description: "Publish unlimited active workspace listings with priority platform exposure",
        badgeName: "All-In Unlimited",
        priceUsd: 8.99,
        listingLimit: null,
        validityDays: 30,
        isEnabled: true,
        sortOrder: 1,
      },
    },
  });

  await recordAuditLog({
    actionType: "PACKAGE_PLANS_LEGACY_SEEDED",
    details: "One-time seed: created package_plans/main with the two legacy tiered packages (LIMITED_3_TIER/UNLIMITED_TIER) since no document existed yet.",
    actorEmail: auth.token.email ?? "system@prohost.app",
    severity: "SECURE",
  });

  return { seeded: true };
});
