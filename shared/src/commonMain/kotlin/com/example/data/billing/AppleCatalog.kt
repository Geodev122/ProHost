package com.example.data.billing

import com.example.util.Sha256

/**
 * App Store side of ProHost Premium (StoreKit 2). Mirrors `functions/src/billing/appleCatalog.ts`: the two products of the
 * "ProHost Premium" subscription group and the purchase tag [appAccountTokenFor], which must be byte-identical to the
 * server's (pinned by `AppleCatalogTest` with values computed by the TypeScript code).
 */
object AppleCatalog {
    const val PRODUCT_MONTHLY = "prohost.premium.monthly"
    const val PRODUCT_YEARLY = "prohost.premium.yearly"
    val PRODUCT_IDS = listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY)

    /** Offer id the iOS mapping gives an App Store introductory offer (trial or discounted first period). */
    const val INTRO_OFFER_ID = "app-store-intro"

    fun isAppleProduct(productId: String?): Boolean = productId != null && productId in PRODUCT_IDS

    /** UUID (version 4 layout) from SHA-256("prohost-account:" + uid): StoreKit's `appAccountToken` for this account. */
    fun appAccountTokenFor(uid: String): String {
        val b = Sha256.digest("prohost-account:$uid".encodeToByteArray()).copyOf(16)
        b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
        val h = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
    }

    /** One App Store product as StoreKit reports it; [introPeriod] is blank when the account gets no introductory offer. */
    data class StoreProduct(
        val productId: String,
        val description: String,
        val priceMicros: Long,
        val currency: String,
        val formattedPrice: String,
        val period: String,
        val introPriceMicros: Long,
        val introFormattedPrice: String,
        val introPeriod: String,
        val introCycles: Int,
    )

    /**
     * The App Store products as the one [PlayProduct] the Premium page reads (product [PlayCatalog.PRODUCT_ID]): each
     * App Store product is a "base plan" (its id classifies monthly / yearly like a Play base plan id), its introductory
     * offer an offer with the intro phase first and the regular price last, as Play orders phases.
     */
    fun asPlayProduct(products: List<StoreProduct>): PlayProduct? {
        val ours = products.filter { isAppleProduct(it.productId) }
        if (ours.isEmpty()) return null
        val offers = ours.flatMap { p ->
            val regular = PlayOfferText.Phase(p.priceMicros, p.currency, p.formattedPrice, p.period, 1)
            val base = PlayOffer(p.productId, null, emptyList(), p.productId, listOf(regular))
            if (p.introPeriod.isBlank()) {
                listOf(base)
            } else {
                val intro = PlayOfferText.Phase(
                    p.introPriceMicros, p.currency, p.introFormattedPrice, p.introPeriod, p.introCycles.coerceAtLeast(1)
                )
                listOf(base, PlayOffer(p.productId, INTRO_OFFER_ID, emptyList(), p.productId, listOf(intro, regular)))
            }
        }
        return PlayProduct(PlayCatalog.PRODUCT_ID, ours.first().description, offers)
    }
}
