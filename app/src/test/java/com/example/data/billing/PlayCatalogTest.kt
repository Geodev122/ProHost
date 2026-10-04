package com.example.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCatalogTest {

    @Test
    fun `base plan ids are exactly the ones in Play Console`() {
        assertEquals("package_pro_mrr", PlayCatalog.PRODUCT_ID)
        assertEquals(listOf("pro-montly", "pro-yearly"), PlayCatalog.BASE_PLANS)
    }

    @Test
    fun `plan labels and intervals come from the base plan`() {
        assertEquals("monthly", PlayCatalog.planInterval("pro-montly"))
        assertEquals("yearly", PlayCatalog.planInterval("pro-yearly"))
        assertNull(PlayCatalog.planInterval("something_else"))
        assertEquals("Yearly", PlayCatalog.planBadge("pro-yearly"))
        assertEquals("Granted", PlayCatalog.planBadge(PlayCatalog.ADMIN_FORCED_PLAN_ID))
        assertEquals("ProHost Premium", PlayCatalog.planLabel(null))
    }

    @Test
    fun `forced upgrades include the legacy lifetime grant`() {
        assertTrue(PlayCatalog.isForcedUpgrade("admin_forced"))
        assertTrue(PlayCatalog.isForcedUpgrade("admin_unlimited_grant"))
        assertFalse(PlayCatalog.isForcedUpgrade("pro-yearly"))
        assertTrue(PlayCatalog.isLifetimeExpiry(PlayCatalog.LIFETIME_EXPIRY_MILLIS))
        assertFalse(PlayCatalog.isLifetimeExpiry(null))
    }

    @Test
    fun `yearly savings compare per-month prices`() {
        // $9.99/month vs $99.99/year (8.3325/month) -> 17% saved.
        assertEquals(17, PlayOfferText.savingsPercent(9_990_000.0, 99_990_000.0 / 12))
        assertNull(PlayOfferText.savingsPercent(9_990_000.0, 9_990_000.0))
        assertNull(PlayOfferText.savingsPercent(0.0, 1.0))
        assertEquals(12.0, PlayOfferText.monthsIn("P1Y")!!, 0.0)
        assertEquals(1.0, PlayOfferText.monthsIn("P1M")!!, 0.0)
        assertNull(PlayOfferText.monthsIn("P7D"))
    }
}
