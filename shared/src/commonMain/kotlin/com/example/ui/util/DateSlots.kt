package com.example.ui.util

import com.example.data.model.BookedSlot
import com.example.data.model.BookingRequest
import com.example.data.model.DEFAULT_BOOKING_HORIZON_MONTHS
import com.example.data.model.MonthlyConfig
import com.example.data.model.RentalFormulaType
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.SpaceOperatingSchedule
import kotlin.math.abs

/**
 * Booking on real dates (owner decision 14 Nov 2026). The host still describes a normal WEEK (weekday → hours / shifts / day
 * price); this projects that week onto a calendar DATE and says which slots are offered, taken or unavailable there, and whether
 * a monthly term can start on a date.
 *
 * Mirror of functions/src/bookings/dateSlots.ts: both run tests/vectors/date-slots.json, keep them identical (messages included).
 * Pure: no clock (callers pass `today`), no platform types.
 *
 * Slot keys: hourly = the start hour ("9"); shift = the shift name ("MORNING"); day = "DAY". A booked slot occupies
 * [startHour, endHour) on its date; two bookings clash when the ranges intersect on one date.
 */
object DateSlots {
    const val MAX_MONTHLY_MONTHS = 12
    const val MAX_DATES_PER_REQUEST = 31
    private val ISO_DAY = Regex("\\d{4}-\\d{2}-\\d{2}")

    data class Slot(val key: String, val startHour: Int, val endHour: Int, val shiftName: String?, val priceUsd: Double, val taken: Boolean = false)
    enum class DayState { PAST, BEYOND, CLOSED, NOT_OFFERED, OPEN }
    data class DayView(val state: DayState, val slots: List<Slot> = emptyList()) {
        /** Open, and at least one slot is still free. */
        val hasFree: Boolean get() = state == DayState.OPEN && slots.any { !it.taken }
        /** Open, but every slot is taken. */
        val isFull: Boolean get() = state == DayState.OPEN && slots.none { !it.taken }
    }

    /** "Mon" … "Sun" for an ISO date (the keys the host's weekly pattern uses); "" when the date is malformed. */
    fun weekdayOf(iso: String): String = IsoDate.parse(iso)?.let(IsoDate::dayAbbreviation).orEmpty()

    private fun parseHour(value: String): Int? = value.substringBefore(':').trim().toIntOrNull()?.takeIf { it in 0..24 }

    private fun operatingDays(schedule: SpaceOperatingSchedule): List<String> =
        if (schedule.isSundayOperating && schedule.operatingDays.none { it.equals("Sun", ignoreCase = true) }) schedule.operatingDays + "Sun"
        else schedule.operatingDays

    /** The slots the host offers on [date] (ignores bookings, closed dates and the window). Monthly rooms offer none here. */
    fun offeredSlots(pricing: RentalPricingConfig, schedule: SpaceOperatingSchedule, date: String): List<Slot> {
        val day = weekdayOf(date)
        if (day.isEmpty() || day !in operatingDays(schedule)) return emptyList()
        val open = parseHour(schedule.openingHour) ?: 0
        val close = parseHour(schedule.closingHour) ?: 24
        return when (pricing.strategyType) {
            RentalStrategyType.HOURLY -> {
                val cells = pricing.hourly?.cellPrices ?: return emptyList()
                (open until close).mapNotNull { h ->
                    cells["$day|$h"]?.takeIf { it > 0.0 }?.let { Slot(h.toString(), h, h + 1, null, it) }
                }
            }
            RentalStrategyType.SHIFT_BASED -> {
                val sb = pricing.shiftBased ?: return emptyList()
                val byName = sb.shifts.associateBy { it.name.name }
                (sb.distribution[day] ?: emptyList()).mapNotNull { name ->
                    val shift = byName[name] ?: return@mapNotNull null
                    if (shift.isUnavailable || shift.price <= 0.0) return@mapNotNull null
                    Slot(name, shift.startHour, shift.endHour, name, shift.price)
                }
            }
            RentalStrategyType.DAY_BASED -> {
                val db = pricing.dayBased ?: return emptyList()
                val price = db.distribution[day]?.price?.takeIf { it > 0.0 } ?: return emptyList()
                val start = if (db.useFacilityHours) open else db.customStartHour ?: open
                val end = if (db.useFacilityHours) close else db.customEndHour ?: close
                listOf(Slot("DAY", start, end, null, price))
            }
            RentalStrategyType.MONTHLY -> emptyList()
        }
    }

