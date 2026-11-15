package com.example.ui.util

import com.example.data.model.Level2Type
import com.example.data.model.RentalPricingConfig
import com.example.data.model.SpaceListing
import com.example.data.model.Subdivision

/**
 * How the Create Listing wizard's division cards map onto a listing (pure, unit-tested in ListingDivisionsTest).
 *
 * A host always configures divisions. A place whose core is one division is the whole place: one division that is not
 * per-attendee and keeps the space's hours is saved as the listing's own `pricing` with no `subdivisions` (its photos join
 * the gallery after the cover, its amenities the facilities, its hashtags the disciplines). A per-attendee division (a
 * whole place can't be priced per person), one with its own hours, or two and more are saved as `subdivisions`.
 */
object ListingDivisions {
    /** Id of the card that stands for an undivided listing when it is opened for editing (never stored). */
    const val WHOLE_PLACE_ID = "SUB-WHOLE"

    data class Shape(
        val pricing: RentalPricingConfig,
        val subdivisions: List<Subdivision>,
        val imageUrls: List<String>,
        val facilities: List<String>,
        val hashtags: List<String>,
        /** The whole place's capacity (null when divided: each division has its own). */
        val capacity: Int? = null
    )

    fun isWholePlace(divisions: List<Subdivision>): Boolean =
        divisions.size == 1 && !AttendeePricing.isPerAttendee(divisions[0]) && divisions[0].scheduleOverride == null

    /** True once at least one division has a real price (per-attendee: configured tiers) — the publish gate. */
    fun hasRealPricing(divisions: List<Subdivision>): Boolean = divisions.any {
        if (AttendeePricing.isPerAttendee(it)) AttendeePricing.isConfigured(it) else it.pricing.hasRealPrice()
    }

    /**
     * The listing fields for [divisions]. [cover] is the brand cover (always `imageUrls[0]`); [galleryExtras] are photos a
     * divided listing already had after its cover (kept as they are).
     */
    fun toListingShape(
        divisions: List<Subdivision>,
        cover: String?,
        galleryExtras: List<String>,
        facilities: List<String>,
        hashtags: List<String>,
        /** Keep the divisions even when one is left (its bookings reference that division's id). */
        keepDivided: Boolean = false
    ): Shape {
        if (!keepDivided && isWholePlace(divisions)) {
            val d = divisions[0]
            return Shape(
                pricing = d.pricing,
                subdivisions = emptyList(),
                imageUrls = (listOfNotNull(cover) + galleryExtras + d.imageUrls).distinct(),
                facilities = (facilities + d.amenities).distinct(),
                hashtags = (hashtags + d.hashtags).distinct(),
                capacity = d.capacity
            )
        }
        return Shape(
            pricing = RentalPricingConfig.default(),
            subdivisions = divisions,
            imageUrls = (listOfNotNull(cover) + galleryExtras).distinct(),
            facilities = facilities,
            hashtags = hashtags
        )
    }

    /** The division cards of an existing listing: its divisions, or one card standing for an undivided place. */
    fun fromListing(listing: SpaceListing, wholePlaceType: Level2Type): List<Subdivision> {
        if (listing.subdivisions.isNotEmpty()) return listing.subdivisions
        if (!listing.pricing.hasRealPrice()) return emptyList()
        return listOf(
            Subdivision(
                id = WHOLE_PLACE_ID,
                name = listing.title,
                type = wholePlaceType,
                imageUrls = listing.imageUrls.drop(1),
                pricing = listing.pricing,
                capacity = listing.capacity
            )
        )
    }

    /** Photos after the cover that stay on the listing itself (a priced undivided listing's moved into its card). */
    fun galleryExtrasOf(listing: SpaceListing?): List<String> = when {
        listing == null -> emptyList()
        listing.subdivisions.isEmpty() && listing.pricing.hasRealPrice() -> emptyList()
        else -> listing.imageUrls.drop(1)
    }
}
