package com.example.data.billing

/** Which of the offers Play returned a checkout uses (all of them come from Play; none is invented). */
sealed interface OfferChoice {
    /** The plan cards' offer: free trial, else deepest discount, else base plan (PlayOfferText.preferredOffer). */
    data object Preferred : OfferChoice
    /** The plain base plan: plan switches, which show and charge regular prices. */
    data object BasePlanOnly : OfferChoice
    /** One offer by id: the Play-configured promo (PlayPromo). */
    data class Specific(val offerId: String) : OfferChoice
}
