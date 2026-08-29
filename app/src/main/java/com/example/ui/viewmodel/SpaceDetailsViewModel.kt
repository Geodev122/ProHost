package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.SpaceDetailsUiEvent
import com.example.ui.state.SpaceDetailsUiState
import com.example.ui.util.SpaceCalculationUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing detailed space viewing, subdivision breakdown, formula/schedule configuration, and booking requests.
 */
class SpaceDetailsViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpaceDetailsUiState())
    val uiState: StateFlow<SpaceDetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SpaceDetailsUiEvent>()
    val events: SharedFlow<SpaceDetailsUiEvent> = _events.asSharedFlow()

    fun loadSpace(space: SpaceListing) {
        val accepted = repository.bookingRequests.value.filter { it.spaceId == space.id && it.status == BookingRequestStatus.ACCEPTED }
        val pending = repository.bookingRequests.value.filter { it.spaceId == space.id && it.status == BookingRequestStatus.PENDING }
        val defaultSubdivision = space.subdivisions.firstOrNull()

        _uiState.update {
            it.copy(
                space = space,
                selectedSubdivision = defaultSubdivision,
                selectedFormula = space.rentalFormulas.firstOrNull(),
                acceptedBookings = accepted,
                pendingBookings = pending,
                isBookingModalOpen = false,
                bookingSuccess = false,
                calculatedTotalUsd = recalculatePrice(space, space.rentalFormulas.firstOrNull(), defaultSubdivision, it.selectedStrategyType, it.selectedDays.size, it.durationMonths)
            )
        }
    }

    fun selectSubdivision(subdivision: Subdivision?) {
        _uiState.update { current ->
            val updatedStrategy = subdivision?.rentalStrategies?.firstOrNull()?.strategy ?: RentalStrategy.SHIFT_BASED
            current.copy(
                selectedSubdivision = subdivision,
                selectedStrategyType = updatedStrategy,
                calculatedTotalUsd = current.space?.let {
                    recalculatePrice(it, current.selectedFormula, subdivision, updatedStrategy, current.selectedDays.size, current.durationMonths)
                } ?: 0.0
            )
        }
    }

    fun selectFormula(formula: RentalFormula) {
        _uiState.update { current ->
            current.copy(
                selectedFormula = formula,
                calculatedTotalUsd = current.space?.let {
                    recalculatePrice(it, formula, current.selectedSubdivision, current.selectedStrategyType, current.selectedDays.size, current.durationMonths)
                } ?: 0.0
            )
        }
    }

    fun selectStrategy(strategy: RentalStrategy) {
        _uiState.update { current ->
            current.copy(
                selectedStrategyType = strategy,
                calculatedTotalUsd = current.space?.let {
                    recalculatePrice(it, current.selectedFormula, current.selectedSubdivision, strategy, current.selectedDays.size, current.durationMonths)
                } ?: 0.0
            )
        }
    }

    fun updateSelectedDays(days: List<String>) {
        _uiState.update { current ->
            current.copy(
                selectedDays = days,
                calculatedTotalUsd = current.space?.let {
                    recalculatePrice(it, current.selectedFormula, current.selectedSubdivision, current.selectedStrategyType, days.size, current.durationMonths)
                } ?: 0.0
            )
        }
    }

    fun updateDurationMonths(months: Int) {
        val validMonths = if (months < 1) 1 else months
        _uiState.update { current ->
            current.copy(
                durationMonths = validMonths,
                calculatedTotalUsd = current.space?.let {
                    recalculatePrice(it, current.selectedFormula, current.selectedSubdivision, current.selectedStrategyType, current.selectedDays.size, validMonths)
                } ?: 0.0
            )
        }
    }

    fun updateHours(startHour: String, endHour: String) {
        _uiState.update { it.copy(selectedStartHour = startHour, selectedEndHour = endHour) }
    }

    fun updateShift(shift: String) {
        _uiState.update { it.copy(selectedShift = shift) }
    }

    fun updateStartDate(date: String) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun updateClinicalNotes(notes: String) {
        _uiState.update { it.copy(clinicalNotes = notes) }
    }

    fun setBookingModalOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isBookingModalOpen = isOpen, bookingSuccess = false) }
    }

    fun submitBookingRequest(user: AppUser, onSuccess: (RentalBookingRequest) -> Unit) {
        val state = _uiState.value
        val space = state.space ?: return
        val formula = state.selectedFormula ?: space.rentalFormulas.firstOrNull() ?: RentalFormula(
            id = "default_${System.currentTimeMillis()}",
            type = RentalFormulaType.FULL_MONTH,
            rateUsd = space.baseMonthlyRateUsd,
            scheduleDescription = "Standard Rental",
            daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
            startHour = "08:00",
            endHour = "18:00"
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingBooking = true) }

            val request = repository.createBookingRequest(
                space = space,
                formula = formula,
                practitioner = user,
                startDate = state.startDate.ifBlank { "Immediate Availability" },
                durationMonths = state.durationMonths,
                notes = state.clinicalNotes.ifBlank { "Standard booking request via ProSpace app." },
                selectedDays = state.selectedDays.ifEmpty { formula.daysOfWeek },
                selectedStartHour = state.selectedStartHour.ifBlank { formula.startHour },
                selectedEndHour = state.selectedEndHour.ifBlank { formula.endHour },
                selectedShift = state.selectedShift,
                calculatedTotalUsd = state.calculatedTotalUsd,
                subdivisionId = state.selectedSubdivision?.id,
                subdivisionName = state.selectedSubdivision?.name,
                selectedStrategy = state.selectedStrategyType.displayName
            )

            _uiState.update {
                it.copy(
                    isSubmittingBooking = false,
                    bookingSuccess = true,
                    lastCreatedBooking = request
                )
            }
            _events.emit(SpaceDetailsUiEvent.BookingCreated(request))
            onSuccess(request)
        }
    }

    private fun recalculatePrice(
        space: SpaceListing,
        formula: RentalFormula?,
        subdivision: Subdivision?,
        strategy: RentalStrategy,
        selectedDaysCount: Int,
        durationMonths: Int
    ): Double {
        return SpaceCalculationUtils.calculateTotalRentalPrice(
            baseMonthlyRate = space.baseMonthlyRateUsd,
            formula = formula,
            subdivision = subdivision,
            selectedStrategyType = strategy,
            selectedDaysCount = selectedDaysCount,
            durationHours = 4.0,
            durationMonths = durationMonths
        )
    }
}
