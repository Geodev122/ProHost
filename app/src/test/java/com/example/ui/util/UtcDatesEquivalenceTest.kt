package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * P2.3b: PriceChange (weekdayOf, nextTermStart, todayIso, hour padding) and SaveStats.monthKey now run on IsoDate (kotlinx-datetime).
 * The reference below is the old UTC Calendar/SimpleDateFormat code, verbatim; every day of 2024-2031 and a wide spread of instants must match.
 */
class UtcDatesEquivalenceTest {
    private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private fun utcCalendar(iso: String): Calendar? = runCatching {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = isoFormat().parse(iso.take(10))!! }
    }.getOrNull()

    private fun oldWeekdayOf(iso: String): String = utcCalendar(iso)?.let { WEEKDAYS[it.get(Calendar.DAY_OF_WEEK) - 1] } ?: ""

    private fun oldNextTermStart(monthly: Boolean, todayIso: String): String {
        val cal = utcCalendar(todayIso) ?: return todayIso
        if (monthly) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 1)
        } else {
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            val untilMonday = ((8 - dow) % 7).let { if (it == 0) 7 else it }
            cal.add(Calendar.DAY_OF_MONTH, untilMonday)
        }
        return isoFormat().format(cal.time)
    }

    private fun oldMonthKey(ms: Long, monthsBack: Int): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms; add(Calendar.MONTH, -monthsBack) }
        return "%04d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun allDays(): Sequence<String> = sequence {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2024, Calendar.JANUARY, 1, 12, 0, 0) }
        val f = isoFormat()
        while (cal.get(Calendar.YEAR) <= 2031) {
            yield(f.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun ref(kind: PriceChange.SlotKind) = PriceChange.SlotRef("SP", kind, "")

    @Test
    fun weekdayOfMatchesForEveryDayAndForTimestampSuffixes() {
        for (d in allDays()) {
            assertEquals(d, oldWeekdayOf(d), PriceChange.weekdayOf(d))
            assertEquals(d, oldWeekdayOf("${d}T08:00:00Z"), PriceChange.weekdayOf("${d}T08:00:00Z"))
        }
        assertEquals("", PriceChange.weekdayOf(""))
        assertEquals("", PriceChange.weekdayOf("not a date"))
    }

    @Test
    fun nextTermStartMatchesForEveryDayMonthlyAndWeekly() {
        for (d in allDays()) {
            assertEquals("monthly $d", oldNextTermStart(true, d), PriceChange.nextTermStart(ref(PriceChange.SlotKind.MONTHLY), d))
            for (kind in listOf(PriceChange.SlotKind.HOURLY, PriceChange.SlotKind.SHIFT, PriceChange.SlotKind.DAY)) {
                assertEquals("$kind $d", oldNextTermStart(false, d), PriceChange.nextTermStart(ref(kind), d))
            }
        }
        assertEquals("garbage", PriceChange.nextTermStart(ref(PriceChange.SlotKind.MONTHLY), "garbage"))
    }

    @Test
    fun monthKeyMatchesAcrossManyInstantsAndMonthSteps() {
        val day = 86_400_000L
        var ms = 0L // 1970-01-01 .. ~2100, stepping 17 days (hits every day-of-month alignment)
        while (ms < 4_102_444_800_000L) {
            for (back in listOf(0, 1, 2, 11, 12, 13, 25)) {
                assertEquals("$ms -$back", oldMonthKey(ms, back), SaveStats.monthKey(ms, back))
            }
            ms += 17 * day + 3_600_000L
        }
        // month boundaries to the millisecond
        for (edge in listOf(1_798_761_599_999L, 1_798_761_600_000L, 1_804_377_599_999L, 1_804_377_600_000L)) {
            assertEquals(oldMonthKey(edge, 0), SaveStats.monthKey(edge, 0))
            assertEquals(oldMonthKey(edge, 1), SaveStats.monthKey(edge, 1))
        }
        assertEquals("202610", SaveStats.monthKey(1_790_000_000_000L))
    }

    @Test
    fun todayIsTheUtcDate() {
        repeat(3) {
            val old = isoFormat().format(java.util.Date())
            if (old == PriceChange.todayIso()) return
        }
        assertEquals(isoFormat().format(java.util.Date()), PriceChange.todayIso())
    }
}
