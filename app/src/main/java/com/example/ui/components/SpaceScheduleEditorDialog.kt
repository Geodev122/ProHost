package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProSpaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScheduleEditorDialog(
    space: SpaceListing,
    viewModel: ProSpaceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allSpaces by viewModel.spaces.collectAsState()
    val liveSpace = allSpaces.find { it.id == space.id } ?: space
    val schedule = liveSpace.schedule

    var openingHour by remember(schedule) { mutableStateOf(schedule.openingHour) }
    var closingHour by remember(schedule) { mutableStateOf(schedule.closingHour) }
    var isSundayOperating by remember(schedule) { mutableStateOf(schedule.isSundayOperating) }

    val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    var selectedDays by remember(schedule) { mutableStateOf(schedule.operatingDays.toSet()) }

    // Blackout slot creation state
    var showAddBlackout by remember { mutableStateOf(false) }
    var blackoutDay by remember { mutableStateOf("Friday") }
    var blackoutStart by remember { mutableStateOf("18:00") }
    var blackoutEnd by remember { mutableStateOf("20:00") }
    var blackoutReason by remember { mutableStateOf("Sterilization & Maintenance") }

    // Formula creation state
    var showAddFormula by remember { mutableStateOf(false) }
    var formulaType by remember { mutableStateOf(RentalFormulaType.DAY_PER_WEEK) }
    var formulaRateUsd by remember { mutableStateOf("150") }
    var formulaStartHour by remember { mutableStateOf("08:00") }
    var formulaEndHour by remember { mutableStateOf("18:00") }
    var formulaDescription by remember { mutableStateOf("1 Day per Week Practice • Choose Your Day") }
    var formulaSelectedDays by remember { mutableStateOf(setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) }
    var formulaDaysCountRequired by remember { mutableStateOf(1) }
    var formulaShiftName by remember { mutableStateOf("Morning Shift") }
    var formulaMinHours by remember { mutableStateOf(2) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
                .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
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
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Availability & Formula Control",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "Owner Suite")
                        }
                        Text(
                            text = "${liveSpace.title} • Configure operating windows and dynamic rental formulas",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. General Operating Hours
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "1. Facility Operating Window",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = openingHour,
                                    onValueChange = { openingHour = it },
                                    label = { Text("Open (HH:mm)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = closingHour,
                                    onValueChange = { closingHour = it },
                                    label = { Text("Close (HH:mm)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Text("Operating Days:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                weekDays.forEach { day ->
                                    val isSelected = selectedDays.contains(day)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                        },
                                        label = { Text(day, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Open on Sundays", fontSize = 13.sp)
                                Switch(
                                    checked = isSundayOperating,
                                    onCheckedChange = { isSundayOperating = it }
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.updateSpaceOperatingSchedule(
                                        spaceId = liveSpace.id,
                                        openingHour = openingHour,
                                        closingHour = closingHour,
                                        operatingDays = selectedDays.toList(),
                                        isSundayOperating = isSundayOperating,
                                        context = context
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Operating Window", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 2. Blackout / Hidden Non-Operating Hours
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "2. Hidden Non-Operating Slots",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Hide specific slots for maintenance or private surgeries",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { showAddBlackout = !showAddBlackout },
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(if (showAddBlackout) Icons.Default.ExpandLess else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(if (showAddBlackout) "Cancel" else "Add Blackout", fontSize = 12.sp)
                                }
                            }

                            if (showAddBlackout) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        InputField(
                                            value = blackoutDay,
                                            onValueChange = { blackoutDay = it },
                                            label = "Day of Week (e.g. Friday)",
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            InputField(
                                                value = blackoutStart,
                                                onValueChange = { blackoutStart = it },
                                                label = "From (HH:mm)",
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            InputField(
                                                value = blackoutEnd,
                                                onValueChange = { blackoutEnd = it },
                                                label = "To (HH:mm)",
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }

                                        InputField(
                                            value = blackoutReason,
                                            onValueChange = { blackoutReason = it },
                                            label = "Reason (e.g. Sterilization, Sanitization)",
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        CustomButton(
                                            text = "Save Blackout Slot",
                                            onClick = {
                                                viewModel.addBlackoutSlot(
                                                    spaceId = liveSpace.id,
                                                    dayOfWeek = blackoutDay,
                                                    startTime = blackoutStart,
                                                    endTime = blackoutEnd,
                                                    reason = blackoutReason,
                                                    context = context
                                                )
                                                showAddBlackout = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // Existing Blackout Slots List
                            if (schedule.blackoutSlots.isEmpty()) {
                                Text(
                                    text = "No blackout slots configured. Space is fully operational during open hours.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                schedule.blackoutSlots.forEach { slot ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "🚫 ${slot.dayOfWeek} (${slot.startTime} - ${slot.endTime})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(slot.reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            IconButton(
                                                onClick = { viewModel.removeBlackoutSlot(liveSpace.id, slot.id, context) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Rental Formulas & Availability Builder
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "3. Space Rental Formulas",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Create Day-per-Week, Shift, Hourly, or Full-Month packages",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { showAddFormula = !showAddFormula },
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(if (showAddFormula) Icons.Default.ExpandLess else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(if (showAddFormula) "Close" else "Create Formula", fontSize = 12.sp)
                                }
                            }

                            if (showAddFormula) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.medium,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("Select Formula Model:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(RentalFormulaType.values().toList()) { type ->
                                                val isSelected = formulaType == type
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        formulaType = type
                                                        when (type) {
                                                            RentalFormulaType.DAY_PER_WEEK -> {
                                                                formulaRateUsd = "150"
                                                                formulaStartHour = "08:00"
                                                                formulaEndHour = "18:00"
                                                                formulaDescription = "Day-per-Week • Specialist picks from available days"
                                                                formulaSelectedDays = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                                                formulaDaysCountRequired = 1
                                                            }
                                                            RentalFormulaType.SHIFT -> {
                                                                formulaRateUsd = "120"
                                                                formulaStartHour = "08:00"
                                                                formulaEndHour = "13:30"
                                                                formulaDescription = "Morning Shift • Dedicated 5.5 hours"
                                                                formulaShiftName = "Morning Shift"
                                                                formulaSelectedDays = setOf("Mon", "Wed", "Fri")
                                                            }
                                                            RentalFormulaType.HOURLY -> {
                                                                formulaRateUsd = "25"
                                                                formulaStartHour = "08:00"
                                                                formulaEndHour = "20:00"
                                                                formulaDescription = "Flexible Hourly Slots ($25/hr)"
                                                                formulaMinHours = 2
                                                                formulaSelectedDays = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                                            }
                                                            RentalFormulaType.FULL_MONTH -> {
                                                                formulaRateUsd = "650"
                                                                formulaStartHour = "08:00"
                                                                formulaEndHour = "20:00"
                                                                formulaDescription = "Full Dedicated Month Exclusive"
                                                                formulaSelectedDays = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                                            }
                                                        }
                                                    },
                                                    label = { Text(type.displayName, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                                )
                                            }
                                        }

                                        // Formula Specific Fields
                                        when (formulaType) {
                                            RentalFormulaType.DAY_PER_WEEK -> {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "💡 In Day-per-Week formula, you set the days your space is available. When a specialist chooses this formula, they will select their specific day(s) from these available days.",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }

                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    InputField(
                                                        value = formulaRateUsd,
                                                        onValueChange = { formulaRateUsd = it },
                                                        label = "Rate ($ USD/mo)",
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                    InputField(
                                                        value = formulaDaysCountRequired.toString(),
                                                        onValueChange = { formulaDaysCountRequired = it.toIntOrNull() ?: 1 },
                                                        label = "Days/Wk Included",
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            RentalFormulaType.SHIFT -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    InputField(
                                                        value = formulaShiftName,
                                                        onValueChange = { formulaShiftName = it },
                                                        label = "Shift Title (e.g. Morning)",
                                                        modifier = Modifier.weight(1.2f),
                                                        singleLine = true
                                                    )
                                                    InputField(
                                                        value = formulaRateUsd,
                                                        onValueChange = { formulaRateUsd = it },
                                                        label = "Rate ($ USD/mo)",
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            RentalFormulaType.HOURLY -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    InputField(
                                                        value = formulaRateUsd,
                                                        onValueChange = { formulaRateUsd = it },
                                                        label = "Rate ($ USD / hour)",
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                    InputField(
                                                        value = formulaMinHours.toString(),
                                                        onValueChange = { formulaMinHours = it.toIntOrNull() ?: 2 },
                                                        label = "Min Booking Hours",
                                                        modifier = Modifier.weight(1f),
                                                        singleLine = true
                                                    )
                                                }
                                            }

                                            RentalFormulaType.FULL_MONTH -> {
                                                InputField(
                                                    value = formulaRateUsd,
                                                    onValueChange = { formulaRateUsd = it },
                                                    label = "Monthly Rate ($ USD)",
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true
                                                )
                                            }
                                        }

                                        InputField(
                                            value = formulaDescription,
                                            onValueChange = { formulaDescription = it },
                                            label = "Formula Description for Specialists",
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text("Set Days Space is Available for this Formula:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            weekDays.forEach { day ->
                                                val isSelected = formulaSelectedDays.contains(day)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        formulaSelectedDays = if (isSelected) {
                                                            if (formulaSelectedDays.size > 1) formulaSelectedDays - day else formulaSelectedDays
                                                        } else {
                                                            formulaSelectedDays + day
                                                        }
                                                    },
                                                    label = { Text(day, fontSize = 10.sp) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            InputField(
                                                value = formulaStartHour,
                                                onValueChange = { formulaStartHour = it },
                                                label = "Available From (HH:mm)",
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            InputField(
                                                value = formulaEndHour,
                                                onValueChange = { formulaEndHour = it },
                                                label = "Available To (HH:mm)",
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }

                                        CustomButton(
                                            text = "Publish Rental Formula",
                                            onClick = {
                                                val rate = formulaRateUsd.toDoubleOrNull() ?: 100.0
                                                val startH = formulaStartHour.substringBefore(":").toIntOrNull() ?: 8
                                                val endH = formulaEndHour.substringBefore(":").toIntOrNull() ?: 18
                                                val dailyH = (endH - startH).coerceAtLeast(1)
                                                val totalWeeklyH = dailyH * formulaSelectedDays.size.coerceAtLeast(1)

                                                viewModel.addCustomFormula(
                                                    spaceId = liveSpace.id,
                                                    type = formulaType,
                                                    rateUsd = rate,
                                                    description = formulaDescription,
                                                    daysOfWeek = formulaSelectedDays.toList(),
                                                    startHour = formulaStartHour,
                                                    endHour = formulaEndHour,
                                                    weeklyHours = totalWeeklyH,
                                                    daysCountRequired = formulaDaysCountRequired,
                                                    minHours = formulaMinHours,
                                                    shiftName = formulaShiftName,
                                                    context = context
                                                )
                                                showAddFormula = false
                                            },
                                            icon = Icons.Default.AddCircle,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // Current Formulas in Space
                            liveSpace.rentalFormulas.forEach { f ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = f.type.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (f.type == RentalFormulaType.HOURLY) "$${f.rateUsd.toInt()} USD/hr" else "$${f.rateUsd.toInt()} USD/mo",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Text(f.scheduleDescription, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (f.daysOfWeek.isNotEmpty() && f.startHour.isNotBlank()) {
                                                Text(
                                                    text = "🗓 Available: ${f.daysOfWeek.joinToString()} • ${f.startHour} - ${f.endHour} (${f.totalWeeklyHours} hrs/wk)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteFormula(liveSpace.id, f.id, context) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                CustomButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
