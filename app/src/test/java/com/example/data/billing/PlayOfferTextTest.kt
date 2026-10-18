package com.example.data.billing

import com.example.data.billing.PlayOfferText.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PlayOfferTextTest {

    private fun usd(micros: Long, period: String, cycles: Int = 1) =
        Phase(micros, "USD", "$" + String.format(java.util.Locale.US, "%.2f", micros / 1_000_000.0), period, cycles)

    @Test
    fun `50 percent first month shows the intro price with the original struck through`() {
        val s = PlayOfferText.summarize("launch-50", listOf(usd(4_990_000, "P1M"), usd(9_990_000, "P1M")))!!
        assertEquals("$4.99", s.headline)
        assertEquals("month", s.headlinePeriod)
        assertEquals("$9.99", s.original)
        assertEquals(50, s.discountPercent)
        assertEquals("first month", s.introSpan)
        assertNull(s.trialSpan)
        assertEquals("$9.99", s.recurring)
        assertTrue(s.hasOffer)
    }

    @Test
    fun `half-price first year on the yearly plan`() {
        val s = PlayOfferText.summarize("launch-50-y", listOf(usd(49_990_000, "P1Y"), usd(99_990_000, "P1Y")))!!
        assertEquals(50, s.discountPercent)
        assertEquals("first year", s.introSpan)
        assertEquals("$99.99", s.original)
        assertEquals("year", s.recurringPeriod)
    }

    @Test
    fun `multi-cycle intro reads first 3 months`() {
        val s = PlayOfferText.summarize("q", listOf(usd(4_990_000, "P1M", 3), usd(9_990_000, "P1M")))!!
        assertEquals("first 3 months", s.introSpan)
        assertEquals(50, s.discountPercent)
    }

    @Test
    fun `intro billed monthly on a yearly plan has a discount but no like-for-like original`() {
        val s = PlayOfferText.summarize("m", listOf(usd(4_990_000, "P1M", 1), usd(99_990_000, "P1Y")))!!
        assertEquals(40, s.discountPercent) // 4.99 vs 8.3325 per month
        assertNull(s.original)
        assertEquals("$4.99", s.headline)
    }

    @Test
    fun `free trial headline and plain base plan`() {
        val trial = PlayOfferText.summarize("t", listOf(usd(0, "P7D"), usd(9_990_000, "P1M")))!!
        assertEquals("Free", trial.headline)
        assertEquals("7 days", trial.trialSpan)
        assertNull(trial.original)
        val plain = PlayOfferText.summarize(null, listOf(usd(9_990_000, "P1M")))!!
        assertEquals("$9.99", plain.headline)
        assertFalse(plain.hasOffer)
        assertNull(plain.discountPercent)
    }

    @Test
    fun `a pricier intro is not marketed as a discount`() {
        val s = PlayOfferText.summarize("x", listOf(usd(12_000_000, "P1M"), usd(9_990_000, "P1M")))!!
        assertEquals("$9.99", s.headline)
        assertNull(s.discountPercent)
        assertNull(s.introSpan)
    }

    @Test
    fun `offer choice prefers a free trial, then the deepest discount, then the base plan`() {
        val base = null to listOf(usd(9_990_000, "P1M"))
        val half = "half" to listOf(usd(4_990_000, "P1M"), usd(9_990_000, "P1M"))
        val third = "third" to listOf(usd(6_660_000, "P1M"), usd(9_990_000, "P1M"))
        val trial = "trial" to listOf(usd(0, "P7D"), usd(9_990_000, "P1M"))
        assertEquals(0, PlayOfferText.pickOffer(listOf(base)))
        assertEquals(1, PlayOfferText.pickOffer(listOf(base, half, third)))
        assertEquals(2, PlayOfferText.pickOffer(listOf(base, third, half)))
        assertEquals(3, PlayOfferText.pickOffer(listOf(base, third, half, trial)))
        assertEquals(1, PlayOfferText.pickOffer(listOf("x" to listOf(usd(9_990_000, "P1M")), base)))
    }

    @Test
    fun `regular price starts after the intro phases`() {
        val start = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 14, 10, 0, 0) }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = PlayOfferText.regularPriceStartsAt(start, listOf("P1M" to 1)) }
        assertEquals(Calendar.NOVEMBER, end.get(Calendar.MONTH)); assertEquals(14, end.get(Calendar.DAY_OF_MONTH))
        val endYear = Calendar.getInstance().apply { timeInMillis = PlayOfferText.regularPriceStartsAt(start, listOf("P7D" to 1, "P1Y" to 1)) }
        assertEquals(2027, endYear.get(Calendar.YEAR)); assertEquals(21, endYear.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `a renamed monthly plan wins over the retired one and plans with offers win`() {
        val m = PlayCatalog.PlanKind.MONTHLY
        val y = PlayCatalog.PlanKind.YEARLY
        // pro-month1 (new, with offers) next to the still-active retired pro-montly (no offers).
        val chosen = PlayOfferText.chooseByKind(listOf(
            PlayOfferText.PlanCandidate("pro-montly", m, 0),
            PlayOfferText.PlanCandidate("pro-month1", m, 2),
            PlayOfferText.PlanCandidate("pro-yearly", y, 1)
        ))
        assertEquals("pro-month1", chosen[m]); assertEquals("pro-yearly", chosen[y])
        // Same offers: a current id beats a retired one, whatever order Play returned them in.
        val tie = PlayOfferText.chooseByKind(listOf(
            PlayOfferText.PlanCandidate("pro-montly", m, 1),
            PlayOfferText.PlanCandidate("pro-month1", m, 1)
        ))
        assertEquals("pro-month1", tie[m])
        // A lone plan is always used, even with no offers (the card still shows its price).
        assertEquals("pro-month1", PlayOfferText.chooseByKind(listOf(PlayOfferText.PlanCandidate("pro-month1", m, 0)))[m])
        assertEquals(PlayCatalog.PlanKind.MONTHLY, PlayCatalog.kindOf("pro-month1"))
        assertNull(PlayOfferText.chooseByKind(emptyList())[m])
    }
}
