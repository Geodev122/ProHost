package com.example.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.example.data.model.BookingRequest
import com.example.data.model.RentalFormulaType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Hands a booking's payment due dates to the user's own calendar app (Google Calendar,
 * Samsung Calendar, …) via the standard insert intent. The user picks the app and the
 * account (Google or on-device), and that calendar's own reminders give localized,
 * offline alerts — no calendar permission and no Google OAuth scope needed.
 */
object PaymentCalendar {

    /** When rent is due: one event, repeated monthly for multi-month leases. */
    data class PaymentSchedule(
        val firstDueUtcMillis: Long,
        val occurrences: Int,
        val amountPerPaymentUsd: Double,
        val isMonthly: Boolean
    )

    fun scheduleFor(booking: BookingRequest): PaymentSchedule? {
        val start = parseIsoDateUtc(booking.startDate) ?: return null
        val months = booking.durationMonths.coerceAtLeast(1)
        val isMonthly = booking.formula.type == RentalFormulaType.FULL_MONTH && months > 1
        return if (isMonthly) {
            PaymentSchedule(start, months, booking.totalAmountUsd / months, isMonthly = true)
        } else {
            PaymentSchedule(start, 1, booking.totalAmountUsd, isMonthly = false)
        }
    }

    fun addPaymentReminders(context: Context, booking: BookingRequest, bookingRef: String) {
        val schedule = scheduleFor(booking)
        if (schedule == null) {
            Toast.makeText(context, "This booking has no start date yet, so there's no due date to add.", Toast.LENGTH_LONG).show()
            return
        }
        val money = NumberFormat.getCurrencyInstance(Locale.US).format(schedule.amountPerPaymentUsd)
        val description = buildString {
            appendLine("ProHost booking $bookingRef — ${booking.spaceTitle}")
            if (schedule.isMonthly) {
                appendLine("Monthly rent: $money (${schedule.occurrences} payments)")
            } else {
                appendLine("Payment due: $money")
            }
            append("Pay your host directly, then tap \"Mark as Paid\" in ProHost → My Bookings.")
        }
        // All-day events must start at UTC midnight (CalendarContract requirement).
        val insert = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Rent due · ${booking.spaceTitle}")
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, schedule.firstDueUtcMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, schedule.firstDueUtcMillis + DAY_MILLIS)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
        if (schedule.isMonthly) {
            insert.putExtra(CalendarContract.Events.RRULE, "FREQ=MONTHLY;COUNT=${schedule.occurrences}")
        }
        try {
            // A chooser lets people with several calendar apps pick the one they use.
            val chooser = Intent.createChooser(insert, "Add payment reminders to…")
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No calendar app found on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseIsoDateUtc(date: String): Long? {
        if (date.isBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(date)?.time
        }.getOrNull()
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
