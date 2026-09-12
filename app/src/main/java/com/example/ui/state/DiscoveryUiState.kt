package com.example.ui.state

import com.example.data.model.*

/**
 * Filter criteria state for space discovery.
 */
data class DiscoveryFilterState(
    val query: String = "",
    val selectedGovernorate: Governorate? = null,
    // A SchemaItem.id from the admin-defined Space Category catalog (or a legacy
    // SpaceType name from the empty-schema fallback) — see SpaceListing.matchesCategory.
    val selectedCategoryId: String? = null,
    val selectedFormulaType: RentalFormulaType? = null,
    val selectedFacility: String? = null,
    val selectedEquipmentCategory: EquipmentCategory? = null,
    val maxPriceUsd: Double = 1500.0,
    val onlyVerified: Boolean = false,
    val onlyActiveSubscribed: Boolean = true,
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
