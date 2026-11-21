package com.example.ui.util

import com.example.data.model.BookingRequest
import kotlin.concurrent.Volatile

/**
 * "Add payment reminders" (My Rentals): hands a booking's due dates to the user's own calendar. Android installs
 * `PaymentCalendar.addPaymentReminders` (insert intent, monthly RRULE) in ProHostApplication; iOS gets EventKitUI in C3.
 */
object PaymentReminders {
    @Volatile
    var add: (booking: BookingRequest, bookingRef: String) -> Unit = { _, _ -> }
}
