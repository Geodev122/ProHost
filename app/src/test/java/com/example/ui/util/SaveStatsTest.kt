package com.example.ui.util

import com.example.data.model.SpaceListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveStatsTest {
    private val oct24 = 1_792_800_000_000L // 2026-10-24 UTC

    @Test
    fun monthKeysMatchTheServer() {
        assertEquals("202610", SaveStats.monthKey(oct24))
        assertEquals("202609", SaveStats.monthKey(oct24, monthsBack = 1))
        assertEquals("202512", SaveStats.monthKey(oct24, monthsBack = 10))
    }

    @Test
    fun savesAreSummedPerMonth() {
        val listings = listOf(
            SpaceListing(id = "a", favoritesByMonth = mapOf("202610" to 3, "202609" to 1)),
            SpaceListing(id = "b", favoritesByMonth = mapOf("202610" to 2))
        )
        assertEquals(5, SaveStats.savesInMonth(listings, oct24))
        assertEquals(1, SaveStats.savesInMonth(listings, oct24, monthsBack = 1))
    }

    @Test
    fun ratesNeedViewsAndStayWithin100() {
        assertNull(SaveStats.ratePercent(3, 0))
        assertEquals(25, SaveStats.ratePercent(5, 20))
        assertEquals(100, SaveStats.ratePercent(9, 4))
    }
}
