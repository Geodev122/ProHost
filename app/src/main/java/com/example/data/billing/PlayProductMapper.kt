package com.example.data.billing

import com.android.billingclient.api.ProductDetails

/** Billing's [ProductDetails] as the shared [PlayProduct] (same offers, same order, same phases). */
fun ProductDetails.toPlayProduct(): PlayProduct = PlayProduct(
    productId = productId,
    description = description,
    offers = subscriptionOfferDetails.orEmpty().map { o ->
        PlayOffer(
            basePlanId = o.basePlanId,
            offerId = o.offerId,
            offerTags = o.offerTags,
            offerToken = o.offerToken,
            phases = o.pricingPhases.pricingPhaseList.map(::phaseOf),
        )
    },
)

/** One Billing pricing phase as [PlayOfferText.Phase]. */
fun phaseOf(p: ProductDetails.PricingPhase) = PlayOfferText.Phase(
    micros = p.priceAmountMicros,
    currency = p.priceCurrencyCode,
    formatted = p.formattedPrice,
    period = p.billingPeriod,
    cycles = p.billingCycleCount.coerceAtLeast(1)
)
