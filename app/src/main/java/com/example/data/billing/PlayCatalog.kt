package com.example.data.billing

/**
 * The one ProHost Premium subscription sold on Google Play. Google Play is the only billing
 * authority: prices, periods and offers always come from the Billing SDK, never from
 * Firestore, an admin catalog or this app. Mirrors functions/src/billing/playCatalog.ts.
 */
object PlayCatalog {
    const val PRODUCT_ID = "package_pro_mrr"

    /** Base plan ids exactly as created in Play Console (the monthly id is spelled "montly"). */
    const val BASE_PLAN_MONTHLY = "pro-montly"
    const val BASE_PLAN_YEARLY = "pro-yearly"
    val BASE_PLANS = listOf(BASE_PLAN_MONTHLY, BASE_PLAN_YEARLY)

    /** ownerPackageId of an admin "Force Upgrade → ProHost" (permanent until revoked). */
    const val ADMIN_FORCED_PLAN_ID = "admin_forced"
    private const val LEGACY_UNLIMITED_GRANT_PLAN_ID = "admin_unlimited_grant"

    /** 2100-01-01 UTC: the expiry written for entitlements that never lapse. */
    const val LIFETIME_EXPIRY_MILLIS = 4_102_444_800_000L

    fun isLifetimeExpiry(expiryMillis: Long?): Boolean =
        expiryMillis != null && expiryMillis >= LIFETIME_EXPIRY_MILLIS

    fun isForcedUpgrade(ownerPackageId: String?): Boolean =
        ownerPackageId == ADMIN_FORCED_PLAN_ID || ownerPackageId == LEGACY_UNLIMITED_GRANT_PLAN_ID

    /** "monthly" / "yearly" (analytics and labels); null for anything else. */
    fun planInterval(basePlanId: String?): String? = when (basePlanId) {
        BASE_PLAN_MONTHLY -> "monthly"
        BASE_PLAN_YEARLY -> "yearly"
        else -> null
    }

    /** Display name for an entitlement's ownerPackageId. */
    fun planLabel(ownerPackageId: String?): String = when {
        ownerPackageId == BASE_PLAN_MONTHLY -> "ProHost Premium · Monthly"
        ownerPackageId == BASE_PLAN_YEARLY -> "ProHost Premium · Yearly"
        isForcedUpgrade(ownerPackageId) -> "Pro Host (granted)"
        else -> "ProHost Premium"
    }

    /** Short tag for badges: "Monthly", "Yearly", "Granted", "Premium". */
    fun planBadge(ownerPackageId: String?): String = when {
        ownerPackageId == BASE_PLAN_MONTHLY -> "Monthly"
        ownerPackageId == BASE_PLAN_YEARLY -> "Yearly"
        isForcedUpgrade(ownerPackageId) -> "Granted"
        else -> "Premium"
    }
}
