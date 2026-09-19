package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.ui.state.DiscoveryFilterState
import com.example.ui.state.DiscoveryUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel managing space catalog discovery, multi-criteria filtering, search debounce, and FCM notification alerts.
 */
class DiscoveryViewModel(
    private val repository: ProHostRepository = ProHostRepository.getInstance()
) : ViewModel() {

    private val _filterState = MutableStateFlow(DiscoveryFilterState())
    val filterState: StateFlow<DiscoveryFilterState> = _filterState.asStateFlow()

    private val _isFilterSheetVisible = MutableStateFlow(false)
    val isFilterSheetVisible: StateFlow<Boolean> = _isFilterSheetVisible.asStateFlow()

    private val _isMapViewActive = MutableStateFlow(false)
    val isMapViewActive: StateFlow<Boolean> = _isMapViewActive.asStateFlow()

    // Combined UI State Flow
    val uiState: StateFlow<DiscoveryUiState> = combine(
        repository.spaces,
        _filterState,
        _isFilterSheetVisible,
        _isMapViewActive,
        repository.currentUser,
        repository.hasLoadedSpacesOnce
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val spaces = values[0] as List<SpaceListing>
        val filter = values[1] as DiscoveryFilterState
        val sheetVisible = values[2] as Boolean
        val mapActive = values[3] as Boolean
        val user = values[4] as AppUser?
        val hasLoadedOnce = values[5] as Boolean
        val savedIds = user?.savedSpaceIds ?: emptyList()
        val filtered = spaces.filter { space ->
            val matchesQuery = filter.query.isBlank() ||
                    space.title.contains(filter.query, ignoreCase = true) ||
                    space.district.contains(filter.query, ignoreCase = true) ||
                    space.complementarySpecialties.any { it.contains(filter.query, ignoreCase = true) } ||
                    space.equipment.any { it.name.contains(filter.query, ignoreCase = true) } ||
                    space.spaceType.displayName.contains(filter.query, ignoreCase = true)

            val matchesGov = filter.selectedGovernorate == null || space.governorate == filter.selectedGovernorate
            val matchesType = space.matchesCategory(filter.selectedCategoryId)
            val matchesFormula = filter.selectedStrategyType == null ||
                    space.pricing.strategyType == filter.selectedStrategyType ||
                    space.subdivisions.any { it.pricing.strategyType == filter.selectedStrategyType }
            val matchesVerified = !filter.onlyVerified || space.isVerified
            val matchesSaved = !filter.onlySaved || savedIds.contains(space.id)

            // isOwnerPackageLapsed hides a listing from a fresh Discovery browse (the
            // host's package lapsed with no renewal — see expirePackages.ts) without
            // unpublishing it; a specialist who already has an ACCEPTED booking there
            // still reaches it via My Bookings, unaffected by this filter, and sees a
            // "host is in verification process" note (SpaceDetailsScreen).
            val isLiveListing = space.status == ListingStatus.ACTIVE && !space.isOwnerSuspended && !space.isOwnerPackageLapsed

            isLiveListing && matchesQuery && matchesGov && matchesType && matchesFormula && matchesVerified && matchesSaved
        }

        DiscoveryUiState(
            filteredSpaces = filtered,
            filterState = filter,
            isFilterSheetVisible = sheetVisible,
            isMapViewActive = mapActive,
            isLoading = !hasLoadedOnce,
            savedSpaceIds = savedIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscoveryUiState())

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun setGovernorateFilter(gov: Governorate?) {
        _filterState.update { it.copy(selectedGovernorate = gov) }
    }

    fun setCategoryFilter(categoryId: String?) {
        _filterState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun setFormulaFilter(strategy: RentalStrategyType?) {
        _filterState.update { it.copy(selectedStrategyType = strategy) }
    }

    fun toggleVerifiedOnly(verifiedOnly: Boolean) {
        _filterState.update { it.copy(onlyVerified = verifiedOnly) }
    }

    fun toggleSavedOnly(savedOnly: Boolean) {
        _filterState.update { it.copy(onlySaved = savedOnly) }
    }

    fun toggleSavedSpace(spaceId: String) {
        viewModelScope.launch {
            repository.toggleSavedSpace(spaceId)
        }
    }

    fun resetFilters() {
        _filterState.value = DiscoveryFilterState()
    }

    fun setFilterSheetVisible(visible: Boolean) {
        _isFilterSheetVisible.value = visible
    }

    fun toggleMapView() {
        _isMapViewActive.value = !_isMapViewActive.value
    }
}
