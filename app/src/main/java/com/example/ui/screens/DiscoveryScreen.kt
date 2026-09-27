package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.state.DiscoveryUiState
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.ProHostViewModel
import com.example.ui.theme.PremiumBackgroundGradient
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
        searchQuery = uiState.filterState.query,
        selectedGovernorate = uiState.filterState.selectedGovernorate,
        categoryOptions = categoryOptions,
        selectedCategoryId = uiState.filterState.selectedCategoryId,
        selectedStrategyType = uiState.filterState.selectedStrategyType,
        onlyVerified = uiState.filterState.onlyVerified,
        onlySaved = uiState.filterState.onlySaved,
        savedSpaceIds = uiState.savedSpaceIds,
        isMapView = uiState.isMapViewActive,
        showFilterSheet = uiState.isFilterSheetVisible,
        isLoading = uiState.isLoading,
        spaceTypeSchema = architectureSchema.spaceTypes,
        onSearchQueryChange = { discoveryViewModel.updateSearchQuery(it) },
        onToggleMapView = { discoveryViewModel.toggleMapView() },
        onSetFilterSheetVisible = { discoveryViewModel.setFilterSheetVisible(it) },
        onSelectGovernorate = { discoveryViewModel.setGovernorateFilter(it) },
        onSelectCategory = { discoveryViewModel.setCategoryFilter(it) },
        onSelectStrategyType = { discoveryViewModel.setFormulaFilter(it) },
        onToggleVerifiedOnly = { discoveryViewModel.toggleVerifiedOnly(it) },
        onToggleSavedOnly = { discoveryViewModel.toggleSavedOnly(it) },
        onToggleSavedSpace = { discoveryViewModel.toggleSavedSpace(it) },
        onResetFilters = { discoveryViewModel.resetFilters() },
        onSelectSpace = onSelectSpace,
        onQuickWhatsApp = { space ->
            viewModel.launchWhatsAppInquiry(context, space, space.rentalFormulas.firstOrNull())
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
    searchQuery: String,
    selectedGovernorate: Governorate?,
    categoryOptions: List<SchemaItem> = SpaceType.values().map { SchemaItem(id = it.name, name = it.displayName, category = "SPACE_TYPE") },
    selectedCategoryId: String?,
    selectedStrategyType: RentalStrategyType?,
    onlyVerified: Boolean,
    onlySaved: Boolean,
    savedSpaceIds: List<String>,
    isMapView: Boolean,
    showFilterSheet: Boolean,
    isLoading: Boolean = false,
    spaceTypeSchema: List<SchemaItem> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onToggleMapView: () -> Unit,
    onSetFilterSheetVisible: (Boolean) -> Unit,
    onSelectGovernorate: (Governorate?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectStrategyType: (RentalStrategyType?) -> Unit,
    onToggleVerifiedOnly: (Boolean) -> Unit,
    onToggleSavedOnly: (Boolean) -> Unit,
    onToggleSavedSpace: (String) -> Unit,
    onResetFilters: () -> Unit,
    onSelectSpace: (SpaceListing, String?) -> Unit,
    onQuickWhatsApp: (SpaceListing) -> Unit
) {
    val context = LocalContext.current
    var searchExpanded by remember { mutableStateOf(false) }

    // Body Content: Map or List — with floating 3-button row overlaid (toggle | search | filter)
    val hasActiveFilter = selectedGovernorate != null ||
            selectedCategoryId != null ||
            selectedStrategyType != null ||
            onlyVerified ||
            onlySaved
    Box(modifier = Modifier
        .fillMaxSize()
        .background(PremiumBackgroundGradient)) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallFloatingActionButton(
                            onClick = onToggleMapView,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isMapView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Map,
                                contentDescription = if (isMapView) "Switch to List View" else "Switch to Map View"
                            )
                        }
                        AnimatedContent(
                            targetState = searchExpanded,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "SearchToggle"
                        ) { expanded ->
                            if (!expanded) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    BadgedBox(badge = {
                                        if (searchQuery.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.error)
                                    }) {
                                        SmallFloatingActionButton(
                                            onClick = { searchExpanded = true },
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.primary,
                                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = "Search workspaces")
                                        }
                                    }
                                }
                            } else {
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
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "Search workspaces…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = onSearchQueryChange,
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                                            )
                                        }
                                        IconButton(onClick = { onSearchQueryChange(""); searchExpanded = false }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        BadgedBox(badge = { if (hasActiveFilter) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
                            SmallFloatingActionButton(
                                onClick = { onSetFilterSheetVisible(true) },
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filters")
                            }
                        }
                    }
                }
            )
        } else {
            // List View
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (spaces.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 72.dp)) {
                    ProEmptyState(
                        title = "No Workspaces Found",
                        description = "Try adjusting your search query, governorate, or category filter.",
                        icon = Icons.Default.SearchOff,
                        actionButtonText = "Reset All Filters",
                        onActionClick = onResetFilters
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
                    item {
                        // One-line hero (Option B): the whole banner collapses to a
                        // single real-data statement instead of a tall image card plus
                        // a separate "Available Workspaces (N)" header below it — the
                        // count here already carries that role. The country named is
                        // whatever this device actually detects (SIM, then last-known
                        // location, then network, then locale — see
                        // PhoneCountryDetector), never a hardcoded "Lebanon": ProHost
                        // isn't Lebanon-only.
                        var detectedCountryName by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            detectedCountryName = com.example.util.PhoneCountryDetector.detectCountry(context).name
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${spaces.size} verified workspace${if (spaces.size == 1) "" else "s"}" +
                                        (detectedCountryName?.let { " in $it" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Flatten: one card per subdivision, or one space card for whole-space listings
                    val listCards = spaces.flatMap { space ->
                        if (space.subdivisions.isNotEmpty()) {
                            space.subdivisions.map { sub -> space to sub }
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
                                onQuickWhatsApp = { onQuickWhatsApp(space) },
                                onToggleSave = { onToggleSavedSpace(space.id) }
                            )
                        } else {
                            SpaceListingCard(
                                space = space,
                                isSaved = savedSpaceIds.contains(space.id),
                                onClick = { onSelectSpace(space, null) },
                                onQuickWhatsApp = { onQuickWhatsApp(space) },
                                onToggleSave = { onToggleSavedSpace(space.id) }
                            )
                        }
                    }
                    }
                }
            }
        }
        // Floating 3-button row for list view (map view gets it via LebanonMapCanvas.topControls)
        if (!isMapView) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallFloatingActionButton(
                    onClick = onToggleMapView,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Switch to Map View"
                    )
                }

                AnimatedContent(
                    targetState = searchExpanded,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SearchToggleList"
                ) { expanded ->
                    if (!expanded) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BadgedBox(badge = {
                                if (searchQuery.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.error)
                            }) {
                                SmallFloatingActionButton(
                                    onClick = { searchExpanded = true },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search workspaces")
                                }
                            }
                        }
                    } else {
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
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search workspaces…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = onSearchQueryChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                                IconButton(onClick = { onSearchQueryChange(""); searchExpanded = false }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                BadgedBox(badge = { if (hasActiveFilter) Badge(containerColor = MaterialTheme.colorScheme.error) }) {
                    SmallFloatingActionButton(
                        onClick = { onSetFilterSheetVisible(true) },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onSetFilterSheetVisible(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            scrimColor = Color.Black.copy(alpha = 0.35f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(28.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
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

                Text("Space Category", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryOptions.forEach { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelectCategory(if (selectedCategoryId == category.id) null else category.id) },
                            label = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Rental Formula", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RentalStrategyType.entries.forEach { strategy ->
                        FilterChip(
                            selected = selectedStrategyType == strategy,
                            onClick = { onSelectStrategyType(if (selectedStrategyType == strategy) null else strategy) },
                            label = { Text(strategy.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = onlyVerified,
                        onClick = { onToggleVerifiedOnly(!onlyVerified) },
                        leadingIcon = if (onlyVerified) {
                            { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text("Verified only", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = onlySaved,
                        onClick = { onToggleSavedOnly(!onlySaved) },
                        leadingIcon = if (onlySaved) {
                            { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text("Saved only", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                Button(
                    onClick = { onSetFilterSheetVisible(false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Apply Filters (${spaces.size} Results)")
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
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
    val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)
    WorkspaceCard(
        info = WorkspaceCardInfo(
            title = "${subdivision.name} · ${space.title}",
            listingType = subdivision.type.displayName,
            location = "${space.district}, ${space.governorate.displayName}",
            rateUsd = lowestPrice.amount,
            rateUnit = lowestPrice.unitLabel,
            imageUrl = space.imageUrls.firstOrNull(),
            operatingHours = "${space.schedule.openingHour} - ${space.schedule.closingHour}",
            totalDaysOpen = "${space.schedule.operatingDays.size} days/wk",
            formulaTypes = listOf(subdivision.pricing.strategyType.displayName),
            isVerified = space.isVerified
        ),
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
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