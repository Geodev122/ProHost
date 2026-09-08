package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.*
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusLocked
import com.example.ui.theme.StatusLockedContainer
import com.example.ui.theme.StatusOnErrorContainer
import com.example.ui.theme.StatusOnInfoContainer
import com.example.ui.theme.StatusOnSuccessContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.Spacing

@Composable
fun SpaceAvailabilityCalendarView(
    space: SpaceListing,
    acceptedBookings: List<RentalBookingRequest>,
    modifier: Modifier = Modifier
) {
    val schedule = space.schedule

    // Calculate total operating hours
    val openHourInt = schedule.openingHour.substringBefore(":").toIntOrNull() ?: 8
    val closeHourInt = schedule.closingHour.substringBefore(":").toIntOrNull() ?: 20
    val dailyOperatingHours = (closeHourInt - openHourInt).coerceAtLeast(1)
    val totalOperatingDays = schedule.operatingDays.size
    val totalWeeklyOperatingHours = dailyOperatingHours * totalOperatingDays

    // Calculate rented hours from ACCEPTED bookings
    val totalRentedWeeklyHours = acceptedBookings
        .filter { it.status == BookingRequestStatus.ACCEPTED }
        .sumOf { it.formula.totalWeeklyHours }

    // Calculate blackout hours
    val totalBlackoutHours = schedule.blackoutSlots.sumOf { slot ->
        val startH = slot.startTime.substringBefore(":").toIntOrNull() ?: 0
        val endH = slot.endTime.substringBefore(":").toIntOrNull() ?: 0
        (endH - startH).coerceAtLeast(1)
    }

    val remainingAvailableHours = (totalWeeklyOperatingHours - totalRentedWeeklyHours - totalBlackoutHours).coerceAtLeast(0)
    val occupancyPercentage = if (totalWeeklyOperatingHours > 0) {
        ((totalRentedWeeklyHours.toFloat() / totalWeeklyOperatingHours.toFloat()) * 100).toInt()
    } else 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Operating & Smart Availability",
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = if (remainingAvailableHours > 0) StatusSuccessContainer else StatusErrorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (remainingAvailableHours > 0) "$remainingAvailableHours hrs/wk Open" else "Fully Booked",
                        color = if (remainingAvailableHours > 0) StatusOnSuccessContainer else StatusOnErrorContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    )
                }
            }

            // Facility Schedule Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏰ Operating Hours: ${schedule.openingHour} - ${schedule.closingHour}",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "🗓️ ${schedule.operatingDays.joinToString()}",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Live Visual Capacity Breakdown Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Capacity Allocation", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$occupancyPercentage% Rented", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                // Multi-segment progress bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val rentedFraction = if (totalWeeklyOperatingHours > 0) (totalRentedWeeklyHours.toFloat() / totalWeeklyOperatingHours.toFloat()).coerceIn(0f, 1f) else 0f
                    val blackoutFraction = if (totalWeeklyOperatingHours > 0) (totalBlackoutHours.toFloat() / totalWeeklyOperatingHours.toFloat()).coerceIn(0f, 1f - rentedFraction) else 0f
                    val availableFraction = (1f - rentedFraction - blackoutFraction).coerceAtLeast(0f)

                    if (rentedFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(rentedFraction)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (blackoutFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(blackoutFraction)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                    if (availableFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(availableFraction)
                                .fillMaxHeight()
                                .background(StatusSuccess)
                        )
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendItem(color = MaterialTheme.colorScheme.primary, text = "Rented ($totalRentedWeeklyHours h)")
                    LegendItem(color = MaterialTheme.colorScheme.outline, text = "Blackout/Closed ($totalBlackoutHours h)")
                    LegendItem(color = StatusSuccess, text = "Available ($remainingAvailableHours h)")
                }
            }

            // Rented Slots List (Publicly Unavailable)
            if (acceptedBookings.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔒 Booked Slots (Unavailable to Public):",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalRentedWeeklyHours hrs locked",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusInfo
                        )
                    }

                    acceptedBookings.forEach { booking ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusInfo.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${booking.formula.scheduleDescription} (${booking.formula.daysOfWeek.joinToString()} • ${booking.formula.startHour} - ${booking.formula.endHour})",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Resident Specialist: ${booking.practitionerName} (${booking.practitionerSpecialty})",
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Slot: ${booking.selectedDateTimeRange.ifBlank { "${booking.startDate} (${booking.durationMonths} mo)" }}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    color = StatusInfoContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "UNAVAILABLE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = StatusOnInfoContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Blackout / Non-Operating Slots (if any)
            if (schedule.blackoutSlots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "🚫 Non-Operating / Owner Blackout Hours:",
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    schedule.blackoutSlots.forEach { slot ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${slot.dayOfWeek}: ${slot.startTime} - ${slot.endTime}",
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = slot.reason,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    color = StatusLockedContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "HIDDEN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusLocked,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
