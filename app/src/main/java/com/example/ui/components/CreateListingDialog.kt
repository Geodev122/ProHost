package com.example.ui.components

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
import com.example.data.model.*
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
        role = UserRole.SPACE_OWNER,
        specialty = "Commercial Workspace Host",
        phone = "+961 70 888 999",
        affiliation = "ProSpace Executive Network",
        syndicateNumber = "HOST-LB-01",
        governorate = Governorate.BEIRUT,
        isVerified = true
    )

    val context = androidx.compose.ui.platform.LocalContext.current

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
                                                    
                                                    val subImgUrl = when (subType) {
                                                        Level2Type.ROOMS -> "https://images.unsplash.com/photo-1629909613654-28e377c37b09?auto=format&fit=crop&q=80&w=400"
                                                        Level2Type.CONFERENCE_ROOM -> "https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&q=80&w=400"
                                                        Level2Type.THEATER_TRAINING -> "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&q=80&w=400"
                                                        Level2Type.DESK_IN_SHARED_AREA -> "https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&q=80&w=400"
                                                    }

                                                    val newSub = Subdivision(
                                                        name = subName,
                                                        type = subType,
                                                        imageUrls = listOf(subImgUrl),
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
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Flexible Renting Formulas", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                    InputField(
                                        value = baseMonthlyRate,
                                        onValueChange = { baseMonthlyRate = it },
                                        label = "Full Month Dedicated Rate (USD/mo)",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Shift / Time-Slot Basis", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Switch(checked = shiftFormulaEnabled, onCheckedChange = { shiftFormulaEnabled = it })
                                            }
                                            if (shiftFormulaEnabled) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                InputField(
                                                    value = shiftRate,
                                                    onValueChange = { shiftRate = it },
                                                    label = "Shift Rate (USD per shift slot)",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Day-per-Week Basis", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Switch(checked = dayPerWeekEnabled, onCheckedChange = { dayPerWeekEnabled = it })
                                            }
                                            if (dayPerWeekEnabled) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                InputField(
                                                    value = dayPerWeekRate,
                                                    onValueChange = { dayPerWeekRate = it },
                                                    label = "Rate for Fixed Days (USD/mo)",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }

                                    Text("Complementary Professional Disciplines", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                }
                            }
                        }

                        3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Complementary Disciplines & Contact Info", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                Text("This helps matching professionals who shared this office space.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Text("Complementary Professional Disciplines", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    id = "SPC-LB-" + UUID.randomUUID().toString().take(6).uppercase(),
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
                                    isVerified = true,
                                    isActiveSubscription = true,
                                    baseMonthlyRateUsd = monthly,
                                    subdivisions = subdivisionsList
                                )

                                onListingCreated(newListing)
                            }
                        },
                        variant = CustomButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.5f),
                        enabled = currentStep != 0 || title.isNotBlank() || district.isNotBlank()
                    )
                }
            }
        }
    }
}
