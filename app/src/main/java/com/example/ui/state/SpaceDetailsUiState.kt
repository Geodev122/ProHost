package com.example.ui.state

import com.example.data.model.*

/**
 * Immutable UI State for the Space Details & Rental Booking Screen/Dialog.
 */
data class SpaceDetailsUiState(
    val space: SpaceListing? = null,
    val selectedSubdivision: Subdivision? = null,
    val selectedFormula: RentalFormula? = null,
    val selectedStrategyType: RentalStrategy = RentalStrategy.SHIFT_BASED,
    val selectedDays: List<String> = emptyList(),
    val selectedStartHour: String = "",
    val selectedEndHour: String = "",
    val selectedShift: String = "",
    val startDate: String = "",
    val durationMonths: Int = 1,
    val clinicalNotes: String = "",
    val calculatedTotalUsd: Double = 0.0,
    val isBookingModalOpen: Boolean = false,
    val isSubmittingBooking: Boolean = false,
    val bookingSuccess: Boolean = false,
    val lastCreatedBooking: RentalBookingRequest? = null,
    val acceptedBookings: List<RentalBookingRequest> = emptyList(),
    val pendingBookings: List<RentalBookingRequest> = emptyList()
)

/**
 * Events from Space Details actions.
 */
sealed interface SpaceDetailsUiEvent {
    data class BookingCreated(val request: RentalBookingRequest) : SpaceDetailsUiEvent
    data class ShowToast(val message: String) : SpaceDetailsUiEvent
    data class OpenWhatsApp(val url: String) : SpaceDetailsUiEvent
}
