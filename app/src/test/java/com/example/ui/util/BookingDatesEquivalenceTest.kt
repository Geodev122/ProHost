package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * P2.3d: the booking dialog's UTC date helpers (now BookingDates, on IsoDate) must equal the old Calendar code, which is copied below verbatim
 * as the reference. Every day of 2024-2031 at three times of day, every weekday against many "until" dates, and the +7-days default start.
 */
class BookingDatesEquivalenceTest {
    private val calendarDayAbbreviations = mapOf(
        Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )

    private fun oldWeekdayAbbreviation(utcTimeMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = utcTimeMillis
        return calendarDayAbbreviations[cal.get(Calendar.DAY_OF_WEEK)] ?: "Mon"
    }

    private fun oldIsoDateString(utcTimeMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = utcTimeMillis
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun oldWeekdayAbbreviationFromIso(iso: String): String {
        val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return "Mon"
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return calendarDayAbbreviations[cal.get(Calendar.DAY_OF_WEEK)] ?: "Mon"
    }

    private fun oldTomorrowUtcMillis(): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun oldWeeklyOccurrencesUntil(weekdayAbbrev: String, untilIso: String): List<String> {
        val targetDow = calendarDayAbbreviations.entries.firstOrNull { it.value == weekdayAbbrev }?.key ?: return emptyList()
        val cursor = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = oldTomorrowUtcMillis() }
        while (cursor.get(Calendar.DAY_OF_WEEK) != targetDow) {
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        val untilParts = untilIso.split("-").mapNotNull { it.toIntOrNull() }
        if (untilParts.size != 3) return emptyList()
        val until = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(untilParts[0], untilParts[1] - 1, untilParts[2], 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dates = mutableListOf<String>()
        while (!cursor.after(until)) {
            dates.add(oldIsoDateString(cursor.timeInMillis))
            cursor.add(Calendar.DAY_OF_MONTH, 7)
        }
        return dates
    }

    private fun utcDays(): Sequence<Long> = sequence {
        var ms = 1_704_067_200_000L // 2024-01-01T00:00Z
        val end = 1_957_824_000_000L // 2032-01-01
        while (ms < end) { yield(ms); ms += 86_400_000L }
    }

    @Test
    fun weekdayAndIsoStringMatchForEveryUtcDayAtThreeTimesOfDay() {
        for (day in utcDays()) for (offset in listOf(0L, 43_200_000L, 86_399_999L)) {
            val ms = day + offset
            assertEquals("weekday $ms", oldWeekdayAbbreviation(ms), BookingDates.weekdayAbbreviation(ms))
            assertEquals("iso $ms", oldIsoDateString(ms), BookingDates.isoDateString(ms))
        }
    }

    @Test
    fun weekdayFromIsoMatchesForEveryDayAndFallsBackLikeBefore() {
        for (day in utcDays()) {
            val iso = oldIsoDateString(day)
            assertEquals(iso, oldWeekdayAbbreviationFromIso(iso), BookingDates.weekdayAbbreviationFromIso(iso))
        }
        for (junk in listOf("", "x", "2026-10", "a-b-c", "2026-10-15-01")) {
            assertEquals(junk, oldWeekdayAbbreviationFromIso(junk), BookingDates.weekdayAbbreviationFromIso(junk))
        }
        // non-padded numbers still work (as with the lenient Calendar)
        assertEquals(oldWeekdayAbbreviationFromIso("2026-1-5"), BookingDates.weekdayAbbreviationFromIso("2026-1-5"))
    }

    @Test
    fun tomorrowIsMidnightUtcTomorrow() {
        repeat(3) { if (oldTomorrowUtcMillis() == BookingDates.tomorrowUtcMillis()) return } // guard against midnight between the two reads
        assertEquals(oldTomorrowUtcMillis(), BookingDates.tomorrowUtcMillis())
    }

    @Test
    fun weeklyOccurrencesMatchForEveryWeekdayAndManyUntilDates() {
        val today = oldTomorrowUtcMillis()
        val untilDays = (-3..400 step 7).map { today + it * 86_400_000L } + listOf(today + 3L * 365 * 86_400_000L)
        for (abbr in listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) for (until in untilDays) {
            val iso = oldIsoDateString(until)
            assertEquals("$abbr until $iso", oldWeeklyOccurrencesUntil(abbr, iso), BookingDates.weeklyOccurrencesUntil(abbr, iso))
        }
        assertEquals(emptyList<String>(), BookingDates.weeklyOccurrencesUntil("Funday", "2030-01-01"))
        assertEquals(oldWeeklyOccurrencesUntil("Mon", "junk"), BookingDates.weeklyOccurrencesUntil("Mon", "junk"))
    }

    @Test
    fun defaultCustomStartIsSevenLocalDaysAhead() {
        fun old(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 7) }.time
        )
        repeat(3) { if (old() == IsoDate.addDays(IsoDate.todayIso(), 7)) return }
        assertEquals(old(), IsoDate.addDays(IsoDate.todayIso(), 7))
    }
}
