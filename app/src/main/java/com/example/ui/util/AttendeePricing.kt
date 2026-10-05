package com.example.ui.util

import com.example.data.model.AttendeePackage
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.Subdivision
import com.example.data.model.SubdivisionPricingMode

/**
 * Per-attendee pricing: the host configures *when* a room is offered with a normal
 * Hourly/Shift/Day strategy, then prices the booking per person. The booking total is
 * attendees × the price of the tier that attendee count falls in, for the whole
 * booking — never multiplied by hours, shifts or days. Monthly can't be per-attendee.
 *
 * Slot "prices" in an attendee room's RentalPricingConfig are availability markers
 * ([AVAILABILITY_MARKER_PRICE]) so the existing slot/availability/conflict code keeps
 * working unchanged; nothing ever charges or displays them.
 *
 * Mirrored server-side in functions/src/lib/attendeePricing.ts — keep both in sync.
 */
object AttendeePricing {
    const val AVAILABILITY_MARKER_PRICE = 1.0

    data class Quote(val tier: AttendeePackage, val attendees: Int, val totalUsd: Double)

    fun isPerAttendee(sub: Subdivision?): Boolean =
        sub != null &&
            sub.pricingMode == SubdivisionPricingMode.PER_ATTENDEE &&
            sub.pricing.strategyType != RentalStrategyType.MONTHLY

    /** The room's own tiers; [fallback] (admin templates) only for rooms saved before tiers existed. */
    fun tiersFor(sub: Subdivision, fallback: List<AttendeePackage> = emptyList()): List<AttendeePackage> {
        fun usable(list: List<AttendeePackage>) = list.filter { it.isEnabled && it.pricePerAttendeeUsd > 0.0 }
        return usable(sub.attendeeTiers).ifEmpty { usable(fallback) }.sortedBy { it.minAttendees }
    }

    fun minAttendees(sub: Subdivision, tiers: List<AttendeePackage>): Int =
        maxOf(sub.minAttendees ?: 1, tiers.minOfOrNull { it.minAttendees } ?: 1, 1)

    /** Null = no upper limit. The room's capacity always caps it. */
    fun maxAttendees(sub: Subdivision, tiers: List<AttendeePackage>): Int? {
        val tierMax = if (tiers.isEmpty() || tiers.any { it.maxAttendees == null }) null else tiers.maxOf { it.maxAttendees ?: 0 }
        return listOfNotNull(sub.capacity, tierMax).minOrNull()
    }

    fun tierFor(count: Int, tiers: List<AttendeePackage>): AttendeePackage? =
        tiers.firstOrNull { count >= it.minAttendees && (it.maxAttendees == null || count <= it.maxAttendees) }

    /** Null when [count] is outside the room's limits or no tier covers it. */
    fun quote(sub: Subdivision, count: Int, fallback: List<AttendeePackage> = emptyList()): Quote? {
        val tiers = tiersFor(sub, fallback)
        if (count < minAttendees(sub, tiers)) return null
        val max = maxAttendees(sub, tiers)
        if (max != null && count > max) return null
        val tier = tierFor(count, tiers) ?: return null
        return Quote(tier, count, count * tier.pricePerAttendeeUsd)
    }

    /** "20 attendees × $8/person (Standard)" */
    fun describe(quote: Quote): String =
        "${quote.attendees} attendee${if (quote.attendees == 1) "" else "s"} × $${formatUsd(quote.tier.pricePerAttendeeUsd)}/person" +
            quote.tier.name.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

    /** Attendee line for a saved booking, or null for a strategy-priced booking. */
    fun bookingSummary(booking: com.example.data.model.BookingRequest): String? {
        if (booking.attendeeCount <= 0) return null
        val perPerson = booking.attendeePackagePriceUsd.takeIf { it > 0.0 }
            ?.let { " × $${formatUsd(it)}/person" }.orEmpty()
        val tier = booking.attendeePackageName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "${booking.attendeeCount} attendee${if (booking.attendeeCount == 1) "" else "s"}$perPerson$tier"
    }

    fun formatUsd(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(java.util.Locale.US, "%.2f", amount)

    fun lowestPricePerPerson(sub: Subdivision, fallback: List<AttendeePackage> = emptyList()): Double? =
        tiersFor(sub, fallback).minOfOrNull { it.pricePerAttendeeUsd }

    /** Editor/ publish check: priced tiers plus at least one offered slot. */
    fun isConfigured(sub: Subdivision): Boolean =
        tiersFor(sub).isNotEmpty() && sub.pricing.hasRealPrice()

    /** Turns offered slots into availability markers when a room switches to per-attendee. */
    fun toAvailabilityMarkers(config: RentalPricingConfig): RentalPricingConfig {
        val m = AVAILABILITY_MARKER_PRICE
        return config.copy(
            hourly = config.hourly?.let { h -> h.copy(cellPrices = h.cellPrices.mapValues { m }) },
            shiftBased = config.shiftBased?.let { s -> s.copy(shifts = s.shifts.map { it.copy(price = m) }) },
            dayBased = config.dayBased?.let { d ->
                d.copy(distribution = d.distribution.mapValues { (_, p) -> if (p.price > 0.0) p.copy(price = m) else p })
            }
        )
    }

    /**
     * Shift-Based per-attendee rooms decide offered shifts by the day distribution (there is
     * no shift price field), so every shift carries the marker. Applied whenever a per-attendee
     * room is built, so rooms saved before this fix (shifts at 0.0) become valid too.
     */
    fun markShifts(config: RentalPricingConfig): RentalPricingConfig = config.copy(
        shiftBased = config.shiftBased?.let { s -> s.copy(shifts = s.shifts.map { it.copy(price = AVAILABILITY_MARKER_PRICE) }) }
    )

    /** Clears markers when a room goes back to strategy pricing, so the host must enter real prices. */
    fun clearAvailabilityMarkers(config: RentalPricingConfig): RentalPricingConfig = config.copy(
        hourly = config.hourly?.let { h -> h.copy(cellPrices = h.cellPrices.mapValues { 0.0 }) },
        shiftBased = config.shiftBased?.let { s -> s.copy(shifts = s.shifts.map { it.copy(price = 0.0) }) },
        dayBased = config.dayBased?.let { d -> d.copy(distribution = d.distribution.mapValues { (_, p) -> p.copy(price = 0.0) }) }
    )
}
