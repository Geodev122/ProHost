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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
                                "Users Directory (${uiState.allUsers.size})",
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
                                "Listings Catalog (${uiState.allSpaces.size})",
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
                        containerColor = VibrantBlue,
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

    // 7. Reset Schema Confirmation Dialog
    if (uiState.isResetSchemaDialogOpen) {
        AdminResetSchemaDialog(
            onDismiss = { adminViewModel.closeResetSchemaDialog() },
            onConfirm = { adminViewModel.confirmResetSchema() }
        )
    }


    // Delete Package Plan Confirmation Dialog
    if (uiState.isDeletePackagePlanDialogOpen) {
        uiState.pendingDeletePlanId?.let { planId ->
            AdminDeletePackagePlanDialog(
                planId = planId,
                subscriberCount = uiState.pendingDeletePlanSubscriberCount,
                onDismiss = { adminViewModel.cancelDeletePackagePlan() },
                onConfirm = { adminViewModel.confirmDeletePackagePlan() }
            )
        }
    }

}

// =========================================================================
// TAB 0: PACKAGES CONFIGURATION & SYSTEM EXPORTS
// =========================================================================
@Composable
private fun AdminPackagesTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel,
    viewModel: ProHostViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Owner Packages & Governance Hub Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProSectionHeader(
                        title = "Packages Configuration",
                        subtitle = "Packages & Control Tag",
                        icon = Icons.Default.AdminPanelSettings
                    )

                    var tagInput by remember(uiState.pricingState.governanceTag) { mutableStateOf(uiState.pricingState.governanceTag) }
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Admin Governance Control Tag") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { adminViewModel.updateGovernanceTag(tagInput) }) {
                                Icon(Icons.Default.Check, contentDescription = "Save Tag", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subscription Plans", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        CustomButton(
                            text = "Add Plan",
                            onClick = { adminViewModel.openAddPackagePlanDialog() },
                            variant = CustomButtonVariant.SECONDARY,
                            icon = Icons.Default.Add,
                            compact = true
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp))
                            Text(
                                "Price and billing period come from Google Play Console, and every plan allows unlimited listings. " +
                                    "Here you only set display priority (lower shows first) and which plan is featured.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Grant-only plans are system-managed by grantPackageToUser.
                    val adminPlans = uiState.packagePlans.packages.values.filterNot { it.isGrantOnly }
                        .sortedWith(compareBy({ !it.isFeatured }, { it.sortOrder }))
                    if (adminPlans.isEmpty()) {
                        Text(
                            "No plans yet — tap Add Plan and enter a Google Play subscription ID.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    adminPlans.forEach { plan ->
                        var priorityInput by remember(plan.id, plan.sortOrder) { mutableStateOf(plan.sortOrder.toString()) }
                        var featuredInput by remember(plan.id, plan.isFeatured) { mutableStateOf(plan.isFeatured) }
                        var isFetchingPlay by remember(plan.id) { mutableStateOf(false) }
                        var playLiveInfo by remember(plan.id) { mutableStateOf<String?>(null) }
                        val productId = plan.googlePlayProductId.ifBlank { plan.id }
                        val priority = priorityInput.toIntOrNull()
                        val isDirty = (priority != null && priority != plan.sortOrder) || featuredInput != plan.isFeatured

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Play ID: $productId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = plan.isEnabled, onCheckedChange = { adminViewModel.togglePackagePlan(plan.id) })
                                IconButton(onClick = { adminViewModel.deletePackagePlan(plan.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove plan", tint = StatusError)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = priorityInput,
                                    onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) priorityInput = v },
                                    label = { Text("Priority") },
                                    isError = priority == null,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Featured", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(4.dp))
                                    Switch(checked = featuredInput, onCheckedChange = { featuredInput = it })
                                }
                            }
                            playLiveInfo?.let { liveInfo ->
                                Text(liveInfo, style = MaterialTheme.typography.labelSmall, color = FreshGreen)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        isFetchingPlay = true
                                        playLiveInfo = null
                                        viewModel.fetchPlayProductDetails(context, productId) { details ->
                                            isFetchingPlay = false
                                            playLiveInfo = if (details != null) {
                                                val price = details.subscriptionOfferDetails?.firstOrNull()
                                                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                                                val period = price?.billingPeriod?.let { " / $it" }.orEmpty()
                                                "Google Play: ${details.name} · ${price?.formattedPrice ?: "—"}$period"
                                            } else {
                                                "Not found in Google Play — check the subscription ID in Play Console."
                                            }
                                        }
                                    },
                                    enabled = !isFetchingPlay
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Check Google Play", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.weight(1f))
                                CustomButton(
                                    text = "Save",
                                    onClick = {
                                        if (priority != null) {
                                            adminViewModel.updatePackagePlan(plan.copy(sortOrder = priority, isFeatured = featuredInput))
                                        }
                                    },
                                    enabled = isDirty && priority != null,
                                    variant = CustomButtonVariant.PRIMARY,
                                    compact = true
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Export Hub Shortcuts — writes a real file (Storage Access Framework
        // "Save As") instead of the old clipboard-copy/share-sheet-only dialog.
        item {
            val exportTxtFile = rememberFileExportLauncher(mimeType = "text/plain")
            val exportJsonFile = rememberFileExportLauncher(mimeType = "application/json")

            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "One-Click System Exports",
                        subtitle = "CSV & JSON snapshots",
                        icon = Icons.Default.CloudDownload
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomButton(
                            text = "Full Audit (TXT)",
                            onClick = { exportTxtFile("prohost_full_audit.txt", adminViewModel.getFullAuditReport()) },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.PRIMARY,
                            icon = Icons.Default.Summarize,
                            compact = true
                        )

                        CustomButton(
                            text = "Master (JSON)",
                            onClick = { exportJsonFile("prohost_master_export.json", adminViewModel.getMasterJsonExport()) },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.SECONDARY,
                            icon = Icons.Default.Code,
                            compact = true
                        )
                    }
                }
            }
        }
    }

    // Add Package Plan Dialog — lives here since the "Add Package" button is in this tab
    if (uiState.isAddPackagePlanDialogOpen) {
        AdminAddPackagePlanDialog(
            existingIds = uiState.packagePlans.packages.keys,
            onDismiss = { adminViewModel.closeAddPackagePlanDialog() },
            onAdd = { plan -> adminViewModel.addPackagePlan(plan) }
        )
    }
}

