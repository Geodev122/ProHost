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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
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
 * ViewModel-connected wrapper for DiscoveryScreen — search/filter/selection state
 * now lives on the dedicated [DiscoveryViewModel] (Phase 4 ViewModel split), not
 * the shared [ProHostViewModel] god object. [viewModel] (the shared instance) is
 * still passed through for the one genuinely cross-cutting action this screen
 * needs — launching a WhatsApp inquiry, which also writes to the shared audit log.
 *
 * DiscoveryViewModel already existed in the repo before this change but was never
 * actually instantiated anywhere — ProHostViewModel had grown its own,
 * independently-maintained duplicate of the exact same search/filter logic
 * (SearchFilterState/filteredSpaces/updateSearchQuery/etc., now removed from
 * ProHostViewModel since this screen was their only real caller).
 */
@Composable
fun DiscoveryScreen(
    viewModel: ProHostViewModel,
    onSelectSpace: (SpaceListing) -> Unit,
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by discoveryViewModel.uiState.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    val availableFacilities = remember(architectureSchema) {
        architectureSchema.amenities.filter { it.isEnabled }.map { it.name }
            .ifEmpty { FacilityCatalog.standard }
    }
    // Same catalog + empty-schema fallback CreateListingDialog's picker uses, so
    // what a host could publish under and what a specialist can filter by agree.
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
        availableFacilities = availableFacilities,
        selectedFacility = uiState.filterState.selectedFacility,
        selectedEquipmentCategory = uiState.filterState.selectedEquipmentCategory,
        maxPriceUsd = uiState.filterState.maxPriceUsd,
        onlyVerified = uiState.filterState.onlyVerified,
        onlySaved = uiState.filterState.onlySaved,
        savedSpaceIds = uiState.savedSpaceIds,
        isMapView = uiState.isMapViewActive,
        showFilterSheet = uiState.isFilterSheetVisible,
        onSearchQueryChange = { discoveryViewModel.updateSearchQuery(it) },
        onToggleMapView = { discoveryViewModel.toggleMapView() },
        onSetFilterSheetVisible = { discoveryViewModel.setFilterSheetVisible(it) },
        onSelectGovernorate = { discoveryViewModel.setGovernorateFilter(it) },
        onSelectCategory = { discoveryViewModel.setCategoryFilter(it) },
        onSelectStrategyType = { discoveryViewModel.setFormulaFilter(it) },
        onSelectFacility = { discoveryViewModel.setFacilityFilter(it) },
        onSelectEquipmentCategory = { discoveryViewModel.setEquipmentCategoryFilter(it) },
        onSetMaxPrice = { discoveryViewModel.setMaxPrice(it) },
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
    // Admin-defined Space Category catalog (enabled SchemaItems, category
    // "SPACE_TYPE"); defaults to the four legacy types for any caller not yet
    // passing the live list.
    categoryOptions: List<SchemaItem> = SpaceType.values().map { SchemaItem(id = it.name, name = it.displayName, category = "SPACE_TYPE") },
    selectedCategoryId: String?,
    selectedStrategyType: RentalStrategyType?,
    selectedFacility: String?,
    // Admin-managed facility catalog (enabled SchemaItems, category "AMENITY") —
    // defaults to the old hardcoded FacilityCatalog.standard only so a caller that
    // hasn't been updated to pass the live list doesn't lose facilities entirely.
    availableFacilities: List<String> = FacilityCatalog.standard,
    selectedEquipmentCategory: EquipmentCategory?,
    maxPriceUsd: Double,
    onlyVerified: Boolean,
    onlySaved: Boolean,
    savedSpaceIds: List<String>,
    isMapView: Boolean,
    showFilterSheet: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleMapView: () -> Unit,
    onSetFilterSheetVisible: (Boolean) -> Unit,
    onSelectGovernorate: (Governorate?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectStrategyType: (RentalStrategyType?) -> Unit,
    onSelectFacility: (String?) -> Unit,
    onSelectEquipmentCategory: (EquipmentCategory?) -> Unit,
    onSetMaxPrice: (Double) -> Unit,
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
                                selectedFacility != null ||
                                selectedEquipmentCategory != null ||
                                onlyVerified ||
                                onlySaved ||
                                maxPriceUsd < 1500.0
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

                if (!isMapView) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Governorate Filter Chips Row (List View only)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGovernorate == null,
                                onClick = { onSelectGovernorate(null) },
                                label = { Text("All Lebanon", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            )
                        }
                        items(Governorate.entries) { gov ->
                            FilterChip(
                                selected = selectedGovernorate == gov,
                                onClick = { onSelectGovernorate(if (selectedGovernorate == gov) null else gov) },
                                label = { Text(gov.displayName.split(" ").first(), fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            )
                        }
                    }
                }
            }
        }

        // Body Content: Map or List
        if (isMapView) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                LebanonMapCanvas(
                    spaces = spaces,
                    onSpaceSelected = { space -> if (space != null) onSelectSpace(space) },
                    onNavigateToDetails = { onSelectSpace(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // List View
            if (spaces.isEmpty()) {
                ProEmptyState(
                    title = "No Workspaces Found",
                    description = "Try adjusting your search query, governorate, or pricing filter.",
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
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                                .shadow(4.dp, RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.img_discovery_hero),
                                    contentDescription = "ProHost Workspaces Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Soft dark gradient overlap to make typography legible
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.42f))
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ProHostCedarBadge(text = "Lebanon Verified Network", isCompact = true)
                                    }
                                    Text(
                                        text = "Specialist Workspace Exchange",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Co-share modern offices, consulting clinics & studios with flexible Whish settlement",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        color = Color.White.copy(alpha = 0.92f)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Available Workspaces (${spaces.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ProHostCedarBadge(text = "Verified Listings", isCompact = false)
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    Text("Filter Workspaces", fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onResetFilters) {
                        Text("Reset")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text("Space Category", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categoryOptions) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelectCategory(if (selectedCategoryId == category.id) null else category.id) },
                            label = { Text(category.name, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text("Rental Formula", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RentalStrategyType.entries) { strategy ->
                        FilterChip(
                            selected = selectedStrategyType == strategy,
                            onClick = { onSelectStrategyType(if (selectedStrategyType == strategy) null else strategy) },
                            label = { Text(strategy.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text("Facility", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableFacilities) { facility ->
                        FilterChip(
                            selected = selectedFacility == facility,
                            onClick = { onSelectFacility(if (selectedFacility == facility) null else facility) },
                            label = { Text(facility, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text("Equipment Category", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EquipmentCategory.entries) { category ->
                        FilterChip(
                            selected = selectedEquipmentCategory == category,
                            onClick = { onSelectEquipmentCategory(if (selectedEquipmentCategory == category) null else category) },
                            label = { Text(category.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Verified listings only", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    Switch(checked = onlyVerified, onCheckedChange = onToggleVerifiedOnly)
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved workspaces only", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    Switch(checked = onlySaved, onCheckedChange = onToggleSavedOnly)
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Maximum Monthly Rate: $${maxPriceUsd.toInt()} USD",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Slider(
                    value = maxPriceUsd.toFloat(),
                    onValueChange = { onSetMaxPrice(it.toDouble()) },
                    valueRange = 100f..1500f,
                    steps = 14
                )

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
    WorkspaceCard(
        title = space.title,
        specialization = space.spaceType.displayName,
        location = "${space.district}, ${space.governorate.displayName}",
        rateUsd = space.baseMonthlyRateUsd,
        imageUrl = space.imageUrls.firstOrNull(),
        scheduleSummary = "${space.schedule.openingHour} - ${space.schedule.closingHour} (${space.schedule.operatingDays.size}d)",
        doctorName = space.ownerName,
        practiceType = if (space.isShared) "Shared Space" else "Private Space",
        isVerified = space.isVerified,
        bookedDoctorCount = space.residentPractitioners.size,
        facilities = space.essentialFacilities,
        isSaved = isSaved,
        onToggleSave = onToggleSave,
        onClick = onClick,
        onWhatsAppClick = onQuickWhatsApp
    )
}

