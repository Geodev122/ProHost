package com.example.ui.components

import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.storage.FirebaseStorageService
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * One division of a listing (a room, desk, hall…): name, type, amenities, photos, pricing and optional own hours. The
 * Create Listing wizard shows it in [DivisionSheet]; [initial] edits an existing division, null adds a new one, and
 * [onSave] returns the finished division (Save is enabled only once it has a name and a real price).
 *
 * [operatingDays]/[openingHour]/[closingHour] come from the space's own [SpaceOperatingSchedule] — the pricing editor
 * keys its Hourly/Shift/Day tables off these, so a division can never offer a slot outside the space's hours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubdivisionForm(
    spaceId: String,
    initial: Subdivision?,
    onSave: (Subdivision) -> Unit,
    onCancel: () -> Unit,
    operatingDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
    openingHour: String = "08:00",
    closingHour: String = "20:00",
    modifier: Modifier = Modifier,
    // Schema-driven amenity catalog — if non-empty replaces the hardcoded fallback below.
    availableAmenities: List<SchemaItem> = emptyList(),
    // Write-back: called when the host types a custom amenity; auto-tags to current
    // division type. No-op default so unupdated callers don't crash.
    onAddCustomAmenity: ((name: String, divisionTypeId: String) -> Unit)? = null,
    // Admin attendee packages — host can copy them as a starting point for tiers.
    attendeeTemplates: List<AttendeePackage> = emptyList()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }
    val initialOverride = initial?.scheduleOverride

    var subName by remember { mutableStateOf(initial?.name ?: "") }
    var subType by remember { mutableStateOf(initial?.type ?: Level2Type.ROOMS) }
    var subAmenitiesSelected by remember { mutableStateOf(initial?.amenities?.toSet() ?: setOf()) }
    var subHashtags by remember { mutableStateOf(initial?.hashtags ?: listOf()) }
    var hashtagInput by remember { mutableStateOf("") }
    var showMoreOptions by remember { mutableStateOf(initial?.scheduleOverride != null || initial?.hashtags?.isNotEmpty() == true) }
    var showAmenityPicker by remember { mutableStateOf(false) }
    var subImageUrls by remember { mutableStateOf(initial?.imageUrls ?: listOf()) }
    var isUploadingSubImage by remember { mutableStateOf(false) }
    // uploadAndGetUrl already catches its own failures and returns null — this
    // surfaces that instead of letting the picker silently do nothing, same
    // pattern as CreateListingDialog's photoUploadError.
    var subImageUploadError by remember { mutableStateOf<String?>(null) }
    var subPricing by remember { mutableStateOf(initial?.pricing ?: RentalPricingConfig.default()) }
    var subPricingMode by remember { mutableStateOf(initial?.pricingMode ?: SubdivisionPricingMode.STRATEGY_BASED) }
    var subCapacity by remember { mutableStateOf<Int?>(initial?.capacity) }
    var subCapacityInput by remember { mutableStateOf(initial?.capacity?.toString() ?: "") }
    var subMinAttendees by remember { mutableStateOf<Int?>(initial?.minAttendees) }
    var subMinAttendeesInput by remember { mutableStateOf(initial?.minAttendees?.toString() ?: "") }
    var subAttendeeTiers by remember { mutableStateOf(initial?.attendeeTiers ?: listOf()) }

    // Per-division operating-schedule override — off by default, meaning this room
    // just follows the whole space's own SpaceOperatingSchedule (the common case).
    // Turning it on seeds from the space's current hours/days so the host is editing
    // a delta (e.g. "this exam room closes at 17:00, not 20:00"), not starting blank.
    var subScheduleOverrideEnabled by remember { mutableStateOf(initialOverride != null) }
    var subOverrideOpeningHour by remember { mutableStateOf(initialOverride?.openingHour ?: openingHour) }
    var subOverrideClosingHour by remember { mutableStateOf(initialOverride?.closingHour ?: closingHour) }
    var subOverrideDays by remember { mutableStateOf(initialOverride?.operatingDays?.toSet() ?: operatingDays.toSet()) }
    var subOverrideSundayOperating by remember { mutableStateOf(initialOverride?.isSundayOperating ?: false) }
    var subOverrideBlackouts by remember { mutableStateOf(initialOverride?.blackoutSlots ?: listOf()) }
    var blackoutDay by remember { mutableStateOf(subOverrideDays.firstOrNull() ?: "Mon") }
    var blackoutStart by remember { mutableStateOf("18:00") }
    var blackoutEnd by remember { mutableStateOf("22:00") }
    var blackoutReason by remember { mutableStateOf("") }

    // Id generated up front so photos upload to their final path before the division
    // itself is saved; an edited division keeps its own id (bookings reference it).
    val pendingSubId = remember { initial?.id ?: ("SUB-" + UUID.randomUUID().toString().take(6).uppercase()) }


    fun buildCurrentSubdivision(): Subdivision = Subdivision(
        id = pendingSubId,
        name = subName,
        type = subType,
        imageUrls = subImageUrls,
        amenities = subAmenitiesSelected.toList(),
        hashtags = subHashtags,
        pricing = if (subPricingMode == SubdivisionPricingMode.PER_ATTENDEE) {
            com.example.ui.util.AttendeePricing.markShifts(subPricing)
        } else subPricing,
        pricingMode = subPricingMode,
        capacity = subCapacity,
        minAttendees = subMinAttendees,
        attendeeTiers = if (subPricingMode == SubdivisionPricingMode.PER_ATTENDEE) subAttendeeTiers else emptyList(),
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
                val detail = FirebaseStorageService.lastUploadError ?: uiText(R.string.check_your_connection_and_try_again)
                subImageUploadError = if (failureCount == uris.size) {
                    if (uris.size == 1) uiText(R.string.couldnt_upload_that_photo_x, detail) else uiText(R.string.couldnt_upload_those_photos_x, detail)
                } else {
                    uiText(R.string.x_of_x_photos_failed_to_upload_x, failureCount, uris.size, detail)
                }
            }
            isUploadingSubImage = false
        }
    }

    val weekDayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    // Use schema-driven amenities when available, filtering by current division type scope.
    // Falls back to the hardcoded defaults only when no schema has been loaded yet.
    val amenityCatalog = remember(availableAmenities, subType) {
        if (availableAmenities.isNotEmpty()) {
            availableAmenities
                .filter { it.scopedToIds.isEmpty() || it.scopedToIds.contains(subType.name) }
                .map { it.name }
        } else {
            listOf(
                "A/C Climate Control", "Dual-Monitor Setup", "Whiteboard / Presentation Kit",
                "High-Speed Wi-Fi", "Soundproofing", "Ergonomic Seating", "Storage Locker",
                "Keyless Access Control", "Privacy Partition", "Natural Lighting", "Standing Desk"
            )
        }
    }
    val isPerAttendee = subPricingMode == SubdivisionPricingMode.PER_ATTENDEE
    // The one reason Save is disabled for a per-attendee room, shown under the tiers — never
    // a silently greyed-out button.
    val attendeeBlocker: String? = if (!isPerAttendee) null else {
        val built = buildCurrentSubdivision()
        when {
            subAttendeeTiers.isEmpty() -> uiText(R.string.add_at_least_one_pricing_tier)
            com.example.ui.util.AttendeePricing.tiersFor(built).isEmpty() -> uiText(R.string.give_at_least_one_tier_a_price_per_person)
            subAttendeeTiers.any { t -> t.maxAttendees.let { it != null && it < t.minAttendees } } -> uiText(R.string.a_tier_s_to_is_below_its_from)
            !built.pricing.hasRealPrice() -> uiText(R.string.pick_at_least_one_hour_shift_or_day_this_r)
            (subCapacity ?: Int.MAX_VALUE) < (subMinAttendees ?: 1) -> uiText(R.string.max_attendees_is_below_min_attendees)
            else -> null
        }
    }
    val isSubFormValid = subName.isNotBlank() && if (isPerAttendee) {
        attendeeBlocker == null
    } else {
        subPricing.hasRealPrice()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {

        // Pickers declared up-front so they render on top of everything
        if (showAmenityPicker) {
            SearchablePickerDialog(
                title = stringResource(R.string.room_amenities),
                options = amenityCatalog,
                selected = subAmenitiesSelected,
                multiSelect = true,
                onToggle = { amen ->
                    subAmenitiesSelected = if (amen in subAmenitiesSelected) subAmenitiesSelected - amen else subAmenitiesSelected + amen
                },
                onDismiss = { showAmenityPicker = false },
                onAddCustom = if (onAddCustomAmenity != null) { name ->
                    onAddCustomAmenity.invoke(name, subType.name)
                } else null
            )
        }

        // Live preview: the card this division becomes in Step 3.
        DivisionCard(division = buildCurrentSubdivision(), onClick = null, onDelete = null)

        FormSection(title = stringResource(R.string.division_what_is_it)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Level2Type.values().forEach { type ->
                    FilterChip(
                        selected = subType == type,
                        onClick = { subType = type },
                        label = { Text(localizedLabel(type.displayName)) },
                        leadingIcon = { Icon(iconFor(type), contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            InputField(
                value = subName,
                onValueChange = { subName = it },
                label = stringResource(R.string.division_name),
                placeholder = stringResource(R.string.division_name_placeholder, localizedLabel(subType.displayName)),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            // Per-person divisions set their max with the tiers below.
            if (!isPerAttendee) {
                InputField(
                    value = subCapacityInput,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(4)
                        subCapacityInput = digits
                        subCapacity = digits.toIntOrNull()?.takeIf { it > 0 }
                    },
                    label = stringResource(R.string.division_capacity),
                    placeholder = stringResource(R.string.no_limit),
                    leadingIcon = Icons.Default.People,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        FormSection(title = stringResource(R.string.division_how_rented), hint = stringResource(R.string.division_how_rented_hint)) {
            RentalPricingConfigEditor(
                config = subPricing,
                operatingDays = if (subScheduleOverrideEnabled) subOverrideDays.toList() else operatingDays,
                openingHour = if (subScheduleOverrideEnabled) subOverrideOpeningHour else openingHour,
                closingHour = if (subScheduleOverrideEnabled) subOverrideClosingHour else closingHour,
                onConfigChange = { subPricing = it },
                availabilityOnly = isPerAttendee,
                afterStrategy = {
                    // Per person is not offered for Monthly, which is a whole-month lease.
                    if (subPricing.strategyType != RentalStrategyType.MONTHLY) {
                        ChoiceGrid(
                            choices = listOf(
                                Choice(SubdivisionPricingMode.STRATEGY_BASED, Icons.Default.Payments, stringResource(R.string.rent_mode_fixed), stringResource(R.string.rent_mode_fixed_hint)),
                                Choice(SubdivisionPricingMode.PER_ATTENDEE, Icons.Default.Groups, stringResource(R.string.rent_mode_per_person), stringResource(R.string.rent_mode_per_person_hint))
                            ),
                            selected = subPricingMode,
                            onSelect = { mode ->
                                if (mode != subPricingMode) {
                                    subPricingMode = mode
                                    subPricing = if (mode == SubdivisionPricingMode.PER_ATTENDEE) {
                                        com.example.ui.util.AttendeePricing.toAvailabilityMarkers(subPricing)
                                    } else com.example.ui.util.AttendeePricing.clearAvailabilityMarkers(subPricing)
                                }
                            }
                        )
                    }
                    Text(
                        if (isPerAttendee) stringResource(R.string.division_pick_times) else stringResource(R.string.division_set_prices),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
            )
            if (isPerAttendee) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    InputField(
                        value = subMinAttendeesInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            subMinAttendeesInput = digits
                            subMinAttendees = digits.toIntOrNull()?.takeIf { it > 0 }
                        },
                        label = stringResource(R.string.min_attendees),
                        placeholder = "1",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    InputField(
                        value = subCapacityInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            subCapacityInput = digits
                            subCapacity = digits.toIntOrNull()?.takeIf { it > 0 }
                        },
                        label = stringResource(R.string.max_attendees),
                        placeholder = stringResource(R.string.no_limit),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                AttendeeTierEditor(
                    tiers = subAttendeeTiers,
                    templates = attendeeTemplates,
                    onTiersChange = { subAttendeeTiers = it }
                )
                if (attendeeBlocker != null) {
                    Text(
                        attendeeBlocker,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(R.string.tiers_are_saved_with_the_room_tap_save_bel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.proColors.success
                    )
                }
            }
        }

        FormSection(title = stringResource(R.string.room_photos), hint = stringResource(R.string.division_photos_hint)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(subImageUrls) { url ->
                    Box(modifier = Modifier.size(72.dp)) {
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        IconButton(
                            onClick = { subImageUrls = subImageUrls - url },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_image), modifier = Modifier.size(14.dp))
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
                                    Icon(
                                        Icons.Default.AddAPhoto,
                                        contentDescription = stringResource(R.string.add_room_image),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(stringResource(R.string.add), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

        FormSection(title = stringResource(R.string.amenities)) {
            val quickAmenities = (amenityCatalog.take(8) + subAmenitiesSelected).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                quickAmenities.forEach { amenity ->
                    val on = amenity in subAmenitiesSelected
                    FilterChip(
                        selected = on,
                        onClick = { subAmenitiesSelected = if (on) subAmenitiesSelected - amenity else subAmenitiesSelected + amenity },
                        label = { Text(amenity) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                AssistChip(
                    onClick = { showAmenityPicker = true },
                    label = { Text(stringResource(R.string.more_amenities)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Rarely needed settings stay folded away.
        TextButton(onClick = { showMoreOptions = !showMoreOptions }) {
            Text(stringResource(R.string.more_options), style = MaterialTheme.typography.labelLarge)
            Icon(if (showMoreOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
        if (showMoreOptions) {
            FormSection(title = stringResource(R.string.hashtags), hint = stringResource(R.string.division_hashtags_hint)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InputField(
                        value = hashtagInput,
                        onValueChange = { hashtagInput = it.trimStart('#').replace(" ", "") },
                        label = "",
                        placeholder = stringResource(R.string.e_g_dentalclinic),
                        prefix = "#",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val tag = hashtagInput.trim().trimStart('#')
                            if (tag.isNotBlank() && !subHashtags.contains(tag)) {
                                subHashtags = subHashtags + tag
                            }
                            hashtagInput = ""
                        },
                        enabled = hashtagInput.trim().isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_hashtag), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (subHashtags.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        subHashtags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { subHashtags = subHashtags - tag },
                                label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
            FormSection(title = stringResource(R.string.custom_hours)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (subScheduleOverrideEnabled) stringResource(R.string.this_room_has_its_own_schedule) else stringResource(R.string.off_follows_the_space_s_hours),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
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
                        Text(stringResource(R.string.open_on_sundays), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = subOverrideSundayOperating, onCheckedChange = { subOverrideSundayOperating = it })
                    }

                    // Room-specific blocked time — independent of the whole space's blackout slots.
                    Text(stringResource(R.string.blocked_times_optional), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (subOverrideBlackouts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_blocked_time), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val blackoutDayOptions = (subOverrideDays + if (subOverrideSundayOperating) setOf("Sun") else emptySet())
                        .let { days -> weekDayOrder.filter { it in days } }
                    if (blackoutDayOptions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(blackoutDayOptions) { day ->
                                FilterChip(
                                    selected = blackoutDay == day,
                                    onClick = { blackoutDay = day },
                                    label = { Text(localizedLabel(day), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            InputField(
                                value = blackoutStart,
                                onValueChange = { blackoutStart = it },
                                label = stringResource(R.string.from_hh_mm),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            InputField(
                                value = blackoutEnd,
                                onValueChange = { blackoutEnd = it },
                                label = stringResource(R.string.to_hh_mm),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        InputField(
                            value = blackoutReason,
                            onValueChange = { blackoutReason = it },
                            label = stringResource(R.string.reason_optional_2),
                            placeholder = stringResource(R.string.e_g_weekly_maintenance),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = {
                                subOverrideBlackouts = subOverrideBlackouts + BlackoutSlot(
                                    dayOfWeek = blackoutDay,
                                    startTime = blackoutStart,
                                    endTime = blackoutEnd,
                                    reason = blackoutReason.ifBlank { uiText(R.string.blocked) }
                                )
                                blackoutReason = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(stringResource(R.string.add_blocked_time), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // ── Save / cancel ─────────────────────────────────────────────────
        if (!isSubFormValid && attendeeBlocker == null) {
            Text(
                stringResource(R.string.division_save_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CustomButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
                variant = CustomButtonVariant.OUTLINED,
                modifier = Modifier.weight(1f)
            )
            ProPrimaryButton(
                text = stringResource(R.string.save_division),
                onClick = { onSave(buildCurrentSubdivision()) },
                enabled = isSubFormValid,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A titled group of the division form, with an optional one-line hint. */
@Composable
private fun FormSection(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
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
    onDismiss: () -> Unit,
    onAddCustom: ((String) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val filtered = options.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    val canAddCustom = onAddCustom != null && query.isNotBlank() && filtered.none { it.equals(query.trim(), ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f).padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                }
                InputField(
                    value = query,
                    onValueChange = { query = it },
                    label = "",
                    placeholder = stringResource(R.string.search_or_type_new),
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (canAddCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val trimmed = query.trim()
                                onAddCustom?.invoke(trimmed)
                                onToggle(trimmed)
                                query = ""
                            }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.add_x, query.trim()), fontSize = MaterialTheme.typography.bodySmall.fontSize, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (filtered.isEmpty() && !canAddCustom) {
                    Text(
                        stringResource(R.string.no_matches),
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    Button(colors = proPrimaryButtonColors(), 
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(stringResource(R.string.done_x_selected, selected.size))
                    }
                }
            }
        }
    }
}

/**
 * Host-defined attendee tiers for a per-attendee room: each tier is an attendee range
 * and a price per person for the whole booking. The booking's attendee count picks
 * the tier (AttendeePricing.tierFor).
 */
@Composable
private fun AttendeeTierEditor(
    tiers: List<AttendeePackage>,
    templates: List<AttendeePackage>,
    onTiersChange: (List<AttendeePackage>) -> Unit
) {
    fun update(index: Int, transform: (AttendeePackage) -> AttendeePackage) =
        onTiersChange(tiers.mapIndexed { i, t -> if (i == index) transform(t) else t })

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.attendee_tiers), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.e_g_1_20_people_at_8_per_person_21_60_at_6),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        tiers.forEachIndexed { index, tier -> key(tier.id) {
            var fromText by remember { mutableStateOf(tier.minAttendees.toString()) }
            var priceText by remember {
                mutableStateOf(if (tier.pricePerAttendeeUsd == 0.0) "" else com.example.ui.util.AttendeePricing.formatUsd(tier.pricePerAttendeeUsd))
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InputField(
                            value = tier.name,
                            onValueChange = { v -> update(index) { it.copy(name = v) } },
                            label = stringResource(R.string.tier_name),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onTiersChange(tiers.filterIndexed { i, _ -> i != index }) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_tier), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        InputField(
                            value = fromText,
                            onValueChange = { v ->
                                // Empty is allowed while typing; the model keeps the last valid value.
                                fromText = v.filter(Char::isDigit).take(5)
                                fromText.toIntOrNull()?.takeIf { it >= 1 }?.let { n -> update(index) { it.copy(minAttendees = n) } }
                            },
                            isError = fromText.toIntOrNull()?.let { it < 1 } ?: true,
                            label = stringResource(R.string.from),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        InputField(
                            value = tier.maxAttendees?.toString() ?: "",
                            onValueChange = { v -> update(index) { it.copy(maxAttendees = v.filter(Char::isDigit).toIntOrNull()) } },
                            label = stringResource(R.string.to),
                            placeholder = "∞",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        InputField(
                            value = priceText,
                            onValueChange = { v ->
                                // Decimals allowed ("7.5"); at most one dot and two decimals.
                                val cleaned = v.filter { it.isDigit() || it == '.' }
                                if (cleaned.count { it == '.' } <= 1 && cleaned.substringAfter('.', "").length <= 2) {
                                    priceText = cleaned
                                    update(index) { it.copy(pricePerAttendeeUsd = cleaned.toDoubleOrNull() ?: 0.0) }
                                }
                            },
                            label = stringResource(R.string.person),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                    val max = tier.maxAttendees
                    if (max != null && max < tier.minAttendees) {
                        Text(stringResource(R.string.to_must_be_at_least_from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (!tier.isEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.this_tier_is_turned_off), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            TextButton(onClick = { update(index) { it.copy(isEnabled = true) } }) { Text(stringResource(R.string.turn_on)) }
                        }
                    }
                }
            }
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val nextMin = (tiers.mapNotNull { it.maxAttendees }.maxOrNull() ?: 0) + 1
                    onTiersChange(
                        tiers + AttendeePackage(
                            id = "TIER-" + UUID.randomUUID().toString().take(6).uppercase(),
                            name = uiText(R.string.tier_x, tiers.size + 1),
                            pricePerAttendeeUsd = 0.0,
                            minAttendees = nextMin,
                            isSystemDefault = false
                        )
                    )
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.add_tier)) }
            if (tiers.isEmpty() && templates.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        onTiersChange(
                            templates.sortedBy { it.minAttendees }.map {
                                it.copy(id = "TIER-" + UUID.randomUUID().toString().take(6).uppercase(), isSystemDefault = false)
                            }
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.use_templates)) }
            }
        }
    }
}

/** The Create Listing wizard's division editor: [SubdivisionForm] in a full-height sheet ([initial] null = new division). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DivisionSheet(
    spaceId: String,
    initial: Subdivision?,
    onSave: (Subdivision) -> Unit,
    onDismiss: () -> Unit,
    operatingDays: List<String>,
    openingHour: String,
    closingHour: String,
    availableAmenities: List<SchemaItem> = emptyList(),
    onAddCustomAmenity: ((name: String, divisionTypeId: String) -> Unit)? = null,
    attendeeTemplates: List<AttendeePackage> = emptyList()
) {
    TrackScreen("listing_division_sheet", "CreateListingDialog")
    ProHostBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(
                if (initial != null) stringResource(R.string.edit_division) else stringResource(R.string.add_division),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            SubdivisionForm(
                spaceId = spaceId,
                initial = initial,
                onSave = onSave,
                onCancel = onDismiss,
                operatingDays = operatingDays,
                openingHour = openingHour,
                closingHour = closingHour,
                availableAmenities = availableAmenities,
                onAddCustomAmenity = onAddCustomAmenity,
                attendeeTemplates = attendeeTemplates
            )
        }
    }
}
