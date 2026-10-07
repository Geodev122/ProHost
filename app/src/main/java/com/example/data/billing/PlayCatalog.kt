package com.example.data.billing

/**
 * The one ProHost Premium subscription sold on Google Play. Google Play is the only billing
 * authority: prices, periods and offers always come from the Billing SDK, never from
 * Firestore, an admin catalog or this app. Mirrors functions/src/billing/playCatalog.ts.
 *
 * Base plans are told apart by their billing period ([PlanKind]), not by an exact id string:
 * the screen classifies the plans Play returns from ProductDetails (see [PlayOfferText.kindOf]),
 * and stored ids (ownerPackageId) go through [kindOf]. A Play Console id that differs from the
 * ones below can no longer produce a third "Premium" tile or break plan switching.
 */
object PlayCatalog {
    const val PRODUCT_ID = "package_pro_mrr"

    /** Only this product ever grants or is sent to the server. Retired plans (growth, enterprise) are ignored. */
    fun isSupportedProduct(productId: String?): Boolean = productId == PRODUCT_ID

    enum class PlanKind { MONTHLY, YEARLY }

    /** Known base plan ids (Play Console spells the original monthly id "pro-montly"). */
    const val BASE_PLAN_MONTHLY = "pro-montly"
    const val BASE_PLAN_YEARLY = "pro-yearly"
    private val MONTHLY_IDS = setOf(BASE_PLAN_MONTHLY, "pro-monthly")
    private val YEARLY_IDS = setOf(BASE_PLAN_YEARLY)

    /** ownerPackageId of an admin "Force Upgrade → ProHost" (permanent until revoked). */
    const val ADMIN_FORCED_PLAN_ID = "admin_forced"
    private const val LEGACY_UNLIMITED_GRANT_PLAN_ID = "admin_unlimited_grant"

    /** 2100-01-01 UTC: the expiry written for entitlements that never lapse. */
    const val LIFETIME_EXPIRY_MILLIS = 4_102_444_800_000L

    fun isLifetimeExpiry(expiryMillis: Long?): Boolean =
        expiryMillis != null && expiryMillis >= LIFETIME_EXPIRY_MILLIS

    fun isForcedUpgrade(ownerPackageId: String?): Boolean =
        ownerPackageId == ADMIN_FORCED_PLAN_ID || ownerPackageId == LEGACY_UNLIMITED_GRANT_PLAN_ID

    /** Monthly / yearly for a stored base plan id; null for grants and unknown ids. */
    fun kindOf(basePlanId: String?): PlanKind? {
        val id = basePlanId?.trim()?.lowercase() ?: return null
        if (isForcedUpgrade(id)) return null
        return when {
            id in MONTHLY_IDS -> PlanKind.MONTHLY
            id in YEARLY_IDS -> PlanKind.YEARLY
            "year" in id || "annual" in id -> PlanKind.YEARLY
            "month" in id || "mont" in id -> PlanKind.MONTHLY
            else -> null
        }
    }

    fun badge(kind: PlanKind): String = when (kind) {
        PlanKind.MONTHLY -> "Monthly"
        PlanKind.YEARLY -> "Yearly"
    }

    /** "monthly" / "yearly" (analytics and labels); null for anything else. */
    fun planInterval(basePlanId: String?): String? = when (kindOf(basePlanId)) {
        PlanKind.MONTHLY -> "monthly"
        PlanKind.YEARLY -> "yearly"
        null -> null
    }

    /** Display name for an entitlement's ownerPackageId. */
    fun planLabel(ownerPackageId: String?): String = when {
        isForcedUpgrade(ownerPackageId) -> "Pro Host (granted)"
        else -> kindOf(ownerPackageId)?.let { "ProHost Premium · ${badge(it)}" } ?: "ProHost Premium"
    }

    /** Short tag for badges: "Monthly", "Yearly", "Granted", "Premium". */
    fun planBadge(ownerPackageId: String?): String = when {
        isForcedUpgrade(ownerPackageId) -> "Granted"
        else -> kindOf(ownerPackageId)?.let { badge(it) } ?: "Premium"
    }
}
