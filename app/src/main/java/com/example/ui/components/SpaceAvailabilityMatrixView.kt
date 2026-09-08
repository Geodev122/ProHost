package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusOnErrorContainer
import com.example.ui.theme.StatusOnSuccessContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarningContainer
import com.example.ui.theme.Spacing

@Composable
fun WeeklyAvailabilityMatrix(
    space: SpaceListing,
    acceptedBookings: List<RentalBookingRequest>,
    onCellClicked: (formula: RentalFormula, day: String, shiftName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val shifts = listOf(
        Triple("Morning", "08:00 - 13:00", 0.3),
        Triple("Afternoon", "13:30 - 18:30", 0.35),
        Triple("Evening", "19:00 - 23:00", 0.35)
    )

    var selectedCellInfo by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) } // (Day, Shift, isBooked)

    // Check if full month booking exists
    val isFullMonthBooked = acceptedBookings.any { 
        it.status == BookingRequestStatus.ACCEPTED && it.formula.type == RentalFormulaType.FULL_MONTH 
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
                            text = "Weekly Availability Matrix",
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Live Heatmap (Morning, Afternoon, Evening)",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = if (isFullMonthBooked) StatusErrorContainer else StatusSuccessContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isFullMonthBooked) "Fully Occupied" else "Multi-Tenant Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFullMonthBooked) StatusOnErrorContainer else StatusOnSuccessContainer,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    )
                }
            }

            // Heatmap Color Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = StatusSuccessContainer, label = "Open (0%)")
                LegendItem(color = StatusWarningContainer, label = "Moderate")
                LegendItem(color = StatusErrorContainer, label = "Reserved (100%)")
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Shift Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.weight(0.7f), contentAlignment = Alignment.CenterStart) {
                    Text("Day", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                shifts.forEach { shift ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = shift.first,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = shift.second,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Days Grid
            days.forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day Label
                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .padding(vertical = Spacing.xs),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = day,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Shift Cells
                    shifts.forEach { shift ->
                        val shiftName = shift.first
                        val timeRange = shift.second

                        // Check booking status
                        val isBooked = isFullMonthBooked || acceptedBookings.any { req ->
                            req.status == BookingRequestStatus.ACCEPTED &&
                                    (req.selectedDays.contains(day) || req.formula.daysOfWeek.contains(day)) &&
                                    (req.formula.type == RentalFormulaType.FULL_MONTH || 
                                     req.selectedShift.equals(shiftName, ignoreCase = true) || 
                                     req.formula.shiftName.equals(shiftName, ignoreCase = true))
                        }

                        // Heatmap styling based on state
                        val cellColor = if (isBooked) StatusErrorContainer else StatusSuccessContainer
                        val borderColor = if (isBooked) StatusError.copy(alpha = 0.5f) else StatusSuccess.copy(alpha = 0.5f)
                        val textColor = if (isBooked) StatusOnErrorContainer else StatusOnSuccessContainer

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(cellColor)
                                .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                                .clickable {
                                    selectedCellInfo = Triple(day, shiftName, isBooked)
                                    if (!isBooked) {
                                        val startH = timeRange.substringBefore(" -")
                                        val endH = timeRange.substringAfter("- ")
                                        val matchingFormula = space.rentalFormulas.find {
                                            it.type == RentalFormulaType.SHIFT && it.shiftName.equals(shiftName, ignoreCase = true)
                                        } ?: space.rentalFormulas.firstOrNull() ?: RentalFormula(
                                            type = RentalFormulaType.SHIFT,
                                            rateUsd = space.baseMonthlyRateUsd * shift.third,
                                            scheduleDescription = "$shiftName Shift ($timeRange)",
                                            daysOfWeek = listOf(day),
                                            startHour = startH,
                                            endHour = endH,
                                            shiftName = shiftName
                                        )
                                        onCellClicked(matchingFormula, day, shiftName)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = Spacing.xs)
                            ) {
                                if (isBooked) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Booked",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = textColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Open",
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

            // Selection feedback banner
            selectedCellInfo?.let { (day, shiftName, isBooked) ->
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
                                text = "$day ($shiftName Shift): ${if (isBooked) "Currently Reserved" else "Available for booking"}",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = { selectedCellInfo = null }) {
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
