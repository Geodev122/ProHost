package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ListingLocationTest {
    private val label: (String) -> String = { it }

    @Test
    fun cityAndCountryFromTheListing() {
        assertEquals("Dubai, United Arab Emirates", ListingLocation.line("Dubai", "Business Bay", "Beirut", "United Arab Emirates", label))
    }

    @Test
    fun aListingAbroadNeverShowsTheBeirutDefault() {
        assertEquals("Paris, France", ListingLocation.line("Paris", "", "Beirut", "France", label))
        assertEquals("France", ListingLocation.line("", "", "Beirut", "France", label))
    }

    @Test
    fun legacyLebaneseListingsFallBackToAreaThenGovernorate() {
        assertEquals("Achrafieh, Lebanon", ListingLocation.line("", "Achrafieh", "Beirut", "", label))
        assertEquals("Beirut, Lebanon", ListingLocation.line("", "", "Beirut", "", label))
    }

    @Test
    fun countryIsShownInTheAppLanguage() {
        assertEquals("طرابلس, لبنان", ListingLocation.line("طرابلس", "", "", "Lebanon", countryLabel = { if (it == "Lebanon") "لبنان" else it }))
    }
}
