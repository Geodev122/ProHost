package com.example.ui.util

import com.example.data.model.AttendeePackage
import com.example.data.model.Level2Type
import com.example.data.model.MonthlyConfig
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.SpaceOperatingSchedule
import com.example.data.model.Subdivision
import com.example.data.model.SubdivisionPricingMode
import com.example.demoSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingDivisionsTest {
    private val monthly = RentalPricingConfig(RentalStrategyType.MONTHLY, monthly = MonthlyConfig(rateUsd = 500.0))
    private val room = Subdivision(
        id = "SUB-A", name = "Room A", type = Level2Type.ROOMS,
        imageUrls = listOf("a1", "a2"), amenities = listOf("Wi-Fi", "Sink"), hashtags = listOf("dental"),
        pricing = monthly
    )
    private val roomB = room.copy(id = "SUB-B", name = "Room B", imageUrls = listOf("b1"))

    @Test
    fun noDivisionsIsNotPublishable() {
        assertFalse(ListingDivisions.hasRealPricing(emptyList()))
        val shape = ListingDivisions.toListingShape(emptyList(), "cover", listOf("x"), listOf("Wi-Fi"), emptyList())
        assertEquals(emptyList<Subdivision>(), shape.subdivisions)
        assertEquals(listOf("cover", "x"), shape.imageUrls)
    }

    @Test
    fun oneDivisionIsTheWholePlace() {
        val shape = ListingDivisions.toListingShape(listOf(room), "cover", emptyList(), listOf("Wi-Fi", "Parking"), listOf("dentist"))
        assertTrue(ListingDivisions.isWholePlace(listOf(room)))
        assertEquals(monthly, shape.pricing)
        assertEquals(emptyList<Subdivision>(), shape.subdivisions)
        assertEquals(listOf("cover", "a1", "a2"), shape.imageUrls)
        assertEquals(listOf("Wi-Fi", "Parking", "Sink"), shape.facilities)
        assertEquals(listOf("dentist", "dental"), shape.hashtags)
        assertEquals(null, shape.capacity)
        assertEquals(6, ListingDivisions.toListingShape(listOf(room.copy(capacity = 6)), "c", emptyList(), emptyList(), emptyList()).capacity)
    }

    @Test
    fun perAttendeeOrOwnHoursStaysADivision() {
        val perAttendee = room.copy(
            pricingMode = SubdivisionPricingMode.PER_ATTENDEE,
            pricing = RentalPricingConfig(RentalStrategyType.HOURLY),
            attendeeTiers = listOf(AttendeePackage(id = "T1", name = "Up to 10", pricePerAttendeeUsd = 10.0))
        )
        assertFalse(ListingDivisions.isWholePlace(listOf(perAttendee)))
        assertEquals(listOf(perAttendee), ListingDivisions.toListingShape(listOf(perAttendee), "c", emptyList(), emptyList(), emptyList()).subdivisions)
        val ownHours = room.copy(scheduleOverride = SpaceOperatingSchedule(openingHour = "09:00"))
        assertFalse(ListingDivisions.isWholePlace(listOf(ownHours)))
    }

    @Test
    fun aBookedDividedListingStaysDividedWithOneDivisionLeft() {
        val shape = ListingDivisions.toListingShape(listOf(room), "c", emptyList(), emptyList(), emptyList(), keepDivided = true)
        assertEquals(listOf(room), shape.subdivisions)
        assertEquals(RentalPricingConfig.default(), shape.pricing)
    }

    @Test
    fun twoDivisionsAreSavedAsDivisions() {
        val shape = ListingDivisions.toListingShape(listOf(room, roomB), "cover", listOf("old"), listOf("Wi-Fi"), emptyList())
        assertEquals(listOf(room, roomB), shape.subdivisions)
        assertEquals(RentalPricingConfig.default(), shape.pricing)
        assertEquals(listOf("cover", "old"), shape.imageUrls)
        assertEquals(listOf("Wi-Fi"), shape.facilities)
        assertTrue(ListingDivisions.hasRealPricing(listOf(room, roomB)))
    }

    @Test
    fun undividedListingOpensAsOneCardAndSavesBackTheSame() {
        val listing = demoSpaces().first().copy(imageUrls = listOf("cover", "p1", "p2"), subdivisions = emptyList())
        val cards = ListingDivisions.fromListing(listing, Level2Type.OFFICE)
        assertEquals(1, cards.size)
        assertEquals(ListingDivisions.WHOLE_PLACE_ID, cards[0].id)
        assertEquals(listing.title, cards[0].name)
        assertEquals(listOf("p1", "p2"), cards[0].imageUrls)
        assertEquals(emptyList<String>(), ListingDivisions.galleryExtrasOf(listing))
        val shape = ListingDivisions.toListingShape(
            cards, "cover", ListingDivisions.galleryExtrasOf(listing), listing.essentialFacilities, listing.complementarySpecialties
        )
        assertEquals(listing.pricing, shape.pricing)
        assertEquals(listing.imageUrls, shape.imageUrls)
        assertEquals(listing.essentialFacilities, shape.facilities)
        val withCapacity = listing.copy(capacity = 12)
        assertEquals(12, ListingDivisions.fromListing(withCapacity, Level2Type.OFFICE)[0].capacity)
    }

    @Test
    fun dividedListingOpensAsItsDivisionsAndKeepsItsGallery() {
        val listing = demoSpaces().first().copy(imageUrls = listOf("cover", "g1"), subdivisions = listOf(room, roomB))
        assertEquals(listOf(room, roomB), ListingDivisions.fromListing(listing, Level2Type.ROOMS))
        assertEquals(listOf("g1"), ListingDivisions.galleryExtrasOf(listing))
    }

    @Test
    fun unpricedDraftHasNoCardAndKeepsItsPhotos() {
        val draft = demoSpaces().first().copy(imageUrls = listOf("cover", "p1"), pricing = RentalPricingConfig.default(), subdivisions = emptyList())
        assertEquals(emptyList<Subdivision>(), ListingDivisions.fromListing(draft, Level2Type.ROOMS))
        assertEquals(listOf("p1"), ListingDivisions.galleryExtrasOf(draft))
    }
}
