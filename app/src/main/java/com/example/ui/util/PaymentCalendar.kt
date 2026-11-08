package com.example.ui.util

import com.example.R
import android.content.ActivityNotFoundException
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

    fun addPaymentReminders(booking: BookingRequest, bookingRef: String) {
        val schedule = scheduleFor(booking)
        if (schedule == null) {
            UiMessages.show(uiText(R.string.calendar_no_start_date), long = true)
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
        try {
            // All-day events must start at UTC midnight (CalendarContract requirement); a chooser lets people with several calendar apps pick theirs.
            PlatformActions.require().addCalendarEvent(
                chooserTitle = uiText(R.string.calendar_add_reminders_to),
                title = uiText(R.string.calendar_rent_due_x, booking.spaceTitle),
                description = description,
                allDayStartUtcMillis = schedule.firstDueUtcMillis,
                allDayEndUtcMillis = schedule.firstDueUtcMillis + DAY_MILLIS,
                rrule = if (schedule.isMonthly) "FREQ=MONTHLY;COUNT=${schedule.occurrences}" else null
            )
        } catch (e: ActivityNotFoundException) {
            UiMessages.show(uiText(R.string.calendar_no_app), long = true)
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
