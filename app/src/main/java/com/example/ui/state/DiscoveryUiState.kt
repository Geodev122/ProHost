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
    // The real, fixed pricing-engine strategy (MONTHLY/HOURLY/SHIFT_BASED/DAY_BASED)
    // every listing/subdivision's RentalPricingConfig.strategyType actually carries —
    // unlike Space Category/Facility, this isn't an admin-open set (the schema's own
    // "Rental Formulas" section is decorative labels with no live mapping to it), so
    // it's matched directly against RentalStrategyType, not a SchemaItem catalog.
    val selectedStrategyType: RentalStrategyType? = null,
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
