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
                                    Text(sub.name, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                                    if (isEditingThis) {
                                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall) {
                                            Text(
                                                "EDITING",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
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

                            Text("Type: ${sub.type.displayName} • Strategy: ${sub.pricing.strategyType.displayName}", fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.primary)
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
                Text(
                    if (editingSubdivisionIndex != null) "Edit Room / Unit Details" else "Add Room / Unit Details",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )

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
                if (subImageUploadError != null) {
                    Text(
                        subImageUploadError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Operating Hours", fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        Text(
                            "Off by default — this room follows the space's own hours/days.",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = subScheduleOverrideEnabled,
                        onCheckedChange = { enabled ->
                            subScheduleOverrideEnabled = enabled
                            if (enabled) {
                                // Seed from the space's current hours — the host edits a
                                // delta (e.g. this room closes earlier), not a blank slate.
                                subOverrideOpeningHour = openingHour
                                subOverrideClosingHour = closingHour
                                subOverrideDays = operatingDays.toSet()
                            }
                        }
                    )
                }
                if (subScheduleOverrideEnabled) {
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
                        Text("Open on Sundays", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        Switch(checked = subOverrideSundayOperating, onCheckedChange = { subOverrideSundayOperating = it })
                    }

                    // Room-specific blocked time — e.g. a maintenance window just for
                    // this room, independent of the whole space's own blackout slots
                    // (the wizard's Blackout Slots section, whole-space-only).
                    Text(
                        "Blocked Times (optional)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                    if (subOverrideBlackouts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            subOverrideBlackouts.forEach { slot ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${slot.dayOfWeek} ${slot.startTime}-${slot.endTime}" +
                                            if (slot.reason.isNotBlank()) " (${slot.reason})" else "",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                                    )
                                    IconButton(
                                        onClick = { subOverrideBlackouts = subOverrideBlackouts.filterNot { it.id == slot.id } },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove blocked time")
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
                                    label = { Text(day, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                            Text("Add Blocked Time", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        }
                    }
                }

                HorizontalDivider()

                // Once a custom schedule is on, the pricing tables below key off this
                // room's own hours/days instead of the whole space's — a hard override
                // that also lets prices be configured only for the hours it's actually
                // open, matching what SpaceCalculationUtils.buildAllSlotsForSpace expands.
                RentalPricingConfigEditor(
                    config = subPricing,
                    operatingDays = if (subScheduleOverrideEnabled) subOverrideDays.toList() else operatingDays,
                    openingHour = if (subScheduleOverrideEnabled) subOverrideOpeningHour else openingHour,
                    closingHour = if (subScheduleOverrideEnabled) subOverrideClosingHour else closingHour,
                    onConfigChange = { subPricing = it }
                )

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
                            enabled = isSubFormValid
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Save Subdivision", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        }

                        OutlinedButton(
                            onClick = { resetSubdivisionForm() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Cancel Edit & Add New Subdivision", fontSize = MaterialTheme.typography.labelMedium.fontSize)
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
                        enabled = isSubFormValid
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Add Subdivision to Listing", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }
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
