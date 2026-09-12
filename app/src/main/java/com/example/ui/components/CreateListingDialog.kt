package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingDialog(
    currentUser: AppUser?,
    onDismiss: () -> Unit,
    onListingCreated: (SpaceListing) -> Unit,
    existingDraft: SpaceListing? = null,
    onSaveDraft: (SpaceListing) -> Unit = {}
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
    var uploadedPhotoUrls by remember { mutableStateOf(existingDraft?.imageUrls ?: emptyList()) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    // Proof of ownership / right to rent — required per listing (no admin review, just
    // kept on file; see SpaceListing.ownershipProofUrl's doc comment). Uploaded
    // immediately on pick, same pattern as cover photos above.
    var ownershipProofDoc by remember { mutableStateOf(DocumentPickerState()) }
    var ownershipProofUrl by remember { mutableStateOf(existingDraft?.ownershipProofUrl) }
    var isUploadingOwnershipProof by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isUploadingPhoto = true
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
                }
            }
            isUploadingPhoto = false
        }
    }

    var title by remember { mutableStateOf(existingDraft?.title ?: "") }
    var selectedSpaceType by remember { mutableStateOf(existingDraft?.spaceType ?: SpaceType.PRIVATE_OFFICE) }
    var selectedGovernorate by remember { mutableStateOf(existingDraft?.governorate ?: Governorate.BEIRUT) }
    var district by remember { mutableStateOf(existingDraft?.district ?: "") }
    var streetAddress by remember { mutableStateOf(existingDraft?.streetAddress ?: "") }

    // Real geolocation from the map picker below — required to publish. Distinct from
    // [district]/[streetAddress] above, which the host types freely; a picked address
    // can be applied into those fields with one tap, but never overwrites them silently.
    // Always starts null even when continuing a Draft — a draft's stored lat/lng may
    // just be the unpicked fallback jitter (see the geocoding fallback below), so the
    // host re-confirms the pin on the map rather than Publish silently trusting it.
    var pickedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var pickedAddressLine by remember { mutableStateOf<String?>(null) }
    var pickedDistrict by remember { mutableStateOf<String?>(null) }
    var floorInfo by remember { mutableStateOf(existingDraft?.floorInfo ?: "Floor 3 (Elevator accessible)") }
    var isShared by remember { mutableStateOf(existingDraft?.isShared ?: true) }
    var baseMonthlyRate by remember { mutableStateOf(existingDraft?.baseMonthlyRateUsd?.toInt()?.toString() ?: "500") }
    var ownerPhone by remember { mutableStateOf(existingDraft?.ownerPhone ?: activeUser.phone) }

    // Facilities toggles
    val standardFacilities = FacilityCatalog.standard
    var selectedFacilities by remember {
        mutableStateOf(existingDraft?.essentialFacilities?.toSet() ?: standardFacilities.take(5).toSet())
    }

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

    var chosenEquipment by remember {
        mutableStateOf(
            existingDraft?.equipment?.takeIf { it.isNotEmpty() }
                ?: listOf(defaultEquipCatalog[0], defaultEquipCatalog[3], defaultEquipCatalog[10])
        )
    }
    var equipmentSearchQuery by remember { mutableStateOf("") }

    // Premises rules — real editable fields, replacing the previously-hardcoded
    // PremisesRules() default at listing construction.
    var smokingAllowed by remember { mutableStateOf(existingDraft?.rules?.smokingAllowed ?: false) }
    var foodAllowed by remember { mutableStateOf(existingDraft?.rules?.foodAllowed ?: true) }
    var petsAllowed by remember { mutableStateOf(existingDraft?.rules?.petsAllowed ?: false) }
    var offHoursAccess by remember { mutableStateOf(existingDraft?.rules?.offHoursAccess ?: true) }
    var visitorPolicy by remember { mutableStateOf(existingDraft?.rules?.visitorPolicy ?: "Clients & visitors welcomed in reception lounge") }

    // Complementary specialties
    val commonSpecialties = listOf("Consultant", "Designer", "Architect", "Developer", "Lawyer", "Accountant", "Marketer", "Coach")
    // Was {"Cardiologist","Endocrinologist","Dermatologist"} — leftover defaults
    // from an earlier, medical-specific version of this chip list; none of them
    // appear among commonSpecialties above, so a host would see a chip row with
    // nothing pre-selected that actually matched. Starts empty instead.
    var selectedSpecialties by remember { mutableStateOf(existingDraft?.complementarySpecialties?.toSet() ?: emptySet()) }

    // Subdivision States (Level 2 Rooms & Desks) — the "add a room" form fields
    // themselves now live inside SubdivisionEditorSection (see that file); this
    // dialog only hoists the resulting list, since buildListing() needs it.
    var subdivisionsList by remember { mutableStateOf(existingDraft?.subdivisions ?: listOf<Subdivision>()) }

    var currentStep by remember { mutableIntStateOf(0) }
    val hasSubdivisions = selectedSpaceType != SpaceType.PRIVATE_OFFICE
    val totalSteps = if (hasSubdivisions) 4 else 3

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
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Scrollable Content per step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (currentStep) {
                        0 -> {
                            // Step 0: Basics & Location
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                InputField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = "Space Title (e.g. Achrafieh Executive Suite)",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Text("Space Category & Layout", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(SpaceType.values()) { type ->
                                        FilterChip(
                                            selected = selectedSpaceType == type,
                                            onClick = { selectedSpaceType = type },
                                            label = { Text(type.displayName, fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                                        )
                                    }
                                }

                                Text("Lebanon Governorate", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(Governorate.values()) { gov ->
                                        FilterChip(
                                            selected = selectedGovernorate == gov,
                                            onClick = { selectedGovernorate = gov },
                                            label = { Text(gov.displayName, fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                                        )
                                    }
                                }

                                Text("Pin the Exact Location", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Text(
                                    "Tap the map to record the real GPS coordinates specialists will see when searching nearby — required to publish.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ListingLocationMapPicker(
                                    initialCenter = LatLng(selectedGovernorate.centerLat, selectedGovernorate.centerLng),
                                    pickedLatLng = pickedLatLng,
                                    onLocationPicked = { picked ->
                                        pickedLatLng = LatLng(picked.lat, picked.lng)
                                        pickedAddressLine = picked.addressLine
                                        pickedDistrict = picked.district
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if ((pickedAddressLine != null || pickedDistrict != null) &&
                                    (streetAddress.isBlank() || district.isBlank())
                                ) {
                                    TextButton(onClick = {
                                        if (streetAddress.isBlank()) pickedAddressLine?.let { streetAddress = it }
                                        if (district.isBlank()) pickedDistrict?.let { district = it }
                                    }) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Apply detected address to fields below", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                                    }
                                }

                                InputField(
                                    value = district,
                                    onValueChange = { district = it },
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

                                InputField(
                                    value = floorInfo,
                                    onValueChange = { floorInfo = it },
                                    label = "Floor & Accessibility",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text("Co-Sharing Practice", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                        Text("Shared with complementary doctors", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                                }

                                Text("Cover Photos", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Text(
                                    "Real photos of the space — shown first in search results. Optional, but listings without any get a plain placeholder.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            }
                        }

                        1 -> {
                            // Step 1: Facilities & Equipment Catalog
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Essential Facilities & Utilities", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                standardFacilities.forEach { facility ->
                                    val isChecked = selectedFacilities.contains(facility)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                selectedFacilities = if (isChecked) selectedFacilities - facility else selectedFacilities + facility
                                            }
                                            .padding(vertical = Spacing.xs)
                                    ) {
                                        Checkbox(checked = isChecked, onCheckedChange = {
                                            selectedFacilities = if (it) selectedFacilities + facility else selectedFacilities - facility
                                        })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(facility, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                    }
                                }

                                HorizontalDivider()

                                Text("Professional Equipment Catalog", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Text("Select equipment available on premises", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                OutlinedTextField(
                                    value = equipmentSearchQuery,
                                    onValueChange = { equipmentSearchQuery = it },
                                    placeholder = { Text("Search equipment...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    singleLine = true
                                )

                                val filteredEquip = defaultEquipCatalog.filter {
                                    equipmentSearchQuery.isBlank() || it.name.contains(equipmentSearchQuery, ignoreCase = true)
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredEquip.forEach { item ->
                                        val isSelected = chosenEquipment.any { it.name == item.name }
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    chosenEquipment = if (isSelected) {
                                                        chosenEquipment.filterNot { it.name == item.name }
                                                    } else {
                                                        chosenEquipment + item
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.name, fontSize = MaterialTheme.typography.bodySmall.fontSize, fontWeight = FontWeight.SemiBold)
                                                    Text(item.category.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Icon(
                                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            if (hasSubdivisions) {
                                SubdivisionEditorSection(
                                    subdivisionsList = subdivisionsList,
                                    onSubdivisionsChange = { subdivisionsList = it }
                                )
                            } else {
                                // Base pricing only — the full formula builder (Hourly,
                                // Shift, Day-per-Week, Full-Month, each with real
                                // per-formula customization) plus Operating Hours and
                                // Blackout slots now live in the same "Availability &
                                // Formula Control" editor used to manage an existing
                                // listing (SpaceScheduleEditorDialog), opened
                                // automatically right after this listing is published.
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Base Pricing", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Enter your base monthly valuation — this publishes with a Full-Month formula active immediately. You'll set operating hours, blackout slots, and any additional Hourly/Shift/Day-per-Week formulas right after publishing.",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    InputField(
                                        value = baseMonthlyRate,
                                        onValueChange = { baseMonthlyRate = it },
                                        label = "Base Monthly Valuation (USD/mo)",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        3 -> {
                            // Step 3: Contact, Premises Rules & Ownership Proof
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Contact & Premises Rules", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.primary)
                                Text("Host contact details and the premises rules specialists will see before booking.", fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Text("Complementary Specialist Disciplines", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(commonSpecialties) { spec ->
                                        val isSel = selectedSpecialties.contains(spec)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                selectedSpecialties = if (isSel) selectedSpecialties - spec else selectedSpecialties + spec
                                            },
                                            label = { Text(spec, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                        )
                                    }
                                }

                                InputField(
                                    value = ownerPhone,
                                    onValueChange = { ownerPhone = it },
                                    label = "Space Owner WhatsApp Phone (+961 ...)",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                HorizontalDivider()

                                Text("Premises Rules", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)

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

                                HorizontalDivider()

                                Text("Proof of Ownership / Right to Rent", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                Text(
                                    "Required to publish — a title deed, lease contract, or other document showing you're entitled to rent this specific space out. Kept on file, no review needed.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                DocumentPickerField(
                                    label = "Ownership / Right-to-Rent Document",
                                    helperText = "PDF, JPG, or PNG",
                                    state = ownershipProofDoc,
                                    onStateChanged = { newState ->
                                        ownershipProofDoc = newState
                                        val uri = newState.uri
                                        if (uri != null) {
                                            coroutineScope.launch {
                                                isUploadingOwnershipProof = true
                                                val ext = newState.fileName?.substringAfterLast('.', "pdf") ?: "pdf"
                                                ownershipProofUrl = storageService.uploadOwnershipProofDocument(listingId, uri, ext)
                                                isUploadingOwnershipProof = false
                                            }
                                        } else {
                                            ownershipProofUrl = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    required = true
                                )
                                if (isUploadingOwnershipProof) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                fun buildListing(status: ListingStatus): SpaceListing {
                    val formulas = mutableListOf<RentalFormula>()
                    val monthly = if (hasSubdivisions) {
                        subdivisionsList.flatMap { it.rentalStrategies }
                            .filter { it.strategy == RentalStrategy.MONTHLY }
                            .map { it.rateUsd }
                            .minOrNull() ?: 450.0
                    } else {
                        baseMonthlyRate.toDoubleOrNull() ?: 500.0
                    }

                    // Always publish with a Full-Month formula so the listing
                    // is never unbookable — Hourly/Shift/Day-per-Week formulas,
                    // operating hours, and blackout slots are set right after
                    // publishing in the same "Availability & Formula Control"
                    // editor used for existing listings (see onListingCreated).
                    if (!hasSubdivisions) {
                        formulas.add(
                            RentalFormula(
                                type = RentalFormulaType.FULL_MONTH,
                                rateUsd = monthly,
                                scheduleDescription = "Dedicated Full Workspace Month (All operating days)",
                                daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                                startHour = "08:00",
                                endHour = "20:00",
                                totalWeeklyHours = 72
                            )
                        )
                    }
                    // Derived from the same formula eagerly, so this listing's real
                    // pricing is correct from the moment it's first saved. When
                    // hasSubdivisions is true, the listing itself carries no pricing
                    // of its own — each Subdivision already got its own pricing set
                    // eagerly in SubdivisionEditorSection's Add button.
                    val pricingConfig = if (!hasSubdivisions) {
                        RentalPricingConfig.fromLegacyFormula(formulas.firstOrNull())
                    } else {
                        RentalPricingConfig.default()
                    }

                    // Prefer the real pin dropped on the map (recorded via
                    // ListingLocationMapPicker above); only fall back to
                    // string-geocoding the typed address if the host somehow
                    // reached submit without one (shouldn't happen for a real
                    // Publish — gated by the Publish button below; expected for
                    // a Draft saved before the host ever opened the map step).
                    var geocodedLat = selectedGovernorate.centerLat + ((-20..20).random() / 1000.0)
                    var geocodedLng = selectedGovernorate.centerLng + ((-20..20).random() / 1000.0)
                    val pinned = pickedLatLng
                    if (pinned != null) {
                        geocodedLat = pinned.latitude
                        geocodedLng = pinned.longitude
                    } else if (streetAddress.isNotBlank() || district.isNotBlank()) {
                        try {
                            val fullAddress = "${streetAddress}, ${district}, ${selectedGovernorate.displayName}, Lebanon"
                            val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                            val addresses = geocoder.getFromLocationName(fullAddress, 1)
                            if (!addresses.isNullOrEmpty()) {
                                geocodedLat = addresses[0].latitude
                                geocodedLng = addresses[0].longitude
                            }
                        } catch (e: Exception) {
                            // fallback to the jittered governorate center above
                        }
                    }

                    return SpaceListing(
                        id = listingId,
                        title = if (title.isNotBlank()) title else "${selectedGovernorate.displayName} ${selectedSpaceType.displayName}",
                        spaceType = selectedSpaceType,
                        governorate = selectedGovernorate,
                        district = if (district.isNotBlank()) district else "Central ${selectedGovernorate.displayName}",
                        streetAddress = if (streetAddress.isNotBlank()) streetAddress else "Main Business Street",
                        floorInfo = floorInfo,
                        lat = geocodedLat,
                        lng = geocodedLng,
                        isShared = isShared,
                        complementarySpecialties = selectedSpecialties.toList(),
                        residentPractitioners = listOf("${activeUser.fullName} (${activeUser.specialty})"),
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
                        ownerId = activeUser.id,
                        ownerName = activeUser.fullName,
                        ownerPhone = ownerPhone,
                        ownerEmail = activeUser.email,
                        ownershipProofUrl = ownershipProofUrl,
                        // Genuinely earned now (see SpaceListing.isVerified's doc
                        // comment) — a new listing starts unverified; the host can
                        // optionally earn the badge afterward from the listing card
                        // ("Get Listing Verified").
                        isVerified = false,
                        isActiveSubscription = true,
                        baseMonthlyRateUsd = monthly,
                        subdivisions = subdivisionsList,
                        imageUrls = uploadedPhotoUrls,
                        ownerIsIdVerified = activeUser.idDocumentUrl != null,
                        status = status
                    )
                }

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep > 0) {
                        ProOutlinedButton(
                            text = "Back",
                            onClick = { currentStep-- },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Save as Draft — bypasses the Publish button's requiredness gate
                    // below entirely (no pin/ownership-proof requirement); available at
                    // any step so a host can save partial progress and come back later.
                    // Not part of the wizard's step-by-step flow — an explicit opt-out.
                    ProOutlinedButton(
                        text = "Save as Draft",
                        onClick = { onSaveDraft(buildListing(ListingStatus.DRAFT)) },
                        modifier = Modifier.weight(1f)
                    )

                    ProPrimaryButton(
                        text = if (currentStep < totalSteps - 1) "Next" else "Publish Listing",
                        onClick = {
                            if (currentStep < totalSteps - 1) {
                                currentStep++
                            } else {
                                onListingCreated(buildListing(ListingStatus.ACTIVE))
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        enabled = if (currentStep < totalSteps - 1) {
                            currentStep != 0 || title.isNotBlank() || district.isNotBlank()
                        } else {
                            pickedLatLng != null && ownershipProofUrl != null && !isUploadingOwnershipProof
                        }
                    )
                }
            }
        }
    }
}
