package com.example.data.billing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * P2.3c: PlayOfferText.regularPriceStartsAt and PlayPromo.endOfDayBeirut now use kotlinx-datetime. The references below are the old
 * java.util.Calendar code, verbatim. They run in eight device time zones (DST and non-DST, half-hour and 45-minute offsets, southern
 * hemisphere), for start instants every day of 2026-2027 at three hours, and for every calendar day (valid or not) of 2025-2032.
 */
class BillingDatesEquivalenceTest {
    private lateinit var original: TimeZone

    @Before
    fun remember() { original = TimeZone.getDefault() }

    @After
    fun restore() = TimeZone.setDefault(original)

    private fun oldRegularPriceStartsAt(startMillis: Long, intro: List<Pair<Int, String>>): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
        for ((n, unit) in intro) {
            val field = when (unit) {
                "day" -> Calendar.DAY_OF_YEAR
                "week" -> Calendar.WEEK_OF_YEAR
                "month" -> Calendar.MONTH
                else -> Calendar.YEAR
            }
            cal.add(field, n)
        }
        return cal.timeInMillis
    }

    private fun oldEndOfDayBeirut(yyyymmdd: String): Long? {
        val m = Regex("(\\d{4})(\\d{2})(\\d{2})").matchEntire(yyyymmdd) ?: return null
        val (y, mo, d) = m.destructured
        val month = mo.toInt().takeIf { it in 1..12 } ?: return null
        val day = d.toInt().takeIf { it in 1..31 } ?: return null
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Beirut")).apply {
            isLenient = false
            clear()
            set(y.toInt(), month - 1, day, 23, 59, 59)
        }.let { runCatching { it.timeInMillis }.getOrNull() }
    }

    // (ISO period, cycles) lists as Play returns them; the old reference gets the already multiplied (n * cycles, unit).
    private val periodLists = listOf(
        listOf("P3D" to 1), listOf("P1W" to 1), listOf("P1M" to 1), listOf("P1M" to 3), listOf("P1Y" to 1), listOf("P7D" to 2, "P1M" to 1),
    )

    private fun expandOld(list: List<Pair<String, Int>>): List<Pair<Int, String>> = list.map { (iso, cycles) ->
        val n = Regex("P(\\d+)([DWMY])").matchEntire(iso)!!.let { it.groupValues[1].toInt() }
        val unit = when (iso.last()) { 'D' -> "day"; 'W' -> "week"; 'M' -> "month"; else -> "year" }
        n * cycles to unit
    }

    @Test
    fun regularPriceStartsAtMatchesCalendarInEightZones() {
        val zones = listOf("UTC", "Asia/Beirut", "America/New_York", "Europe/Berlin", "Australia/Lord_Howe", "Pacific/Auckland", "Asia/Kolkata", "America/Sao_Paulo")
        val dayMs = 86_400_000L
        val first = 1_767_225_600_000L // 2026-01-01T00:00Z
        var checked = 0
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (day in 0 until 730) {
                for (hour in listOf(0L, 11L, 22L)) {
                    val start = first + day * dayMs + hour * 3_600_000L + 123_456L
                    for (list in periodLists) {
                        val expected = oldRegularPriceStartsAt(start, expandOld(list))
                        assertEquals("$zone start=$start $list", expected, PlayOfferText.regularPriceStartsAt(start, list))
                        checked++
                    }
                }
            }
        }
        assertEquals(true, checked > 100_000)
    }

    @Test
    fun regularPriceStartsAtIgnoresMalformedPeriodsLikeBefore() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val start = 1_767_225_600_000L
        assertEquals(start, PlayOfferText.regularPriceStartsAt(start, listOf("junk" to 1)))
        assertEquals(oldRegularPriceStartsAt(start, listOf(7 to "day")), PlayOfferText.regularPriceStartsAt(start, listOf("junk" to 1, "P7D" to 1)))
    }

    @Test
    fun endOfDayBeirutMatchesForEveryDayValidOrNot() {
        var checked = 0
        for (year in 2025..2032) for (month in 1..12) for (day in 1..31) {
            val key = "%04d%02d%02d".format(java.util.Locale.US, year, month, day)
            assertEquals(key, oldEndOfDayBeirut(key), PlayPromo.endOfDayBeirut(key))
            checked++
        }
        for (bad in listOf("", "2026123", "202612311", "abcdefgh", "20261301", "20260001", "20260100", "00001231", "20260230", "20250229", "20280229")) {
            assertEquals(bad, oldEndOfDayBeirut(bad), PlayPromo.endOfDayBeirut(bad))
        }
        assertEquals(true, checked > 2_800)
    }
}
