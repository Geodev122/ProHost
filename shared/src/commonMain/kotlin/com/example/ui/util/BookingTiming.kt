package com.example.ui.util

import com.example.data.model.BookedSlot
import com.example.data.model.BookingRequest
import com.example.data.model.RentalFormulaType
import kotlinx.datetime.daysUntil

/**
 * How a booking's timing reads on screen, from its own formula (owner, 22 Nov 2026). An hourly, shift or day booking IS its
 * booked times: it lists each one, its duration is the hours / shifts / days booked and it starts at the first of them; only a
 * monthly booking has a term in months. ([BookingRequest.durationMonths] defaults to 1 for every booking, which is why
 * hour and shift requests used to read "1 month".)
 */
object BookingTiming {

    /** A dated booking's times as (date, startHour, endHour, shiftName) rows, contiguous hours of one date merged. */
    private data class Row(val date: String, val start: Int, val end: Int, val shift: String?)

    private fun rows(slots: List<BookedSlot>): List<Row> {
        val out = ArrayList<Row>()
        for (s in slots.sortedWith(compareBy({ it.date }, { it.startHour }))) {
            val last = out.lastOrNull()
            if (last != null && last.date == s.date && last.end == s.startHour && last.shift == null && s.shiftName == null) {
                out[out.size - 1] = last.copy(end = s.endHour)
            } else {
                out.add(Row(s.date, s.startHour, s.endHour, s.shiftName))
            }
        }
        return out
    }

    private fun hh(hour: Int) = hour.toString().padStart(2, '0') + ":00"

    private fun dateLabel(iso: String): String = IsoDate.parse(iso)?.let { DateLabels.dayLabel(it) } ?: iso

    private val BookingRequest.isMonthly get() = formula.type == RentalFormulaType.FULL_MONTH

    private val BookingRequest.isDated get() = dateModel == 2 && bookedSlots.isNotEmpty()

    /** One line per booked time, in date order ("Wed 3 Dec · 09:00 – 11:00", "Thu 4 Dec · Morning (08:00 – 12:00)"). */
    fun lines(b: BookingRequest): List<String> {
        if (b.isDated) {
            return rows(b.bookedSlots).map { r ->
                val time = timeRange(hh(r.start), hh(r.end), " – ")
                val label = when {
                    b.formula.type == RentalFormulaType.DAY_PER_WEEK -> sharedText("whole_day_x_to_x", "\u2066${hh(r.start)}\u2069", "\u2066${hh(r.end)}\u2069")
                    r.shift != null -> "${localizedLabel(r.shift)} ($time)"
                    else -> time
                }
                "${dateLabel(r.date)} · $label"
            }
        }
        if (b.isMonthly && IsoDate.parse(b.startDate) != null) {
            return listOf(DateSlots.describeMonthly(b.startDate, b.durationMonths).let { "⁦$it⁩" })
        }
        // Dated requests saved before bookedSlots existed, and weekly requests from before dated booking.
        if (b.selectedDateTimeRange.isNotBlank() && b.dateModel == 2) {
            return b.selectedDateTimeRange.split("; ").filter { it.isNotBlank() }.map { "⁦$it⁩" }
        }
        val days = b.selectedDays.ifEmpty { b.formula.daysOfWeek }.joinToString(", ") { localizedLabel(it) }
        val hours = if (b.selectedStartHour.isNotBlank()) timeRange(b.selectedStartHour, b.selectedEndHour)
        else timeRange(b.formula.startHour, b.formula.endHour)
        val shift = if (b.selectedShift.isNotBlank()) " · ${localizedLabel(b.selectedShift)}" else ""
        return listOf(listOf(days, hours).filter { it.isNotBlank() }.joinToString(" @ ") + shift)
    }

    /** The booking's length in its own unit: months for monthly, else the hours, shifts or days booked. */
    fun duration(b: BookingRequest): String {
        if (b.isMonthly) return sharedPlural("n_months", b.durationMonths.coerceAtLeast(1), b.durationMonths.coerceAtLeast(1))
        if (b.isDated) {
            val slots = b.bookedSlots
            return when (b.formula.type) {
                RentalFormulaType.SHIFT -> sharedPlural("n_shifts", slots.size, slots.size)
                RentalFormulaType.DAY_PER_WEEK -> slots.map { it.date }.distinct().size.let { sharedPlural("n_days", it, it) }
                else -> slots.sumOf { (it.endHour - it.startHour).coerceAtLeast(0) }.let { sharedPlural("n_hours", it, it) }
            }
        }
        // Weekly (pre-dated) requests: the length of one visit.
        return when (b.formula.type) {
            RentalFormulaType.SHIFT -> sharedPlural("n_shifts", 1, 1)
            RentalFormulaType.DAY_PER_WEEK -> sharedPlural("n_days", 1, 1)
            else -> hoursBetween(b.selectedStartHour.ifBlank { b.formula.startHour }, b.selectedEndHour.ifBlank { b.formula.endHour })
                ?.let { sharedPlural("n_hours", it, it) } ?: localizedLabel(b.formula.type.displayName)
        }
    }

    /** When it starts: the first booked time for hourly/shift/day bookings, the start date for monthly ones. */
    fun start(b: BookingRequest): String {
        if (b.isDated) {
            val first = rows(b.bookedSlots).first()
            return if (b.formula.type == RentalFormulaType.DAY_PER_WEEK) dateLabel(first.date)
            else "${dateLabel(first.date)} · ⁦${hh(first.start)}⁩"
        }
        return if (IsoDate.parse(b.startDate) != null) dateLabel(b.startDate) else b.startDate
    }

    /** How far today is through the booking's term (its booked dates, or start + months), in whole days. */
    data class Progress(val fraction: Float, val elapsedDays: Int, val remainingDays: Int)

    fun progress(b: BookingRequest, today: kotlinx.datetime.LocalDate = IsoDate.todayDate()): Progress {
        val term = BookingTerms.bookingTerm(b)
        val start = term?.first?.let { IsoDate.parse(it) }
        val end = term?.second?.let { IsoDate.parse(it) }
        if (start == null || end == null) return Progress(0f, 0, 0)
        val total = start.daysUntil(end).coerceAtLeast(1)
        val elapsed = start.daysUntil(today).coerceIn(0, total)
        return Progress(elapsed.toFloat() / total, elapsed, total - elapsed)
    }

    private fun hoursBetween(from: String, to: String): Int? {
        val a = from.substringBefore(':').trim().toIntOrNull() ?: return null
        val z = to.substringBefore(':').trim().toIntOrNull() ?: return null
        return (z - a).takeIf { it > 0 }
    }
}
