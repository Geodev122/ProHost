import { HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
/**
 * The set of roles this app recognizes, mirroring the Kotlin UserRole enum
 * (app/src/main/java/com/example/data/model/DataModels.kt). Kept as a small,
 * explicit union rather than importing from the Android module (Cloud
 * Functions and the Android app are separate build systems / languages) —
 * if UserRole ever changes on the client, this must be updated to match.
 *
 * Every account starts SPECIALIST. PRO_HOST is never self-service or free —
 * it's granted exclusively by grantEntitlement() (see entitlements.ts) the
 * moment a Google Play subscription or admin grant activates. There is no standalone
 * "request role upgrade" function anymore.
 */
export type AppRole = "SPECIALIST" | "PRO_HOST" | "ADMIN";

export const DEFAULT_ROLE: AppRole = "SPECIALIST";

export function isAppRole(value: unknown): value is AppRole {
  return value === "SPECIALIST" || value === "PRO_HOST" || value === "ADMIN";
}

/**
 * Every role-management function (grantAdminRole, revokeProHostRole,
 * setAccountSuspended, bootstrapSuperAdmin) changes an account's state in two
 * separate systems that have no shared transaction: the Auth custom claim and
 * the user_profiles Firestore document. They used to set the claim first and
 * write Firestore after with no error handling in between — a transient
 * Firestore failure (timeout, quota, network blip) after a successful claims
 * write left the two silently out of sync: the Auth token says one role/
 * suspension state, the Firestore doc (which firestore.rules' own live checks
 * like isSuspended()/liveRole() treat as authoritative for several gates)
 * says another, with no error surfaced to the admin who made the call.
 *
 * This makes the pair fail-safe instead of fully atomic (true cross-system
 * atomicity isn't available): set the claim, attempt the Firestore write, and
 * if that throws, roll the claim back to its previous value before rethrowing
 * — so the caller either sees the whole change land, or a clean error with
 * nothing changed, never a silent half-applied state. If the rollback itself
 * fails (rare — Auth already accepted one write moments earlier), that is
 * surfaced explicitly as needing manual review rather than swallowed.
 */
export async function setClaimsThenFirestore(
  auth: import("firebase-admin/auth").Auth,
  uid: string,
  previousClaims: Record<string, unknown> | undefined,
  nextClaims: Record<string, unknown>,
  applyFirestoreWrite: () => Promise<void>
): Promise<void> {
  await auth.setCustomUserClaims(uid, nextClaims);
  try {
    await applyFirestoreWrite();
  } catch (err) {
    try {
      await auth.setCustomUserClaims(uid, previousClaims ?? null);
    } catch (rollbackErr) {
      logger.error(
        "Role change failed while writing Firestore AND could not be rolled back — Auth claim and " +
          "Firestore are out of sync and need manual review.",
        { err: String(err), rollbackErr: String(rollbackErr) }
      );
      throw new HttpsError("internal", "The role change couldn't be completed. Please try again.");
    }
    logger.error("Role change failed while writing Firestore; the Auth claim change was rolled back.", { err: String(err) });
    throw new HttpsError("internal", "The role change couldn't be completed. Please try again.");
  }
}
