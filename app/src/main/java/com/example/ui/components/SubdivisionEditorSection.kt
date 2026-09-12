package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.storage.FirebaseStorageService
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The room/desk ("subdivision") builder — add, list, and remove individually
 * rentable rooms or desks within a Center/Polyclinic/Co-working listing. Shared
 * by CreateListingDialog's Step 3 (at creation) and SpaceScheduleEditorDialog's
 * "Rooms & Subdivisions" section (post-publish), so a host is never stuck with
 * whatever subdivisions they happened to define during the original wizard.
 *
 * [operatingDays]/[openingHour]/[closingHour] come from the parent space's own
 * [SpaceOperatingSchedule] — the per-division pricing editor keys its Hourly/Shift/
 * Day-Based tables off these, so a division can never offer a slot outside hours
 * the space itself doesn't operate in.
 *
 * Only [subdivisionsList] and [onSubdivisionsChange] are hoisted — the "add a
 * new room" form fields are transient, single-use input state that resets after
 * each add, so they live locally here rather than in either caller.
 */
@Composable
fun SubdivisionEditorSection(
    subdivisionsList: List<Subdivision>,
    onSubdivisionsChange: (List<Subdivision>) -> Unit,
    operatingDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
    openingHour: String = "08:00",
    closingHour: String = "20:00",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }

    var subName by remember { mutableStateOf("") }
    var subType by remember { mutableStateOf(Level2Type.ROOMS) }
    var subAmenitiesSelected by remember { mutableStateOf(setOf<String>()) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showAmenityPicker by remember { mutableStateOf(false) }
    var subImageUrls by remember { mutableStateOf(listOf<String>()) }
    var isUploadingSubImage by remember { mutableStateOf(false) }
    var subPricing by remember { mutableStateOf(RentalPricingConfig.default()) }

    // Pending id so images upload to their final path before the Subdivision object
    // itself is created — same "generate the id up front" pattern CreateListingDialog
    // uses for the parent listing's own photos. Regenerated after each successful Add
    // (see the Button below) — a single remember{} here would give every room added
    // in the same session the identical id, silently overwriting each other's images.
    var pendingSubId by remember { mutableStateOf("SUB-" + UUID.randomUUID().toString().take(6).uppercase()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingSubImage = true
            uris.forEach { uri ->
                val imageId = UUID.randomUUID().toString().take(8)
                // spaceId isn't known here (a subdivision can be configured before the
                // parent listing itself is ever saved) — pendingSubId doubles as both
                // the eventual Subdivision.id and a unique enough path segment, since
                // uploadSubdivisionImage's path is keyed by spaceId anyway once this
                // moves under a real listing; using it here too keeps every image this
                // section ever uploads under a name unique to this one room.
                val url = storageService.uploadSubdivisionImage(
                    spaceId = pendingSubId,
                    subdivisionId = pendingSubId,
                    imageId = imageId,
                    fileUri = uri,
                    fileExtension = "jpg"
                )
                if (url != null) subImageUrls = subImageUrls + url
            }
            isUploadingSubImage = false
        }
    }

    val amenityCatalog = listOf(
        "A/C Climate Control", "Dual-Monitor Setup", "Whiteboard / Presentation Kit",
        "High-Speed Wi-Fi", "Soundproofing", "Ergonomic Seating", "Storage Locker",
        "Keyless Access Control", "Privacy Partition", "Natural Lighting", "Standing Desk"
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Configure Rooms & Workspace Subdivisions",
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Add individual rooms, offices, or shared desks that professionals can rent separately. Each gets its own type, amenities, images, and renting strategy.",
            fontSize = MaterialTheme.typography.labelMedium.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (subdivisionsList.isNotEmpty()) {
            Text("Configured Subdivisions (${subdivisionsList.size})", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subdivisionsList.forEachIndexed { index, sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sub.name, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                                Text("Type: ${sub.type.displayName}", fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Strategy: ${sub.pricing.strategyType.displayName}",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (sub.amenities.isNotEmpty()) {
                                    Text("Amenities: ${sub.amenities.joinToString()}", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (sub.imageUrls.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(sub.imageUrls) { url ->
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { onSubdivisionsChange(subdivisionsList.filterIndexed { i, _ -> i != index }) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Room / Unit Details", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodySmall.fontSize)

                InputField(
                    value = subName,
                    onValueChange = { subName = it },
                    label = "Room Name / Desk ID (e.g. Treatment Room B)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Type and Amenities are chosen through real searchable popup pickers
                // (spec Step 3: "Type via multiselect popup", "Amenities via multiselect
                // popup") — the inline chip rows they replaced couldn't scale past a
                // handful of options and had no search for Type at all.
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                PickerTriggerRow(
                    summary = subType.displayName,
                    placeholder = "Choose a room type",
                    onClick = { showTypePicker = true }
                )

                Text("Amenities", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                PickerTriggerRow(
                    summary = subAmenitiesSelected.joinToString(),
                    placeholder = "Choose amenities",
                    onClick = { showAmenityPicker = true }
                )

                if (showTypePicker) {
                    SearchablePickerDialog(
                        title = "Room / Unit Type",
                        options = Level2Type.values().map { it.displayName },
                        selected = setOf(subType.displayName),
                        multiSelect = false,
                        onToggle = { label ->
                            Level2Type.values().firstOrNull { it.displayName == label }?.let { subType = it }
                            showTypePicker = false
                        },
                        onDismiss = { showTypePicker = false }
                    )
                }
                if (showAmenityPicker) {
                    SearchablePickerDialog(
                        title = "Room Amenities",
                        options = amenityCatalog,
                        selected = subAmenitiesSelected,
                        multiSelect = true,
                        onToggle = { amen ->
                            subAmenitiesSelected = if (amen in subAmenitiesSelected) subAmenitiesSelected - amen else subAmenitiesSelected + amen
                        },
                        onDismiss = { showAmenityPicker = false }
                    )
                }

                Text("Images", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subImageUrls) { url ->
                        Box(modifier = Modifier.size(72.dp)) {
                            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            IconButton(
                                onClick = { subImageUrls = subImageUrls - url },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image")
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(enabled = !isUploadingSubImage) { imagePickerLauncher.launch("image/*") },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUploadingSubImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add room image")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                RentalPricingConfigEditor(
                    config = subPricing,
                    operatingDays = operatingDays,
                    openingHour = openingHour,
                    closingHour = closingHour,
                    onConfigChange = { subPricing = it }
                )

                Button(
                    onClick = {
                        val newSub = Subdivision(
                            id = pendingSubId,
                            name = subName,
                            type = subType,
                            imageUrls = subImageUrls,
                            amenities = subAmenitiesSelected.toList(),
                            pricing = subPricing
                        )
                        onSubdivisionsChange(subdivisionsList + newSub)
                        subName = ""
                        subAmenitiesSelected = emptySet()
                        subImageUrls = emptyList()
                        subPricing = RentalPricingConfig.default()
                        pendingSubId = "SUB-" + UUID.randomUUID().toString().take(6).uppercase()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = subName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Add Room / Desk to Listing", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                }
            }
        }
    }
}

/** The tappable "current selection" row that opens a [SearchablePickerDialog]. */
@Composable
private fun PickerTriggerRow(
    summary: String,
    placeholder: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary.ifBlank { placeholder },
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                color = if (summary.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Search, contentDescription = "Open picker", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * A searchable popup picker. Single-select ([multiSelect] = false) reports the tap
 * through [onToggle] and expects the caller to close it; multi-select keeps the
 * dialog open so several options can be toggled, and closes on Done/outside tap.
 * Owns its own search text so it always opens with a clean filter.
 */
@Composable
private fun SearchablePickerDialog(
    title: String,
    options: List<String>,
    selected: Set<String>,
    multiSelect: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = options.filter { query.isBlank() || it.contains(query, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                if (filtered.isEmpty()) {
                    Text(
                        "No matches.",
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered) { option ->
                        val isSelected = option in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(option) }
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (multiSelect) {
                                Checkbox(checked = isSelected, onCheckedChange = { onToggle(option) })
                            } else {
                                RadioButton(selected = isSelected, onClick = { onToggle(option) })
                            }
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(option, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                    }
                }
                if (multiSelect) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Done (${selected.size} selected)")
                    }
                }
            }
        }
    }
}
