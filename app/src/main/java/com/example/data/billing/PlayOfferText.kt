package com.example.data.billing

import com.android.billingclient.api.ProductDetails

/**
 * Human-readable price terms for the offer PlayBillingManager.launchSubscriptionPurchase
 * uses ([preferredOffer]), e.g. "Free for 7 days, then $4.99 / month".
 * Play policy requires price, billing period and trial terms to be shown before purchase.
 */
object PlayOfferText {

    /**
     * The one offer both the price text and the purchase sheet use. Play only returns
     * developer offers the user is eligible for, in no guaranteed order: prefer one with a
     * free trial, then one with an intro price, else the plain base plan.
     */
    fun preferredOffer(details: ProductDetails?): ProductDetails.SubscriptionOfferDetails? {
        val offers = details?.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null
        fun phases(o: ProductDetails.SubscriptionOfferDetails) = o.pricingPhases.pricingPhaseList
        return offers.firstOrNull { o -> o.offerId != null && phases(o).dropLast(1).any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { o -> o.offerId != null && phases(o).size > 1 }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.first()
    }

    fun describe(details: ProductDetails?): String? {
        val phases = preferredOffer(details)?.pricingPhases?.pricingPhaseList
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
    fun caption(details: ProductDetails?): String? {
        val phases = preferredOffer(details)?.pricingPhases?.pricingPhaseList
        if (phases.isNullOrEmpty()) return null
        return if (phases.size > 1) describe(details) else "per ${per(phases.last().billingPeriod)}"
    }

    /** Recurring price only, e.g. "$4.99". */
    fun recurringPrice(details: ProductDetails?): String? =
        preferredOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    /** "per month", "per year", etc. for the recurring phase — null when no product. */
    fun billingPeriodLabel(details: ProductDetails?): String? {
        val phase = preferredOffer(details)?.pricingPhases?.pricingPhaseList?.lastOrNull()
            ?: return null
        return "per ${per(phase.billingPeriod)}"
    }

    /** Short free-trial label, e.g. "7-day free trial". Null when no trial phase. */
    fun trialLabel(details: ProductDetails?): String? {
        val phases = preferredOffer(details)?.pricingPhases?.pricingPhaseList
            ?: return null
        val trial = phases.firstOrNull { it.priceAmountMicros == 0L } ?: return null
        return "Free for ${duration(trial.billingPeriod, trial.billingCycleCount.coerceAtLeast(1))}"
    }

    /** Short intro-offer label, e.g. "50% off for 3 months". Null when no discounted intro phase. */
    fun introLabel(details: ProductDetails?): String? {
        val phases = preferredOffer(details)?.pricingPhases?.pricingPhaseList
            ?: return null
        val intro = phases.dropLast(1).firstOrNull { it.priceAmountMicros > 0L } ?: return null
        val span = duration(intro.billingPeriod, intro.billingCycleCount.coerceAtLeast(1))
        return "${intro.formattedPrice} for $span"
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