// =========================================================================
// TAB 1: USERS DIRECTORY & GOVERNANCE
// =========================================================================
@Composable
private fun AdminUsersDirectoryTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AdminGrantAccessCard(
                adminViewModel = adminViewModel,
                packagePlans = uiState.packagePlans
            )
        }
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Users Governance Directory",
                            subtitle = "Manage user records",
                            icon = Icons.Default.People
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CustomButton(
                                text = "CSV",
                                onClick = { adminViewModel.exportUsersDirectory("CSV") },
                                variant = CustomButtonVariant.PRIMARY,
                                icon = Icons.Default.Download,
                                compact = true
                            )
                            CustomButton(
                                text = "JSON",
                                onClick = { adminViewModel.exportUsersDirectory("JSON") },
                                variant = CustomButtonVariant.SECONDARY,
                                icon = Icons.Default.Code,
                                compact = true
                            )
                        }
                    }

                    // Search Field
                    InputField(
                        value = uiState.userSearchQuery,
                        onValueChange = { adminViewModel.setUserSearchQuery(it) },
                        label = "Search by Name, Email, Specialty, Phone, City...",
                        leadingIcon = Icons.Default.Search,
                        trailingIcon = {
                            if (uiState.userSearchQuery.isNotBlank()) {
                                IconButton(onClick = { adminViewModel.setUserSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Role Filter Chips
                    Text("Filter by Role:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedUserRoleFilter == null,
                                onClick = { adminViewModel.setUserRoleFilter(null) },
                                label = { Text("All Roles (${uiState.allUsers.size})") }
                            )
                        }
                        items(UserRole.entries) { role ->
                            val count = uiState.allUsers.count { it.role == role }
                            FilterChip(
                                selected = uiState.selectedUserRoleFilter == role,
                                onClick = { adminViewModel.setUserRoleFilter(role) },
                                label = { Text("${role.name.replace("_", " ")} ($count)") }
                            )
                        }
                    }
                }
            }
        }

        // Users Count Summary
        item {
            Text(
                text = "Showing ${uiState.filteredUsers.size} of ${uiState.allUsers.size} registered users",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.filteredUsers.isEmpty()) {
            item {
                ProEmptyState(
                    title = "No Users Found",
                    description = "No user records match the specified query and filters.",
                    icon = Icons.Default.PersonOff
                )
            }
        }

        // List of filtered users
        items(uiState.filteredUsers, key = { it.id }) { user ->
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ProMemberAvatar(
                                name = user.fullName,
                                specialty = user.specialty,
                                isVerified = user.isVerified,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = user.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (user.isSuspended) {
                                Surface(color = StatusErrorContainer, shape = MaterialTheme.shapes.small) {
                                    Text(
                                        text = "SUSPENDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusOnErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            // Role Badge
                            Surface(
                                color = when (user.role) {
                                    UserRole.ADMIN -> StatusWarningContainer
                                    UserRole.PRO_HOST -> CarnationOrangeContainer
                                    UserRole.SPECIALIST -> OxfordBlueContainer
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = user.role.name.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (user.role) {
                                        UserRole.ADMIN -> AmberWarning
                                        UserRole.PRO_HOST -> CarnationOrange
                                        UserRole.SPECIALIST -> OxfordBlue
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // User Details Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Location:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.city.ifBlank { user.governorate.ifBlank { user.country } },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column {
                            Text("Phone Status:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (user.isVerified) "Verified" else "Unverified",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (user.isVerified) FreshGreen else StatusError
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Phone / WhatsApp:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.phone.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            Text("Member Since:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.createdAtMillis?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "—",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column {
                            Text("Last Sign-In:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.lastSignInAtMillis?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "—",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Edit Button
                        CustomButton(
                            text = "Edit",
                            onClick = { adminViewModel.openEditUserDialog(user) },
                            modifier = Modifier.weight(0.9f),
                            variant = CustomButtonVariant.OUTLINED,
                            icon = Icons.Default.Edit,
                            compact = true
                        )

                        // Grant Admin Button
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openGrantAdminDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = "Grant Admin",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Suspend / Reactivate Button — the state between "exists" and
                        // "deleted"; never offered for an Admin account (the server
                        // rejects that anyway, but hiding it here avoids a confusing
                        // failed attempt).
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openSuspendUserDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (user.isSuspended) Icons.Default.LockOpen else Icons.Default.Block,
                                    contentDescription = if (user.isSuspended) "Reactivate Account" else "Suspend Account",
                                    tint = if (user.isSuspended) FreshGreen else StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Revoke Pro Host Button — the downgrade path back to Specialist
                        // that never existed before; only meaningful for an account that
                        // currently holds the role.
                        if (user.role == UserRole.PRO_HOST) {
                            IconButton(
                                onClick = { adminViewModel.openRevokeProHostDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveModerator,
                                    contentDescription = "Revoke Pro Host Role",
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Delete Button
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openDeleteUserDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: LISTINGS CATALOG & CRUD
// =========================================================================
@Composable
private fun AdminListingsCatalogTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Listings Catalog",
                            icon = Icons.Default.Apartment
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CustomButton(
                                text = "CSV",
                                onClick = { adminViewModel.exportListingsCatalog("CSV") },
                                variant = CustomButtonVariant.PRIMARY,
                                icon = Icons.Default.Download,
                                compact = true
                            )
                            CustomButton(
                                text = "JSON",
                                onClick = { adminViewModel.exportListingsCatalog("JSON") },
                                variant = CustomButtonVariant.SECONDARY,
                                icon = Icons.Default.Code,
                                compact = true
                            )
                        }
                    }

                    // Search Field
                    InputField(
                        value = uiState.listingSearchQuery,
                        onValueChange = { adminViewModel.setListingSearchQuery(it) },
                        label = "Search by Title, District, Address, Host...",
                        leadingIcon = Icons.Default.Search,
                        trailingIcon = {
                            if (uiState.listingSearchQuery.isNotBlank()) {
                                IconButton(onClick = { adminViewModel.setListingSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Space Category Filter Chips — from the admin-defined catalog
                    // (the same one the host's Create wizard publishes under), so an
                    // admin-added category is filterable here the moment it exists.
                    val categoryFilterOptions = uiState.schema.spaceTypes.filter { it.isEnabled }.ifEmpty {
                        SpaceType.values().map { SchemaItem(id = it.name, name = it.displayName, category = "SPACE_TYPE") }
                    }
                    Text("Filter by Space Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedListingTypeFilter == null,
                                onClick = { adminViewModel.setListingTypeFilter(null) },
                                label = { Text("All Categories (${uiState.allSpaces.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        items(categoryFilterOptions) { category ->
                            val count = uiState.allSpaces.count { it.matchesCategory(category.id) }
                            FilterChip(
                                selected = uiState.selectedListingTypeFilter == category.id,
                                onClick = { adminViewModel.setListingTypeFilter(category.id) },
                                label = { Text("${category.name} ($count)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Status Filter
                    Text("Filter by Subscription / Verification:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    val pendingVerificationCount = uiState.allSpaces.count { !it.verificationDocUrl.isNullOrBlank() && !it.isVerified }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "ALL" to "All",
                            "ACTIVE_30D" to "Active",
                            "EXPIRED" to "Expired",
                            "VERIFIED" to "Verified",
                            "PENDING_VERIFICATION" to "Pending Review ($pendingVerificationCount)"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = uiState.selectedListingStatusFilter == key,
                                onClick = { adminViewModel.setListingStatusFilter(key) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Showing ${uiState.filteredSpaces.size} of ${uiState.allSpaces.size} listings in Lebanon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.filteredSpaces.isEmpty()) {
            item {
                ProEmptyState(
                    title = "No Listings Match Filters",
                    description = "Try adjusting your search query or workspace type filter.",
                    icon = Icons.Default.SearchOff
                )
            }
        }

        items(uiState.filteredSpaces, key = { it.id }) { space ->
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = space.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${space.district}, ${space.governorate.displayName.split(" ").first()} • ${space.streetAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val (adminLowestPrice, adminLowestUnit) = remember(space) { SpaceCalculationUtils.lowestPriceSummary(space) }
                        Text(
                            text = "$${adminLowestPrice.toInt()}$adminLowestUnit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = space.spaceType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (space.isActiveSubscription) {
                            ProStatusBadge(ProBadgeType.ACTIVE_30D)
                        } else {
                            ProStatusBadge(ProBadgeType.EXPIRED)
                        }

                        if (space.isVerified) {
                            ProStatusBadge(ProBadgeType.CUSTOM_SUCCESS, customText = "Verified")
                        } else if (!space.verificationDocUrl.isNullOrBlank()) {
                            ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Pending Verification")
                        }
                    }

                    if (!space.isVerified && !space.verificationDocUrl.isNullOrBlank()) {
                        CustomButton(
                            text = "View Verification Document (${space.verificationDocType?.name?.replace('_', ' ') ?: "on file"})",
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(space.verificationDocUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open document URL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = CustomButtonVariant.OUTLINED,
                            icon = Icons.Default.Description,
                            compact = true
                        )
                    }

                    Text(
                        text = "Host: ${space.ownerName} • Phone: ${space.ownerPhone} • Formulas: ${space.rentalFormulas.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle subscription active status — a plain admin override, no
                        // expiry date attached (setListingSubscriptionActive just flips the
                        // boolean; it doesn't grant or fabricate any time period).
                        CustomButton(
                            text = if (space.isActiveSubscription) "Deactivate" else "Activate",
                            onClick = { adminViewModel.toggleListingSubscription(space.id, space.isActiveSubscription) },
                            modifier = Modifier.weight(1.1f),
                            variant = CustomButtonVariant.OUTLINED,
                            icon = if (space.isActiveSubscription) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            compact = true
                        )

                        // Toggle Verified
                        CustomButton(
                            text = if (space.isVerified) "Unverify" else "Verify",
                            onClick = { adminViewModel.toggleListingVerification(space.id) },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.OUTLINED,
                            icon = Icons.Default.Verified,
                            compact = true
                        )

                        // Edit Button
                        CustomButton(
                            text = "Edit",
                            onClick = { adminViewModel.openEditListingDialog(space) },
                            modifier = Modifier.weight(0.8f),
                            variant = CustomButtonVariant.OUTLINED,
                            icon = Icons.Default.Edit,
                            compact = true
                        )

                        // Delete Button
                        IconButton(
                            onClick = { adminViewModel.openDeleteListingDialog(space) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: GOD SCHEMA (UNIFIED ADMIN-EDITABLE DATABASE SCHEMA)
// =========================================================================
@Composable
private fun AdminSchemaArchitectureTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    val schema = uiState.schema
    var editingItem by remember { mutableStateOf<SchemaItem?>(null) }
    var amenityGroupFilter by remember { mutableStateOf("All") }
    var showAddAttendeePackageDialog by remember { mutableStateOf(false) }
    var editingAttendeePackage by remember { mutableStateOf<AttendeePackage?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: God Schema overview + stats + reset/add
        item {
            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(title = "God Schema", subtitle = "Live admin-editable database", icon = Icons.Default.AccountTree)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CustomButton(
                                text = "Reset",
                                onClick = { adminViewModel.openResetSchemaDialog() },
                                variant = CustomButtonVariant.OUTLINED,
                                icon = Icons.Default.RestartAlt,
                                compact = true
                            )
                            CustomButton(
                                text = "Add Node",
                                onClick = { adminViewModel.openAddSchemaItemDialog() },
                                variant = CustomButtonVariant.SECONDARY,
                                icon = Icons.Default.Add,
                                compact = true
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tiles = listOf(
                            Triple("Space Types", schema.spaceTypes, Icons.Default.Apartment),
                            Triple("Division Types", schema.divisionTypes, Icons.Default.Category),
                            Triple("Facilities", schema.facilities, Icons.Default.CheckCircle),
                            Triple("Amenities", schema.amenities, Icons.Default.Biotech),
                            Triple("Strategies", schema.rentalStrategies, Icons.Default.Schedule)
                        )
                        items(tiles) { (title, items, icon) ->
                            Box(modifier = Modifier.width(140.dp)) {
                                ProMetricTile(
                                    title = title,
                                    value = "${items.size}",
                                    subtitle = "${items.count { it.isEnabled }} active · ${items.count { !it.isEnabled }} off",
                                    icon = icon
                                )
                            }
                        }
                        item {
                            Box(modifier = Modifier.width(140.dp)) {
                                ProMetricTile(
                                    title = "Packages",
                                    value = "${schema.attendeePackages.size}",
                                    subtitle = "${schema.attendeePackages.count { it.isEnabled }} active",
                                    icon = Icons.Default.ConfirmationNumber
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Space Types
        item {
            GodSchemaSection(
                title = "Space Types",
                subtitle = "Top-level listing categories",
                icon = Icons.Default.Apartment,
                items = schema.spaceTypes,
                onAdd = { adminViewModel.openAddSchemaItemDialog(presetCategory = SchemaCategory.SPACE_TYPE) },
                onToggle = { item -> adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) },
                onDelete = { item -> adminViewModel.deleteSchemaItem(item.id, item.category) },
                onEdit = { item -> editingItem = item }
            ) { item ->
                if (item.maxSubdivisions != null) {
                    Text(
                        "Max subdivisions: ${item.maxSubdivisions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 2: Division Types (flat — all available for all space types)
        item {
            GodSchemaSection(
                title = "Division Types",
                subtitle = "Subdivision types (available across all space types)",
                icon = Icons.Default.MeetingRoom,
                items = schema.divisionTypes,
                onAdd = { adminViewModel.openAddSchemaItemDialog(presetCategory = SchemaCategory.DIVISION_TYPE) },
                onToggle = { item -> adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) },
                onDelete = { item -> adminViewModel.deleteSchemaItem(item.id, item.category) },
                onEdit = { item -> editingItem = item },
                extraContent = { item ->
                    if (item.supportsAttendeeMode) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                            Text(
                                "per-attendee",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            )
        }

        // Section 3: Facilities (whole-space — all subdivisions inherit these)
        item {
            GodSchemaSection(
                title = "Facilities",
                subtitle = "Whole-space facilities — shown to all subdivisions",
                icon = Icons.Default.HomeWork,
                items = schema.facilities,
                onAdd = { adminViewModel.openAddSchemaItemDialog(presetCategory = SchemaCategory.FACILITY) },
                onToggle = { item -> adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) },
                onDelete = { item -> adminViewModel.deleteSchemaItem(item.id, item.category) },
                onEdit = { item -> editingItem = item }
            )
        }

        // Section 4: Amenities (subdivision-level, filterable by group)
        item {
            val amenityGroups = listOf("All") + schema.amenities.map { it.amenityGroup }.filter { it.isNotBlank() }.distinct().sorted()
            val visibleAmenities = if (amenityGroupFilter == "All") schema.amenities
            else schema.amenities.filter { it.amenityGroup == amenityGroupFilter }
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(title = "Amenities", subtitle = "Subdivision-level features, equipment & amenities", icon = Icons.Default.Biotech)
                        CustomButton(
                            text = "Add",
                            onClick = { adminViewModel.openAddSchemaItemDialog(presetCategory = SchemaCategory.AMENITY) },
                            variant = CustomButtonVariant.SECONDARY,
                            icon = Icons.Default.Add,
                            compact = true
                        )
                    }
                    // Group filter chips
                    if (amenityGroups.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(amenityGroups) { group ->
                                FilterChip(
                                    selected = amenityGroupFilter == group,
                                    onClick = { amenityGroupFilter = group },
                                    label = { Text(group, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    visibleAmenities.forEach { item ->
                        SchemaItemRow(
                            item = item,
                            onToggle = { adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) },
                            onDelete = { adminViewModel.deleteSchemaItem(item.id, item.category) },
                            onEdit = { editingItem = item }
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (item.amenityGroup.isNotBlank()) {
                                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                        Text(
                                            item.amenityGroup,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                val scopeLabel = if (item.scopedToIds.isEmpty()) "All divisions" else "${item.scopedToIds.size} div. types"
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
                                    Text(
                                        scopeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    if (visibleAmenities.isEmpty()) {
                        Text("No amenities in this group.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (schema.amenities.isEmpty()) {
                            CustomButton(
                                text = "Seed Defaults",
                                onClick = { adminViewModel.seedDefaultAmenities() },
                                variant = CustomButtonVariant.OUTLINED,
                                icon = Icons.Default.AutoFixHigh,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Section 5: Rental Strategies
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(title = "Rental Strategies", subtitle = "Reference labels — mirrors RentalStrategyType", icon = Icons.Default.Schedule)
                        CustomButton(
                            text = "Add",
                            onClick = { adminViewModel.openAddSchemaItemDialog(presetCategory = SchemaCategory.RENTAL_STRATEGY) },
                            variant = CustomButtonVariant.SECONDARY,
                            icon = Icons.Default.Add,
                            compact = true
                        )
                    }
                    schema.rentalStrategies.forEach { item ->
                        SchemaItemRow(
                            item = item,
                            onToggle = { adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) },
                            onDelete = { adminViewModel.deleteSchemaItem(item.id, item.category) },
                            onEdit = { editingItem = item }
                        )
                        HorizontalDivider(color = LightGray.copy(alpha = 0.4f))
                    }
                }
            }
        }

        // Section 6: Attendee Packages
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Attendee Packages",
                            subtitle = "Per-person pricing for conference & event subdivisions",
                            icon = Icons.Default.ConfirmationNumber
                        )
                        CustomButton(
                            text = "Add",
                            onClick = { showAddAttendeePackageDialog = true },
                            variant = CustomButtonVariant.SECONDARY,
                            icon = Icons.Default.Add,
                            compact = true
                        )
                    }
                    if (schema.attendeePackages.isEmpty()) {
                        Text(
                            "No attendee packages defined yet.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        schema.attendeePackages.forEach { pkg ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(pkg.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (!pkg.isSystemDefault) {
                                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.extraSmall) {
                                                Text(
                                                    "CUSTOM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "$${
                                            pkg.pricePerAttendeeUsd.let {
                                                if (it == it.toLong().toDouble()) it.toLong().toString() else String.format("%.2f", it)
                                            }
                                        }" +
                                            "/person · min ${pkg.minAttendees}${pkg.maxAttendees?.let { " · max $it" } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (pkg.inclusions.isNotEmpty()) {
                                        Text(
                                            pkg.inclusions.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    Switch(checked = pkg.isEnabled, onCheckedChange = { adminViewModel.toggleAttendeePackage(pkg.id) })
                                    IconButton(onClick = { editingAttendeePackage = pkg }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { adminViewModel.deleteAttendeePackage(pkg.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = LightGray.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }

        // Target Disciplines (hashtag analytics)
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        ProSectionHeader(title = "Target Disciplines Usage", subtitle = "Hashtags hosts attach to listings", icon = Icons.Default.Info)
                        IconButton(onClick = { adminViewModel.refreshHashtagAnalytics() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    }
                    if (uiState.hashtagAnalytics.isEmpty()) {
                        Text("No hashtags recorded yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.hashtagAnalytics.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${entry.tag}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${entry.count} uses • ${entry.governorate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Firestore Database Schema Reference Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Cloud Firestore Collections Contract",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Collection: 'workspace_listings' (Documents: SpaceListing)\n" +
                                "• Collection: 'user_profiles' (Documents: AppUser)\n" +
                                "• Collection: 'booking_requests' (Documents: RentalBookingRequest)\n" +
                                "• Collection: 'package_plans' (Documents: PackagePlanCatalog)\n" +
                                "• Collection: 'audit_security_logs' (Documents: AuditSecurityLog)\n" +
                                "• Collection: 'system_metadata' (Documents: AdminPricingState)\n" +
                                "• Collection: 'hashtag_usage' (Documents: HashtagUsageEntry)\n" +
                                "• Collection: 'schema_architecture' (Documents: SpaceArchitectureSchema)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Inline edit dialog for schema items
    editingItem?.let { item ->
        AdminEditSchemaItemDialog(
            item = item,
            availableDivisionTypes = schema.divisionTypes,
            onSave = { updated ->
                adminViewModel.updateSchemaItem(updated)
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }

    // Attendee package dialogs
    if (showAddAttendeePackageDialog) {
        AdminAttendeePackageDialog(
            existingPackage = null,
            onSave = { name, desc, price, inclusions, min, max ->
                adminViewModel.addAttendeePackage(name, desc, price, inclusions, min, max)
                showAddAttendeePackageDialog = false
            },
            onDismiss = { showAddAttendeePackageDialog = false }
        )
    }
    editingAttendeePackage?.let { pkg ->
        AdminAttendeePackageDialog(
            existingPackage = pkg,
            onSave = { name, desc, price, inclusions, min, max ->
                adminViewModel.updateAttendeePackage(
                    pkg.copy(
                        name = name,
                        description = desc,
                        pricePerAttendeeUsd = price,
                        inclusions = inclusions,
                        minAttendees = min,
                        maxAttendees = max
                    )
                )
                editingAttendeePackage = null
            },
            onDismiss = { editingAttendeePackage = null }
        )
    }
}

// =========================================================================
// GOD SCHEMA HELPERS
// =========================================================================

@Composable
private fun GodSchemaSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<SchemaItem>,
    onAdd: () -> Unit,
    onToggle: (SchemaItem) -> Unit,
    onDelete: (SchemaItem) -> Unit,
    onEdit: (SchemaItem) -> Unit,
    extraContent: (@Composable (SchemaItem) -> Unit)? = null
) {
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProSectionHeader(title = title, subtitle = subtitle, icon = icon)
                CustomButton(text = "Add", onClick = onAdd, variant = CustomButtonVariant.SECONDARY, icon = Icons.Default.Add, compact = true)
            }
            if (items.isEmpty()) {
                Text("No items yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEachIndexed { idx, item ->
                val extra: (@Composable () -> Unit)? = if (extraContent != null) ({ extraContent(item) }) else null
                SchemaItemRow(item = item, onToggle = { onToggle(item) }, onDelete = { onDelete(item) }, onEdit = { onEdit(item) }, extraContent = extra)
                if (idx < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun SchemaItemRow(
    item: SchemaItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
                color = if (item.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (item.category) {
                            SchemaCategory.SPACE_TYPE -> Icons.Default.Apartment
                            SchemaCategory.DIVISION_TYPE -> Icons.Default.MeetingRoom
                            SchemaCategory.FACILITY -> Icons.Default.HomeWork
                            SchemaCategory.AMENITY -> Icons.Default.Biotech
                            SchemaCategory.RENTAL_STRATEGY -> Icons.Default.Schedule
                            else -> Icons.Default.AccountTree
                        },
                        contentDescription = null,
                        tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (!item.isSystemDefault) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(color = CarnationOrangeContainer, shape = MaterialTheme.shapes.extraSmall) {
                            Text(
                                "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CarnationOrange,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    "ID: ${item.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                if (item.description.isNotBlank()) {
                    Text(item.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                extraContent?.invoke()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Switch(checked = item.isEnabled, onCheckedChange = { onToggle() }, modifier = Modifier.padding(start = 2.dp))
            if (!item.isSystemDefault) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminEditSchemaItemDialog(
    item: SchemaItem,
    availableDivisionTypes: List<SchemaItem>,
    onSave: (SchemaItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var description by remember { mutableStateOf(item.description) }
    var amenityGroup by remember { mutableStateOf(item.amenityGroup) }
    var maxSubs by remember { mutableStateOf(item.maxSubdivisions?.toString() ?: "") }
    var selectedScopedIds by remember { mutableStateOf(item.scopedToIds.toSet()) }
    var supportsAttendeeMode by remember { mutableStateOf(item.supportsAttendeeMode) }
    var markerColorInput by remember { mutableStateOf(item.markerColor ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Schema Item", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (item.category == SchemaCategory.SPACE_TYPE) {
                    OutlinedTextField(
                        value = maxSubs,
                        onValueChange = { maxSubs = it },
                        label = { Text("Max Subdivisions (leave blank = unlimited)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    // Marker color picker
                    Text("Map Marker Color", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(MARKER_COLOR_PRESETS) { hex ->
                            val selected = markerColorInput.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        try { Color(android.graphics.Color.parseColor(hex)) }
                                        catch (e: Exception) { MaterialTheme.colorScheme.surfaceVariant }
                                    )
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { markerColorInput = hex }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = markerColorInput,
                        onValueChange = { markerColorInput = it.take(7) },
                        label = { Text("Hex color (e.g. #5B9BFF)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (item.category == SchemaCategory.DIVISION_TYPE) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Supports Per-Attendee Pricing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "Show attendee mode toggle for this division type",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = supportsAttendeeMode, onCheckedChange = { supportsAttendeeMode = it })
                    }
                }
                if (item.category == SchemaCategory.AMENITY) {
                    OutlinedTextField(
                        value = amenityGroup,
                        onValueChange = { amenityGroup = it },
                        label = { Text("Amenity Group (e.g. Comfort, Access, Equipment)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (availableDivisionTypes.isNotEmpty()) {
                        Text("Scoped to Division Types (empty = all):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        availableDivisionTypes.forEach { dt ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = dt.id in selectedScopedIds, onCheckedChange = { checked ->
                                    selectedScopedIds = if (checked) selectedScopedIds + dt.id else selectedScopedIds - dt.id
                                })
                                Text(dt.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            CustomButton(
                text = "Save",
                onClick = {
                    onSave(item.copy(
                        name = name.trim(),
                        description = description.trim(),
                        amenityGroup = amenityGroup.trim(),
                        maxSubdivisions = if (item.category == SchemaCategory.SPACE_TYPE) maxSubs.trim().toIntOrNull() else item.maxSubdivisions,
                        scopedToIds = if (item.category == SchemaCategory.AMENITY) selectedScopedIds.toList() else item.scopedToIds,
                        supportsAttendeeMode = if (item.category == SchemaCategory.DIVISION_TYPE) supportsAttendeeMode else item.supportsAttendeeMode,
                        markerColor = if (item.category == SchemaCategory.SPACE_TYPE) markerColorInput.takeIf { it.isNotBlank() } else item.markerColor
                    ))
                },
                variant = CustomButtonVariant.PRIMARY,
                enabled = name.isNotBlank()
            )
        },
        dismissButton = {
            CustomButton(text = "Cancel", onClick = onDismiss, variant = CustomButtonVariant.OUTLINED)
        }
    )
}

// =========================================================================
// TAB 5: SECURITY AUDIT & LOGS
// =========================================================================
@Composable
private fun AdminSecurityAuditTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel,
    currentUser: com.example.data.model.AppUser? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            var auditExportFromMillis by remember { mutableStateOf<Long?>(null) }
            var auditExportToMillis by remember { mutableStateOf<Long?>(null) }
            val exportAuditCsvFile = rememberFileExportLauncher(mimeType = "text/csv")

            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Security & Audit",
                        icon = Icons.Default.Shield
                    )

                    Text(
                        text = "Total Registered Logs: ${uiState.auditLogs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DateRangePickerRow(
                        fromMillis = auditExportFromMillis,
                        toMillis = auditExportToMillis,
                        onFromChange = { auditExportFromMillis = it },
                        onToChange = { auditExportToMillis = it }
                    )

                    CustomButton(
                        text = "Export Audit Logs (CSV)",
                        onClick = {
                            val csv = adminViewModel.exportAuditLogsCsv(auditExportFromMillis, auditExportToMillis)
                            exportAuditCsvFile("prohost_audit_logs.csv", csv)
                        },
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.Download,
                        compact = true
                    )
                }
            }
        }

        item {
            val context = LocalContext.current
            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Legal Documents",
                        subtitle = "Upload to publish new version",
                        icon = Icons.Default.Gavel
                    )
                    data class LegalDocSlot(
                        val docId: String,
                        val title: String,
                        val mimeType: String,
                        val extension: String,
                        val noPublishedCopyIsBlocking: Boolean
                    )
                    val slots = listOf(
                        LegalDocSlot("privacy_policy", "Privacy Policy", "text/html", "html", true),
                        LegalDocSlot("terms_of_use", "Terms of Use", "text/html", "html", true),
                        LegalDocSlot("revocation_policy", "Revocation Policy", "text/html", "html", true),
                        LegalDocSlot(
                            LegalDocumentVersion.RERENTAL_TEMPLATE_DOC_ID,
                            "Re-Rental Authorization Template (PDF)",
                            "application/pdf",
                            "pdf",
                            // Never a dead end: the app falls back to generating this PDF
                            // from the built-in template when no admin version exists yet.
                            false
                        )
                    )
                    slots.forEach { slot ->
                        val current = uiState.legalDocuments[slot.docId]
                        val isUploading = uiState.isUploadingLegalDocument == slot.docId
                        val pickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri: Uri? ->
                            if (uri != null) {
                                var resolvedName: String? = null
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && nameIndex >= 0) resolvedName = cursor.getString(nameIndex)
                                }
                                adminViewModel.uploadLegalDocument(
                                    slot.docId, uri, resolvedName,
                                    currentUser?.email ?: "admin@prohost.app",
                                    contentType = slot.mimeType,
                                    fileExtension = slot.extension
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(slot.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (current != null) {
                                        "v${current.version} — published ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(current.uploadedAtMillis))}"
                                    } else if (slot.noPublishedCopyIsBlocking) {
                                        "Not yet published"
                                    } else {
                                        "Not yet published — app falls back to a generated PDF"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (current != null || !slot.noPublishedCopyIsBlocking) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            CustomButton(
                                text = if (current != null) "Upload New Version" else "Upload",
                                onClick = { pickerLauncher.launch(slot.mimeType) },
                                variant = CustomButtonVariant.OUTLINED,
                                enabled = !isUploading,
                                isLoading = isUploading,
                                icon = Icons.Default.UploadFile,
                                compact = true
                            )
                        }
                    }
                }
            }
        }

        items(uiState.auditLogs) { log ->
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isHighSeverity = log.severity.contains(
                            "WARN",
                            ignoreCase = true
                        ) || log.severity.contains("CRIT", ignoreCase = true) || log.severity.contains("HIGH", ignoreCase = true)
                        Surface(
                            color = if (isHighSeverity) StatusErrorContainer else StatusSuccessContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = log.severity,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighSeverity) StatusError else StatusSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = log.actionType,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = log.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Actor: ${log.actorEmail} • IP: ${log.ipAddress} • ID: ${log.id}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// =========================================================================
// MODAL DIALOGS IMPLEMENTATION
// =========================================================================

/**
 * 1. Multi-Format Export Dialog
 */
@Composable
private fun AdminExportDataDialog(
    title: String,
    content: String,
    format: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lineCount = remember(content) { content.lines().size }
    val charCount = remember(content) { content.length }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Format: $format • $lineCount lines • $charCount characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Scrollable Content Box
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = content,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(Spacing.md)
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomButton(
                        text = "Copy Text",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(title, content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.ContentCopy
                    )

                    CustomButton(
                        text = "Share / Export",
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Export Data"))
                        },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.Share
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDemoControlTab(
    uiState: AdminUiState,
    adminViewModel: AdminViewModel,
    viewModel: ProHostViewModel
) {
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    val allSpaces = uiState.allSpaces
    val allUsers = uiState.allUsers
    val allBookings by viewModel.bookingRequests.collectAsState()

    val demoSpacesCount = remember(allSpaces) {
        allSpaces.count { it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-") }
    }
    val demoUsersCount = remember(allUsers) {
        allUsers.count { it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-") || it.email.startsWith("demo.") }
    }
    val demoBookingsCount = remember(allBookings) {
        allBookings.count { it.isDemo || it.id.startsWith("demo-") || it.id.startsWith("DEMO-") }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text("Demo Content Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Generate or purge legitimate demo listings, fake rental requests, and demo specialist/host accounts for testing, client showcases, and UI verification before going live in production.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Active Demo Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Demo Listings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$demoSpacesCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Fake Requests", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$demoBookingsCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = FreshGreen)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Demo Users", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$demoUsersCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AmberWarning)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CustomButton(
                            text = "Generate Demo Content",
                            onClick = { adminViewModel.seedDemoContent() },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.PRIMARY,
                            icon = Icons.Default.AutoAwesome
                        )

                        CustomButton(
                            text = "Purge Demo Content",
                            onClick = { showPurgeConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.DANGER,
                            icon = Icons.Default.DeleteSweep
                        )
                    }
                }
            }
        }
    }

    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Purge All Demo Content?") },
            text = {
                Text("This will permanently delete all $demoSpacesCount demo listings, $demoBookingsCount fake rental requests, and $demoUsersCount demo user accounts from Firestore and local memory before production.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeConfirmDialog = false
                        adminViewModel.purgeDemoContent()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * 2. Edit User Dialog
 */
@Composable
private fun AdminEditUserDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onSave: (AppUser) -> Unit
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phone) }
    var specialty by remember { mutableStateOf(user.specialty) }
    var country by remember { mutableStateOf(user.country) }
    var governorateArea by remember { mutableStateOf(user.governorate) }
    var city by remember { mutableStateOf(user.city) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit User: ${user.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                InputField(value = fullName, onValueChange = { fullName = it }, label = "Full Name", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = email, onValueChange = { email = it }, label = "Email", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = phone, onValueChange = { phone = it }, label = "Phone (WhatsApp)", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = "Specialty / Profession",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                InputField(value = country, onValueChange = { country = it }, label = "Country", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(
                    value = governorateArea,
                    onValueChange = { governorateArea = it },
                    label = "Governorate / Area",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                InputField(value = city, onValueChange = { city = it }, label = "City", modifier = Modifier.fillMaxWidth(), singleLine = true)
                // Role and phone-verified status both go exclusively through dedicated
                // Cloud Functions (grantAdminRole / assignInitialRole) — a direct write to
                // either from this generic edit form is denied by Firestore rules. Shown
                // read-only here; use the Grant Admin action on the user row instead.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Role: ${user.role.name.replace("_", " ")}  •  ${if (user.isVerified) "Phone Verified" else "Phone Unverified"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Change these from the user row's own actions, not here.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED
                    )
                    CustomButton(
                        text = "Save Changes",
                        onClick = {
                            val updated = user.copy(
                                fullName = fullName.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                specialty = specialty.trim(),
                                country = country.trim(),
                                governorate = governorateArea.trim(),
                                city = city.trim()
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.PRIMARY
                    )
                }
            }
        }
    }
}

/**
 * 3. Delete User Dialog
 */
@Composable
private fun AdminDeleteUserDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError) },
        title = { Text("Delete User Record?") },
        text = {
            Text("Are you sure you want to permanently remove '${user.fullName}' (${user.email})'s profile from the pl" +
                "atform? Their sign-in credentials are not revoked by this action.")
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Delete",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

/**
 * 3b. Grant Admin Confirmation Dialog
 */
@Composable
private fun AdminGrantAdminDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Grant Admin Role?") },
        text = {
            Text("Are you sure you want to grant full Admin privileges to '${user.fullName}' (${user.email})? This giv" +
                "es them unrestricted access to governance, pricing, and user management.")
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Grant",
                onClick = onConfirm,
                variant = CustomButtonVariant.SUCCESS
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

@Composable
private fun AdminSuspendUserDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val suspending = !user.isSuspended
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (suspending) Icons.Default.Block else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (suspending) StatusError else FreshGreen
            )
        },
        title = { Text(if (suspending) "Suspend Account?" else "Reactivate Account?") },
        text = {
            Text(
                if (suspending) {
                    "'${user.fullName}' (${user.email}) will be signed out and unable to sign back in, create listings, or submit booking " +
                    "requests until reactivated. Their data and history are kept — this is not a deletion."
                } else {
                    "'${user.fullName}' (${user.email}) will regain full access immediately."
                }
            )
        },
        confirmButton = {
            CustomButton(
                text = if (suspending) "Confirm Suspend" else "Confirm Reactivate",
                onClick = onConfirm,
                variant = if (suspending) CustomButtonVariant.DANGER else CustomButtonVariant.SUCCESS
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

@Composable
private fun AdminRevokeProHostDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RemoveModerator, contentDescription = null, tint = StatusError) },
        title = { Text("Revoke Pro Host Role?") },
        text = {
            Text(
                "'${user.fullName}' (${user.email}) will be downgraded back to Specialist immediately. Every listing they've published will be " +
                "marked inactive/expired (still visible in Discovery unless the specialist filters for active-subscription only, but shown as " +
                "expired — not deleted). This does not affect their ability to book workspaces as a Specialist."
            )
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Revoke",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

/**
 * 5. Delete Listing Dialog
 */
@Composable
private fun AdminDeleteListingDialog(
    listing: SpaceListing,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError) },
        title = { Text("Delete Workspace Listing?") },
        text = {
            Text("Are you sure you want to delete '${listing.title}' in ${listing.district}? This will remove it from " +
                "discovery and cancel any active rental bookings.")
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Delete",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

/**
 * 8. Delete Package Plan Confirmation Dialog (BUG-C3)
 */
@Composable
private fun AdminDeletePackagePlanDialog(
    planId: String,
    subscriberCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError) },
        title = { Text("Remove Package Plan?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Are you sure you want to remove package '$planId'? This cannot be undone.")
                if (subscriberCount > 0) {
                    Surface(
                        color = StatusError.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                            Text(
                                "$subscriberCount active subscriber${if (subscriberCount == 1) "" else "s"} will lose access on their next renewal check.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusError
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CustomButton(
                text = "Remove Package",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

/**
 * 6. Add Schema Node Dialog
 */
private val MARKER_COLOR_PRESETS = listOf(
    "#5B9BFF", "#FF8F73", "#7DD9A0", "#B197FC", "#E8C468", "#6FE3E3",
    "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD"
)

@Composable
private fun AdminAddSchemaItemDialog(
    initialCategory: String = SchemaCategory.DIVISION_TYPE,
    onDismiss: () -> Unit,
    onAdd: (name: String, category: String, description: String, iconName: String, maxSubdivisions: Int?, markerColor: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var maxSubdivisionsInput by remember { mutableStateOf("") }
    var markerColorInput by remember { mutableStateOf("") }

    val categories = listOf(
        SchemaCategory.SPACE_TYPE to "Space Type",
        SchemaCategory.DIVISION_TYPE to "Division Type",
        SchemaCategory.FACILITY to "Facility (whole-space)",
        SchemaCategory.AMENITY to "Amenity (subdivision)",
        SchemaCategory.RENTAL_STRATEGY to "Rental Strategy"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Architecture Schema Node", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                Text("Target Schema Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { (catKey, catLabel) ->
                        FilterChip(
                            selected = selectedCategory == catKey,
                            onClick = { selectedCategory = catKey },
                            label = { Text(catLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                InputField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Schema Node Name (e.g. Laser Surgery Suite)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                InputField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description / Lebanese Compliance Context",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )

                if (selectedCategory == "SPACE_TYPE") {
                    InputField(
                        value = maxSubdivisionsInput,
                        onValueChange = { maxSubdivisionsInput = it.filter { c -> c.isDigit() } },
                        label = "Max subdivisions this category includes (optional)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    // Map marker color picker
                    Text("Map Marker Color", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(MARKER_COLOR_PRESETS) { hex ->
                            val selected = markerColorInput.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        try { Color(android.graphics.Color.parseColor(hex)) }
                                        catch (e: Exception) { MaterialTheme.colorScheme.surfaceVariant }
                                    )
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { markerColorInput = hex }
                            )
                        }
                    }
                    InputField(
                        value = markerColorInput,
                        onValueChange = { markerColorInput = it.take(7) },
                        label = "Hex color (e.g. #5B9BFF) — optional",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED
                    )
                    CustomButton(
                        text = "Create Node",
                        onClick = {
                            if (name.isNotBlank()) {
                                val maxSub = if (selectedCategory == "SPACE_TYPE") maxSubdivisionsInput.toIntOrNull() else null
                                val colorVal = if (selectedCategory == "SPACE_TYPE") markerColorInput.takeIf { it.isNotBlank() } else null
                                onAdd(name, selectedCategory, description, "Category", maxSub, colorVal)
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.SECONDARY
                    )
                }
            }
        }
    }
}

/**
 * 7. Reset Schema Dialog
 */
@Composable
private fun AdminResetSchemaDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AmberWarning) },
        title = { Text("Reset Architecture Schema?") },
        text = {
            Text("This will restore all default Lebanese workspace classifications — space types, division types, faci" +
                "lities, amenities and rental strategies — while removing custom additions.")
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Reset",
                onClick = onConfirm,
                variant = CustomButtonVariant.PRIMARY
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

/**
 * Adds a Google Play subscription to the in-app catalog. The plan id IS the Play
 * subscription ID: playBillingRtdn.ts stores that ID as ownerPackageId, and the app
 * resolves the host's current plan via packages[ownerPackageId].
 */
@Composable
private fun AdminAddPackagePlanDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (PackagePlan) -> Unit
) {
    var productId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priorityInput by remember { mutableStateOf("0") }
    var featured by remember { mutableStateOf(false) }

    val cleanId = productId.trim()
    val idValid = cleanId.matches(Regex("[a-z0-9][a-z0-9._]{0,39}"))
    val idCollision = cleanId in existingIds
    val priority = priorityInput.toIntOrNull()
    val canAdd = idValid && !idCollision && name.isNotBlank() && priority != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                OutlinedTextField(
                    value = productId,
                    onValueChange = { productId = it.lowercase() },
                    label = { Text("Google Play subscription ID") },
                    placeholder = { Text("e.g. prohost_monthly") },
                    supportingText = {
                        Text(
                            when {
                                cleanId.isEmpty() -> "Must exactly match the subscription ID in Play Console."
                                !idValid -> "Lowercase letters, digits, dots and underscores only."
                                idCollision -> "This plan is already in the catalog."
                                else -> "Price and billing period are read from Google Play."
                            }
                        )
                    },
                    isError = cleanId.isNotEmpty() && (!idValid || idCollision),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = priorityInput,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) priorityInput = v },
                        label = { Text("Priority") },
                        supportingText = { Text("Lower shows first") },
                        isError = priority == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text("Featured", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = featured, onCheckedChange = { featured = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED
                    )
                    CustomButton(
                        text = "Add Plan",
                        onClick = {
                            onAdd(
                                PackagePlan(
                                    id = cleanId,
                                    name = name.trim(),
                                    description = description.trim(),
                                    isEnabled = true,
                                    sortOrder = priority ?: 0,
                                    isFeatured = featured,
                                    googlePlayProductId = cleanId
                                )
                            )
                        },
                        enabled = canAdd,
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.SECONDARY
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminAttendeePackageDialog(
    existingPackage: AttendeePackage?,
    onSave: (name: String, description: String, priceUsd: Double, inclusions: List<String>, minAttendees: Int, maxAttendees: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = existingPackage != null
    var name by remember { mutableStateOf(existingPackage?.name ?: "") }
    var description by remember { mutableStateOf(existingPackage?.description ?: "") }
    var priceText by remember {
        mutableStateOf(
            existingPackage?.pricePerAttendeeUsd?.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else String.format("%.2f", it)
            } ?: ""
        )
    }
    var minText by remember { mutableStateOf(existingPackage?.minAttendees?.toString() ?: "1") }
    var maxText by remember { mutableStateOf(existingPackage?.maxAttendees?.toString() ?: "") }
    var inclusionInput by remember { mutableStateOf("") }
    var inclusions by remember { mutableStateOf(existingPackage?.inclusions ?: emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Attendee Package" else "Add Attendee Package", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Package Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. Standard Package") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price / Person (USD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        prefix = { Text("$") }
                    )
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min Attendees") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text("Max Attendees (leave blank = no cap)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Inclusions", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inclusionInput,
                        onValueChange = { inclusionInput = it },
                        label = { Text("Add item") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = {
                        val item = inclusionInput.trim()
                        if (item.isNotBlank() && item !in inclusions) {
                            inclusions = inclusions + item
                            inclusionInput = ""
                        }
                    }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                }
                if (inclusions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(inclusions) { inc ->
                            FilterChip(
                                selected = false,
                                onClick = { inclusions = inclusions - inc },
                                label = { Text(inc, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            CustomButton(
                text = if (isEditing) "Save" else "Add Package",
                onClick = {
                    val price = priceText.trim().toDoubleOrNull() ?: 0.0
                    val min = minText.trim().toIntOrNull() ?: 1
                    val max = maxText.trim().toIntOrNull()
                    onSave(name.trim(), description.trim(), price, inclusions, min, max)
                },
                variant = CustomButtonVariant.PRIMARY,
                enabled = name.isNotBlank() && (priceText.trim().toDoubleOrNull() ?: -1.0) > 0
            )
        },
        dismissButton = {
            CustomButton(text = "Cancel", onClick = onDismiss, variant = CustomButtonVariant.OUTLINED)
        }
    )
}
