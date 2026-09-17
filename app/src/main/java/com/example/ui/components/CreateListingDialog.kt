package com.example.ui.components

import android.net.Uri
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
    spaceCategories: List<SchemaItem> = emptyList()
) {
    if (currentUser == null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign In Required", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                    Text(
                        "Your account couldn't be loaded. Please sign in again before creating a listing.",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
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

    // Proof of ownership / right to rent — required per listing (no admin review, just
    // kept on file; see SpaceListing.ownershipProofUrl's doc comment). Uploaded
    // immediately on pick, same pattern as cover photos above.
    var ownershipProofDoc by rememberSaveable(stateSaver = DocumentPickerStateSaver) { mutableStateOf(DocumentPickerState()) }
    var ownershipProofUrl by rememberSaveable { mutableStateOf(existingDraft?.ownershipProofUrl) }
    var isUploadingOwnershipProof by rememberSaveable { mutableStateOf(false) }
    // Same silent-failure gap as photoUploadError above — and more consequential here,
    // since ownershipProofUrl staying null is exactly what keeps the Next button on
    // Step 1 permanently disabled with no visible explanation (see enabled= below).
    var ownershipUploadError by remember { mutableStateOf<String?>(null) }
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
                val url = storageService.uploadListingImage(
                    spaceId = listingId,
                    imageId = imageId,
                    fileUri = uri,
                    fileExtension = "jpg"
                )
                if (url != null) {
                    uploadedPhotoUrls = uploadedPhotoUrls + url
                } else {
                    failureCount++
                }
            }
            if (failureCount > 0) {
                photoUploadError = if (failureCount == uris.size) {
                    "Couldn't upload ${if (uris.size == 1) "that photo" else "those photos"}. Check your connection and try again."
                } else {
                    "$failureCount of ${uris.size} photos failed to upload. Check your connection and try again."
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
    var country by rememberSaveable { mutableStateOf("Lebanon") }
    var city by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf(existingDraft?.district ?: "") }
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
    var operatingDays by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(existingDraft?.schedule?.operatingDays?.toSet() ?: setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) }
    var openingHour by rememberSaveable { mutableStateOf(existingDraft?.schedule?.openingHour ?: "08:00") }
    var closingHour by rememberSaveable { mutableStateOf(existingDraft?.schedule?.closingHour ?: "20:00") }

    // Ownership / right-to-rent — moved from the wizard's last step to its first
    // (spec 1.5): mandatory to advance past Step 1, not just to Publish, and gated
    // behind an owner-vs-re-renter choice. Deliberately a DIFFERENT field
    // (ownershipProofUrl) and a DIFFERENT, new dialog flow from the optional,
    // post-publish "Get Listing Verified" badge system (verificationDocUrl /
    // ListingVerificationDialog) — the two must never be conflated.
    var ownershipRole by rememberSaveable(stateSaver = OwnershipRoleSaver) { mutableStateOf(existingDraft?.ownershipDocRole) }
    // Dialog-visibility booleans stay plain remember — losing them on process death
    // just closes a dialog, harmless. hasAcknowledgedAuditDisclaimer is converted since
    // it's a real progress flag, not a transient dialog state.
    var showOwnershipRolePrompt by remember { mutableStateOf(existingDraft?.ownershipProofUrl == null) }
    var showRerentalTemplateDialog by remember { mutableStateOf(false) }

    // Facilities toggles
    var masterFacilities by remember { mutableStateOf(availableFacilities) }
    var selectedFacilities by rememberSaveable(stateSaver = StringSetSaver) {
        mutableStateOf(existingDraft?.essentialFacilities?.toSet() ?: masterFacilities.take(3).toSet())
    }
    var showFacilityDialog by remember { mutableStateOf(false) }

    // Equipment builder
    val defaultEquipCatalog = listOf(
        EquipmentItem("EQ-T1", "Motorized Standing Desk & Ergonomic Chair", EquipmentCategory.WORKSPACES, 1),
        EquipmentItem("EQ-T2", "Executive Conference Table (Seats 8)", EquipmentCategory.WORKSPACES, 1),
        EquipmentItem("EQ-T3", "Client Reception Lounge Sofa Set", EquipmentCategory.WORKSPACES, 1),
        EquipmentItem("EQ-T4", "4K Ultra-HD Presentation Screen", EquipmentCategory.IT_TECH, 1),
        EquipmentItem("EQ-T5", "High-Speed Laser Multi-Function Printer", EquipmentCategory.IT_TECH, 1),
        EquipmentItem("EQ-T6", "Video Conferencing Camera & Mic Pod", EquipmentCategory.IT_TECH, 1),
        EquipmentItem("EQ-T7", "Lockable Document Storage & Safe", EquipmentCategory.WORKSPACES, 1),
        EquipmentItem("EQ-T8", "Studio Softbox Lighting Kit", EquipmentCategory.SPECIALIZED, 2),
        EquipmentItem("EQ-T9", "Soundproof Acoustic Isolation Booth", EquipmentCategory.SPECIALIZED, 1),
        EquipmentItem("EQ-T10", "Workstation PC Dual-Monitor Setup", EquipmentCategory.IT_TECH, 1),
        EquipmentItem("EQ-T11", "Espresso Bar & Beverage Refrigerator", EquipmentCategory.OFFICE_AMENITIES, 1),
        EquipmentItem("EQ-T12", "Magnetic Glass Presentation Whiteboard", EquipmentCategory.OFFICE_AMENITIES, 2)
    )
    var masterEquipmentCatalog by remember { mutableStateOf(defaultEquipCatalog) }
    var showEquipmentDialog by remember { mutableStateOf(false) }

    var chosenEquipment by rememberSaveable(stateSaver = EquipmentListSaver) {
        mutableStateOf(
            existingDraft?.equipment?.takeIf { it.isNotEmpty() }
                ?: listOf(masterEquipmentCatalog[0], masterEquipmentCatalog[3], masterEquipmentCatalog[10])
        )
    }
    var equipmentSearchQuery by rememberSaveable { mutableStateOf("") }
    // Custom equipment entry — the fixed catalog above (defaultEquipCatalog) is a
    // representative starting list, not exhaustive; a host whose space has
    // something not on it (a piece of clinical gear, a specific tool) can add it
    // by name instead of being stuck picking the closest fixed match.
    var showAddCustomEquipment by remember { mutableStateOf(false) }
    var customEquipmentName by rememberSaveable { mutableStateOf("") }
    var customEquipmentCategory by rememberSaveable(stateSaver = EquipmentCategorySaver) { mutableStateOf(EquipmentCategory.WORKSPACES) }

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
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
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps • Lebanon Network",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Only ever true once the auto-save LaunchedEffect below has
                        // actually fired — never claims a save that didn't happen.
                        if (lastAutoSavedAtMillis != null) {
                            Text(
                                text = "Draft auto-saved",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                                Text("Space Identification", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                InputField(
                                    value = title,
                                    onValueChange = { title = it; hasUserTyped = true },
                                    label = "Space Brand Name (e.g. Achrafieh Executive Suite)",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text("Space Category", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
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
                                                Text(category.name, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text("Location & Description", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)

                                InputField(
                                    value = description,
                                    onValueChange = { if (it.length <= 100) description = it },
                                    label = "Description (${description.length}/100)",
                                    placeholder = "One or two lines a specialist sees before opening the listing",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3
                                )

                                Text("Pin the Exact Location", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Text(
                                    "Drop or drag the marker to the real GPS coordinates specialists will see when searching nearby — required to publish. The address fields below fill in automatically; edit them freely afterward.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ListingLocationMapPicker(
                                    initialLat = pickedLatLng?.latitude ?: existingDraft?.lat,
                                    initialLng = pickedLatLng?.longitude ?: existingDraft?.lng,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    onLocationConfirmed = { lat, lng, address, gov ->
                                        pickedLatLng = LatLng(lat, lng)
                                        streetAddress = address
                                        derivedGovernorate = gov
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
                                    value = if (city.isNotBlank()) city else derivedGovernorate.displayName,
                                    onValueChange = { city = it },
                                    label = "City / Governorate",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                InputField(
                                    value = district,
                                    onValueChange = { district = it; hasUserTyped = true },
                                    label = "District / Neighborhood (e.g., Hamra / Sassine)",
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

                                Text("Floor", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconButton(onClick = { if (floorNumber > -5) floorNumber-- }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease floor")
                                    }
                                    Text(
                                        text = if (floorNumber == 0) "Ground Floor" else "Floor $floorNumber",
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(onClick = { if (floorNumber < 30) floorNumber++ }) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase floor")
                                    }
                                }

                                HorizontalDivider()

                                Text("Target Disciplines", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Hashtag the rentee backgrounds you'd prefer (e.g. #Cardiologist, #Architect).",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                                                label = { Text("#$suggestion", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
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
                                                label = { Text("#$tag", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Text("Proof of Ownership / Right to Rent", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Required before continuing — a title deed, lease contract, or signed re-rental authorization showing you're entitled to rent this specific space out. Kept on file, no review needed.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ownershipRole == OwnershipRole.RERENTER) {
                                    OutlinedButton(onClick = { showRerentalTemplateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Download Authorization Template")
                                    }
                                }
                                if (ownershipRole != null || ownershipProofUrl != null) {
                                    DocumentPickerField(
                                        label = if (ownershipRole == OwnershipRole.RERENTER) "Signed Re-Rental Authorization" else "Ownership / Right-to-Rent Document",
                                        helperText = "PDF, JPG, or PNG",
                                        state = ownershipProofDoc,
                                        onStateChanged = { newState ->
                                            ownershipProofDoc = newState
                                            val uri = newState.uri
                                            if (uri != null) {
                                                coroutineScope.launch {
                                                    isUploadingOwnershipProof = true
                                                    ownershipUploadError = null
                                                    val ext = newState.fileName?.substringAfterLast('.', "pdf") ?: "pdf"
                                                    val url = storageService.uploadOwnershipProofDocument(listingId, uri, ext)
                                                    ownershipProofUrl = url
                                                    if (url == null) {
                                                        ownershipUploadError = "Couldn't upload that document. Check your connection and try again."
                                                    }
                                                    isUploadingOwnershipProof = false
                                                }
                                            } else {
                                                ownershipProofUrl = null
                                                ownershipUploadError = null
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        required = true
                                    )
                                    if (isUploadingOwnershipProof) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    if (ownershipUploadError != null) {
                                        Text(
                                            ownershipUploadError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                                        )
                                    }
                                } else {
                                    OutlinedButton(onClick = { showOwnershipRolePrompt = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Confirm ownership status to continue")
                                    }
                                }

                                HorizontalDivider()

                                Text("Cover Photos", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Text(
                                    "Real photos of the space — shown first in search results. At least one is required to publish (Save as Draft never needs one).",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Step 2: Operational Parameters & Facility Rules
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Communication Setup", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
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

                                Text("Facility Operating Hours & Days", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Controls the availability logic in Step 3 — the days and hours you select here are the only ones a rentable slot can ever be offered in.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
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

                                Text("Shared Essential Facilities", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                val firstFacility = selectedFacilities.firstOrNull()
                                val facilitySummary = if (firstFacility != null) {
                                    if (selectedFacilities.size > 1) "$firstFacility (+${selectedFacilities.size - 1} more selected)" else "$firstFacility selected"
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
                                            Text("Tap to open picker & manage master list", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("See More ➔", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                HorizontalDivider()

                                Text("Professional Equipment Catalog", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                val firstEquip = chosenEquipment.firstOrNull()?.name
                                val equipSummary = if (firstEquip != null) {
                                    if (chosenEquipment.size > 1) "$firstEquip (+${chosenEquipment.size - 1} more selected)" else "$firstEquip selected"
                                } else {
                                    "No equipment selected"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    onClick = { showEquipmentDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(equipSummary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text("Tap to open equipment catalog & manage master list", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("See More ➔", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                if (showAddCustomEquipment) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customEquipmentName,
                                            onValueChange = { customEquipmentName = it },
                                            label = { Text("Equipment name") },
                                            placeholder = { Text("e.g. Portable Ultrasound Unit") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            EquipmentCategory.values().forEach { cat ->
                                                FilterChip(
                                                    selected = customEquipmentCategory == cat,
                                                    onClick = { customEquipmentCategory = cat },
                                                    label = { Text(cat.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ProOutlinedButton(
                                                text = "Cancel",
                                                onClick = { showAddCustomEquipment = false; customEquipmentName = "" },
                                                modifier = Modifier.weight(1f)
                                            )
                                            ProPrimaryButton(
                                                text = "Add",
                                                onClick = {
                                                    val trimmed = customEquipmentName.trim()
                                                    if (trimmed.isNotEmpty() && chosenEquipment.none { it.name.equals(trimmed, ignoreCase = true) }) {
                                                        chosenEquipment = chosenEquipment + EquipmentItem(
                                                            id = "EQ-CUSTOM-" + UUID.randomUUID().toString().take(6).uppercase(),
                                                            name = trimmed,
                                                            category = customEquipmentCategory
                                                        )
                                                    }
                                                    customEquipmentName = ""
                                                    showAddCustomEquipment = false
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = customEquipmentName.isNotBlank()
                                            )
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { showAddCustomEquipment = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add custom equipment not listed above")
                                    }
                                }

                                HorizontalDivider()

                                Text("Premises Rules and Policy", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Smoking Allowed", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                    Switch(checked = smokingAllowed, onCheckedChange = { smokingAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Food Allowed", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                    Switch(checked = foodAllowed, onCheckedChange = { foodAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pets Allowed", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                    Switch(checked = petsAllowed, onCheckedChange = { petsAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Off-Hours Access", fontSize = MaterialTheme.typography.bodySmall.fontSize)
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
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Whole Space or Divisions?", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(false to "Whole Space", true to "Has Divisions").forEach { (value, label) ->
                                        FilterChip(
                                            selected = hasSubdivisions == value,
                                            onClick = { hasSubdivisions = value },
                                            label = { Text(label) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                if (hasSubdivisions) {
                                    SubdivisionEditorSection(
                                        spaceId = listingId,
                                        subdivisionsList = subdivisionsList,
                                        onSubdivisionsChange = { subdivisionsList = it },
                                        operatingDays = operatingDays.toList(),
                                        openingHour = openingHour,
                                        closingHour = closingHour
                                    )
                                } else {
                                    Text("Renting Formula", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
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
                        title = if (title.isNotBlank()) title else "${derivedGovernorate.displayName} ${selectedCategoryName ?: selectedSpaceType.displayName}",
                        description = description,
                        spaceType = selectedSpaceType,
                        spaceCategoryId = selectedCategoryId,
                        spaceCategoryName = selectedCategoryName,
                        governorate = derivedGovernorate,
                        district = if (district.isNotBlank()) district else "Central ${derivedGovernorate.displayName}",
                        streetAddress = if (streetAddress.isNotBlank()) streetAddress else "Main Business Street",
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
                        equipment = chosenEquipment,
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
                        ownerIsIdVerified = activeUser.idDocumentUrl != null,
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
                    if (streetAddress.isBlank() && district.isBlank()) return null
                    return withContext(Dispatchers.IO) {
                        try {
                            val fullAddress = "${streetAddress}, ${district}, ${derivedGovernorate.displayName}, Lebanon"
                            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
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
                            if (hasUserTyped && (draft.title.isNotBlank() || draft.district.isNotBlank())) {
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
                                (title.isNotBlank() || district.isNotBlank()) &&
                                    !isUploadingOwnershipProof && pickedLatLng != null
                            } else {
                                true
                            }
                        } else {
                            // hasRealPrice() mirrors publishValidation.ts's own check
                            // exactly — Publish used to only verify the pin/ownership-
                            // doc/photo were present, never that any actual price was
                            // configured, so a listing could flip live with a
                            // "published successfully" toast and then get silently
                            // demoted back to Draft moments later by the server.
                            val hasRealPricing = if (hasSubdivisions) {
                                subdivisionsList.isNotEmpty() && subdivisionsList.any { it.pricing.hasRealPrice() }
                            } else {
                                wholeSpacePricing.hasRealPrice()
                            }
                            pickedLatLng != null && !isUploadingOwnershipProof &&
                                uploadedPhotoUrls.isNotEmpty() && hasRealPricing
                        }
                    )
                }
            }
        }

        if (showOwnershipRolePrompt) {
            AlertDialog(
                onDismissRequest = { /* Not dismissible without a choice — the gate is mandatory. */ },
                title = { Text("Are you the owner or a re-renter?") },
                text = {
                    Text(
                        "This determines which document proves your right to list this space. " +
                            "Owner: upload your own title deed or lease. Re-renter: download the " +
                            "authorization template, have the property owner sign it, then upload the signed copy.",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ownershipRole = OwnershipRole.OWNER
                        showOwnershipRolePrompt = false
                    }) { Text("I'm the Owner") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        ownershipRole = OwnershipRole.RERENTER
                        showOwnershipRolePrompt = false
                    }) { Text("I'm Re-Renting") }
                }
            )
        }

        if (showRerentalTemplateDialog) {
            LegalDocumentDialog(
                document = com.example.legal.LegalContent.rerentalAuthorizationTemplate,
                onDismiss = { showRerentalTemplateDialog = false }
            )
        }

        if (showFacilityDialog) {
            FacilityPickerDialog(
                facilities = masterFacilities,
                selectedFacilities = selectedFacilities,
                onDismiss = { showFacilityDialog = false },
                onSave = { selectedFacilities = it },
                onAddNewFacility = { newFac ->
                    masterFacilities = masterFacilities + newFac
                }
            )
        }

        if (showEquipmentDialog) {
            EquipmentPickerDialog(
                catalog = masterEquipmentCatalog,
                chosenEquipment = chosenEquipment,
                onDismiss = { showEquipmentDialog = false },
                onSave = { chosenEquipment = it },
                onAddNewEquipment = { newItem ->
                    masterEquipmentCatalog = masterEquipmentCatalog + newItem
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f).padding(16.dp)
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
                    Button(
                        onClick = {
                            val trimmed = newFacilityInput.trim()
                            if (trimmed.isNotBlank() && !facilities.contains(trimmed)) {
                                onAddNewFacility(trimmed)
                                currentSelected = currentSelected + trimmed
                                newFacilityInput = ""
                            }
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Add New")
                    }
                }

                Button(
                    onClick = { onSave(currentSelected); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Save & Apply (${currentSelected.size} Selected)")
                }
            }
        }
    }
}

@Composable
private fun EquipmentPickerDialog(
    catalog: List<EquipmentItem>,
    chosenEquipment: List<EquipmentItem>,
    onDismiss: () -> Unit,
    onSave: (List<EquipmentItem>) -> Unit,
    onAddNewEquipment: (EquipmentItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var currentChosen by remember { mutableStateOf(chosenEquipment) }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(EquipmentCategory.WORKSPACES) }

    val filtered = remember(catalog, query) {
        catalog.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp)
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
                    Text("Select Professional Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search equipment catalog...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isSelected = currentChosen.any { it.name == item.name }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    currentChosen = if (isSelected) currentChosen.filterNot { it.name == item.name } else currentChosen + item
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    currentChosen = if (checked) currentChosen + item else currentChosen.filterNot { it.name == item.name }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(item.category.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Add New Equipment to Master List", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Equipment name...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(EquipmentCategory.entries) { cat ->
                        FilterChip(
                            selected = newCategory == cat,
                            onClick = { newCategory = cat },
                            label = { Text(cat.displayName, fontSize = 10.sp) }
                        )
                    }
                }
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank() && currentChosen.none { it.name.equals(trimmed, ignoreCase = true) }) {
                            val newItem = EquipmentItem(
                                id = "EQ-CUS-" + System.currentTimeMillis().toString().takeLast(6),
                                name = trimmed,
                                category = newCategory,
                                quantity = 1
                            )
                            onAddNewEquipment(newItem)
                            currentChosen = currentChosen + newItem
                            newName = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = newName.isNotBlank()
                ) {
                    Text("Add New to Master List")
                }

                Button(
                    onClick = { onSave(currentChosen); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Save & Apply (${currentChosen.size} Selected)")
                }
            }
        }
    }
}
