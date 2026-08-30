package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.crypto.WhishSecurity
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.components.dialogs.SystemDebuggerDialog
import com.example.ui.state.AdminUiEvent
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.ProSpaceViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    viewModel: ProSpaceViewModel,
    adminViewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by adminViewModel.uiState.collectAsState()
    var isDebuggerDialogOpen by remember { mutableStateOf(false) }

    // Listen to admin events (Toasts & export triggers)
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
                    // Export dialog is opened
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
                            shape = RoundedCornerShape(12.dp),
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
                        Spacer(modifier = Modifier.width(12.dp))
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
                                text = "geo.elnajjar@gmail.com • Central Node",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Badges Group
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProStatusBadge(
                            type = ProBadgeType.SUPER_ADMIN,
                            modifier = Modifier.wrapContentWidth()
                        )
                        Surface(
                            color = FreshGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isDebuggerDialogOpen = true },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 36.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "System Debugger",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = { adminViewModel.exportAllCsv() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 36.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Export Master",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
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
                        text = { Text("Schema Architecture", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 5,
                        onClick = { adminViewModel.setSelectedTab(5) },
                        text = { Text("Security Audit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
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
                    5 -> AdminSecurityAuditTab(uiState = uiState, adminViewModel = adminViewModel)
                }
            }
        }
    }

    // ==========================================
    // ALL ADMIN MODAL DIALOGS
    // ==========================================

    // System & Firebase Compliance Debugger
    if (isDebuggerDialogOpen) {
        SystemDebuggerDialog(
            repository = viewModel.repository,
            onDismissRequest = { isDebuggerDialogOpen = false }
        )
    }

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

    // 4. Edit Listing Dialog
    if (uiState.isEditListingDialogOpen && uiState.editingListing != null) {
        AdminEditListingDialog(
            listing = uiState.editingListing!!,
            onDismiss = { adminViewModel.closeEditListingDialog() },
            onSave = { updatedListing -> adminViewModel.saveListing(updatedListing) }
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
            onDismiss = { adminViewModel.closeAddSchemaItemDialog() },
            onAdd = { name, category, description, iconName ->
                adminViewModel.addSchemaItem(name, category, description, iconName)
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
        // Governance Exemption Badge
        item {
            Surface(
                color = StatusWarningContainer,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Admin Governance Exemption Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusOnWarningContainer
                        )
                        Text(
                            text = "As the governance authority (geo.elnajjar@gmail.com), administrator profiles are exempt from front-user document uploads (National ID / Syndicate permits) and hold direct supervisory authority over all platform schemas, fee rates, and listings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOnWarningContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Financial Run-Rate Summary Cards
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProSectionHeader(
                        title = "Financial Run-Rate Calculations",
                        subtitle = "Real-time SaaS recurring metrics and Lebanese commercial projections",
                        icon = Icons.AutoMirrored.Filled.TrendingUp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProMetricTile(
                            title = "Active MRR",
                            value = "$${String.format(Locale.US, "%.2f", uiState.activeMrr)}",
                            subtitle = "Active 30d Subscriptions",
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

        // Dynamic Subscription Pricing Engine Controller
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Dynamic Pricing Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Space owner monthly subscription fee corridor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { adminViewModel.resetSubscriptionFeeBaseline() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset $1.80", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Active Fee Hero Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Subscription Fee:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", uiState.pricingState.monthlySubscriptionFeeUsd)} USD/mo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Customizable Preset Chips:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Preset Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.pricingState.presetOptions) { preset ->
                            val isSelected = Math.abs(uiState.pricingState.monthlySubscriptionFeeUsd - preset) < 0.01
                            FilterChip(
                                selected = isSelected,
                                onClick = { adminViewModel.setSubscriptionFee(preset) },
                                label = {
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", preset)}",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text(
                        text = "Fine-Tuning Slider ($0.50 - $15.00 USD):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = uiState.pricingState.monthlySubscriptionFeeUsd.toFloat(),
                        onValueChange = { adminViewModel.setSubscriptionFee(Math.round(it * 100.0) / 100.0) },
                        valueRange = 0.50f..15.00f,
                        steps = 28
                    )
                }
            }
        }

        // Owner Packages & Governance Hub Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProSectionHeader(
                        title = "Owner Packages & Governance Hub",
                        subtitle = "Configure Package Tiers, PAYG fees per workspace type, and Control Tag",
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

                    Text(
                        text = "Package 1: Pay As You Go (Per-Listing Fees by Type)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.pricingState.paygPrivateOfficeUsd.toString(),
                            onValueChange = { val d = it.toDoubleOrNull(); if (d != null) adminViewModel.updatePaygFee(SpaceType.PRIVATE_OFFICE, d) },
                            label = { Text("Private Office ($)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.pricingState.paygCenterUsd.toString(),
                            onValueChange = { val d = it.toDoubleOrNull(); if (d != null) adminViewModel.updatePaygFee(SpaceType.CENTER, d) },
                            label = { Text("Center ($)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.pricingState.paygPolyclinicUsd.toString(),
                            onValueChange = { val d = it.toDoubleOrNull(); if (d != null) adminViewModel.updatePaygFee(SpaceType.POLYCLINIC, d) },
                            label = { Text("Polyclinic ($)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.pricingState.paygCoworkingUsd.toString(),
                            onValueChange = { val d = it.toDoubleOrNull(); if (d != null) adminViewModel.updatePaygFee(SpaceType.COWORKING_SPACE, d) },
                            label = { Text("Coworking ($)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Package 2 (3 Listings Limit) & Package 3 (Unlimited)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    var pkg2Fee by remember(uiState.pricingState.package2MonthlyFeeUsd) { mutableStateOf(uiState.pricingState.package2MonthlyFeeUsd.toString()) }
                    var pkg3Fee by remember(uiState.pricingState.package3MonthlyFeeUsd) { mutableStateOf(uiState.pricingState.package3MonthlyFeeUsd.toString()) }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pkg2Fee,
                            onValueChange = { pkg2Fee = it },
                            label = { Text("Pkg 2 Fee ($/mo)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pkg3Fee,
                            onValueChange = { pkg3Fee = it },
                            label = { Text("Pkg 3 Fee ($/mo)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            val f2 = pkg2Fee.toDoubleOrNull() ?: 3.99
                            val f3 = pkg3Fee.toDoubleOrNull() ?: 8.99
                            adminViewModel.updatePackageFees(f2, f3)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Package Bundle Fees")
                    }
                }
            }
        }

        // Quick Export Hub Shortcuts
        item {
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
                            onClick = { adminViewModel.exportAllAuditReport() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                        ) {
                            Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Full Audit (TXT)", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { adminViewModel.exportAllJson() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
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
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { adminViewModel.exportUsersDirectory("JSON") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("JSON", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Search Field
                    InputField(
                        value = uiState.userSearchQuery,
                        onValueChange = { adminViewModel.setUserSearchQuery(it) },
                        label = "Search by Name, Email, Syndicate, Specialty, Phone...",
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

                    // Status Filter Chips
                    Text("Filter by Verification:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedUserStatusFilter == null,
                                onClick = { adminViewModel.setUserStatusFilter(null) },
                                label = { Text("All Statuses") }
                            )
                        }
                        items(MemberVerificationStatus.entries) { status ->
                            FilterChip(
                                selected = uiState.selectedUserStatusFilter == status,
                                onClick = { adminViewModel.setUserStatusFilter(status) },
                                label = { Text(status.displayName) }
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

                        // Role Badge
                        Surface(
                            color = when (user.role) {
                                UserRole.ADMIN -> StatusWarningContainer
                                UserRole.SPACE_OWNER -> CarnationOrangeContainer
                                UserRole.PROFESSIONAL -> OxfordBlueContainer
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = user.role.name.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (user.role) {
                                    UserRole.ADMIN -> AmberWarning
                                    UserRole.SPACE_OWNER -> CarnationOrange
                                    UserRole.PROFESSIONAL -> OxfordBlue
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // User Details Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Syndicate / ID:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.syndicateNumber.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Territory:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.governorate.displayName.split(" ").first(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Trust Score:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${user.trustScore}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = FreshGreen)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Phone / WhatsApp:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.phone.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            Text("Affiliation:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.affiliation.ifBlank { "Independent" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Verification Button
                        OutlinedButton(
                            onClick = { adminViewModel.toggleUserVerification(user.id) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (user.isVerified) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (user.isVerified) StatusError else StatusSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (user.isVerified) "Revoke Verify" else "Accredit User",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Edit Button
                        OutlinedButton(
                            onClick = { adminViewModel.openEditUserDialog(user) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
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
                            title = "Listings Catalog & Governance",
                            subtitle = "Modify, verify, activate 30-day passes, or remove listings",
                            icon = Icons.Default.Apartment
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { adminViewModel.exportListingsCatalog("CSV") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CSV", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { adminViewModel.exportListingsCatalog("JSON") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
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

                    // Space Type Filter Chips
                    Text("Filter by Workspace Type:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedListingTypeFilter == null,
                                onClick = { adminViewModel.setListingTypeFilter(null) },
                                label = { Text("All Types (${uiState.allSpaces.size})") }
                            )
                        }
                        items(SpaceType.entries) { type ->
                            val count = uiState.allSpaces.count { it.spaceType == type }
                            FilterChip(
                                selected = uiState.selectedListingTypeFilter == type,
                                onClick = { adminViewModel.setListingTypeFilter(type) },
                                label = { Text("${type.displayName} ($count)") }
                            )
                        }
                    }

                    // Status Filter
                    Text("Filter by Subscription / Verification:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "ACTIVE_30D" to "Active 30d", "EXPIRED" to "Expired", "VERIFIED" to "Verified").forEach { (key, label) ->
                            FilterChip(
                                selected = uiState.selectedListingStatusFilter == key,
                                onClick = { adminViewModel.setListingStatusFilter(key) },
                                label = { Text(label) }
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
                            shape = RoundedCornerShape(6.dp)
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
                        // Toggle 30d Active
                        OutlinedButton(
                            onClick = { adminViewModel.toggleListingSubscription(space.id, space.isActiveSubscription) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (space.isActiveSubscription) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (space.isActiveSubscription) "Expire 30d" else "Activate 30d",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Toggle Verified
                        OutlinedButton(
                            onClick = { adminViewModel.toggleListingVerification(space.id) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (space.isVerified) "Unverify" else "Verify",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Edit Button
                        OutlinedButton(
                            onClick = { adminViewModel.openEditListingDialog(space) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.8f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
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
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Workspace Hosts & Property Ownership",
                            subtitle = "Monitor registered space owners, owned units, and subscription standing",
                            icon = Icons.Default.HomeWork
                        )

                        Button(
                            onClick = { adminViewModel.exportOwnerRegistrations() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Hosts", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProMetricTile(
                            title = "Total Hosts",
                            value = "${uiState.ownerUsers.size}",
                            subtitle = "Registered Owners",
                            icon = Icons.Default.Person,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Active Units",
                            value = "${uiState.allSpaces.count { it.isActiveSubscription }}",
                            subtitle = "Active 30d Listings",
                            icon = Icons.Default.CheckCircle,
                            iconTint = FreshGreen,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Whish Settled",
                            value = "$${String.format(Locale.US, "%.0f", uiState.totalSettlementVolume)}",
                            subtitle = "Gross Volume",
                            icon = Icons.Default.Paid,
                            iconTint = WhishRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Whish Pay Gateway Protocol",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { adminViewModel.exportTransactionsLedger() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Ledger", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = "• Channel ID: ${WhishSecurity.CHANNEL_ID}\n" +
                                "• Website / Source: ${WhishSecurity.SOURCE_EMAIL}\n" +
                                "• Hash Signature: MD5(channel|amount|currency|orderId|secretKey)",
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
                subtitle = "Cryptographically signed checkout events with MD5 signatures",
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
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "MD5 Hash Signature: ${tx.signatureHash}",
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
    val allItems = schema.allItems
    val filteredItems = if (uiState.selectedSchemaCategoryFilter == "ALL") {
        allItems
    } else {
        allItems.filter { it.category == uiState.selectedSchemaCategoryFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Schema Header & Overview
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "Space Architecture Schema",
                            subtitle = "Modify database taxonomies, categories, amenities, and rental strategies",
                            icon = Icons.Default.AccountTree
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { adminViewModel.openResetSchemaDialog() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = { adminViewModel.openAddSchemaItemDialog() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Node", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Database Architecture Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProMetricTile(
                            title = "Space Types",
                            value = "${schema.spaceTypes.size}",
                            subtitle = "Tier 1 Root",
                            icon = Icons.Default.Apartment,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Subcategories",
                            value = "${schema.subcategories.size}",
                            subtitle = "Level 2 Class",
                            icon = Icons.Default.Category,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Amenities",
                            value = "${schema.amenities.size}",
                            subtitle = "Facilities",
                            icon = Icons.Default.CheckCircle,
                            iconTint = FreshGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProMetricTile(
                            title = "Equipment",
                            value = "${schema.equipmentCategories.size}",
                            subtitle = "Hardware/Assets",
                            icon = Icons.Default.Biotech,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Specialties",
                            value = "${schema.specialties.size}",
                            subtitle = "Orders & Syndicates",
                            icon = Icons.Default.Badge,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Strategies",
                            value = "${schema.rentalStrategies.size}",
                            subtitle = "Time Formulas",
                            icon = Icons.Default.Schedule,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Category Selector Filter Chips
                    Text("Filter Schema Category:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val categories = listOf(
                            "ALL" to "All Nodes (${allItems.size})",
                            "SPACE_TYPE" to "Space Types (${schema.spaceTypes.size})",
                            "SUBCATEGORY" to "Subcategories (${schema.subcategories.size})",
                            "AMENITY" to "Amenities (${schema.amenities.size})",
                            "EQUIPMENT" to "Equipment (${schema.equipmentCategories.size})",
                            "SPECIALTY" to "Specialties (${schema.specialties.size})",
                            "RENTAL_STRATEGY" to "Rental Formulas (${schema.rentalStrategies.size})"
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
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (item.category) {
                                        "SPACE_TYPE" -> Icons.Default.Apartment
                                        "SUBCATEGORY" -> Icons.Default.Category
                                        "AMENITY" -> Icons.Default.CheckCircle
                                        "EQUIPMENT" -> Icons.Default.Biotech
                                        "SPECIALTY" -> Icons.Default.Badge
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
                                Spacer(modifier = Modifier.width(6.dp))
                                if (item.isSystemDefault) {
                                    Surface(
                                        color = OxfordBlueContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "LEBANESE DEFAULT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OxfordBlue,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        color = CarnationOrangeContainer,
                                        shape = RoundedCornerShape(4.dp)
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
                                fontSize = 11.sp
                            )
                            if (item.description.isNotBlank()) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
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

        // Firestore Database Schema Reference Card
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = OxfordBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud Firestore Collections Contract",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Collection: 'spaces' (Documents: SpaceListing)\n" +
                                "• Collection: 'users' (Documents: AppUser)\n" +
                                "• Collection: 'bookings' (Documents: RentalBookingRequest)\n" +
                                "• Collection: 'transactions' (Documents: WhishTransaction)\n" +
                                "• Collection: 'schema_architecture' (Documents: SpaceArchitectureSchema)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
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
    adminViewModel: AdminViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            title = "Security & Audit Event Stream",
                            subtitle = "Tamper-proof event logs and compliance telemetry",
                            icon = Icons.Default.Shield
                        )

                        Button(
                            onClick = { adminViewModel.exportAllAuditReport() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Audit", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = "• Protocol: SHA-256 / MD5 Dual Security Layer\n" +
                                "• Access Clearance: Super Admin geo.elnajjar@gmail.com\n" +
                                "• Total Registered Logs: ${uiState.auditLogs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            shape = RoundedCornerShape(4.dp)
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
                            fontSize = 11.sp,
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
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
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
                    shape = RoundedCornerShape(8.dp),
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
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(12.dp)
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
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
    var syndicateNumber by remember { mutableStateOf(user.syndicateNumber) }
    var affiliation by remember { mutableStateOf(user.affiliation) }
    var selectedRole by remember { mutableStateOf(user.role) }
    var selectedGov by remember { mutableStateOf(user.governorate) }
    var isVerified by remember { mutableStateOf(user.isVerified) }
    var trustScoreText by remember { mutableStateOf(user.trustScore.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
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
                InputField(value = syndicateNumber, onValueChange = { syndicateNumber = it }, label = "Syndicate / License #", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = affiliation, onValueChange = { affiliation = it }, label = "Affiliation / Studio", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(
                    value = trustScoreText,
                    onValueChange = { trustScoreText = it },
                    label = "Trust Index (0-100)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Role Clearance:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.name.replace("_", " "), fontSize = 11.sp) }
                        )
                    }
                }

                Text("Territory Governorate:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Governorate.entries) { gov ->
                        FilterChip(
                            selected = selectedGov == gov,
                            onClick = { selectedGov = gov },
                            label = { Text(gov.displayName.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accredited / Verified Status:", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isVerified, onCheckedChange = { isVerified = it })
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val parsedTrust = trustScoreText.toIntOrNull() ?: user.trustScore
                            val updated = user.copy(
                                fullName = fullName.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                specialty = specialty.trim(),
                                syndicateNumber = syndicateNumber.trim(),
                                affiliation = affiliation.trim(),
                                role = selectedRole,
                                governorate = selectedGov,
                                isVerified = isVerified,
                                verificationStatus = if (isVerified) MemberVerificationStatus.VERIFIED else user.verificationStatus,
                                trustScore = parsedTrust.coerceIn(0, 100)
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
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
            Text("Are you sure you want to permanently remove '${user.fullName}' (${user.email}) from the platform? This will delete all associated session data.")
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
 * 4. Edit Listing Dialog
 */
@Composable
private fun AdminEditListingDialog(
    listing: SpaceListing,
    onDismiss: () -> Unit,
    onSave: (SpaceListing) -> Unit
) {
    var title by remember { mutableStateOf(listing.title) }
    var district by remember { mutableStateOf(listing.district) }
    var streetAddress by remember { mutableStateOf(listing.streetAddress) }
    var floorInfo by remember { mutableStateOf(listing.floorInfo) }
    var priceText by remember { mutableStateOf(listing.baseMonthlyRateUsd.toInt().toString()) }
    var ownerName by remember { mutableStateOf(listing.ownerName) }
    var ownerPhone by remember { mutableStateOf(listing.ownerPhone) }
    var selectedSpaceType by remember { mutableStateOf(listing.spaceType) }
    var selectedGov by remember { mutableStateOf(listing.governorate) }
    var isShared by remember { mutableStateOf(listing.isShared) }
    var isVerified by remember { mutableStateOf(listing.isVerified) }
    var isActiveSub by remember { mutableStateOf(listing.isActiveSubscription) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit Workspace: ${listing.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                InputField(value = title, onValueChange = { title = it }, label = "Workspace Title", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = district, onValueChange = { district = it }, label = "District / Neighborhood", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = streetAddress, onValueChange = { streetAddress = it }, label = "Street Address", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = floorInfo, onValueChange = { floorInfo = it }, label = "Floor / Building Info", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = "Monthly Rate ($ USD/mo)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                InputField(value = ownerName, onValueChange = { ownerName = it }, label = "Host Name", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = ownerPhone, onValueChange = { ownerPhone = it }, label = "Host Phone", modifier = Modifier.fillMaxWidth(), singleLine = true)

                Text("Space Type Classification:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SpaceType.entries) { type ->
                        FilterChip(
                            selected = selectedSpaceType == type,
                            onClick = { selectedSpaceType = type },
                            label = { Text(type.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Governorate:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Governorate.entries) { gov ->
                        FilterChip(
                            selected = selectedGov == gov,
                            onClick = { selectedGov = gov },
                            label = { Text(gov.displayName.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Shared Space Format:", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Accredited / Verified:", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isVerified, onCheckedChange = { isVerified = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active 30-day Subscription Pass:", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActiveSub, onCheckedChange = { isActiveSub = it })
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: listing.baseMonthlyRateUsd
                            val updated = listing.copy(
                                title = title.trim(),
                                district = district.trim(),
                                streetAddress = streetAddress.trim(),
                                floorInfo = floorInfo.trim(),
                                baseMonthlyRateUsd = price,
                                ownerName = ownerName.trim(),
                                ownerPhone = ownerPhone.trim(),
                                spaceType = selectedSpaceType,
                                governorate = selectedGov,
                                isShared = isShared,
                                isVerified = isVerified,
                                isActiveSubscription = isActiveSub
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
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
    onDismiss: () -> Unit,
    onAdd: (name: String, category: String, description: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("SUBCATEGORY") }

    val categories = listOf(
        "SPACE_TYPE" to "Space Type (Root)",
        "SUBCATEGORY" to "Subcategory (Level 2)",
        "AMENITY" to "Amenity / Facility",
        "EQUIPMENT" to "Equipment Category",
        "SPECIALTY" to "Complementary Specialty",
        "RENTAL_STRATEGY" to "Rental Time Formula"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                            label = { Text(catLabel, fontSize = 11.sp) }
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

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onAdd(name, selectedCategory, description, "Category")
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
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
