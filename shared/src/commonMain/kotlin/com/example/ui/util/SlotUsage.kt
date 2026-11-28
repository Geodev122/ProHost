package com.example.ui.util

import com.example.data.model.BookingRequest
import com.example.data.model.BookingRequestStatus
import com.example.data.model.MonthlyConfig
import com.example.data.model.RentalFormulaType
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.ShiftBasedConfig
import com.example.data.model.SpaceListing
import com.example.data.model.SpaceOperatingSchedule
import com.example.data.model.HourlyConfig
import com.example.data.model.publicCode
import com.example.ui.util.PriceChange.SlotKind
import com.example.ui.util.PriceChange.SlotRef

/**
 * Owner rule (28 Nov 2026): a host may switch a slot of a published listing off (hour, shift, day, month, a closed date)
 * only when no booking and no active request uses it. [keepUsedSlots] takes the listing as the host edited it and puts back
 * every slot that a pending or accepted booking whose term hasn't ended still uses; free slots stay off. The server applies the
 * same rule on every save (functions/src/listings/slotUsage.ts, shared vectors tests/vectors/slot-usage.json — keep identical).
 * Which booking uses which slot is [PriceChange.bookingUses], the matcher Change price already shares with the server.
 */
object SlotUsage {

    /** [listing] = the edit with used slots put back; [kept] = what was put back, for the host ("Monday 09:00–10:00 · B-AB12CD"). */
    data class Result(val listing: SpaceListing, val kept: List<String>)

    fun isActive(b: BookingRequest, today: String): Boolean =
        (b.status == BookingRequestStatus.PENDING || b.status == BookingRequestStatus.ACCEPTED) &&
            !b.rejectedBySystem && !BookingTerms.hasEnded(b, today)

    fun keepUsedSlots(
        original: SpaceListing,
        edited: SpaceListing,
        bookings: List<BookingRequest>,
        today: String = IsoDate.todayIso(),
    ): Result {
        val active = bookings.filter { it.spaceId == original.id && isActive(it, today) }
        if (active.isEmpty()) return Result(edited, emptyList())
        val kept = mutableListOf<String>()

        val wholeScope = active.filter { it.subdivisionId.isNullOrBlank() || it.subdivisionId == original.id }
        val (pricing, pricingKept) = keepPricing(original.id, original.id, original.pricing, edited.pricing, wholeScope)
        kept += pricingKept

        val subs = edited.subdivisions.map { sub ->
            val before = original.subdivisions.firstOrNull { it.id == sub.id } ?: return@map sub
            val inRoom = active.filter { it.subdivisionId == sub.id }
            if (inRoom.isEmpty()) return@map sub
            val (p, k) = keepPricing(original.id, sub.id, before.pricing, sub.pricing, inRoom)
            kept += k
            val beforeClosed = before.scheduleOverride?.closedDates.orEmpty()
            val (sched, kc) = keepOpenDates(sub.scheduleOverride, beforeClosed, inRoom)
            kept += kc
            sub.copy(pricing = p, scheduleOverride = sched)
        }

        val (schedule, closedKept) = keepOpenDates(edited.schedule, original.schedule.closedDates, active)
        kept += closedKept

        return Result(edited.copy(pricing = pricing, subdivisions = subs, schedule = schedule ?: edited.schedule), kept)
    }

    /** [new] with every slot of [old] that one of [bookings] used under [old] and no longer can under [new] put back. */
    fun keepPricing(
        spaceId: String,
        scopeId: String,
        old: RentalPricingConfig,
        new: RentalPricingConfig,
        bookings: List<BookingRequest>,
    ): Pair<RentalPricingConfig, List<String>> {
        if (bookings.isEmpty() || old.strategyType != new.strategyType) return new to emptyList()
        var p = new
        val kept = mutableListOf<String>()
        for (slot in PriceChange.slotsOfPricing(scopeId, old)) {
            val lost = bookings.filter {
                PriceChange.bookingUses(it, spaceId, old, slot.ref) && !PriceChange.bookingUses(it, spaceId, p, slot.ref)
            }
            val stillOffered = PriceChange.slotsOfPricing(scopeId, p).any { it.ref == slot.ref }
            val dropped = if (stillOffered) lost else bookings.filter { PriceChange.bookingUses(it, spaceId, old, slot.ref) }
            if (dropped.isEmpty()) continue
            p = restore(p, old, slot.ref)
            kept += "${slot.group} ${slot.label} · ${dropped.first().publicCode}"
        }
        if (old.strategyType == RentalStrategyType.MONTHLY) {
            val newMonthly = p.monthly
            val oldMonthly = old.monthly
            val outside = bookings.filter { it.formula.type == RentalFormulaType.FULL_MONTH }
                .firstOrNull { b -> bookedMonths(b).any { m -> !monthOffered(newMonthly, m) } && bookedMonths(b).all { m -> monthOffered(oldMonthly, m) } }
            if (outside != null && oldMonthly != null && newMonthly != null) {
                p = p.copy(monthly = newMonthly.copy(
                    fromMonth = oldMonthly.fromMonth, fromYear = oldMonthly.fromYear, isIndefinite = oldMonthly.isIndefinite,
                    toMonth = oldMonthly.toMonth, toYear = oldMonthly.toYear, excludedRanges = oldMonthly.excludedRanges,
                ))
                kept += "${localizedLabel("Monthly")} · ${outside.publicCode}"
            }
        }
        return p to kept
    }

