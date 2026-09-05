/**
 * The set of roles this app recognizes, mirroring the Kotlin UserRole enum
 * (app/src/main/java/com/example/data/model/DataModels.kt). Kept as a small,
 * explicit union rather than importing from the Android module (Cloud
 * Functions and the Android app are separate build systems / languages) —
 * if UserRole ever changes on the client, this must be updated to match.
 */
export type AppRole = "PROFESSIONAL" | "SPACE_OWNER" | "ADMIN";

export const DEFAULT_ROLE: AppRole = "PROFESSIONAL";

/** Roles a signed-in user may request for themselves via requestRoleUpgrade. */
export const SELF_SERVICE_UPGRADABLE_ROLES: readonly AppRole[] = ["SPACE_OWNER"];

export function isAppRole(value: unknown): value is AppRole {
  return value === "PROFESSIONAL" || value === "SPACE_OWNER" || value === "ADMIN";
}
