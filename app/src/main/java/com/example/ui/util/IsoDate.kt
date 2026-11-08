package com.example.ui.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
}
