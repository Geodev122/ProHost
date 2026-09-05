package com.example.ui.util

import com.example.data.model.*
import java.net.URLEncoder

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

    /**
     * Builds a structured, URLEncoded WhatsApp inquiry link for a listing or booking request.
     */
    fun buildWhatsAppInquiryUrl(
        ownerPhone: String,
        ownerName: String,
        space: SpaceListing,
        user: AppUser?,
        selectedFormula: RentalFormula?,
        request: RentalBookingRequest? = null
    ): String {
        val professionalName = user?.fullName ?: "Professional Member"
        val specialty = user?.specialty ?: "Independent Professional"
        val affiliation = user?.affiliation ?: "ProSpace Member Network"
        val syndicate = user?.syndicateNumber ?: "PRO-LB-VERIFIED"

        val formulaText = selectedFormula?.let { "${it.type.displayName} (${it.scheduleDescription} @ $${it.rateUsd}/mo)" }
            ?: "Full Practice Month ($${space.baseMonthlyRateUsd})"

        val requestSnippet = if (request != null) {
            val daysStr = if (request.selectedDays.isNotEmpty()) request.selectedDays.joinToString() else request.formula.daysOfWeek.joinToString()
            val timesStr = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank()) "${request.selectedStartHour} - ${request.selectedEndHour}" else "${request.formula.startHour} - ${request.formula.endHour}"
            val shiftStr = if (request.selectedShift.isNotBlank()) " (${request.selectedShift})" else ""

            "\n\n[In-App Booking Request Details]\n" +
            "• Request ID: #${request.id}\n" +
            "• Formula: ${request.formula.type.displayName} - ${request.formula.scheduleDescription}\n" +
            "• Chosen Availability: $daysStr @ $timesStr$shiftStr\n" +
            "• Start Date: ${request.startDate} (${request.durationMonths} month${if (request.durationMonths > 1) "s" else ""})\n" +
            "• Total Agreement Value: $${request.totalAmountUsd.toInt()} USD\n" +
            "• Professional Notes: ${request.clinicalNotes}\n" +
            "• In-App Status: PENDING HOST APPROVAL"
        } else ""

        val rawMessage = "Hello ${ownerName},\n\n" +
                "I am ${professionalName} (${specialty}, affiliated with ${affiliation}, ID #${syndicate}).\n\n" +
                "I am contacting you regarding your space \"${space.title}\" located in ${space.district}, ${space.governorate.displayName} on ProHost.\n" +
                "Selected Formula: ${formulaText}$requestSnippet\n\n" +
                "I would like to finalize payment and walk-through details.\n" +
                "Listing Ref: ProHost #LB-${space.id}"

        val encoded = URLEncoder.encode(rawMessage, "UTF-8")
        val cleanPhone = ownerPhone.replace("+", "").replace(" ", "").replace("-", "")
        return "https://wa.me/$cleanPhone?text=$encoded"
    }
}
