package com.example.ui.util

import kotlinx.datetime.LocalDate

/**
 * Dates with month and day NAMES in the app language ("Tue 17 Nov", "December 2026", Arabic names). The platform installs the
 * formatter (Android: java.time `DateTimeFormatter.ofPattern(pattern, locale)` in `AndroidLocaleDates`, ProHostApplication);
 * the defaults below are only used before that (and on platforms without one yet).
 */
object LocaleDates {
    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** [date] formatted with a java.time-style [pattern] ("EEE d MMM", "d MMM yyyy", "LLLL yyyy") in [languageTag]. */
    var format: (date: LocalDate, pattern: String, languageTag: String) -> String = { d, _, _ -> d.toString() }

    /** Short weekday name of ISO day [isoDay] (1 = Monday … 7 = Sunday) in [languageTag]. */
    var weekdayShort: (isoDay: Int, languageTag: String) -> String = { d, _ -> DAYS[d - 1] }
}
