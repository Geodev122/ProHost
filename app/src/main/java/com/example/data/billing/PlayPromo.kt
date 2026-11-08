package com.example.data.billing

import com.android.billingclient.api.ProductDetails

/**
 * The promo card is configured only in Google Play: a subscription offer carrying these offer tags
 * (Play Console › Subscriptions › ProHost Premium › base plan › Offers › Tags; lowercase, ≤ 20 chars):
 *  - `promo`               marks the offer as the promo card's offer (never shown on the plan cards)
 *  - `code-prohost2026`    the code shown on the card (displayed upper-case)
 *  - `ends-20261231`       countdown end: the end of that day in Beirut (optional)
 *  - `early-launch`        title "Early launch promo" (otherwise "Limited-time promo")
 * Play decides who gets it (offer eligibility, regions, active/inactive); the benefit text comes from the
 * offer's own phases. Nothing about the promo is stored in Firestore or decided by the server.
 */
object PlayPromo {
    const val TAG = "promo"
    private const val CODE_PREFIX = "code-"
    private const val ENDS_PREFIX = "ends-"
    private const val EARLY_LAUNCH = "early-launch"
    private val BEIRUT: java.util.TimeZone = java.util.TimeZone.getTimeZone("Asia/Beirut")

    data class PromoOffer(
        val offerId: String,
        val basePlanId: String,
        val code: String,
        val title: String,
        val endsAtMillis: Long?,
        val summary: PlayOfferText.OfferSummary
    ) {
        /** Live while Play returns it and its `ends-` date (if any) is still ahead. */
        fun isLive(nowMillis: Long): Boolean = endsAtMillis == null || nowMillis < endsAtMillis

        /** The headline from Play's phases: "Your first month free", "50% off your first month", … */
        val benefit: String get() = benefitOf(summary)
    }

    data class Countdown(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

    fun isPromoTagged(tags: List<String>): Boolean = tags.any { it.trim().equals(TAG, ignoreCase = true) }

    /** Pure: the promo described by an offer's tags and phases; null when it isn't a complete promo offer. */
    fun parse(offerId: String, basePlanId: String, tags: List<String>, phases: List<PlayOfferText.Phase>): PromoOffer? {
        val clean = tags.map { it.trim().lowercase() }
        if (TAG !in clean) return null
        val code = clean.firstOrNull { it.startsWith(CODE_PREFIX) }?.removePrefix(CODE_PREFIX)
            ?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        val summary = PlayOfferText.summarize(offerId, phases)?.takeIf { it.hasOffer } ?: return null
        return PromoOffer(
            offerId = offerId,
            basePlanId = basePlanId,
            code = code,
            title = if (EARLY_LAUNCH in clean) "Early launch promo" else "Limited-time promo",
            endsAtMillis = clean.firstOrNull { it.startsWith(ENDS_PREFIX) }?.removePrefix(ENDS_PREFIX)?.let(::endOfDayBeirut),
            summary = summary
        )
    }

    /** The promo offer Play returned for this account (first one tagged `promo`), if any. */
    fun find(details: ProductDetails?): PromoOffer? =
        details?.subscriptionOfferDetails.orEmpty().firstNotNullOfOrNull { o ->
            val id = o.offerId ?: return@firstNotNullOfOrNull null
            parse(id, o.basePlanId, o.offerTags, o.pricingPhases.pricingPhaseList.map(PlayOfferText::phaseOf))
        }

    /** Pure: 23:59:59 Beirut time on yyyymmdd; null when malformed. */
    fun endOfDayBeirut(yyyymmdd: String): Long? {
        val m = Regex("(\\d{4})(\\d{2})(\\d{2})").matchEntire(yyyymmdd) ?: return null
        val (y, mo, d) = m.destructured
        val month = mo.toInt().takeIf { it in 1..12 } ?: return null
        val day = d.toInt().takeIf { it in 1..31 } ?: return null
        return java.util.Calendar.getInstance(BEIRUT).apply {
            isLenient = false
            clear()
            set(y.toInt(), month - 1, day, 23, 59, 59)
        }.let { runCatching { it.timeInMillis }.getOrNull() }
    }

    /** Pure: the headline for an offer's phases. */
    fun benefitOf(s: PlayOfferText.OfferSummary): String = when {
        s.introSpan != null && s.discountPercent != null -> "${s.discountPercent}% off your ${s.introSpan}"
        s.trialSpan != null -> if (s.trialSpan.startsWith("1 ")) "Your first ${s.trialSpan.removePrefix("1 ")} free"
            else "${s.trialSpan.replaceFirstChar { it.uppercase() }} free"
        else -> "ProHost Premium offer"
    }

    /** Pure: time left until [endMillis], never negative. */
    fun countdown(nowMillis: Long, endMillis: Long): Countdown {
        val s = ((endMillis - nowMillis) / 1000).coerceAtLeast(0)
        return Countdown(s / 86_400, (s % 86_400) / 3_600, (s % 3_600) / 60, s % 60)
    }
}
