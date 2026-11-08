package com.example.ui.util

/**
 * The booking dialog's UTC date helpers (the Material date picker works in UTC-midnight millis), moved out of `RentalBookingDialog` so they can be
 * tested and shared: all date-only, no time of day, no `java.util.Calendar`. `BookingDatesEquivalenceTest` proves them equal to the old Calendar code.
 * Malformed input (a date that does not exist, e.g. `2026-02-30`) falls back instead of being rolled into the next month; the dialog only ever
 * passes dates the picker produced.
 */
internal object BookingDates {
    private val ABBREVIATIONS = setOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /** `Sun`..`Sat` of the UTC date [utcTimeMillis] falls on. */
    fun weekdayAbbreviation(utcTimeMillis: Long): String = IsoDate.dayAbbreviation(IsoDate.dateFromUtcMillis(utcTimeMillis))

    /** `yyyy-MM-dd` of the UTC date [utcTimeMillis] falls on. */
    fun isoDateString(utcTimeMillis: Long): String = IsoDate.dateFromUtcMillis(utcTimeMillis).toString()

    /** `Sun`..`Sat` of an ISO date string; "Mon" when it is not a date (as before). */
    fun weekdayAbbreviationFromIso(iso: String): String {
        val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return "Mon"
        val date = IsoDate.dateOrNull(parts[0], parts[1], parts[2]) ?: return "Mon"
        return IsoDate.dayAbbreviation(date)
    }

    /** Midnight UTC tomorrow: the earliest selectable date across every calendar-date picker in the dialog. */
    fun tomorrowUtcMillis(): Long = IsoDate.startOfDayUtcMillis(IsoDate.nextDay(IsoDate.todayUtcDate()))

    /** Every real date matching [weekdayAbbrev] from tomorrow through [untilIso] inclusive (Day-Based "recurring weekly until a date"). */
    fun weeklyOccurrencesUntil(weekdayAbbrev: String, untilIso: String): List<String> {
        if (weekdayAbbrev !in ABBREVIATIONS) return emptyList()
        var cursor = IsoDate.nextDay(IsoDate.todayUtcDate())
        while (IsoDate.dayAbbreviation(cursor) != weekdayAbbrev) cursor = IsoDate.nextDay(cursor)
        val parts = untilIso.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return emptyList()
        val until = IsoDate.dateOrNull(parts[0], parts[1], parts[2]) ?: return emptyList()
        val dates = mutableListOf<String>()
        while (cursor <= until) {
            dates.add(cursor.toString())
            cursor = IsoDate.plusDays(cursor, 7)
        }
        return dates
    }

    /**
     * Whether a booking that starts on [startDate] (an ISO date, or anything else) has started: the start (midnight, local) is no later than now, i.e. the
     * date is [today] or earlier. Anything that is not a date counts as started (legacy labels). Prefix-compatible with the old lenient parse of
     * `yyyy-MM-dd...` (only the first 10 characters are read).
     */
    fun hasStarted(startDate: String, today: kotlinx.datetime.LocalDate = IsoDate.todayDate()): Boolean =
        IsoDate.parse(startDate.take(10))?.let { it <= today } ?: true
}
