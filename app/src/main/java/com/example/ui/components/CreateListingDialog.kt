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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingDialog(
    currentUser: AppUser?,
    onDismiss: () -> Unit,
    onListingCreated: (SpaceListing) -> Unit
) {
    val activeUser = currentUser ?: AppUser(
        id = "USR-ADMIN-ROOT",
        email = "geo.elnajjar@gmail.com",
        fullName = "Geo El-Najjar",
        role = UserRole.PRO_HOST,
        specialty = "Commercial Workspace Host",
        phone = "+961 70 888 999",
        country = "Lebanon",
        governorate = "Beirut",
        city = "Beirut",
        isVerified = true
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storageService = remember { FirebaseStorageService.getInstance() }

    // Generated up front (not just at submit time) so photos can upload to their final
    // listings/{listingId}/ path as soon as they're picked, instead of at the end.
    val listingId = remember { "SPC-LB-" + UUID.randomUUID().toString().take(6).uppercase() }
    var uploadedPhotoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    // Proof of ownership / right to rent — required per listing (no admin review, just
    // kept on file; see SpaceListing.ownershipProofUrl's doc comment). Uploaded
    // immediately on pick, same pattern as cover photos above.
    var ownershipProofDoc by remember { mutableStateOf(DocumentPickerState()) }
    var ownershipProofUrl by remember { mutableStateOf<String?>(null) }
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

    var title by remember { mutableStateOf("") }
    var selectedSpaceType by remember { mutableStateOf(SpaceType.PRIVATE_OFFICE) }
    var selectedGovernorate by remember { mutableStateOf(Governorate.BEIRUT) }
    var district by remember { mutableStateOf("") }
    var streetAddress by remember { mutableStateOf("") }
    var floorInfo by remember { mutableStateOf("Floor 3 (Elevator accessible)") }
    var isShared by remember { mutableStateOf(true) }
    var baseMonthlyRate by remember { mutableStateOf("500") }
    var ownerPhone by remember { mutableStateOf(activeUser.phone) }

    // Facilities toggles
    val standardFacilities = listOf(
        "24/7 Generator Electricity",
        "Continuous Water Supply",
        "High-Speed Fiber Wi-Fi",
        "HVAC Climate Control",
        "Daily Professional Cleaning",
        "Dedicated Underground Parking",
        "Client Accessibility / Elevator",
        "Reception & Admin Support"
    )
    var selectedFacilities by remember { mutableStateOf(standardFacilities.take(5).toSet()) }

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
        mutableStateOf(listOf(defaultEquipCatalog[0], defaultEquipCatalog[3], defaultEquipCatalog[10]))
    }
    var equipmentSearchQuery by remember { mutableStateOf("") }

    // Rental formulas
    var shiftFormulaEnabled by remember { mutableStateOf(true) }
    var shiftRate by remember { mutableStateOf("120") }
    var dayPerWeekEnabled by remember { mutableStateOf(true) }
    var dayPerWeekRate by remember { mutableStateOf("250") }

    // Complementary specialties
    val commonSpecialties = listOf("Consultant", "Designer", "Architect", "Developer", "Lawyer", "Accountant", "Marketer", "Coach")
    var selectedSpecialties by remember { mutableStateOf(setOf("Cardiologist", "Endocrinologist", "Dermatologist")) }

    // Subdivision States (Level 2 Rooms & Desks)
    var subdivisionsList by remember { mutableStateOf(listOf<Subdivision>()) }

    // local states for adding/building subdivisions
    var subName by remember { mutableStateOf("") }
    var subType by remember { mutableStateOf(Level2Type.ROOMS) }
    var subAmenitiesSelected by remember { mutableStateOf(setOf<String>()) }
    
    // Renting strategies for subdivisions
    var subHourlyRate by remember { mutableStateOf("15") }
    var subHourlyEnabled by remember { mutableStateOf(false) }
    
    var subShiftRate by remember { mutableStateOf("60") }
    var subShiftHours by remember { mutableStateOf("Morning Shift (8AM - 1PM)") }
    var subShiftEnabled by remember { mutableStateOf(false) }
    
    var subDailyRate by remember { mutableStateOf("120") }
    var subDailyEnabled by remember { mutableStateOf(false) }
    
    var subMonthlyRate by remember { mutableStateOf("450") }
    var subMonthlyEnabled by remember { mutableStateOf(false) }

    val subAmenitiesPreset = listOf(
        "A/C Climate Control",
        "Dual-Monitor Workstation",
        "Whiteboard / Presentation kit",
        "High-Speed LAN/Wi-Fi",
        "Professional Soundproofing",
        "Patient Consultation Recliner",
        "Medical Sterilization Tray",
        "Keyless Lock / Access Control",
        "Privacy Curtains / Drapes"
    )

    var currentStep by remember { mutableIntStateOf(0) } 
    val hasSubdivisions = selectedSpaceType != SpaceType.PRIVATE_OFFICE
    val totalSteps = if (hasSubdivisions) 4 else 3

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
            shape = RoundedCornerShape(20.dp),
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
                            text = "Publish Workspace Listing",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps • Lebanon Network",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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

                                Text("Space Category & Layout", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(SpaceType.values()) { type ->
                                        FilterChip(
                                            selected = selectedSpaceType == type,
                                            onClick = { selectedSpaceType = type },
                                            label = { Text(type.displayName, fontSize = 12.sp) }
                                        )
                                    }
                                }

                                Text("Lebanon Governorate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(Governorate.values()) { gov ->
                                        FilterChip(
                                            selected = selectedGovernorate == gov,
                                            onClick = { selectedGovernorate = gov },
                                            label = { Text(gov.displayName, fontSize = 12.sp) }
                                        )
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
                                        Text("Co-Sharing Practice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Shared with complementary doctors", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                                }

                                Text("Cover Photos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Real photos of the space — shown first in search results. Optional, but listings without any get a plain placeholder.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uploadedPhotoUrls) { url ->
                                        Box(
                                            modifier = Modifier
                                                .size(88.dp)
                                                .clip(RoundedCornerShape(10.dp))
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
                                                .clip(RoundedCornerShape(10.dp))
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
                                Text("Essential Facilities & Utilities", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                standardFacilities.forEach { facility ->
                                    val isChecked = selectedFacilities.contains(facility)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedFacilities = if (isChecked) selectedFacilities - facility else selectedFacilities + facility
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(checked = isChecked, onCheckedChange = {
                                            selectedFacilities = if (it) selectedFacilities + facility else selectedFacilities - facility
                                        })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(facility, fontSize = 13.sp)
                                    }
                                }

                                HorizontalDivider()

                                Text("Professional Equipment Catalog", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Select equipment available on premises", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                OutlinedTextField(
                                    value = equipmentSearchQuery,
                                    onValueChange = { equipmentSearchQuery = it },
                                    placeholder = { Text("Search equipment...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
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
                                            shape = RoundedCornerShape(8.dp),
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
                                                    Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(item.category.displayName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        "Configure Rooms & Workspace Subdivisions",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Add individual rooms, offices, clinical chambers, or shared desks that professionals can rent separately. Specify individual features and custom pricing strategies for each.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (subdivisionsList.isNotEmpty()) {
                                        Text("Configured Subdivisions (${subdivisionsList.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            subdivisionsList.forEachIndexed { index, sub ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(sub.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                            Text("Type: ${sub.type.displayName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                            Text(
                                                                "Renting: " + sub.rentalStrategies.joinToString { "${it.strategy.displayName} ($${it.rateUsd})" },
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            if (sub.amenities.isNotEmpty()) {
                                                                Text("Amenities: ${sub.amenities.joinToString()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                        IconButton(onClick = { subdivisionsList = subdivisionsList.filterIndexed { i, _ -> i != index } }) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider()
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Add Room / Unit Details", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            
                                            InputField(
                                                value = subName,
                                                onValueChange = { subName = it },
                                                label = "Room Name / Desk ID (e.g. Treatment Room B)",
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )

                                            Text("Room / Subdivision Type", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(Level2Type.values()) { type ->
                                                    FilterChip(
                                                        selected = subType == type,
                                                        onClick = { subType = type },
                                                        label = { Text(type.displayName, fontSize = 11.sp) }
                                                    )
                                                }
                                            }

                                            Text("Room Amenities", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(subAmenitiesPreset) { amen ->
                                                    val isSel = subAmenitiesSelected.contains(amen)
                                                    FilterChip(
                                                        selected = isSel,
                                                        onClick = {
                                                            subAmenitiesSelected = if (isSel) subAmenitiesSelected - amen else subAmenitiesSelected + amen
                                                        },
                                                        label = { Text(amen, fontSize = 10.sp) }
                                                    )
                                                }
                                            }

                                            Text("Renting Strategies & Rates for this Room", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = subHourlyEnabled, onCheckedChange = { subHourlyEnabled = it })
                                                    Text("Hourly Basis", fontSize = 12.sp)
                                                }
                                                if (subHourlyEnabled) {
                                                    OutlinedTextField(
                                                        value = subHourlyRate,
                                                        onValueChange = { subHourlyRate = it },
                                                        label = { Text("USD/hr") },
                                                        modifier = Modifier.width(100.dp),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = subShiftEnabled, onCheckedChange = { subShiftEnabled = it })
                                                        Text("Shift-Based", fontSize = 12.sp)
                                                    }
                                                    if (subShiftEnabled) {
                                                        OutlinedTextField(
                                                            value = subShiftHours,
                                                            onValueChange = { subShiftHours = it },
                                                            label = { Text("Shift Details (e.g. 8AM-1PM)") },
                                                            modifier = Modifier.fillMaxWidth(0.55f),
                                                            singleLine = true
                                                        )
                                                    }
                                                }
                                                if (subShiftEnabled) {
                                                    OutlinedTextField(
                                                        value = subShiftRate,
                                                        onValueChange = { subShiftRate = it },
                                                        label = { Text("USD/shift") },
                                                        modifier = Modifier.width(100.dp),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = subDailyEnabled, onCheckedChange = { subDailyEnabled = it })
                                                    Text("Daily Basis", fontSize = 12.sp)
                                                }
                                                if (subDailyEnabled) {
                                                    OutlinedTextField(
                                                        value = subDailyRate,
                                                        onValueChange = { subDailyRate = it },
                                                        label = { Text("USD/day") },
                                                        modifier = Modifier.width(100.dp),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = subMonthlyEnabled, onCheckedChange = { subMonthlyEnabled = it })
                                                    Text("Monthly Basis", fontSize = 12.sp)
                                                }
                                                if (subMonthlyEnabled) {
                                                    OutlinedTextField(
                                                        value = subMonthlyRate,
                                                        onValueChange = { subMonthlyRate = it },
                                                        label = { Text("USD/mo") },
                                                        modifier = Modifier.width(100.dp),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val subStrategies = mutableListOf<SubdivisionStrategy>()
                                                    if (subHourlyEnabled) {
                                                        subStrategies.add(SubdivisionStrategy(RentalStrategy.HOURLY, subHourlyRate.toDoubleOrNull() ?: 15.0))
                                                    }
                                                    if (subShiftEnabled) {
                                                        subStrategies.add(SubdivisionStrategy(RentalStrategy.SHIFT_BASED, subShiftRate.toDoubleOrNull() ?: 60.0, availableHoursOrShifts = subShiftHours))
                                                    }
                                                    if (subDailyEnabled) {
                                                        subStrategies.add(SubdivisionStrategy(RentalStrategy.DAILY, subDailyRate.toDoubleOrNull() ?: 120.0))
                                                    }
                                                    if (subMonthlyEnabled) {
                                                        subStrategies.add(SubdivisionStrategy(RentalStrategy.MONTHLY, subMonthlyRate.toDoubleOrNull() ?: 450.0))
                                                    }
                                                    
                                                    // No hardcoded stock photo per type anymore (was a fixed
                                                    // Unsplash URL regardless of the actual room/desk). Rooms
                                                    // and desks inherit the parent listing's real cover photos
                                                    // visually; a dedicated per-subdivision photo picker is a
                                                    // separate feature, not part of this fix.
                                                    val newSub = Subdivision(
                                                        name = subName,
                                                        type = subType,
                                                        imageUrls = emptyList(),
                                                        amenities = subAmenitiesSelected.toList(),
                                                        rentalStrategies = subStrategies
                                                    )
                                                    subdivisionsList = subdivisionsList + newSub
                                                    subName = ""
                                                    subAmenitiesSelected = emptySet()
                                                    subHourlyEnabled = false
                                                    subShiftEnabled = false
                                                    subDailyEnabled = false
                                                    subMonthlyEnabled = false
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = subName.isNotBlank() && (subHourlyEnabled || subShiftEnabled || subDailyEnabled || subMonthlyEnabled)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add Room / Desk to Listing", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Smart Dynamic Pricing & Formula Wizard (Step 2)
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Smart Dynamic Pricing & Formula Wizard", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Enter your base monthly valuation. The system automatically computes and suggests pro-rata fractional rates for shifting and day-per-week rentals.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    InputField(
                                        value = baseMonthlyRate,
                                        onValueChange = {
                                            baseMonthlyRate = it
                                            val baseVal = it.toDoubleOrNull() ?: 500.0
                                            shiftRate = (baseVal * 0.4 / 4).toInt().toString()
                                            dayPerWeekRate = (baseVal * 0.35).toInt().toString()
                                        },
                                        label = "Base Monthly Valuation (USD/mo)",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    val baseNum = baseMonthlyRate.toDoubleOrNull() ?: 500.0
                                    val suggestedShift = (baseNum * 0.4 / 4).toInt()
                                    val suggestedDayPerWk = (baseNum * 0.35).toInt()
                                    val suggestedHourly = (baseNum / 160).toInt().coerceAtLeast(15)

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Shift / Time-Slot Basis (40% pro-rata)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Suggested: $$suggestedShift USD / slot", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Switch(checked = shiftFormulaEnabled, onCheckedChange = { shiftFormulaEnabled = it })
                                            }
                                            if (shiftFormulaEnabled) {
                                                InputField(
                                                    value = shiftRate,
                                                    onValueChange = { shiftRate = it },
                                                    label = "Shift Rate (USD)",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Day-per-Week Basis (35% pro-rata)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Suggested: $$suggestedDayPerWk USD / mo", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Switch(checked = dayPerWeekEnabled, onCheckedChange = { dayPerWeekEnabled = it })
                                            }
                                            if (dayPerWeekEnabled) {
                                                InputField(
                                                    value = dayPerWeekRate,
                                                    onValueChange = { dayPerWeekRate = it },
                                                    label = "Day-per-Week Rate (USD)",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Hourly consulting reference rate is auto-calculated at ~$suggestedHourly USD/hr.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Step 3: Operating Hours, Blackouts & Contact
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Operating Hours, Blackouts & Contact", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Define operating days, shift hours, and host contact details for bookings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Text("Complementary Specialist Disciplines", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(commonSpecialties) { spec ->
                                        val isSel = selectedSpecialties.contains(spec)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                selectedSpecialties = if (isSel) selectedSpecialties - spec else selectedSpecialties + spec
                                            },
                                            label = { Text(spec, fontSize = 11.sp) }
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

                                Text("Proof of Ownership / Right to Rent", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Required to publish — a title deed, lease contract, or other document showing you're entitled to rent this specific space out. Kept on file, no review needed.",
                                    fontSize = 11.sp,
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

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep > 0) {
                        CustomButton(
                            text = "Back",
                            onClick = { currentStep-- },
                            variant = CustomButtonVariant.OUTLINED,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CustomButton(
                        text = if (currentStep < totalSteps - 1) "Next" else "Publish Listing",
                        onClick = {
                            if (currentStep < totalSteps - 1) {
                                currentStep++
                            } else {
                                // Finalize listing creation
                                val formulas = mutableListOf<RentalFormula>()
                                val monthly = if (hasSubdivisions) {
                                    subdivisionsList.flatMap { it.rentalStrategies }
                                        .filter { it.strategy == RentalStrategy.MONTHLY }
                                        .map { it.rateUsd }
                                        .minOrNull() ?: 450.0
                                } else {
                                    baseMonthlyRate.toDoubleOrNull() ?: 500.0
                                }
                                
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
                                    if (shiftFormulaEnabled) {
                                        val sRate = shiftRate.toDoubleOrNull() ?: 120.0
                                        formulas.add(
                                            RentalFormula(
                                                type = RentalFormulaType.SHIFT,
                                                rateUsd = sRate,
                                                scheduleDescription = "Morning / Afternoon Shift Slots",
                                                daysOfWeek = listOf("Mon", "Wed", "Fri"),
                                                startHour = "08:00",
                                                endHour = "14:00",
                                                totalWeeklyHours = 18,
                                                shiftName = "Morning Shift"
                                            )
                                        )
                                    }
                                    if (dayPerWeekEnabled) {
                                        val dRate = dayPerWeekRate.toDoubleOrNull() ?: 180.0
                                        formulas.add(
                                            RentalFormula(
                                                type = RentalFormulaType.DAY_PER_WEEK,
                                                rateUsd = dRate,
                                                scheduleDescription = "Day-per-Week Space Reservation (Choose 1 or 2 days/wk)",
                                                daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
                                                startHour = "08:00",
                                                endHour = "18:00",
                                                totalWeeklyHours = 10,
                                                daysCountRequired = 1
                                            )
                                        )
                                    }
                                }

                                // Geocode address using Android's Geocoder
                                val fullAddress = "${streetAddress}, ${district}, ${selectedGovernorate.displayName}, Lebanon"
                                var geocodedLat = selectedGovernorate.centerLat + ((-20..20).random() / 1000.0)
                                var geocodedLng = selectedGovernorate.centerLng + ((-20..20).random() / 1000.0)
                                try {
                                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                    val addresses = geocoder.getFromLocationName(fullAddress, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        geocodedLat = addresses[0].latitude
                                        geocodedLng = addresses[0].longitude
                                    }
                                } catch (e: Exception) {
                                    // fallback
                                }

                                val newListing = SpaceListing(
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
                                    rentalFormulas = formulas,
                                    rules = PremisesRules(),
                                    ownerId = activeUser.id,
                                    ownerName = activeUser.fullName,
                                    ownerPhone = ownerPhone,
                                    ownerEmail = activeUser.email,
                                    ownershipProofUrl = ownershipProofUrl,
                                    isVerified = true,
                                    isActiveSubscription = true,
                                    baseMonthlyRateUsd = monthly,
                                    subdivisions = subdivisionsList,
                                    imageUrls = uploadedPhotoUrls
                                )

                                onListingCreated(newListing)
                            }
                        },
                        variant = CustomButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.5f),
                        enabled = if (currentStep < totalSteps - 1) {
                            currentStep != 0 || title.isNotBlank() || district.isNotBlank()
                        } else {
                            ownershipProofUrl != null && !isUploadingOwnershipProof
                        }
                    )
                }
            }
        }
    }
}
