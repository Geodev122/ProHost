package com.example.ui.util

import com.example.ui.preview.mockBooking1
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * P2.3f: the Manage page / My Bookings date logic moved to IsoDate, DateLabels and BookingDates. Old code copied verbatim as the reference:
 *  - DateLabels.dayLabel vs SimpleDateFormat("EEE d MMM", locale).format(<an instant on that local day>) for every day of 2024-2031, English and Arabic, three zones;
 *  - BookingDates.hasStarted vs the old `SimpleDateFormat.parse(startDate)` + `!start.after(Date())`;
 *  - SpaceCalculationUtils.bookingCoversDate vs the Manage page's private Calendar/SimpleDateFormat copy.
 */
class ManageDatesEquivalenceTest {
    private lateinit var tz: TimeZone
    private lateinit var locale: Locale
    private var savedResources: android.content.res.Resources? = null

    @Before
    fun remember() {
        tz = TimeZone.getDefault(); locale = Locale.getDefault()
        savedResources = UiText.resources
        UiText.resources = null // dayLabel then follows the device locale, which the test sets
    }

    @After
    fun restore() {
        TimeZone.setDefault(tz); Locale.setDefault(locale); UiText.resources = savedResources
    }

    private fun allDays(): Sequence<LocalDate> = sequence {
        var d = LocalDate(2024, 1, 1)
        while (d.year <= 2031) { yield(d); d = IsoDate.nextDay(d) }
    }

    private val zones = listOf("UTC", "Asia/Beirut", "America/New_York")

    @Test
    fun dayLabelMatchesTheOldFormatterForEveryDayInEnglishAndArabic() {
        for (tag in listOf("en-US", "ar")) for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone)); Locale.setDefault(Locale.forLanguageTag(tag))
            val old = SimpleDateFormat("EEE d MMM", Locale.getDefault())
            for (day in allDays()) {
                val instant = Calendar.getInstance().apply { clear(); set(day.year, day.monthNumber - 1, day.dayOfMonth, 15, 30, 0) }.time
                assertEquals("$tag $zone $day", old.format(instant), DateLabels.dayLabel(day))
            }
        }
    }

    private fun oldHasStarted(startDate: String, now: Date): Boolean = runCatching {
        val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(startDate)
        start == null || !start.after(now)
    }.getOrDefault(true)

    @Test
    fun hasStartedMatchesForStartsAroundEveryDayAndForNonDates() {
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (today in allDays()) {
                val now = Calendar.getInstance().apply { clear(); set(today.year, today.monthNumber - 1, today.dayOfMonth, 15, 30, 0) }.time
                for (offset in -3..3) {
                    val start = IsoDate.plusDays(today, offset).toString()
                    assertEquals("$zone today=$today start=$start", oldHasStarted(start, now), BookingDates.hasStarted(start, today))
                    assertEquals("$zone ${start}T08:00", oldHasStarted("${start}T08:00", now), BookingDates.hasStarted("${start}T08:00", today))
                }
            }
            val now = Date()
            for (junk in listOf("", "Next Monday", "2026-10", "legacy label")) {
                assertEquals(junk, oldHasStarted(junk, now), BookingDates.hasStarted(junk))
            }
        }
    }

    // The Manage page's old private copy of bookingCoversDate (verbatim).
    private fun oldCovers(startDateRaw: String, months: Int, calendarDates: List<String>, isoDate: String): Boolean {
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        if (calendarDates.isNotEmpty()) return isoDate in calendarDates
        val start = startDateRaw.trim().take(10)
        if (start.length < 7) return true
        val startKey = if (start.length == 7) "$start-01" else start
        val end = runCatching {
            val cal = Calendar.getInstance().apply { time = iso.parse(startKey) ?: return true }
            cal.add(Calendar.MONTH, months.coerceAtLeast(1))
            iso.format(cal.time)
        }.getOrNull() ?: return true
        return isoDate >= startKey && isoDate < end
    }

    @Test
    fun bookingCoversDateMatchesTheOldManagePageCopy() {
        var i = 0
        for (start in allDays()) {
            if (i++ % 5 != 0) continue
            for (months in listOf(1, 2, 3, 12, 13)) for (probe in listOf(-1, 0, 1, 27, 28, 29, 30, 31, 32, 364, 365, 366, 400)) {
                val booking = mockBooking1.copy(startDate = start.toString(), durationMonths = months, selectedCalendarDates = emptyList())
                val probeIso = IsoDate.plusDays(start, probe).toString()
                assertEquals("$start x$months probe $probeIso", oldCovers(start.toString(), months, emptyList(), probeIso), SpaceCalculationUtils.bookingCoversDate(booking, probeIso))
            }
        }
        for ((raw, months) in listOf("" to 1, "2026-10" to 2, "Next Monday" to 1, "garbage here" to 3, "2026-10-15T08:00" to 1)) {
            val booking = mockBooking1.copy(startDate = raw, durationMonths = months, selectedCalendarDates = emptyList())
            for (probe in listOf("2026-10-01", "2026-10-15", "2026-11-15", "2027-01-01")) {
                assertEquals("$raw $probe", oldCovers(raw, months, emptyList(), probe), SpaceCalculationUtils.bookingCoversDate(booking, probe))
            }
        }
        val booking = mockBooking1.copy(selectedCalendarDates = listOf("2026-10-15"))
        assertEquals(oldCovers("x", 1, listOf("2026-10-15"), "2026-10-15"), SpaceCalculationUtils.bookingCoversDate(booking, "2026-10-15"))
    }
}
