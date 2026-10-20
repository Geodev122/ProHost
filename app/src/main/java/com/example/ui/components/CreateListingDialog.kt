package com.example.ui.components

import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    // Admin attendee packages, offered to hosts as starting templates for a
    // per-attendee room's own tiers.
    attendeeTemplates: List<AttendeePackage> = emptyList(),
    // Write-back: called when the host types a new custom facility or equipment item
    // so it gets persisted to the global schema catalog as a CUSTOM NODE.
    // Receives the category (FACILITY or AMENITY), name, and optional scopedToIds.
    // No-op default so unupdated callers don't crash.
    onAddCustomSchemaItem: (category: String, name: String, scopedToIds: List<String>) -> Unit = { _, _, _ -> }
) {
    LaunchedEffect(Unit) {
        if (existingDraft == null && onListingUpdated == null) {
            com.example.analytics.AnalyticsTracker.listingCreateStart()
        }
    }
    if (currentUser == null) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.sign_in_required), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.your_account_couldn_t_be_loaded_please_sig),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CustomButton(text = stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.align(Alignment.End), variant = CustomButtonVariant.TEXT)
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
    // Internal key only — people see the server-assigned L- display code.
    val listingId = remember { existingDraft?.id ?: ("SPC-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()) }
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
                    photoUploadError = uiText(R.string.one_or_more_images_exceed_the_15_mb_limit)
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
                val detail = FirebaseStorageService.lastUploadError ?: uiText(R.string.check_your_connection_and_try_again)
                photoUploadError = if (failureCount == uris.size) {
                    if (uris.size == 1) uiText(R.string.couldnt_upload_that_photo_x, detail) else uiText(R.string.couldnt_upload_those_photos_x, detail)
                } else {
                    uiText(R.string.x_of_x_photos_failed_to_upload_x, failureCount, uris.size, detail)
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
    var visitorPolicy by rememberSaveable { mutableStateOf(existingDraft?.rules?.visitorPolicy ?: uiText(R.string.clients_visitors_welcomed_in_reception_lou)) }

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
    // Each wizard step is a GA4 screen; leaving before publishing shows up as the last step viewed.
    com.example.ui.components.TrackScreen("listing_wizard_step_${currentStep + 1}", "CreateListingDialog")
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

    // A full-screen page (not a floating card): edge-to-edge window, its own top bar with Close, a three-segment step
    // bar, and system Back stepping back through the wizard before it closes.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        androidx.activity.compose.BackHandler(enabled = currentStep > 0) { currentStep-- }
        // The dialog window draws under the system bars, so it sets their icon colour for its own surface.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (existingDraft != null) stringResource(R.string.continue_draft_listing) else stringResource(R.string.publish_workspace_listing),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        // Only ever true once the auto-save LaunchedEffect below has
                        // actually fired — never claims a save that didn't happen.
                        Text(
                            text = if (lastAutoSavedAtMillis != null) {
                                stringResource(R.string.wizard_step_x_of_x, currentStep + 1, totalSteps) + " · " + stringResource(R.string.draft_auto_saved)
                            } else {
                                stringResource(R.string.wizard_step_x_of_x, currentStep + 1, totalSteps)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                WizardStepBar(
                    steps = listOf(
                        stringResource(R.string.wizard_step_space),
                        stringResource(R.string.wizard_step_details),
                        stringResource(R.string.wizard_step_availability)
                    ),
                    current = currentStep,
                    onStepClick = { target -> if (target < currentStep) currentStep = target }
                )

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
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Text(
                                    stringResource(R.string.space_identification),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                InputField(
                                    value = title,
                                    onValueChange = { title = it; hasUserTyped = true },
                                    label = stringResource(R.string.space_brand_name_e_g_achrafieh_executive_s),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text(stringResource(R.string.space_category), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                                    stringResource(R.string.location_description),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                InputField(
                                    value = description,
                                    onValueChange = { if (it.length <= 100) description = it },
                                    label = stringResource(R.string.description_x_100, description.length),
                                    placeholder = stringResource(R.string.one_or_two_lines_a_specialist_sees_before),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3
                                )

                                Text(stringResource(R.string.pin_the_exact_location), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    stringResource(R.string.drop_or_drag_the_marker_to_the_real_gps_co) +
                                    stringResource(R.string.to_publish_the_address_fields_below_fill_i),
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
                                    label = stringResource(R.string.country),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                InputField(
                                    value = city,
                                    onValueChange = { city = it },
                                    label = stringResource(R.string.city),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                InputField(
                                    value = streetAddress,
                                    onValueChange = { streetAddress = it },
                                    label = stringResource(R.string.street_address_building_name),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text(stringResource(R.string.floor), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    IconButton(onClick = { if (floorNumber > -5) floorNumber-- }) {
                                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_floor))
                                    }
                                    Text(
                                        text = if (floorNumber == 0) stringResource(R.string.ground_floor) else stringResource(R.string.floor_x, floorNumber),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(onClick = { if (floorNumber < 30) floorNumber++ }) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_floor))
                                    }
                                }

                                HorizontalDivider()

                                Text(
                                    stringResource(R.string.target_disciplines),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.hashtag_the_rentee_backgrounds_you_d_prefe),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    InputField(
                                        value = hashtagInput,
                                        onValueChange = { hashtagInput = it },
                                        label = "",
                                        placeholder = stringResource(R.string.discipline),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                                        val tag = hashtagInput.trim().trimStart('#')
                                        if (tag.isNotBlank()) {
                                            selectedSpecialties = selectedSpecialties + tag
                                            hashtagInput = ""
                                        }
                                    }) { Text(stringResource(R.string.add)) }
                                }
                                val matchingSuggestions = suggestedHashtags.filter {
                                    hashtagInput.isNotBlank() && it.contains(hashtagInput.trim().trimStart('#'), ignoreCase = true) &&
                                        !selectedSpecialties.contains(it)
                                }.take(6)
                                if (matchingSuggestions.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                        items(matchingSuggestions) { suggestion ->
                                            AssistChip(
                                                onClick = { selectedSpecialties = selectedSpecialties + suggestion; hashtagInput = "" },
                                                label = { Text("#$suggestion", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                                if (selectedSpecialties.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                        items(selectedSpecialties.toList()) { tag ->
                                            InputChip(
                                                selected = true,
                                                onClick = { selectedSpecialties = selectedSpecialties - tag },
                                                label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(14.dp)) }
                                            )
                                        }
                                    }
                                }



                                HorizontalDivider()

                                Text(stringResource(R.string.cover_photos), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    stringResource(R.string.real_photos_of_the_space_shown_first_in_se) +
                                    stringResource(R.string.draft_never_needs_one),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uploadedPhotoUrls.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                                                    contentDescription = stringResource(R.string.remove_photo),
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
                                                    Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.add_photo))
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
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                                Text(
                                    stringResource(R.string.communication_setup),
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
                                    label = stringResource(R.string.whatsapp_number_for_booking_requests),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                HorizontalDivider()

                                Text(
                                    stringResource(R.string.facility_operating_hours_days),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.controls_the_availability_logic_in_step_3) +
                                    stringResource(R.string.slot_can_ever_be_offered_in),
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

                                Text(stringResource(R.string.shared_essential_facilities), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                val firstFacility = selectedFacilities.firstOrNull()
                                val facilitySummary = if (firstFacility != null) {
                                    if (selectedFacilities.size > 1) {
                                        uiText(R.string.x_x_more_selected, firstFacility, selectedFacilities.size - 1)
                                    } else {
                                        "$firstFacility selected"
                                    }
                                } else {
                                    uiText(R.string.no_facilities_selected)
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
                                                stringResource(R.string.tap_to_open_picker_manage_master_list),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.see_more),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text(
                                    stringResource(R.string.premises_rules_and_policy),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.smoking_allowed), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = smokingAllowed, onCheckedChange = { smokingAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.food_allowed), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = foodAllowed, onCheckedChange = { foodAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.pets_allowed), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = petsAllowed, onCheckedChange = { petsAllowed = it })
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.off_hours_access), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = offHoursAccess, onCheckedChange = { offHoursAccess = it })
                                }
                                InputField(
                                    value = visitorPolicy,
                                    onValueChange = { visitorPolicy = it },
                                    label = stringResource(R.string.visitor_policy),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        2 -> {
                            // Step 3: Availability Control Logic
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                                // Mode selector card
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    shape = MaterialTheme.shapes.large,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                stringResource(R.string.how_do_specialists_rent_this_space),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            if (hasSubdivisions) stringResource(R.string.divisions_specialists_pick_a_specific_room)
                                            else stringResource(R.string.whole_space_specialists_rent_the_entire_sp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                        ) {
                                            listOf(false to uiText(R.string.whole_space), true to uiText(R.string.has_divisions)).forEach { (value, label) ->
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
                                                    stringResource(R.string.space_level_steps_1_2_name_location_facili),
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
                                                    stringResource(R.string.per_subdivision_this_step_room_name_type_a),
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
                                        attendeeTemplates = attendeeTemplates
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.large,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                                Icon(
                                                    Icons.Default.Payments,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(stringResource(R.string.renting_formula), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Builds the SpaceListing from the wizard's current field state — shared
                // by both "Publish Listing" (status ACTIVE) and "Save as Draft" (status
                // DRAFT, no requiredness gating) so the two paths can never disagree on
                // how a listing gets assembled.
                fun buildListing(status: ListingStatus, fallbackLatLng: LatLng? = null): SpaceListing {
                    // The real source of truth for an undivided listing's pricing is
                    // wholeSpacePricing (set via RentalPricingConfigEditor above). `monthly`
                    // is only the rough monthly-equivalent kept for baseMonthlyRateUsd.
                    var monthly = existingDraft?.baseMonthlyRateUsd ?: 500.0
                    if (!hasSubdivisions) {
                        val p = wholeSpacePricing
                        when (p.strategyType) {
                            RentalStrategyType.MONTHLY -> {
                                val m = p.monthly ?: MonthlyConfig()
                                monthly = m.rateUsd
                            }
                            RentalStrategyType.HOURLY -> {
                                val prices = p.hourly?.cellPrices ?: emptyMap()
                                val avgRate = prices.values.average().takeIf { !it.isNaN() } ?: 25.0
                                monthly = avgRate * 8 * 22 // rough monthly-equivalent for the legacy display field only
                            }
                            RentalStrategyType.SHIFT_BASED -> {
                                val activeShift = p.shiftBased?.shifts?.firstOrNull { !it.isUnavailable }
                                val rate = activeShift?.price ?: 60.0
                                monthly = rate * 20
                            }
                            RentalStrategyType.DAY_BASED -> {
                                val prices = p.dayBased?.distribution?.values?.map { it.price }?.filter { it > 0.0 } ?: emptyList()
                                val rate = prices.average().takeIf { !it.isNaN() } ?: 120.0
                                monthly = rate * (p.dayBased?.distribution?.size?.takeIf { it > 0 } ?: 4)
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
                        floorInfo = if (floorNumber == 0) uiText(R.string.ground_floor) else uiText(R.string.floor_x, floorNumber),
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
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                            // Short label: next to Back and Next the longer "Save as Draft" was cut to "Save as D…".
                            text = stringResource(R.string.save_draft_short),
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
                        text = if (currentStep < totalSteps - 1) stringResource(R.string.next) else if (onListingUpdated != null) stringResource(R.string.save_changes) else stringResource(R.string.publish_listing),
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
                                subdivisionsList.isNotEmpty() && subdivisionsList.any {
                                    if (com.example.ui.util.AttendeePricing.isPerAttendee(it)) com.example.ui.util.AttendeePricing.isConfigured(it)
                                    else it.pricing.hasRealPrice()
                                }
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

/**
 * The wizard's progress: one segment per step (filled up to the current one) with its name under it. Finished steps are
 * tappable to go back; later ones are not (each step's gate decides when Next unlocks).
 */
@Composable
private fun WizardStepBar(steps: List<String>, current: Int, onStepClick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        steps.forEachIndexed { index, name ->
            val done = index < current
            val active = index == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .then(if (done) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Tab) { onStepClick(index) } else Modifier)
                    .semantics {
                        selected = active
                        stateDescription = name
                    }
                    .padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (done || active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f).padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.select_shared_essential_facilities), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                }

                InputField(
                    value = query,
                    onValueChange = { query = it },
                    label = "",
                    placeholder = stringResource(R.string.search_facilities),
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
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
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(facility, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InputField(
                        value = newFacilityInput,
                        onValueChange = { newFacilityInput = it },
                        label = "",
                        placeholder = stringResource(R.string.add_new_to_master_list),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    CustomButton(
                        text = stringResource(R.string.add_new),
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
                    text = stringResource(R.string.save_apply_x_selected, currentSelected.size),
                    onClick = { onSave(currentSelected); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

