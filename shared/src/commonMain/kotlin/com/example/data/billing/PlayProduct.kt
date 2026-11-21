package com.example.data.billing

/**
 * One Play subscription offer as the shared offer logic reads it (from Billing's `ProductDetails.SubscriptionOfferDetails`
 * on Android; StoreKit later maps its own products to the same shape). [phases] are in Play's order, the last one is the
 * recurring price. [offerId] is null for the plain base plan.
 */
data class PlayOffer(
    val basePlanId: String,
    val offerId: String?,
    val offerTags: List<String>,
    val offerToken: String,
    val phases: List<PlayOfferText.Phase>,
)

/** A Play subscription product: its id, Play's product description and every offer Play returned for this account. */
data class PlayProduct(
    val productId: String,
    val description: String,
    val offers: List<PlayOffer>,
)
