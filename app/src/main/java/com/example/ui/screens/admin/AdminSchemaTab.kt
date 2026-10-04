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
// TAB 4: GOD SCHEMA (UNIFIED ADMIN-EDITABLE DATABASE SCHEMA)
// =========================================================================
@Composable
internal fun AdminSchemaArchitectureTab(
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
                    // Additive only: unlike Reset, keeps every existing entry and its edits.
                    CustomButton(
                        text = "Add Missing Built-in Entries",
                        onClick = { adminViewModel.addMissingDefaultSchemaItems() },
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.AutoFixHigh,
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            title = "Attendee Tier Templates",
                            subtitle = "Starting tiers hosts can copy into a per-attendee room, then edit",
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
                                "• Collection: 'subscriptions' (Google Play subscription records, server-only)\n" +
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
internal fun GodSchemaSection(
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
internal fun SchemaItemRow(
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
internal fun AdminEditSchemaItemDialog(
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
    var markerColorInput by remember { mutableStateOf(item.markerColor ?: "") }

    ProHostDialog(
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

/**
 * 6. Add Schema Node Dialog
 */
internal val MARKER_COLOR_PRESETS = listOf(
    "#5B9BFF", "#FF8F73", "#7DD9A0", "#B197FC", "#E8C468", "#6FE3E3",
    "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD"
)

@Composable
internal fun AdminAddSchemaItemDialog(
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
internal fun AdminResetSchemaDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ProHostDialog(
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
