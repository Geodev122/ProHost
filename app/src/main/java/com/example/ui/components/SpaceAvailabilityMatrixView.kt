package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusOnErrorContainer
import com.example.ui.theme.StatusOnSuccessContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.util.BookingRecurrence
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils

/**
 * The Specialist-facing "what can I actually book" view. Used to be a fixed
 * Mon-Sun x Morning/Afternoon/Evening grid completely unrelated to the space's
 * real formulas — tapping an "open" cell synthesized an approximate formula on
 * the spot. Now renders the exact same derived slots
 * (SpaceCalculationUtils.buildRentableSlots) the host's own Availability Control
 * editor uses for its on/off toggles, grouped by day, so what a Specialist sees
 * here and what the host actually configured can never disagree.
 */
@Composable
fun SpaceAvailabilityMatrixView(
    space: SpaceListing,
    acceptedBookings: List<RentalBookingRequest>,
    onCellClicked: (formula: RentalFormula, day: String, shiftName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSlotInfo by remember { mutableStateOf<Pair<RentableSlot, Boolean>?>(null) } // (slot, isBooked)


    // buildAllSlotsForSpace expands the space's own pricing when it has no
    // subdivisions, or every subdivision's pricing when it does — fixing the bug
    // where this view (and SpaceScheduleEditorDialog) only ever read
    // space.rentalFormulas and so showed "no rentable slots" for every
    // Center/Polyclinic/Co-working listing, regardless of what was actually
    // configured on its subdivisions.
    val derivedSlots = remember(space) {
        SpaceCalculationUtils.buildAllSlotsForSpace(space)
    }

    // A slot the host switched off in Availability Control (SpaceScheduleEditorDialog)
    // is hidden here too — it was never really offered, so it shouldn't show as
    // either "open" or "booked".
    val offeredSlots = remember(derivedSlots, space.schedule.blackoutSlots) {
        derivedSlots.filterNot { slot ->
            space.schedule.blackoutSlots.any {
                it.dayOfWeek.equals(slot.day, ignoreCase = true) &&
                    it.startTime == slot.startTime && it.endTime == slot.endTime
            }
        }
    }

    // "Fully Occupied" now means every offered slot is locked by an accepted
    // booking under the shared rule — not "someone booked a full month of any
    // room", which greyed out the whole card for multi-room spaces.
    val isFullyBooked = remember(offeredSlots, acceptedBookings, space.id) {
        offeredSlots.isNotEmpty() && offeredSlots.all {
            SpaceCalculationUtils.isSlotLocked(it, space.id, acceptedBookings)
        }
    }

    val slotsByDay = remember(offeredSlots) {
        val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        offeredSlots.groupBy { it.day }
            .toList()
            .sortedBy { (day, _) -> dayOrder.indexOf(day).let { if (it < 0) dayOrder.size else it } }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Weekly Availability",
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "The real slots this space's formulas offer",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isFullyBooked) {
                    Surface(
                        color = StatusErrorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Fully Occupied",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusOnErrorContainer,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = StatusSuccessContainer, label = "Open")
                LegendItem(color = StatusErrorContainer, label = "Booked")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (offeredSlots.isEmpty()) {
                Text(
                    text = "This host hasn't published any rentable slots yet.",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // One row per day; within a day, one chip per real slot the space's
                // formulas actually produce — an Hourly formula can mean many chips,
                // a Shift/Day-per-Week/Full-Month formula means one.
                slotsByDay.forEach { (day, slotsForDay) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = day,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            slotsForDay.forEach { slot ->
                                // One shared lock rule with RentalBookingDialog and the host's
                                // accept check (SpaceCalculationUtils.isSlotLocked) — scoped to
                                // the slot's own room, so a full-month booking of Room A no longer
                                // greys out Room B here.
                                val isBooked = SpaceCalculationUtils.isSlotLocked(slot, space.id, acceptedBookings)
                                val cellColor = if (isBooked) StatusErrorContainer else StatusSuccessContainer
                                val borderColor = if (isBooked) StatusError.copy(alpha = 0.5f) else StatusSuccess.copy(alpha = 0.5f)
                                val textColor = if (isBooked) StatusOnErrorContainer else StatusOnSuccessContainer

                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(cellColor)
                                        .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                                        .clickable {
                                            selectedSlotInfo = slot to isBooked
                                            if (!isBooked) {
                                                // Real subdivision/strategy resolution happens inside
                                                // RentalBookingDialog itself whenever space.subdivisions is
                                                // non-empty — this only needs to be a real, non-null,
                                                // approximately-right formula to open that dialog on the
                                                // right strategy/day. Same shared synthesis SpaceDetailsScreen's
                                                // preview and RentalBookingDialog's own submission use, so a
                                                // tapped cell's price always matches the same slot everywhere.
                                                val matchingFormula = SpaceCalculationUtils.representativeFormula(
                                                    listOf(slot), BookingRecurrence.FLAT
                                                ) ?: RentalFormula(
                                                    id = slot.sourceFormulaId,
                                                    type = RentalFormulaType.FULL_MONTH,
                                                    rateUsd = 0.0,
                                                    scheduleDescription = slot.label,
                                                    daysOfWeek = listOf(slot.day),
                                                    startHour = slot.startTime,
                                                    endHour = slot.endTime
                                                )
                                                onCellClicked(matchingFormula, slot.day, matchingFormula.shiftName)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isBooked) Icons.Default.Lock else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = textColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${slot.startTime}-${slot.endTime}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selection feedback banner
            selectedSlotInfo?.let { (slot, isBooked) ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${slot.day} ${slot.startTime}-${slot.endTime}: ${if (isBooked) "Currently Reserved" else "Available for booking"}",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = { selectedSlotInfo = null }) {
                            Text("Dismiss", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
                .border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
        )
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
