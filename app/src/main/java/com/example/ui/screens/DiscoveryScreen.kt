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
import com.example.ui.viewmodel.ProSpaceViewModel
import com.example.ui.theme.PremiumBackgroundGradient

/**
 * ViewModel-connected wrapper for DiscoveryScreen — search/filter/selection state
 * now lives on the dedicated [DiscoveryViewModel] (Phase 4 ViewModel split), not
 * the shared [ProSpaceViewModel] god object. [viewModel] (the shared instance) is
 * still passed through for the one genuinely cross-cutting action this screen
 * needs — launching a WhatsApp inquiry, which also writes to the shared audit log.
 *
 * DiscoveryViewModel already existed in the repo before this change but was never
 * actually instantiated anywhere — ProSpaceViewModel had grown its own,
 * independently-maintained duplicate of the exact same search/filter logic
 * (SearchFilterState/filteredSpaces/updateSearchQuery/etc., now removed from
 * ProSpaceViewModel since this screen was their only real caller).
 */
@Composable
fun DiscoveryScreen(
    viewModel: ProSpaceViewModel,
    onSelectSpace: (SpaceListing) -> Unit,
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by discoveryViewModel.uiState.collectAsState()

    DiscoveryScreenContent(
        spaces = uiState.filteredSpaces,
        searchQuery = uiState.filterState.query,
        selectedGovernorate = uiState.filterState.selectedGovernorate,
        selectedSpaceType = uiState.filterState.selectedSpaceType,
        selectedFormulaType = uiState.filterState.selectedFormulaType,
        maxPriceUsd = uiState.filterState.maxPriceUsd,
        selectedSpace = uiState.selectedSpace,
        isMapView = uiState.isMapViewActive,
        showFilterSheet = uiState.isFilterSheetVisible,
        onSearchQueryChange = { discoveryViewModel.updateSearchQuery(it) },
        onToggleMapView = { discoveryViewModel.toggleMapView() },
        onSetFilterSheetVisible = { discoveryViewModel.setFilterSheetVisible(it) },
        onSelectGovernorate = { discoveryViewModel.setGovernorateFilter(it) },
        onSelectSpaceType = { discoveryViewModel.setSpaceTypeFilter(it) },
        onSelectFormulaType = { discoveryViewModel.setFormulaFilter(it) },
        onSetMaxPrice = { discoveryViewModel.setMaxPrice(it) },
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
    selectedSpaceType: SpaceType?,
    selectedFormulaType: RentalFormulaType?,
    maxPriceUsd: Double,
    selectedSpace: SpaceListing?,
    isMapView: Boolean,
    showFilterSheet: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleMapView: () -> Unit,
    onSetFilterSheetVisible: (Boolean) -> Unit,
    onSelectGovernorate: (Governorate?) -> Unit,
    onSelectSpaceType: (SpaceType?) -> Unit,
    onSelectFormulaType: (RentalFormulaType?) -> Unit,
    onSetMaxPrice: (Double) -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search workspaces, studios, offices, districts...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
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
                            .clip(RoundedCornerShape(12.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val hasActiveFilter = selectedGovernorate != null ||
                                selectedSpaceType != null ||
                                selectedFormulaType != null ||
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

                Spacer(modifier = Modifier.height(10.dp))

                // Governorate Filter Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedGovernorate == null,
                            onClick = { onSelectGovernorate(null) },
                            label = { Text("All Lebanon", fontSize = 11.sp) }
                        )
                    }
                    items(Governorate.entries) { gov ->
                        FilterChip(
                            selected = selectedGovernorate == gov,
                            onClick = { onSelectGovernorate(if (selectedGovernorate == gov) null else gov) },
                            label = { Text(gov.displayName.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Body Content: Map or List
        if (isMapView) {
            LebanonMapCanvas(
                spaces = spaces,
                selectedSpace = selectedSpace,
                onSpaceSelected = { space -> if (space != null) onSelectSpace(space) },
                onNavigateToDetails = { onSelectSpace(it) },
                modifier = Modifier.fillMaxSize()
            )
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
                                    contentDescription = "ProSpace Workspaces Banner",
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
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ProSpaceCedarBadge(text = "Lebanon Verified Network", isCompact = true)
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
                                        fontSize = 12.sp,
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
                            ProSpaceCedarBadge(text = "Verified Listings", isCompact = false)
                        }
                    }

                    items(spaces, key = { it.id }) { space ->
                        SpaceListingCard(
                            space = space,
                            onClick = { onSelectSpace(space) },
                            onQuickWhatsApp = { onQuickWhatsApp(space) }
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
                    Text("Filter Workspaces", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onResetFilters) {
                        Text("Reset")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Space Type", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SpaceType.entries) { type ->
                        FilterChip(
                            selected = selectedSpaceType == type,
                            onClick = { onSelectSpaceType(if (selectedSpaceType == type) null else type) },
                            label = { Text(type.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Rental Formula", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RentalFormulaType.entries) { formula ->
                        FilterChip(
                            selected = selectedFormulaType == formula,
                            onClick = { onSelectFormulaType(if (selectedFormulaType == formula) null else formula) },
                            label = { Text(formula.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Maximum Monthly Rate: $${maxPriceUsd.toInt()} USD",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Slider(
                    value = maxPriceUsd.toFloat(),
                    onValueChange = { onSetMaxPrice(it.toDouble()) },
                    valueRange = 100f..1500f,
                    steps = 14
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSetFilterSheetVisible(false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
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
    onQuickWhatsApp: () -> Unit
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
        onClick = onClick,
        onWhatsAppClick = onQuickWhatsApp
    )
}

