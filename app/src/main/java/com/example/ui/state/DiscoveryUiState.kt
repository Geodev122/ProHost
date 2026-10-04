package com.example.ui.state

import com.example.data.model.*

/**
 * One "Pricing formula" filter option: a rental strategy, or per-attendee pricing. A
 * per-attendee room is offered on an Hourly/Shift/Day schedule but priced per person
 * (ui/util/AttendeePricing.kt), so it matches [PER_ATTENDEE] and never a strategy's unit.
 */
enum class PricingFormulaFilter(val strategy: RentalStrategyType?) {
    MONTHLY(RentalStrategyType.MONTHLY),
    HOURLY(RentalStrategyType.HOURLY),
    SHIFT_BASED(RentalStrategyType.SHIFT_BASED),
    DAY_BASED(RentalStrategyType.DAY_BASED),
    PER_ATTENDEE(null);

    companion object {
        fun of(strategy: RentalStrategyType): PricingFormulaFilter = entries.first { it.strategy == strategy }
    }
}

/**
 * Filter criteria for space discovery. Every list is multi-select and means "any of";
 * an empty set means no restriction. [priceRange] only applies while exactly one
 * pricing formula is selected, because prices are only comparable within one unit.
 */
data class DiscoveryFilterState(
    val query: String = "",
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedDivisionTypes: Set<Level2Type> = emptySet(),
    val selectedStrategies: Set<PricingFormulaFilter> = emptySet(),
    val priceRange: ClosedFloatingPointRange<Float>? = null,
    val onlyVerified: Boolean = false,
    val selectedCountries: Set<String> = emptySet()
) {
    val activeFilterCount: Int
        get() = listOf(
            selectedCategoryIds.isNotEmpty(),
            selectedDivisionTypes.isNotEmpty(),
            selectedStrategies.isNotEmpty(),
            priceRange != null,
            onlyVerified,
            selectedCountries.isNotEmpty()
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
    // Distinct country names across all live listings (blank = Lebanon), for the country filter.
    val availableCountries: List<String> = emptyList(),
    // Min..max price across live listings for the single selected formula, else null.
    val priceBounds: ClosedFloatingPointRange<Float>? = null,
    val filterState: DiscoveryFilterState = DiscoveryFilterState(),
    val isFilterSheetVisible: Boolean = false,
    val isMapViewActive: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val savedSpaceIds: List<String> = emptyList(),
    // Explore loads active listings a page at a time; true when another page may exist.
    val hasMore: Boolean = false,
    // Live listings loaded so far (search and filters run over these).
    val loadedListingCount: Int = 0
)
