package com.example.data.billing

import com.example.data.billing.PlayOfferText.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PlayPromoTest {
    private val freeMonth = listOf(
        Phase(0, "USD", "Free", "P1M", 1),
        Phase(9_990_000, "USD", "$9.99", "P1M", 1)
    )

    @Test
    fun `a promo-tagged offer becomes the card, configured only by its Play tags and phases`() {
        val p = PlayPromo.parse("prohost2026", "pro-monthly", listOf("promo", "code-prohost2026", "ends-20261231", "early-launch"), freeMonth)!!
        assertEquals("PROHOST2026", p.code)
        assertEquals("Early launch promo", p.title)
        assertEquals("Your first month free", p.benefit)
        assertEquals("pro-monthly", p.basePlanId)
        val end = Calendar.getInstance(TimeZone.getTimeZone("Asia/Beirut")).apply { timeInMillis = p.endsAtMillis!! }
        assertEquals(2026, end.get(Calendar.YEAR)); assertEquals(Calendar.DECEMBER, end.get(Calendar.MONTH))
        assertEquals(31, end.get(Calendar.DAY_OF_MONTH)); assertEquals(23, end.get(Calendar.HOUR_OF_DAY))
        assertTrue(p.isLive(p.endsAtMillis!! - 1))
        assertFalse(p.isLive(p.endsAtMillis!!))
    }

    @Test
    fun `incomplete or untagged offers are not promos`() {
        assertNull(PlayPromo.parse("x", "pro-monthly", listOf("code-abc"), freeMonth)) // no promo tag
        assertNull(PlayPromo.parse("x", "pro-monthly", listOf("promo"), freeMonth)) // no code
        assertNull(PlayPromo.parse("x", "pro-monthly", listOf("promo", "code-abc"), listOf(Phase(9_990_000, "USD", "$9.99", "P1M", 1))))
        val noEnd = PlayPromo.parse("x", "pro-monthly", listOf("PROMO", "code-abc1"), freeMonth)!!
        assertNull(noEnd.endsAtMillis)
        assertTrue(noEnd.isLive(Long.MAX_VALUE - 1))
        assertEquals("Limited-time promo", noEnd.title)
        assertNull(PlayPromo.parse("x", "pro-monthly", listOf("promo", "code-abc", "ends-20261340"), freeMonth)!!.endsAtMillis)
    }

    @Test
    fun `benefit text follows the offer phases`() {
        val half = PlayOfferText.summarize("h", listOf(Phase(4_990_000, "USD", "$4.99", "P1M", 1), Phase(9_990_000, "USD", "$9.99", "P1M", 1)))!!
        assertEquals("50% off your first month", PlayPromo.benefitOf(half))
        val week = PlayOfferText.summarize("w", listOf(Phase(0, "USD", "Free", "P7D", 1), Phase(9_990_000, "USD", "$9.99", "P1M", 1)))!!
        assertEquals("7 days free", PlayPromo.benefitOf(week))
        assertTrue(PlayPromo.isPromoTagged(listOf("x", " Promo ")))
        assertFalse(PlayPromo.isPromoTagged(listOf("promotion")))
    }

    @Test
    fun `countdown splits the remaining time and never goes negative`() {
        val now = 1_000_000L
        assertEquals(PlayPromo.Countdown(2, 3, 4, 5), PlayPromo.countdown(now, now + ((2 * 86_400 + 3 * 3_600 + 4 * 60 + 5) * 1000L)))
        assertEquals(PlayPromo.Countdown(0, 0, 0, 0), PlayPromo.countdown(now, now - 5_000))
    }
}
