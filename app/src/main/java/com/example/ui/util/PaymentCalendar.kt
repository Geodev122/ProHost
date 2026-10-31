package com.example.ui.util

import com.example.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
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
            UiMessages.show(context, uiText(R.string.calendar_no_start_date), long = true)
            return
        }
        val money = NumberFormat.getCurrencyInstance(Locale.US).format(schedule.amountPerPaymentUsd)
        val description = buildString {
            appendLine(uiText(R.string.calendar_booking_line, bookingRef, booking.spaceTitle))
            if (schedule.isMonthly) {
                appendLine(uiText(R.string.calendar_monthly_rent, money, schedule.occurrences))
            } else {
                appendLine(uiText(R.string.calendar_payment_due, money))
            }
            booking.lastPriceChange?.takeIf { it.changedTotal }?.let { change ->
                val usd = NumberFormat.getCurrencyInstance(Locale.US)
                appendLine(
                    if (change.effectiveFrom.isNotBlank()) {
                        uiText(R.string.calendar_price_updated_from, usd.format(change.oldTotal), usd.format(change.newTotal), change.effectiveFrom)
                    } else {
                        uiText(R.string.calendar_price_updated, usd.format(change.oldTotal), usd.format(change.newTotal))
                    }
                )
            }
            append(uiText(R.string.calendar_pay_directly))
        }
        // All-day events must start at UTC midnight (CalendarContract requirement).
        val insert = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, uiText(R.string.calendar_rent_due_x, booking.spaceTitle))
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
            val chooser = Intent.createChooser(insert, uiText(R.string.calendar_add_reminders_to))
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            UiMessages.show(context, uiText(R.string.calendar_no_app), long = true)
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
