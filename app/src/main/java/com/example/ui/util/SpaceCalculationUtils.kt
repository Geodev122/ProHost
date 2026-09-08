package com.example.ui.util

import com.example.data.model.*

/**
 * Pure helper functions for pricing calculations, slot validations, and WhatsApp URL generation.
 * Separated from View layer to adhere strictly to MVVM standards.
 */
object SpaceCalculationUtils {

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
