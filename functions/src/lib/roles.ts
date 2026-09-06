/**
 * The set of roles this app recognizes, mirroring the Kotlin UserRole enum
 * (app/src/main/java/com/example/data/model/DataModels.kt). Kept as a small,
 * explicit union rather than importing from the Android module (Cloud
 * Functions and the Android app are separate build systems / languages) —
 * if UserRole ever changes on the client, this must be updated to match.
 *
 * Every account starts SPECIALIST. PRO_HOST is never self-service or free —
 * it's granted exclusively by grantEntitlement() (see entitlements.ts) the
 * moment a real OWNER_PACKAGE or PAYG_LISTING Whish payment settles. There is
 * no standalone "request role upgrade" function anymore.
 */
export type AppRole = "SPECIALIST" | "PRO_HOST" | "ADMIN";

export const DEFAULT_ROLE: AppRole = "SPECIALIST";

export function isAppRole(value: unknown): value is AppRole {
  return value === "SPECIALIST" || value === "PRO_HOST" || value === "ADMIN";
}
