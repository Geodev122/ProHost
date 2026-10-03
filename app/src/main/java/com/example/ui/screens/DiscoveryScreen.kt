package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.state.DiscoveryFilterState
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.ProHostViewModel
import com.example.ui.theme.premiumBackgroundBrush
import com.example.ui.theme.Spacing

/**
 * ViewModel-connected wrapper for DiscoveryScreen.
 */
@Composable
fun DiscoveryScreen(
    viewModel: ProHostViewModel,
    onSelectSpace: (SpaceListing, String?) -> Unit,
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by discoveryViewModel.uiState.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    val categoryOptions = remember(architectureSchema) {
        architectureSchema.spaceTypes.filter { it.isEnabled }.ifEmpty {
            SpaceType.values().map { SchemaItem(id = it.name, name = it.displayName, category = "SPACE_TYPE") }
        }
    }

    DiscoveryScreenContent(
        spaces = uiState.filteredSpaces,
        filterState = uiState.filterState,
        categoryOptions = categoryOptions,
        divisionTypeOptions = uiState.availableDivisionTypes,
        availableCountries = uiState.availableCountries,
        priceBounds = uiState.priceBounds,
        matchingSubdivisionIds = uiState.matchingSubdivisionIds,
        savedSpaceIds = uiState.savedSpaceIds,
        isMapView = uiState.isMapViewActive,
        showFilterSheet = uiState.isFilterSheetVisible,
        isLoading = uiState.isLoading,
        loadError = uiState.loadError,
        onRetryLoad = { discoveryViewModel.retryLoad() },
        spaceTypeSchema = architectureSchema.spaceTypes,
        onSearchQueryChange = { discoveryViewModel.updateSearchQuery(it) },
        onToggleMapView = { discoveryViewModel.toggleMapView() },
        onSetFilterSheetVisible = { discoveryViewModel.setFilterSheetVisible(it) },
        onSelectCategories = { discoveryViewModel.setCategoryFilter(it) },
        onSelectDivisionTypes = { discoveryViewModel.setDivisionTypeFilter(it) },
        onSelectCountries = { discoveryViewModel.setCountryFilter(it) },
        onSelectStrategies = { discoveryViewModel.setFormulaFilter(it) },
        onPriceRangeChange = { discoveryViewModel.setPriceRange(it) },
        onToggleVerifiedOnly = { discoveryViewModel.toggleVerifiedOnly(it) },
        onToggleSavedOnly = { discoveryViewModel.toggleSavedOnly(it) },
        onToggleSavedSpace = { discoveryViewModel.toggleSavedSpace(it) },
        onResetFilters = { discoveryViewModel.resetFilters() },
        onSelectSpace = { space, subId ->
            com.example.analytics.AnalyticsTracker.selectItem(
                if (uiState.isMapViewActive) "explore_map" else "explore_list",
                space,
                space.subdivisions.firstOrNull { it.id == subId },
                uiState.filteredSpaces.indexOfFirst { it.id == space.id }.takeIf { it >= 0 }
            )
            onSelectSpace(space, subId)
        },
        onQuickWhatsApp = { space, subdivision ->
            viewModel.launchWhatsAppInquiry(context, space, subdivision = subdivision)
        }
    )
}

