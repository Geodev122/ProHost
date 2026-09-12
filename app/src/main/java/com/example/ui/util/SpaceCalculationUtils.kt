package com.example.ui.util

import com.example.data.model.*

/**
 * One rentable unit of time produced by a rental pricing strategy. Shared source of
 * truth between the host's Availability Control editor (SpaceScheduleEditorDialog's
 * on/off toggles) and the Specialist-facing availability view
 * (SpaceAvailabilityMatrixView) — both render the exact same derived slots
 * ([SpaceCalculationUtils.buildAllSlotsForSpace]) instead of two different models
 * that could silently disagree (the specialist view used to be a fixed
 * Morning/Afternoon/Evening grid unrelated to what the host actually configured;
 * later, both screens read only [SpaceListing.rentalFormulas] directly, which meant
 * neither ever saw a subdivided listing's real availability at all).
 */
/** One rental-recurrence a [RentableSlot] can be priced under. FLAT is the only
 *  recurrence Monthly/Hourly strategies ever populate (there's only ever one price);
 *  Shift-Based and Day-Based strategies can populate up to all three of the others,
 *  since the new pricing model prices each recurrence scenario independently rather
 *  than deriving one from another by proration. */
enum class BookingRecurrence { FLAT, ONE_TIME, SAME_DAY_EVERY_WEEK, SAME_DAY_EVERY_MONTH }

data class RentableSlot(
    val groupLabel: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val label: String,
    // Which space/subdivision produced this slot (space.id, or a subdivision's own
    // id) — lets a caller (e.g. a Specialist tapping an open slot) resolve the real
    // source directly instead of guessing/synthesizing one from the slot alone.
    val sourceFormulaId: String,
    // Real, independently-set prices per recurrence, from buildBookableSlots().
    val pricesByRecurrence: Map<BookingRecurrence, Double> = emptyMap(),
    val strategyType: RentalStrategyType? = null
)

/**
 * Pure helper functions for pricing calculations, slot validations, and WhatsApp URL generation.
 * Separated from View layer to adhere strictly to MVVM standards.
 */
object SpaceCalculationUtils {

    private fun parseHour(value: String): Int? =
        value.substringBefore(':').trim().toIntOrNull()?.takeIf { it in 0..24 }

    private fun hourLabel(hour: Int): String = "%02d:00".format(hour)

    private fun effectiveOperatingDays(schedule: SpaceOperatingSchedule): List<String> {
        val days = schedule.operatingDays.toMutableList()
        if (schedule.isSundayOperating && days.none { it.equals("Sun", ignoreCase = true) }) {
            days.add("Sun")
        }
        return days
    }

