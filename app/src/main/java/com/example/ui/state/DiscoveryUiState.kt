package com.example.ui.state

import com.example.data.model.*

/**
 * Filter criteria state for space discovery.
 */
data class DiscoveryFilterState(
    val query: String = "",
    val selectedGovernorate: Governorate? = null,
    val selectedSpaceType: SpaceType? = null,
    val selectedFormulaType: RentalFormulaType? = null,
    val selectedFacility: String? = null,
    val selectedEquipmentCategory: EquipmentCategory? = null,
    val maxPriceUsd: Double = 1500.0,
    val onlyVerified: Boolean = false,
    val onlyActiveSubscribed: Boolean = true
)

/**
 * Immutable UI State for the Discovery & Search Screen.
 */
data class DiscoveryUiState(
    val allSpaces: List<SpaceListing> = emptyList(),
    val filteredSpaces: List<SpaceListing> = emptyList(),
    val filterState: DiscoveryFilterState = DiscoveryFilterState(),
    val selectedSpace: SpaceListing? = null,
    val isFilterSheetVisible: Boolean = false,
    val isMapViewActive: Boolean = false,
    val isLoading: Boolean = false,
    val fcmAlerts: List<FCMAlert> = emptyList()
)

/**
 * Events/Actions emitted by the Discovery UI.
 */
sealed interface DiscoveryUiEvent {
    data class SpaceSelected(val space: SpaceListing) : DiscoveryUiEvent
    data class ShowToast(val message: String) : DiscoveryUiEvent
    data class OpenWhatsApp(val url: String) : DiscoveryUiEvent
}
