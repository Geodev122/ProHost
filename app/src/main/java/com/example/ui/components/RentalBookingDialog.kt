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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.util.BookingRecurrence
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import java.text.NumberFormat
import java.util.*

private fun recurrenceLabel(recurrence: BookingRecurrence): String = when (recurrence) {
    BookingRecurrence.FLAT -> "Flat"
    BookingRecurrence.ONE_TIME -> "One-time"
    BookingRecurrence.SAME_DAY_EVERY_WEEK -> "Same day, every week"
    BookingRecurrence.SAME_DAY_EVERY_MONTH -> "Same day, every month"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalBookingDialog(
    space: SpaceListing,
    initialFormula: RentalFormula?,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit,
    onRequestSubmitted: () -> Unit,
    replacesBookingId: String? = null
) {
    val context = LocalContext.current
    val allWeekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val hasSubdivisions = space.subdivisions.isNotEmpty()
    var selectedSubdivision by remember {
        mutableStateOf(space.subdivisions.firstOrNull())
    }

    // The single source of truth for what's actually bookable and at what real price —
    // the exact same expansion the host's Availability Control editor and the
    // specialist's own Availability Matrix already use (SpaceCalculationUtils
    // .buildAllSlotsForSpace), so this dialog can never show/charge a different number
    // than what the specialist already saw before tapping "Request."
    val allSlots = remember(space) { SpaceCalculationUtils.buildAllSlotsForSpace(space) }
    val scopedSlots = remember(allSlots, hasSubdivisions, selectedSubdivision) {
        if (hasSubdivisions) {
            val subId = selectedSubdivision?.id
            if (subId != null) allSlots.filter { it.sourceFormulaId == subId } else emptyList()
        } else {
            allSlots
        }
    }
    val availableStrategyTypes = remember(scopedSlots) {
        scopedSlots.mapNotNull { it.strategyType }.distinct()
    }
    var selectedStrategyType by remember(availableStrategyTypes) {
        mutableStateOf(
            // Best-effort: land on the strategy matching a rebook/edit's prior formula
            // when it's still actually offered; otherwise just the first real option.
            initialFormula?.type?.let { legacyType ->
                availableStrategyTypes.firstOrNull { SpaceCalculationUtils.legacyFormulaType(it) == legacyType }
            } ?: availableStrategyTypes.firstOrNull()
        )
    }
    val strategySlots = remember(scopedSlots, selectedStrategyType) {
        scopedSlots.filter { it.strategyType == selectedStrategyType }
    }

    // --- Hourly: pick a day, then one or more real priced cells for that day ---
    var hourlyDay by remember(strategySlots) {
        mutableStateOf(strategySlots.firstOrNull()?.day ?: allWeekDays.first())
    }
    val hourlyDayOptions = remember(strategySlots) { strategySlots.map { it.day }.distinct() }
    val hourlyCellsForDay = remember(strategySlots, hourlyDay) {
        strategySlots.filter { it.day == hourlyDay }.sortedBy { it.startTime }
    }
    var selectedHourlyCells by remember(hourlyDay) { mutableStateOf(setOf<RentableSlot>()) }

    // --- Shift-Based: pick a day, a real shift offered that day, and a recurrence ---
    var shiftDay by remember(strategySlots) {
        mutableStateOf(strategySlots.firstOrNull()?.day ?: allWeekDays.first())
    }
    val shiftDayOptions = remember(strategySlots) { strategySlots.map { it.day }.distinct() }
    val shiftsForDay = remember(strategySlots, shiftDay) { strategySlots.filter { it.day == shiftDay } }
    var selectedShiftSlot by remember(shiftsForDay) { mutableStateOf(shiftsForDay.firstOrNull()) }
    var shiftRecurrence by remember(selectedShiftSlot) {
        mutableStateOf(selectedShiftSlot?.pricesByRecurrence?.keys?.firstOrNull() ?: BookingRecurrence.SAME_DAY_EVERY_WEEK)
    }

    // --- Day-Based: pick a recurrence, then one or more real priced days for it ---
    var dayBasedRecurrence by remember(strategySlots) {
        mutableStateOf(
            strategySlots.flatMap { it.pricesByRecurrence.keys }.distinct().firstOrNull()
                ?: BookingRecurrence.SAME_DAY_EVERY_WEEK
        )
    }
    val dayBasedDayOptions = remember(strategySlots, dayBasedRecurrence) {
        strategySlots.filter { it.pricesByRecurrence.containsKey(dayBasedRecurrence) }
    }
    var selectedDayBasedDays by remember(dayBasedRecurrence) { mutableStateOf(setOf<String>()) }

    // The real, non-fabricated selection driving both price and what gets submitted —
    // one branch per strategy, each sourced from real RentableSlots above.
    val selectedSlotsForPricing: List<RentableSlot> = when (selectedStrategyType) {
        RentalStrategyType.MONTHLY -> strategySlots
        RentalStrategyType.HOURLY -> selectedHourlyCells.toList()
        RentalStrategyType.SHIFT_BASED -> listOfNotNull(selectedShiftSlot)
        RentalStrategyType.DAY_BASED -> dayBasedDayOptions.filter { it.day in selectedDayBasedDays }
        null -> emptyList()
    }

    // Monthly is the only strategy whose real price scales with a duration commitment
    // (SpaceCalculationUtils.calculateTotalRentalPrice) — Hourly/Shift/Day-Based prices
    // already represent the full cost of the chosen recurrence, so a duration selector
    // for them would just be lying about what the total actually is.
    val durationOptions = listOf(1, 2, 3, 6, 12)
    var selectedDurationMonths by remember { mutableStateOf(1) }
    val effectiveRecurrence = when (selectedStrategyType) {
        RentalStrategyType.SHIFT_BASED -> shiftRecurrence
        RentalStrategyType.DAY_BASED -> dayBasedRecurrence
        else -> BookingRecurrence.FLAT
    }
    val totalCalculatedUsd = remember(selectedSlotsForPricing, effectiveRecurrence, selectedDurationMonths, selectedStrategyType) {
        SpaceCalculationUtils.calculateTotalRentalPrice(selectedSlotsForPricing, effectiveRecurrence, selectedDurationMonths)
    }

    // Start Date
    val dateOptions = listOf("Immediate (Tomorrow)", "Next Monday", "1st of Next Month", "Custom Date")
    var selectedDateOption by remember { mutableStateOf(dateOptions[0]) }
    var customStartDate by remember { mutableStateOf("2026-09-01") }

    var clinicalNotes by remember { mutableStateOf("") }

    val chosenSlotSummary = remember(selectedStrategyType, selectedSlotsForPricing, effectiveRecurrence) {
        when (selectedStrategyType) {
            RentalStrategyType.MONTHLY -> {
                val m = if (hasSubdivisions) selectedSubdivision?.pricing?.monthly else space.pricing.monthly
                if (m?.isIndefinite == true) "Full month, indefinite" else "Full month"
            }
            RentalStrategyType.HOURLY -> {
                if (selectedSlotsForPricing.isEmpty()) "No hours selected yet"
                else selectedSlotsForPricing.sortedBy { it.startTime }
                    .joinToString(", ") { "${it.day} ${it.startTime}-${it.endTime}" }
            }
            RentalStrategyType.SHIFT_BASED -> {
                val slot = selectedSlotsForPricing.firstOrNull()
                if (slot == null) "No shift selected yet"
                else "${slot.label} • ${recurrenceLabel(effectiveRecurrence)}"
            }
            RentalStrategyType.DAY_BASED -> {
                if (selectedSlotsForPricing.isEmpty()) "No days selected yet"
                else "${selectedSlotsForPricing.joinToString(", ") { it.day }} • ${recurrenceLabel(effectiveRecurrence)}"
            }
            null -> "No availability configured for this ${if (hasSubdivisions) "room" else "space"} yet"
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
                                text = "Rental Request",
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "Real-Time Availability")
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

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Choose Subdivision (if any)
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
                                    Row(
                                        modifier = Modifier.padding(Spacing.md).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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

                    // 2. Choose Renting Strategy — only strategies with real, priced
                    // availability actually appear (a strategy the host never
                    // configured never shows up as a fake option).
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${if (hasSubdivisions) "2" else "1"}. Select Renting Strategy",
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (availableStrategyTypes.isEmpty()) {
                            Text(
                                text = "This ${if (hasSubdivisions) "room" else "space"} has no bookable availability configured yet.",
                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableStrategyTypes.forEach { strategy ->
                                    FilterChip(
                                        selected = selectedStrategyType == strategy,
                                        onClick = { selectedStrategyType = strategy },
                                        label = { Text(strategy.displayName, fontSize = MaterialTheme.typography.labelMedium.fontSize) }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Customize Your Required Availability — driven entirely by the
                    // real RentableSlots for the chosen strategy, never a hardcoded
                    // preset list disconnected from what the host actually configured.
                    if (selectedStrategyType != null) {
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
                                        text = "Customize Your Required Availability",
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                when (selectedStrategyType) {
                                    RentalStrategyType.MONTHLY -> {
                                        val rate = strategySlots.firstOrNull()?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0
                                        Text(
                                            text = "Exclusive full-space access on all operating days (${strategySlots.map { it.day }.distinct().joinToString(", ")}), " +
                                                "billed at $${rate.toInt()} USD per month.",
                                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    RentalStrategyType.HOURLY -> {
                                        Text("Choose a day:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            hourlyDayOptions.forEach { day ->
                                                FilterChip(
                                                    selected = hourlyDay == day,
                                                    onClick = { hourlyDay = day },
                                                    label = { Text(day, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        Text("Choose one or more priced hours:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                        if (hourlyCellsForDay.isEmpty()) {
                                            Text("No priced hours on $hourlyDay.", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.error)
                                        }
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            hourlyCellsForDay.forEach { cell ->
                                                val isSelected = cell in selectedHourlyCells
                                                val price = cell.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedHourlyCells = if (isSelected) selectedHourlyCells - cell else selectedHourlyCells + cell
                                                    },
                                                    label = { Text("${cell.startTime} · $${price.toInt()}", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                                )
                                            }
                                        }
                                    }

                                    RentalStrategyType.SHIFT_BASED -> {
                                        Text("Choose a day:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            shiftDayOptions.forEach { day ->
                                                FilterChip(
                                                    selected = shiftDay == day,
                                                    onClick = { shiftDay = day },
                                                    label = { Text(day, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        Text("Choose a shift:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                        if (shiftsForDay.isEmpty()) {
                                            Text("No shifts offered on $shiftDay.", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.error)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            shiftsForDay.forEach { slot ->
                                                FilterChip(
                                                    selected = selectedShiftSlot == slot,
                                                    onClick = { selectedShiftSlot = slot },
                                                    label = { Text(slot.groupLabel.substringAfter("• "), fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        val shiftRecurrenceOptions = selectedShiftSlot?.pricesByRecurrence?.keys?.toList().orEmpty()
                                        if (shiftRecurrenceOptions.isNotEmpty()) {
                                            Text("Choose a commitment:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                shiftRecurrenceOptions.forEach { rec ->
                                                    val price = selectedShiftSlot?.pricesByRecurrence?.get(rec) ?: 0.0
                                                    FilterChip(
                                                        selected = shiftRecurrence == rec,
                                                        onClick = { shiftRecurrence = rec },
                                                        label = { Text("${recurrenceLabel(rec)} · $${price.toInt()}", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    RentalStrategyType.DAY_BASED -> {
                                        val dayBasedRecurrenceOptions = strategySlots.flatMap { it.pricesByRecurrence.keys }.distinct()
                                        if (dayBasedRecurrenceOptions.isNotEmpty()) {
                                            Text("Choose a commitment:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                dayBasedRecurrenceOptions.forEach { rec ->
                                                    FilterChip(
                                                        selected = dayBasedRecurrence == rec,
                                                        onClick = { dayBasedRecurrence = rec },
                                                        label = { Text(recurrenceLabel(rec), fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                        Text("Choose one or more priced days:", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                                        if (dayBasedDayOptions.isEmpty()) {
                                            Text("No days priced for this commitment.", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.error)
                                        }
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            dayBasedDayOptions.forEach { slot ->
                                                val isSelected = slot.day in selectedDayBasedDays
                                                val price = slot.pricesByRecurrence[dayBasedRecurrence] ?: 0.0
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedDayBasedDays = if (isSelected) selectedDayBasedDays - slot.day else selectedDayBasedDays + slot.day
                                                    },
                                                    label = { Text("${slot.day} · $${price.toInt()}", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                                )
                                            }
                                        }
                                    }

                                    null -> {}
                                }
                            }
                        }
                    }

                    // 4. Start Date Selector
                    Column {
                        Text(
                            text = "Select Starting Date",
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

                    // 5. Rental Duration Term — Monthly only; every other strategy's
                    // real price already represents the full cost of the chosen
                    // recurrence (see effectiveRecurrence above).
                    if (selectedStrategyType == RentalStrategyType.MONTHLY) {
                        Column {
                            Text(
                                text = "Rental Duration Term",
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
                    }

                    // 6. Notes & Scope
                    Column {
                        Text(
                            text = "Requirements & Notes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        InputField(
                            value = clinicalNotes,
                            onValueChange = { clinicalNotes = it },
                            label = "",
                            placeholder = "Intended use, team size & special equipment needed...",
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
                                        text = "Total for this request",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Based on your selection above",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Text(
                                    text = "$${totalCalculatedUsd.toInt()} USD",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                    val canSubmit = selectedStrategyType != null && selectedSlotsForPricing.isNotEmpty()

                    fun buildFormulaForSubmission(): RentalFormula? {
                        // Same synthesis SpaceDetailsScreen's preview and
                        // SpaceAvailabilityMatrixView's tap-to-book cells use — see its
                        // doc comment for why this is shared rather than three
                        // independent approximations. Only the description is
                        // overridden here, since this dialog already has a more
                        // specific, live-updating summary than the shared helper's
                        // generic slot label.
                        return SpaceCalculationUtils.representativeFormula(selectedSlotsForPricing, effectiveRecurrence)
                            ?.copy(scheduleDescription = chosenSlotSummary)
                    }

                    // In-App Only Request Button
                    ProOutlinedButton(
                        text = "Request",
                        onClick = {
                            val formula = buildFormulaForSubmission()
                            if (formula == null) {
                                Toast.makeText(context, "Please select an available slot first.", Toast.LENGTH_SHORT).show()
                                return@ProOutlinedButton
                            }
                            viewModel.submitBookingRequest(
                                space = space,
                                formula = formula,
                                startDate = computedDate,
                                durationMonths = if (selectedStrategyType == RentalStrategyType.MONTHLY) selectedDurationMonths else 1,
                                notes = clinicalNotes,
                                context = context,
                                alsoOpenWhatsApp = false,
                                selectedDays = formula.daysOfWeek,
                                selectedStartHour = formula.startHour,
                                selectedEndHour = formula.endHour,
                                selectedShift = if (selectedStrategyType == RentalStrategyType.SHIFT_BASED) formula.shiftName else "",
                                calculatedTotalUsd = totalCalculatedUsd,
                                subdivisionId = selectedSubdivision?.id,
                                subdivisionName = selectedSubdivision?.name,
                                selectedStrategy = selectedStrategyType?.name,
                                replacesBookingId = replacesBookingId
                            )
                            onRequestSubmitted()
                            onDismiss()
                        },
                        icon = Icons.AutoMirrored.Filled.Send,
                        compact = true,
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
                    )

                    // Request + WhatsApp Connect Button
                    CustomButton(
                        text = "Request and contact",
                        onClick = {
                            val formula = buildFormulaForSubmission() ?: return@CustomButton
                            viewModel.submitBookingRequest(
                                space = space,
                                formula = formula,
                                startDate = computedDate,
                                durationMonths = if (selectedStrategyType == RentalStrategyType.MONTHLY) selectedDurationMonths else 1,
                                notes = clinicalNotes,
                                context = context,
                                alsoOpenWhatsApp = true,
                                selectedDays = formula.daysOfWeek,
                                selectedStartHour = formula.startHour,
                                selectedEndHour = formula.endHour,
                                selectedShift = if (selectedStrategyType == RentalStrategyType.SHIFT_BASED) formula.shiftName else "",
                                calculatedTotalUsd = totalCalculatedUsd,
                                subdivisionId = selectedSubdivision?.id,
                                subdivisionName = selectedSubdivision?.name,
                                selectedStrategy = selectedStrategyType?.name,
                                replacesBookingId = replacesBookingId
                            )
                            onRequestSubmitted()
                            onDismiss()
                        },
                        variant = CustomButtonVariant.WHATSAPP,
                        iconPainter = painterResource(id = R.drawable.ic_whatsapp),
                        compact = true,
                        enabled = canSubmit,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}
