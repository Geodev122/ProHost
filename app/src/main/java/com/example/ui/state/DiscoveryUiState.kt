package com.example.ui.state

import com.example.data.model.*

/**
 * Filter criteria state for space discovery.
 */
data class DiscoveryFilterState(
    val query: String = "",
    val selectedGovernorate: Governorate? = null,
    val selectedCategoryId: String? = null,
    val selectedStrategyType: RentalStrategyType? = null,
    val onlyVerified: Boolean = false,
    val onlySaved: Boolean = false
)

/**
 * Immutable UI State for the Discovery & Search Screen.
 */
data class DiscoveryUiState(
    val filteredSpaces: List<SpaceListing> = emptyList(),
    val filterState: DiscoveryFilterState = DiscoveryFilterState(),
    val isFilterSheetVisible: Boolean = false,
    val isMapViewActive: Boolean = false,
    val isLoading: Boolean = false,
    val savedSpaceIds: List<String> = emptyList()
)
