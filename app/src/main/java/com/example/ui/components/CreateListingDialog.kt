package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.storage.FirebaseStorageService
import com.example.ui.theme.Spacing
import com.example.ui.util.SpaceCalculationUtils
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// Savers for rememberSaveable — process death mid-wizard would otherwise lose all
// in-progress state. The debounced 3s Draft auto-save already covers a kill that
// happens after that window fires; these close the narrower "killed within the
// first few seconds" gap for the fields cheap enough to make Bundle-safe.
private val CategoryEnumSaver = Saver<SpaceType, String>(
    save = { it.name },
    restore = { runCatching { SpaceType.valueOf(it) }.getOrDefault(SpaceType.PRIVATE_OFFICE) }
)

private val GovernorateSaver = Saver<Governorate, String>(
    save = { it.name },
    restore = { runCatching { Governorate.valueOf(it) }.getOrDefault(Governorate.BEIRUT) }
)

private val EquipmentCategorySaver = Saver<EquipmentCategory, String>(
    save = { it.name },
    restore = { runCatching { EquipmentCategory.valueOf(it) }.getOrDefault(EquipmentCategory.WORKSPACES) }
)

private val OwnershipRoleSaver = Saver<OwnershipRole?, String>(
    save = { it?.name ?: "" },
    restore = { name -> name.takeIf { it.isNotEmpty() }?.let { runCatching { OwnershipRole.valueOf(it) }.getOrNull() } }
)

private val PhoneCountrySaver = Saver<Country, String>(
    save = { it.name },
    restore = { findCountryByName(it) }
)

private val DocumentPickerStateSaver = Saver<DocumentPickerState, List<String?>>(
    save = { listOf(it.uri?.toString(), it.fileName) },
    restore = { DocumentPickerState(it.getOrNull(0)?.let(Uri::parse), it.getOrNull(1)) }
)

private val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

private val StringListSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it }
)

