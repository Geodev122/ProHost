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
     * (null = any base plan). Play only returns developer offers this Google account is
     * eligible for (a "new customers" offer is never returned to someone who already had the
     * subscription), in no guaranteed order; [pickOffer] chooses among them.
     */
    fun preferredOffer(details: ProductDetails?, basePlanId: String? = null): ProductDetails.SubscriptionOfferDetails? {
        // `promo`-tagged offers belong to the promo card only (PlayPromo), never to the plan cards.
        val offers = details?.subscriptionOfferDetails.orEmpty()
            .filter { (basePlanId == null || it.basePlanId == basePlanId) && !PlayPromo.isPromoTagged(it.offerTags) }
        if (offers.isEmpty()) return null
        val index = pickOffer(offers.map { o -> o.offerId to o.pricingPhases.pricingPhaseList.map(::phaseOf) })
        return offers[index]
    }

    /**
     * Pure offer choice over (offerId, phases): a free trial first, then the deepest discount
     * (lowest first-phase price per month), else the plain base plan, else the first one.
     * Every eligible offer Play returns is considered, so a new or edited offer in Play Console
     * shows up as soon as Play returns it.
     */
    fun pickOffer(offers: List<Pair<String?, List<Phase>>>): Int {
        if (offers.isEmpty()) return -1
        val developerOffers = offers.indices.filter { offers[it].first != null && offers[it].second.size > 1 }
        developerOffers.firstOrNull { i -> offers[i].second.dropLast(1).any { it.micros == 0L } }?.let { return it }
        developerOffers.minByOrNull { i ->
            val first = offers[i].second.first()
            first.micros / (monthsIn(first.period) ?: 1.0)
        }?.let { return it }
        return offers.indexOfFirst { it.first == null }.takeIf { it >= 0 } ?: 0
    }

    /** One pricing phase, decoupled from ProductDetails so the offer maths is unit-testable. */
    data class Phase(val micros: Long, val currency: String, val formatted: String, val period: String, val cycles: Int)

    internal fun phaseOf(p: ProductDetails.PricingPhase) = Phase(
        micros = p.priceAmountMicros,
        currency = p.priceCurrencyCode,
        formatted = p.formattedPrice,
        period = p.billingPeriod,
        cycles = p.billingCycleCount.coerceAtLeast(1)
    )

    /**
     * What a plan card shows for one base plan's offer, all from Google Play:
     *  - [headline]: the price paid now — the discounted intro price, "Free" for a trial, or the
     *    regular price when there's no offer;
     *  - [original]: the regular price for the same period, struck through next to the headline
     *    (only when it is genuinely higher);
     *  - [discountPercent]: how much the intro saves against the regular price;
     *  - [introSpan] "first month" / "first 3 months" / "first year", [trialSpan] "7 days";
     *  - [recurring] / [recurringPeriod]: what it renews at, e.g. "$9.99" / "month";
     *  - [introPeriods]: ISO periods to add to today to get the date the regular price starts.
     */
    data class OfferSummary(
        val offerId: String?,
        val headline: String,
        val headlinePeriod: String,
        val original: String?,
        val discountPercent: Int?,
        val introSpan: String?,
        val trialSpan: String?,
        val recurring: String,
        val recurringPeriod: String,
        val introPeriods: List<Pair<String, Int>>,
        /** "≈ $4.17 / month" for a headline billed per year (or longer); null otherwise. */
        val headlinePerMonth: String? = null
    ) {
        val hasOffer: Boolean get() = introSpan != null || trialSpan != null
    }

    /** Pure: summary of an offer's phases (last phase = the recurring price). */
    fun summarize(offerId: String?, phases: List<Phase>): OfferSummary? {
        val recurring = phases.lastOrNull() ?: return null
        val intros = phases.dropLast(1)
        val trial = intros.firstOrNull { it.micros == 0L }
        val discounted = intros.firstOrNull { it.micros > 0L }
        val recurringPerMonth = monthsIn(recurring.period)?.let { recurring.micros / it }
        val discount = discounted?.let { d ->
            val perMonth = monthsIn(d.period)?.let { d.micros / it }
            if (recurringPerMonth != null && perMonth != null) savingsPercent(recurringPerMonth, perMonth) else null
        }
        val headlinePhase = if (discounted != null && discount != null) discounted else if (trial != null) null else recurring
        val headlinePerMonth = headlinePhase?.let { h ->
            val months = monthsIn(h.period)?.takeIf { it > 1.0 } ?: return@let null
            runCatching {
                val fmt = java.text.NumberFormat.getCurrencyInstance()
                fmt.currency = java.util.Currency.getInstance(h.currency)
                "≈ ${fmt.format(h.micros / 1_000_000.0 / months)} / month"
            }.getOrNull()
        }
        return OfferSummary(
            headlinePerMonth = headlinePerMonth,
            offerId = offerId,
            headline = when {
                discounted != null && discount != null -> discounted.formatted
                trial != null -> "Free"
                else -> recurring.formatted
            },
            headlinePeriod = per((if (discounted != null && discount != null) discounted else recurring).period),
            // Struck-through original only when it is the same billing period (a like-for-like price).
            original = recurring.formatted.takeIf { discounted != null && discount != null && discounted.period == recurring.period },
            discountPercent = discount,
            introSpan = discounted?.takeIf { discount != null }?.let { firstSpan(it.period, it.cycles) },
            trialSpan = trial?.let { duration(it.period, it.cycles) },
            recurring = recurring.formatted,
            recurringPeriod = per(recurring.period),
            introPeriods = intros.map { it.period to it.cycles }
        )
    }

    /** The summary for the offer the purchase sheet will use for [basePlanId]. */
    fun summary(details: ProductDetails?, basePlanId: String): OfferSummary? {
        val offer = preferredOffer(details, basePlanId) ?: return null
        return summarize(offer.offerId, offer.pricingPhases.pricingPhaseList.map(::phaseOf))
    }

    /** Pure: when the regular price starts if the intro phases begin at [startMillis]. */
    fun regularPriceStartsAt(startMillis: Long, introPeriods: List<Pair<String, Int>>): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
        for ((iso, cycles) in introPeriods) {
            val (n, unit) = parse(iso) ?: continue
            val field = when (unit) {
                "day" -> java.util.Calendar.DAY_OF_YEAR
                "week" -> java.util.Calendar.WEEK_OF_YEAR
                "month" -> java.util.Calendar.MONTH
                else -> java.util.Calendar.YEAR
            }
            cal.add(field, n * cycles)
        }
        return cal.timeInMillis
    }

    /** "first month", "first 3 months", "first year", "first 7 days". */
    private fun firstSpan(iso: String, cycles: Int): String {
        val span = duration(iso, cycles)
        return if (span.startsWith("1 ")) "first ${span.removePrefix("1 ")}" else "first $span"
    }

    /** Base plan ids Play returned for this product. */
    fun basePlans(details: ProductDetails?): List<String> =
        details?.subscriptionOfferDetails.orEmpty().map { it.basePlanId }.distinct()

    /**
     * Monthly or yearly for a base plan, from its recurring billing period in Play (P1M / P1Y),
     * so the screen never depends on how the id is spelled in Play Console. Falls back to the id.
     */
    fun kindOf(details: ProductDetails?, basePlanId: String): PlayCatalog.PlanKind? {
        val offers = details?.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == basePlanId }
        val recurring = (offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull())
            ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.billingPeriod
        return recurring?.let(::kindForPeriod) ?: PlayCatalog.kindOf(basePlanId)
    }

    /** Pure: P1M (or any period ≤ 1 month) → monthly, ≥ 12 months → yearly, else null. */
    fun kindForPeriod(iso: String): PlayCatalog.PlanKind? {
        val months = monthsIn(iso) ?: return null
        return when {
            months <= 1.0 -> PlayCatalog.PlanKind.MONTHLY
            months >= 12.0 -> PlayCatalog.PlanKind.YEARLY
            else -> null
        }
    }

    /** A base plan Play returned, as far as choosing between two of the same kind needs. */
    data class PlanCandidate(val id: String, val kind: PlayCatalog.PlanKind, val offerCount: Int)

    /**
     * Pure: one base plan per kind. When Play returns two of a kind (a renamed plan whose old one is still
     * active), the one with more offers wins, then a current id over a retired one, then the first returned.
     */
    fun chooseByKind(candidates: List<PlanCandidate>): Map<PlayCatalog.PlanKind, String> =
        candidates.withIndex()
            .groupBy { it.value.kind }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareByDescending<IndexedValue<PlanCandidate>> { it.value.offerCount }
                        .thenBy { it.value.id.lowercase() in PlayCatalog.LEGACY_MONTHLY_IDS }
                        .thenBy { it.index }
                ).first().value.id
            }

    /** The base plan id Play returned for each kind (at most one monthly and one yearly). */
    fun plansByKind(details: ProductDetails?): Map<PlayCatalog.PlanKind, String> {
        val offers = details?.subscriptionOfferDetails.orEmpty()
        val candidates = basePlans(details).mapNotNull { id ->
            val kind = kindOf(details, id) ?: return@mapNotNull null
            PlanCandidate(id, kind, offers.count { it.basePlanId == id && !PlayPromo.isPromoTagged(it.offerTags) })
        }
        return chooseByKind(candidates)
    }

    /** The phases checkout will charge: the preferred offer, or the plain base plan for a plan switch. */
    private fun phases(details: ProductDetails?, basePlanId: String?, basePlanOnly: Boolean = false) =
        (if (basePlanOnly) {
            details?.subscriptionOfferDetails.orEmpty().firstOrNull { it.basePlanId == basePlanId && it.offerId == null }
        } else preferredOffer(details, basePlanId))?.pricingPhases?.pricingPhaseList

    /** Terms for the buy button, e.g. "$4.99 / month for 1 month, then $9.99 / month"; [basePlanOnly] for switches. */
    fun describe(details: ProductDetails?, basePlanId: String? = null, basePlanOnly: Boolean = false): String? {
        val phases = phases(details, basePlanId, basePlanOnly)
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

    /** Short free-trial label, e.g. "Free for 7 days". Null when no trial phase. */
    fun trialLabel(details: ProductDetails?, basePlanId: String? = null): String? {
        val list = phases(details, basePlanId) ?: return null
        val trial = list.dropLast(1).firstOrNull { it.priceAmountMicros == 0L } ?: return null
        return "Free for ${duration(trial.billingPeriod, trial.billingCycleCount.coerceAtLeast(1))}"
    }

    /** Recurring price in micros and its billing period in months (P1M → 1, P1Y → 12). */
    fun recurringMicrosPerMonth(details: ProductDetails?, basePlanId: String): Double? {
        val phase = phases(details, basePlanId)?.lastOrNull() ?: return null
        val months = monthsIn(phase.billingPeriod) ?: return null
        return phase.priceAmountMicros.toDouble() / months
    }

    /** "Save XX%" for the yearly base plan against twelve monthly payments; null below 1%. */
    fun yearlySavingsPercent(details: ProductDetails?): Int? {
        val plans = plansByKind(details)
        val monthly = plans[PlayCatalog.PlanKind.MONTHLY]?.let { recurringMicrosPerMonth(details, it) } ?: return null
        val yearly = plans[PlayCatalog.PlanKind.YEARLY]?.let { recurringMicrosPerMonth(details, it) } ?: return null
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
