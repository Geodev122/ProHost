package com.example.ui.util

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The CSV exports moved from SimpleDateFormat to [IsoDate.localDateTimeText]; both must print the same text for every instant. */
class LocalDateTimeTextTest {
    private val zones = listOf("UTC", "Asia/Beirut", "America/New_York", "Asia/Kolkata", "Pacific/Chatham")

    private fun legacy(ms: Long, zone: String): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(Date(ms))

    @Test fun matchesSimpleDateFormatAcrossZonesAndClockChanges() {
        val instants = buildList {
            add(0L)
            add(1_762_600_000_000L) // Nov 2025
            add(1_798_761_599_999L) // last ms of 2026 UTC
            // every 37 minutes and 13 s across 2026, which crosses each zone's clock changes
            var t = 1_767_225_600_000L
            while (t < 1_798_761_600_000L) { add(t); t += 2_233_000L }
        }
        for (zone in zones) for (ms in instants) {
            assertEquals("$zone @ $ms", legacy(ms, zone), IsoDate.localDateTimeText(ms, TimeZone.of(zone)))
        }
    }
}
