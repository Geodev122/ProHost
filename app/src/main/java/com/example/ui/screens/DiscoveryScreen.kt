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
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.premiumBackgroundBrush
import com.example.ui.theme.Spacing

/**
 * ViewModel-connected wrapper for DiscoveryScreen.
 */
@Composable
fun DiscoveryScreen(
    viewModel: ProHostViewModel,
    onSelectSpace: (SpaceListing, String?) -> Unit,
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    // Shown in Explore's own floating header when the shell has no app bar (specialists).
    unreadAlertCount: Int = 0,
    onAlertsClick: (() -> Unit)? = null
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
        unreadAlertCount = unreadAlertCount,
        onAlertsClick = onAlertsClick,
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
    unreadAlertCount: Int = 0,
    onAlertsClick: (() -> Unit)? = null,
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
                        unreadAlertCount = unreadAlertCount,
                        onAlertsClick = onAlertsClick,
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
                unreadAlertCount = unreadAlertCount,
                onAlertsClick = onAlertsClick
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // List View
            if (isLoading) {
                ShimmerLoadingList(modifier = Modifier.padding(top = 4.dp), itemHeight = 220.dp)
            } else if (loadError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = bottomNavInset)) {
                    ProEmptyState(
                        title = "Couldn't Load Workspaces",
                        description = loadError,
                        icon = Icons.Default.WifiOff,
                        actionButtonText = "Retry",
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
                        title = "No Workspaces Found",
                        description = if (searchQuery.isNotBlank()) {
                            "Nothing matches \"${searchQuery.trim()}\" with the current filters."
                        } else {
                            "Try a different space type, division type or pricing formula."
                        },
                        icon = Icons.Default.SearchOff,
                        // Search and filters run over the loaded page: offer the next one
                        // before suggesting the person change their search.
                        actionButtonText = if (hasMore) "Search More Listings" else "Reset All Filters",
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
                                    Text("Load more workspaces", style = MaterialTheme.typography.labelLarge)
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
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Searching more listings…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formulaLabel(formula: PricingFormulaFilter): String =
    formula.strategy?.displayName ?: "Per attendee"

/** The price unit a formula's range slider is in: per person for per-attendee rooms. */
private fun formulaUnitLabel(formula: PricingFormulaFilter): String =
    formula.strategy?.let { SpaceCalculationUtils.strategyUnitLabel(it) } ?: "/person"

/**
 * Explore's own header (specialists have no app bar): map/list toggle top-left, the brand
 * top-centre and notifications top-right ([onAlertsClick]; Pro Hosts, who keep the app bar,
 * pass null), then search under the toggle and filters under the bell. On the map the row is
 * transparent and each control floats on its own surface; on the list it has the list's
 * background and sits above it in the layout. [belowControls] is the map's "Search this area".
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
    unreadAlertCount: Int,
    onAlertsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    focusSearchOnOpen: Boolean = false,
    onSearchFocusConsumed: () -> Unit = {},
    belowControls: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isMapView) Modifier else Modifier.background(premiumBackgroundBrush()))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            MapListToggle(
                isMapView = isMapView,
                floating = isMapView,
                onToggle = onToggleMapView,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (onAlertsClick != null) {
                ExploreControlSurface(floating = isMapView, modifier = Modifier.align(Alignment.Center)) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProHostBrandLogo(size = 24.dp)
                        Text(
                            "ProHost",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                ExploreControlSurface(
                    floating = isMapView,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    BadgedBox(badge = {
                        if (unreadAlertCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(if (unreadAlertCount > 9) "9+" else "$unreadAlertCount")
                            }
                        }
                    }) {
                        IconButton(
                            onClick = onAlertsClick,
                            modifier = Modifier.size(44.dp).testTag("top_bar_notifications_button")
                        ) {
                            Icon(
                                if (unreadAlertCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = if (unreadAlertCount > 0) "Notifications, $unreadAlertCount unread" else "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = searchExpanded,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ExploreSearchToggle"
            ) { expanded ->
                if (!expanded) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        ExploreControlSurface(floating = isMapView, shape = CircleShape) {
                            BadgedBox(badge = {
                                if (searchQuery.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.error)
                            }) {
                                IconButton(onClick = { onSearchExpandedChange(true) }, modifier = Modifier.size(44.dp)) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = if (searchQuery.isNotEmpty()) "Search workspaces: $searchQuery" else "Search workspaces",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
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
            }
            ExploreControlSurface(floating = isMapView, shape = CircleShape) {
                BadgedBox(badge = {
                    if (activeFilterCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$activeFilterCount") }
                    }
                }) {
                    IconButton(onClick = onOpenFilters, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = if (activeFilterCount > 0) "Filters, $activeFilterCount active" else "Filters",
                            tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { belowControls() }
    }
}

/** One Explore control: floats on its own surface over the map, flat on the list background. */
@Composable
private fun ExploreControlSurface(
    floating: Boolean,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = if (floating) MaterialTheme.colorScheme.surface.copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface,
        shadowElevation = if (floating) 6.dp else 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier,
        content = content
    )
}

/** Segmented Map | List switch; the active half is filled with the brand colour. */
@Composable
private fun MapListToggle(
    isMapView: Boolean,
    floating: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExploreControlSurface(floating = floating, shape = CircleShape, modifier = modifier) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(true to "Map", false to "List").forEach { (isMapOption, label) ->
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
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (isMapOption) Icons.Default.Map else Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
    ExploreControlSurface(floating = floating, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
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
                        "Name, area, specialty, room type…",
                        style = MaterialTheme.typography.bodySmall,
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
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear and close search", modifier = Modifier.size(18.dp))
            }
            FilledIconButton(
                onClick = { submit() },
                modifier = Modifier.padding(end = 4.dp).size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "Search", modifier = Modifier.size(18.dp))
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

            // Always offered: it used to hide unless listings spanned 2+ countries, which
            // (with legacy listings' blank country) meant it never appeared.
            MultiSelectDropdownField(
                label = "Country",
                options = (availableCountries + filterState.selectedCountries).distinct().sorted(),
                selected = filterState.selectedCountries,
                optionLabel = { it },
                onSelectionChange = onSelectCountries,
                modifier = Modifier.fillMaxWidth()
            )

            MultiSelectDropdownField(
                label = "Pricing formula",
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