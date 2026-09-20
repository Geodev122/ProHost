package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
    onSelectSpace: (SpaceListing) -> Unit,
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
    onSelectSpace: (SpaceListing) -> Unit,
    onQuickWhatsApp: (SpaceListing) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient)
    ) {
        // Top Search Bar & View Toggle Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search...", fontSize = MaterialTheme.typography.bodySmall.fontSize) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Map vs List Toggle Button
                    IconButton(
                        onClick = onToggleMapView,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isMapView) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (isMapView) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.Map,
                            contentDescription = "Toggle Map/List",
                            tint = if (isMapView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Filter Button with Badge
                    IconButton(
                        onClick = { onSetFilterSheetVisible(true) },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val hasActiveFilter = selectedGovernorate != null ||
                                selectedCategoryId != null ||
                                selectedStrategyType != null ||
                                onlyVerified ||
                                onlySaved
                        BadgedBox(
                            badge = {
                                if (hasActiveFilter) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
                        }
                    }
                }

            }
        }

        // Body Content: Map or List
        if (isMapView) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                LebanonMapCanvas(
                    spaces = spaces,
                    onSpaceSelected = { space -> if (space != null) onSelectSpace(space) },
                    onNavigateToDetails = { onSelectSpace(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // List View
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (spaces.isEmpty()) {
                ProEmptyState(
                    title = "No Workspaces Found",
                    description = "Try adjusting your search query, governorate, or category filter.",
                    icon = Icons.Default.SearchOff,
                    actionButtonText = "Reset All Filters",
                    onActionClick = onResetFilters
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(16.dp),
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

                    items(spaces, key = { it.id }) { space ->
                        SpaceListingCard(
                            space = space,
                            isSaved = savedSpaceIds.contains(space.id),
                            onClick = { onSelectSpace(space) },
                            onQuickWhatsApp = { onQuickWhatsApp(space) },
                            onToggleSave = { onToggleSavedSpace(space.id) }
                        )
                    }
                }
            }
        }
    }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { onSetFilterSheetVisible(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter Workspaces", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onResetFilters) {
                        Text("Reset")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text("Space Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categoryOptions) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelectCategory(if (selectedCategoryId == category.id) null else category.id) },
                            label = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text("Rental Formula", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RentalStrategyType.entries) { strategy ->
                        FilterChip(
                            selected = selectedStrategyType == strategy,
                            onClick = { onSelectStrategyType(if (selectedStrategyType == strategy) null else strategy) },
                            label = { Text(strategy.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Verified listings only", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = onlyVerified, onCheckedChange = onToggleVerifiedOnly)
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved workspaces only", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = onlySaved, onCheckedChange = onToggleSavedOnly)
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = { onSetFilterSheetVisible(false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Apply Filters (${spaces.size} Results)")
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
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
        title = space.title,
        listingType = space.spaceType.displayName,
        location = "${space.district}, ${space.governorate.displayName}",
        rateUsd = lowestPrice.amount,
        rateUnit = lowestPrice.unitLabel,
        imageUrl = space.imageUrls.firstOrNull(),
        operatingHours = "${space.schedule.openingHour} - ${space.schedule.closingHour}",
        totalDaysOpen = "${space.schedule.operatingDays.size} days/wk",
        formulaTypes = formulaTypes.toList(),
        isVerified = space.isVerified,
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
        onDetailsClick = onClick,
        onWhatsAppClick = onQuickWhatsApp
    )
}
