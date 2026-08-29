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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Operating & Smart Availability",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = if (remainingAvailableHours > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (remainingAvailableHours > 0) "$remainingAvailableHours hrs/wk Open" else "Fully Booked",
                        color = if (remainingAvailableHours > 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "🗓️ ${schedule.operatingDays.joinToString()}",
                    fontSize = 12.sp,
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
                    Text("Capacity Allocation", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$occupancyPercentage% Rented", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                // Multi-segment progress bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFFE2E8F0))
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
                                .background(Color(0xFF94A3B8))
                        )
                    }
                    if (availableFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(availableFraction)
                                .fillMaxHeight()
                                .background(Color(0xFF22C55E))
                        )
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendItem(color = MaterialTheme.colorScheme.primary, text = "Rented ($totalRentedWeeklyHours h)")
                    LegendItem(color = Color(0xFF94A3B8), text = "Blackout/Closed ($totalBlackoutHours h)")
                    LegendItem(color = Color(0xFF22C55E), text = "Available ($remainingAvailableHours h)")
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalRentedWeeklyHours hrs locked",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D4ED8)
                        )
                    }

                    acceptedBookings.forEach { booking ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${booking.formula.scheduleDescription} (${booking.formula.daysOfWeek.joinToString()} • ${booking.formula.startHour} - ${booking.formula.endHour})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Resident Professional: ${booking.practitionerName} (${booking.practitionerSpecialty})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Slot: ${booking.selectedDateTimeRange.ifBlank { "${booking.startDate} (${booking.durationMonths} mo)" }}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Surface(
                                    color = Color(0xFFDBEAFE),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "UNAVAILABLE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1D4ED8),
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    schedule.blackoutSlots.forEach { slot ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
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
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = slot.reason,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "HIDDEN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
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
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
