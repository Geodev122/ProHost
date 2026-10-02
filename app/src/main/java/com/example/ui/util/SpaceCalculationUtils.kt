package com.example.ui.util

import com.example.data.model.*
import java.util.Calendar

/**
 * One rentable unit of time produced by a rental pricing strategy. Shared source of
 * truth between CreateListingDialog's Blackout Slots on/off toggles and the
 * Specialist-facing availability views — both render the exact same derived slots
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
                            // Single flat price per shift now (item 7b) — a single-
                            // entry map keyed FLAT, matching Monthly/Hourly's shape,
                            // rather than 3 now-meaningless legacy recurrence keys.
                            pricesByRecurrence = mapOf(BookingRecurrence.FLAT to shift.price),
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
                    if (dp.price <= 0.0) return@mapNotNull null
                    RentableSlot(
                        groupLabel = "Day-Based • $sourceLabel", day = day,
                        startTime = hourLabel(from), endTime = hourLabel(to),
                        label = "$day  full day (${hourLabel(from)}-${hourLabel(to)})",
                        sourceFormulaId = sourceId,
                        // Single flat price per day now (this session's Day-Based
                        // rework, same shape as Shift-Based's item 7b) — a single-
                        // entry map keyed FLAT rather than 3 now-meaningless legacy
                        // recurrence keys.
                        pricesByRecurrence = mapOf(BookingRecurrence.FLAT to dp.price),
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
                // A division with its own scheduleOverride (different hours/days/
                // blackouts than the rest of the space — e.g. one exam room closing
                // earlier) expands against that instead of the whole space's schedule.
                // Every screen that reads slots (host's editor, the specialist's
                // availability matrix, RentalBookingDialog) goes through this one
                // function, so they can never disagree about which schedule applies.
                buildBookableSlots(sub.pricing, sub.scheduleOverride ?: space.schedule, sourceId = sub.id, sourceLabel = sub.name)
            }
        }
    }

    private fun hoursOverlap(aStart: String, aEnd: String, bStart: String, bEnd: String): Boolean {
        val s1 = parseHour(aStart) ?: return false
        val e1 = parseHour(aEnd) ?: return false
        val s2 = parseHour(bStart) ?: return false
        val e2 = parseHour(bEnd) ?: return false
        return s1 < e2 && s2 < e1
    }

    private fun bookingScope(booking: RentalBookingRequest, spaceId: String): String =
        booking.subdivisionId ?: spaceId

    private fun bookingDays(booking: RentalBookingRequest): List<String> =
        if (booking.selectedDays.isNotEmpty()) booking.selectedDays else booking.formula.daysOfWeek

    /**
     * Whether an ACCEPTED booking already locks [slot]. The one conflict rule every
     * screen shares — the specialist's availability matrix, the booking dialog (which
     * hides locked slots), and the host's accept path (see [findAcceptConflict]) —
     * so a slot can't read as open in one place and taken in another.
     *
     * Scoped to the slot's own subdivision (or the whole space when it has none): a
     * full-month booking of Room A locks Room A's slots, not Room B's. A FULL_MONTH
     * booking locks every slot in its scope; anything else locks slots on a day it
     * covers whose hours overlap its start/end. Since a booking's start/end is the
     * min/max over the slots it chose, non-contiguous hourly picks lock the hours in
     * between too — conservative on purpose. [ignoreBookingId] lets an edit of an
     * accepted booking not be blocked by itself.
     */
    fun isSlotLocked(
        slot: RentableSlot,
        spaceId: String,
        acceptedBookings: List<RentalBookingRequest>,
        ignoreBookingId: String? = null
    ): Boolean = acceptedBookings.any { req ->
        req.status == BookingRequestStatus.ACCEPTED &&
            req.id != ignoreBookingId &&
            req.spaceId == spaceId &&
            bookingScope(req, spaceId) == slot.sourceFormulaId &&
            (req.formula.type == RentalFormulaType.FULL_MONTH ||
                // A booking scoped to specific calendar dates (item 7b/Decision 2)
                // only locks those exact dates — see isCalendarDateLocked — never
                // the whole recurring weekday+shift pattern, so this general
                // "is the pattern ever offered" check stays true and other
                // specialists can still book a different date of the same shift.
                (req.selectedCalendarDates.isEmpty() &&
                    bookingDays(req).contains(slot.day) &&
                    hoursOverlap(req.formula.startHour, req.formula.endHour, slot.startTime, slot.endTime)))
    }

    /**
     * The per-date counterpart to [isSlotLocked] — whether [isoDate] (a real
     * calendar date matching [slot]'s weekday) is already locked by an ACCEPTED
     * booking. Used by RentalBookingDialog's calendar-date picker (item 7b) to
     * grey out only the specific dates another specialist already has, leaving
     * the rest of the same weekday+shift pattern open. A booking with no
     * selectedCalendarDates (every non-Shift strategy, and any Shift-Based
     * booking made before this field existed) still locks every occurrence of
     * its weekday — the same fallback [isSlotLocked] uses.
     */
    fun isCalendarDateLocked(
        slot: RentableSlot,
        isoDate: String,
        spaceId: String,
        acceptedBookings: List<RentalBookingRequest>,
        ignoreBookingId: String? = null
    ): Boolean = acceptedBookings.any { req ->
        req.status == BookingRequestStatus.ACCEPTED &&
            req.id != ignoreBookingId &&
            req.spaceId == spaceId &&
            bookingScope(req, spaceId) == slot.sourceFormulaId &&
            (req.formula.type == RentalFormulaType.FULL_MONTH ||
                (hoursOverlap(req.formula.startHour, req.formula.endHour, slot.startTime, slot.endTime) &&
                    if (req.selectedCalendarDates.isNotEmpty()) {
                        isoDate in req.selectedCalendarDates
                    } else {
                        bookingDays(req).contains(slot.day)
                    }))
    }

    /**
     * The ACCEPTED booking [candidate] would collide with if it were accepted now,
     * or null when it's clear — same scoping and overlap rule as [isSlotLocked],
     * applied booking-to-booking. The booking [candidate] is an edit of
     * (replacesBookingId) is released on acceptance, so it never counts. When both
     * bookings carry real selectedCalendarDates (item 7b/Decision 2), the exact
     * dates are compared instead of weekday overlap — two Shift-Based bookings on
     * the same weekday but disjoint dates are not a conflict. When only one side
     * (or neither) has real dates, falls back to the original weekday check —
     * conservative, so a legacy weekday-forever lock is never silently bypassed.
     */
    fun findAcceptConflict(
        candidate: RentalBookingRequest,
        allBookings: List<RentalBookingRequest>
    ): RentalBookingRequest? = allBookings.firstOrNull { other ->
        other.id != candidate.id &&
            other.id != candidate.replacesBookingId &&
            other.status == BookingRequestStatus.ACCEPTED &&
            other.spaceId == candidate.spaceId &&
            bookingScope(other, candidate.spaceId) == bookingScope(candidate, candidate.spaceId) &&
            (other.formula.type == RentalFormulaType.FULL_MONTH ||
                candidate.formula.type == RentalFormulaType.FULL_MONTH ||
                (hoursOverlap(other.formula.startHour, other.formula.endHour, candidate.formula.startHour, candidate.formula.endHour) &&
                    if (other.selectedCalendarDates.isNotEmpty() && candidate.selectedCalendarDates.isNotEmpty()) {
                        other.selectedCalendarDates.any { it in candidate.selectedCalendarDates }
                    } else {
                        bookingDays(other).any { it in bookingDays(candidate) }
                    }))
    }

    data class PriceDisplay(
        val amount: Double,
        val unitLabel: String
    )

    /**
     * Dynamically computes the lowest configured price and unit label across all available
     * whole-space formulas and subdivision pricing configs for a given workspace listing.
     */
    fun findLowestConfiguredPrice(space: SpaceListing): PriceDisplay {
        val candidates = mutableListOf<PriceDisplay>()

        if (space.baseMonthlyRateUsd > 0.0) {
            candidates.add(PriceDisplay(space.baseMonthlyRateUsd, "/mo"))
        }

        space.rentalFormulas.forEach { formula ->
            if (formula.rateUsd > 0.0) {
                val label = rateUnitLabel(formula.type)
                candidates.add(PriceDisplay(formula.rateUsd, label))
            }
        }

        space.subdivisions.forEach { sub ->
            val pricing = sub.pricing
            when (pricing.strategyType) {
                RentalStrategyType.MONTHLY -> {
                    val rate = pricing.monthly?.rateUsd ?: 0.0
                    if (rate > 0.0) candidates.add(PriceDisplay(rate, "/mo"))
                }
                RentalStrategyType.HOURLY -> {
                    val rates = pricing.hourly?.cellPrices?.values?.filter { it > 0.0 } ?: emptyList()
                    rates.minOrNull()?.let { candidates.add(PriceDisplay(it, "/hr")) }
                }
                RentalStrategyType.SHIFT_BASED -> {
                    val shiftRates = pricing.shiftBased?.shifts?.map { it.price }?.filter { it > 0.0 } ?: emptyList()
                    shiftRates.minOrNull()?.let { candidates.add(PriceDisplay(it, "/shift")) }
                }
                RentalStrategyType.DAY_BASED -> {
                    val dayRates = pricing.dayBased?.distribution?.values?.map { it.price }?.filter { it > 0.0 } ?: emptyList()
                    dayRates.minOrNull()?.let { candidates.add(PriceDisplay(it, "/day")) }
                }
            }
        }

        return candidates.minByOrNull { it.amount } ?: PriceDisplay(space.baseMonthlyRateUsd.coerceAtLeast(0.0), "/mo")
    }

    fun findLowestPriceForSubdivision(sub: com.example.data.model.Subdivision): PriceDisplay =
        lowestPriceFor(sub.pricing) ?: PriceDisplay(0.0, strategyUnitLabel(sub.pricing.strategyType))

    /** Lowest configured price of one pricing config, in that config's own unit; null if unpriced. */
    fun lowestPriceFor(pricing: com.example.data.model.RentalPricingConfig): PriceDisplay? {
        val unit = strategyUnitLabel(pricing.strategyType)
        val amount = when (pricing.strategyType) {
            RentalStrategyType.MONTHLY -> pricing.monthly?.rateUsd?.takeIf { it > 0.0 }
            RentalStrategyType.HOURLY -> pricing.hourly?.cellPrices?.values?.filter { it > 0.0 }?.minOrNull()
            RentalStrategyType.SHIFT_BASED -> pricing.shiftBased?.shifts?.map { it.price }?.filter { it > 0.0 }?.minOrNull()
            RentalStrategyType.DAY_BASED -> pricing.dayBased?.distribution?.values?.map { it.price }?.filter { it > 0.0 }?.minOrNull()
        }
        return amount?.let { PriceDisplay(it, unit) }
    }

    /**
     * The unit a formula's [RentalFormula.rateUsd] is actually denominated in, so a
     * rate can be labelled honestly instead of being stamped "/mo" regardless of type.
     *
     * This mirrors what the host is asked to enter in CreateListingDialog's
     * Additional Rental Formulas builder: HOURLY collects "Rate ($ USD / hour)", while SHIFT,
     * DAY_PER_WEEK and FULL_MONTH all collect a monthly figure ("Rate ($ USD/mo)" /
     * "Monthly Rate ($ USD)"). Deriving a per-shift or per-day number from the
     * monthly one would be inventing a figure the host never set.
     */
    fun rateUnitLabel(type: RentalFormulaType): String = when (type) {
        RentalFormulaType.HOURLY -> "/hr"
        RentalFormulaType.SHIFT -> "/shift"
        RentalFormulaType.DAY_PER_WEEK -> "/day"
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

    /** "/hr", "/shift", "/day" or "/mo" for a pricing strategy — never hard-code a suffix. */
    fun strategyUnitLabel(strategy: RentalStrategyType): String = rateUnitLabel(legacyFormulaType(strategy))

    /**
     * Returns the lowest configured price for a space and its correct unit label
     * (e.g. 8.0 to "/hr", 25.0 to "/shift"). Used wherever a single summary price
     * is needed — listing cards, detail screen fallback — so all callers show real
     * data rather than a static baseMonthlyRateUsd/mo regardless of strategy.
     */
    fun lowestPriceSummary(space: com.example.data.model.SpaceListing): Pair<Double, String> {
        val slots = buildAllSlotsForSpace(space)
        val cheapest = slots
            .mapNotNull { slot ->
                val price = slot.pricesByRecurrence[BookingRecurrence.FLAT] ?: return@mapNotNull null
                val strategyType = slot.strategyType ?: return@mapNotNull null
                val unit = rateUnitLabel(legacyFormulaType(strategyType))
                price to unit
            }
            .minByOrNull { (price, _) -> price }
        return cheapest ?: (space.baseMonthlyRateUsd to "/mo")
    }

    /**
     * Synthesizes a legacy [RentalFormula] from real [RentableSlot]s — the single
     * shared construction every screen that still has to hand a [RentalFormula] to
     * [RentalBookingRequest]/downstream legacy readers uses (SpaceDetailsScreen's
     * renting-option preview and Check Availability panel, RentalBookingDialog's
     * final submission), so all of them describe the exact same real price/
     * schedule instead of independent approximations that could silently
     * disagree. [recurrence] only matters for Shift-Based/Day-Based slots
     * (Monthly/Hourly always price under FLAT). Returns null for an empty slot list.
     */
    fun representativeFormula(slots: List<RentableSlot>, recurrence: BookingRecurrence): RentalFormula? {
        val first = slots.firstOrNull() ?: return null
        val strategy = first.strategyType ?: return null
        val days = slots.map { it.day }.distinct()
        val rate = when (strategy) {
            RentalStrategyType.MONTHLY -> first.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0
            RentalStrategyType.HOURLY -> slots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
            // A shift's pricesByRecurrence map only ever carries a FLAT key now
            // (item 7b) — read that regardless of what recurrence the caller
            // passes, same as Monthly/Hourly above.
            RentalStrategyType.SHIFT_BASED -> slots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
            // A day's pricesByRecurrence map only ever carries a FLAT key now (this
            // session's Day-Based rework, mirroring item 7b's Shift-Based change) —
            // read that regardless of what recurrence the caller passes.
            RentalStrategyType.DAY_BASED -> slots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
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
     *
     * Neither SHIFT_BASED (item 7b) nor DAY_BASED (this session's identical rework)
     * is priced through this function anymore — each now has one flat price, and
     * the specialist picks specific calendar dates rather than a term/recurrence
     * tier, so the real total is simply `selectedCalendarDates.size * price` (per
     * weekday for Day-Based, since different days can carry different prices),
     * computed directly at RentalBookingDialog's own call site where the chosen
     * dates are known. The `else` branch below is now unreachable in practice —
     * kept only so a stray legacy call site fails soft (0) rather than crashing,
     * since neither remaining strategy's pricesByRecurrence map carries anything
     * but a FLAT key.
     */
    fun calculateTotalRentalPrice(
        selectedSlots: List<RentableSlot>,
        recurrence: BookingRecurrence,
        durationMonths: Int,
        startDate: Calendar = Calendar.getInstance()
    ): Double {
        val strategy = selectedSlots.firstOrNull()?.strategyType
        val effectiveMonths = durationMonths.coerceAtLeast(1)
        return when (strategy) {
            RentalStrategyType.MONTHLY ->
                (selectedSlots.firstOrNull()?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0) * effectiveMonths
            RentalStrategyType.HOURLY ->
                selectedSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
            // Unreachable for MONTHLY/HOURLY/SHIFT_BASED/DAY_BASED (all handled
            // above or bypass this function entirely) — see this function's doc
            // comment.
            else ->
                selectedSlots.sumOf { slot ->
                    (slot.pricesByRecurrence[recurrence] ?: 0.0) *
                        countRecurrenceOccurrences(slot.day, recurrence, startDate, effectiveMonths)
                }
        }
    }

    private val dayAbbreviationToCalendarDay = mapOf(
        "Sun" to Calendar.SUNDAY, "Mon" to Calendar.MONDAY, "Tue" to Calendar.TUESDAY,
        "Wed" to Calendar.WEDNESDAY, "Thu" to Calendar.THURSDAY, "Fri" to Calendar.FRIDAY,
        "Sat" to Calendar.SATURDAY
    )

    /**
     * How many times a slot on [day] is actually delivered under [recurrence] over a
     * term of [durationMonths] months beginning on [startDate] (term window is
     * [startDate, startDate + months), exclusive). ONE_TIME is exactly one delivery;
     * SAME_DAY_EVERY_MONTH is one per month of the term; SAME_DAY_EVERY_WEEK walks
     * the real calendar and counts that weekday inside the window. FLAT (Monthly/
     * Hourly) is treated as a single unit — those strategies never call this.
     */
    fun countRecurrenceOccurrences(
        day: String,
        recurrence: BookingRecurrence,
        startDate: Calendar,
        durationMonths: Int
    ): Int {
        val months = durationMonths.coerceAtLeast(1)
        return when (recurrence) {
            BookingRecurrence.ONE_TIME, BookingRecurrence.FLAT -> 1
            BookingRecurrence.SAME_DAY_EVERY_MONTH -> months
            BookingRecurrence.SAME_DAY_EVERY_WEEK -> {
                val target = dayAbbreviationToCalendarDay[day.take(3)] ?: return 0
                val end = (startDate.clone() as Calendar).apply { add(Calendar.MONTH, months) }
                val cursor = startDate.clone() as Calendar
                var count = 0
                while (cursor.before(end)) {
                    if (cursor.get(Calendar.DAY_OF_WEEK) == target) count++
                    cursor.add(Calendar.DAY_OF_MONTH, 1)
                }
                count
            }
        }
    }

    /**
     * The next real calendar date — today, if it matches, otherwise the soonest day
     * after today — whose weekday equals [day] (a 3-letter abbreviation like "Mon"),
     * formatted "yyyy-MM-dd". HOURLY/SHIFT_BASED/DAY_BASED slots only ever encode a
     * recurring weekday pattern, not a concrete date — Workspace Details' slot-tap-
     * to-request flow needs one real date to submit a booking against, the same way
     * RentalBookingDialog's own calendar picker (item 7b) resolves a specific date
     * before submitting a Shift-Based request.
     */
    fun nextDateForWeekday(day: String, from: Calendar = Calendar.getInstance()): String {
        val target = dayAbbreviationToCalendarDay[day.take(3)] ?: Calendar.MONDAY
        val cal = (from.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != target) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Turns RentalBookingDialog's start-date choice into a real calendar date (time
     * cleared to midnight) — the same four presets the dialog offers, with the
     * custom option parsed from "yyyy-MM-dd" and falling back to tomorrow if it
     * can't be parsed, so the occurrence count above always has a real anchor.
     */
    fun resolveStartDate(option: String, customIsoDate: String): Calendar {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (option) {
            "Next Monday" -> do { cal.add(Calendar.DAY_OF_MONTH, 1) } while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            "1st of Next Month" -> { cal.add(Calendar.MONTH, 1); cal.set(Calendar.DAY_OF_MONTH, 1) }
            "Custom Date" -> {
                val parts = customIsoDate.trim().split("-").mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    cal.set(parts[0], parts[1] - 1, parts[2])
                } else {
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            else -> cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal
    }

    // A buildWhatsAppInquiryUrl(...) helper used to live here, duplicating
    // ProHostViewModel.launchWhatsAppInquiry's message-building logic almost
    // verbatim with zero callers anywhere in the app — a stale fork that would
    // have drifted out of sync with the real, live version. Removed; use
    // ProHostViewModel.launchWhatsAppInquiry instead.
}
