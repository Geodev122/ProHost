package com.example.ui.util

import com.example.data.model.BookingRequest
import com.example.data.model.PriceChangeMode
import com.example.data.model.RentalFormulaType
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.SpaceListing
import com.example.data.model.Subdivision
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

/**
 * Pro Host "Change price": slot identity, the slots a room offers, and how a price change
 * re-prices a booking. Mirrors functions/src/bookings/priceChange.ts — keep both identical
 * (PriceChangeTest.kt / priceChange.test.ts share their cases). The server
 * (changeSlotPrice callable) is the authority; the app uses this for the step-3 slot list and
 * the old → new total preview.
 */
object PriceChange {

    enum class SlotKind { MONTHLY, HOURLY, SHIFT, DAY, TIER }

    /** [scopeId] = the room id, or the space id when the space has no rooms. */
    data class SlotRef(val scopeId: String, val kind: SlotKind, val key: String)

    data class PriceSlot(
        val ref: SlotRef,
        /** Section the slot is listed under ("Monday", "Shifts", "Monthly", "Per person"). */
        val group: String,
        val label: String,
        val price: Double,
        val unit: String
    )

    /** A division of a space as Change price sees it: the space itself, or one room. */
    data class Division(val scopeId: String, val name: String, val typeLabel: String, val subdivision: Subdivision?)

    data class Reprice(val newTotal: Double, val effectiveFrom: String, val units: Int)

    private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val DAY_ORDER = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val DAY_NAMES = mapOf(
        "Mon" to "Monday", "Tue" to "Tuesday", "Wed" to "Wednesday", "Thu" to "Thursday",
        "Fri" to "Friday", "Sat" to "Saturday", "Sun" to "Sunday"
    )

    fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun utcCalendar(iso: String): Calendar? = runCatching {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = isoFormat().parse(iso.take(10))!! }
    }.getOrNull()

    fun todayIso(): String = isoFormat().format(java.util.Date())

    fun weekdayOf(iso: String): String =
        utcCalendar(iso)?.let { WEEKDAYS[it.get(Calendar.DAY_OF_WEEK) - 1] } ?: ""

    private fun parseHour(value: String): Int? = value.substringBefore(":").trim().toIntOrNull()

    private fun pad(h: Int) = "%02d:00".format(Locale.US, h)

    /** The space's divisions: one per room, or the whole space when it has none. */
    fun divisionsOf(space: SpaceListing): List<Division> =
        if (space.subdivisions.isEmpty()) {
            listOf(Division(space.id, "Whole space", space.pricing.strategyType.displayName, null))
        } else {
            space.subdivisions.map { Division(it.id, it.name, it.type.displayName, it) }
        }

    /** Every priced slot of a division (per-attendee rooms: their per-person packages). */
    fun slotsOf(division: Division, space: SpaceListing): List<PriceSlot> {
        val sub = division.subdivision
        if (sub != null && AttendeePricing.isPerAttendee(sub)) {
            return sub.attendeeTiers.filter { it.isEnabled && it.pricePerAttendeeUsd > 0 }.map {
                PriceSlot(SlotRef(division.scopeId, SlotKind.TIER, it.id), "Per person", it.name, it.pricePerAttendeeUsd, SpaceCalculationUtils.PER_PERSON_UNIT)
            }
        }
        return slotsOfPricing(division.scopeId, sub?.pricing ?: space.pricing)
    }

    fun slotsOfPricing(scopeId: String, pricing: RentalPricingConfig): List<PriceSlot> {
        val unit = SpaceCalculationUtils.strategyUnitLabel(pricing.strategyType)
        return when (pricing.strategyType) {
            RentalStrategyType.MONTHLY -> pricing.monthly?.takeIf { it.rateUsd > 0 }?.let {
                listOf(PriceSlot(SlotRef(scopeId, SlotKind.MONTHLY, ""), "Monthly", "Monthly rent", it.rateUsd, unit))
            } ?: emptyList()
            RentalStrategyType.HOURLY -> (pricing.hourly?.cellPrices ?: emptyMap())
                .filter { it.value > 0 }
                .mapNotNull { (key, price) ->
                    val day = key.substringBefore("|")
                    val hour = key.substringAfter("|").toIntOrNull() ?: return@mapNotNull null
                    Triple(day, hour, PriceSlot(SlotRef(scopeId, SlotKind.HOURLY, key), DAY_NAMES[day] ?: day, "${pad(hour)}–${pad(hour + 1)}", price, unit))
                }
                .sortedWith(compareBy<Triple<String, Int, PriceSlot>>({ dayRank(it.first) }, { it.second }))
                .map { it.third }
            RentalStrategyType.SHIFT_BASED -> {
                val cfg = pricing.shiftBased ?: return emptyList()
                cfg.shifts.filter { s -> !s.isUnavailable && s.price > 0 && cfg.distribution.values.any { s.name.name in it } }.map { s ->
                    val days = DAY_ORDER.filter { d -> cfg.distribution[d]?.contains(s.name.name) == true }
                    PriceSlot(
                        SlotRef(scopeId, SlotKind.SHIFT, s.name.name), "Shifts",
                        "${shiftLabel(s.name.name)} (${pad(s.startHour)}–${pad(s.endHour)}) · ${days.joinToString(", ")}",
                        s.price, unit
                    )
                }
            }
            RentalStrategyType.DAY_BASED -> (pricing.dayBased?.distribution ?: emptyMap())
                .filter { it.value.price > 0 }
                .toList()
                .sortedBy { dayRank(it.first) }
                .map { (day, p) -> PriceSlot(SlotRef(scopeId, SlotKind.DAY, day), "Days", DAY_NAMES[day] ?: day, p.price, unit) }
        }
    }

    private fun dayRank(day: String): Int = DAY_ORDER.indexOf(day).let { if (it < 0) 99 else it }

    fun shiftLabel(name: String): String = name.lowercase().replaceFirstChar { it.uppercase() } + " shift"

    /** Days a slot is offered on (null = not day-bound). */
    fun slotDays(pricing: RentalPricingConfig?, ref: SlotRef): List<String>? = when (ref.kind) {
        SlotKind.HOURLY -> listOf(ref.key.substringBefore("|"))
        SlotKind.DAY -> listOf(ref.key)
        SlotKind.SHIFT -> pricing?.shiftBased?.distribution?.filter { ref.key in it.value }?.keys?.toList() ?: emptyList()
        else -> null
    }

    private fun bookingDays(b: BookingRequest): List<String> = b.selectedDays.ifEmpty { b.formula.daysOfWeek }

    /** Whether [b] is charged for slot [ref] — same scope rule as SpaceCalculationUtils.isSlotLocked. */
    fun bookingUses(b: BookingRequest, spaceId: String, pricing: RentalPricingConfig?, ref: SlotRef): Boolean {
        if (b.spaceId != spaceId) return false
        if ((b.subdivisionId?.takeIf { it.isNotBlank() } ?: spaceId) != ref.scopeId) return false
        val type = b.formula.type
        val fs = parseHour(b.formula.startHour)
        val fe = parseHour(b.formula.endHour)
        return when (ref.kind) {
            SlotKind.MONTHLY -> type == RentalFormulaType.FULL_MONTH
            SlotKind.TIER -> !b.selectedAttendeePackageId.isNullOrBlank() && b.selectedAttendeePackageId == ref.key
            SlotKind.HOURLY -> {
                if (type != RentalFormulaType.HOURLY || fs == null || fe == null) return false
                val day = ref.key.substringBefore("|")
                val hour = ref.key.substringAfter("|").toIntOrNull() ?: return false
                day in bookingDays(b) && hour >= fs && hour < fe
            }
            SlotKind.SHIFT -> {
                if (type != RentalFormulaType.SHIFT || fs == null || fe == null) return false
                val shift = pricing?.shiftBased?.shifts?.firstOrNull { it.name.name == ref.key } ?: return false
                if (!(shift.startHour >= fs && shift.endHour <= fe)) return false
                val days = slotDays(pricing, ref).orEmpty()
                if (b.selectedCalendarDates.isNotEmpty()) b.selectedCalendarDates.any { weekdayOf(it) in days }
                else bookingDays(b).any { it in days }
            }
            SlotKind.DAY -> {
                if (type != RentalFormulaType.DAY_PER_WEEK) return false
                if (b.selectedCalendarDates.isNotEmpty()) b.selectedCalendarDates.any { weekdayOf(it) == ref.key }
                else ref.key in bookingDays(b)
            }
        }
    }

    private fun monthIndex(iso: String): Int = iso.take(4).toInt() * 12 + iso.substring(5, 7).toInt() - 1

    /** Units of [ref] charged on/after [fromIso]; null = undated (hourly cells, tiers). */
    fun unitsFrom(b: BookingRequest, pricing: RentalPricingConfig?, ref: SlotRef, fromIso: String): Int? = when (ref.kind) {
        SlotKind.MONTHLY -> {
            val months = b.durationMonths.coerceAtLeast(1)
            val term = SpaceCalculationUtils.bookingTerm(b)
            if (term == null) months else {
                val startM = monthIndex(term.first)
                val fromM = maxOf(startM, monthIndex(fromIso))
                (startM + months - fromM).coerceAtLeast(0)
            }
        }
        SlotKind.SHIFT, SlotKind.DAY -> {
            val days = slotDays(pricing, ref).orEmpty()
            if (b.selectedCalendarDates.isEmpty()) null
            else b.selectedCalendarDates.count { it >= fromIso && weekdayOf(it) in days }
        }
        SlotKind.HOURLY, SlotKind.TIER -> null
    }

    /** First day of the next billing period: the 1st of next month (monthly) or next Monday. */
    fun nextTermStart(ref: SlotRef, todayIso: String): String {
        val cal = utcCalendar(todayIso) ?: return todayIso
        if (ref.kind == SlotKind.MONTHLY) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, 1)
        } else {
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sun
            val untilMonday = ((8 - dow) % 7).let { if (it == 0) 7 else it }
            cal.add(Calendar.DAY_OF_MONTH, untilMonday)
        }
        return isoFormat().format(cal.time)
    }

    /** The new total for [b] when [ref] goes from [oldPrice] to [newPrice] under [mode]. */
    fun reprice(
        b: BookingRequest, pricing: RentalPricingConfig?, ref: SlotRef,
        oldPrice: Double, newPrice: Double, mode: PriceChangeMode, todayIso: String = todayIso()
    ): Reprice {
        val total = b.totalAmountUsd
        if (mode == PriceChangeMode.KEEP) return Reprice(round2(total), "", 0)
        val term = SpaceCalculationUtils.bookingTerm(b)
        var from = if (mode == PriceChangeMode.NOW) todayIso else nextTermStart(ref, todayIso)
        if (term != null && term.first > from) from = term.first
        val units = unitsFrom(b, pricing, ref, from) ?: when {
            mode != PriceChangeMode.NOW -> 0
            ref.kind == SlotKind.TIER -> b.attendeeCount.coerceAtLeast(0)
            else -> 1 // an hourly booking pays each booked cell once
        }
        if (units == 0) return Reprice(round2(total), "", 0)
        return Reprice(round2(maxOf(0.0, total + (newPrice - oldPrice) * units)), from, units)
    }

    /** Pricing a booking's slot lives in (the room's, or the space's when it has no rooms). */
    fun pricingFor(space: SpaceListing, scopeId: String): RentalPricingConfig? =
        if (scopeId == space.id && space.subdivisions.isEmpty()) space.pricing
        else space.subdivisions.firstOrNull { it.id == scopeId }?.pricing

    /** The division a booking belongs to, or null when its room was removed. */
    fun divisionOf(space: SpaceListing, booking: BookingRequest): Division? =
        divisionsOf(space).firstOrNull { it.scopeId == (booking.subdivisionId?.takeIf { s -> s.isNotBlank() } ?: space.id) }

    /** The slots of [division] that [booking] is charged for. */
    fun slotsForBooking(space: SpaceListing, division: Division, booking: BookingRequest): List<PriceSlot> {
        val pricing = pricingFor(space, division.scopeId)
        return slotsOf(division, space).filter { bookingUses(booking, space.id, pricing, it.ref) }
    }
}
