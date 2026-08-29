package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.ui.state.DiscoveryFilterState
import com.example.ui.state.DiscoveryUiEvent
import com.example.ui.state.DiscoveryUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing space catalog discovery, multi-criteria filtering, search debounce, and FCM notification alerts.
 */
class DiscoveryViewModel(
    private val repository: ProSpaceRepository = ProSpaceRepository.getInstance()
) : ViewModel() {

    private val _filterState = MutableStateFlow(DiscoveryFilterState())
    val filterState: StateFlow<DiscoveryFilterState> = _filterState.asStateFlow()

    private val _selectedSpace = MutableStateFlow<SpaceListing?>(null)
    val selectedSpace: StateFlow<SpaceListing?> = _selectedSpace.asStateFlow()

    private val _isFilterSheetVisible = MutableStateFlow(false)
    val isFilterSheetVisible: StateFlow<Boolean> = _isFilterSheetVisible.asStateFlow()

    private val _isMapViewActive = MutableStateFlow(false)
    val isMapViewActive: StateFlow<Boolean> = _isMapViewActive.asStateFlow()

    private val _events = MutableSharedFlow<DiscoveryUiEvent>()
    val events: SharedFlow<DiscoveryUiEvent> = _events.asSharedFlow()

    // Combined UI State Flow
    val uiState: StateFlow<DiscoveryUiState> = combine(
        repository.spaces,
        _filterState,
        _selectedSpace,
        combine(_isFilterSheetVisible, _isMapViewActive, repository.fcmAlerts) { sheet, map, alerts ->
            Triple(sheet, map, alerts)
        }
    ) { spaces: List<SpaceListing>, filter: DiscoveryFilterState, selected: SpaceListing?, extra: Triple<Boolean, Boolean, List<FCMAlert>> ->
        val (sheetVisible, mapActive, alerts) = extra
        val filtered = spaces.filter { space ->
            val matchesQuery = filter.query.isBlank() ||
                    space.title.contains(filter.query, ignoreCase = true) ||
                    space.district.contains(filter.query, ignoreCase = true) ||
                    space.complementarySpecialties.any { it.contains(filter.query, ignoreCase = true) } ||
                    space.equipment.any { it.name.contains(filter.query, ignoreCase = true) } ||
                    space.spaceType.displayName.contains(filter.query, ignoreCase = true)

            val matchesGov = filter.selectedGovernorate == null || space.governorate == filter.selectedGovernorate
            val matchesType = filter.selectedSpaceType == null || space.spaceType == filter.selectedSpaceType
            val matchesFormula = filter.selectedFormulaType == null || space.rentalFormulas.any { it.type == filter.selectedFormulaType }
            val matchesFacility = filter.selectedFacility == null || space.essentialFacilities.contains(filter.selectedFacility)
            val matchesEquip = filter.selectedEquipmentCategory == null || space.equipment.any { it.category == filter.selectedEquipmentCategory }
            val matchesPrice = space.baseMonthlyRateUsd <= filter.maxPriceUsd
            val matchesVerified = !filter.onlyVerified || space.isVerified
            val matchesSub = !filter.onlyActiveSubscribed || space.isActiveSubscription

            matchesQuery && matchesGov && matchesType && matchesFormula && matchesFacility && matchesEquip && matchesPrice && matchesVerified && matchesSub
        }

        DiscoveryUiState(
            allSpaces = spaces,
            filteredSpaces = filtered,
            filterState = filter,
            selectedSpace = selected,
            isFilterSheetVisible = sheetVisible,
            isMapViewActive = mapActive,
            isLoading = false,
            fcmAlerts = alerts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscoveryUiState())

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun setGovernorateFilter(gov: Governorate?) {
        _filterState.update { it.copy(selectedGovernorate = gov) }
    }

    fun setSpaceTypeFilter(type: SpaceType?) {
        _filterState.update { it.copy(selectedSpaceType = type) }
    }

    fun setFormulaFilter(formula: RentalFormulaType?) {
        _filterState.update { it.copy(selectedFormulaType = formula) }
    }

    fun setFacilityFilter(facility: String?) {
        _filterState.update { it.copy(selectedFacility = facility) }
    }

    fun setEquipmentCategoryFilter(category: EquipmentCategory?) {
        _filterState.update { it.copy(selectedEquipmentCategory = category) }
    }

    fun setMaxPrice(price: Double) {
        _filterState.update { it.copy(maxPriceUsd = price) }
    }

    fun toggleVerifiedOnly(verifiedOnly: Boolean) {
        _filterState.update { it.copy(onlyVerified = verifiedOnly) }
    }

    fun resetFilters() {
        _filterState.value = DiscoveryFilterState()
    }

    fun selectSpace(space: SpaceListing?) {
        _selectedSpace.value = space
        if (space != null) {
            viewModelScope.launch {
                _events.emit(DiscoveryUiEvent.SpaceSelected(space))
            }
        }
    }

    fun setFilterSheetVisible(visible: Boolean) {
        _isFilterSheetVisible.value = visible
    }

    fun toggleMapView() {
        _isMapViewActive.value = !_isMapViewActive.value
    }

    fun markAlertAsRead(alertId: String) {
        repository.markAlertAsRead(alertId)
    }
}