    /**
     * Expands one [RentalPricingConfig] (a whole space's own pricing, or a single
     * subdivision's) into the individual bookable slots it produces — the single
     * expansion function every screen that needs real availability calls: the host's
     * Availability Control editor, the specialist's availability matrix, AND (from
     * Phase 3 onward) the booking dialog itself, so all three are guaranteed to agree
     * by construction. [sourceId]/[sourceLabel] identify which space/subdivision a
     * slot came from — pass the space's own id/title when there are no subdivisions,
     * or each subdivision's id/name when there are.
     */
    fun buildBookableSlots(
        config: RentalPricingConfig,
        schedule: SpaceOperatingSchedule,
        sourceId: String,
        sourceLabel: String,
        referenceMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
        referenceYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    ): List<RentableSlot> {
        val operatingDays = effectiveOperatingDays(schedule)
        if (operatingDays.isEmpty()) return emptyList()
        val openHour = parseHour(schedule.openingHour) ?: 0
        val closeHour = parseHour(schedule.closingHour) ?: 24

        return when (config.strategyType) {
            RentalStrategyType.MONTHLY -> {
                val m = config.monthly ?: return emptyList()
                fun beforeOrEqual(m1: Int, y1: Int, m2: Int, y2: Int) = y1 < y2 || (y1 == y2 && m1 <= m2)
                val afterFrom = beforeOrEqual(m.fromMonth, m.fromYear, referenceMonth, referenceYear)
                val beforeTo = m.isIndefinite || (m.toMonth != null && m.toYear != null &&
                    beforeOrEqual(referenceMonth, referenceYear, m.toMonth, m.toYear))
                val excluded = m.excludedRanges.any { r ->
                    beforeOrEqual(r.fromMonth, r.fromYear, referenceMonth, referenceYear) &&
                        beforeOrEqual(referenceMonth, referenceYear, r.toMonth, r.toYear)
                }
                // One slot per operating day (not one day-less slot) so this renders as
                // a normal day-grouped grid like every other strategy — each day's chip
                // carries the identical monthly rate, since the whole space is booked as
                // one exclusive unit regardless of which day's chip was tapped.
                if (!afterFrom || !beforeTo || excluded) emptyList() else operatingDays.map { day ->
                    RentableSlot(
                        groupLabel = "Monthly • $sourceLabel", day = day,
                        startTime = schedule.openingHour, endTime = schedule.closingHour,
                        label = "$day  full day (${schedule.openingHour} - ${schedule.closingHour})",
                        sourceFormulaId = sourceId,
                        pricesByRecurrence = mapOf(BookingRecurrence.FLAT to m.rateUsd),
                        strategyType = RentalStrategyType.MONTHLY
                    )
                }
            }
            RentalStrategyType.HOURLY -> {
                val h = config.hourly ?: return emptyList()
                operatingDays.flatMap { day ->
                    (openHour until closeHour).mapNotNull { hour ->
                        val price = h.cellPrices["$day|$hour"] ?: return@mapNotNull null
                        RentableSlot(
                            groupLabel = "Hourly • $sourceLabel", day = day,
                            startTime = hourLabel(hour), endTime = hourLabel(hour + 1),
                            label = "$day  ${hourLabel(hour)} - ${hourLabel(hour + 1)}",
                            sourceFormulaId = sourceId,
                            pricesByRecurrence = mapOf(BookingRecurrence.FLAT to price),
                            strategyType = RentalStrategyType.HOURLY
                        )
                    }
                }
            }
            RentalStrategyType.SHIFT_BASED -> {
                val s = config.shiftBased ?: return emptyList()
                val shiftsByName = s.shifts.associateBy { it.name.name }
                operatingDays.flatMap { day ->
                    (s.distribution[day] ?: emptyList()).mapNotNull { shiftNameStr ->
                        val shift = shiftsByName[shiftNameStr] ?: return@mapNotNull null
                        if (shift.isUnavailable) return@mapNotNull null
                        RentableSlot(
                            groupLabel = "Shift • ${shift.name.displayName}", day = day,
                            startTime = hourLabel(shift.startHour), endTime = hourLabel(shift.endHour),
                            label = "$day  ${shift.name.displayName} (${hourLabel(shift.startHour)}-${hourLabel(shift.endHour)})",
                            sourceFormulaId = sourceId,
                            pricesByRecurrence = mapOf(
                                BookingRecurrence.ONE_TIME to shift.pricing.oneTimePrice,
                                BookingRecurrence.SAME_DAY_EVERY_WEEK to shift.pricing.sameDayEveryWeekPrice,
                                BookingRecurrence.SAME_DAY_EVERY_MONTH to shift.pricing.monthlyRecurrencePrice
                            ),
                            strategyType = RentalStrategyType.SHIFT_BASED
                        )
                    }
                }
            }
            RentalStrategyType.DAY_BASED -> {
                val d = config.dayBased ?: return emptyList()
                val from = if (d.useFacilityHours) openHour else (d.customStartHour ?: openHour)
                val to = if (d.useFacilityHours) closeHour else (d.customEndHour ?: closeHour)
                operatingDays.mapNotNull { day ->
                    val dp = d.distribution[day] ?: return@mapNotNull null
                    val prices = buildMap<BookingRecurrence, Double> {
                        dp.oneTimePrice?.let { put(BookingRecurrence.ONE_TIME, it) }
                        dp.sameDayEachWeekPrice?.let { put(BookingRecurrence.SAME_DAY_EVERY_WEEK, it) }
                        dp.sameDayEachMonthPrice?.let { put(BookingRecurrence.SAME_DAY_EVERY_MONTH, it) }
                    }
                    if (prices.isEmpty()) return@mapNotNull null
                    RentableSlot(
                        groupLabel = "Day-Based • $sourceLabel", day = day,
                        startTime = hourLabel(from), endTime = hourLabel(to),
                        label = "$day  full day (${hourLabel(from)}-${hourLabel(to)})",
                        sourceFormulaId = sourceId, pricesByRecurrence = prices,
                        strategyType = RentalStrategyType.DAY_BASED
                    )
                }
            }
        }
    }

