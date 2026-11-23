package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionsTest {
    @Test fun lebanonHasItsEightGovernoratesInEnglish() {
        assertEquals(listOf("Akkar", "Baalbek-Hermel", "Beirut", "Beqaa", "Mount Lebanon", "Nabatieh", "North Lebanon", "South Lebanon"), Regions.of("lb"))
    }

    @Test fun everyListedCountryHasAreas() {
        assertTrue(Regions.of("US").contains("California"))
        assertTrue(Regions.of("AE").isNotEmpty())
        assertEquals(emptyList<String>(), Regions.of("ZZ"))
    }

    @Test fun geocoderNamesMatchTheList() {
        assertEquals("Mount Lebanon", Regions.match("LB", "Mount Lebanon Governorate"))
        assertEquals("Beirut", Regions.match("LB", "beirut"))
        assertNull(Regions.match("LB", null))
    }

    @Test fun arabicLabelsForLebanonElseEnglish() {
        assertEquals("جبل لبنان", Regions.label("LB", "Mount Lebanon", "ar"))
        assertEquals("Mount Lebanon", Regions.label("LB", "Mount Lebanon", "en"))
        assertEquals("California", Regions.label("US", "California", "ar"))
    }
}
