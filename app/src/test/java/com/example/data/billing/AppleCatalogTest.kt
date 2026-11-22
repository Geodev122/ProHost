package com.example.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleCatalogTest {

    /** Values computed by `appAccountTokenFor` in functions/src/billing/appleCatalog.ts: app and server must agree. */
    @Test
    fun appAccountTokenMatchesServer() {
        assertEquals("21e616a4-eb48-422c-b827-40705cdd5551", AppleCatalog.appAccountTokenFor("uid-123"))
        assertEquals("2961538e-adf5-4326-b26c-229d5efeda9f", AppleCatalog.appAccountTokenFor("abcXYZ09"))
    }

    private fun product(id: String, period: String, micros: Long, intro: Triple<Long, String, Int>? = null) =
        AppleCatalog.StoreProduct(
            productId = id, description = "Publish your spaces", priceMicros = micros, currency = "USD",
            formattedPrice = "$" + micros / 1_000_000.0, period = period,
            introPriceMicros = intro?.first ?: 0, introFormattedPrice = if (intro == null) "" else "intro",
            introPeriod = intro?.second ?: "", introCycles = intro?.third ?: 0,
        )

    @Test
    fun storeProductsBecomeTwoPlansWithOffers() {
        val play = AppleCatalog.asPlayProduct(
            listOf(
                product(AppleCatalog.PRODUCT_MONTHLY, "P1M", 8_990_000, Triple(0, "P1M", 1)),
                product(AppleCatalog.PRODUCT_YEARLY, "P1Y", 79_990_000, Triple(39_990_000, "P1Y", 1)),
                product("other.product", "P1M", 1_000_000),
            )
        )!!
        assertEquals(PlayCatalog.PRODUCT_ID, play.productId)
        assertEquals(4, play.offers.size)
        val kinds = PlayOfferText.plansByKind(play)
        assertEquals(AppleCatalog.PRODUCT_MONTHLY, kinds[PlayCatalog.PlanKind.MONTHLY])
        assertEquals(AppleCatalog.PRODUCT_YEARLY, kinds[PlayCatalog.PlanKind.YEARLY])
        // The free month is picked for the monthly card, the half-price first year for the yearly card.
        assertEquals(AppleCatalog.INTRO_OFFER_ID, PlayOfferText.preferredOffer(play, AppleCatalog.PRODUCT_MONTHLY)?.offerId)
        val yearly = PlayOfferText.preferredOffer(play, AppleCatalog.PRODUCT_YEARLY)!!
        assertEquals(listOf(39_990_000L, 79_990_000L), yearly.phases.map { it.micros })
    }

    @Test
    fun noIntroOfferWhenNotEligible() {
        val play = AppleCatalog.asPlayProduct(listOf(product(AppleCatalog.PRODUCT_MONTHLY, "P1M", 8_990_000)))!!
        assertEquals(1, play.offers.size)
        assertNull(play.offers.single().offerId)
        assertNull(AppleCatalog.asPlayProduct(listOf(product("other", "P1M", 1))))
    }

    @Test
    fun singleListingTierIsItsOwnProduct() {
        for (id in listOf(PlayCatalog.SINGLE_PRODUCT_ID, "single-monthly", "single-yearly", AppleCatalog.SINGLE_MONTHLY)) {
            assertEquals(id, PlayCatalog.PlanTier.SINGLE, PlayCatalog.tierOf(id))
        }
        for (id in listOf(PlayCatalog.PRODUCT_ID, "pro-month1", "pro-yearly", AppleCatalog.PRODUCT_YEARLY, PlayCatalog.ADMIN_FORCED_PLAN_ID, null)) {
            assertEquals(id.toString(), PlayCatalog.PlanTier.UNLIMITED, PlayCatalog.tierOf(id))
        }
        assertEquals(true, PlayCatalog.isSupportedProduct(PlayCatalog.SINGLE_PRODUCT_ID))
        val products = AppleCatalog.asPlayProducts(
            listOf(
                product(AppleCatalog.PRODUCT_MONTHLY, "P1M", 8_990_000),
                product(AppleCatalog.SINGLE_MONTHLY, "P1M", 4_490_000),
                product(AppleCatalog.SINGLE_YEARLY, "P1Y", 44_990_000),
            )
        )
        assertEquals(listOf(PlayCatalog.PRODUCT_ID, PlayCatalog.SINGLE_PRODUCT_ID), products.map { it.productId })
        assertEquals(AppleCatalog.SINGLE_YEARLY, PlayOfferText.plansByKind(products[1])[PlayCatalog.PlanKind.YEARLY])
        // The tier switch shows the regular monthly price, never the intro price.
        val withIntro = AppleCatalog.asPlayProduct(listOf(product(AppleCatalog.SINGLE_MONTHLY, "P1M", 4_490_000, Triple(0, "P1M", 1))), PlayCatalog.SINGLE_PRODUCT_ID)
        assertEquals("$4.49", PlayOfferText.regularPrice(withIntro, AppleCatalog.SINGLE_MONTHLY))
        assertNull(PlayOfferText.regularPrice(withIntro, "missing"))
    }
}
