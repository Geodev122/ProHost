package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.SpaceListing
import com.example.ui.components.ProEmptyState
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.ProHostViewModel

/**
 * The saved-spaces list — AppUser.savedSpaceIds already persists correctly
 * (ProHostRepository.toggleSavedSpace, the heart icon on SpaceDetailsScreen/
 * DiscoveryScreen), but until this screen there was no way to ever see what had
 * been saved. Shared by both SPECIALIST and PRO_HOST (see the drawer entry in
 * AppDrawerContent.kt and its routing in ProHostNavGraph.kt) — a Pro Host is still
 * a Specialist underneath and can save/browse spaces the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFavoritesScreen(
    viewModel: ProHostViewModel,
    onSelectSpace: (SpaceListing) -> Unit,
    onNavigateToExplore: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()

    val savedIds = currentUser?.savedSpaceIds.orEmpty().toSet()
    val savedSpaces = allSpaces.filter { it.id in savedIds }
    // A saved listing that's since gone Paused/Draft, or been deleted, simply
    // has no matching entry in allSpaces — this used to silently drop it from
    // the list with no explanation, and the stale id stayed in savedSpaceIds
    // forever with no way to clean it up. Surfaced explicitly below instead,
    // with a Remove action that actually clears the stale id.
    val unavailableIds = savedIds - savedSpaces.map { it.id }.toSet()

    // Grouped by category (Private Office/Center/Polyclinic/Co-working, or any
    // admin-added category) rather than one flat list — the same
    // spaceCategoryName-with-legacy-fallback every other category-aware screen
    // in the app already reads (see OwnerAnalyticsScreen), so a listing under a
    // newly admin-added category groups correctly too, not just the original 4.
    val groupedSavedSpaces = savedSpaces
        .groupBy { it.spaceCategoryName ?: it.spaceType.displayName }
        .toSortedMap()

    Scaffold(
        // Nested inside the app-shell Scaffold's already-inset content area — its
        // default contentWindowInsets would otherwise re-apply the status-bar-height
        // top inset a second time, producing extra blank space above this TopAppBar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("My Favorites", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        if (savedSpaces.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${savedSpaces.size} Saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (savedSpaces.isEmpty() && unavailableIds.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ProEmptyState(
                    title = "No Favorites Yet",
                    description = "Tap the heart icon on any workspace to save it here for quick access later.",
                    icon = Icons.Filled.FavoriteBorder,
                    actionButtonText = "Explore Workspaces",
                    onActionClick = onNavigateToExplore
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                groupedSavedSpaces.forEach { (category, spacesInCategory) ->
                    item(key = "header_$category") {
                        Text(
                            text = "$category (${spacesInCategory.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (category == groupedSavedSpaces.firstKey()) 0.dp else Spacing.sm)
                        )
                    }
                    items(spacesInCategory, key = { it.id }) { space ->
                        SpaceListingCard(
                            space = space,
                            onClick = { onSelectSpace(space) },
                            onQuickWhatsApp = { viewModel.launchWhatsAppInquiry(context, space, null) },
                            isSaved = true,
                            onToggleSave = { viewModel.toggleSavedSpace(space.id) }
                        )
                    }
                }
                if (unavailableIds.isNotEmpty()) {
                    item(key = "header_unavailable") {
                        Text(
                            text = "No Longer Available (${unavailableIds.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (groupedSavedSpaces.isEmpty()) 0.dp else Spacing.sm)
                        )
                    }
                    items(unavailableIds.toList(), key = { it }) { spaceId ->
                        UnavailableFavoriteCard(
                            onRemove = { viewModel.toggleSavedSpace(spaceId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableFavoriteCard(onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No longer available",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "This saved listing was paused, unpublished, or removed by its host.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}
