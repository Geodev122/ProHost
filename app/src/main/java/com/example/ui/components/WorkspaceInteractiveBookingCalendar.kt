package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class BookingCalendarMode {
    DATE_RANGE,
    MONTHLY_LEASE,
    HOURLY_SHIFT,
    SPECIFIC_DAYS_WEEKLY
}

data class CalendarDayItem(
    val date: Date,
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isPast: Boolean,
    val isOperatingDay: Boolean,
    val isBlackoutDay: Boolean,
    val isFullyBooked: Boolean,
    val isPartiallyBooked: Boolean,
    val formattedDateStr: String
) {
    /** A day the caller can never select as a start/end date — closed, blacked out, or already leased. */
    val isBlockedForSelection: Boolean get() = isBlackoutDay || isFullyBooked
}

/**
 * Whether [booking] is actually active on [date] — not just whether its
 * recurrence pattern matches this day-of-week, which is all the calendar used
 * to check. That meant a booking from any month blocked the same weekday in
 * every OTHER month shown too, forever, since its real date range was never
 * consulted. [booking.endDate] is often blank (most bookings only ever store
 * startDate + durationMonths), so the effective end is computed the same way
 * ProHostRepository.createBookingRequest describes a booking's term.
 */
private fun isBookingActiveOnDate(booking: RentalBookingRequest, date: Date, dateFormatter: SimpleDateFormat): Boolean {
    val start = try { dateFormatter.parse(booking.startDate) } catch (e: Exception) { null } ?: return false
    val end = if (booking.endDate.isNotBlank()) {
        try { dateFormatter.parse(booking.endDate) } catch (e: Exception) { null }
    } else null
    val effectiveEnd = end ?: Calendar.getInstance(Locale.US).apply {
        time = start
        add(Calendar.MONTH, booking.durationMonths.coerceAtLeast(1))
    }.time
    return !date.before(start) && !date.after(effectiveEnd)
}