private val EquipmentListSaver = listSaver<List<EquipmentItem>, Any?>(
    save = { list -> list.flatMap { listOf(it.id, it.name, it.category.name, it.quantity, it.description) } },
    restore = { flat ->
        flat.chunked(5).map {
            EquipmentItem(
                id = it[0] as String,
                name = it[1] as String,
                category = runCatching { EquipmentCategory.valueOf(it[2] as String) }.getOrDefault(EquipmentCategory.WORKSPACES),
                quantity = it[3] as Int,
                description = it[4] as String
            )
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun CreateListingDialog(
    currentUser: AppUser?,
    onDismiss: () -> Unit,
    onListingCreated: (SpaceListing) -> Unit,
    existingDraft: SpaceListing? = null,
    onSaveDraft: (SpaceListing) -> Unit = {},
    // Admin-only "edit an already-existing (non-Draft) listing" mode — set only when
    // this dialog is opened from the Listings Catalog admin tab's Edit action. When
    // non-null: the final step's primary button reads "Save Changes" and calls THIS
    // instead of onListingCreated (a straight repository update, not a new publish —
    // it must not re-run the create-flow's package-limit gating, which exists to
    // meter NEW listings, not edits to ones that already exist), "Save as Draft" is
    // hidden (editing an existing ACTIVE/PAUSED listing should never silently demote
    // it to Draft), and the listing's own current status is preserved rather than
    // forced to ACTIVE. The ordinary create/continue-a-Draft flow (existingDraft set,
    // this left null) is completely unaffected and still goes through onListingCreated.
    onListingUpdated: ((SpaceListing) -> Unit)? = null,
    // Silent, periodic auto-save while the wizard is open — debounced, never
    // closes the dialog or shows a toast (unlike the explicit "Save as Draft"
    // button above, which does both, per its own onClick). Defaults to a no-op
    // so a caller that hasn't been updated to pass it yet just doesn't get
    // auto-save, rather than crashing.
    onAutoSaveDraft: (SpaceListing) -> Unit = {},
    // Top-used hashtags across the platform, fetched once by the caller when the
    // dialog opens (ProHostViewModel.topHashtags) — filtered client-side by prefix
    // as the host types, so this needs no per-keystroke network query.
    suggestedHashtags: List<String> = emptyList(),
    // Admin-managed facility catalog (enabled SchemaItems, category "AMENITY") —
    // defaults to the old hardcoded FacilityCatalog.standard only so a caller that
    // hasn't been updated to pass the live list doesn't lose facilities entirely.
    availableFacilities: List<String> = FacilityCatalog.standard,
    // Admin-managed Space Category catalog (enabled SchemaItems, category
    // "SPACE_TYPE") — replaces the old closed SpaceType.values() picker. Empty
    // falls back to the 4 legacy types below so a caller that hasn't been updated
    // yet doesn't lose the category picker entirely.
    spaceCategories: List<SchemaItem> = emptyList(),
    // Admin-managed amenity catalog (enabled SchemaItems, category "AMENITY") —
    // passed into SubdivisionEditorSection, which filters by division type scope.
    availableAmenities: List<SchemaItem> = emptyList(),
    // Admin-managed division type catalog (enabled SchemaItems, category "DIVISION_TYPE") —
    // passed into SubdivisionEditorSection to check supportsAttendeeMode per type.
    availableDivisionTypeSchema: List<SchemaItem> = emptyList(),
    // Write-back: called when the host types a new custom facility or equipment item
    // so it gets persisted to the global schema catalog as a CUSTOM NODE.
    // Receives the category (FACILITY or AMENITY), name, and optional scopedToIds.
    // No-op default so unupdated callers don't crash.
    onAddCustomSchemaItem: (category: String, name: String, scopedToIds: List<String>) -> Unit = { _, _, _ -> }
) {
    if (currentUser == null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign In Required", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Your account couldn't be loaded. Please sign in again before creating a listing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CustomButton(text = "Close", onClick = onDismiss, modifier = Modifier.align(Alignment.End), variant = CustomButtonVariant.TEXT)
                }
            }
        }
        return
    }
    val activeUser = currentUser

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }

    // Generated up front (not just at submit time) so photos can upload to their final
    // listings/{listingId}/ path as soon as they're picked, instead of at the end.
    // Reuses the draft's own id when continuing one, so "Save as Draft" -> "Continue
    // Editing" -> "Publish" all write to the same document instead of forking a
    // second listing.
    val listingId = remember { existingDraft?.id ?: ("SPC-LB-" + UUID.randomUUID().toString().take(6).uppercase()) }
    var hasUserTyped by rememberSaveable { mutableStateOf(existingDraft != null) }
    // Drives the small "Draft auto-saved" caption in the header — set once the
    // auto-save LaunchedEffect below has actually fired at least once.
    var lastAutoSavedAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var uploadedPhotoUrls by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(existingDraft?.imageUrls ?: emptyList()) }
    var isUploadingPhoto by rememberSaveable { mutableStateOf(false) }
    // uploadAndGetUrl (FirebaseStorageService) already catches every upload failure and
    // returns null rather than throwing — necessary so one bad file doesn't crash the
    // coroutine, but it previously meant a permission-denied/network failure here was
    // completely silent: the picker just did nothing and the host had no idea why. Not
    // rememberSaveable — transient feedback, cleared on the next attempt either way.
    var photoUploadError by remember { mutableStateOf<String?>(null) }

    val ownershipProofUrl = existingDraft?.ownershipProofUrl
    val ownershipRole = existingDraft?.ownershipDocRole
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingPhoto = true
            photoUploadError = null
            var failureCount = 0
            uris.forEach { uri ->
                val imageId = UUID.randomUUID().toString().take(8)
                val fileSizeBytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getLong(0) else null
                        }
                    }.getOrNull()
                }
                if (fileSizeBytes != null && fileSizeBytes > 15 * 1024 * 1024) {
                    failureCount++
                    photoUploadError = "One or more images exceed the 15 MB limit. Please choose smaller files."
                    return@forEach
                }
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
                val url = if (bytes != null) {
                    storageService.uploadListingImageBytes(
                        spaceId = listingId,
                        imageId = imageId,
                        rawBytes = bytes,
                        fileExtension = "jpg"
                    )
                } else null
                if (url != null) {
                    uploadedPhotoUrls = uploadedPhotoUrls + url
                } else {
                    failureCount++
                }
            }
            if (failureCount > 0) {
                val detail = FirebaseStorageService.lastUploadError ?: "Check your connection and try again."
                photoUploadError = if (failureCount == uris.size) {
                    "Couldn't upload ${if (uris.size == 1) "that photo" else "those photos"}: $detail"
                } else {
                    "$failureCount of ${uris.size} photos failed to upload: $detail"
                }
            }
            isUploadingPhoto = false
        }
    }

    var title by rememberSaveable { mutableStateOf(existingDraft?.title ?: "") }
    var selectedSpaceType by rememberSaveable(stateSaver = CategoryEnumSaver) { mutableStateOf(existingDraft?.spaceType ?: SpaceType.PRIVATE_OFFICE) }

    // Admin-defined Space Category catalog (spec 1.1) — replaces the closed 4-value
    // picker. Falls back to a synthetic list mirroring the 4 legacy SpaceType values
    // when the admin schema is empty, so the picker is never blank.
    val categoryOptions = remember(spaceCategories) {
        spaceCategories.filter { it.isEnabled }.ifEmpty {
            SpaceType.values().map { legacy ->
                SchemaItem(id = legacy.name, name = legacy.displayName, category = "SPACE_TYPE")
            }
        }
    }
    // Best-effort mapping onto the closed legacy enum, purely for readers that still
    // key off SpaceListing.spaceType (Discovery filters, badges) — a category with no
    // obvious match (a brand-new admin category, or one of the two schema-only
    // categories with no legacy equivalent) falls back to PRIVATE_OFFICE rather than
    // crashing on a missing branch.
    fun legacyTypeFor(categoryId: String?): SpaceType = when (categoryId) {
        "ST-01", SpaceType.PRIVATE_OFFICE.name -> SpaceType.PRIVATE_OFFICE
        "ST-02", SpaceType.CENTER.name -> SpaceType.CENTER
        "ST-03", SpaceType.POLYCLINIC.name -> SpaceType.POLYCLINIC
        "ST-04", SpaceType.COWORKING_SPACE.name -> SpaceType.COWORKING_SPACE
        else -> SpaceType.PRIVATE_OFFICE
    }
    // Real category identity — a SchemaItem.id/name from the admin-defined catalog.
    // selectedSpaceType above is kept in sync purely for legacy readers. Defaults to
    // whichever category maps onto the draft's (or a fresh listing's) legacy type so
    // the picker never starts with nothing selected.
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(
            existingDraft?.spaceCategoryId
                ?: categoryOptions.firstOrNull { legacyTypeFor(it.id) == selectedSpaceType }?.id
                ?: categoryOptions.firstOrNull()?.id
        )
    }
    var selectedCategoryName by rememberSaveable {
        mutableStateOf(existingDraft?.spaceCategoryName ?: categoryOptions.firstOrNull { it.id == selectedCategoryId }?.name)
    }
    // No longer shown as its own picker — a governorate field alongside a real map
    // pin only ever fought the map (see ListingLocationMapPicker's doc comment).
    // Derived instead from the picked pin's nearest match in buildListing(), purely
    // to keep the existing governorate-keyed fields (Discovery filters, address
    // fallback text) working unchanged. Starts at the draft's last-known value so a
    // resumed draft doesn't jump to Beirut before its pin is re-picked.
    var derivedGovernorate by rememberSaveable(stateSaver = GovernorateSaver) { mutableStateOf(existingDraft?.governorate ?: Governorate.BEIRUT) }
    var description by rememberSaveable { mutableStateOf(existingDraft?.description ?: "") }
    var country by rememberSaveable { mutableStateOf(existingDraft?.country ?: "") }
    var city by rememberSaveable { mutableStateOf(existingDraft?.city ?: "") }
    var streetAddress by rememberSaveable { mutableStateOf(existingDraft?.streetAddress ?: "") }

    // Real geolocation from the map picker below — required to publish. Distinct from
    // [district]/[streetAddress] above, which the host types freely; a picked address
    // auto-applies into those fields the moment the pin is dropped/moved, but never
    // overwrites text the host has already typed there themselves.
    // Prefilled from a resumed Draft's last saved lat/lng (a host used to have to
    // re-find and re-drop the exact same pin from scratch every time they continued
    // one). Worst case for a very old Draft saved before the pin was ever set, this
    // is the jittered governorate-center fallback rather than a real pin — still a
    // far better starting point on the map than the generic default center, and the
    // host still explicitly confirms/moves it before Publish unlocks (see the Step 1
    // gate below).
    // GeoPoint implements Parcelable/Serializable, so it's Bundle-safe with the
    // default saver directly — no custom Saver needed.
    var pickedLatLng by rememberSaveable { mutableStateOf(existingDraft?.let { LatLng(it.lat, it.lng) }) }
    // Numeric floor, range -5..30 per spec (basement levels down to a high-rise's
    // upper floors). Was a free-text "Floor & Accessibility" string; accessibility
    // notes belong in the description/rules now, not smuggled into a number field.
    var floorNumber by rememberSaveable { mutableStateOf(existingDraft?.floorInfo?.filter { it.isDigit() || it == '-' }?.toIntOrNull() ?: 1) }
    // isShared is no longer a Step 1 toggle — subdivision vs. whole-space is decided
    // in Step 3 (Availability) instead. Kept as an inert field defaulting to the
    // draft's last value (or true, matching the removed toggle's old default) so
    // nothing downstream that still reads SpaceListing.isShared silently changes.
    val isShared = existingDraft?.isShared ?: true
    var ownerPhone by rememberSaveable { mutableStateOf(existingDraft?.ownerPhone ?: activeUser.phone) }
    // Split into a real country-code picker + local digits (spec 2.1) — this used
    // to be a bare text field with just a "+961 ..." placeholder hint, despite
    // PhoneNumberField/CountryPickerDialog already existing and being used for
    // exactly this purpose at login/registration. Best-effort split of whatever
    // combined string a resumed Draft already carries: match its longest known
    // dial-code prefix, defaulting to Lebanon for a brand-new listing.
    var ownerPhoneCountry by rememberSaveable(stateSaver = PhoneCountrySaver) {
        mutableStateOf(
            COUNTRIES.filter { ownerPhone.trim().startsWith(it.dialCode) }
                .maxByOrNull { it.dialCode.length }
                ?: COUNTRIES.first { it.isoCode == "LB" }
        )
    }
    var ownerPhoneLocal by rememberSaveable {
        mutableStateOf(ownerPhone.trim().removePrefix(ownerPhoneCountry.dialCode).trim())
    }

    // Target Disciplines (hashtags) — free-typed, not a fixed chip list; each publish
    // records its tags centrally (ProHostViewModel.createNewSpaceListing) so future
    // hosts see suggestions drawn from real prior usage. suggestedHashtags below is
    // the caller-supplied top-used list (fetched once when the dialog opens), filtered
    // client-side by prefix as the host types — no per-keystroke network query.
    var hashtagInput by rememberSaveable { mutableStateOf("") }
    var selectedSpecialties by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(existingDraft?.complementarySpecialties?.toSet() ?: emptySet()) }

    // Operating hours & days — previously collected only post-publish
    // (SpaceScheduleEditorDialog); now part of the wizard itself (spec 2.2/2.3) since
    // Step 3's per-strategy availability tables need real operating hours/days to key
    // off from the moment they're built.
    val weekDayOptions = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var operatingDays by rememberSaveable(stateSaver = StringSetSaver) {
        mutableStateOf(existingDraft?.schedule?.operatingDays?.toSet() ?: setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
    }
    var openingHour by rememberSaveable { mutableStateOf(existingDraft?.schedule?.openingHour ?: "08:00") }
    var closingHour by rememberSaveable { mutableStateOf(existingDraft?.schedule?.closingHour ?: "20:00") }



    // Facilities toggles
    var masterFacilities by remember { mutableStateOf(availableFacilities) }
    var selectedFacilities by rememberSaveable(stateSaver = StringSetSaver) {
        mutableStateOf(existingDraft?.essentialFacilities?.toSet() ?: masterFacilities.take(3).toSet())
    }
    var showFacilityDialog by remember { mutableStateOf(false) }


    // Premises rules — real editable fields, replacing the previously-hardcoded
    // PremisesRules() default at listing construction.
    var smokingAllowed by rememberSaveable { mutableStateOf(existingDraft?.rules?.smokingAllowed ?: false) }
    var foodAllowed by rememberSaveable { mutableStateOf(existingDraft?.rules?.foodAllowed ?: true) }
    var petsAllowed by rememberSaveable { mutableStateOf(existingDraft?.rules?.petsAllowed ?: false) }
    var offHoursAccess by rememberSaveable { mutableStateOf(existingDraft?.rules?.offHoursAccess ?: true) }
    var visitorPolicy by rememberSaveable { mutableStateOf(existingDraft?.rules?.visitorPolicy ?: "Clients & visitors welcomed in reception lounge") }

    // Subdivision States (Level 2 Rooms & Desks) — the "add a room" form fields
    // themselves now live inside SubdivisionEditorSection (see that file); this
    // dialog only hoists the resulting list, since buildListing() needs it.
    // Deliberately excluded from the rememberSaveable retrofit — a complex nested data
    // class list with no natural flat representation; the existing 3s Draft auto-save
    // already covers a process death beyond that narrow window, and a custom Saver here
    // would mean hand-maintaining a third serialization format (Firestore map, in-memory
    // object, now Bundle) for one of the wizard's most complex config types.
    var subdivisionsList by remember { mutableStateOf(existingDraft?.subdivisions ?: listOf<Subdivision>()) }
    // The listing's own pricing when it has no divisions — real structured config,
    // not the old free-text-only "base monthly valuation" field (baseMonthlyRate
    // stays as a legacy display-only derivative, computed from this in buildListing()).
    // Deliberately excluded — same reasoning as subdivisionsList above; this is the
    // single most complex config type in the wizard.
    var wholeSpacePricing by remember { mutableStateOf(existingDraft?.pricing ?: RentalPricingConfig.default()) }

    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    // A genuine, independent choice now (spec Step 3's opening toggle) — replacing
    // the previous inference from selectedSpaceType, which made a divided Private
    // Office or an undivided Center impossible (the two facts have nothing to do
    // with each other; a host might want either combination). Defaults from
    // whether a resumed draft already has any subdivisions, or a sensible guess
    // from the category otherwise, so a fresh wizard doesn't start on a jarring
    // default.
    var hasSubdivisions by rememberSaveable {
        mutableStateOf(existingDraft?.let { it.subdivisions.isNotEmpty() } ?: (selectedSpaceType != SpaceType.PRIVATE_OFFICE))
    }
    // Always 3, matching the spec's fixed Step 1/2/3 structure — previously 4 for a
    // subdivided listing but only 3 for an undivided one, which meant the
    // "Contact, Premises Rules & Ownership Proof" step (index 3) was completely
    // UNREACHABLE for any Private Office listing (the default selectedSpaceType for
    // every new listing): the Publish button's enabled check still required
    // ownershipProofUrl != null, but no step in range 0..totalSteps-1 ever rendered
    // the picker that could set it. A Private Office listing could never actually be
    // published through this wizard. Moving ownership to Step 1 (always reachable)
    // fixes this as a side effect of the restructuring, not as a separate patch.
    val totalSteps = 3

    // Blackout Slots and Additional Rental Formulas (briefly ported in from the
    // now-deleted SpaceScheduleEditorDialog) were removed again on request — the
    // single-price-per-slot pricing model (RentalPricingConfigEditor above) already
    // covers what a listing needs to publish, and these two controls duplicated
    // that without adding anything the current booking logic actually reads. Any
    // blackout slots a listing already had before this removal are preserved as-is
    // (see buildListing() below) — there is just no UI here to add more.

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.95f),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (existingDraft != null) "Continue Draft Listing" else "Publish Workspace Listing",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps • Lebanon Network",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Only ever true once the auto-save LaunchedEffect below has
                        // actually fired — never claims a save that didn't happen.
                        if (lastAutoSavedAtMillis != null) {
                            Text(
                                text = "Draft auto-saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                val scrollState = rememberScrollState()
                LaunchedEffect(currentStep) {
                    scrollState.scrollTo(0)
                }

                // Scrollable Content per step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    when (currentStep) {
                        0 -> {
                            // Step 1: Space Definition & Ownership Verification
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "Space Identification",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                InputField(
                                    value = title,
                                    onValueChange = { title = it; hasUserTyped = true },
                                    label = "Space Brand Name (e.g. Achrafieh Executive Suite)",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text("Space Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categoryOptions) { category ->
                                        FilterChip(
                                            selected = selectedCategoryId == category.id,
                                            onClick = {
                                                selectedCategoryId = category.id
                                                selectedCategoryName = category.name
                                                selectedSpaceType = legacyTypeFor(category.id)
                                            },
                                            label = {
                                                Text(category.name, style = MaterialTheme.typography.labelMedium)
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text(
                                    "Location & Description",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                InputField(
                                    value = description,
                                    onValueChange = { if (it.length <= 100) description = it },
                                    label = "Description (${description.length}/100)",
                                    placeholder = "One or two lines a specialist sees before opening the listing",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3
                                )

                                Text("Pin the Exact Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Drop or drag the marker to the real GPS coordinates specialists will see when searching nearby — required " +
                                    "to publish. The address fields below fill in automatically; edit them freely afterward.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ListingLocationMapPicker(
                                    initialLat = pickedLatLng?.latitude ?: existingDraft?.lat,
                                    initialLng = pickedLatLng?.longitude ?: existingDraft?.lng,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    onLocationConfirmed = { lat, lng, street, resolvedCity, resolvedCountry ->
                                        pickedLatLng = LatLng(lat, lng)
                                        if (street.isNotBlank()) streetAddress = street
                                        if (resolvedCity.isNotBlank()) city = resolvedCity
                                        if (resolvedCountry.isNotBlank()) country = resolvedCountry
                                    }
                                )

                                InputField(
                                    value = country,
                                    onValueChange = { country = it },
                                    label = "Country",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                InputField(
                                    value = city,
                                    onValueChange = { city = it },
                                    label = "City",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                InputField(
                                    value = streetAddress,
                                    onValueChange = { streetAddress = it },
                                    label = "Street Address & Building Name",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text("Floor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconButton(onClick = { if (floorNumber > -5) floorNumber-- }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease floor")
                                    }
                                    Text(
                                        text = if (floorNumber == 0) "Ground Floor" else "Floor $floorNumber",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(onClick = { if (floorNumber < 30) floorNumber++ }) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase floor")
                                    }
                                }

                                HorizontalDivider()

                                Text(
                                    "Target Disciplines",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Hashtag the rentee backgrounds you'd prefer (e.g. #Cardiologist, #Architect).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = hashtagInput,
                                        onValueChange = { hashtagInput = it },
                                        placeholder = { Text("#Discipline") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    TextButton(onClick = {
                                        val tag = hashtagInput.trim().trimStart('#')
                                        if (tag.isNotBlank()) {
                                            selectedSpecialties = selectedSpecialties + tag
                                            hashtagInput = ""
                                        }
                                    }) { Text("Add") }
                                }
                                val matchingSuggestions = suggestedHashtags.filter {
                                    hashtagInput.isNotBlank() && it.contains(hashtagInput.trim().trimStart('#'), ignoreCase = true) &&
                                        !selectedSpecialties.contains(it)
                                }.take(6)
                                if (matchingSuggestions.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(matchingSuggestions) { suggestion ->
                                            AssistChip(
                                                onClick = { selectedSpecialties = selectedSpecialties + suggestion; hashtagInput = "" },
                                                label = { Text("#$suggestion", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                                if (selectedSpecialties.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(selectedSpecialties.toList()) { tag ->
                                            InputChip(
                                                selected = true,
                                                onClick = { selectedSpecialties = selectedSpecialties - tag },
                                                label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                                            )
                                        }
                                    }
                                }



                                HorizontalDivider()

                                Text("Cover Photos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Real photos of the space — shown first in search results. At least one is required to publish (Save as " +
                                    "Draft never needs one).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uploadedPhotoUrls.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uploadedPhotoUrls) { url ->
                                        Box(
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                        ) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { uploadedPhotoUrls = uploadedPhotoUrls - url },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove photo",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Surface(
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(MaterialTheme.shapes.medium)
                                                .clickable(enabled = !isUploadingPhoto) {
                                                    photoPickerLauncher.launch("image/*")
                                                },
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isUploadingPhoto) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                } else {
                                                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo")
                                                }
                                            }
                                        }
                                    }
                                }
                                if (photoUploadError != null) {
                                    Text(
                                        photoUploadError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Step 2: Operational Parameters & Facility Rules
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    "Communication Setup",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                PhoneNumberField(
                                    country = ownerPhoneCountry,
                                    onCountryChange = {
                                        ownerPhoneCountry = it
                                        ownerPhone = "${it.dialCode} $ownerPhoneLocal"
                                    },
                                    number = ownerPhoneLocal,
                                    onNumberChange = {
                                        ownerPhoneLocal = it
                                        ownerPhone = "${ownerPhoneCountry.dialCode} $it"
                                    },
                                    label = "WhatsApp Number for Booking Requests",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                HorizontalDivider()

                                Text(
                                    "Facility Operating Hours & Days",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Controls the availability logic in Step 3 — the days and hours you select here are the only ones a rentable " +
                                    "slot can ever be offered in.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OperatingScheduleEditorSection(
                                    openingHour = openingHour,
                                    onOpeningHourChange = { openingHour = it },
                                    closingHour = closingHour,
                                    onClosingHourChange = { closingHour = it },
                                    selectedDays = operatingDays,
                                    onDaysChange = { operatingDays = it },
                                    weekDayOptions = weekDayOptions
                                )

                                HorizontalDivider()

                                Text("Shared Essential Facilities", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                val firstFacility = selectedFacilities.firstOrNull()
                                val facilitySummary = if (firstFacility != null) {
                                    if (selectedFacilities.size > 1) {
                                        "$firstFacility (+${selectedFacilities.size - 1} more selected)"
                                    } else {
                                        "$firstFacility selected"
                                    }
                                } else {
                                    "No facilities selected"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    onClick = { showFacilityDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(facilitySummary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Tap to open picker & manage master list",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "See More ➔",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text(
                                    "Premises Rules and Policy",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Smoking Allowed", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = smokingAllowed, onCheckedChange = { smokingAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Food Allowed", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = foodAllowed, onCheckedChange = { foodAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pets Allowed", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = petsAllowed, onCheckedChange = { petsAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Off-Hours Access", style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = offHoursAccess, onCheckedChange = { offHoursAccess = it })
                                }
                                InputField(
                                    value = visitorPolicy,
                                    onValueChange = { visitorPolicy = it },
                                    label = "Visitor Policy",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        2 -> {
                            // Step 3: Availability Control Logic
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Mode selector card
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    shape = MaterialTheme.shapes.large,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "How do specialists rent this space?",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            if (hasSubdivisions) "Divisions — specialists pick a specific room or desk inside the space."
                                            else "Whole Space — specialists rent the entire space as-is.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(false to "Whole Space", true to "Has Divisions").forEach { (value, label) ->
                                                FilterChip(
                                                    selected = hasSubdivisions == value,
                                                    onClick = { hasSubdivisions = value },
                                                    label = { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) },
                                                    leadingIcon = if (hasSubdivisions == value) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                    } else null,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (hasSubdivisions) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(Spacing.md),
                                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                                Icon(
                                                    Icons.Default.Dashboard,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Text(
                                                    "Space-level (Steps 1 & 2): Name · Location · Facilities · Equipment · Rules",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                                Icon(
                                                    Icons.Default.MeetingRoom,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "Per-subdivision (this step): Room name · Type · Amenities · Photos · Pricing",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.sm))
                                    SubdivisionEditorSection(
                                        spaceId = listingId,
                                        subdivisionsList = subdivisionsList,
                                        onSubdivisionsChange = { subdivisionsList = it },
                                        operatingDays = operatingDays.toList(),
                                        openingHour = openingHour,
                                        closingHour = closingHour,
                                        availableAmenities = availableAmenities,
                                        onAddCustomAmenity = { name, divisionTypeId ->
                                            onAddCustomSchemaItem("AMENITY", name, listOf(divisionTypeId))
                                        },
                                        availableDivisionTypeSchema = availableDivisionTypeSchema
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.large,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                                Icon(
                                                    Icons.Default.Payments,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text("Renting Formula", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            RentalPricingConfigEditor(
                                                config = wholeSpacePricing,
                                                operatingDays = operatingDays.toList(),
                                                openingHour = openingHour,
                                                closingHour = closingHour,
                                                onConfigChange = { wholeSpacePricing = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Builds the SpaceListing from the wizard's current field state — shared
                // by both "Publish Listing" (status ACTIVE) and "Save as Draft" (status
                // DRAFT, no requiredness gating) so the two paths can never disagree on
                // how a listing gets assembled.
                fun buildListing(status: ListingStatus, fallbackLatLng: LatLng? = null): SpaceListing {
                    // The real source of truth for an undivided listing's pricing is
                    // wholeSpacePricing (set via RentalPricingConfigEditor above) — no
                    // longer a free-text "base monthly valuation" field. formulas below
                    // is a synthesized legacy bridge, not a second source of truth: it
                    // exists only so screens not yet migrated to RentalPricingConfig
                    // (SpaceDetailsScreen's formula list, RentalBookingDialog's
                    // non-subdivision branch — both deferred to a later phase) still
                    // show something representative for a freshly-published listing,
                    // rather than "no formulas" for a listing that genuinely has real
                    // pricing configured.
                    val formulas = mutableListOf<RentalFormula>()
                    var monthly = existingDraft?.baseMonthlyRateUsd ?: 500.0
                    if (!hasSubdivisions) {
                        val p = wholeSpacePricing
                        when (p.strategyType) {
                            RentalStrategyType.MONTHLY -> {
                                val m = p.monthly ?: MonthlyConfig()
                                monthly = m.rateUsd
                                formulas.add(
                                    RentalFormula(
                                        type = RentalFormulaType.FULL_MONTH, rateUsd = m.rateUsd,
                                        scheduleDescription = "Dedicated Full Workspace Month (All operating days)",
                                        daysOfWeek = operatingDays.toList(), startHour = openingHour, endHour = closingHour
                                    )
                                )
                            }
                            RentalStrategyType.HOURLY -> {
                                val prices = p.hourly?.cellPrices ?: emptyMap()
                                val avgRate = prices.values.average().takeIf { !it.isNaN() } ?: 25.0
                                monthly = avgRate * 8 * 22 // rough monthly-equivalent for the legacy display field only
                                formulas.add(
                                    RentalFormula(
                                        type = RentalFormulaType.HOURLY, rateUsd = avgRate,
                                        scheduleDescription = "Hourly Rental", daysOfWeek = operatingDays.toList(),
                                        startHour = openingHour, endHour = closingHour
                                    )
                                )
                            }
                            RentalStrategyType.SHIFT_BASED -> {
                                val activeShift = p.shiftBased?.shifts?.firstOrNull { !it.isUnavailable }
                                val rate = activeShift?.price ?: 60.0
                                monthly = rate * 20
                                formulas.add(
                                    RentalFormula(
                                        type = RentalFormulaType.SHIFT, rateUsd = rate,
                                        scheduleDescription = "Shift Rental", daysOfWeek = operatingDays.toList(),
                                        startHour = activeShift?.startHour?.let { "%02d:00".format(it) } ?: openingHour,
                                        endHour = activeShift?.endHour?.let { "%02d:00".format(it) } ?: closingHour,
                                        shiftName = activeShift?.name?.displayName ?: "Morning Shift"
                                    )
                                )
                            }
                            RentalStrategyType.DAY_BASED -> {
                                val prices = p.dayBased?.distribution?.values?.map { it.price }?.filter { it > 0.0 } ?: emptyList()
                                val rate = prices.average().takeIf { !it.isNaN() } ?: 120.0
                                monthly = rate * (p.dayBased?.distribution?.size?.takeIf { it > 0 } ?: 4)
                                formulas.add(
                                    RentalFormula(
                                        type = RentalFormulaType.DAY_PER_WEEK, rateUsd = rate,
                                        scheduleDescription = "Day-Based Rental",
                                        daysOfWeek = p.dayBased?.distribution?.keys?.toList() ?: operatingDays.toList(),
                                        startHour = openingHour, endHour = closingHour,
                                        daysCountRequired = (p.dayBased?.distribution?.size ?: 1).coerceAtLeast(1)
                                    )
                                )
                            }
                        }
                    }
                    // Each Subdivision already got its own pricing set eagerly in
                    // SubdivisionEditorSection's Add button — the listing itself
                    // carries no pricing of its own when it has divisions.
                    val pricingConfig = if (!hasSubdivisions) wholeSpacePricing else RentalPricingConfig.default()

                    // Prefer the real pin dropped on the map (recorded via
                    // ListingLocationMapPicker above); otherwise use whatever fallback
                    // the caller already resolved (see resolveFallbackGeocode() below —
                    // this function itself never geocodes, so it stays main-thread-safe
                    // even when called synchronously from snapshotFlow's auto-save).
                    var geocodedLat = fallbackLatLng?.latitude
                        ?: (derivedGovernorate.centerLat + ((-20..20).random() / 1000.0))
                    var geocodedLng = fallbackLatLng?.longitude
                        ?: (derivedGovernorate.centerLng + ((-20..20).random() / 1000.0))
                    val pinned = pickedLatLng
                    if (pinned != null) {
                        geocodedLat = pinned.latitude
                        geocodedLng = pinned.longitude
                    }

                    return SpaceListing(
                        id = listingId,
                        title = title.ifBlank {
                            listOf(city.trim(), selectedCategoryName ?: selectedSpaceType.displayName)
                                .filter { it.isNotBlank() }.joinToString(" ")
                        },
                        description = description,
                        spaceType = selectedSpaceType,
                        spaceCategoryId = selectedCategoryId,
                        spaceCategoryName = selectedCategoryName,
                        governorate = derivedGovernorate,
                        district = "",
                        streetAddress = streetAddress.trim(),
                        country = country.trim(),
                        city = city.trim(),
                        floorInfo = if (floorNumber == 0) "Ground Floor" else "Floor $floorNumber",
                        lat = geocodedLat,
                        lng = geocodedLng,
                        isShared = isShared,
                        complementarySpecialties = selectedSpecialties.toList(),
                        // Never overwrite this with just whoever is currently editing —
                        // ProHostRepository's booking-accept flow is the real, live-
                        // mutating source of this list (appends "name (specialty)" per
                        // accepted booking); a save here used to always clobber it back
                        // down to one entry, discarding every specialist a host had
                        // actually accepted since publish.
                        residentPractitioners = existingDraft?.residentPractitioners ?: emptyList(),
                        essentialFacilities = selectedFacilities.toList(),
                        equipment = existingDraft?.equipment ?: emptyList(),
                        pricing = pricingConfig,
                        rentalFormulas = formulas,
                        rules = PremisesRules(
                            smokingAllowed = smokingAllowed,
                            foodAllowed = foodAllowed,
                            petsAllowed = petsAllowed,
                            visitorPolicy = visitorPolicy,
                            offHoursAccess = offHoursAccess
                        ),
                        schedule = SpaceOperatingSchedule(
                            openingHour = openingHour,
                            closingHour = closingHour,
                            operatingDays = operatingDays.toList(),
                            // No in-wizard control adds to this anymore (removed on request —
                            // see the removal note near this dialog's top-level state) — carry
                            // forward whatever a listing already had rather than wiping it.
                            blackoutSlots = existingDraft?.schedule?.blackoutSlots ?: emptyList()
                        ),
                        ownerId = activeUser.id,
                        ownerName = activeUser.fullName,
                        ownerPhone = ownerPhone,
                        ownerEmail = activeUser.email,
                        ownershipProofUrl = ownershipProofUrl,
                        ownershipDocRole = ownershipRole,
                        // Genuinely earned now (see SpaceListing.isVerified's doc
                        // comment) — a new listing starts unverified; the host can
                        // optionally earn the badge afterward from the listing card
                        // ("Get Listing Verified").
                        isVerified = false,
                        isActiveSubscription = true,
                        baseMonthlyRateUsd = monthly,
                        // Gated on the live hasSubdivisions toggle, not just whatever
                        // subdivisionsList still holds — a host who added a room, then
                        // switched back to "whole space," used to still publish with
                        // that room's (likely blank/zero) pricing underneath, since
                        // buildAllSlotsForSpace picks whole-space vs. per-subdivision
                        // pricing purely from whether subdivisions is empty. This is
                        // the actual source of truth for that decision, so it must
                        // agree with what the host currently sees on screen.
                        subdivisions = if (hasSubdivisions) subdivisionsList else emptyList(),
                        imageUrls = uploadedPhotoUrls,
                        ownerProfilePictureUrl = activeUser.profilePictureUrl,
                        status = status
                    )
                }

                // Resolves a fallback pin for buildListing() when the host typed an
                // address but never dropped a map pin, off the main thread (Geocoder's
                // synchronous lookup used to run directly on the composition thread here
                // — moved out into its own suspend function, called from a coroutine at
                // each button's onClick, so buildListing() itself never blocks). Returns
                // null (falling back to buildListing()'s own jittered-governorate-center
                // default) whenever a pin already exists or there's nothing to geocode —
                // matches ListingLocationMapPicker.resolveAndEmit()'s exact pattern.
                @Suppress("DEPRECATION")
                suspend fun resolveFallbackGeocode(): LatLng? {
                    if (pickedLatLng != null) return null
                    if (streetAddress.isBlank()) return null
                    return withContext(Dispatchers.IO) {
                        try {
                            val fullAddress = listOf(streetAddress, city, country).filter { it.isNotBlank() }.joinToString(", ")
                            val geocoder = android.location.Geocoder(context, java.util.Locale.ENGLISH)
                            val addresses = geocoder.getFromLocationName(fullAddress, 1)
                            addresses?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                // Silent draft auto-save — debounced 3s after the last field change, and
                // only once there's something worth keeping (title or district non-blank,
                // the same minimal-progress bar Step 1's own "Next" gate already uses).
                // Distinct from the "Save as Draft" button below, whose onClick (at the
                // OwnerHubScreen call site) closes the dialog and shows a toast — reusing
                // that here would kick the host out mid-typing. onAutoSaveDraft defaults to
                // a no-op, so a caller that hasn't wired it up just doesn't get this.
                // drop(1) skips the initial snapshot (reopening an existing Draft shouldn't
                // immediately re-save it before anything actually changed).
                LaunchedEffect(Unit) {
                    snapshotFlow { buildListing(ListingStatus.DRAFT) }
                        .drop(1)
                        .debounce(3000)
                        .distinctUntilChanged()
                        .collect { draft ->
                            if (hasUserTyped && (draft.title.isNotBlank() || draft.streetAddress.isNotBlank())) {
                                onAutoSaveDraft(draft)
                                lastAutoSavedAtMillis = System.currentTimeMillis()
                            }
                        }
                }

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep > 0) {
                        // A compact arrow, not a full-width "Back" button — this row
                        // already shares space with Save-as-Draft/Next/Publish/Save
                        // Changes, all of which are the more important actions; Back
                        // only needs to be reachable, not equally weighted.
                        OutlinedIconButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }

                    // Save as Draft — bypasses the Publish button's requiredness gate
                    // below entirely (no pin/ownership-proof requirement); available at
                    // any step so a host can save partial progress and come back later.
                    // Not part of the wizard's step-by-step flow — an explicit opt-out.
                    // Hidden entirely in admin-edit mode (onListingUpdated != null):
                    // demoting an already-existing ACTIVE/PAUSED listing to Draft is not
                    // something "Edit" should ever do silently.
                    if (onListingUpdated == null) {
                        ProOutlinedButton(
                            text = "Save as Draft",
                            onClick = {
                                coroutineScope.launch {
                                    val fallback = resolveFallbackGeocode()
                                    onSaveDraft(buildListing(ListingStatus.DRAFT, fallback))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ProPrimaryButton(
                        text = if (currentStep < totalSteps - 1) "Next" else if (onListingUpdated != null) "Save Changes" else "Publish Listing",
                        onClick = {
                            if (currentStep < totalSteps - 1) {
                                currentStep++
                            } else if (onListingUpdated != null) {
                                coroutineScope.launch {
                                    // pickedLatLng is already mandatory to reach this step
                                    // (see the Step 1 gate above and the enabled= gate
                                    // below), so resolveFallbackGeocode() is a guaranteed
                                    // no-op here in practice — kept for the same call
                                    // shape as the other two buttons rather than
                                    // special-casing this one out.
                                    val fallback = resolveFallbackGeocode()
                                    onListingUpdated(buildListing(existingDraft?.status ?: ListingStatus.ACTIVE, fallback))
                                }
                            } else {
                                coroutineScope.launch {
                                    val fallback = resolveFallbackGeocode()
                                    onListingCreated(buildListing(ListingStatus.ACTIVE, fallback))
                                }
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        enabled = if (currentStep < totalSteps - 1) {
                            if (currentStep == 0) {
                                // pickedLatLng is required here too now, not just at the
                                // final Publish gate — a host used to be able to fully
                                // configure every later step and pricing detail, then
                                // find Publish permanently disabled with no indication
                                // the missing piece was all the way back on Step 1.
                                (title.isNotBlank() || streetAddress.isNotBlank()) && pickedLatLng != null
                            } else {
                                true
                            }
                        } else {
                            val hasRealPricing = if (hasSubdivisions) {
                                subdivisionsList.isNotEmpty() && subdivisionsList.any { it.pricing.hasRealPrice() }
                            } else {
                                wholeSpacePricing.hasRealPrice()
                            }
                            pickedLatLng != null && uploadedPhotoUrls.isNotEmpty() && hasRealPricing
                        }
                    )
                }
            }
        }



        if (showFacilityDialog) {
            FacilityPickerDialog(
                facilities = masterFacilities,
                selectedFacilities = selectedFacilities,
                onDismiss = { showFacilityDialog = false },
                onSave = { selectedFacilities = it },
                onAddNewFacility = { newFac ->
                    masterFacilities = masterFacilities + newFac
                    onAddCustomSchemaItem("FACILITY", newFac, emptyList())
                }
            )
        }

    }
}

@Composable
private fun FacilityPickerDialog(
    facilities: List<String>,
    selectedFacilities: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onAddNewFacility: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var currentSelected by remember { mutableStateOf(selectedFacilities) }
    var newFacilityInput by remember { mutableStateOf("") }

    val filtered = remember(facilities, query) {
        facilities.filter { it.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f).padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Shared Essential Facilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search facilities...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it }) { facility ->
                        val isChecked = currentSelected.contains(facility)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    currentSelected = if (isChecked) currentSelected - facility else currentSelected + facility
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    currentSelected = if (checked) currentSelected + facility else currentSelected - facility
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(facility, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFacilityInput,
                        onValueChange = { newFacilityInput = it },
                        placeholder = { Text("Add new to master list...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    CustomButton(
                        text = "Add New",
                        onClick = {
                            val trimmed = newFacilityInput.trim()
                            if (trimmed.isNotBlank() && !facilities.contains(trimmed)) {
                                onAddNewFacility(trimmed)
                                currentSelected = currentSelected + trimmed
                                newFacilityInput = ""
                            }
                        }
                    )
                }

                CustomButton(
                    text = "Save & Apply (${currentSelected.size} Selected)",
                    onClick = { onSave(currentSelected); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

