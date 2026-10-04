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

// =========================================================================
// TAB 2: LISTINGS CATALOG & CRUD
// =========================================================================
@Composable
internal fun AdminListingsCatalogTab(
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
            val hasMoreRows by adminViewModel.hasMoreRows.collectAsState()
            if (hasMoreRows) {
                TextButton(onClick = { adminViewModel.loadMoreRows() }) {
                    Text("Showing the first ${uiState.allSpaces.size} listings — load more", style = MaterialTheme.typography.labelMedium)
                }
            }
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

/**
 * 5. Delete Listing Dialog
 */
@Composable
internal fun AdminDeleteListingDialog(
    listing: SpaceListing,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ProHostDialog(
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminAttendeePackageDialog(
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

    ProHostDialog(
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
