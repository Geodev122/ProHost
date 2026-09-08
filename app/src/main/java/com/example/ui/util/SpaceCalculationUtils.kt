package com.example.ui.util

import com.example.data.model.*

/**
 * Pure helper functions for pricing calculations, slot validations, and WhatsApp URL generation.
 * Separated from View layer to adhere strictly to MVVM standards.
 */
object SpaceCalculationUtils {

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