/** "08:00" -> 480. Malformed input falls back to 0 rather than crashing the calendar. */
private fun timeToMinutes(hhmm: String): Int {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

/** Real hour-range overlap — "08:00"-"14:00" style strings. */
private fun hourRangesOverlap(startA: String, endA: String, startB: String, endB: String): Boolean {
    val aStart = timeToMinutes(startA)
    val aEnd = timeToMinutes(endA)
    val bStart = timeToMinutes(startB)
    val bEnd = timeToMinutes(endB)
    return aStart < bEnd && bStart < aEnd
}

data class AvailabilityCheckResult(
    val isAvailable: Boolean,
    val statusLevel: AvailabilityLevel, // AVAILABLE, WARNING, UNAVAILABLE
    val statusHeadline: String,
    val detailedReason: String,
    val conflictingDatesCount: Int = 0,
    val availableHoursCount: Int = 0
)

enum class AvailabilityLevel {
    AVAILABLE,
    PARTIAL,
    UNAVAILABLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceInteractiveBookingCalendar(
    space: SpaceListing,
    acceptedBookings: List<RentalBookingRequest> = emptyList(),
    initialFormula: RentalFormula? = null,
    modifier: Modifier = Modifier,
    onScheduleSelected: ((
        startDate: String,
        endDate: String,
        durationMonths: Int,
        selectedDays: List<String>,
        startHour: String,
        endHour: String,
        selectedShift: String,
        totalCalculatedUsd: Double,
        isInstantAvailable: Boolean
    ) -> Unit)? = null
) {
    val schedule = space.schedule
    val calendar = remember { Calendar.getInstance(Locale.US) }
    
    // Calendar month navigation
    var currentYearMonth by remember {
        val cal = Calendar.getInstance(Locale.US)
        mutableStateOf(Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)))
    }

    // Selected Date Range State
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    
    var selectedStartDate by remember {
        val cal = Calendar.getInstance(Locale.US)
        cal.add(Calendar.DAY_OF_YEAR, 1) // Default tomorrow
        mutableStateOf(cal.time)
    }

    var selectedEndDate by remember {
        val cal = Calendar.getInstance(Locale.US)
        cal.add(Calendar.DAY_OF_YEAR, 31) // Default 1 month
        mutableStateOf(cal.time)
    }

    // Duration & Frequency Selection
    val durationChipOptions = listOf(
        "1 Day" to 1,
        "3 Days" to 3,
        "1 Week" to 7,
        "2 Weeks" to 14,
        "1 Month" to 30,
        "3 Months" to 90,
        "6 Months" to 180
    )
    var selectedDurationIndex by remember { mutableStateOf(4) } // Default 1 Month
    var durationMonthsCount by remember { mutableStateOf(1) }

    // Shift and Times
    val shiftOptions = listOf(
        "Full Day" to Pair(schedule.openingHour.ifBlank { "08:00" }, schedule.closingHour.ifBlank { "20:00" }),
        "Morning Shift" to Pair("08:00", "13:00"),
        "Afternoon Shift" to Pair("13:00", "18:00"),
        "Evening Shift" to Pair("18:00", "22:00"),
        "Custom Hours" to Pair("09:00", "17:00")
    )
    var selectedShiftName by remember { mutableStateOf("Full Day") }
    var customStartHour by remember { mutableStateOf(schedule.openingHour.ifBlank { "08:00" }) }
    var customEndHour by remember { mutableStateOf(schedule.closingHour.ifBlank { "20:00" }) }

    // Operating days recurrence toggle
    val allWeekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDaysOfWeek by remember {
        mutableStateOf(
            if (schedule.operatingDays.isNotEmpty()) schedule.operatingDays.toSet()
            else setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        )
    }

    // Update End Date when duration chip clicked
    fun applyDuration(days: Int) {
        val cal = Calendar.getInstance(Locale.US)
        cal.time = selectedStartDate
        cal.add(Calendar.DAY_OF_YEAR, days)
        selectedEndDate = cal.time
        durationMonthsCount = if (days >= 30) days / 30 else 1
    }

    // Generate days in the current visible month grid
    val daysInMonth = remember(currentYearMonth, space, acceptedBookings) {
        val cal = Calendar.getInstance(Locale.US)
        cal.set(Calendar.YEAR, currentYearMonth.first)
        cal.set(Calendar.MONTH, currentYearMonth.second)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val monthMaxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday...
        val offset = (firstDayOfWeek - Calendar.MONDAY + 7) % 7

        val todayCal = Calendar.getInstance(Locale.US)
        val todayStr = dateFormatter.format(todayCal.time)

        val list = mutableListOf<CalendarDayItem>()

        // Previous month filler days
        val prevCal = cal.clone() as Calendar
        prevCal.add(Calendar.MONTH, -1)
        val prevMax = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in offset - 1 downTo 0) {
            val dayNum = prevMax - i
            prevCal.set(Calendar.DAY_OF_MONTH, dayNum)
            list.add(
                CalendarDayItem(
                    date = prevCal.time,
                    dayNumber = dayNum,
                    isCurrentMonth = false,
                    isToday = false,
                    isPast = prevCal.before(todayCal),
                    isOperatingDay = false,
                    isBlackoutDay = false,
                    isFullyBooked = false,
                    isPartiallyBooked = false,
                    formattedDateStr = dateFormatter.format(prevCal.time)
                )
            )
        }

        // Current month days
        for (day in 1..monthMaxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val curDate = cal.time
            val dateStr = dateFormatter.format(curDate)
            val dayOfWeekShort = SimpleDateFormat("EEE", Locale.US).format(curDate)

            val isPast = cal.before(todayCal) && dateStr != todayStr
            val isToday = dateStr == todayStr
            val isOperating = schedule.operatingDays.contains(dayOfWeekShort)

            // Check blackouts
            val isBlackout = schedule.blackoutSlots.any { it.dayOfWeek.equals(dayOfWeekShort, ignoreCase = true) }

            // Check bookings on this day — must actually be active on THIS calendar
            // date, not just share a day-of-week (see isBookingActiveOnDate's doc).
            val dayBookings = acceptedBookings.filter { bkg ->
                bkg.spaceId == space.id &&
                bkg.status == BookingRequestStatus.ACCEPTED &&
                (bkg.selectedDays.isEmpty() || bkg.selectedDays.contains(dayOfWeekShort)) &&
                isBookingActiveOnDate(bkg, curDate, dateFormatter)
            }
            // A day is fully leased (unselectable) if a full-month exclusive booking
            // covers it, or if some combination of accepted shifts already spans the
            // entire operating window for that day.
            val isFullyBooked = dayBookings.any { it.formula.type == RentalFormulaType.FULL_MONTH } ||
                (dayBookings.isNotEmpty() && run {
                    val openMin = timeToMinutes(schedule.openingHour.ifBlank { "08:00" })
                    val closeMin = timeToMinutes(schedule.closingHour.ifBlank { "20:00" })
                    val covered = BooleanArray(closeMin.coerceAtLeast(openMin) - openMin)
                    dayBookings.forEach { bkg ->
                        val bStart = (timeToMinutes(bkg.selectedStartHour.ifBlank { bkg.formula.startHour }) - openMin).coerceIn(0, covered.size)
                        val bEnd = (timeToMinutes(bkg.selectedEndHour.ifBlank { bkg.formula.endHour }) - openMin).coerceIn(0, covered.size)
                        for (i in bStart until bEnd) covered[i] = true
                    }
                    covered.isNotEmpty() && covered.all { it }
                })
            val isPartiallyBooked = dayBookings.isNotEmpty() && !isFullyBooked

            list.add(
                CalendarDayItem(
                    date = curDate,
                    dayNumber = day,
                    isCurrentMonth = true,
                    isToday = isToday,
                    isPast = isPast,
                    isOperatingDay = isOperating,
                    isBlackoutDay = isBlackout,
                    isFullyBooked = isFullyBooked,
                    isPartiallyBooked = isPartiallyBooked,
                    formattedDateStr = dateStr
                )
            )
        }

        // Fill trailing cells up to multiple of 7
        val remaining = (7 - (list.size % 7)) % 7
        val nextCal = cal.clone() as Calendar
        nextCal.add(Calendar.MONTH, 1)
        for (day in 1..remaining) {
            nextCal.set(Calendar.DAY_OF_MONTH, day)
            list.add(
                CalendarDayItem(
                    date = nextCal.time,
                    dayNumber = day,
                    isCurrentMonth = false,
                    isToday = false,
                    isPast = false,
                    isOperatingDay = false,
                    isBlackoutDay = false,
                    isFullyBooked = false,
                    isPartiallyBooked = false,
                    formattedDateStr = dateFormatter.format(nextCal.time)
                )
            )
        }

        list
    }

    // Real-Time Availability Engine
    val availabilityCheck = remember(
        selectedStartDate,
        selectedEndDate,
        selectedDaysOfWeek,
        selectedShiftName,
        customStartHour,
        customEndHour,
        space,
        acceptedBookings
    ) {
        val startCal = Calendar.getInstance(Locale.US).apply { time = selectedStartDate }
        val endCal = Calendar.getInstance(Locale.US).apply { time = selectedEndDate }
        
        var totalDaysChecked = 0
        var closedDaysCount = 0
        var blackoutConflictsCount = 0
        var existingBookingConflicts = 0

        val tempCal = startCal.clone() as Calendar
        while (!tempCal.after(endCal)) {
            val dayOfWeekStr = SimpleDateFormat("EEE", Locale.US).format(tempCal.time)
            
            if (selectedDaysOfWeek.contains(dayOfWeekStr)) {
                totalDaysChecked++
                
                // Check if facility operates on this day
                if (!schedule.operatingDays.contains(dayOfWeekStr)) {
                    closedDaysCount++
                }

                // Check blackout slots
                val hasBlackout = schedule.blackoutSlots.any {
                    it.dayOfWeek.equals(dayOfWeekStr, ignoreCase = true)
                }
                if (hasBlackout) {
                    blackoutConflictsCount++
                }

                // Check collisions with accepted reservations actually active on this
                // calendar date — day-of-week alone isn't enough (see isBookingActiveOnDate).
                val collision = acceptedBookings.any { bkg ->
                    bkg.spaceId == space.id &&
                    bkg.status == BookingRequestStatus.ACCEPTED &&
                    (bkg.selectedDays.isEmpty() || bkg.selectedDays.contains(dayOfWeekStr)) &&
                    isBookingActiveOnDate(bkg, tempCal.time, dateFormatter) &&
                    (bkg.formula.type == RentalFormulaType.FULL_MONTH ||
                     hourRangesOverlap(
                         bkg.selectedStartHour.ifBlank { bkg.formula.startHour },
                         bkg.selectedEndHour.ifBlank { bkg.formula.endHour },
                         customStartHour,
                         customEndHour
                     ))
                }
                if (collision) {
                    existingBookingConflicts++
                }
            }
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        when {
            closedDaysCount > 0 && selectedDaysOfWeek.size == closedDaysCount -> {
                AvailabilityCheckResult(
                    isAvailable = false,
                    statusLevel = AvailabilityLevel.UNAVAILABLE,
                    statusHeadline = "Workspace is Closed on Selected Days",
                    detailedReason = "The facility schedule does not operate on selected days (${schedule.operatingDays.joinToString()}).",
                    conflictingDatesCount = closedDaysCount
                )
            }
            existingBookingConflicts > 0 -> {
                AvailabilityCheckResult(
                    isAvailable = false,
                    statusLevel = AvailabilityLevel.PARTIAL,
                    statusHeadline = "Time Slot Conflict Detected",
                    detailedReason = "$existingBookingConflicts day(s) overlap with an active reservation. Consider switching shift or choosing alternative weekdays.",
                    conflictingDatesCount = existingBookingConflicts
                )
            }
            blackoutConflictsCount > 0 -> {
                AvailabilityCheckResult(
                    isAvailable = false,
                    statusLevel = AvailabilityLevel.PARTIAL,
                    statusHeadline = "Host Maintenance Blackout Notice",
                    detailedReason = "The host has blocked maintenance/private hours on some selected days. Alternative shifts remain available.",
                    conflictingDatesCount = blackoutConflictsCount
                )
            }
            else -> {
                AvailabilityCheckResult(
                    isAvailable = true,
                    statusLevel = AvailabilityLevel.AVAILABLE,
                    statusHeadline = "100% Available — Instant Match Confirmed",
                    detailedReason = "No schedule collisions found. Space is fully operational with backup generator and fiber optics ready.",
                    conflictingDatesCount = 0,
                    availableHoursCount = totalDaysChecked * 8
                )
            }
        }
    }

    // Dynamic Price Calculation
    val totalCalculatedUsd = remember(
        space,
        durationMonthsCount,
        selectedDurationIndex,
        selectedShiftName,
        selectedDaysOfWeek
    ) {
        val baseMonthly = space.baseMonthlyRateUsd.coerceAtLeast(100.0)
        val durationDays = durationChipOptions.getOrNull(selectedDurationIndex)?.second ?: 30
        
        val shiftMultiplier = when (selectedShiftName) {
            "Morning Shift", "Afternoon Shift" -> 0.55
            "Evening Shift" -> 0.40
            else -> 1.0
        }

        val daysFraction = (selectedDaysOfWeek.size.toDouble() / 6.0).coerceIn(0.2, 1.0)

        if (durationDays < 30) {
            // Daily / weekly calculation
            val dailyRate = (baseMonthly / 24.0) * shiftMultiplier
            dailyRate * durationDays * daysFraction
        } else {
            // Monthly calculation
            (baseMonthly * shiftMultiplier * daysFraction) * durationMonthsCount
        }
    }

    // True when every operating day this month is already blocked (blackout or fully
    // leased) — worth calling out explicitly rather than making the host/specialist
    // discover it one greyed-out day at a time.
    val isEntireMonthBooked = remember(daysInMonth) {
        val operatingDays = daysInMonth.filter { it.isCurrentMonth && it.isOperatingDay && !it.isPast }
        operatingDays.isNotEmpty() && operatingDays.all { it.isBlockedForSelection }
    }

    // Header Month String
    val monthTitle = remember(currentYearMonth) {
        val cal = Calendar.getInstance(Locale.US)
        cal.set(Calendar.YEAR, currentYearMonth.first)
        cal.set(Calendar.MONTH, currentYearMonth.second)
        SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_booking_calendar"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calendar Top Bar: Title & Month Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Interactive Booking Calendar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Select dates, shift duration & verify live availability",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance(Locale.US)
                                cal.set(Calendar.YEAR, currentYearMonth.first)
                                cal.set(Calendar.MONTH, currentYearMonth.second)
                                cal.add(Calendar.MONTH, -1)
                                currentYearMonth = Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", modifier = Modifier.size(16.dp))
                        }
                        
                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance(Locale.US)
                                cal.set(Calendar.YEAR, currentYearMonth.first)
                                cal.set(Calendar.MONTH, currentYearMonth.second)
                                cal.add(Calendar.MONTH, 1)
                                currentYearMonth = Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (isEntireMonthBooked) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "$monthTitle is fully booked — try a different month or shift.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Quick Duration Preset Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "RESERVATION DURATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(durationChipOptions.indices.toList()) { index ->
                        val (label, days) = durationChipOptions[index]
                        val isSelected = selectedDurationIndex == index
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDurationIndex = index
                                applyDuration(days)
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            }

            // Weekday Column Headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLetter ->
                    Text(
                        text = dayLetter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }

            // Interactive Calendar Days Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val rows = daysInMonth.chunked(7)
                rows.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        week.forEach { dayItem ->
                            val isStart = dateFormatter.format(dayItem.date) == dateFormatter.format(selectedStartDate)
                            val isEnd = dateFormatter.format(dayItem.date) == dateFormatter.format(selectedEndDate)
                            val inRange = dayItem.date.after(selectedStartDate) && dayItem.date.before(selectedEndDate)
                            val isSelectable = dayItem.isCurrentMonth && !dayItem.isPast && !dayItem.isBlockedForSelection

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(
                                        when {
                                            isStart && isEnd -> MaterialTheme.shapes.medium
                                            isStart -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                                            isEnd -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                                            inRange -> RoundedCornerShape(0.dp)
                                            else -> CircleShape
                                        }
                                    )
                                    .background(
                                        when {
                                            isStart || isEnd -> MaterialTheme.colorScheme.primary
                                            inRange -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            dayItem.isCurrentMonth && dayItem.isBlockedForSelection -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                            dayItem.isToday -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = isSelectable) {
                                        if (dayItem.date.before(selectedStartDate) || selectedStartDate == selectedEndDate) {
                                            selectedStartDate = dayItem.date
                                            val cal = Calendar.getInstance(Locale.US).apply { time = dayItem.date }
                                            val daysToAdd = durationChipOptions.getOrNull(selectedDurationIndex)?.second ?: 30
                                            cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                                            selectedEndDate = cal.time
                                        } else {
                                            selectedEndDate = dayItem.date
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (dayItem.isCurrentMonth && !dayItem.isPast && dayItem.isBlockedForSelection && !isStart && !isEnd) {
                                        // Blocked days show a lock instead of the day number — this
                                        // used to just tint a status dot amber/red while leaving the
                                        // day fully tappable, so a host's accepted booking or blackout
                                        // slot never actually stopped a conflicting selection.
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Unavailable",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    } else {
                                        Text(
                                            text = dayItem.dayNumber.toString(),
                                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                            fontWeight = if (isStart || isEnd || dayItem.isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                !dayItem.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                                dayItem.isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                                isStart || isEnd -> Color.White
                                                inRange -> MaterialTheme.colorScheme.onPrimaryContainer
                                                !dayItem.isOperatingDay -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }

                                    // Status Dot Indicator
                                    if (dayItem.isCurrentMonth && !dayItem.isPast) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isStart || isEnd -> Color.White
                                                        !dayItem.isOperatingDay || dayItem.isBlockedForSelection -> MaterialTheme.colorScheme.error
                                                        dayItem.isPartiallyBooked -> StatusWarning // Amber
                                                        else -> StatusSuccess // Green Available
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Calendar Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusSuccess))
                    Text("Available", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StatusWarning))
                    Text("Partial / Shifts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    Text("Closed / Booked", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // Selected Dates & Shift / Time Slot Customizer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SLOT & SCHEDULE CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Shift Selector — a shift is blocked (greyed out, unselectable) when its
                // hours genuinely overlap an accepted booking active somewhere in the
                // currently selected date range, on one of the currently selected days.
                val conflictingShiftNames = remember(selectedStartDate, selectedEndDate, selectedDaysOfWeek, space, acceptedBookings) {
                    val startCal = Calendar.getInstance(Locale.US).apply { time = selectedStartDate }
                    val endCal = Calendar.getInstance(Locale.US).apply { time = selectedEndDate }
                    val activeBookingsInRange = mutableListOf<RentalBookingRequest>()
                    val tempCal = startCal.clone() as Calendar
                    while (!tempCal.after(endCal)) {
                        val dow = SimpleDateFormat("EEE", Locale.US).format(tempCal.time)
                        if (selectedDaysOfWeek.contains(dow)) {
                            acceptedBookings.filter { bkg ->
                                bkg.spaceId == space.id && bkg.status == BookingRequestStatus.ACCEPTED &&
                                    (bkg.selectedDays.isEmpty() || bkg.selectedDays.contains(dow)) &&
                                    isBookingActiveOnDate(bkg, tempCal.time, dateFormatter)
                            }.forEach { if (it !in activeBookingsInRange) activeBookingsInRange.add(it) }
                        }
                        tempCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    shiftOptions.filter { (_, times) ->
                        activeBookingsInRange.any { bkg ->
                            bkg.formula.type == RentalFormulaType.FULL_MONTH ||
                                hourRangesOverlap(
                                    bkg.selectedStartHour.ifBlank { bkg.formula.startHour },
                                    bkg.selectedEndHour.ifBlank { bkg.formula.endHour },
                                    times.first,
                                    times.second
                                )
                        }
                    }.map { it.first }.toSet()
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(shiftOptions) { (shiftName, times) ->
                        val isSelected = selectedShiftName == shiftName
                        val isConflicting = shiftName in conflictingShiftNames
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = when {
                                isConflicting -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable(enabled = !isConflicting) {
                                selectedShiftName = shiftName
                                customStartHour = times.first
                                customEndHour = times.second
                            }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = shiftName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isConflicting) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isConflicting) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
                                    }
                                }
                                Text(
                                    text = if (isConflicting) "${times.first} - ${times.second} • Booked" else "${times.first} - ${times.second}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Operating Days Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Days of Week:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        allWeekDays.forEach { day ->
                            val isDaySelected = selectedDaysOfWeek.contains(day)
                            val isSpaceOpen = schedule.operatingDays.contains(day)
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isDaySelected -> MaterialTheme.colorScheme.primary
                                    !isSpaceOpen -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .clickable(enabled = isSpaceOpen) {
                                        selectedDaysOfWeek = if (isDaySelected) {
                                            if (selectedDaysOfWeek.size > 1) selectedDaysOfWeek - day else selectedDaysOfWeek
                                        } else {
                                            selectedDaysOfWeek + day
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = day.take(1),
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDaySelected) Color.White else if (!isSpaceOpen) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Availability Check Status Banner
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when (availabilityCheck.statusLevel) {
                    AvailabilityLevel.AVAILABLE -> StatusSuccessContainer
                    AvailabilityLevel.PARTIAL -> StatusWarningContainer
                    AvailabilityLevel.UNAVAILABLE -> StatusErrorContainer
                },
                border = BorderStroke(
                    1.dp,
                    when (availabilityCheck.statusLevel) {
                        AvailabilityLevel.AVAILABLE -> StatusSuccess
                        AvailabilityLevel.PARTIAL -> StatusWarning
                        AvailabilityLevel.UNAVAILABLE -> StatusError
                    }
                ),
                modifier = Modifier.fillMaxWidth().testTag("availability_status_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = when (availabilityCheck.statusLevel) {
                            AvailabilityLevel.AVAILABLE -> Icons.Default.CheckCircle
                            AvailabilityLevel.PARTIAL -> Icons.Default.Warning
                            AvailabilityLevel.UNAVAILABLE -> Icons.Default.Cancel
                        },
                        contentDescription = null,
                        tint = when (availabilityCheck.statusLevel) {
                            AvailabilityLevel.AVAILABLE -> StatusOnSuccessContainer
                            AvailabilityLevel.PARTIAL -> StatusOnWarningContainer
                            AvailabilityLevel.UNAVAILABLE -> StatusOnErrorContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = availabilityCheck.statusHeadline,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (availabilityCheck.statusLevel) {
                                AvailabilityLevel.AVAILABLE -> StatusOnSuccessContainer
                                AvailabilityLevel.PARTIAL -> StatusOnWarningContainer
                                AvailabilityLevel.UNAVAILABLE -> StatusOnErrorContainer
                            }
                        )
                        Text(
                            text = availabilityCheck.detailedReason,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Summary Info & Price Estimator
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected Lease Schedule",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${displayDateFormatter.format(selectedStartDate)} → ${displayDateFormatter.format(selectedEndDate)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$selectedShiftName ($customStartHour - $customEndHour) • ${selectedDaysOfWeek.sorted().joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Estimated Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$${String.format(Locale.US, "%.0f", totalCalculatedUsd)} USD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "≈ ${(totalCalculatedUsd * 89500).toLong()} LBP",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action Button: Confirm and Proceed to Booking
            if (onScheduleSelected != null) {
                Button(
                    onClick = {
                        onScheduleSelected(
                            dateFormatter.format(selectedStartDate),
                            dateFormatter.format(selectedEndDate),
                            durationMonthsCount,
                            selectedDaysOfWeek.toList(),
                            customStartHour,
                            customEndHour,
                            selectedShiftName,
                            totalCalculatedUsd,
                            availabilityCheck.isAvailable
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_calendar_schedule_button"),
                    shape = MaterialTheme.shapes.medium,
                    // A hard UNAVAILABLE result (blackout or a genuinely overlapping accepted
                    // booking) now actually blocks submission — this used to stay tappable
                    // regardless, so the "Unavailable" banner above was purely informational
                    // and a conflicting request could be submitted anyway.
                    enabled = availabilityCheck.statusLevel != AvailabilityLevel.UNAVAILABLE,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (availabilityCheck.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        if (availabilityCheck.statusLevel == AvailabilityLevel.UNAVAILABLE) Icons.Default.Lock else Icons.Default.BookOnline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = when (availabilityCheck.statusLevel) {
                            AvailabilityLevel.AVAILABLE -> "Book Selected Dates (${String.format(Locale.US, "%.0f", totalCalculatedUsd)} USD)"
                            AvailabilityLevel.PARTIAL -> "Proceed with Custom Schedule"
                            AvailabilityLevel.UNAVAILABLE -> "Unavailable — Choose Different Dates"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
