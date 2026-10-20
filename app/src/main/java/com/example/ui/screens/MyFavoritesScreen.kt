package com.example.ui.screens

import com.example.R
import androidx.compose.ui.res.stringResource
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
import com.example.ui.components.ShimmerLoadingList
import com.example.data.model.SpaceListing
import com.example.ui.components.ProEmptyState
import com.example.ui.components.ProHostAlertBanner
import com.example.ui.components.ProHostAlertSeverity
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
    val isRestoringSession by viewModel.isRestoringSession.collectAsState()
    val sessionError by viewModel.sessionRestoreError.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()
    // Show loading only while the session is still restoring AND no spaces have loaded yet.
    // Once spaces arrive, show the real content regardless of session state.
    val isLoading = isRestoringSession && allSpaces.isEmpty() && currentUser == null
    val errorMessage = if (!isLoading) sessionError else null

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        // Loading fades into the content instead of popping.
        androidx.compose.animation.Crossfade(targetState = isLoading, label = "savedLoading") { loadingNow ->
        if (loadingNow) {
            ShimmerLoadingList(modifier = Modifier.padding(innerPadding), itemHeight = 200.dp)
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Spacing.lg)) {
                ProHostAlertBanner(message = errorMessage ?: "", severity = ProHostAlertSeverity.ERROR)
            }
        } else if (savedSpaces.isEmpty() && unavailableIds.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ProEmptyState(
                    title = stringResource(R.string.nothing_saved_yet),
                    description = stringResource(R.string.tap_the_heart_on_any_space_to_keep_it_here),
                    icon = Icons.Filled.FavoriteBorder,
                    actionButtonText = stringResource(R.string.explore_spaces),
                    onActionClick = onNavigateToExplore
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                groupedSavedSpaces.forEach { (category, spacesInCategory) ->
                    item(key = "header_$category", contentType = "header") {
                        Text(
                            text = "$category (${spacesInCategory.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = if (category == groupedSavedSpaces.firstKey()) 0.dp else Spacing.sm)
                        )
                    }
                    items(spacesInCategory, key = { it.id }) { space ->
                        // Inserted/removed rows slide and fade instead of jumping.
                        Box(Modifier.animateItem()) {
                        SpaceListingCard(
                            space = space,
                            onClick = { onSelectSpace(space) },
                            onQuickWhatsApp = { viewModel.launchWhatsAppInquiry(context, space, null) },
                            isSaved = true,
                            onToggleSave = { viewModel.toggleSavedSpace(space.id, context) }
                        )
                    }
}
                }
                if (unavailableIds.isNotEmpty()) {
                    item(key = "header_unavailable") {
                        Text(
                            text = stringResource(R.string.no_longer_available_x, unavailableIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (groupedSavedSpaces.isEmpty()) 0.dp else Spacing.sm)
                        )
                    }
                    items(unavailableIds.toList(), key = { it }) { spaceId ->
                        // Inserted/removed rows slide and fade instead of jumping.
                        Box(Modifier.animateItem()) {
                        UnavailableFavoriteCard(
                            onRemove = { viewModel.toggleSavedSpace(spaceId, context) }
                        )
                    }
}
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                    text = stringResource(R.string.no_longer_available),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.this_saved_listing_was_paused_unpublished),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(shape = MaterialTheme.shapes.medium, onClick = onRemove) {
                Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}
