package com.example.ui.screens

import com.example.ui.util.uiPlural
import com.example.ui.util.locationLine
import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.ui.theme.Radius
import com.example.ui.theme.Elevation
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
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.state.DiscoveryFilterState
import com.example.ui.state.PricingFormulaFilter
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.ProHostViewModel
import com.example.ui.theme.proHostScreenBackground
import com.example.ui.theme.Spacing

/**
 * ViewModel-connected wrapper for DiscoveryScreen.
 */
@Composable
fun DiscoveryScreen(
    viewModel: ProHostViewModel,
    onSelectSpace: (SpaceListing, String?) -> Unit,
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    // Explore has no app bar: its floating header carries the logo next to the Map | List toggle.
    showBrand: Boolean = false,
    // Pro Host / Admin: a ☰ control in the header opens their drawer.
    onOpenMenu: (() -> Unit)? = null,
    // A card's Availability button: the listing opens with its availability sheet up.
    onOpenAvailability: ((SpaceListing, String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by discoveryViewModel.uiState.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    val categoryOptions = remember(architectureSchema) {
        architectureSchema.spaceTypes.filter { it.isEnabled }.ifEmpty {
            SpaceType.values().map { SchemaItem(id = it.name, name = localizedLabel(it.displayName), category = "SPACE_TYPE") }
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
        },
        onMapCenterCountryDetected = { country ->
            discoveryViewModel.setMapCenterCountry(country)
        },
        hasMore = uiState.hasMore,
        isSearchingMore = uiState.isSearchingMore,
        onLoadMore = { discoveryViewModel.loadMore() },
        initialListIndex = discoveryViewModel.listScrollIndex,
        initialListOffset = discoveryViewModel.listScrollOffset,
        onListPositionSaved = { index, offset -> discoveryViewModel.saveListPosition(index, offset) },
        initialMapCamera = discoveryViewModel.mapCamera,
        onMapCameraSaved = { discoveryViewModel.saveMapCamera(it) },
        showBrand = showBrand,
        onOpenMenu = onOpenMenu,
        onOpenAvailability = onOpenAvailability?.let { open ->
            { space: SpaceListing, subId: String? ->
                com.example.analytics.AnalyticsTracker.selectItem(
                    if (uiState.isMapViewActive) "explore_map" else "explore_list",
                    space,
                    space.subdivisions.firstOrNull { it.id == subId },
                    uiState.filteredSpaces.indexOfFirst { it.id == space.id }.takeIf { it >= 0 }
                )
                open(space, subId)
            }
        },
        onMapGesture = { discoveryViewModel.setNavExpandedOnMap(false) }
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
    categoryOptions: List<SchemaItem> = SpaceType.values().map { SchemaItem(id = it.name, name = localizedLabel(it.displayName), category = "SPACE_TYPE") },
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
    onSelectStrategies: (Set<PricingFormulaFilter>) -> Unit,
    onPriceRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onToggleVerifiedOnly: (Boolean) -> Unit,
    onToggleSavedSpace: (String) -> Unit,
    onResetFilters: () -> Unit,
    onSelectSpace: (SpaceListing, String?) -> Unit,
    onQuickWhatsApp: (SpaceListing, Subdivision?) -> Unit,
    onMapCenterCountryDetected: (String) -> Unit = {},
    // Paging: more active listings may exist beyond the loaded page.
    hasMore: Boolean = false,
    isSearchingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    // Where Explore was before a listing was opened (restored on return).
    initialListIndex: Int = 0,
    initialListOffset: Int = 0,
    onListPositionSaved: (index: Int, offset: Int) -> Unit = { _, _ -> },
    initialMapCamera: com.google.android.gms.maps.model.CameraPosition? = null,
    onMapCameraSaved: (com.google.android.gms.maps.model.CameraPosition) -> Unit = {},
    showBrand: Boolean = false,
    onOpenMenu: (() -> Unit)? = null,
    onOpenAvailability: ((SpaceListing, String?) -> Unit)? = null,
    onMapGesture: () -> Unit = {}
) {
    val bottomNavInset = LocalBottomNavInset.current
    val exploreListState = androidx.compose.foundation.lazy.rememberLazyListState(initialListIndex, initialListOffset)
    DisposableEffect(exploreListState) {
        onDispose { onListPositionSaved(exploreListState.firstVisibleItemIndex, exploreListState.firstVisibleItemScrollOffset) }
    }
    var searchExpanded by rememberSaveable { mutableStateOf(filterState.query.isNotEmpty()) }
    // The keyboard opens only when the person taps search — not when Explore re-opens
    // with a search already applied (e.g. returning from a listing).
    var searchFocusRequested by remember { mutableStateOf(false) }
    val searchQuery = filterState.query

    Box(modifier = Modifier
        .fillMaxSize()
        .proHostScreenBackground()) {
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
                onCenterCountryDetected = { country -> onMapCenterCountryDetected(country) },
                onSearchArea = { if (hasMore) onLoadMore() },
                initialCamera = initialMapCamera,
                onCameraSaved = onMapCameraSaved,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                spaceTypeSchema = spaceTypeSchema,
                bottomInset = bottomNavInset,
                onUserGesture = onMapGesture,
                topControls = { searchAreaPill ->
                    ExploreOverlayHeader(
                        isMapView = true,
                        belowControls = searchAreaPill,
                        searchQuery = searchQuery,
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = { searchExpanded = it; searchFocusRequested = it },
                        focusSearchOnOpen = searchFocusRequested,
                        onSearchFocusConsumed = { searchFocusRequested = false },
                        activeFilterCount = filterState.activeFilterCount,
                        onToggleMapView = onToggleMapView,
                        onSearchQueryChange = onSearchQueryChange,
                        onOpenFilters = { onSetFilterSheetVisible(true) },
                        showBrand = showBrand,
                        onOpenMenu = onOpenMenu,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            )
        } else {
          // List view: the header sits in the layout above the list (same background), so
          // nothing scrolls underneath it.
          Column(modifier = Modifier.fillMaxSize()) {
            ExploreOverlayHeader(
                isMapView = false,
                searchQuery = searchQuery,
                searchExpanded = searchExpanded,
                onSearchExpandedChange = { searchExpanded = it; searchFocusRequested = it },
                focusSearchOnOpen = searchFocusRequested,
                onSearchFocusConsumed = { searchFocusRequested = false },
                activeFilterCount = filterState.activeFilterCount,
                onToggleMapView = onToggleMapView,
                onSearchQueryChange = onSearchQueryChange,
                onOpenFilters = { onSetFilterSheetVisible(true) },
                showBrand = showBrand,
                onOpenMenu = onOpenMenu
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // List View
            if (isLoading) {
                ShimmerLoadingList(modifier = Modifier.padding(top = 4.dp), itemHeight = 220.dp)
            } else if (loadError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = bottomNavInset)) {
                    ProEmptyState(
                        title = stringResource(R.string.couldn_t_load_workspaces),
                        description = loadError,
                        icon = Icons.Default.WifiOff,
                        actionButtonText = stringResource(R.string.retry),
                        onActionClick = onRetryLoad
                    )
                }
            } else if (spaces.isEmpty() && isSearchingMore) {
                // The whole catalog hasn't been searched yet — not a "no results" state.
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = bottomNavInset)) {
                    SearchingMoreRow()
                }
            } else if (spaces.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = bottomNavInset)) {
                    ProEmptyState(
                        title = stringResource(R.string.no_workspaces_found),
                        description = if (searchQuery.isNotBlank()) {
                            uiText(R.string.nothing_matches_x_with_the_current_filters, searchQuery.trim())
                        } else {
                            uiText(R.string.try_a_different_space_type_division_type_o)
                        },
                        icon = Icons.Default.SearchOff,
                        // Search and filters run over the loaded page: offer the next one
                        // before suggesting the person change their search.
                        actionButtonText = if (hasMore) stringResource(R.string.search_more_listings) else stringResource(R.string.reset_all_filters),
                        onActionClick = {
                            if (hasMore) {
                                onLoadMore()
                            } else {
                                onResetFilters()
                                onSearchQueryChange("")
                                searchExpanded = false
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        state = exploreListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + bottomNavInset),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
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
                                onAvailability = onOpenAvailability?.let { open -> { open(space, sub.id) } },
                                onQuickWhatsApp = { onQuickWhatsApp(space, sub) },
                                onToggleSave = { onToggleSavedSpace(space.id) }
                            )
                        } else {
                            SpaceListingCard(
                                space = space,
                                isSaved = savedSpaceIds.contains(space.id),
                                onClick = { onSelectSpace(space, null) },
                                onAvailability = onOpenAvailability?.let { open -> { open(space, null) } },
                                onQuickWhatsApp = { onQuickWhatsApp(space, null) },
                                onToggleSave = { onToggleSavedSpace(space.id) }
                            )
                        }
                    }
                    if (isSearchingMore) {
                        item(key = "searching_more") { SearchingMoreRow() }
                    }
                    // Paging footer: composing it (scrolled near the end) loads the next
                    // page automatically; the button covers a failed or slow load.
                    if (hasMore && !isSearchingMore) {
                        item(key = "load_more_footer") {
                            LaunchedEffect(listCards.size) { onLoadMore() }
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                TextButton(onClick = onLoadMore) {
                                    Text(stringResource(R.string.load_more_workspaces), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    }
                }
            }
            }
          }
        }
    }

    if (showFilterSheet) {
        com.example.ui.components.TrackScreen("explore_filters")
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
            onResetFilters = onResetFilters
        )
    }
}