/**
 * Dumb Presentation Screen for Discovery & Search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreenContent(
    spaces: List<SpaceListing>,
    filterState: DiscoveryFilterState,
    categoryOptions: List<SchemaItem> = SpaceType.values().map { SchemaItem(id = it.name, name = it.displayName, category = "SPACE_TYPE") },
    divisionTypeOptions: List<Level2Type> = Level2Type.entries.toList(),
    availableCountries: List<String> = emptyList(),
    priceBounds: ClosedFloatingPointRange<Float>? = null,
    matchingSubdivisionIds: Map<String, Set<String>> = emptyMap(),
    savedSpaceIds: List<String>,
    isMapView: Boolean,
    showFilterSheet: Boolean,
    isLoading: Boolean = false,
    loadError: String? = null,
    onRetryLoad: () -> Unit = {},
    spaceTypeSchema: List<SchemaItem> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onToggleMapView: () -> Unit,
    onSetFilterSheetVisible: (Boolean) -> Unit,
    onSelectCategories: (Set<String>) -> Unit,
    onSelectDivisionTypes: (Set<Level2Type>) -> Unit,
    onSelectCountries: (Set<String>) -> Unit = {},
    onSelectStrategies: (Set<RentalStrategyType>) -> Unit,
    onPriceRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onToggleVerifiedOnly: (Boolean) -> Unit,
    onToggleSavedOnly: (Boolean) -> Unit,
    onToggleSavedSpace: (String) -> Unit,
    onResetFilters: () -> Unit,
    onSelectSpace: (SpaceListing, String?) -> Unit,
    onQuickWhatsApp: (SpaceListing, Subdivision?) -> Unit
) {
    val context = LocalContext.current
    var searchExpanded by rememberSaveable { mutableStateOf(filterState.query.isNotEmpty()) }
    val searchQuery = filterState.query
    var detectedCountryName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        detectedCountryName = com.example.util.PhoneCountryDetector.detectCountry(context).name
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(premiumBackgroundBrush())) {
        if (isMapView) {
            LebanonMapCanvas(
                spaces = spaces,
                // Only update the map's own local marker preview — never navigate here.
                // Navigation only happens from an explicit "Check Details" tap
                // (onNavigateToDetails) or an explicit division-card tap
                // (onDivisionSelected); a plain marker tap, cluster tap, or the
                // carousel settling from a scroll must never leave this screen.
                onSpaceSelected = {},
                onNavigateToDetails = { onSelectSpace(it, null) },
                onDivisionSelected = { space, subdivisionId -> onSelectSpace(space, subdivisionId) },
                modifier = Modifier.fillMaxSize().clipToBounds(),
                spaceTypeSchema = spaceTypeSchema,
                topControls = {
                    ExploreTopControls(
                        isMapView = true,
                        searchQuery = searchQuery,
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = { searchExpanded = it },
                        activeFilterCount = filterState.activeFilterCount,
                        onToggleMapView = onToggleMapView,
                        onSearchQueryChange = onSearchQueryChange,
                        onOpenFilters = { onSetFilterSheetVisible(true) },
                        listingCount = spaces.size,
                        countryName = detectedCountryName,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            )
        } else {
            // List View
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (loadError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 72.dp)) {
                    ProEmptyState(
                        title = "Couldn't Load Workspaces",
                        description = loadError,
                        icon = Icons.Default.WifiOff,
                        actionButtonText = "Retry",
                        onActionClick = onRetryLoad
                    )
                }
            } else if (spaces.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 72.dp)) {
                    ProEmptyState(
                        title = "No Workspaces Found",
                        description = if (searchQuery.isNotBlank()) {
                            "Nothing matches \"${searchQuery.trim()}\" with the current filters."
                        } else {
                            "Try a different space type, division type or pricing formula."
                        },
                        icon = Icons.Default.SearchOff,
                        actionButtonText = "Reset All Filters",
                        onActionClick = {
                            onResetFilters()
                            onSearchQueryChange("")
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    // Flatten: one card per (matching) subdivision, or one card for a
                    // whole-space listing.
                    val listCards = spaces.flatMap { space ->
                        if (space.subdivisions.isNotEmpty()) {
                            val visibleSubs = matchingSubdivisionIds[space.id]
                                ?.let { ids -> space.subdivisions.filter { it.id in ids } }
                                ?: space.subdivisions
                            visibleSubs.map { sub -> space to sub }
                        } else {
                            listOf(space to null)
                        }
                    }
                    items(listCards, key = { (space, sub) -> "${space.id}_${sub?.id ?: "whole"}" }) { (space, sub) ->
                        if (sub != null) {
                            SubdivisionDiscoveryCard(
                                space = space,
                                subdivision = sub,
                                isSaved = savedSpaceIds.contains(space.id),
                                onClick = { onSelectSpace(space, sub.id) },
                                onQuickWhatsApp = { onQuickWhatsApp(space, sub) },
                                onToggleSave = { onToggleSavedSpace(space.id) }
                            )
                        } else {
                            SpaceListingCard(
                                space = space,
                                isSaved = savedSpaceIds.contains(space.id),
                                onClick = { onSelectSpace(space, null) },
                                onQuickWhatsApp = { onQuickWhatsApp(space, null) },
                                onToggleSave = { onToggleSavedSpace(space.id) }
                            )
                        }
                    }
                    }
                }
            }
        }
        // Map view gets the same row through LebanonMapCanvas.topControls above.
        if (!isMapView) {
            ExploreTopControls(
                isMapView = false,
                searchQuery = searchQuery,
                searchExpanded = searchExpanded,
                onSearchExpandedChange = { searchExpanded = it },
                activeFilterCount = filterState.activeFilterCount,
                onToggleMapView = onToggleMapView,
                onSearchQueryChange = onSearchQueryChange,
                onOpenFilters = { onSetFilterSheetVisible(true) },
                listingCount = spaces.size,
                countryName = detectedCountryName,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }

    if (showFilterSheet) {
        DiscoveryFilterSheet(
            filterState = filterState,
            resultCount = spaces.size,
            categoryOptions = categoryOptions,
            divisionTypeOptions = divisionTypeOptions,
            availableCountries = availableCountries,
            priceBounds = priceBounds,
            onDismiss = { onSetFilterSheetVisible(false) },
            onSelectCategories = onSelectCategories,
            onSelectDivisionTypes = onSelectDivisionTypes,
            onSelectCountries = onSelectCountries,
            onSelectStrategies = onSelectStrategies,
            onPriceRangeChange = onPriceRangeChange,
            onToggleVerifiedOnly = onToggleVerifiedOnly,
            onToggleSavedOnly = onToggleSavedOnly,
            onResetFilters = onResetFilters
        )
    }
}

/** Unified explore strip: listing count chip + map/list toggle + search + filters. */
@Composable
private fun ExploreTopControls(
    isMapView: Boolean,
    searchQuery: String,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    activeFilterCount: Int,
    onToggleMapView: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    listingCount: Int = 0,
    countryName: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Listing count tag
        if (!searchExpanded) {
            val countLabel = buildString {
                append("$listingCount workspace${if (listingCount == 1) "" else "s"}")
                countryName?.let { append(" · $it") }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Controls strip
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Map / list toggle
                IconButton(
                    onClick = onToggleMapView,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (isMapView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Map,
                        contentDescription = if (isMapView) "Switch to List View" else "Switch to Map View",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Search (expandable) — takes all remaining space
                AnimatedContent(
                    targetState = searchExpanded,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ExploreSearchToggle"
                ) { expanded ->
                    if (!expanded) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BadgedBox(badge = {
                                if (searchQuery.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.error)
                            }) {
                                IconButton(
                                    onClick = { onSearchExpandedChange(true) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search workspaces",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        ExploreSearchField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onClose = {
                                onSearchQueryChange("")
                                onSearchExpandedChange(false)
                            }
                        )
                    }
                }

                // Filters button with badge
                BadgedBox(badge = {
                    if (activeFilterCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$activeFilterCount") }
                    }
                }) {
                    IconButton(
                        onClick = onOpenFilters,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeFilterCount > 0) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.onSecondary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Opening search should be ready to type, not need a second tap.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp).size(18.dp)
            )
            Box(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp)) {
                if (query.isEmpty()) {
                    Text(
                        "Name, area, specialty, room type…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = { keyboard?.hide(); onClose() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryFilterSheet(
    filterState: DiscoveryFilterState,
    resultCount: Int,
    categoryOptions: List<SchemaItem>,
    divisionTypeOptions: List<Level2Type>,
    availableCountries: List<String> = emptyList(),
    priceBounds: ClosedFloatingPointRange<Float>?,
    onDismiss: () -> Unit,
    onSelectCategories: (Set<String>) -> Unit,
    onSelectDivisionTypes: (Set<Level2Type>) -> Unit,
    onSelectCountries: (Set<String>) -> Unit = {},
    onSelectStrategies: (Set<RentalStrategyType>) -> Unit,
    onPriceRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onToggleVerifiedOnly: (Boolean) -> Unit,
    onToggleSavedOnly: (Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    ProHostBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter Workspaces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onResetFilters, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }
            }

            MultiSelectDropdownField(
                label = "Space type",
                options = categoryOptions.map { it.id },
                selected = filterState.selectedCategoryIds,
                optionLabel = { id -> categoryOptions.firstOrNull { it.id == id }?.name ?: id },
                onSelectionChange = onSelectCategories,
                modifier = Modifier.fillMaxWidth()
            )

            MultiSelectDropdownField(
                label = "Division type",
                options = divisionTypeOptions,
                selected = filterState.selectedDivisionTypes,
                optionLabel = { it.displayName },
                onSelectionChange = onSelectDivisionTypes,
                modifier = Modifier.fillMaxWidth()
            )

            if (availableCountries.size > 1) {
                MultiSelectDropdownField(
                    label = "Country",
                    options = availableCountries,
                    selected = filterState.selectedCountries,
                    optionLabel = { it },
                    onSelectionChange = onSelectCountries,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            MultiSelectDropdownField(
                label = "Pricing formula",
                options = RentalStrategyType.entries.toList(),
                selected = filterState.selectedStrategies,
                optionLabel = { "${it.displayName} (${SpaceCalculationUtils.strategyUnitLabel(it).removePrefix("/")})" },
                onSelectionChange = onSelectStrategies,
                modifier = Modifier.fillMaxWidth()
            )

            // Prices are only comparable within one unit, so the range opens once a
            // single formula is chosen.
            val singleStrategy = filterState.selectedStrategies.singleOrNull()
            if (singleStrategy != null && priceBounds != null) {
                val unit = SpaceCalculationUtils.strategyUnitLabel(singleStrategy)
                val current = (filterState.priceRange ?: priceBounds).let { range ->
                    range.start.coerceIn(priceBounds)..range.endInclusive.coerceIn(priceBounds)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Price range", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$${current.start.toInt()} – $${current.endInclusive.toInt()}$unit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RangeSlider(
                        value = current,
                        onValueChange = { onPriceRangeChange(if (it == priceBounds) null else it) },
                        valueRange = priceBounds,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (filterState.selectedStrategies.size > 1) {
                Text(
                    "Choose a single pricing formula to filter by price.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filterState.onlyVerified,
                    onClick = { onToggleVerifiedOnly(!filterState.onlyVerified) },
                    leadingIcon = if (filterState.onlyVerified) {
                        { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Verified only", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = filterState.onlySaved,
                    onClick = { onToggleSavedOnly(!filterState.onlySaved) },
                    leadingIcon = if (filterState.onlySaved) {
                        { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Saved only", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Show $resultCount Result${if (resultCount == 1) "" else "s"}")
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
fun SubdivisionDiscoveryCard(
    space: SpaceListing,
    subdivision: Subdivision,
    isSaved: Boolean = false,
    onClick: () -> Unit,
    onQuickWhatsApp: () -> Unit,
    onToggleSave: (() -> Unit)? = null
) {
    // This card is one division, so it shows that division's own price and photo.
    val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestPriceForSubdivision(subdivision)
    WorkspaceCard(
        info = WorkspaceCardInfo(
            title = "${subdivision.name} · ${space.title}",
            listingType = subdivision.type.displayName,
            location = "${space.district}, ${space.governorate.displayName}",
            rateUsd = lowestPrice.amount,
            rateUnit = lowestPrice.unitLabel,
            imageUrl = subdivision.imageUrls.firstOrNull() ?: space.imageUrls.firstOrNull(),
            operatingHours = "${space.schedule.openingHour} - ${space.schedule.closingHour}",
            totalDaysOpen = "${space.schedule.operatingDays.size} days/wk",
            formulaTypes = listOf(subdivision.pricing.strategyType.displayName),
            isVerified = space.isVerified
        ),
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
        onDetailsClick = onClick,
        onWhatsAppClick = onQuickWhatsApp
    )
}

@Composable
fun SpaceListingCard(
    space: SpaceListing,
    onClick: () -> Unit,
    onQuickWhatsApp: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null
) {
    val formulaTypes = mutableSetOf<String>()
    formulaTypes.add(space.pricing.strategyType.displayName)
    space.subdivisions.forEach { sub ->
        formulaTypes.add(sub.pricing.strategyType.displayName)
    }

    val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)

    WorkspaceCard(
        info = WorkspaceCardInfo(
            title = space.title,
            listingType = space.spaceType.displayName,
            location = "${space.district}, ${space.governorate.displayName}",
            rateUsd = lowestPrice.amount,
            rateUnit = lowestPrice.unitLabel,
            imageUrl = space.imageUrls.firstOrNull(),
            operatingHours = "${space.schedule.openingHour} - ${space.schedule.closingHour}",
            totalDaysOpen = "${space.schedule.operatingDays.size} days/wk",
            formulaTypes = formulaTypes.toList(),
            isVerified = space.isVerified
        ),
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
        onDetailsClick = onClick,
        onWhatsAppClick = onQuickWhatsApp
    )
}