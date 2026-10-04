package com.example.data.billing

import com.android.billingclient.api.ProductDetails

/**
 * Human-readable price terms for the offer PlayBillingManager.launchSubscriptionPurchase
 * uses ([preferredOffer]), e.g. "Free for 7 days, then $4.99 / month".
 * Play policy requires price, billing period and trial terms to be shown before purchase.
 */
object PlayOfferText {

    /**
     * The one offer both the price text and the purchase sheet use for [basePlanId]
     * (null = any base plan). Play only returns developer offers the user is eligible for,
     * in no guaranteed order: prefer one with a free trial, then one with an intro price,
     * else the plain base plan.
     */
    fun preferredOffer(details: ProductDetails?, basePlanId: String? = null): ProductDetails.SubscriptionOfferDetails? {
        val offers = details?.subscriptionOfferDetails.orEmpty()
            .filter { basePlanId == null || it.basePlanId == basePlanId }
        if (offers.isEmpty()) return null
        fun phases(o: ProductDetails.SubscriptionOfferDetails) = o.pricingPhases.pricingPhaseList
        return offers.firstOrNull { o -> o.offerId != null && phases(o).dropLast(1).any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { o -> o.offerId != null && phases(o).size > 1 }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.first()
    }

    /** Base plans Play returned for this product, in [PlayCatalog.BASE_PLANS] order first. */
    fun basePlans(details: ProductDetails?): List<String> {
        val ids = details?.subscriptionOfferDetails.orEmpty().map { it.basePlanId }.distinct()
        return PlayCatalog.BASE_PLANS.filter { it in ids } + ids.filterNot { it in PlayCatalog.BASE_PLANS }
    }

    private fun phases(details: ProductDetails?, basePlanId: String?) =
        preferredOffer(details, basePlanId)?.pricingPhases?.pricingPhaseList

    fun describe(details: ProductDetails?, basePlanId: String? = null): String? {
        val phases = phases(details, basePlanId)
        if (phases.isNullOrEmpty()) return null
        val recurring = phases.last()
        val base = "${recurring.formattedPrice} / ${per(recurring.billingPeriod)}"
        val intro = phases.dropLast(1).joinToString(", ") { phase ->
            val span = duration(phase.billingPeriod, phase.billingCycleCount.coerceAtLeast(1))
            if (phase.priceAmountMicros == 0L) "Free for $span"
            else "${phase.formattedPrice} / ${per(phase.billingPeriod)} for $span"
        }
        return if (intro.isEmpty()) base else "$intro, then $base"
    }

    /** Line under a large price: "per month", or the full terms when there's an intro offer. */
    fun caption(details: ProductDetails?, basePlanId: String? = null): String? {
        val phases = phases(details, basePlanId)
        if (phases.isNullOrEmpty()) return null
        return if (phases.size > 1) describe(details, basePlanId) else "per ${per(phases.last().billingPeriod)}"
    }

    /** Recurring price only, e.g. "$4.99". */
    fun recurringPrice(details: ProductDetails?, basePlanId: String? = null): String? =
        phases(details, basePlanId)?.lastOrNull()?.formattedPrice

    /** "per month", "per year", etc. for the recurring phase — null when no product. */
    fun billingPeriodLabel(details: ProductDetails?, basePlanId: String? = null): String? {
        val phase = phases(details, basePlanId)?.lastOrNull() ?: return null
        return "per ${per(phase.billingPeriod)}"
    }

    /** Short free-trial label, e.g. "Free for 7 days". Null when no trial phase. */
    fun trialLabel(details: ProductDetails?, basePlanId: String? = null): String? {
        val list = phases(details, basePlanId) ?: return null
        val trial = list.dropLast(1).firstOrNull { it.priceAmountMicros == 0L } ?: return null
        return "Free for ${duration(trial.billingPeriod, trial.billingCycleCount.coerceAtLeast(1))}"
    }

    /** Short intro-offer label, e.g. "$1.99 for 3 months". Null when no discounted intro phase. */
    fun introLabel(details: ProductDetails?, basePlanId: String? = null): String? {
        val list = phases(details, basePlanId) ?: return null
        val intro = list.dropLast(1).firstOrNull { it.priceAmountMicros > 0L } ?: return null
        val span = duration(intro.billingPeriod, intro.billingCycleCount.coerceAtLeast(1))
        return "${intro.formattedPrice} for $span"
    }

    /** Recurring price in micros and its billing period in months (P1M → 1, P1Y → 12). */
    fun recurringMicrosPerMonth(details: ProductDetails?, basePlanId: String): Double? {
        val phase = phases(details, basePlanId)?.lastOrNull() ?: return null
        val months = monthsIn(phase.billingPeriod) ?: return null
        return phase.priceAmountMicros.toDouble() / months
    }

    /** "Save XX%" for the yearly base plan against twelve monthly payments; null below 1%. */
    fun yearlySavingsPercent(details: ProductDetails?): Int? {
        val monthly = recurringMicrosPerMonth(details, PlayCatalog.BASE_PLAN_MONTHLY) ?: return null
        val yearly = recurringMicrosPerMonth(details, PlayCatalog.BASE_PLAN_YEARLY) ?: return null
        return savingsPercent(monthly, yearly)
    }

    /** Pure: percentage saved paying [perMonthB] instead of [perMonthA]. */
    fun savingsPercent(perMonthA: Double, perMonthB: Double): Int? {
        if (perMonthA <= 0.0 || perMonthB >= perMonthA) return null
        val pct = kotlin.math.round((1 - perMonthB / perMonthA) * 100).toInt()
        return pct.takeIf { it >= 1 }
    }

    /** Months in an ISO-8601 billing period (P1M, P3M, P1Y, P52W); null for day periods. */
    fun monthsIn(iso: String): Double? {
        val (n, unit) = parse(iso) ?: return null
        return when (unit) {
            "month" -> n.toDouble()
            "year" -> n * 12.0
            "week" -> n * 12.0 / 52.0
            else -> null
        }
    }

    private fun parse(iso: String): Pair<Int, String>? {
        val match = Regex("P(\\d+)([DWMY])").matchEntire(iso) ?: return null
        val n = match.groupValues[1].toIntOrNull() ?: return null
        val unit = when (match.groupValues[2]) {
            "D" -> "day"
            "W" -> "week"
            "M" -> "month"
            else -> "year"
        }
        return n to unit
    }

    /** "month" for P1M, "3 months" for P3M. */
    private fun per(iso: String): String {
        val (n, unit) = parse(iso) ?: return iso
        return if (n == 1) unit else "$n ${unit}s"
    }

    /** Total length of [cycles] periods, e.g. P1W × 1 -> "1 week", P1M × 3 -> "3 months". */
    private fun duration(iso: String, cycles: Int): String {
        val (n, unit) = parse(iso) ?: return iso
        val total = n * cycles
        return if (total == 1) "1 $unit" else "$total ${unit}s"
    }
}