    /** The term [start, end) of a booking as ISO dates, or null when unknown (legacy label start dates): unknown = ongoing. */
    private fun termOf(booking: BookingRequest): Pair<String, String>? {
        val dates = booking.selectedCalendarDates.filter { ISO_DAY.matches(it) }.sorted()
        if (dates.isNotEmpty()) return IsoDate.addDays(dates.last(), 1)?.let { dates.first() to it }
        val raw = booking.startDate.trim().take(10)
        val start = when {
            ISO_DAY.matches(raw) -> raw
            Regex("\\d{4}-\\d{2}").matches(raw) -> "$raw-01"
            else -> return null
        }
        return IsoDate.addMonths(start, booking.durationMonths.coerceAtLeast(1))?.let { start to it }
    }

    /**
     * The hour ranges [start, end) a booking occupies on [date]; empty when it does not touch that date: dated slots (new bookings)
     * exactly; a monthly booking the whole day for every date of its term; older bookings their hour span on their explicit dates,
     * or on their weekdays inside their term.
     */
    fun occupiedOn(booking: BookingRequest, date: String): List<Pair<Int, Int>> {
        if (booking.bookedSlots.isNotEmpty()) return booking.bookedSlots.filter { it.date == date }.map { it.startHour to it.endHour }
        val term = termOf(booking)
        val inTerm = term == null || (term.first <= date && date < term.second)
        if (booking.formula.type == RentalFormulaType.FULL_MONTH) return if (inTerm) listOf(0 to 24) else emptyList()
        val start = parseHour(booking.selectedStartHour) ?: parseHour(booking.formula.startHour) ?: 0
        var end = parseHour(booking.selectedEndHour) ?: parseHour(booking.formula.endHour) ?: 24
        if (end <= start) end = 24
        val dates = booking.selectedCalendarDates.filter { ISO_DAY.matches(it) }
        if (dates.isNotEmpty()) return if (date in dates) listOf(start to end) else emptyList()
        if (!inTerm) return emptyList()
        val days = (booking.selectedDays + booking.formula.daysOfWeek).map { it.take(3).lowercase() }
        return if (days.isEmpty() || weekdayOf(date).lowercase() in days) listOf(start to end) else emptyList()
    }

    /** Last bookable date for an hourly / shift / day room. */
    fun horizonEnd(today: String, pricing: RentalPricingConfig): String {
        val months = pricing.bookingHorizonMonths?.takeIf { it in 1..24 } ?: DEFAULT_BOOKING_HORIZON_MONTHS
        return IsoDate.addMonths(today, months) ?: today
    }

    /** What a calendar date offers for an hourly / shift / day room. [closedDates] = the listing's and the room's, together. */
    fun dayView(
        date: String, today: String, pricing: RentalPricingConfig, schedule: SpaceOperatingSchedule,
        closedDates: Collection<String> = emptyList(), bookings: List<BookingRequest> = emptyList(),
    ): DayView {
        if (!ISO_DAY.matches(date) || date < today) return DayView(DayState.PAST)
        if (date > horizonEnd(today, pricing)) return DayView(DayState.BEYOND)
        if (date in closedDates) return DayView(DayState.CLOSED)
        val offered = offeredSlots(pricing, schedule, date)
        if (offered.isEmpty()) return DayView(DayState.NOT_OFFERED)
        val busy = bookings.flatMap { occupiedOn(it, date) }
        return DayView(DayState.OPEN, offered.map { s -> s.copy(taken = busy.any { it.first < s.endHour && s.startHour < it.second }) })
    }

    /** Why a request's dated slots can't be sent, or null. [checkPrices] is false for per-attendee rooms (slot prices are markers). */
    fun bookedSlotsProblem(
        slots: List<BookedSlot>, today: String, pricing: RentalPricingConfig, schedule: SpaceOperatingSchedule,
        closedDates: Collection<String> = emptyList(), bookings: List<BookingRequest> = emptyList(), checkPrices: Boolean = true,
    ): String? {
        if (slots.isEmpty()) return "Choose at least one date."
        if (slots.map { it.date }.toSet().size > MAX_DATES_PER_REQUEST) return "Choose at most $MAX_DATES_PER_REQUEST dates in one request."
        val seen = HashSet<String>()
        for (s in slots) {
            if (!seen.add("${s.date}|${s.key}")) return "The same slot was chosen twice."
            val view = dayView(s.date, today, pricing, schedule, closedDates, bookings)
            when (view.state) {
                DayState.PAST -> return "${s.date} has already passed."
                DayState.BEYOND -> return "${s.date} is too far ahead for this room."
                DayState.CLOSED -> return "The host is closed on ${s.date}."
                else -> Unit
            }
            val match = view.slots.firstOrNull { it.key == s.key && it.startHour == s.startHour && it.endHour == s.endHour }
                ?: return "That time is no longer offered on ${s.date}."
            if (match.taken) return "That time on ${s.date} was just booked by someone else."
            if (checkPrices && abs(match.priceUsd - s.priceUsd) > 0.01) return "The price changed. Please send a new request."
        }
        return null
    }

