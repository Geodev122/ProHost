package com.example.ui.state

import com.example.data.model.*

/**
 * Filter criteria for space discovery. Every list is multi-select and means "any of";
 * an empty set means no restriction. [priceRange] only applies while exactly one
 * pricing formula is selected, because prices are only comparable within one unit.
 */
data class DiscoveryFilterState(
    val query: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedDivisionTypes: Set<Level2Type> = emptySet(),
    val selectedStrategies: Set<RentalStrategyType> = emptySet(),
    val priceRange: ClosedFloatingPointRange<Float>? = null,
    val onlyVerified: Boolean = false,
    val onlySaved: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOf(
            selectedCategoryIds.isNotEmpty(),
            selectedDivisionTypes.isNotEmpty(),
            selectedStrategies.isNotEmpty(),
            priceRange != null,
            onlyVerified,
            onlySaved
        ).count { it }
}

/**
 * Immutable UI State for the Discovery & Search Screen.
 */
data class DiscoveryUiState(
    val filteredSpaces: List<SpaceListing> = emptyList(),
    // spaceId → the divisions that matched the division-level filters. A space missing
    // from the map shows all its divisions (no division-level filter is active).
    val matchingSubdivisionIds: Map<String, Set<String>> = emptyMap(),
    val availableDivisionTypes: List<Level2Type> = emptyList(),
    // Min..max price across live listings for the single selected formula, else null.
    val priceBounds: ClosedFloatingPointRange<Float>? = null,
    val filterState: DiscoveryFilterState = DiscoveryFilterState(),
    val isFilterSheetVisible: Boolean = false,
    val isMapViewActive: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val savedSpaceIds: List<String> = emptyList()
)
