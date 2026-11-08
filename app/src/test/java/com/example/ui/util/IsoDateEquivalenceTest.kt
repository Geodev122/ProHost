package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * P2.3a: IsoDate (kotlinx-datetime) must give exactly what the app's old java.util.Calendar / SimpleDateFormat code gave, for every valid ISO day
 * of 2024-2031 (leap years 2024 and 2028 included), every month step and a set of day steps. The reference below is the old code, verbatim.
 */
class IsoDateEquivalenceTest {
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun oldShift(iso: String, field: Int, amount: Int): String? = runCatching {
        val f = fmt
        val cal = Calendar.getInstance().apply { time = f.parse(iso)!! }
        cal.add(field, amount)
        f.format(cal.time)
    }.getOrNull()

    private fun allDays(): Sequence<String> = sequence {
        val cal = Calendar.getInstance().apply { clear(); set(2024, Calendar.JANUARY, 1, 12, 0, 0) }
        val f = fmt
        while (cal.get(Calendar.YEAR) <= 2031) {
            yield(f.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    @Test
    fun addMonthsMatchesCalendarForEveryDay() {
        var checked = 0
        for (day in allDays()) {
            for (months in listOf(1, 2, 3, 6, 11, 12, 13, 24)) {
                assertEquals("$day + $months months", oldShift(day, Calendar.MONTH, months), IsoDate.addMonths(day, months))
                checked++
            }
        }
        assertEquals(true, checked > 20_000)
    }

    @Test
    fun addDaysMatchesCalendarForEveryDay() {
        for (day in allDays()) {
            for (days in listOf(1, 7, 30, 45, 366)) {
                assertEquals("$day + $days days", oldShift(day, Calendar.DAY_OF_MONTH, days), IsoDate.addDays(day, days))
            }
        }
    }

    @Test
    fun monthEndClampsLikeTheAppAlways() {
        assertEquals("2026-02-28", IsoDate.addMonths("2026-01-31", 1))
        assertEquals("2028-02-29", IsoDate.addMonths("2028-01-31", 1))
        assertEquals("2026-09-30", IsoDate.addMonths("2026-08-31", 1))
        assertEquals("2027-02-28", IsoDate.addMonths("2026-12-31", 2))
    }

    @Test
    fun dayAbbreviationMatchesSimpleDateFormatEEEForEveryDay() {
        val eee = SimpleDateFormat("EEE", Locale.US)
        val f = fmt
        for (day in allDays()) {
            assertEquals(day, eee.format(f.parse(day)!!), IsoDate.dayAbbreviation(IsoDate.parse(day)!!))
        }
    }

    @Test
    fun todayIsTheLocalDateInTheSameFormat() {
        // Guard against running across midnight between the two reads.
        repeat(3) {
            val old = fmt.format(java.util.Date())
            val new = IsoDate.todayIso()
            if (old == new) return
        }
        assertEquals(fmt.format(java.util.Date()), IsoDate.todayIso())
    }

    @Test
    fun malformedInputIsNullNotAnException() {
        assertNull(IsoDate.addMonths("Next Monda", 1))
        assertNull(IsoDate.addDays("", 1))
        assertNull(IsoDate.addDays("2026-13-01", 1))
    }
}
