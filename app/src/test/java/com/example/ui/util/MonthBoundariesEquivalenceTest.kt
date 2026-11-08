package com.example.ui.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone as KxTimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/**
 * P2.3g: month boundaries (Manage page, Owner Analytics) and local-midnight conversion without Calendar. The old Calendar chains below are copied
 * verbatim as the reference. Validated first on the JDK in all 604 zones (11.9 million comparisons, 0 differences, see PROGRESS.md); this test
 * repeats it in CI: every JDK zone x instants every ~29 hours over 2025-2027, plus an hourly scan in the zones whose clocks change at midnight
 * (Cairo, Havana, Santiago, Beirut, Azores) and in Berlin / New York / Lord Howe, plus local midnight of every date 2024-2032 in those zones.
 * Cuba and the Azores have a repeated midnight (clocks go back at 00:00 / 01:00): plain java.time would differ there by an hour; the new code takes
 * the later occurrence exactly like Calendar.
 */
class MonthBoundariesEquivalenceTest {
    private fun oldThisMonth(now: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        val s = (c.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return s.timeInMillis
    }

    private fun oldLastMonth(now: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        val s = (c.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return (s.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis
    }

    private fun oldPrevSameDay(now: Long, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun oldPrevMonthStart(prevSameDay: Long, tz: TimeZone): Long =
        Calendar.getInstance(tz).apply { timeInMillis = prevSameDay; set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    private fun check(zoneId: String, now: Long): Int {
        val tz = TimeZone.getTimeZone(zoneId)
        val kx = KxTimeZone.of(zoneId)
        val (thisStart, lastStart) = MonthBoundaries.manage(now, kx)
        val a = MonthBoundaries.analytics(now, kx)
        val oldPrevSame = oldPrevSameDay(now, tz)
        assertEquals("$zoneId now=$now manage.thisMonth", oldThisMonth(now, tz), thisStart)
        assertEquals("$zoneId now=$now manage.lastMonth", oldLastMonth(now, tz), lastStart)
        assertEquals("$zoneId now=$now analytics.currentMonthStart", oldThisMonth(now, tz), a.currentMonthStart)
        assertEquals("$zoneId now=$now analytics.prevMonthSameDay", oldPrevSame, a.prevMonthSameDay)
        assertEquals("$zoneId now=$now analytics.prevMonthStart", oldPrevMonthStart(oldPrevSame, tz), a.prevMonthStart)
        return 5
    }

    private val from = 1_735_689_600_000L // 2025-01-01T00:00Z
    private val to = 1_830_297_600_000L // 2028-01-01T00:00Z

    @Test
    fun everyJdkZoneAtInstantsEveryAboutTwentyNineHours() {
        var checked = 0
        for (id in ZoneId.getAvailableZoneIds().sorted()) {
            if (runCatching { KxTimeZone.of(id) }.isFailure) continue
            var now = from
            while (now < to) { checked += check(id, now); now += 29 * 3_600_000L + 1_234_567L }
        }
        assertTrue("checked $checked", checked > 1_000_000)
    }

    @Test
    fun hourlyScanInTheMidnightClockChangeZonesAndThreeCommonOnes() {
        for (id in listOf("Africa/Cairo", "America/Havana", "America/Santiago", "Asia/Beirut", "Atlantic/Azores", "Europe/Berlin", "America/New_York", "Australia/Lord_Howe")) {
            var now = from
            while (now < to) { check(id, now); now += 3_600_000L + 421_000L }
        }
    }

    @Test
    fun localMidnightOfEveryDateMatchesCalendarIncludingTheRepeatedMidnight() {
        for (id in listOf("America/Havana", "Cuba", "Atlantic/Azores", "Africa/Cairo", "Asia/Beirut", "America/Santiago", "Europe/Berlin", "UTC")) {
            val tz = TimeZone.getTimeZone(id)
            val kx = KxTimeZone.of(id)
            var d = LocalDate(2024, 1, 1)
            while (d.year <= 2032) {
                val old = Calendar.getInstance(tz).apply { clear(); set(d.year, d.monthNumber - 1, d.dayOfMonth, 0, 0, 0) }.timeInMillis
                assertEquals("$id $d", old, IsoDate.startOfLocalDayMillis(d, kx))
                d = IsoDate.nextDay(d)
            }
        }
        // the repeated midnight itself (Havana 2026-11-01: clocks go back at 01:00, so 00:00-01:00 happens twice; Calendar takes the later one = 05:00Z)
        assertEquals(1_793_509_200_000L, IsoDate.startOfLocalDayMillis(LocalDate(2026, 11, 1), KxTimeZone.of("America/Havana")))
    }
}
