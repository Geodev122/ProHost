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
    onSelectSpace: (SpaceListing) -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()

    val savedIds = currentUser?.savedSpaceIds.orEmpty().toSet()
    val savedSpaces = allSpaces.filter { it.id in savedIds }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Favorites", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) })
        }
    ) { innerPadding ->
        if (savedSpaces.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ProEmptyState(
                    title = "No Favorites Yet",
                    description = "Tap the heart icon on any workspace to save it here for quick access later.",
                    icon = Icons.Filled.FavoriteBorder
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
                items(savedSpaces, key = { it.id }) { space ->
                    SpaceListingCard(
                        space = space,
                        onClick = { onSelectSpace(space) },
                        onQuickWhatsApp = { viewModel.launchWhatsAppInquiry(context, space, null) },
                        isSaved = true,
                        onToggleSave = { viewModel.toggleSavedSpace(space.id) }
                    )
                }
            }
        }
    }
}
