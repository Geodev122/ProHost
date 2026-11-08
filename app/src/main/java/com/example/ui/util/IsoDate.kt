package com.example.ui.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * Calendar-date arithmetic on ISO `yyyy-MM-dd` strings with no `java.util.Calendar` / `SimpleDateFormat`, so it can live in shared (commonMain)
 * code. Same rules the app always used: adding months clamps to the last day of the target month (2026-01-31 + 1 month = 2026-02-28, like
 * `Calendar.add(MONTH)` and, since 8 Nov 2026, `functions/src/bookings/bookingTerms.ts`); "today" is the device's local date.
 * Malformed input returns null (the old lenient `SimpleDateFormat` would roll `2026-02-30` into March; the app only ever stores valid ISO dates).
 * `IsoDateEquivalenceTest` proves equality with the old Calendar code for every day of 2024-2031.
 */
object IsoDate {
    fun parse(iso: String): LocalDate? = try {
        LocalDate.parse(iso)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun nextDay(date: LocalDate): LocalDate = date.plus(1, DateTimeUnit.DAY)

    fun addDays(iso: String, days: Int): String? = parse(iso)?.plus(days, DateTimeUnit.DAY)?.toString()

    fun addMonths(iso: String, months: Int): String? = parse(iso)?.plus(months, DateTimeUnit.MONTH)?.toString()

    /** The device's local date today, as `yyyy-MM-dd`. */
    fun todayIso(): String = todayDate().toString()

    fun todayDate(): LocalDate = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** `Mon`, `Tue`, ... (English, as stored in slot day keys), the same text `SimpleDateFormat("EEE", Locale.US)` printed. */
    fun dayAbbreviation(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    // --- UTC helpers (server-style dates: price changes, save statistics) ---

    /** Today's date in UTC, `yyyy-MM-dd`. */
    fun todayUtcIso(): String = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()

    /** 0 = Sunday ... 6 = Saturday. */
    fun weekdaySunday0(date: LocalDate): Int = date.dayOfWeek.isoDayNumber % 7

    fun plusDays(date: LocalDate, days: Int): LocalDate = date.plus(days, DateTimeUnit.DAY)

    /** The 1st of the month after [date]'s month. */
    fun firstOfNextMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.monthNumber, 1).plus(1, DateTimeUnit.MONTH)

    /** `yyyyMM` of [ms] in UTC, [monthsBack] whole months earlier (the server's `favoritesByMonth` key). */
    fun monthKeyUtc(ms: Long, monthsBack: Int = 0): String {
        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
        val index = d.year * 12 + (d.monthNumber - 1) - monthsBack
        return index.floorDiv(12).toString().padStart(4, '0') + (index.mod(12) + 1).toString().padStart(2, '0')
    }

    /** The UTC date of [utcMillis] (what the Material date picker's UTC-midnight millis stand for). */
    fun dateFromUtcMillis(utcMillis: Long): LocalDate = Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date

    /** Today's date in UTC. */
    fun todayUtcDate(): LocalDate = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    /** Midnight UTC at the start of [date], in epoch millis (the date picker's value for that day). */
    fun startOfDayUtcMillis(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    /** The date for year/month/day, or null when it does not exist (month 13, Feb 30 ...). */
    fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate(year, month, day)
    } catch (e: IllegalArgumentException) {
        null
    }
}
