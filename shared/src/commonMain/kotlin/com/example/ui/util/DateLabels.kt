package com.example.ui.util

import kotlinx.datetime.LocalDate
import kotlin.concurrent.Volatile

/**
 * Locale-aware date labels (day and month NAMES in the app language, e.g. "Mon 9 Nov" / Arabic names). The platform formats them:
 * Android installs its SimpleDateFormat("EEE d MMM") version (AndroidDateLabels), iOS will install an NSDateFormatter one. Until a
 * platform installs it, an English fallback is used.
 */
object DateLabels {
    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    @Volatile
    var format: (LocalDate) -> String = { d -> "${DAYS[d.dayOfWeek.ordinal]} ${d.dayOfMonth} ${MONTHS[d.monthNumber - 1]}" }

    /** "EEE d MMM" in the app language. */
    fun dayLabel(date: LocalDate): String = format(date)
}
