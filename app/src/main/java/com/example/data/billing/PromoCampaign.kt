package com.example.data.billing

/**
 * The marketed promo-code campaign (promo_campaigns/current, server-written; see
 * functions/src/billing/promoCampaign.ts). Google Play holds the real code and its benefit; this
 * only drives the Premium screen's countdown card and the red dot on "Redeem code".
 */
data class PromoCampaign(
    val active: Boolean,
    val code: String,
    val title: String,
    val benefit: String,
    val endsAtMillis: Long,
    val maxRedemptions: Int?,
    val redeemed: Int,
    /** The base plan the Play promotion is attached to ("monthly"/"yearly"); the card's checkout opens it. */
    val basePlanKind: PlayCatalog.PlanKind?
) {
    /** Shown only while switched on, before its end and with spots left. */
    fun isLive(nowMillis: Long): Boolean =
        active && code.isNotBlank() && nowMillis < endsAtMillis && (spotsLeft ?: 1) > 0

    val spotsLeft: Int? get() = maxRedemptions?.let { (it - redeemed).coerceAtLeast(0) }

    data class Countdown(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

    companion object {
        fun fromMap(m: Map<String, Any?>?): PromoCampaign? {
            if (m == null) return null
            val code = (m["code"] as? String)?.trim().orEmpty()
            val ends = (m["endsAt"] as? Number)?.toLong() ?: return null
            return PromoCampaign(
                active = m["active"] == true,
                code = code,
                title = (m["title"] as? String)?.takeIf { it.isNotBlank() } ?: "Early launch promo",
                benefit = (m["benefit"] as? String)?.takeIf { it.isNotBlank() } ?: "Your first month free",
                endsAtMillis = ends,
                maxRedemptions = (m["maxRedemptions"] as? Number)?.toInt()?.takeIf { it > 0 },
                redeemed = (m["redeemed"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                basePlanKind = when ((m["basePlanKind"] as? String)?.lowercase()) {
                    "yearly" -> PlayCatalog.PlanKind.YEARLY
                    "monthly" -> PlayCatalog.PlanKind.MONTHLY
                    else -> null
                }
            )
        }

        /** Pure: time left until [endMillis], never negative. */
        fun countdown(nowMillis: Long, endMillis: Long): Countdown {
            val s = ((endMillis - nowMillis) / 1000).coerceAtLeast(0)
            return Countdown(s / 86_400, (s % 86_400) / 3_600, (s % 3_600) / 60, s % 60)
        }
    }
}
