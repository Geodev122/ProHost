package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.RentalsUiEvent
import com.example.ui.state.RentalsUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing Practitioner rentals, shift schedules, check-in verification, and Whish Pay settlements.
 */
class RentalsViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

    private val _selectedBookingForPayment = MutableStateFlow<RentalBookingRequest?>(null)
    private val _isWhishPayModalOpen = MutableStateFlow(false)
    private val _isProcessingPayment = MutableStateFlow(false)
    private val _lastSettledTransaction = MutableStateFlow<WhishTransaction?>(null)

    private val _events = MutableSharedFlow<RentalsUiEvent>()
    val events: SharedFlow<RentalsUiEvent> = _events.asSharedFlow()

    private val _paymentUiState = combine(
        _selectedBookingForPayment,
        _isWhishPayModalOpen,
        _isProcessingPayment,
        _lastSettledTransaction
    ) { selectedBooking, modalOpen, processing, lastTx ->
        Quadruple(selectedBooking, modalOpen, processing, lastTx)
    }

    val uiState: StateFlow<RentalsUiState> = combine(
        repository.bookingRequests,
        repository.currentUser,
        _paymentUiState
    ) { bookings: List<RentalBookingRequest>, user: AppUser?, paymentState: Quadruple<RentalBookingRequest?, Boolean, Boolean, WhishTransaction?> ->
        val (selectedBooking, modalOpen, processing, lastTx) = paymentState

        val myBookings = if (user == null) emptyList()
        else bookings.filter {
            it.practitionerId == user.id || it.practitionerEmail.equals(user.email, ignoreCase = true)
        }

        val active = myBookings.filter { it.status == BookingRequestStatus.ACCEPTED }
        val pending = myBookings.filter { it.status == BookingRequestStatus.PENDING }
        val past = myBookings.filter {
            it.status == BookingRequestStatus.REJECTED ||
            it.status == BookingRequestStatus.CANCELLED
        }

        RentalsUiState(
            myBookings = myBookings,
            activeBookings = active,
            pendingBookings = pending,
            pastBookings = past,
            selectedBookingForPayment = selectedBooking,
            isWhishPayModalOpen = modalOpen,
            isProcessingPayment = processing,
            lastSettledTransaction = lastTx
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RentalsUiState())

    fun openWhishPayModal(booking: RentalBookingRequest?) {
        _selectedBookingForPayment.value = booking
        _isWhishPayModalOpen.value = booking != null
    }

    fun settleBookingPayment(
        booking: RentalBookingRequest,
        payerName: String,
        payerPhone: String,
        context: Context,
        onSuccess: (WhishTransaction) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessingPayment.value = true
            val txId = "TX-WSH-" + System.currentTimeMillis()
            val orderId = "ORD-BKG-" + booking.id
            val signature = com.example.data.crypto.WhishSecurity.generateSignature(
                amount = booking.totalAmountUsd,
                orderId = orderId
            )

            val success = repository.processWhishPayBooking(
                bookingId = booking.id,
                payerName = payerName,
                payerPhone = payerPhone,
                txId = txId,
                signature = signature
            )

            val tx = WhishTransaction(
                id = txId,
                orderId = orderId,
                amountUsd = booking.totalAmountUsd,
                status = if (success) TransactionStatus.SUCCESS else TransactionStatus.FAILED,
                timestamp = System.currentTimeMillis(),
                payerName = payerName,
                payerPhone = payerPhone,
                signatureHash = signature,
                spaceId = booking.spaceId,
                spaceTitle = booking.spaceTitle
            )

            _lastSettledTransaction.value = tx
            _isProcessingPayment.value = false
            _isWhishPayModalOpen.value = false

            if (success) {
                // Notify user & trigger FCM
                val alert = FCMAlert(
                    title = "Whish Pay Settled ($${booking.totalAmountUsd.toInt()} USD)",
                    body = "Rental for ${booking.spaceTitle} is confirmed. TX #${tx.id.takeLast(6)}.",
                    category = "WHISH_PAY"
                )
                repository.addFCMAlert(alert)
                com.example.service.ProSpaceMessagingService.showPhysicalNotification(
                    context,
                    alert.title,
                    alert.body
                )

                _events.emit(RentalsUiEvent.PaymentCompleted(booking.id))
                onSuccess(tx)
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A, val second: B, val third: C, val fourth: D
)
