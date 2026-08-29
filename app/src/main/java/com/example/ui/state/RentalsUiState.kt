package com.example.ui.state

import com.example.data.model.*

/**
 * Immutable UI State for Practitioner Rentals, Active Shifts & Whish Pay settlements.
 */
data class RentalsUiState(
    val myBookings: List<RentalBookingRequest> = emptyList(),
    val activeBookings: List<RentalBookingRequest> = emptyList(),
    val pendingBookings: List<RentalBookingRequest> = emptyList(),
    val pastBookings: List<RentalBookingRequest> = emptyList(),
    val selectedBookingForPayment: RentalBookingRequest? = null,
    val isWhishPayModalOpen: Boolean = false,
    val isProcessingPayment: Boolean = false,
    val lastSettledTransaction: WhishTransaction? = null
)

/**
 * Events for the Rentals workflow.
 */
sealed interface RentalsUiEvent {
    data class ShowToast(val message: String) : RentalsUiEvent
    data class PaymentCompleted(val bookingId: String) : RentalsUiEvent
    data class OpenWhatsApp(val url: String) : RentalsUiEvent
}
