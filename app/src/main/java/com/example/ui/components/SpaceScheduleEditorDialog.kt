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
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScheduleEditorDialog(
    space: SpaceListing,
    viewModel: ProHostViewModel,
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

    // The rentable slots the host can switch on or off are derived from the space's
    // own pricing (or each subdivision's) rather than typed by hand, so a blocked
    // slot always lines up with something a specialist could actually have booked.
    // Shared with SpaceAvailabilityMatrixView (the Specialist-facing side) via
    // SpaceCalculationUtils.buildAllSlotsForSpace so both sides can never disagree —
    // and, unlike the old buildRentableSlots(liveSpace.rentalFormulas, ...) call this
    // replaces, this actually expands subdivision pricing too, instead of silently
    // returning nothing for every subdivided listing.
    val derivedSlots = remember(liveSpace) {
        SpaceCalculationUtils.buildAllSlotsForSpace(liveSpace)
    }

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
                                text = "Availability Control",
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "Owner Suite")
                        }
                        Text(
                            text = "${liveSpace.title} • Configure operating windows and dynamic rental formulas",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)

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
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OperatingScheduleEditorSection(
                                openingHour = openingHour,
                                onOpeningHourChange = { openingHour = it },
                                closingHour = closingHour,
                                onClosingHourChange = { closingHour = it },
                                selectedDays = selectedDays,
                                onDaysChange = { selectedDays = it },
                                weekDayOptions = weekDays
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Open on Sundays", fontSize = MaterialTheme.typography.bodySmall.fontSize)
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
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Operating Window", fontSize = MaterialTheme.typography.bodySmall.fontSize, fontWeight = FontWeight.Bold)
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
                            Column {
                                Text(
                                    text = "2. Slots Offered for Rent",
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Every slot your formulas create is on by default. Switch off anything you don't want to rent out.",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (derivedSlots.isEmpty()) {
                                Text(
                                    text = "No rentable slots yet — publish a rental formula in section 3 below and its slots will appear here.",
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                derivedSlots.groupBy { it.groupLabel }.forEach { (groupLabel, slots) ->
                                    Text(
                                        text = groupLabel,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    slots.forEach { slot ->
                                        val blocking = schedule.blackoutSlots.firstOrNull {
                                            it.dayOfWeek.equals(slot.day, ignoreCase = true) &&
                                                it.startTime == slot.startTime &&
                                                it.endTime == slot.endTime
                                        }
                                        val isOffered = blocking == null
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = slot.label,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                                        color = if (isOffered) {
                                                            MaterialTheme.colorScheme.onSurface
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                    Text(
                                                        text = if (isOffered) "Available to rent" else "Hidden — not offered",
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                        color = if (isOffered) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Switch(
                                                    checked = isOffered,
                                                    onCheckedChange = { nowOffered ->
                                                        if (nowOffered) {
                                                            blocking?.let { viewModel.removeBlackoutSlot(liveSpace.id, it.id, context) }
                                                        } else {
                                                            viewModel.addBlackoutSlot(
                                                                spaceId = liveSpace.id,
                                                                dayOfWeek = slot.day,
                                                                startTime = slot.startTime,
                                                                endTime = slot.endTime,
                                                                reason = "Not offered for rent",
                                                                context = context
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Anything blocked that no current formula produces — kept
                            // visible so older or since-removed slots stay removable
                            // instead of silently blocking availability forever.
                            val orphanSlots = schedule.blackoutSlots.filterNot { blocked ->
                                derivedSlots.any {
                                    it.day.equals(blocked.dayOfWeek, ignoreCase = true) &&
                                        it.startTime == blocked.startTime &&
                                        it.endTime == blocked.endTime
                                }
                            }
                            if (orphanSlots.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    text = "Other blocked slots",
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    fontWeight = FontWeight.SemiBold
                                )
                                orphanSlots.forEach { slot ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "🚫 ${slot.dayOfWeek} (${slot.startTime} - ${slot.endTime})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                                                )
                                                Text(slot.reason, fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Create Day-per-Week, Shift, Hourly, or Full-Month packages",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                                    Text(if (showAddFormula) "Close" else "Create Formula", fontSize = MaterialTheme.typography.labelMedium.fontSize)
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
                                        Text("Select Formula Model:", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.Bold)
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
                                                    label = { Text(type.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
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
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(Spacing.sm)
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

                                        Text("Set Days Space is Available for this Formula:", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)
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
                                                    label = { Text(day.take(1), fontSize = MaterialTheme.typography.bodySmall.fontSize) },
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

                                        ProPrimaryButton(
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
                                    shape = MaterialTheme.shapes.medium,
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
                                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (f.type == RentalFormulaType.HOURLY) "$${f.rateUsd.toInt()} USD/hr" else "$${f.rateUsd.toInt()} USD/mo",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                                )
                                            }
                                            Text(f.scheduleDescription, fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                    // 4. Rooms & Subdivisions — previously only editable at listing-creation
                    // time (CreateListingDialog's Step 2); a host who published first and
                    // only later needed another room had no in-app way to add one. Shown for
                    // any space type that supports subdivisions, or one that already has any
                    // (so a type change never strands existing rooms behind a hidden section).
                    //
                    // spaceType alone isn't enough once admin-defined categories exist:
                    // legacyTypeFor() (CreateListingDialog) maps every genuinely-new admin
                    // category with no legacy equivalent to SpaceType.PRIVATE_OFFICE, so a
                    // listing published under such a category — with no subdivisions yet —
                    // would otherwise fail this check and have no in-app way to ever add a
                    // first one. legacySpaceTypeForCategoryId returning null is exactly that
                    // case: a real admin category, not actually a Private Office.
                    val isGenuinelyNewCategory = liveSpace.spaceCategoryId != null &&
                        legacySpaceTypeForCategoryId(liveSpace.spaceCategoryId) == null
                    if (liveSpace.spaceType != SpaceType.PRIVATE_OFFICE || liveSpace.subdivisions.isNotEmpty() || isGenuinelyNewCategory) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            SubdivisionEditorSection(
                                modifier = Modifier.padding(14.dp),
                                subdivisionsList = liveSpace.subdivisions,
                                operatingDays = liveSpace.schedule.operatingDays,
                                openingHour = liveSpace.schedule.openingHour,
                                closingHour = liveSpace.schedule.closingHour,
                                onSubdivisionsChange = { newList ->
                                    if (newList.size > liveSpace.subdivisions.size) {
                                        val added = newList.find { new -> liveSpace.subdivisions.none { it.id == new.id } }
                                        if (added != null) viewModel.addSubdivision(liveSpace.id, added, context)
                                    } else if (newList.size < liveSpace.subdivisions.size) {
                                        val removedId = liveSpace.subdivisions.find { old -> newList.none { it.id == old.id } }?.id
                                        if (removedId != null) viewModel.removeSubdivision(liveSpace.id, removedId, context)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                ProPrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
