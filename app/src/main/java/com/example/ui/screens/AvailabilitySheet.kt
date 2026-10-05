package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.BookingRecurrence
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import com.example.util.ShareLinks
import kotlinx.coroutines.launch

/**
 * The "Check Availability" sheet of a listing: room folder tabs, per-strategy day bars,
 * slot selection, the attendee block and the send confirmation. State is owned by
 * SpaceDetailsScreenContent (so the page, bottom strip and sheet share one selection)
 * and handed in as MutableState holders; room choice and sending go back through the
 * screen's selectRoom / requestWithRequirements / sendMultiSlotRequest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvailabilitySheet(
    liveSpace: SpaceListing,
    viewModel: ProHostViewModel,
    acceptedBookings: List<RentalBookingRequest>,
    architectureSchema: SpaceArchitectureSchema,
    strategyPreviewGroups: List<Pair<RentalStrategyType, List<RentableSlot>>>,
    isSpecialistViewer: Boolean,
    selectedSubdivisionId: String?,
    availabilitySheetState: SheetState,
    isSendingSlotRequest: Boolean,
    availabilityPanelStateHolder: MutableState<String>,
    selectedSlotsState: MutableState<Set<RentableSlot>>,
    selectedHoursPerDayState: MutableState<Map<String, Set<String>>>,
    showSendConfirmState: MutableState<Boolean>,
    onSelectRoom: (Subdivision) -> Unit,
    onRequest: () -> Unit,
    onSendRequest: (
        slots: List<RentableSlot>,
        hourly: Map<String, Set<String>>,
        all: List<RentableSlot>,
        attendeeQuote: com.example.ui.util.AttendeePricing.Quote?
    ) -> Unit
) {
    val context = LocalContext.current
    var availabilityPanelState by availabilityPanelStateHolder
    var selectedSlots by selectedSlotsState
    var selectedHoursPerDay by selectedHoursPerDayState
    var showSendConfirm by showSendConfirmState
    fun selectRoom(sub: Subdivision) = onSelectRoom(sub)
    fun requestWithRequirements() = onRequest()
    fun sendMultiSlotRequest(
        slotsToSend: List<RentableSlot>,
        hourlySelections: Map<String, Set<String>>,
        allAvailableSlots: List<RentableSlot>,
        attendeeQuote: com.example.ui.util.AttendeePricing.Quote? = null
    ) = onSendRequest(slotsToSend, hourlySelections, allAvailableSlots, attendeeQuote)
    val sheetSlotGroups = remember(strategyPreviewGroups, selectedSubdivisionId) {
        if (selectedSubdivisionId != null) {
            strategyPreviewGroups.map { (type, slots) ->
                type to slots.filter { it.sourceFormulaId == selectedSubdivisionId }
            }.filter { (_, slots) -> slots.isNotEmpty() }
        } else {
            strategyPreviewGroups
        }
    }
    val selectedSubdivision = remember(selectedSubdivisionId, liveSpace) {
        if (selectedSubdivisionId != null) liveSpace.subdivisions.find { it.id == selectedSubdivisionId } else null
    }
    val attendeeSub = selectedSubdivision?.takeIf { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
    val isAttendeeMode = attendeeSub != null
    val attendeeTiers = remember(attendeeSub, architectureSchema) {
        attendeeSub?.let { com.example.ui.util.AttendeePricing.tiersFor(it, architectureSchema.attendeePackages) } ?: emptyList()
    }
    val attendeeMin = attendeeSub?.let { com.example.ui.util.AttendeePricing.minAttendees(it, attendeeTiers) } ?: 1
    val attendeeMax = attendeeSub?.let { com.example.ui.util.AttendeePricing.maxAttendees(it, attendeeTiers) }

    // Per-strategy, per-day expand state
    var expandedDaysByStrategy by remember(sheetSlotGroups) { mutableStateOf(mapOf<RentalStrategyType, Set<String>>()) }
    // Attendee mode fields (scoped to sheet lifetime)
    var sheetAttendeeCount by remember(attendeeSub?.id) { mutableStateOf(attendeeMin) }
    val sheetAttendeeQuote = remember(attendeeSub, sheetAttendeeCount, architectureSchema) {
        attendeeSub?.let { com.example.ui.util.AttendeePricing.quote(it, sheetAttendeeCount, architectureSchema.attendeePackages) }
    }

    val dayOrder = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday","Mon","Tue","Wed","Thu","Fri","Sat","Sun")

    // Total selected slot count across strategies
    val totalSelectedSlots = selectedSlots.size + selectedHoursPerDay.values.sumOf { it.size }
    val hasSelection = totalSelectedSlots > 0

    val canSubmit = hasSelection && (!isAttendeeMode || sheetAttendeeQuote != null)

    ResizableBottomSheet(
        onDismissRequest = {
            availabilityPanelState = if (selectedSubdivisionId != null) "peek" else "hidden"
            selectedSlots = emptySet()
            selectedHoursPerDay = emptyMap()
        },
        modifier = Modifier.shadow(16.dp, SheetShape),
        sheetState = availabilitySheetState,
        // Blue wash (matches the "Press to see option availability" trigger bar)
        // so the trigger and the sheet it opens read as one consistent design.
        accentTint = true,
        // Pinned and draggable: resize the sheet from its title; the rest scrolls.
        header = {
            ProSectionHeader(
                title = "Check Availability",
                subtitle = if (isAttendeeMode) "Enter attendee count, then tap days to select slots." else "Tap a day to expand and select open slots.",
                icon = Icons.Default.EventAvailable,
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Same folder tabs as the page: switching here switches the room the
            // sheet shows (and drops slots picked for the previous room).
            if (liveSpace.subdivisions.size > 1 && selectedSubdivisionId != null) {
                RoomFolderTabs(
                    rooms = liveSpace.subdivisions,
                    selectedId = selectedSubdivisionId.orEmpty(),
                    onSelect = { selectRoom(it) }
                )
            }

            // ── Attendee mode block ──────────────────────────────────────
            if (isAttendeeMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Attendee Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        com.example.ui.components.AttendeeCountAndTiers(
                            count = sheetAttendeeCount,
                            min = attendeeMin,
                            max = attendeeMax,
                            tiers = attendeeTiers,
                            quote = sheetAttendeeQuote,
                            onCountChange = { sheetAttendeeCount = it }
                        )
                    }
                }
            }

            if (sheetSlotGroups.isEmpty()) {
                Text("No slots configured for this space.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sheetSlotGroups.forEach { (strategyType, slots) ->
                    // Strategy section label
                    Text(
                        if (isAttendeeMode) "Availability — ${strategyType.displayName}" else strategyType.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Group slots by day
                    val slotsByDay = slots.groupBy { it.day }.toList()
                        .sortedBy { (day, _) -> dayOrder.indexOfFirst { it.equals(day, ignoreCase = true) }.let { if (it >= 0) it else 99 } }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        slotsByDay.forEach { (day, daySlots) ->
                            val lockedInDay = daySlots.count { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                            val availInDay = daySlots.size - lockedInDay
                            val isFull = availInDay == 0
                            val expandedDays = expandedDaysByStrategy[strategyType] ?: emptySet()
                            val isDayExpanded = expandedDays.contains(day)

                            // ── Day bar ──────────────────────────────────────
                            Surface(
                                onClick = {
                                    if (!isFull) {
                                        val current = expandedDaysByStrategy[strategyType] ?: emptySet()
                                        expandedDaysByStrategy = expandedDaysByStrategy + (strategyType to
                                            if (isDayExpanded) current - day else current + day)
                                    }
                                },
                                enabled = !isFull,
                                color = when {
                                    isFull -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    isDayExpanded -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = if (isDayExpanded) RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp) else MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Day name
                                    Text(
                                        day,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFull) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            else if (isDayExpanded) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Badges row
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isFull) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("FULL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        } else {
                                            // Green available badge
                                            Surface(color = MaterialTheme.proColors.success.copy(alpha = 0.15f), shape = CircleShape) {
                                                Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.proColors.success))
                                                    Text(
                                                        "$availInDay",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.proColors.success
                                                    )
                                                }
                                            }
                                            // Red rented badge
                                            if (lockedInDay > 0) {
                                                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = CircleShape) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                                        Text(
                                                            "$lockedInDay",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                            Icon(
                                                imageVector = if (isDayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isDayExpanded) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Expanded day body (available slots only) ──────
                            if (isDayExpanded && !isFull) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val availableSlots = daySlots.filter { !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                        when (strategyType) {
                                            RentalStrategyType.MONTHLY -> {
                                                availableSlots.forEach { slot ->
                                                    val isSelected = selectedSlots.contains(slot)
                                                    Surface(
                                                        onClick = { selectedSlots = if (isSelected) selectedSlots - slot else selectedSlots + slot },
                                                        color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.18f) else MaterialTheme.proColors.successContainer,
                                                        shape = MaterialTheme.shapes.small,
                                                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.proColors.success) else null
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                            Icon(
                                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.EventAvailable,
                                                                contentDescription = null,
                                                                tint = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Column {
                                                                Text(
                                                                    if (isSelected) "Selected — Monthly Lease" else "Available — Monthly Lease",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
                                                                )
                                                                Text(
                                                                    "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}${SpaceCalculationUtils.strategyUnitLabel(RentalStrategyType.MONTHLY)}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            RentalStrategyType.HOURLY -> {
                                                Text(
                                                    "Select time slots for $day:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    items(availableSlots.sortedBy { it.startTime }) { slot ->
                                                        val isSelected = selectedHoursPerDay[day]?.contains(slot.startTime) == true
                                                        Surface(
                                                            onClick = {
                                                                val current = selectedHoursPerDay[day] ?: emptySet()
                                                                selectedHoursPerDay = selectedHoursPerDay + (day to
                                                                    if (isSelected) current - slot.startTime else current + slot.startTime)
                                                            },
                                                            color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.22f) else MaterialTheme.proColors.successContainer,
                                                            shape = MaterialTheme.shapes.extraSmall,
                                                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.proColors.success) else null
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Text(
                                                                    slot.startTime,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
                                                                )
                                                                Text(
                                                                    if (isAttendeeMode) "Open" else "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (isSelected) {
                                                                        MaterialTheme.proColors.success.copy(alpha = 0.8f)
                                                                    } else {
                                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            RentalStrategyType.SHIFT_BASED -> {
                                                availableSlots.forEach { slot ->
                                                    val isSelected = selectedSlots.contains(slot)
                                                    val displayLabel = slot.label
                                                        .replace(
                                                            Regex("^${Regex.escape(day)}\\s*[-–•]?\\s*", RegexOption.IGNORE_CASE),
                                                            ""
                                                        ).trim().ifBlank { slot.label }
                                                    Surface(
                                                        onClick = { selectedSlots = if (isSelected) selectedSlots - slot else selectedSlots + slot },
                                                        color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                                        shape = MaterialTheme.shapes.small,
                                                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                                                            if (isSelected) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 12.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier.size(8.dp).clip(CircleShape)
                                                                        .background(if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success)
                                                                )
                                                                Text(
                                                                    displayLabel,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                                )
                                                            }
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    if (isAttendeeMode) "Open" else "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                Text(
                                                                    if (isSelected) "Selected" else "Available",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            RentalStrategyType.DAY_BASED -> {
                                                availableSlots.forEach { slot ->
                                                    val isSelected = selectedSlots.contains(slot)
                                                    Surface(
                                                        onClick = { selectedSlots = if (isSelected) selectedSlots - slot else selectedSlots + slot },
                                                        color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                                        shape = MaterialTheme.shapes.small,
                                                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                                                            if (isSelected) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 12.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier.size(8.dp).clip(CircleShape)
                                                                        .background(if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success)
                                                                )
                                                                Text(slot.day, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                            }
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    if (isAttendeeMode) "Open" else "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                Text(
                                                                    if (isSelected) "Selected" else "Available",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Action bar ───────────────────────────────────────────────
                if (!isSpecialistViewer) {
                    Text(
                        "Viewing as host — only professionals can request slots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (canSubmit) {
                    val slotLabel = if (totalSelectedSlots == 1) "1 Slot" else "$totalSelectedSlots Slots"
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { requestWithRequirements() },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Request $slotLabel", fontWeight = FontWeight.Bold)
                        }
                        // WhatsApp: compose structured message listing all selected slots
                        Button(
                            onClick = {
                                val slotLines = buildString {
                                    selectedSlots.forEachIndexed { i, s ->
                                        val unit = s.strategyType?.let { SpaceCalculationUtils.strategyUnitLabel(it) } ?: ""
                                        val priceSuffix = if (isAttendeeMode) "" else s.pricesByRecurrence[BookingRecurrence.FLAT]?.let { " — \$${it.toInt()}$unit" } ?: ""
                                        appendLine("  ${i + 1}. ${s.label}$priceSuffix")
                                    }
                                    selectedHoursPerDay.entries.forEachIndexed { di, (day, hrs) ->
                                        hrs.forEachIndexed { hi, hr ->
                                            appendLine("  ${selectedSlots.size + di * 100 + hi + 1}. $day $hr")
                                        }
                                    }
                                }.trimEnd()
                                val attendeeBlock = sheetAttendeeQuote?.let { q ->
                                    "\n\n👥 ${com.example.ui.util.AttendeePricing.describe(q)}" +
                                        "\n💰 Total for the booking: \$${com.example.ui.util.AttendeePricing.formatUsd(q.totalUsd)} USD"
                                } ?: ""
                                val message = "Hello! I'm interested in booking *${liveSpace.title}*.\n\n📍" +
                                    " ${liveSpace.district}, ${liveSpace.governorate.displayName}" +
                                    "\n\n🗓 Selected Slots ($totalSelectedSlots):\n$slotLines${attendeeBlock}" +
                                    "\n\nAre these slots still available?"
                                try {
                                    val whatsappUrl = "https://wa.me/${viewModel.formatWhatsAppNumber(liveSpace.ownerPhone)}" +
                                        "?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(whatsappUrl)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

            }
        }
    }

    // ── Confirm-and-send popup ───────────────────────────────────────────
    if (showSendConfirm) {
        val selectedSubdivisionForDialog = if (selectedSubdivisionId != null)
            liveSpace.subdivisions.find { it.id == selectedSubdivisionId } else null
        val allSheetSlots = sheetSlotGroups.flatMap { (_, s) -> s }
        val dialogQuote = if (com.example.ui.util.AttendeePricing.isPerAttendee(selectedSubdivisionForDialog)) sheetAttendeeQuote else null

        val totalCostForDialog = if (dialogQuote != null)
            dialogQuote.totalUsd
        else
            selectedSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 } +
            selectedHoursPerDay.entries.sumOf { (day, hrs) ->
                allSheetSlots.filter { it.day == day && it.startTime in hrs }.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
            }

        ProHostDialog(
            onDismissRequest = { if (!isSendingSlotRequest) showSendConfirm = false },
            icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
            title = { Text("Confirm Your Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Space / location / room summary
                    listOf(
                        "Space" to liveSpace.title,
                        "Location" to "${liveSpace.district}, ${liveSpace.governorate.displayName}",
                        "Room" to (selectedSubdivisionForDialog?.name ?: "Whole Space")
                    ).forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider()
                    // Slot summary
                    val allSelectedSlotLabels = buildList {
                        selectedSlots.forEach { add(it.label) }
                        selectedHoursPerDay.forEach { (day, hrs) -> hrs.forEach { hr -> add("$day $hr") } }
                    }
                    Text("Selected Slots (${allSelectedSlotLabels.size})",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    allSelectedSlotLabels.take(5).forEach { label ->
                        Text("• $label", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (allSelectedSlotLabels.size > 5) {
                        Text("+ ${allSelectedSlotLabels.size - 5} more slots", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Attendee block
                    if (dialogQuote != null) {
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Attendees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${dialogQuote.attendees} people", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Price", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$${com.example.ui.util.AttendeePricing.formatUsd(dialogQuote.tier.pricePerAttendeeUsd)}/person" +
                                    dialogQuote.tier.name.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimated Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$${String.format("%.2f", totalCostForDialog)} USD",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendMultiSlotRequest(
                            slotsToSend = selectedSlots.toList(),
                            hourlySelections = selectedHoursPerDay,
                            allAvailableSlots = sheetSlotGroups.flatMap { (_, s) -> s },
                            attendeeQuote = dialogQuote
                        )
                    },
                    enabled = !isSendingSlotRequest
                ) {
                    if (isSendingSlotRequest) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Send")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirm = false }, enabled = !isSendingSlotRequest) { Text("Cancel") }
            }
        )
    }

}
