package com.example.ui.util

import com.example.data.model.*

/**
 * One rentable unit of time produced by a rental formula. Shared source of truth
 * between the host's Availability Control editor (SpaceScheduleEditorDialog's
 * on/off toggles) and the Specialist-facing availability view
 * (SpaceAvailabilityMatrixView) — both render the exact same derived slots
 * ([SpaceCalculationUtils.buildRentableSlots]) instead of two different models
 * that could silently disagree (the specialist view used to be a fixed
 * Morning/Afternoon/Evening grid unrelated to what the host actually configured).
 */
data class RentableSlot(
    val groupLabel: String,
    val day: String,
    val startTime: String,
    val endTime: String,
    val label: String,
    // Which RentalFormula actually produced this slot — lets a caller (e.g. a
    // Specialist tapping an open slot) resolve the real formula directly
    // (space.rentalFormulas.find { it.id == sourceFormulaId }) instead of
    // guessing/synthesizing one from the slot's day/time alone.
    val sourceFormulaId: String
)

/**
 * Pure helper functions for pricing calculations, slot validations, and WhatsApp URL generation.
 * Separated from View layer to adhere strictly to MVVM standards.
 */
object SpaceCalculationUtils {

    private fun parseHour(value: String): Int? =
        value.substringBefore(':').trim().toIntOrNull()?.takeIf { it in 0..24 }

    private fun hourLabel(hour: Int): String = "%02d:00".format(hour)

    /**
     * Expands a space's rental formulas into the individual slots a specialist could
     * book, restricted to the days the space actually operates. Hourly formulas
     * expand to one slot per hour, shift formulas to one slot per day for that
     * shift's window, and day-per-week / full-month formulas to one whole-day slot.
     *
     * Days are emitted in the same 3-letter form the rest of the app matches against
     * (SimpleDateFormat("EEE")).
     */
    fun buildRentableSlots(
        formulas: List<RentalFormula>,
        schedule: SpaceOperatingSchedule
    ): List<RentableSlot> {
        val operatingDays = schedule.operatingDays.toMutableList()
        if (schedule.isSundayOperating && operatingDays.none { it.equals("Sun", ignoreCase = true) }) {
            operatingDays.add("Sun")
        }
        if (operatingDays.isEmpty()) return emptyList()

        val openHour = parseHour(schedule.openingHour) ?: 0
        val closeHour = parseHour(schedule.closingHour) ?: 24

        return formulas.flatMap { formula ->
            val days = operatingDays.filter { day ->
                formula.daysOfWeek.any { it.equals(day, ignoreCase = true) }
            }
            when (formula.type) {
                RentalFormulaType.HOURLY -> {
                    val from = maxOf(parseHour(formula.startHour) ?: openHour, openHour)
                    val to = minOf(parseHour(formula.endHour) ?: closeHour, closeHour)
                    days.flatMap { day ->
                        (from until to).map { hour ->
                            RentableSlot(
                                groupLabel = "Hourly • ${formula.scheduleDescription}",
                                day = day,
                                startTime = hourLabel(hour),
                                endTime = hourLabel(hour + 1),
                                label = "$day  ${hourLabel(hour)} - ${hourLabel(hour + 1)}",
                                sourceFormulaId = formula.id
                            )
                        }
                    }
                }
                RentalFormulaType.SHIFT -> days.map { day ->
                    RentableSlot(
                        groupLabel = "Shift • ${formula.shiftName}",
                        day = day,
                        startTime = formula.startHour,
                        endTime = formula.endHour,
                        label = "$day  ${formula.startHour} - ${formula.endHour}",
                        sourceFormulaId = formula.id
                    )
                }
                RentalFormulaType.DAY_PER_WEEK, RentalFormulaType.FULL_MONTH -> days.map { day ->
                    RentableSlot(
                        groupLabel = if (formula.type == RentalFormulaType.FULL_MONTH) {
                            "Full Month"
                        } else {
                            "Day per Week"
                        },
                        day = day,
                        startTime = schedule.openingHour,
                        endTime = schedule.closingHour,
                        label = "$day  full day (${schedule.openingHour} - ${schedule.closingHour})",
                        sourceFormulaId = formula.id
                    )
                }
            }
        }.distinctBy { "${it.groupLabel}|${it.day}|${it.startTime}|${it.endTime}" }
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
     * Computes the total price in USD based on selected formula, hours, days, duration, or subdivision strategy.
     */
    fun calculateTotalRentalPrice(
        baseMonthlyRate: Double,
        formula: RentalFormula?,
        subdivision: Subdivision?,
        selectedStrategyType: RentalStrategy,
        selectedDaysCount: Int,
        durationHours: Double,
        durationMonths: Int
    ): Double {
        val effectiveMonths = if (durationMonths <= 0) 1 else durationMonths

        // 1. Check if subdivision strategy is selected
        if (subdivision != null && subdivision.rentalStrategies.isNotEmpty()) {
            val matchingStrategy = subdivision.rentalStrategies.find { it.strategy == selectedStrategyType }
                ?: subdivision.rentalStrategies.first()
            return when (matchingStrategy.strategy) {
                RentalStrategy.HOURLY -> {
                    val rate = matchingStrategy.rateUsd
                    rate * (if (durationHours > 0) durationHours else 4.0)
                }
                RentalStrategy.SHIFT_BASED -> {
                    val rate = matchingStrategy.rateUsd
                    rate * (if (selectedDaysCount > 0) selectedDaysCount else 1) * effectiveMonths * 4.0
                }
                RentalStrategy.DAILY -> {
                    val rate = matchingStrategy.rateUsd
                    rate * (if (selectedDaysCount > 0) selectedDaysCount else 1) * effectiveMonths * 4.0
                }
                RentalStrategy.MONTHLY -> {
                    val rate = matchingStrategy.rateUsd
                    rate * effectiveMonths
                }
            }
        }

        // 2. Standard Level 1 Rental Formula pricing
        if (formula != null) {
            return formula.rateUsd * effectiveMonths
        }

        // 3. Fallback to base listing monthly rate
        return baseMonthlyRate * effectiveMonths
    }

    // A buildWhatsAppInquiryUrl(...) helper used to live here, duplicating
    // ProHostViewModel.launchWhatsAppInquiry's message-building logic almost
    // verbatim with zero callers anywhere in the app — a stale fork that would
    // have drifted out of sync with the real, live version. Removed; use
    // ProHostViewModel.launchWhatsAppInquiry instead.
}
