package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.ui.viewmodel.ProHostViewModel
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalBookingDialog(
    space: SpaceListing,
    initialFormula: RentalFormula?,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit,
    onRequestSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()

    val allWeekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    val hasSubdivisions = space.subdivisions.isNotEmpty()
    var selectedSubdivision by remember {
        mutableStateOf(space.subdivisions.firstOrNull())
    }
    var selectedSubStrategy by remember(selectedSubdivision) {
        mutableStateOf(selectedSubdivision?.rentalStrategies?.firstOrNull())
    }

    var innerSelectedFormula by remember {
        mutableStateOf(initialFormula ?: space.rentalFormulas.firstOrNull() ?: RentalFormula(
            type = RentalFormulaType.FULL_MONTH,
            rateUsd = space.baseMonthlyRateUsd,
            scheduleDescription = "Full Dedicated Month",
            daysOfWeek = space.schedule.operatingDays.ifEmpty { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat") },
            startHour = space.schedule.openingHour,
            endHour = space.schedule.closingHour,
            totalWeeklyHours = 72
        ))
    }

    val selectedFormula = remember(hasSubdivisions, selectedSubdivision, selectedSubStrategy, innerSelectedFormula) {
        if (hasSubdivisions && selectedSubdivision != null && selectedSubStrategy != null) {
            val formulaType = when (selectedSubStrategy!!.strategy) {
                RentalStrategy.HOURLY -> RentalFormulaType.HOURLY
                RentalStrategy.SHIFT_BASED -> RentalFormulaType.SHIFT
                RentalStrategy.DAILY -> RentalFormulaType.DAY_PER_WEEK
                RentalStrategy.MONTHLY -> RentalFormulaType.FULL_MONTH
            }
            val desc = when (selectedSubStrategy!!.strategy) {
                RentalStrategy.HOURLY -> "Hourly Rental of ${selectedSubdivision!!.name}"
                RentalStrategy.SHIFT_BASED -> "Shift Rental of ${selectedSubdivision!!.name} (${selectedSubStrategy!!.availableHoursOrShifts})"
                RentalStrategy.DAILY -> "Daily Rental of ${selectedSubdivision!!.name}"
                RentalStrategy.MONTHLY -> "Monthly Rental of ${selectedSubdivision!!.name}"
            }
            RentalFormula(
                id = "SUB-FRM-" + selectedSubdivision!!.id.take(4) + "-" + selectedSubStrategy!!.strategy.name.take(3),
                type = formulaType,
                rateUsd = selectedSubStrategy!!.rateUsd,
                scheduleDescription = desc,
                daysOfWeek = space.schedule.operatingDays.ifEmpty { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat") },
                startHour = if (selectedSubStrategy!!.strategy == RentalStrategy.SHIFT_BASED && selectedSubStrategy!!.availableHoursOrShifts.contains("-")) {
                    selectedSubStrategy!!.availableHoursOrShifts.substringBefore("-").trim()
                } else "08:00",
                endHour = if (selectedSubStrategy!!.strategy == RentalStrategy.SHIFT_BASED && selectedSubStrategy!!.availableHoursOrShifts.contains("-")) {
                    selectedSubStrategy!!.availableHoursOrShifts.substringAfter("-").trim()
                } else "18:00",
                totalWeeklyHours = 40,
                daysCountRequired = 1,
                shiftName = if (selectedSubStrategy!!.strategy == RentalStrategy.SHIFT_BASED) selectedSubStrategy!!.availableHoursOrShifts else ""
            )
        } else {
            innerSelectedFormula
        }
    }

    // Availability selection state based on formula
    // For DAY_PER_WEEK:
    var chosenDaysForDayPerWeek by remember(selectedFormula) {
        mutableStateOf(
            if (selectedFormula.type == RentalFormulaType.DAY_PER_WEEK) {
                selectedFormula.daysOfWeek.take(selectedFormula.daysCountRequired.coerceAtLeast(1)).toSet()
            } else {
                selectedFormula.daysOfWeek.toSet()
            }
        )
    }

    // For SHIFT:
    var selectedShiftPreset by remember(selectedFormula) {
        mutableStateOf(
            if (selectedFormula.startHour >= "13:00") "Afternoon Shift" else "Morning Shift"
        )
    }
    var shiftStartHour by remember(selectedFormula) { mutableStateOf(selectedFormula.startHour) }
    var shiftEndHour by remember(selectedFormula) { mutableStateOf(selectedFormula.endHour) }
    var chosenDaysForShift by remember(selectedFormula) {
        mutableStateOf(selectedFormula.daysOfWeek.toSet())
    }

    // For HOURLY:
    var chosenDaysForHourly by remember(selectedFormula) {
        mutableStateOf(setOf(selectedFormula.daysOfWeek.firstOrNull() ?: "Mon"))
    }
    var hourlyStartHour by remember(selectedFormula) { mutableStateOf("09:00") }
    var hourlyEndHour by remember(selectedFormula) { mutableStateOf("13:00") }

    // Start Date & Duration
    val dateOptions = listOf(
        "Immediate (Tomorrow)",
        "Next Monday",
        "1st of Next Month",
        "Custom Date"
    )
    var selectedDateOption by remember { mutableStateOf(dateOptions[0]) }
    var customStartDate by remember { mutableStateOf("2026-09-01") }

    val durationOptions = listOf(1, 2, 3, 6, 12)
    var selectedDurationMonths by remember { mutableStateOf(1) }

    var clinicalNotes by remember {
        mutableStateOf(
            "Specialist workspace rental (${currentUser?.specialty?.ifBlank { "Specialist" } ?: "Specialist"})."
        )
    }

    var bookingModeTab by remember { mutableStateOf(0) } // 0: Interactive Calendar & Live Availability, 1: Step-by-Step Formula Wizard

    // Dynamic Financial Calculation
    val dynamicMonthlyRate = remember(selectedFormula, chosenDaysForDayPerWeek, chosenDaysForShift, chosenDaysForHourly, hourlyStartHour, hourlyEndHour) {
        when (selectedFormula.type) {
            RentalFormulaType.FULL_MONTH -> selectedFormula.rateUsd
            RentalFormulaType.DAY_PER_WEEK -> {
                // Base rate covers formula's daysCountRequired; if user selects more days, scale proportionally
                val baseDays = selectedFormula.daysCountRequired.coerceAtLeast(1)
                val selectedCount = chosenDaysForDayPerWeek.size.coerceAtLeast(1)
                val perDayRate = selectedFormula.rateUsd / baseDays
                perDayRate * selectedCount
            }
            RentalFormulaType.SHIFT -> {
                val baseDays = selectedFormula.daysOfWeek.size.coerceAtLeast(1)
                val selectedCount = chosenDaysForShift.size.coerceAtLeast(1)
                (selectedFormula.rateUsd / baseDays) * selectedCount
            }
            RentalFormulaType.HOURLY -> {
                val startH = hourlyStartHour.substringBefore(":").toIntOrNull() ?: 9
                val endH = hourlyEndHour.substringBefore(":").toIntOrNull() ?: 13
                val dailyHrs = (endH - startH).coerceAtLeast(1)
                val daysPerWeek = chosenDaysForHourly.size.coerceAtLeast(1)
                val hourlyRate = if (selectedFormula.rateUsd < 100) selectedFormula.rateUsd else 25.0
                hourlyRate * dailyHrs * daysPerWeek * 4 // 4 weeks in a month
            }
        }
    }

    val totalCalculatedUsd = dynamicMonthlyRate * selectedDurationMonths

    // Computed Slot Description
    val chosenSlotSummary = remember(selectedFormula, chosenDaysForDayPerWeek, chosenDaysForShift, chosenDaysForHourly, hourlyStartHour, hourlyEndHour, shiftStartHour, shiftEndHour, selectedShiftPreset) {
        when (selectedFormula.type) {
            RentalFormulaType.DAY_PER_WEEK -> {
                val days = chosenDaysForDayPerWeek.toList().sorted()
                "Every ${days.joinToString(", ")} (${selectedFormula.startHour} - ${selectedFormula.endHour})"
            }
            RentalFormulaType.SHIFT -> {
                val days = chosenDaysForShift.toList().sorted()
                "$selectedShiftPreset ($shiftStartHour - $shiftEndHour) on ${days.joinToString(", ")}"
            }
            RentalFormulaType.HOURLY -> {
                val days = chosenDaysForHourly.toList().sorted()
                "Hourly Slot: ${days.joinToString(", ")} from $hourlyStartHour to $hourlyEndHour"
            }
            RentalFormulaType.FULL_MONTH -> {
                "Full Practice Month (${selectedFormula.daysOfWeek.joinToString()} • ${selectedFormula.startHour} - ${selectedFormula.endHour})"
            }
        }
    }

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
                                text = "Rental Request & Booking",
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "Formula-Based")
                        }
                        Text(
                            text = "${space.title} • ${space.district}, ${space.governorate.displayName}",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                TabRow(
                    selectedTabIndex = bookingModeTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = bookingModeTab == 0,
                        onClick = { bookingModeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Interactive Calendar", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            }
                        }
                    )
                    Tab(
                        selected = bookingModeTab == 1,
                        onClick = { bookingModeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Formula Wizard", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)

                if (bookingModeTab == 0) {
                    // Mode 0: Interactive Calendar with Real-time Availability & Collision Checker
                    val spaceAcceptedBookings = viewModel.bookingRequests.collectAsState().value.filter {
                        it.spaceId == space.id && it.status == BookingRequestStatus.ACCEPTED
                    }
                    WorkspaceInteractiveBookingCalendar(
                        space = space,
                        acceptedBookings = spaceAcceptedBookings,
                        initialFormula = selectedFormula,
                        onScheduleSelected = { startDate, endDate, durationMonths, selectedDays, startHour, endHour, selectedShift, totalUsd, isInstantAvailable ->
                            val formula = selectedFormula.copy(
                                rateUsd = totalUsd / durationMonths.coerceAtLeast(1),
                                scheduleDescription = "Interactive Booking $selectedShift ($startHour - $endHour)",
                                daysOfWeek = selectedDays,
                                startHour = startHour,
                                endHour = endHour,
                                totalWeeklyHours = 40
                            )

                            viewModel.submitBookingRequest(
                                space = space,
                                formula = formula,
                                startDate = startDate,
                                durationMonths = durationMonths,
                                notes = clinicalNotes,
                                context = context,
                                alsoOpenWhatsApp = false,
                                selectedDays = selectedDays,
                                selectedStartHour = startHour,
                                selectedEndHour = endHour,
                                selectedShift = selectedShift,
                                calculatedTotalUsd = totalUsd,
                                subdivisionId = selectedSubdivision?.id,
                                subdivisionName = selectedSubdivision?.name,
                                selectedStrategy = selectedSubStrategy?.strategy?.name
                            )
                            onRequestSubmitted()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Mode 1: Step-by-Step Formula Configuration
                    // Scrollable Form Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    // 1. Choose Formula or Subdivision & Strategy
                    if (hasSubdivisions) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "1. Choose Subdivision / Room to Rent",
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            space.subdivisions.forEach { sub ->
                                val isSelected = sub.id == selectedSubdivision?.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSubdivision = sub },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.md)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedSubdivision = sub },
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(
                                                        text = sub.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Type: ${sub.type.displayName}",
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (sub.amenities.isNotEmpty()) {
                                                        Text(
                                                            text = "Amenities: ${sub.amenities.joinToString()}",
                                                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "2. Select Renting Strategy",
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            selectedSubdivision?.rentalStrategies?.forEach { strat ->
                                val isSelected = strat.strategy == selectedSubStrategy?.strategy
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSubStrategy = strat },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(Spacing.md).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedSubStrategy = strat },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.secondary)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = strat.strategy.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (strat.availableHoursOrShifts.isNotBlank()) {
                                                    Text(
                                                        text = "Schedule: ${strat.availableHoursOrShifts}",
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "$${strat.rateUsd.toInt()} USD",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1. Select Rental Formula",
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${space.rentalFormulas.size} Owner Formulas Offered",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            space.rentalFormulas.forEach { formula ->
                                val isSelected = formula.id == selectedFormula.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { innerSelectedFormula = formula },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Column(modifier = Modifier.padding(Spacing.md)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { innerSelectedFormula = formula },
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.xs))
                                                Column {
                                                    Text(
                                                        text = formula.type.displayName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = formula.scheduleDescription,
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "$${formula.rateUsd.toInt()} USD/mo",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // Owner Availability Offered Tag
                                        Spacer(modifier = Modifier.height(Spacing.xs))
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.EventAvailable,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(Spacing.xs))
                                                Text(
                                                    text = "Owner Availability: ${formula.daysOfWeek.joinToString()} • ${formula.startHour} - ${formula.endHour}",
                                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Dynamic Availability Slot Selection (Conditioned on Formula Type)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "2. Customize Your Required Availability",
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            when (selectedFormula.type) {
                                RentalFormulaType.DAY_PER_WEEK -> {
                                    Text(
                                        text = "The owner offers availability on: ${selectedFormula.daysOfWeek.joinToString(", ")}.\n" +
                                               "Please choose which day(s) (${selectedFormula.daysCountRequired} day(s) included in base rate) you want to rent:",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )

                                    // Interactive Day Picker Chips (Filter only by owner's available days)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allWeekDays.forEach { day ->
                                            val isOfferedByOwner = selectedFormula.daysOfWeek.contains(day)
                                            val isSelected = chosenDaysForDayPerWeek.contains(day)

                                            FilterChip(
                                                selected = isSelected && isOfferedByOwner,
                                                enabled = isOfferedByOwner,
                                                onClick = {
                                                    if (isOfferedByOwner) {
                                                        chosenDaysForDayPerWeek = if (isSelected) {
                                                            if (chosenDaysForDayPerWeek.size > 1) chosenDaysForDayPerWeek - day else chosenDaysForDayPerWeek
                                                        } else {
                                                            chosenDaysForDayPerWeek + day
                                                        }
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = day,
                                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = if (isSelected && isOfferedByOwner) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                                } else null,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LebaneseCedarGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Your Chosen Practice Days: ${chosenDaysForDayPerWeek.joinToString(", ")} (${selectedFormula.startHour} - ${selectedFormula.endHour})",
                                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                RentalFormulaType.SHIFT -> {
                                    Text(
                                        text = "Shift timing: ${selectedFormula.startHour} - ${selectedFormula.endHour}. Owner offers this shift on: ${selectedFormula.daysOfWeek.joinToString(", ")}.\n" +
                                               "Choose your shift preference and practice days:",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )

                                    // Shift Preset Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Morning Shift (8 AM - 1 PM)", "Afternoon Shift (1:30 PM - 6:30 PM)", "Full Shift Window").forEach { preset ->
                                            val isSel = selectedShiftPreset.startsWith(preset.take(7))
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    selectedShiftPreset = preset
                                                    if (preset.startsWith("Morning")) {
                                                        shiftStartHour = "08:00"
                                                        shiftEndHour = "13:00"
                                                    } else if (preset.startsWith("Afternoon")) {
                                                        shiftStartHour = "13:30"
                                                        shiftEndHour = "18:30"
                                                    } else {
                                                        shiftStartHour = selectedFormula.startHour
                                                        shiftEndHour = selectedFormula.endHour
                                                    }
                                                },
                                                label = { Text(preset, fontSize = 10.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Text("Select Shift Days from Owner's Availability:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allWeekDays.forEach { day ->
                                            val isOffered = selectedFormula.daysOfWeek.contains(day)
                                            val isSelected = chosenDaysForShift.contains(day)

                                            FilterChip(
                                                selected = isSelected && isOffered,
                                                enabled = isOffered,
                                                onClick = {
                                                    if (isOffered) {
                                                        chosenDaysForShift = if (isSelected) {
                                                            if (chosenDaysForShift.size > 1) chosenDaysForShift - day else chosenDaysForShift
                                                        } else {
                                                            chosenDaysForShift + day
                                                        }
                                                    }
                                                },
                                                label = { Text(day, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                RentalFormulaType.HOURLY -> {
                                    Text(
                                        text = "Hourly Booking: Space open from ${selectedFormula.startHour} to ${selectedFormula.endHour} on: ${selectedFormula.daysOfWeek.joinToString(", ")}.\n" +
                                               "Choose your required days & precise practice hours:",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = hourlyStartHour,
                                            onValueChange = { hourlyStartHour = it },
                                            label = { Text("Start Time (e.g. 09:00)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = hourlyEndHour,
                                            onValueChange = { hourlyEndHour = it },
                                            label = { Text("End Time (e.g. 13:00)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }

                                    Text("Select practice days:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allWeekDays.forEach { day ->
                                            val isOffered = selectedFormula.daysOfWeek.contains(day)
                                            val isSelected = chosenDaysForHourly.contains(day)

                                            FilterChip(
                                                selected = isSelected && isOffered,
                                                enabled = isOffered,
                                                onClick = {
                                                    if (isOffered) {
                                                        chosenDaysForHourly = if (isSelected) {
                                                            if (chosenDaysForHourly.size > 1) chosenDaysForHourly - day else chosenDaysForHourly
                                                        } else {
                                                            chosenDaysForHourly + day
                                                        }
                                                    }
                                                },
                                                label = { Text(day, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                RentalFormulaType.FULL_MONTH -> {
                                    Text(
                                        text = "Exclusive Full-Month Access: The clinic space is reserved exclusively for your practice during all facility operating days (${space.schedule.operatingDays.joinToString(", ")}) from ${space.schedule.openingHour} to ${space.schedule.closingHour}.",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // 3. Start Date Selector
                    Column {
                        Text(
                            text = "3. Select Starting Date",
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(dateOptions) { option ->
                                val isSelected = selectedDateOption == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDateOption = option },
                                    label = { Text(option, fontSize = MaterialTheme.typography.labelMedium.fontSize) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }

                        if (selectedDateOption == "Custom Date") {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            OutlinedTextField(
                                value = customStartDate,
                                onValueChange = { customStartDate = it },
                                label = { Text("Start Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 4. Rental Duration Term
                    Column {
                        Text(
                            text = "4. Rental Duration Term",
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            durationOptions.forEach { months ->
                                val isSelected = selectedDurationMonths == months
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedDurationMonths = months },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$months mo",
                                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Notes & Scope
                    Column {
                        Text(
                            text = "5. Specialist Requirements & Notes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        InputField(
                            value = clinicalNotes,
                            onValueChange = { clinicalNotes = it },
                            label = "Intended use, team size & special equipment needed",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 4
                        )
                    }

                    // Booking Rules Notice Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = StatusSuccessContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "Smart Availability & Confirmation Rule",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    color = StatusSuccess
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "• Space hours remain AVAILABLE to other professionals until the space owner accepts your request.\n" +
                                       "• Once accepted by the owner, your chosen schedule ($chosenSlotSummary) is locked exclusively for your use.\n" +
                                       "• Payment is settled directly with the space owner (Cash / Whish Money / Wire Transfer).",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = StatusOnSuccessContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Financial Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Total Rental Agreement Value",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "$${dynamicMonthlyRate.toInt()} USD × $selectedDurationMonths month${if (selectedDurationMonths > 1) "s" else ""}",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$${totalCalculatedUsd.toInt()} USD",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                            Text(
                                text = "📋 Selected Slot: $chosenSlotSummary",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val computedDate = if (selectedDateOption == "Custom Date") customStartDate else selectedDateOption
                    val chosenDaysList = when (selectedFormula.type) {
                        RentalFormulaType.DAY_PER_WEEK -> chosenDaysForDayPerWeek.toList()
                        RentalFormulaType.SHIFT -> chosenDaysForShift.toList()
                        RentalFormulaType.HOURLY -> chosenDaysForHourly.toList()
                        RentalFormulaType.FULL_MONTH -> selectedFormula.daysOfWeek
                    }
                    val startH = when (selectedFormula.type) {
                        RentalFormulaType.SHIFT -> shiftStartHour
                        RentalFormulaType.HOURLY -> hourlyStartHour
                        else -> selectedFormula.startHour
                    }
                    val endH = when (selectedFormula.type) {
                        RentalFormulaType.SHIFT -> shiftEndHour
                        RentalFormulaType.HOURLY -> hourlyEndHour
                        else -> selectedFormula.endHour
                    }

                    // In-App Only Request Button
                    ProOutlinedButton(
                        text = "In-App Request",
                        onClick = {
                            viewModel.submitBookingRequest(
                                space = space,
                                formula = selectedFormula,
                                startDate = computedDate,
                                durationMonths = selectedDurationMonths,
                                notes = clinicalNotes,
                                context = context,
                                alsoOpenWhatsApp = false,
                                selectedDays = chosenDaysList,
                                selectedStartHour = startH,
                                selectedEndHour = endH,
                                selectedShift = if (selectedFormula.type == RentalFormulaType.SHIFT) selectedShiftPreset else "",
                                calculatedTotalUsd = totalCalculatedUsd,
                                subdivisionId = selectedSubdivision?.id,
                                subdivisionName = selectedSubdivision?.name,
                                selectedStrategy = selectedSubStrategy?.strategy?.name
                            )
                            onRequestSubmitted()
                            onDismiss()
                        },
                        icon = Icons.AutoMirrored.Filled.Send,
                        modifier = Modifier.weight(1f)
                    )

                    // Request + WhatsApp Connect Button
                    CustomButton(
                        text = "Send & WhatsApp",
                        onClick = {
                            viewModel.submitBookingRequest(
                                space = space,
                                formula = selectedFormula,
                                startDate = computedDate,
                                durationMonths = selectedDurationMonths,
                                notes = clinicalNotes,
                                context = context,
                                alsoOpenWhatsApp = true,
                                selectedDays = chosenDaysList,
                                selectedStartHour = startH,
                                selectedEndHour = endH,
                                selectedShift = if (selectedFormula.type == RentalFormulaType.SHIFT) selectedShiftPreset else "",
                                calculatedTotalUsd = totalCalculatedUsd,
                                subdivisionId = selectedSubdivision?.id,
                                subdivisionName = selectedSubdivision?.name,
                                selectedStrategy = selectedSubStrategy?.strategy?.name
                            )
                            onRequestSubmitted()
                            onDismiss()
                        },
                        variant = CustomButtonVariant.WHATSAPP,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}
}
