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
        // Real totals (adminCounts); rows come only from search or the live review queue.
        item {
            val counts = uiState.counts
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminMetricTile("Listings", counts?.totalListings?.toString() ?: "…", modifier = Modifier.weight(1f))
                AdminMetricTile("Published", counts?.activeListings?.toString() ?: "…", modifier = Modifier.weight(1f))
                AdminMetricTile("To review", counts?.pendingReview?.toString() ?: "…", modifier = Modifier.weight(1f))
                AdminMetricTile("Bookings", counts?.totalBookings?.toString() ?: "…", modifier = Modifier.weight(1f))
            }
        }
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Listings & Bookings",
                        subtitle = "Search by title, L-/D-/B- code, id or the host's / renter's email",
                        icon = Icons.Default.Apartment
                    )

                    InputField(
                        value = uiState.listingSearchQuery,
                        onValueChange = { adminViewModel.setListingSearchQuery(it) },
                        label = "Title, L-/D-/B- code, id or email",
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
                    // (the same one the host's Create wizard publishes under).
                    val categoryFilterOptions = uiState.schema.spaceTypes.filter { it.isEnabled }.ifEmpty {
                        SpaceType.values().map { SchemaItem(id = it.name, name = it.displayName, category = "SPACE_TYPE") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedListingTypeFilter == null,
                                onClick = { adminViewModel.setListingTypeFilter(null) },
                                label = { Text("All categories", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        items(categoryFilterOptions) { category ->
                            FilterChip(
                                selected = uiState.selectedListingTypeFilter == category.id,
                                onClick = { adminViewModel.setListingTypeFilter(category.id) },
                                label = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                "ALL" to "All",
                                "ACTIVE_30D" to "Active",
                                "EXPIRED" to "Expired",
                                "VERIFIED" to "Verified",
                                "PENDING_VERIFICATION" to "Pending review"
                            )
                        ) { (key, label) ->
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
                text = if (uiState.listingSearchQuery.isBlank()) {
                    "Awaiting verification review (${uiState.reviewQueue.size}) — search to find any listing"
                } else {
                    "${uiState.filteredSpaces.size} listing(s), ${uiState.bookingResults.size} booking(s)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            uiState.isSearchingListings -> item { ShimmerLoadingList(count = 2, itemHeight = 140.dp) }
            uiState.searchError != null && uiState.listingSearchQuery.isNotBlank() -> item {
                ProHostAlertBanner(message = uiState.searchError, severity = ProHostAlertSeverity.ERROR)
            }
            uiState.filteredSpaces.isEmpty() && uiState.bookingResults.isEmpty() -> item {
                ProEmptyState(
                    title = if (uiState.listingSearchQuery.isBlank()) "Nothing to review" else "No matches",
                    description = if (uiState.listingSearchQuery.isBlank()) {
                        "No listing is waiting for verification."
                    } else {
                        "Nothing matches \"${uiState.listingSearchQuery.trim()}\"."
                    },
                    icon = Icons.Default.SearchOff
                )
            }
        }

        if (uiState.bookingResults.isNotEmpty()) {
            items(uiState.bookingResults, key = { "booking_${it.id}" }) { booking ->
                AdminBookingResultCard(booking = booking, onOpenPerson = { uid -> adminViewModel.openDossier(uid) })
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

                        // Host's full profile (dossier)
                        if (space.ownerId.isNotBlank()) {
                            IconButton(
                                onClick = { adminViewModel.openDossier(space.ownerId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Badge, contentDescription = "Host profile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
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

/** One booking found by the admin search (B- code, renter/host email or name). */
@Composable
private fun AdminBookingResultCard(booking: RentalBookingRequest, onOpenPerson: (String) -> Unit) {
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(booking.publicCode, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(booking.status.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(booking.spaceTitle.ifBlank { "Listing" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${booking.practitionerName.ifBlank { "Renter" }} → ${booking.ownerName.ifBlank { "Host" }} · " +
                    "${booking.startDate} · $${"%.2f".format(java.util.Locale.US, booking.totalAmountUsd)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (booking.practitionerId.isNotBlank()) {
                    CustomButton(
                        text = "Renter profile",
                        onClick = { onOpenPerson(booking.practitionerId) },
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.Badge,
                        compact = true
                    )
                }
                if (booking.ownerId.isNotBlank()) {
                    CustomButton(
                        text = "Host profile",
                        onClick = { onOpenPerson(booking.ownerId) },
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.Badge,
                        compact = true
                    )
                }
            }
        }
    }
}
