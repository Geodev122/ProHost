package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.ui.components.*
import androidx.compose.foundation.BorderStroke
import com.example.ui.state.AdminUiEvent
import com.example.ui.state.AdminUiState
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    viewModel: ProHostViewModel,
    adminViewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by adminViewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    var showListingFilterMenu by remember { mutableStateOf(false) }

    // Listen to admin events (Toasts)
    LaunchedEffect(adminViewModel) {
        adminViewModel.events.collectLatest { event ->
            when (event) {
                is AdminUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is AdminUiEvent.PricingUpdated -> {
                    Toast.makeText(context, "Subscription fee updated to $${event.newFee} USD", Toast.LENGTH_SHORT).show()
                }
                is AdminUiEvent.DataExportReady -> {
                    Toast.makeText(context, "${event.title} ready for download", Toast.LENGTH_SHORT).show()
                }
                is AdminUiEvent.OpenUrl -> {
                    try {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(event.url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "No app can open this document.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush())
    ) {
        // Admin Header Banner
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // Top Identity & Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = "Super Admin Shield",
                                    tint = AmberWarning,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            Text(
                                text = "Governance Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentUser?.email ?: "Unknown admin"} • Central Node",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    // Status Badges Group
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (isOffline) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else FreshGreen.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isOffline) MaterialTheme.colorScheme.error else FreshGreen, CircleShape)
                                )
                                Text(
                                    if (isOffline) "Offline" else "Cloud Sync Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOffline) MaterialTheme.colorScheme.error else FreshGreen,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Cloud sync status — only shown on admin side; non-admin users never see this
                if (isOffline) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text(
                                syncStatusMessage ?: "Firestore unreachable — serving cached data.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(MaterialTheme.shapes.medium),
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { adminViewModel.setSelectedTab(0) },
                        text = { Text("Packages", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { adminViewModel.setSelectedTab(1) },
                        text = {
                            Text(
                                "Users" + (uiState.counts?.let { " (${it.totalUsers})" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 2,
                        onClick = { adminViewModel.setSelectedTab(2) },
                        text = {
                            Text(
                                "Listings & Bookings" + (uiState.counts?.let { " (${it.totalListings})" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == 3,
                        onClick = { adminViewModel.setSelectedTab(3) },
                        text = { Text("Analytics", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 4,
                        onClick = { adminViewModel.setSelectedTab(4) },
                        text = { Text("Schema", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 5,
                        onClick = { adminViewModel.setSelectedTab(5) },
                        text = { Text("Security", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 6,
                        onClick = { adminViewModel.setSelectedTab(6) },
                        text = { Text("Demo Control", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 900.dp)
            ) {
                when (uiState.selectedTab) {
                    0 -> AdminPackagesTab(uiState = uiState, adminViewModel = adminViewModel)
                    1 -> AdminUsersDirectoryTab(uiState = uiState, adminViewModel = adminViewModel)
                    2 -> AdminListingsCatalogTab(uiState = uiState, adminViewModel = adminViewModel)
                    3 -> AdminAnalyticsScreen(adminViewModel = adminViewModel)
                    4 -> AdminSchemaArchitectureTab(uiState = uiState, adminViewModel = adminViewModel)
                    5 -> AdminSecurityAuditTab(uiState = uiState, adminViewModel = adminViewModel, currentUser = currentUser)
                    6 -> AdminDemoControlTab(uiState = uiState, adminViewModel = adminViewModel, viewModel = viewModel)
                }
            }
            if (uiState.selectedTab == 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showListingFilterMenu = true },
                        containerColor = SteelBlue,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Listing Filters")
                    }
                    DropdownMenu(
                        expanded = showListingFilterMenu,
                        onDismissRequest = { showListingFilterMenu = false }
                    ) {
                        listOf(
                            "ALL" to "All Listings",
                            "ACTIVE_30D" to "Active (Last 30d)",
                            "EXPIRED" to "Expired",
                            "VERIFIED" to "Verified",
                            "PENDING_VERIFICATION" to "Pending Review"
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    adminViewModel.setListingStatusFilter(key)
                                    showListingFilterMenu = false
                                },
                                leadingIcon = if (uiState.selectedListingStatusFilter == key) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // ALL ADMIN MODAL DIALOGS
    // ==========================================

    // 1. Multi-Format Export Dialog
    if (uiState.isExportDialogOpen) {
        AdminExportDataDialog(
            title = uiState.exportDataTitle,
            content = uiState.exportDataContent,
            format = uiState.activeExportFormat,
            onDismiss = { adminViewModel.closeExportDialog() }
        )
    }

    // 2. Edit User Dialog
    if (uiState.isEditUserDialogOpen) {
        uiState.editingUser?.let { user ->
            AdminEditUserDialog(
                user = user,
                onDismiss = { adminViewModel.closeEditUserDialog() },
                onSave = { updatedUser -> adminViewModel.saveUser(updatedUser) }
            )
        }
    }

    // 3. Delete User Confirmation Dialog
    if (uiState.isDeleteUserDialogOpen) {
        uiState.deletingUser?.let { user ->
            AdminDeleteUserDialog(
                user = user,
                onDismiss = { adminViewModel.closeDeleteUserDialog() },
                onConfirm = { adminViewModel.confirmDeleteUser(user.id) }
            )
        }
    }

    // 3b. Grant Admin Confirmation Dialog
    if (uiState.isSuspendUserDialogOpen) {
        uiState.suspendingUser?.let { user ->
            AdminSuspendUserDialog(
                user = user,
                onDismiss = { adminViewModel.closeSuspendUserDialog() },
                onConfirm = { adminViewModel.confirmToggleSuspend() }
            )
        }
    }

    if (uiState.isGrantAdminDialogOpen) {
        uiState.grantingAdminUser?.let { user ->
            AdminGrantAdminDialog(
                user = user,
                onDismiss = { adminViewModel.closeGrantAdminDialog() },
                onConfirm = { adminViewModel.confirmGrantAdmin(user.email) }
            )
        }
    }

    if (uiState.isRevokeProHostDialogOpen) {
        uiState.revokingProHostUser?.let { user ->
            AdminRevokeProHostDialog(
                user = user,
                onDismiss = { adminViewModel.closeRevokeProHostDialog() },
                onConfirm = { adminViewModel.confirmRevokeProHost() }
            )
        }
    }

    // 4. Edit Listing Dialog — the same full wizard used to create a listing
    // (photos, subdivisions, pricing config, ownership doc, everything), prefilled
    // from the existing listing and in its dedicated admin-edit mode (onListingUpdated)
    // so saving updates the listing directly rather than re-running publish/quota
    // gating meant for brand-new listings. Replaces the old AdminEditListingDialog,
    // a bare ~9-field form with no photo/subdivision/pricing editing at all.
    if (uiState.isEditListingDialogOpen && uiState.editingListing != null) {
        CreateListingDialog(
            currentUser = currentUser,
            existingDraft = uiState.editingListing,
            onDismiss = { adminViewModel.closeEditListingDialog() },
            onListingCreated = {},
            onListingUpdated = { updatedListing -> adminViewModel.saveListing(updatedListing) },
            spaceCategories = uiState.schema.spaceTypes,
            availableAmenities = uiState.schema.amenities.filter { it.isEnabled }
        )
    }

    // 5. Delete Listing Confirmation Dialog
    if (uiState.isDeleteListingDialogOpen) {
        uiState.deletingListing?.let { listing ->
            AdminDeleteListingDialog(
                listing = listing,
                onDismiss = { adminViewModel.closeDeleteListingDialog() },
                onConfirm = { adminViewModel.confirmDeleteListing(listing.id) }
            )
        }
    }

    // 6. Add Schema Node Dialog
    if (uiState.isAddSchemaItemDialogOpen) {
        AdminAddSchemaItemDialog(
            initialCategory = uiState.addSchemaItemPresetCategory ?: SchemaCategory.DIVISION_TYPE,
            onDismiss = { adminViewModel.closeAddSchemaItemDialog() },
            onAdd = { name, category, description, iconName, maxSubdivisions, markerColor ->
                adminViewModel.addSchemaItem(category, name, description, iconName, maxSubdivisions, markerColor = markerColor)
            }
        )
    }

    // Full profile of one account (opened from Users / Listings search results).
    uiState.dossier?.let { dossier -> AdminDossierSheet(dossier = dossier, uiState = uiState, adminViewModel = adminViewModel) }
    AdminBillingDialogs(uiState = uiState, adminViewModel = adminViewModel)
    if (uiState.isLoadingDossier && uiState.dossier == null) {
        Dialog(onDismissRequest = { adminViewModel.closeDossier() }) {
            CircularProgressIndicator()
        }
    }

    // 7. Reset Schema Confirmation Dialog
    if (uiState.isResetSchemaDialogOpen) {
        AdminResetSchemaDialog(
            onDismiss = { adminViewModel.closeResetSchemaDialog() },
            onConfirm = { adminViewModel.confirmResetSchema() }
        )
    }

}

@Composable
internal fun AdminMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    ProSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
