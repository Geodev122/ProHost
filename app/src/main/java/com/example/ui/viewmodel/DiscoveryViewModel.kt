package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.ui.state.DiscoveryFilterState
import com.example.ui.state.DiscoveryUiState
import com.example.ui.util.SpaceCalculationUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * ViewModel managing space catalog discovery, multi-criteria filtering and debounced search.
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

    // Typing updates filterState.query immediately (the text field stays responsive);
    // the list only re-filters once typing pauses.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedQuery: Flow<String> = _filterState
        .map { it.query.trim() }
        .distinctUntilChanged()
        .debounce(SEARCH_DEBOUNCE_MS)
        .onStart { emit(_filterState.value.query.trim()) }

    // Combined UI State Flow
    val uiState: StateFlow<DiscoveryUiState> = combine(
        repository.spaces,
        _filterState,
        _isFilterSheetVisible,
        _isMapViewActive,
        repository.currentUser,
        repository.hasLoadedSpacesOnce,
        debouncedQuery
    ) { values ->
        val spaces = (values[0] as? List<*>)?.filterIsInstance<SpaceListing>() ?: emptyList()
        val filter = values[1] as DiscoveryFilterState
        val sheetVisible = values[2] as Boolean
        val mapActive = values[3] as Boolean
        val user = values[4] as AppUser?
        val hasLoadedOnce = values[5] as Boolean
        val query = values[6] as String
        val savedIds = user?.savedSpaceIds ?: emptyList()

        // isOwnerPackageLapsed hides a listing from a fresh Discovery browse (the
        // host's package lapsed with no renewal — see expirePackages.ts) without
        // unpublishing it; a specialist who already has an ACCEPTED booking there
        // still reaches it via My Bookings, unaffected by this filter.
        val liveSpaces = spaces.filter {
            it.status == ListingStatus.ACTIVE && !it.isOwnerSuspended && !it.isOwnerPackageLapsed
        }
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val singleStrategy = filter.selectedStrategies.singleOrNull()
        val priceRange = filter.priceRange.takeIf { singleStrategy != null }
        val divisionLevelFilterActive = filter.selectedDivisionTypes.isNotEmpty() ||
            filter.selectedStrategies.isNotEmpty() || priceRange != null

        val matchingSubdivisionIds = mutableMapOf<String, Set<String>>()
        val filtered = liveSpaces.filter { space ->
            val matchesQuery = tokens.isEmpty() || searchText(space).let { text -> tokens.all { it in text } }
            val matchesType = filter.selectedCategoryIds.isEmpty() ||
                filter.selectedCategoryIds.any { space.matchesCategory(it) }
            val matchesVerified = !filter.onlyVerified || space.isVerified
            val matchesSaved = !filter.onlySaved || savedIds.contains(space.id)
            if (!(matchesQuery && matchesType && matchesVerified && matchesSaved)) return@filter false

            val matchingUnits = rentableUnits(space).filter { unit ->
                (filter.selectedDivisionTypes.isEmpty() || unit.divisionType in filter.selectedDivisionTypes) &&
                    (filter.selectedStrategies.isEmpty() || unit.strategy in filter.selectedStrategies) &&
                    (priceRange == null || (unit.price != null && unit.price.toFloat() in priceRange))
            }
            if (divisionLevelFilterActive && space.subdivisions.isNotEmpty()) {
                matchingSubdivisionIds[space.id] = matchingUnits.mapNotNull { it.subdivisionId }.toSet()
            }
            matchingUnits.isNotEmpty()
        }

        val priceBounds = singleStrategy?.let { strategy ->
            val prices = liveSpaces.flatMap { rentableUnits(it) }
                .filter { it.strategy == strategy }
                .mapNotNull { it.price?.toFloat() }
            if (prices.isEmpty()) null else {
                val min = kotlin.math.floor(prices.min())
                val max = kotlin.math.ceil(prices.max())
                min..(if (max > min) max else min + 1f)
            }
        }

        DiscoveryUiState(
            filteredSpaces = filtered,
            matchingSubdivisionIds = matchingSubdivisionIds,
            availableDivisionTypes = liveSpaces.flatMap { sp -> sp.subdivisions.map { it.type } }
                .distinct().sortedBy { it.ordinal }
                .ifEmpty { Level2Type.entries.toList() },
            priceBounds = priceBounds,
            filterState = filter,
            isFilterSheetVisible = sheetVisible,
            isMapViewActive = mapActive,
            isLoading = !hasLoadedOnce,
            savedSpaceIds = savedIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscoveryUiState())

    /** One bookable unit: a division, or the whole space when it has no divisions. */
    private data class RentableUnit(
        val subdivisionId: String?,
        val divisionType: Level2Type?,
        val strategy: RentalStrategyType,
        val price: Double?
    )

    private fun rentableUnits(space: SpaceListing): List<RentableUnit> =
        if (space.subdivisions.isNotEmpty()) {
            space.subdivisions.map { sub ->
                RentableUnit(sub.id, sub.type, sub.pricing.strategyType, SpaceCalculationUtils.lowestPriceFor(sub.pricing)?.amount)
            }
        } else {
            listOf(
                RentableUnit(
                    subdivisionId = null,
                    divisionType = null,
                    strategy = space.pricing.strategyType,
                    price = SpaceCalculationUtils.lowestPriceFor(space.pricing)?.amount
                        ?: space.baseMonthlyRateUsd.takeIf { it > 0.0 }
                )
            )
        }

    /** Everything a search token may match, lower-cased once per space. */
    private fun searchText(space: SpaceListing): String = buildList {
        add(space.title); add(space.description); add(space.district); add(space.city)
        add(space.streetAddress); add(space.governorate.displayName); add(space.spaceType.displayName)
        space.spaceCategoryName?.let { add(it) }
        addAll(space.complementarySpecialties)
        addAll(space.essentialFacilities)
        space.equipment.forEach { add(it.name) }
        space.subdivisions.forEach { sub ->
            add(sub.name); add(sub.type.displayName); addAll(sub.hashtags); addAll(sub.amenities)
            add(sub.pricing.strategyType.displayName)
        }
        add(space.pricing.strategyType.displayName)
    }.joinToString(" ").lowercase()

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun setCategoryFilter(categoryIds: Set<String>) {
        _filterState.update { it.copy(selectedCategoryIds = categoryIds) }
    }

    fun setDivisionTypeFilter(types: Set<Level2Type>) {
        _filterState.update { it.copy(selectedDivisionTypes = types) }
    }

    fun setFormulaFilter(strategies: Set<RentalStrategyType>) {
        // A price range is only meaningful within one formula's unit — drop it when
        // the formula selection changes.
        _filterState.update {
            it.copy(selectedStrategies = strategies, priceRange = if (strategies == it.selectedStrategies) it.priceRange else null)
        }
    }

    fun setPriceRange(range: ClosedFloatingPointRange<Float>?) {
        _filterState.update { it.copy(priceRange = range) }
    }

    fun toggleVerifiedOnly(verifiedOnly: Boolean) {
        _filterState.update { it.copy(onlyVerified = verifiedOnly) }
    }

    fun toggleSavedOnly(savedOnly: Boolean) {
        _filterState.update { it.copy(onlySaved = savedOnly) }
    }

    fun toggleSavedSpace(spaceId: String) {
        viewModelScope.launch {
            try {
                repository.toggleSavedSpace(spaceId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DiscoveryVM", "Operation failed", e)
            }
        }
    }

    fun resetFilters() {
        // Keep what the user typed — Reset is about the filter sheet.
        _filterState.update { DiscoveryFilterState(query = it.query) }
    }

    fun setFilterSheetVisible(visible: Boolean) {
        _isFilterSheetVisible.value = visible
    }

    fun toggleMapView() {
        _isMapViewActive.value = !_isMapViewActive.value
    }
}
