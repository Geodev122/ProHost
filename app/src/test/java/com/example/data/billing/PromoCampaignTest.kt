package com.example.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromoCampaignTest {
    private val end = 2_000_000_000_000L
    private fun doc(vararg extra: Pair<String, Any?>) = mapOf<String, Any?>(
        "active" to true, "code" to "PROHOST2026", "endsAt" to end, "maxRedemptions" to 100L, "redeemed" to 12L,
        "basePlanKind" to "monthly"
    ) + extra

    @Test
    fun `live while active, before the end and with spots left`() {
        val c = PromoCampaign.fromMap(doc())!!
        assertTrue(c.isLive(end - 1))
        assertFalse(c.isLive(end))
        assertEquals(88, c.spotsLeft)
        assertEquals(PlayCatalog.PlanKind.MONTHLY, c.basePlanKind)
        assertEquals("Early launch promo", c.title)
        assertFalse(PromoCampaign.fromMap(doc("active" to false))!!.isLive(0))
        assertFalse(PromoCampaign.fromMap(doc("redeemed" to 100L))!!.isLive(0))
        assertFalse(PromoCampaign.fromMap(doc("code" to " "))!!.isLive(0))
    }

    @Test
    fun `no cap means no spots counter and missing end means no campaign`() {
        val c = PromoCampaign.fromMap(doc("maxRedemptions" to null))!!
        assertNull(c.spotsLeft)
        assertTrue(c.isLive(0))
        assertNull(PromoCampaign.fromMap(doc("endsAt" to null)))
        assertNull(PromoCampaign.fromMap(null))
    }

    @Test
    fun `countdown splits the remaining time and never goes negative`() {
        val now = 1_000_000L
        val left = PromoCampaign.countdown(now, now + ((2 * 86_400 + 3 * 3_600 + 4 * 60 + 5) * 1000L))
        assertEquals(PromoCampaign.Countdown(2, 3, 4, 5), left)
        assertEquals(PromoCampaign.Countdown(0, 0, 0, 0), PromoCampaign.countdown(now, now - 5_000))
    }
}
