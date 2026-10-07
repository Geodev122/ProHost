package com.example.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayCatalogTest {

    @Test
    fun `only ProHost Premium is a supported product`() {
        assertEquals("package_pro_mrr", PlayCatalog.PRODUCT_ID)
        assertTrue(PlayCatalog.isSupportedProduct("package_pro_mrr"))
        assertFalse(PlayCatalog.isSupportedProduct("package_growth_mrr"))
        assertFalse(PlayCatalog.isSupportedProduct("package_enterprise_mrr"))
        assertFalse(PlayCatalog.isSupportedProduct(null))
    }

    @Test
    fun `plans are classified by kind whatever the id spelling`() {
        assertEquals(PlayCatalog.PlanKind.MONTHLY, PlayCatalog.kindOf("pro-montly"))
        assertEquals(PlayCatalog.PlanKind.MONTHLY, PlayCatalog.kindOf("pro-monthly"))
        assertEquals(PlayCatalog.PlanKind.YEARLY, PlayCatalog.kindOf("pro-yearly"))
        assertEquals(PlayCatalog.PlanKind.YEARLY, PlayCatalog.kindOf("premium-annual"))
        assertNull(PlayCatalog.kindOf("admin_forced"))
        assertNull(PlayCatalog.kindOf(null))
        assertEquals("Monthly", PlayCatalog.planBadge("pro-monthly"))
        assertEquals(PlayCatalog.PlanKind.MONTHLY, PlayOfferText.kindForPeriod("P1M"))
        assertEquals(PlayCatalog.PlanKind.YEARLY, PlayOfferText.kindForPeriod("P1Y"))
        assertEquals(PlayCatalog.PlanKind.YEARLY, PlayOfferText.kindForPeriod("P12M"))
        assertNull(PlayOfferText.kindForPeriod("P3M"))
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
