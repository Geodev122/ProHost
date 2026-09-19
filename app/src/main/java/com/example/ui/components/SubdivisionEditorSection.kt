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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The room/desk ("subdivision") builder — add, edit, and remove individually
 * rentable rooms or desks within a Center/Polyclinic/Co-working listing.
 * CreateListingDialog's Step 3 is the sole caller, for both a brand-new listing
 * and editing an already-published one (existingDraft + onListingUpdated), so a
 * host is never stuck with whatever subdivisions they happened to define during
 * the original wizard.
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
    spaceId: String,
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
    // uploadAndGetUrl already catches its own failures and returns null — this
    // surfaces that instead of letting the picker silently do nothing, same
    // pattern as CreateListingDialog's photoUploadError/ownershipUploadError.
    var subImageUploadError by remember { mutableStateOf<String?>(null) }
    var subPricing by remember { mutableStateOf(RentalPricingConfig.default()) }

    // Per-division operating-schedule override — off by default, meaning this room
    // just follows the whole space's own SpaceOperatingSchedule (the common case).
    // Turning it on seeds from the space's current hours/days so the host is editing
    // a delta (e.g. "this exam room closes at 17:00, not 20:00"), not starting blank.
    var subScheduleOverrideEnabled by remember { mutableStateOf(false) }
    var subOverrideOpeningHour by remember { mutableStateOf(openingHour) }
    var subOverrideClosingHour by remember { mutableStateOf(closingHour) }
    var subOverrideDays by remember { mutableStateOf(operatingDays.toSet()) }
    var subOverrideSundayOperating by remember { mutableStateOf(false) }
    var subOverrideBlackouts by remember { mutableStateOf(listOf<BlackoutSlot>()) }
    var blackoutDay by remember { mutableStateOf(operatingDays.firstOrNull() ?: "Mon") }
    var blackoutStart by remember { mutableStateOf("18:00") }
    var blackoutEnd by remember { mutableStateOf("22:00") }
    var blackoutReason by remember { mutableStateOf("") }

    // Pending id so images upload to their final path before the Subdivision object
    // itself is created — same "generate the id up front" pattern CreateListingDialog
    // uses for the parent listing's own photos. Regenerated after each successful Add
    // (see the Button below) — a single remember{} here would give every room added
    // in the same session the identical id, silently overwriting each other's images.
    var pendingSubId by remember { mutableStateOf("SUB-" + UUID.randomUUID().toString().take(6).uppercase()) }

    // Which entry in subdivisionsList (if any) the form below is currently editing
    // in place, rather than building a new one. Previously the only way to change
    // an already-added subdivision was to delete it and re-add it from scratch —
    // this tracks the in-progress edit so the "Add" button can become "Save
    // Changes" and commit a replacement instead of an append.
    var editingSubdivisionIndex by remember { mutableStateOf<Int?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingSubImage = true
            subImageUploadError = null
            var failureCount = 0
            uris.forEach { uri ->
                val imageId = UUID.randomUUID().toString().take(8)
                val bytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                val url = if (bytes != null) {
                    storageService.uploadSubdivisionImageBytes(
                        spaceId = spaceId,
                        subdivisionId = pendingSubId,
                        imageId = imageId,
                        rawBytes = bytes,
                        fileExtension = "jpg"
                    )
                } else null
                if (url != null) subImageUrls = subImageUrls + url else failureCount++
            }
            if (failureCount > 0) {
                val detail = FirebaseStorageService.lastUploadError ?: "Check your connection and try again."
                subImageUploadError = if (failureCount == uris.size) {
                    "Couldn't upload ${if (uris.size == 1) "that photo" else "those photos"}: $detail"
                } else {
                    "$failureCount of ${uris.size} photos failed to upload: $detail"
                }
            }
            isUploadingSubImage = false
        }
    }

    val weekDayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val amenityCatalog = listOf(
        "A/C Climate Control", "Dual-Monitor Setup", "Whiteboard / Presentation Kit",
        "High-Speed Wi-Fi", "Soundproofing", "Ergonomic Seating", "Storage Locker",
        "Keyless Access Control", "Privacy Partition", "Natural Lighting", "Standing Desk"
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Intro header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Default.Apartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column {
                Text("Rooms & Workspace Divisions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Add each rentable room or desk separately — specialists will book them individually.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (subdivisionsList.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Text("Configured Rooms (${subdivisionsList.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subdivisionsList.forEachIndexed { index, sub ->
                    val isEditingThis = editingSubdivisionIndex == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isEditingThis) 2.dp else 0.dp,
                                color = if (isEditingThis) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                shape = MaterialTheme.shapes.medium
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isEditingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(sub.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    if (isEditingThis) {
                                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall) {
                                            Text(
                                                "EDITING",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }

                                Row {
                                    if (!isEditingThis) {
                                        IconButton(onClick = {
                                            subName = sub.name
                                            subType = sub.type
                                            subAmenitiesSelected = sub.amenities.toSet()
                                            subImageUrls = sub.imageUrls
                                            subPricing = sub.pricing
                                            val override = sub.scheduleOverride
                                            subScheduleOverrideEnabled = override != null
                                            subOverrideOpeningHour = override?.openingHour ?: openingHour
                                            subOverrideClosingHour = override?.closingHour ?: closingHour
                                            subOverrideDays = override?.operatingDays?.toSet() ?: operatingDays.toSet()
                                            subOverrideSundayOperating = override?.isSundayOperating ?: false
                                            subOverrideBlackouts = override?.blackoutSlots ?: emptyList()
                                            blackoutDay = subOverrideDays.firstOrNull() ?: "Mon"
                                            pendingSubId = sub.id
                                            editingSubdivisionIndex = index
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Division", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(onClick = {
                                        if (isEditingThis) {
                                            editingSubdivisionIndex = null
                                            subName = ""
                                            subImageUrls = emptyList()
                                            subPricing = RentalPricingConfig.default()
                                        }
                                        onSubdivisionsChange(subdivisionsList.filterIndexed { i, _ -> i != index })
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Division", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                    Text(sub.type.displayName, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                    Text(sub.pricing.strategyType.displayName, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            if (sub.amenities.isNotEmpty()) {
                                Text("✓ ${sub.amenities.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }
                }
            }
            HorizontalDivider()
        }

        // Pickers declared up-front so they render on top of everything
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

        val isSubFormValid = subName.isNotBlank() && subPricing.hasRealPrice()

        fun resetSubdivisionForm() {
            editingSubdivisionIndex = null
            subName = ""
            subType = Level2Type.ROOMS
            subAmenitiesSelected = emptySet()
            subImageUrls = emptyList()
            subPricing = RentalPricingConfig.default()
            subScheduleOverrideEnabled = false
            subOverrideOpeningHour = openingHour
            subOverrideClosingHour = closingHour
            subOverrideDays = operatingDays.toSet()
            subOverrideSundayOperating = false
            subOverrideBlackouts = emptyList()
            blackoutDay = operatingDays.firstOrNull() ?: "Mon"
            blackoutStart = "18:00"
            blackoutEnd = "22:00"
            blackoutReason = ""
            pendingSubId = "SUB-" + UUID.randomUUID().toString().take(6).uppercase()
        }

        fun buildCurrentSubdivision(): Subdivision = Subdivision(
            id = pendingSubId,
            name = subName,
            type = subType,
            imageUrls = subImageUrls,
            amenities = subAmenitiesSelected.toList(),
            pricing = subPricing,
            scheduleOverride = if (subScheduleOverrideEnabled) {
                SpaceOperatingSchedule(
                    openingHour = subOverrideOpeningHour,
                    closingHour = subOverrideClosingHour,
                    operatingDays = subOverrideDays.toList(),
                    isSundayOperating = subOverrideSundayOperating,
                    blackoutSlots = subOverrideBlackouts
                )
            } else null
        )

        // ── Section 1: Identity ─────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text("1", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        if (editingSubdivisionIndex != null) "Edit Room / Unit" else "Room / Unit Identity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                InputField(
                    value = subName,
                    onValueChange = { subName = it },
                    label = "Room Name / Desk ID (e.g. Treatment Room B)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerTriggerRow(
                    summary = subType.displayName,
                    placeholder = "Choose a room type",
                    onClick = { showTypePicker = true }
                )

                Text("Amenities", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerTriggerRow(
                    summary = subAmenitiesSelected.joinToString().ifBlank { "None selected" },
                    placeholder = "Choose amenities",
                    onClick = { showAmenityPicker = true }
                )
            }
        }

        // ── Section 2: Photos ───────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text("2", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("Room Photos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(subImageUrls) { url ->
                        Box(modifier = Modifier.size(72.dp)) {
                            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            IconButton(
                                onClick = { subImageUrls = subImageUrls - url },
                                modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(enabled = !isUploadingSubImage) { imagePickerLauncher.launch("image/*") },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUploadingSubImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add room image", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                if (subImageUploadError != null) {
                    Text(subImageUploadError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ── Section 3: Pricing ──────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text("3", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("Pricing Strategy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                // Pricing keys off this room's own hours when schedule override is on,
                // otherwise inherits the parent space's operating schedule.
                RentalPricingConfigEditor(
                    config = subPricing,
                    operatingDays = if (subScheduleOverrideEnabled) subOverrideDays.toList() else operatingDays,
                    openingHour = if (subScheduleOverrideEnabled) subOverrideOpeningHour else openingHour,
                    closingHour = if (subScheduleOverrideEnabled) subOverrideClosingHour else closingHour,
                    onConfigChange = { subPricing = it }
                )
            }
        }

        // ── Section 4: Custom Hours (optional) ─────────────────────────────
        Surface(
            color = if (subScheduleOverrideEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(
                width = if (subScheduleOverrideEnabled) 1.5.dp else 1.dp,
                color = if (subScheduleOverrideEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.weight(1f)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                            Text("4", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column {
                            Text("Custom Hours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (subScheduleOverrideEnabled) "This room has its own schedule." else "Off — follows the space's hours.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = subScheduleOverrideEnabled,
                        onCheckedChange = { enabled ->
                            subScheduleOverrideEnabled = enabled
                            if (enabled) {
                                subOverrideOpeningHour = openingHour
                                subOverrideClosingHour = closingHour
                                subOverrideDays = operatingDays.toSet()
                            }
                        }
                    )
                }

                if (subScheduleOverrideEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    OperatingScheduleEditorSection(
                        openingHour = subOverrideOpeningHour,
                        onOpeningHourChange = { subOverrideOpeningHour = it },
                        closingHour = subOverrideClosingHour,
                        onClosingHourChange = { subOverrideClosingHour = it },
                        selectedDays = subOverrideDays,
                        onDaysChange = { subOverrideDays = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Open on Sundays", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = subOverrideSundayOperating, onCheckedChange = { subOverrideSundayOperating = it })
                    }

                    // Room-specific blocked time — independent of the whole space's blackout slots.
                    Text("Blocked Times (optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (subOverrideBlackouts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            subOverrideBlackouts.forEach { slot ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${slot.dayOfWeek}  ${slot.startTime}–${slot.endTime}" +
                                                if (slot.reason.isNotBlank()) "  ·  ${slot.reason}" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { subOverrideBlackouts = subOverrideBlackouts.filterNot { it.id == slot.id } },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove blocked time", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val blackoutDayOptions = (subOverrideDays + if (subOverrideSundayOperating) setOf("Sun") else emptySet())
                        .let { days -> weekDayOrder.filter { it in days } }
                    if (blackoutDayOptions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(blackoutDayOptions) { day ->
                                FilterChip(
                                    selected = blackoutDay == day,
                                    onClick = { blackoutDay = day },
                                    label = { Text(day, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = blackoutStart,
                                onValueChange = { blackoutStart = it },
                                label = { Text("From (HH:mm)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = blackoutEnd,
                                onValueChange = { blackoutEnd = it },
                                label = { Text("To (HH:mm)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = blackoutReason,
                            onValueChange = { blackoutReason = it },
                            label = { Text("Reason (optional)") },
                            placeholder = { Text("e.g. Weekly maintenance") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = {
                                subOverrideBlackouts = subOverrideBlackouts + BlackoutSlot(
                                    dayOfWeek = blackoutDay,
                                    startTime = blackoutStart,
                                    endTime = blackoutEnd,
                                    reason = blackoutReason.ifBlank { "Blocked" }
                                )
                                blackoutReason = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Blocked Time", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // ── Action buttons ──────────────────────────────────────────────────
        if (editingSubdivisionIndex != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val editIndex = editingSubdivisionIndex
                        val updatedSub = buildCurrentSubdivision()
                        onSubdivisionsChange(
                            subdivisionsList.mapIndexed { i, existing -> if (i == editIndex) updatedSub else existing }
                        )
                        resetSubdivisionForm()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isSubFormValid,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Save Changes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { resetSubdivisionForm() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Cancel & Add New Room", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Button(
                onClick = {
                    val newSub = buildCurrentSubdivision()
                    onSubdivisionsChange(subdivisionsList + newSub)
                    resetSubdivisionForm()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isSubFormValid,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text("Add Room to Listing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
