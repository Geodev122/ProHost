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
import com.example.data.crypto.WhishSecurity
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.state.AdminUiEvent
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
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
            .background(PremiumBackgroundGradient)
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
                            color = FreshGreen.copy(alpha = 0.12f),
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
                                        .background(FreshGreen, CircleShape)
                                )
                                Text(
                                    "Live",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FreshGreen,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
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
                        text = { Text("Revenue & Run-Rate", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { adminViewModel.setSelectedTab(1) },
                        text = { Text("Users Directory (${uiState.allUsers.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 2,
                        onClick = { adminViewModel.setSelectedTab(2) },
                        text = { Text("Listings Catalog (${uiState.allSpaces.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 3,
                        onClick = { adminViewModel.setSelectedTab(3) },
                        text = { Text("Owners & Payments", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
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
                        text = { Text("Transactions", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
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
                    0 -> AdminRevenueTab(uiState = uiState, adminViewModel = adminViewModel)
                    1 -> AdminUsersDirectoryTab(uiState = uiState, adminViewModel = adminViewModel)
                    2 -> AdminListingsCatalogTab(uiState = uiState, adminViewModel = adminViewModel)
                    3 -> AdminOwnersAndPaymentsTab(uiState = uiState, adminViewModel = adminViewModel)
                    4 -> AdminSchemaArchitectureTab(uiState = uiState, adminViewModel = adminViewModel)
                    5 -> AdminSecurityAuditTab(uiState = uiState, adminViewModel = adminViewModel, currentUser = currentUser)
                    // Transaction search/filter/CSV-export — kept as its own sub-tab
                    // rather than nested inside tab 0's run-rate LazyColumn (avoids
                    // nesting two scrollables) now that Package Revenue is no longer
                    // a separate top-level destination outside Admin Console.
                    6 -> AdminRevenueScreen(adminViewModel = adminViewModel)
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
    if (uiState.isEditUserDialogOpen && uiState.editingUser != null) {
        AdminEditUserDialog(
            user = uiState.editingUser!!,
            onDismiss = { adminViewModel.closeEditUserDialog() },
            onSave = { updatedUser -> adminViewModel.saveUser(updatedUser) }
        )
    }

    // 3. Delete User Confirmation Dialog
    if (uiState.isDeleteUserDialogOpen && uiState.deletingUser != null) {
        AdminDeleteUserDialog(
            user = uiState.deletingUser!!,
            onDismiss = { adminViewModel.closeDeleteUserDialog() },
            onConfirm = { adminViewModel.confirmDeleteUser(uiState.deletingUser!!.id) }
        )
    }

    // 3b. Grant Admin Confirmation Dialog
    if (uiState.isSuspendUserDialogOpen && uiState.suspendingUser != null) {
        AdminSuspendUserDialog(
            user = uiState.suspendingUser!!,
            onDismiss = { adminViewModel.closeSuspendUserDialog() },
            onConfirm = { adminViewModel.confirmToggleSuspend() }
        )
    }

    if (uiState.isGrantAdminDialogOpen && uiState.grantingAdminUser != null) {
        AdminGrantAdminDialog(
            user = uiState.grantingAdminUser!!,
            onDismiss = { adminViewModel.closeGrantAdminDialog() },
            onConfirm = { adminViewModel.confirmGrantAdmin(uiState.grantingAdminUser!!.email) }
        )
    }

    if (uiState.isRevokeProHostDialogOpen && uiState.revokingProHostUser != null) {
        AdminRevokeProHostDialog(
            user = uiState.revokingProHostUser!!,
            onDismiss = { adminViewModel.closeRevokeProHostDialog() },
            onConfirm = { adminViewModel.confirmRevokeProHost() }
        )
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
            spaceCategories = uiState.schema.spaceTypes
        )
    }

    // 5. Delete Listing Confirmation Dialog
    if (uiState.isDeleteListingDialogOpen && uiState.deletingListing != null) {
        AdminDeleteListingDialog(
            listing = uiState.deletingListing!!,
            onDismiss = { adminViewModel.closeDeleteListingDialog() },
            onConfirm = { adminViewModel.confirmDeleteListing(uiState.deletingListing!!.id) }
        )
    }

    // 6. Add Schema Node Dialog
    if (uiState.isAddSchemaItemDialogOpen) {
        AdminAddSchemaItemDialog(
            initialCategory = uiState.addSchemaItemPresetCategory ?: "SUBCATEGORY",
            onDismiss = { adminViewModel.closeAddSchemaItemDialog() },
            onAdd = { name, category, description, iconName, maxSubdivisions ->
                adminViewModel.addSchemaItem(category, name, description, iconName, maxSubdivisions)
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

    // 8. Add Package Plan Dialog — this used to just toggle isAddPackagePlanDialogOpen
    // with no dialog anywhere actually reading it, so "Add Package" was a fully dead
    // button; this is the real dialog it was always meant to open.
    if (uiState.isAddPackagePlanDialogOpen) {
        AdminAddPackagePlanDialog(
            existingIds = uiState.packagePlans.packages.keys,
            onDismiss = { adminViewModel.closeAddPackagePlanDialog() },
            onAdd = { plan -> adminViewModel.addPackagePlan(plan) }
        )
    }
}

// =========================================================================
// TAB 0: REVENUE & PRICING ENGINE
// =========================================================================
@Composable
private fun AdminRevenueTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Financial Run-Rate Summary Cards
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProSectionHeader(
                        title = "Run-Rate",
                        icon = Icons.AutoMirrored.Filled.TrendingUp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProMetricTile(
                            title = "Active MRR",
                            value = "$${String.format(Locale.US, "%.2f", uiState.activeMrr)}",
                            subtitle = "Active Subscriptions",
                            icon = Icons.Default.AccountBalance,
                            iconTint = FreshGreen,
                            modifier = Modifier.weight(1f)
                        )

                        ProMetricTile(
                            title = "100% Capacity MRR",
                            value = "$${String.format(Locale.US, "%.2f", uiState.potentialMrr)}",
                            subtitle = "Full Inventory Potential",
                            icon = Icons.Default.AllInclusive,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProMetricTile(
                            title = "Projected ARR",
                            value = "$${String.format(Locale.US, "%.2f", uiState.projectedArr)}",
                            subtitle = "Annualized Recurring Run-Rate",
                            icon = Icons.Default.CalendarToday,
                            iconTint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )

                        ProMetricTile(
                            title = "Whish Volume",
                            value = "$${String.format(Locale.US, "%.2f", uiState.totalSettlementVolume)}",
                            subtitle = "Total Settled via Whish Money",
                            icon = Icons.Default.Payments,
                            iconTint = WhishRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Owner Packages & Governance Hub Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProSectionHeader(
                        title = "Packages Configuration",
                        subtitle = "Create and edit Pro Host packages, and the Control Tag",
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
                        Text(
                            text = "Packages",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { adminViewModel.openAddPackagePlanDialog() },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Add Package", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (uiState.packagePlans.packages.isEmpty()) {
                        Text(
                            "No packages configured yet — add one above.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Each row is buffered locally and committed on its own Save (task
                    // #106's pattern), not on keystroke — replaces the old fixed
                    // Package-2/Package-3 fee+limit inputs with a real, admin-creatable
                    // list. Price/limit/validity are the trust boundary that matters:
                    // initiateWhishPayment.ts always re-reads this same package_plans
                    // doc server-side at charge time, never trusting the client.
                    uiState.packagePlans.packages.values.sortedBy { it.sortOrder }.forEach { plan ->
                        var nameInput by remember(plan.id, plan.name) { mutableStateOf(plan.name) }
                        var descInput by remember(plan.id, plan.description) { mutableStateOf(plan.description) }
                        var badgeInput by remember(plan.id, plan.badgeName) { mutableStateOf(plan.badgeName) }
                        var priceInput by remember(plan.id, plan.priceUsd) { mutableStateOf(plan.priceUsd.toString()) }
                        var unlimitedInput by remember(plan.id, plan.listingLimit) { mutableStateOf(plan.listingLimit == null) }
                        var limitInput by remember(plan.id, plan.listingLimit) { mutableStateOf((plan.listingLimit ?: 3).toString()) }
                        var validityInput by remember(plan.id, plan.validityDays) { mutableStateOf(plan.validityDays.toString()) }

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
                                Text("#${plan.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (plan.isEnabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (plan.isEnabled) FreshGreen else StatusError
                                    )
                                    Switch(checked = plan.isEnabled, onCheckedChange = { adminViewModel.togglePackagePlan(plan.id) })
                                    IconButton(onClick = { adminViewModel.deletePackagePlan(plan.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete package", tint = StatusError)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = descInput,
                                onValueChange = { descInput = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = badgeInput,
                                    onValueChange = { badgeInput = it },
                                    label = { Text("Badge text") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = priceInput,
                                    onValueChange = { priceInput = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Price ($)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!unlimitedInput) {
                                    OutlinedTextField(
                                        value = limitInput,
                                        onValueChange = { limitInput = it.filter { c -> c.isDigit() } },
                                        label = { Text("Listing limit") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(
                                    modifier = if (unlimitedInput) Modifier.weight(1f) else Modifier,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Unlimited", style = MaterialTheme.typography.labelSmall)
                                    Switch(checked = unlimitedInput, onCheckedChange = { unlimitedInput = it })
                                }
                                OutlinedTextField(
                                    value = validityInput,
                                    onValueChange = { validityInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Validity (days)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Button(
                                onClick = {
                                    adminViewModel.updatePackagePlan(
                                        plan.copy(
                                            name = nameInput.ifBlank { plan.name },
                                            description = descInput,
                                            badgeName = badgeInput,
                                            priceUsd = priceInput.toDoubleOrNull() ?: plan.priceUsd,
                                            listingLimit = if (unlimitedInput) null else (limitInput.toIntOrNull()?.takeIf { it >= 1 } ?: plan.listingLimit),
                                            validityDays = validityInput.toIntOrNull()?.takeIf { it >= 1 } ?: plan.validityDays
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Save Package")
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
                        subtitle = "Download instant CSV & JSON snapshots for auditing",
                        icon = Icons.Default.CloudDownload
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportTxtFile("prohost_full_audit.txt", adminViewModel.getFullAuditReport()) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                        ) {
                            Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Full Audit (TXT)", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { exportJsonFile("prohost_master_export.json", adminViewModel.getMasterJsonExport()) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Master (JSON)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
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
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Users Governance Directory",
                            subtitle = "Inspect, edit, verify, or remove user records",
                            icon = Icons.Default.People
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { adminViewModel.exportUsersDirectory("CSV") },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("CSV", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { adminViewModel.exportUsersDirectory("JSON") },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("JSON", style = MaterialTheme.typography.labelSmall)
                            }
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
                                isIdVerified = user.idDocumentUrl != null,
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
                            Text(user.city.ifBlank { user.governorate.ifBlank { user.country } }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                        Column {
                            Text("ID Document:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (user.idDocumentUrl != null) "On File" else "Missing", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
                        OutlinedButton(
                            onClick = { adminViewModel.openEditUserDialog(user) },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }

                        // Grant Admin Button
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openGrantAdminDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Grant Admin", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                            Button(
                                onClick = { adminViewModel.exportListingsCatalog("CSV") },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("CSV", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { adminViewModel.exportListingsCatalog("JSON") },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("JSON", style = MaterialTheme.typography.labelSmall)
                            }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "ACTIVE_30D" to "Active", "EXPIRED" to "Expired", "VERIFIED" to "Verified").forEach { (key, label) ->
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
                                text = "📍 ${space.district}, ${space.governorate.displayName.split(" ").first()} • ${space.streetAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "$${space.baseMonthlyRateUsd.toInt()}/mo",
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
                        }
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
                        OutlinedButton(
                            onClick = { adminViewModel.toggleListingSubscription(space.id, space.isActiveSubscription) },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (space.isActiveSubscription) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = if (space.isActiveSubscription) "Deactivate" else "Activate",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Toggle Verified
                        OutlinedButton(
                            onClick = { adminViewModel.toggleListingVerification(space.id) },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = if (space.isVerified) "Unverify" else "Verify",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Edit Button
                        OutlinedButton(
                            onClick = { adminViewModel.openEditListingDialog(space) },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(0.8f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }

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
// TAB 3: OWNERS & PAYMENTS (WHISH MONEY LEDGER)
// =========================================================================
/** Three cumulative-count/total lines for the Hosts & Properties chart, each bucketed
 * by day within the optional [fromMillis]/[toMillis] range (null = unbounded, same
 * "Any" semantics as DateRangePickerRow). Pro Host upgrades come from the one
 * reliable dated record of that event — audit log entries with
 * actionType == "ROLE_PROMOTED_PRO_HOST" — the promotion write itself only stamps a
 * generic updatedAt that many other things overwrite too. Properties-listed uses the
 * new server-stamped SpaceListing.createdAtMillis (null/missing for any listing
 * created before that field existed — simply excluded, not backfilled). Whish
 * settlements only count SUCCESS transactions, matching the settled-volume fix
 * elsewhere in this tab. */
private fun computeHostsAndPropertiesSeries(
    auditLogs: List<AuditSecurityLog>,
    spaces: List<SpaceListing>,
    transactions: List<WhishTransaction>,
    fromMillis: Long?,
    toMillis: Long?
): List<ChartSeries> {
    fun inRange(millis: Long) = (fromMillis == null || millis >= fromMillis) && (toMillis == null || millis <= toMillis)

    fun dayBucket(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun cumulative(events: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val byDay = events.groupBy { dayBucket(it.first) }.mapValues { (_, v) -> v.sumOf { it.second } }
        var running = 0.0
        return byDay.toSortedMap().map { (day, dayTotal) ->
            running += dayTotal
            day to running
        }
    }

    val upgrades = auditLogs
        .filter { it.actionType == "ROLE_PROMOTED_PRO_HOST" && inRange(it.timestamp) }
        .map { it.timestamp to 1.0 }
    val listed = spaces
        .mapNotNull { it.createdAtMillis }
        .filter { inRange(it) }
        .map { it to 1.0 }
    val settled = transactions
        .filter { it.status == TransactionStatus.SUCCESS && inRange(it.timestamp) }
        .map { it.timestamp to it.amountUsd }

    return listOf(
        ChartSeries("Pro Host Upgrades", FreshGreen, cumulative(upgrades)),
        ChartSeries("Properties Listed", VibrantBlue, cumulative(listed)),
        ChartSeries("Whish Settlements ($)", CarnationOrange, cumulative(settled))
    )
}

@Composable
private fun AdminOwnersAndPaymentsTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Space Hosts Summary
        item {
            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Hosts & Properties",
                            icon = Icons.Default.HomeWork
                        )

                        Button(
                            onClick = { adminViewModel.exportOwnerRegistrations() },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Export Hosts", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    var chartFromMillis by remember { mutableStateOf<Long?>(null) }
                    var chartToMillis by remember { mutableStateOf<Long?>(null) }
                    DateRangePickerRow(
                        fromMillis = chartFromMillis,
                        toMillis = chartToMillis,
                        onFromChange = { chartFromMillis = it },
                        onToChange = { chartToMillis = it }
                    )

                    val series = remember(uiState.auditLogs, uiState.allSpaces, uiState.allTransactions, chartFromMillis, chartToMillis) {
                        computeHostsAndPropertiesSeries(uiState.auditLogs, uiState.allSpaces, uiState.allTransactions, chartFromMillis, chartToMillis)
                    }
                    MultiSeriesLineChart(series = series)
                }
            }
        }

        // Whish Pay Cryptographic Protocol Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = WhishRed,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "Whish Pay Gateway Protocol",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { adminViewModel.exportTransactionsLedger() },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Export Ledger", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = "• Channel ID: ${WhishSecurity.CHANNEL_ID}\n" +
                                "• Website / Source: ${WhishSecurity.SOURCE_EMAIL}\n" +
                                "• Hash Signature: SHA-256(channel|amount|currency|orderId|secretKey)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Search & Status Filters for Transactions
                    InputField(
                        value = uiState.txSearchQuery,
                        onValueChange = { adminViewModel.setTxSearchQuery(it) },
                        label = "Search Tx by Order ID, Payer Name, Phone...",
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All (${uiState.allTransactions.size})", "SUCCESS" to "Success", "PENDING" to "Pending", "FAILED" to "Failed").forEach { (key, label) ->
                            FilterChip(
                                selected = uiState.selectedTxStatusFilter == key,
                                onClick = { adminViewModel.setTxStatusFilter(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        item {
            ProSectionHeader(
                title = "Live Audit Transactions (${uiState.filteredTransactions.size})",
                subtitle = "Cryptographically signed checkout events with SHA-256 signatures",
                icon = Icons.AutoMirrored.Filled.ReceiptLong
            )
        }

        if (uiState.filteredTransactions.isEmpty()) {
            item {
                ProEmptyState(
                    title = "No Transactions Found",
                    description = "No transaction records match the specified filters.",
                    icon = Icons.Default.Receipt
                )
            }
        }

        items(uiState.filteredTransactions, key = { it.id }) { tx ->
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (tx.status) {
                            TransactionStatus.SUCCESS -> ProStatusBadge(ProBadgeType.CUSTOM_SUCCESS, customText = "SUCCESS")
                            TransactionStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "PENDING")
                            TransactionStatus.FAILED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "FAILED")
                        }

                        Text(
                            text = "$${String.format(Locale.US, "%.2f", tx.amountUsd)} USD",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = tx.spaceTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Payer:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${tx.payerName} (${tx.payerPhone})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Order ID:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tx.orderId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                // Not MD5 (the signing function is SHA-256) and not something
                                // this client — or an admin reading it — ever verifies; it's
                                // just the server's own audit record of what it sent Whish.
                                text = "Server signature (audit record): ${tx.signatureHash}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(tx.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: SPACE ARCHITECTURE SCHEMA (DATABASE MAPPINGS)
// =========================================================================
@Composable
private fun AdminSchemaArchitectureTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    val schema = uiState.schema
    // Rental Formulas get their own independent section below (they're decorative
    // reference labels, not the real per-listing pricing config — see that section's own
    // comment) — this list, its filter, and its stat tiles are scoped to everything else.
    val mainSchemaItems = schema.spaceTypes + schema.subcategories + schema.amenities + schema.equipmentCategories
    val filteredItems = if (uiState.selectedSchemaCategoryFilter == "ALL") {
        mainSchemaItems
    } else {
        mainSchemaItems.filter { it.category == uiState.selectedSchemaCategoryFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Schema Header & Overview
        item {
            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Architecture",
                            icon = Icons.Default.AccountTree
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { adminViewModel.openResetSchemaDialog() },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Reset Defaults", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = { adminViewModel.openAddSchemaItemDialog() },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Add Node", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Database Architecture Stats — one horizontally-scrollable row of 5
                    // (Specialties removed — fully migrated to the free-text hashtag system,
                    // see the Target Disciplines Usage card below), each showing real live
                    // active-vs-disabled counts rather than just a bare total.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tiles = listOf(
                            Triple("Space Types", schema.spaceTypes, Icons.Default.Apartment),
                            Triple("Subcategories", schema.subcategories, Icons.Default.Category),
                            Triple("Amenities", schema.amenities, Icons.Default.CheckCircle),
                            Triple("Equipment", schema.equipmentCategories, Icons.Default.Biotech),
                            Triple("Rental Formulas", schema.rentalStrategies, Icons.Default.Schedule)
                        )
                        items(tiles) { (title, items, icon) ->
                            val activeCount = items.count { it.isEnabled }
                            val offCount = items.size - activeCount
                            Box(modifier = Modifier.width(150.dp)) {
                                ProMetricTile(
                                    title = title,
                                    value = "${items.size}",
                                    subtitle = "$activeCount active · $offCount off",
                                    icon = icon
                                )
                            }
                        }
                    }

                    // Category Selector Filter Chips
                    Text("Filter Schema Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val categories = listOf(
                            "ALL" to "All Nodes (${mainSchemaItems.size})",
                            "SPACE_TYPE" to "Space Types (${schema.spaceTypes.size})",
                            "SUBCATEGORY" to "Subcategories (${schema.subcategories.size})",
                            "AMENITY" to "Amenities (${schema.amenities.size})",
                            "EQUIPMENT" to "Equipment (${schema.equipmentCategories.size})"
                        )
                        items(categories) { (catKey, catLabel) ->
                            FilterChip(
                                selected = uiState.selectedSchemaCategoryFilter == catKey,
                                onClick = { adminViewModel.setSchemaCategoryFilter(catKey) },
                                label = { Text(catLabel) }
                            )
                        }
                    }
                }
            }
        }

        // Target Disciplines (Hashtag) usage analytics — spec 1.4's admin analytics
        // requirement. governorate here is whichever listing most recently used the
        // tag, not a full per-governorate breakdown; this is a single-country
        // deployment today, so a fuller breakdown wasn't worth the added complexity.
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Target Disciplines Usage",
                            subtitle = "Hashtags hosts use to describe preferred rentee backgrounds",
                            icon = Icons.Default.Info
                        )
                        IconButton(onClick = { adminViewModel.refreshHashtagAnalytics() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    if (uiState.hashtagAnalytics.isEmpty()) {
                        Text(
                            "No hashtags recorded yet.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

        // Schema Items List
        item {
            Text(
                text = "Schema Items (${filteredItems.size} configured in local & cloud registry)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(filteredItems, key = { it.id }) { item ->
            ProSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = if (item.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (item.category) {
                                        "SPACE_TYPE" -> Icons.Default.Apartment
                                        "SUBCATEGORY" -> Icons.Default.Category
                                        "AMENITY" -> Icons.Default.CheckCircle
                                        "EQUIPMENT" -> Icons.Default.Biotech
                                        "RENTAL_STRATEGY" -> Icons.Default.Schedule
                                        else -> Icons.Default.AccountTree
                                    },
                                    contentDescription = null,
                                    tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!item.isSystemDefault) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = CarnationOrangeContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "CUSTOM NODE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CarnationOrange,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Category: ${item.category} • ID: ${item.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                            if (item.description.isNotBlank()) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                )
                            }
                            if (item.category == "SPACE_TYPE") {
                                Text(
                                    text = "Max subdivisions: ${item.maxSubdivisions?.toString() ?: "Unlimited"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = item.isEnabled,
                            onCheckedChange = { adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) }
                        )

                        if (!item.isSystemDefault) {
                            IconButton(onClick = { adminViewModel.deleteSchemaItem(item.id, item.category) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Renting Formulas — an independent section, deliberately separate from the
        // category/subdivision/amenity/equipment list above: these are reference labels
        // only (they mirror the 4 real RentalStrategyType values a host actually
        // configures per-listing in the wizard's Step 3 pricing editors) — renaming or
        // disabling one here doesn't change what a host can select or how a listing is
        // priced. Kept for descriptive/reference purposes only.
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Renting Formulas",
                            subtitle = "Reference labels only — real per-listing pricing is configured in the listing wizard",
                            icon = Icons.Default.Schedule
                        )
                        Button(
                            onClick = { adminViewModel.openAddSchemaItemDialog(presetCategory = "RENTAL_STRATEGY") },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Add Formula", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    schema.rentalStrategies.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (item.description.isNotBlank()) {
                                    Text(
                                        item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { adminViewModel.toggleSchemaItemEnabled(item.id, item.category, item.isEnabled) }
                                )
                                if (!item.isSystemDefault) {
                                    IconButton(onClick = { adminViewModel.deleteSchemaItem(item.id, item.category) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = LightGray.copy(alpha = 0.4f))
                    }
                }
            }
        }

        // Firestore Database Schema Reference Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(20.dp))
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
                                "• Collection: 'whish_transactions' (Documents: WhishTransaction)\n" +
                                "• Collection: 'audit_security_logs' (Documents: AuditSecurityLog)\n" +
                                "• Collection: 'system_metadata' (Documents: AdminPricingState)\n" +
                                "• Collection: 'hashtag_usage' (Documents: HashtagUsageEntry)\n" +
                                "• Collection: 'schema_architecture' (Documents: SpaceArchitectureSchema)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        }
    }
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

                    Button(
                        onClick = {
                            val csv = adminViewModel.exportAuditLogsCsv(auditExportFromMillis, auditExportToMillis)
                            exportAuditCsvFile("prohost_audit_logs.csv", csv)
                        },
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Export Audit Logs (CSV)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            val context = LocalContext.current
            ProSurfaceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Legal Documents",
                        subtitle = "Upload a new file to publish a new version — prior versions are kept, never overwritten",
                        icon = Icons.Default.Gavel
                    )
                    data class LegalDocSlot(val docId: String, val title: String, val mimeType: String, val extension: String, val noPublishedCopyIsBlocking: Boolean)
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
                                    color = if (current != null || !slot.noPublishedCopyIsBlocking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedButton(
                                enabled = !isUploading,
                                onClick = { pickerLauncher.launch(slot.mimeType) },
                                shape = MaterialTheme.shapes.small
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    if (current != null) "Upload New Version" else "Upload",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
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
                        val isHighSeverity = log.severity.contains("WARN", ignoreCase = true) || log.severity.contains("CRIT", ignoreCase = true) || log.severity.contains("HIGH", ignoreCase = true)
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
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(title, content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Copy Text")
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Export Data"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Share / Export")
                    }
                }
            }
        }
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
                InputField(value = specialty, onValueChange = { specialty = it }, label = "Specialty / Profession", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = country, onValueChange = { country = it }, label = "Country", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = governorateArea, onValueChange = { governorateArea = it }, label = "Governorate / Area", modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                        Text("Cancel")
                    }
                    Button(
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
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                    ) {
                        Text("Save Changes")
                    }
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
            Text("Are you sure you want to permanently remove '${user.fullName}' (${user.email})'s profile from the platform? Their sign-in credentials are not revoked by this action.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError)
            ) {
                Text("Confirm Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
            Text("Are you sure you want to grant full Admin privileges to '${user.fullName}' (${user.email})? This gives them unrestricted access to governance, pricing, and user management.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm Grant")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
                    "'${user.fullName}' (${user.email}) will be signed out and unable to sign back in, create listings, or submit booking requests until reactivated. Their data and history are kept — this is not a deletion."
                } else {
                    "'${user.fullName}' (${user.email}) will regain full access immediately."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (suspending) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) {
                Text(if (suspending) "Confirm Suspend" else "Confirm Reactivate")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
                "'${user.fullName}' (${user.email}) will be downgraded back to Specialist immediately. Every listing they've published will be marked inactive/expired (still visible in Discovery unless the specialist filters for active-subscription only, but shown as expired — not deleted). This does not affect their ability to book workspaces as a Specialist."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirm Revoke")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
            Text("Are you sure you want to delete '${listing.title}' in ${listing.district}? This will remove it from discovery and cancel any active rental bookings.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError)
            ) {
                Text("Confirm Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 6. Add Schema Node Dialog
 */
@Composable
private fun AdminAddSchemaItemDialog(
    initialCategory: String = "SUBCATEGORY",
    onDismiss: () -> Unit,
    onAdd: (name: String, category: String, description: String, iconName: String, maxSubdivisions: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var maxSubdivisionsInput by remember { mutableStateOf("") }

    val categories = listOf(
        "SPACE_TYPE" to "Space Type (Root)",
        "SUBCATEGORY" to "Subcategory (Level 2)",
        "AMENITY" to "Amenity / Facility",
        "EQUIPMENT" to "Equipment Category",
        "RENTAL_STRATEGY" to "Rental Time Formula"
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
                            label = { Text(catLabel, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
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
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val maxSub = if (selectedCategory == "SPACE_TYPE") maxSubdivisionsInput.toIntOrNull() else null
                                onAdd(name, selectedCategory, description, "Category", maxSub)
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                    ) {
                        Text("Create Node")
                    }
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
            Text("This will restore all default Lebanese workspace classifications (OEA, LOP, BBA subcategories, solar amenities, and medical equipment) while removing custom additions.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
            ) {
                Text("Confirm Reset")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 8. Add Package Plan Dialog — this used to be fully missing: the "Add Package"
 * button toggled isAddPackagePlanDialogOpen but no composable ever read that
 * state, so nothing happened when tapped and no package could ever be created
 * from the Admin Console.
 */
@Composable
private fun AdminAddPackagePlanDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (PackagePlan) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var badgeName by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var unlimited by remember { mutableStateOf(false) }
    var limitInput by remember { mutableStateOf("") }
    var validityInput by remember { mutableStateOf("30") }

    // Derived from the name so the admin never has to think about it, but still
    // shown read-only — collisions (e.g. re-adding "LIMITED_3_TIER") are refused
    // client-side with a clear message rather than silently overwriting an
    // existing package via a same-id merge write.
    val derivedId = remember(name) {
        name.trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifBlank { "PACKAGE" }
    }
    val idCollision = derivedId in existingIds

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
                    Text("Add Package", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                InputField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Package Name (e.g. Growth Plan)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (name.isNotBlank()) {
                    Text(
                        if (idCollision) "A package with id \"$derivedId\" already exists — choose a different name." else "Package id: $derivedId",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idCollision) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                InputField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )

                InputField(
                    value = badgeName,
                    onValueChange = { badgeName = it },
                    label = "Badge text (shown in the drawer/hero)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                InputField(
                    value = priceInput,
                    onValueChange = { priceInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Price (USD)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!unlimited) {
                        InputField(
                            value = limitInput,
                            onValueChange = { limitInput = it.filter { c -> c.isDigit() } },
                            label = "Listing limit",
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = if (unlimited) Modifier.weight(1f) else Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Unlimited", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = unlimited, onCheckedChange = { unlimited = it })
                    }
                    InputField(
                        value = validityInput,
                        onValueChange = { validityInput = it.filter { c -> c.isDigit() } },
                        label = "Validity (days)",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val canAdd = name.isNotBlank() && !idCollision &&
                    priceInput.toDoubleOrNull() != null &&
                    (unlimited || limitInput.toIntOrNull()?.let { it >= 1 } == true) &&
                    (validityInput.toIntOrNull()?.let { it >= 1 } == true)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onAdd(
                                PackagePlan(
                                    id = derivedId,
                                    name = name.trim(),
                                    description = description,
                                    badgeName = badgeName,
                                    priceUsd = priceInput.toDoubleOrNull() ?: 0.0,
                                    listingLimit = if (unlimited) null else limitInput.toIntOrNull(),
                                    validityDays = validityInput.toIntOrNull() ?: 30,
                                    isEnabled = true
                                )
                            )
                        },
                        enabled = canAdd,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                    ) {
                        Text("Add Package")
                    }
                }
            }
        }
    }
}