    private fun restore(p: RentalPricingConfig, old: RentalPricingConfig, ref: SlotRef): RentalPricingConfig = when (ref.kind) {
        SlotKind.HOURLY -> {
            val price = old.hourly?.cellPrices?.get(ref.key)
            if (price == null) p else p.copy(hourly = HourlyConfig((p.hourly?.cellPrices ?: emptyMap()) + (ref.key to price)))
        }
        SlotKind.SHIFT -> {
            val oldCfg = old.shiftBased
            val oldShift = oldCfg?.shifts?.firstOrNull { it.name.name == ref.key }
            if (oldCfg == null || oldShift == null) p else {
                val cfg = p.shiftBased ?: ShiftBasedConfig(shifts = emptyList())
                val shifts = if (cfg.shifts.any { it.name == oldShift.name }) cfg.shifts.map { if (it.name == oldShift.name) oldShift else it }
                else cfg.shifts + oldShift
                val dist = cfg.distribution.toMutableMap()
                oldCfg.distribution.forEach { (day, names) ->
                    if (ref.key in names) dist[day] = ((dist[day] ?: emptyList()) + ref.key).distinct()
                }
                p.copy(shiftBased = cfg.copy(shifts = shifts, distribution = dist))
            }
        }
        SlotKind.DAY -> {
            val oldDay = old.dayBased?.distribution?.get(ref.key)
            val cfg = p.dayBased ?: old.dayBased?.copy(distribution = emptyMap())
            if (oldDay == null || cfg == null) p else p.copy(dayBased = cfg.copy(distribution = cfg.distribution + (ref.key to oldDay)))
        }
        SlotKind.MONTHLY -> {
            val oldRate = old.monthly?.rateUsd ?: 0.0
            val cfg = p.monthly ?: old.monthly
            if (cfg == null) p else p.copy(monthly = cfg.copy(rateUsd = if (cfg.rateUsd > 0) cfg.rateUsd else oldRate))
        }
        SlotKind.TIER -> p
    }

    /** [schedule] without the closed dates that were added in this edit and that one of [bookings] needs. */
    private fun keepOpenDates(
        schedule: SpaceOperatingSchedule?,
        closedBefore: List<String>,
        bookings: List<BookingRequest>,
    ): Pair<SpaceOperatingSchedule?, List<String>> {
        if (schedule == null || bookings.isEmpty()) return schedule to emptyList()
        val added = schedule.closedDates.filter { it !in closedBefore }
        val reopened = added.mapNotNull { date -> bookings.firstOrNull { needsDate(it, date) }?.let { date to it } }
        if (reopened.isEmpty()) return schedule to emptyList()
        val dates = reopened.map { it.first }.toSet()
        return schedule.copy(closedDates = schedule.closedDates.filter { it !in dates }) to
            reopened.map { (date, b) -> "$date · ${b.publicCode}" }
    }

    /** Whether booking [b] takes place on [date] (yyyy-MM-dd). */
    fun needsDate(b: BookingRequest, date: String): Boolean {
        if (b.bookedSlots.isNotEmpty()) return b.bookedSlots.any { it.date == date }
        if (b.selectedCalendarDates.isNotEmpty()) return date in b.selectedCalendarDates
        val term = BookingTerms.bookingTerm(b) ?: return false
        if (date < term.first || date >= term.second) return false
        if (b.formula.type == RentalFormulaType.FULL_MONTH) return true
        val days = b.selectedDays.ifEmpty { b.formula.daysOfWeek }
        return PriceChange.weekdayOf(date) in days
    }

    private fun monthIndex(month: Int, year: Int) = year * 12 + month - 1

    /** Month indexes (year × 12 + month − 1) of a monthly booking's term. */
    fun bookedMonths(b: BookingRequest): List<Int> {
        val start = IsoDate.parse(b.startDate.take(10)) ?: return emptyList()
        val first = monthIndex(start.monthNumber, start.year)
        return (0 until b.durationMonths.coerceAtLeast(1)).map { first + it }
    }

    /** Whether month index [m] is inside [cfg]'s window and not excluded. */
    fun monthOffered(cfg: MonthlyConfig?, m: Int): Boolean {
        if (cfg == null || cfg.rateUsd <= 0) return false
        if (m < monthIndex(cfg.fromMonth, cfg.fromYear)) return false
        if (!cfg.isIndefinite && cfg.toMonth != null && cfg.toYear != null && m > monthIndex(cfg.toMonth, cfg.toYear)) return false
        return cfg.excludedRanges.none { m >= monthIndex(it.fromMonth, it.fromYear) && m <= monthIndex(it.toMonth, it.toYear) }
    }
}
