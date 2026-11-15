package com.example.ui.util

import com.example.data.model.RentalBookingRequest
import com.example.data.model.RentalFormulaType
import com.example.data.model.RentalStrategyType

/**
 * A booking's term and the unit labels of rates: shared by Android, iOS and the web console. `SpaceCalculationUtils` keeps
 * same-signature delegates. The term logic mirrors functions/src/bookings/bookingTerms.ts (shared vectors).
 */
object BookingTerms {
    private val ISO_DAY = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val ISO_MONTH = Regex("\\d{4}-\\d{2}")

    /**
     * A booking's term [start, end) as ISO dates: its calendar dates, else startDate + durationMonths;
     * null when no term was recorded (legacy docs saved a label) — treated as ongoing.
     */
    fun bookingTerm(booking: RentalBookingRequest): Pair<String, String>? {
        val dates = booking.selectedCalendarDates.filter { ISO_DAY.matches(it) }.sorted()
        if (dates.isNotEmpty()) return IsoDate.addDays(dates.last(), 1)?.let { dates.first() to it }
        val raw = booking.startDate.trim().take(10)
        val start = when {
            ISO_DAY.matches(raw) -> raw
            ISO_MONTH.matches(raw) -> "$raw-01"
            else -> return null
        }
        return IsoDate.addMonths(start, booking.durationMonths.coerceAtLeast(1))?.let { start to it }
    }

    /** True once the booking's whole term is over — it no longer locks anything. */
    fun hasEnded(booking: RentalBookingRequest, today: String = IsoDate.todayIso()): Boolean =
        bookingTerm(booking)?.let { it.second <= today } ?: false

    /** Whether two bookings' terms share a day (an unknown term overlaps everything). */
    fun termsOverlap(a: RentalBookingRequest, b: RentalBookingRequest): Boolean {
        val ta = bookingTerm(a) ?: return true
        val tb = bookingTerm(b) ?: return true
        return ta.first < tb.second && tb.first < ta.second
    }

    /** Whether [booking]'s term (its calendar dates, or startDate + durationMonths) covers [isoDate]. */
    fun bookingCoversDate(booking: RentalBookingRequest, isoDate: String): Boolean {
        if (booking.selectedCalendarDates.isNotEmpty()) return isoDate in booking.selectedCalendarDates
        val start = booking.startDate.trim().take(10)
        if (start.length < 7) return true // no term recorded: treat as ongoing
        val startKey = if (start.length == 7) "$start-01" else start
        val end = IsoDate.addMonths(startKey, booking.durationMonths.coerceAtLeast(1)) ?: return true
        return isoDate >= startKey && isoDate < end
    }

    val PER_PERSON_UNIT: String get() = localizedLabel("/person")

    fun rateUnitLabel(type: RentalFormulaType): String = when (type) {
        RentalFormulaType.HOURLY -> localizedLabel("/hr")
        RentalFormulaType.SHIFT -> localizedLabel("/shift")
        RentalFormulaType.DAY_PER_WEEK -> localizedLabel("/day")
        RentalFormulaType.FULL_MONTH -> localizedLabel("/mo")
    }

    fun legacyFormulaType(strategy: RentalStrategyType): RentalFormulaType = when (strategy) {
        RentalStrategyType.MONTHLY -> RentalFormulaType.FULL_MONTH
        RentalStrategyType.HOURLY -> RentalFormulaType.HOURLY
        RentalStrategyType.SHIFT_BASED -> RentalFormulaType.SHIFT
        RentalStrategyType.DAY_BASED -> RentalFormulaType.DAY_PER_WEEK
    }

    fun strategyUnitLabel(strategy: RentalStrategyType): String = rateUnitLabel(legacyFormulaType(strategy))
}