    /**
     * The one call every availability-facing screen makes: expands the space's own
     * pricing when it has no subdivisions, or every subdivision's pricing when it
     * does. This is the direct fix for the bug where a subdivided listing's
     * [Subdivision.rentalStrategies] were never read by slot-building at all — both
     * the host's Availability Control editor and the specialist's availability
     * matrix showed "no rentable slots" for every Center/Polyclinic/Co-working
     * listing even though [RentalBookingDialog] could still (fragile-parsing) book
     * one. [Subdivision.pricing]/[SpaceListing.pricing] are always populated —
     * either genuinely new, or synthesized on read from the legacy
     * rentalFormulas/rentalStrategies fields — so this never returns empty for a
     * listing that has real availability configured, old or new.
     */
    fun buildAllSlotsForSpace(space: SpaceListing): List<RentableSlot> {
        return if (space.subdivisions.isEmpty()) {
            buildBookableSlots(space.pricing, space.schedule, sourceId = space.id, sourceLabel = space.title)
        } else {
            space.subdivisions.flatMap { sub ->
                buildBookableSlots(sub.pricing, space.schedule, sourceId = sub.id, sourceLabel = sub.name)
            }
        }
    }

    /**
     * The unit a formula's [RentalFormula.rateUsd] is actually denominated in, so a
     * rate can be labelled honestly instead of being stamped "/mo" regardless of type.
     *
     * This mirrors what the host is asked to enter in SpaceScheduleEditorDialog's
     * formula builder: HOURLY collects "Rate ($ USD / hour)", while SHIFT,
     * DAY_PER_WEEK and FULL_MONTH all collect a monthly figure ("Rate ($ USD/mo)" /
     * "Monthly Rate ($ USD)"). Deriving a per-shift or per-day number from the
     * monthly one would be inventing a figure the host never set.
     */
    fun rateUnitLabel(type: RentalFormulaType): String = when (type) {
        RentalFormulaType.HOURLY -> "/hr"
        RentalFormulaType.SHIFT,
        RentalFormulaType.DAY_PER_WEEK,
        RentalFormulaType.FULL_MONTH -> "/mo"
    }

    /**
     * Maps a real [RentalStrategyType] onto the legacy [RentalFormulaType] that
     * [RentalBookingRequest]/the host's Accept-Reject screens/the Digital Key Pass/
     * WhatsApp templates still key off downstream — a deliberately thin, honest
     * mapping, not a data-model migration (see [representativeFormula]'s doc comment).
     */
    fun legacyFormulaType(strategy: RentalStrategyType): RentalFormulaType = when (strategy) {
        RentalStrategyType.MONTHLY -> RentalFormulaType.FULL_MONTH
        RentalStrategyType.HOURLY -> RentalFormulaType.HOURLY
        RentalStrategyType.SHIFT_BASED -> RentalFormulaType.SHIFT
        RentalStrategyType.DAY_BASED -> RentalFormulaType.DAY_PER_WEEK
    }