/** Shown while Explore loads further pages for an active search (see DiscoveryViewModel). */
@Composable
private fun SearchingMoreRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            stringResource(R.string.searching_more_listings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formulaLabel(formula: PricingFormulaFilter): String =
    formula.strategy?.displayName ?: uiText(R.string.per_attendee)

/** The price unit a formula's range slider is in: per person for per-attendee rooms. */
private fun formulaUnitLabel(formula: PricingFormulaFilter): String =
    formula.strategy?.let { SpaceCalculationUtils.strategyUnitLabel(it) } ?: SpaceCalculationUtils.PER_PERSON_UNIT

/**
 * Explore's own header (specialists have no app bar), one row: search top-left, the Map | List toggle
 * top-centre with the brand logo wrapped in the same pill ([showBrand]; Pro Hosts and Admins keep the app
 * bar's brand, so they get the toggle alone) and filters top-right. There is no notification bell here:
 * notifications live in the app bar of the other tabs. On the map the row is transparent and each control
 * floats on its own surface; on the list it has the list's background and sits above it in the layout.
 * [belowControls] is the map's "Search this area".
 */
@Composable
private fun ExploreOverlayHeader(
    isMapView: Boolean,
    searchQuery: String,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    activeFilterCount: Int,
    onToggleMapView: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    showBrand: Boolean,
    modifier: Modifier = Modifier,
    onOpenMenu: (() -> Unit)? = null,
    focusSearchOnOpen: Boolean = false,
    onSearchFocusConsumed: () -> Unit = {},
    belowControls: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isMapView) Modifier else Modifier.proHostScreenBackground())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // One row: search · logo + Map|List · filters
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (onOpenMenu != null) {
                ExploreControlSurface(floating = isMapView, shape = CircleShape) {
                    IconButton(onClick = onOpenMenu, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.open_menu), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            BadgedControl(
                badge = if (searchQuery.isNotEmpty()) "" else null
            ) {
                ExploreControlSurface(floating = isMapView, shape = CircleShape) {
                    IconButton(onClick = { onSearchExpandedChange(!searchExpanded) }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = when {
                                searchExpanded -> uiText(R.string.close_search)
                                searchQuery.isNotEmpty() -> uiText(R.string.search_workspaces_x, searchQuery)
                                else -> uiText(R.string.search_workspaces)
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            }
            MapListToggle(
                isMapView = isMapView,
                floating = isMapView,
                onToggle = onToggleMapView,
                showBrand = showBrand,
                modifier = Modifier.align(Alignment.Center)
            )
            BadgedControl(
                badge = activeFilterCount.takeIf { it > 0 }?.toString(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                ExploreControlSurface(floating = isMapView, shape = CircleShape) {
                    IconButton(onClick = onOpenFilters, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = if (activeFilterCount > 0) stringResource(R.string.filters_x_active, activeFilterCount) else stringResource(R.string.filters),
                            tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // The search field opens on its own full-width row under the search button.
        androidx.compose.animation.AnimatedVisibility(visible = searchExpanded) {
            ExploreSearchField(
                query = searchQuery,
                floating = isMapView,
                requestFocus = focusSearchOnOpen,
                onFocusConsumed = onSearchFocusConsumed,
                onSubmit = onSearchQueryChange,
                onClose = {
                    onSearchQueryChange("")
                    onSearchExpandedChange(false)
                }
            )
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { belowControls() }
    }
}

/**
 * A control with a count (or dot, for "") badge drawn OUTSIDE its round surface, so two
 * digits are never clipped by the circle (a BadgedBox inside a clipped Surface was).
 */
@Composable
private fun BadgedControl(badge: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier) {
        content()
        if (badge != null) {
            val badgeModifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp)
            val count = badge.toIntOrNull()
            if (count != null) ProHostCountBadge(count, badgeModifier) else ProHostDotBadge(badgeModifier)
        }
    }
}

/** One Explore control: floats on its own surface over the map, flat on the list background. */
@Composable
private fun ExploreControlSurface(
    floating: Boolean,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Radius.Md,
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = if (floating) MaterialTheme.colorScheme.surface.copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface,
        shadowElevation = if (floating) Elevation.level3 else Elevation.level1,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier,
        content = content
    )
}

/** Segmented Map | List switch (active half filled with the brand colour), optionally led by the brand logo. */
@Composable
private fun MapListToggle(
    isMapView: Boolean,
    floating: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showBrand: Boolean = false
) {
    ExploreControlSurface(floating = floating, shape = CircleShape, modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = if (showBrand) 8.dp else 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (showBrand) {
                ProHostBrandLogo(size = 26.dp)
                Spacer(Modifier.width(Spacing.xs))
            }
            listOf(true to uiText(R.string.map), false to uiText(R.string.list)).forEach { (isMapOption, label) ->
                val selected = isMapOption == isMapView
                val container by androidx.compose.animation.animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "mapListToggle"
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(container)
                        .selectable(selected = selected, onClick = { if (!selected) onToggle() }, role = Role.Tab)
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Icon only; the label stays for TalkBack.
                    Icon(
                        if (isMapOption) Icons.Default.Map else Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = stringResource(R.string.x_view, label),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Expanded search: the text is a draft until ✓ (or the keyboard's Search key) applies it;
 * ✕ clears the search and closes the field.
 */
@Composable
private fun ExploreSearchField(
    query: String,
    floating: Boolean,
    requestFocus: Boolean,
    onFocusConsumed: () -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(query) { mutableStateOf(query) }
    val submit = {
        onSubmit(draft.trim())
        keyboard?.hide()
        focusManager.clearFocus()
    }
    // Opened by a tap: ready to type, no second tap needed. Restored on return: no keyboard.
    // One-shot: switching Map/List rebuilds this field and must not reopen the keyboard.
    LaunchedEffect(Unit) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocusConsumed()
        }
    }
    ExploreControlSurface(floating = floating, shape = Radius.Md, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp).size(18.dp)
            )
            Box(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp)) {
                if (draft.isEmpty()) {
                    Text(
                        stringResource(R.string.name_area_specialty_room_type),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_and_close_search), modifier = Modifier.size(18.dp))
            }
            FilledIconButton(
                onClick = { submit() },
                modifier = Modifier.padding(end = 4.dp).size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.search), modifier = Modifier.size(18.dp))
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
    onSelectStrategies: (Set<PricingFormulaFilter>) -> Unit,
    onPriceRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    onToggleVerifiedOnly: (Boolean) -> Unit,
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.filter_workspaces), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onResetFilters, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.reset), style = MaterialTheme.typography.labelMedium)
                }
            }

            MultiSelectDropdownField(
                label = stringResource(R.string.space_type),
                options = categoryOptions.map { it.id },
                selected = filterState.selectedCategoryIds,
                optionLabel = { id -> categoryOptions.firstOrNull { it.id == id }?.name ?: id },
                onSelectionChange = onSelectCategories,
                modifier = Modifier.fillMaxWidth()
            )

            MultiSelectDropdownField(
                label = stringResource(R.string.division_type),
                options = divisionTypeOptions,
                selected = filterState.selectedDivisionTypes,
                optionLabel = { localizedLabel(it.displayName) },
                onSelectionChange = onSelectDivisionTypes,
                modifier = Modifier.fillMaxWidth()
            )

            // Always offered: it used to hide unless listings spanned 2+ countries, which
            // (with legacy listings' blank country) meant it never appeared.
            MultiSelectDropdownField(
                label = stringResource(R.string.country),
                options = (availableCountries + filterState.selectedCountries).distinct().sorted(),
                selected = filterState.selectedCountries,
                optionLabel = { it },
                onSelectionChange = onSelectCountries,
                modifier = Modifier.fillMaxWidth()
            )

            MultiSelectDropdownField(
                label = stringResource(R.string.pricing_formula),
                options = PricingFormulaFilter.entries.toList(),
                selected = filterState.selectedStrategies,
                optionLabel = { "${formulaLabel(it)} (${formulaUnitLabel(it).removePrefix("/")})" },
                onSelectionChange = onSelectStrategies,
                modifier = Modifier.fillMaxWidth()
            )

            // Prices are only comparable within one unit, so the range opens once a
            // single formula is chosen.
            val singleStrategy = filterState.selectedStrategies.singleOrNull()
            if (singleStrategy != null && priceBounds != null) {
                val unit = formulaUnitLabel(singleStrategy)
                val current = (filterState.priceRange ?: priceBounds).let { range ->
                    range.start.coerceIn(priceBounds)..range.endInclusive.coerceIn(priceBounds)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.price_range), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
                    stringResource(R.string.choose_a_single_pricing_formula_to_filter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = filterState.onlyVerified,
                    onClick = { onToggleVerifiedOnly(!filterState.onlyVerified) },
                    leadingIcon = if (filterState.onlyVerified) {
                        { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text(stringResource(R.string.verified_only), style = MaterialTheme.typography.labelSmall) }
                )
            }

            Button(
                colors = proPrimaryButtonColors(),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(pluralStringResource(R.plurals.show_results, resultCount, resultCount))
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
,
    // Opens the listing with its availability sheet up; without it the button just opens the listing.
    onAvailability: (() -> Unit)? = null
) {
    // This card is one division, so it shows that division's own price and photo.
    val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestPriceForSubdivision(subdivision)
    WorkspaceCard(
        info = WorkspaceCardInfo(
            title = "${subdivision.name} · ${space.title}",
            listingType = localizedLabel(subdivision.type.displayName),
            location = space.locationLine(),
            rateUsd = lowestPrice.amount,
            rateUnit = lowestPrice.unitLabel,
            imageUrl = subdivision.imageUrls.firstOrNull() ?: space.imageUrls.firstOrNull(),
            operatingHours = "\u2066${space.schedule.openingHour} - ${space.schedule.closingHour}\u2069",
            totalDaysOpen = uiPlural(R.plurals.days_per_week, space.schedule.operatingDays.size, space.schedule.operatingDays.size),
            formulaTypes = listOf(localizedLabel(subdivision.pricing.strategyType.displayName)),
            isVerified = space.isVerified
        ),
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
        onDetailsClick = onAvailability ?: onClick,
        onWhatsAppClick = onQuickWhatsApp,
        detailsLabel = if (onAvailability != null) uiText(R.string.availability) else uiText(R.string.check_space),
        detailsIcon = if (onAvailability != null) Icons.Default.EventAvailable else Icons.Default.Visibility
    )
}

@Composable
fun SpaceListingCard(
    space: SpaceListing,
    onClick: () -> Unit,
    onQuickWhatsApp: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null
,
    // Opens the listing with its availability sheet up; without it the button just opens the listing.
    onAvailability: (() -> Unit)? = null
) {
    val formulaTypes = mutableSetOf<String>()
    formulaTypes.add(localizedLabel(space.pricing.strategyType.displayName))
    space.subdivisions.forEach { sub ->
        formulaTypes.add(localizedLabel(sub.pricing.strategyType.displayName))
    }

    val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)

    WorkspaceCard(
        info = WorkspaceCardInfo(
            title = space.title,
            listingType = localizedLabel(space.spaceType.displayName),
            location = space.locationLine(),
            rateUsd = lowestPrice.amount,
            rateUnit = lowestPrice.unitLabel,
            imageUrl = space.imageUrls.firstOrNull(),
            operatingHours = "\u2066${space.schedule.openingHour} - ${space.schedule.closingHour}\u2069",
            totalDaysOpen = uiPlural(R.plurals.days_per_week, space.schedule.operatingDays.size, space.schedule.operatingDays.size),
            formulaTypes = formulaTypes.toList(),
            isVerified = space.isVerified
        ),
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
        onDetailsClick = onAvailability ?: onClick,
        onWhatsAppClick = onQuickWhatsApp,
        detailsLabel = if (onAvailability != null) uiText(R.string.availability) else uiText(R.string.check_space),
        detailsIcon = if (onAvailability != null) Icons.Default.EventAvailable else Icons.Default.Visibility
    )
}