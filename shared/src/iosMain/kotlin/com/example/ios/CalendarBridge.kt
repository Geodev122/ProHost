package com.example.ios

import com.example.data.model.BookingRequest
import com.example.data.model.RentalFormulaType
import com.example.ui.util.PlatformActions
import com.example.ui.util.UiMessages
import com.example.ui.util.sharedText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle

/**
 * Apple's "new event" sheet (Swift `CalendarBridgeImpl`, EventKitUI; no calendar permission needed for it). The day is
 * the UTC calendar date of [startUtcMillis]; [monthlyCount] > 1 repeats it monthly that many times.
 */
interface CalendarBridge {
    fun presentAllDayEvent(title: String, notes: String, startUtcMillis: Long, monthlyCount: Int)
}

/** The iOS copy of Android's `PaymentCalendar` (same schedule, same texts, US-dollar amounts like `NumberFormat(Locale.US)`). */
object IosPaymentReminders {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun add(booking: BookingRequest, bookingRef: String) {
        val start = runCatching { LocalDate.parse(booking.startDate).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
            .getOrNull()
        if (booking.startDate.isBlank() || start == null) {
            UiMessages.show(sharedText("calendar_no_start_date"), long = true)
            return
        }
        val months = booking.durationMonths.coerceAtLeast(1)
        val isMonthly = booking.formula.type == RentalFormulaType.FULL_MONTH && months > 1
        val occurrences = if (isMonthly) months else 1
        val money = usd(if (isMonthly) booking.totalAmountUsd / months else booking.totalAmountUsd)
        val description = buildString {
            appendLine(sharedText("calendar_booking_line", bookingRef, booking.spaceTitle))
            if (isMonthly) appendLine(sharedText("calendar_monthly_rent", money, occurrences))
            else appendLine(sharedText("calendar_payment_due", money))
            booking.lastPriceChange?.takeIf { it.changedTotal }?.let { change ->
                appendLine(
                    if (change.effectiveFrom.isNotBlank()) {
                        sharedText("calendar_price_updated_from", usd(change.oldTotal), usd(change.newTotal), change.effectiveFrom)
                    } else {
                        sharedText("calendar_price_updated", usd(change.oldTotal), usd(change.newTotal))
                    }
                )
            }
            append(sharedText("calendar_pay_directly"))
        }
        try {
            PlatformActions.require().addCalendarEvent(
                chooserTitle = sharedText("calendar_add_reminders_to"),
                title = sharedText("calendar_rent_due_x", booking.spaceTitle),
                description = description,
                allDayStartUtcMillis = start,
                allDayEndUtcMillis = start + DAY_MILLIS,
                rrule = if (isMonthly) "FREQ=MONTHLY;COUNT=$occurrences" else null
            )
        } catch (e: Exception) {
            UiMessages.show(sharedText("calendar_no_app"), long = true)
        }
    }

    private fun usd(amount: Double): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale(localeIdentifier = "en_US")
        }
        return formatter.stringFromNumber(NSNumber(double = amount)) ?: "$$amount"
    }
}