    /**
     * Synthesizes a legacy [RentalFormula] from real [RentableSlot]s — the single
     * shared construction every screen that still has to hand a [RentalFormula] to
     * [RentalBookingRequest]/downstream legacy readers uses (SpaceDetailsScreen's
     * renting-option preview, SpaceAvailabilityMatrixView's tap-to-book cells,
     * RentalBookingDialog's final submission), so all three describe the exact same
     * real price/schedule instead of three independent approximations that could
     * silently disagree. [recurrence] only matters for Shift-Based/Day-Based slots
     * (Monthly/Hourly always price under FLAT). Returns null for an empty slot list.
     */
    fun representativeFormula(slots: List<RentableSlot>, recurrence: BookingRecurrence): RentalFormula? {
        val first = slots.firstOrNull() ?: return null
        val strategy = first.strategyType ?: return null
        val days = slots.map { it.day }.distinct()
        val rate = when (strategy) {
            RentalStrategyType.MONTHLY -> first.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0
            RentalStrategyType.HOURLY -> slots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
            else -> slots.sumOf { it.pricesByRecurrence[recurrence] ?: 0.0 }
        }
        // Real weekly hours from the slots themselves — this feeds the host's
        // utilization stat (OwnerHubScreen), the "hrs/wk deducted" note
        // (OwnerIncomingRequestsView) and the booked-hours sum
        // (SpaceAvailabilityCalendarView), all of which read
        // RentalFormula.totalWeeklyHours and were showing its hardcoded default
        // of 30 for every booking before this was computed.
        val weeklyHours = slots.sumOf { slot ->
            val from = parseHour(slot.startTime) ?: 0
            val to = parseHour(slot.endTime) ?: from
            (to - from).coerceAtLeast(0)
        }
        return RentalFormula(
            id = first.sourceFormulaId,
            type = legacyFormulaType(strategy),
            rateUsd = rate,
            scheduleDescription = first.label,
            daysOfWeek = days,
            startHour = slots.minByOrNull { it.startTime }?.startTime ?: first.startTime,
            endHour = slots.maxByOrNull { it.endTime }?.endTime ?: first.endTime,
            totalWeeklyHours = weeklyHours,
            daysCountRequired = days.size.coerceAtLeast(1),
            shiftName = if (strategy == RentalStrategyType.SHIFT_BASED) first.groupLabel.substringAfter("• ") else "Morning Shift"
        )
    }

    /**
     * Sums real, independently-set per-slot prices for the given recurrence — never
     * a proration of some other price. Replaces a same-named, fully dead
     * calculateTotalRentalPrice(baseMonthlyRate, formula, subdivision,
     * selectedStrategyType: RentalStrategy, ...) that had zero callers anywhere in
     * the app (RentalBookingDialog computed its own ad-hoc dynamicMonthlyRate
     * instead of ever calling it) and referenced the now-legacy RentalStrategy enum.
     */
    fun calculateTotalRentalPrice(
        selectedSlots: List<RentableSlot>,
        recurrence: BookingRecurrence,
        durationMonths: Int
    ): Double {
        val strategy = selectedSlots.firstOrNull()?.strategyType
        val effectiveMonths = durationMonths.coerceAtLeast(1)
        return when (strategy) {
            RentalStrategyType.MONTHLY ->
                (selectedSlots.firstOrNull()?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0) * effectiveMonths
            RentalStrategyType.HOURLY ->
                selectedSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
            else ->
                selectedSlots.sumOf { it.pricesByRecurrence[recurrence] ?: 0.0 }
        }
    }

    // A buildWhatsAppInquiryUrl(...) helper used to live here, duplicating
    // ProHostViewModel.launchWhatsAppInquiry's message-building logic almost
    // verbatim with zero callers anywhere in the app — a stale fork that would
    // have drifted out of sync with the real, live version. Removed; use
    // ProHostViewModel.launchWhatsAppInquiry instead.
}
