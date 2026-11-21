package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The shared formatter must print exactly what DateRangePickerRow's SimpleDateFormat("MMM d, yyyy", Locale.US) printed. */
class UsShortDateTest {
    @Test fun `matches SimpleDateFormat in several time zones`() {
        val saved = TimeZone.getDefault()
        try {
            for (zone in listOf("UTC", "Asia/Beirut", "America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
                var t = 1_735_689_600_000L // 2025-01-01
                repeat(400) {
                    assertEquals("$zone $t", sdf.format(Date(t)), IsoDate.usShortDate(t))
                    t += 86_400_000L * 3 + 3_600_000L * 7
                }
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
