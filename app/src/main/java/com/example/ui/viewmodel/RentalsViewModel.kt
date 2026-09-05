package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.RentalsUiEvent
import com.example.ui.state.RentalsUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing Practitioner rentals, shift schedules, check-in verification, and Whish Pay settlements.
 */
class RentalsViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

    private val functionsClient = FirebaseFunctionsClient()

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

    /**
     * Initiates a real Whish payment for [booking] via the initiateWhishPayment Cloud
     * Function (amount is looked up server-side from the booking's own totalAmountUsd,
     * never trusted from the client), opens the returned checkout URL, then polls
     * checkWhishStatus for a bounded time. [onSuccess] fires only once the server has
     * independently confirmed the payment with Whish and granted the entitlement —
     * this used to construct a "SUCCESS" WhishTransaction locally and call
     * repository.processWhishPayBooking(), which self-reported success with no real
     * payment involved at all.
     */
    fun settleBookingPayment(
        booking: RentalBookingRequest,
        payerName: String,
        payerPhone: String,
        context: Context,
        onSuccess: (WhishTransaction) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessingPayment.value = true
            val initResult = functionsClient.initiateWhishPayment("BOOKING", booking.id, payerName, payerPhone)
            val init = initResult.getOrNull()
            if (init == null) {
                _isProcessingPayment.value = false
                _events.emit(RentalsUiEvent.PaymentCompleted(booking.id))
                return@launch
            }

            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(init.collectUrl)))
            } catch (e: Exception) {
                // Caller UI is expected to surface this via events/uiState; nothing more
                // to do here if the browser can't be launched.
            }

            var settledStatus = "PENDING"
            for (attempt in 1..24) {
                delay(5000)
                val status = functionsClient.checkWhishStatus(init.txId).getOrNull()
                if (status == "SUCCESS" || status == "FAILED") {
                    settledStatus = status
                    break
                }
            }

            _isProcessingPayment.value = false
            _isWhishPayModalOpen.value = false

            if (settledStatus == "SUCCESS") {
                val tx = WhishTransaction(
                    id = init.txId,
                    orderId = init.orderId,
                    amountUsd = booking.totalAmountUsd,
                    status = TransactionStatus.SUCCESS,
                    timestamp = System.currentTimeMillis(),
                    payerName = payerName,
                    payerPhone = payerPhone,
                    signatureHash = "",
                    spaceId = booking.spaceId,
                    spaceTitle = booking.spaceTitle
                )
                _lastSettledTransaction.value = tx

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
            } else {
                _events.emit(RentalsUiEvent.PaymentCompleted(booking.id))
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A, val second: B, val third: C, val fourth: D
)
