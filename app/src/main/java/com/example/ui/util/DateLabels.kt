package com.example.ui.util

import kotlinx.datetime.LocalDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Locale-aware date labels (day and month NAMES in the app language, e.g. "Mon 9 Nov" / Arabic names). This is the one place where the
 * platform's formatter is still used for dates; it is the seam that becomes an `expect`/`actual` formatter for iOS (P2.5/P3). The label of a
 * [LocalDate] is built from noon of that local date, so a clock change at midnight can never move it to another day.
 */
object DateLabels {
    /** "EEE d MMM" in the app language (falls back to the device locale before [UiText] is initialised). */
    fun dayLabel(date: LocalDate): String {
        val locale = UiText.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
        val noon = Calendar.getInstance().apply { clear(); set(date.year, date.monthNumber - 1, date.dayOfMonth, 12, 0, 0) }
        return SimpleDateFormat("EEE d MMM", locale).format(noon.time)
    }
}