    private fun hh(hour: Int) = hour.toString().padStart(2, '0') + ":00"

    /** Consecutive slots of one date merged into ranges: [(date, startHour, endHour)], in date and time order. */
    fun ranges(slots: List<BookedSlot>): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        for (s in slots.sortedWith(compareBy({ it.date }, { it.startHour }))) {
            val last = out.lastOrNull()
            if (last != null && last.first == s.date && last.third == s.startHour) out[out.size - 1] = Triple(last.first, last.second, s.endHour)
            else out.add(Triple(s.date, s.startHour, s.endHour))
        }
        return out
    }

    /** Stored, language-neutral summary of a dated booking: "2026-11-17 10:00-12:00; 2026-11-19 15:00-16:00". Screens print their own localized form. */
    fun describe(slots: List<BookedSlot>): String = ranges(slots).joinToString("; ") { (date, start, end) -> "$date ${hh(start)}-${hh(end)}" }

    private fun monthIndex(year: Int, month: Int) = year * 12 + (month - 1)

    /** Whether the host rents the room in the calendar month [year]-[month] (inside the from/to window and not excluded). */
    fun monthOffered(config: MonthlyConfig, year: Int, month: Int): Boolean {
        val at = monthIndex(year, month)
        if (at < monthIndex(config.fromYear, config.fromMonth)) return false
        if (!config.isIndefinite) {
            val toYear = config.toYear ?: return false
            val toMonth = config.toMonth ?: return false
            if (at > monthIndex(toYear, toMonth)) return false
        }
        return config.excludedRanges.none { monthIndex(it.fromYear, it.fromMonth) <= at && at <= monthIndex(it.toYear, it.toMonth) }
    }

    /** The term [start, end) of a monthly booking: whole months from any start date (end clamped to the month's last day). */
    fun monthlyTerm(start: String, months: Int): Pair<String, String> = start to (IsoDate.addMonths(start, months.coerceAtLeast(1)) ?: start)

    /** Why a monthly booking can't start on [start] for [months] months, or null. */
    fun monthlyProblem(start: String, months: Int, today: String, pricing: RentalPricingConfig, bookings: List<BookingRequest> = emptyList()): String? {
        if (!ISO_DAY.matches(start) || IsoDate.parse(start) == null || start < today) return "Choose a start date from today onward."
        if (months < 1 || months > MAX_MONTHLY_MONTHS) return "Choose between 1 and $MAX_MONTHLY_MONTHS months."
        val config = pricing.monthly?.takeIf { pricing.strategyType == RentalStrategyType.MONTHLY } ?: return "This room isn't rented by the month."
        val term = monthlyTerm(start, months)
        val last = IsoDate.addDays(term.second, -1) ?: return "Choose a start date from today onward."
        val from = monthIndex(start.substring(0, 4).toInt(), start.substring(5, 7).toInt())
        val to = monthIndex(last.substring(0, 4).toInt(), last.substring(5, 7).toInt())
        for (i in from..to) if (!monthOffered(config, i / 12, i % 12 + 1)) return "The host doesn't rent this room for part of that period."
        for (b in bookings) {
            val booked = "Part of that period is already booked."
            if (b.bookedSlots.isNotEmpty()) {
                if (b.bookedSlots.any { term.first <= it.date && it.date < term.second }) return booked
                continue
            }
            val dates = b.selectedCalendarDates.filter { ISO_DAY.matches(it) }
            if (dates.isNotEmpty()) {
                if (dates.any { term.first <= it && it < term.second }) return booked
                continue
            }
            val t = termOf(b)
            if (t == null || (t.first < term.second && term.first < t.second)) return booked
        }
        return null
    }

    /** How many months (0..12) a monthly booking starting on [start] can run. */
    fun maxMonthlyMonths(start: String, today: String, pricing: RentalPricingConfig, bookings: List<BookingRequest> = emptyList()): Int {
        var ok = 0
        for (n in 1..MAX_MONTHLY_MONTHS) {
            if (monthlyProblem(start, n, today, pricing, bookings) != null) break
            ok = n
        }
        return ok
    }
}
