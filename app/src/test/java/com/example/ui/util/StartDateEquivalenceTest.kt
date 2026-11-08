package com.example.ui.util

import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * P2.3e: SpaceCalculationUtils.resolveStartDate / nextDateForWeekday / countRecurrenceOccurrences now work on LocalDate instead of Calendar.
 * The references below are the old Calendar code, verbatim, driven from a fixed "now" (15:30 local on each tested day) so every day of
 * 2024-2031 can be compared, in three device time zones (UTC, Asia/Beirut with its midnight DST gap, America/New_York).
 */
class StartDateEquivalenceTest {
    private lateinit var original: TimeZone

    @Before fun remember() { original = TimeZone.getDefault() }

    @After fun restore() = TimeZone.setDefault(original)

    private val dayAbbreviationToCalendarDay = mapOf(
        "Sun" to Calendar.SUNDAY, "Mon" to Calendar.MONDAY, "Tue" to Calendar.TUESDAY,
        "Wed" to Calendar.WEDNESDAY, "Thu" to Calendar.THURSDAY, "Fri" to Calendar.FRIDAY,
        "Sat" to Calendar.SATURDAY
    )

    private fun nowAt(date: LocalDate): Calendar =
        Calendar.getInstance().apply { clear(); set(date.year, date.monthNumber - 1, date.dayOfMonth, 15, 30, 0) }

    private fun iso(cal: Calendar) = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    private fun oldResolveStartDate(now: Calendar, option: String, customIsoDate: String): Calendar {
        val cal = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (option) {
            "Next Monday" -> do { cal.add(Calendar.DAY_OF_MONTH, 1) } while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            "1st of Next Month" -> { cal.add(Calendar.MONTH, 1); cal.set(Calendar.DAY_OF_MONTH, 1) }
            "Custom Date" -> {
                val parts = customIsoDate.trim().split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) cal.set(parts[0], parts[1] - 1, parts[2]) else cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            else -> cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal
    }

    private fun oldNextDateForWeekday(day: String, from: Calendar): String {
        val target = dayAbbreviationToCalendarDay[day.take(3)] ?: Calendar.MONDAY
        val cal = (from.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != target) cal.add(Calendar.DAY_OF_MONTH, 1)
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun oldCountWeekly(day: String, startDate: Calendar, months: Int): Int {
        val target = dayAbbreviationToCalendarDay[day.take(3)] ?: return 0
        val end = (startDate.clone() as Calendar).apply { add(Calendar.MONTH, months) }
        val cursor = startDate.clone() as Calendar
        var count = 0
        while (cursor.before(end)) {
            if (cursor.get(Calendar.DAY_OF_WEEK) == target) count++
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        return count
    }

    private fun allDays(): Sequence<LocalDate> = sequence {
        var d = LocalDate(2024, 1, 1)
        while (d.year <= 2031) { yield(d); d = IsoDate.nextDay(d) }
    }

    private val zones = listOf("UTC", "Asia/Beirut", "America/New_York")
    private val customs = listOf("2026-10-15", "2028-02-29", "2026-1-5", "junk", "", "2026-10")

    @Test
    fun resolveStartDateMatchesForEveryDayInThreeZones() {
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (today in allDays()) {
                val now = nowAt(today)
                for (option in listOf("Next Monday", "1st of Next Month", "Tomorrow", "Whatever")) {
                    assertEquals("$zone $today $option", iso(oldResolveStartDate(now, option, "")), SpaceCalculationUtils.resolveStartDate(option, "", today).toString())
                }
                for (custom in customs) {
                    assertEquals("$zone $today custom '$custom'", iso(oldResolveStartDate(now, "Custom Date", custom)), SpaceCalculationUtils.resolveStartDate("Custom Date", custom, today).toString())
                }
            }
        }
    }

    @Test
    fun nextDateForWeekdayMatchesForEveryDayAndWeekday() {
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (today in allDays()) {
                val now = nowAt(today)
                for (day in listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Monday", "Xyz", "")) {
                    assertEquals("$zone $today $day", oldNextDateForWeekday(day, now), SpaceCalculationUtils.nextDateForWeekday(day, today))
                }
            }
        }
    }

    @Test
    fun weeklyOccurrenceCountMatchesForManyStartsAndTermLengths() {
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            var i = 0
            for (start in allDays()) {
                if (i++ % 3 != 0) continue
                val startCal = oldResolveStartDate(nowAt(start), "Custom Date", start.toString())
                for (day in listOf("Sun", "Mon", "Wed", "Sat", "Xyz")) for (months in listOf(1, 2, 3, 6, 12, 13)) {
                    assertEquals(
                        "$zone $start $day x$months",
                        oldCountWeekly(day, startCal, months),
                        SpaceCalculationUtils.countRecurrenceOccurrences(day, BookingRecurrence.SAME_DAY_EVERY_WEEK, start, months)
                    )
                }
            }
        }
        assertEquals(1, SpaceCalculationUtils.countRecurrenceOccurrences("Mon", BookingRecurrence.ONE_TIME, LocalDate(2026, 1, 1), 5))
        assertEquals(5, SpaceCalculationUtils.countRecurrenceOccurrences("Mon", BookingRecurrence.SAME_DAY_EVERY_MONTH, LocalDate(2026, 1, 1), 5))
    }
}
