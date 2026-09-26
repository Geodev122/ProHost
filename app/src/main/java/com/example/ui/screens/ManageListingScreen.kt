package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.ProSectionHeader
import com.example.ui.components.ProStatusBadge
import com.example.ui.components.ProBadgeType
import com.example.ui.components.ProSurfaceCard
import com.example.ui.theme.*
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

private val WEEKDAY_ORDER = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class DivisionInfo(
    val id: String,
    val name: String,
    val pricing: RentalPricingConfig,
    val schedule: SpaceOperatingSchedule
)

/**
 * "Manage" — the whole-listing performance + availability page reached from My
 * Listings' Manage button (OwnerHubScreen.kt), replacing what used to just open
 * the read-only SpaceDetailsScreen. One performance card per division (or a
 * single card for the whole space when it has no subdivisions) showing its
 * rental strategy, real occupancy, and this-month-vs-last-month yield, each
 * with a "jump to" arrow that scrolls straight to that division's own
 * availability table further down the same page — a calendar-style grid keyed
 * by the division's real configured strategy (weekday rows for Hourly/Shift/
 * Day-Based, month rows for Monthly), reusing the exact same slot-building and
 * booking-lock logic every other availability-facing screen shares
 * (SpaceCalculationUtils), so this can never disagree with what a specialist
 * actually sees.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ManageListingScreen(
    space: SpaceListing,
    viewModel: ProHostViewModel,
    onBack: () -> Unit
) {
    val isLoading by viewModel.isRestoringSession.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()
    val liveSpace = allSpaces.find { it.id == space.id } ?: space
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    val acceptedBookings = remember(allBookingRequests, liveSpace.id) {
        allBookingRequests.filter { it.spaceId == liveSpace.id && it.status == BookingRequestStatus.ACCEPTED }
    }

    val divisions = remember(liveSpace) {
        if (liveSpace.subdivisions.isEmpty()) {
            listOf(DivisionInfo(liveSpace.id, liveSpace.title, liveSpace.pricing, liveSpace.schedule))
        } else {
            liveSpace.subdivisions.map { sub ->
                DivisionInfo(sub.id, sub.name, sub.pricing, sub.scheduleOverride ?: liveSpace.schedule)
            }
        }
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val jumpRequesters = remember(divisions) { divisions.associate { it.id to BringIntoViewRequester() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(liveSpace.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(innerPadding))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ProSectionHeader(
                    title = "Division Performance",
                    subtitle = if (divisions.size > 1) "${divisions.size} rentable divisions" else "Whole space rented as one unit",
                    icon = Icons.Default.Dashboard
                )

                divisions.forEach { division ->
                    DivisionPerformanceCard(
                        division = division,
                        isWholeSpace = liveSpace.subdivisions.isEmpty(),
                        spaceId = liveSpace.id,
                        acceptedBookings = acceptedBookings,
                        onJumpToSection = {
                            coroutineScope.launch {
                                jumpRequesters[division.id]?.bringIntoView()
                            }
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                ProSectionHeader(
                    title = "Availability",
                    subtitle = "Configured slots and their current booking state",
                    icon = Icons.Default.CalendarMonth
                )

                divisions.forEach { division ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { m -> jumpRequesters[division.id]?.let { m.bringIntoViewRequester(it) } ?: m }
                    ) {
                        Text(
                            text = division.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        DivisionAvailabilityTable(
                            division = division,
                            spaceId = liveSpace.id,
                            acceptedBookings = acceptedBookings
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DivisionPerformanceCard(
    division: DivisionInfo,
    isWholeSpace: Boolean,
    spaceId: String,
    acceptedBookings: List<RentalBookingRequest>,
    onJumpToSection: () -> Unit
) {
    val allSlots = remember(division) {
        SpaceCalculationUtils.buildBookableSlots(division.pricing, division.schedule, division.id, division.name)
    }
    val rentedCount = remember(allSlots, acceptedBookings) {
        allSlots.count { SpaceCalculationUtils.isSlotLocked(it, spaceId, acceptedBookings) }
    }
    val occupancyPct = if (allSlots.isEmpty()) 0 else (rentedCount * 100) / allSlots.size

    // Real revenue booked this calendar month vs last, scoped to this exact
    // division — the only timestamp every strategy's ACCEPTED booking always
    // carries is createdAt, so "this month's yield" means bookings closed this
    // month, not necessarily rental periods falling inside it (Monthly/Hourly
    // bookings have no calendar-date range at all to compare against instead).
    val divisionBookings = remember(acceptedBookings, division.id, isWholeSpace) {
        acceptedBookings.filter { if (isWholeSpace) it.subdivisionId == null else it.subdivisionId == division.id }
    }
    val (thisMonthYield, lastMonthYield) = remember(divisionBookings) {
        val now = Calendar.getInstance()
        val startOfThisMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfLastMonth = (startOfThisMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val thisMonth = divisionBookings.filter { it.createdAt >= startOfThisMonth.timeInMillis }.sumOf { it.totalAmountUsd }
        val lastMonth = divisionBookings.filter {
            it.createdAt >= startOfLastMonth.timeInMillis && it.createdAt < startOfThisMonth.timeInMillis
        }.sumOf { it.totalAmountUsd }
        thisMonth to lastMonth
    }

    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(division.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            division.pricing.strategyType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
                        )
                    }
                }
                IconButton(onClick = onJumpToSection) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to ${division.name}'s availability")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        Text("Occupancy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$occupancyPct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                allSlots.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                                occupancyPct >= 66 -> FreshGreen
                                occupancyPct >= 33 -> CarnationOrange
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            if (allSlots.isEmpty()) "No slots configured" else "$rentedCount of ${allSlots.size} slots rented",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        Text("Yield This Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$${"%,.0f".format(thisMonthYield)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val delta = thisMonthYield - lastMonthYield
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (delta >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = if (delta >= 0) FreshGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "vs $${"%,.0f".format(lastMonthYield)} last month",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DivisionAvailabilityTable(
    division: DivisionInfo,
    spaceId: String,
    acceptedBookings: List<RentalBookingRequest>
) {
    when (division.pricing.strategyType) {
        RentalStrategyType.MONTHLY -> MonthlyAvailabilityTable(division, spaceId, acceptedBookings)
        else -> WeeklyAvailabilityTable(division, spaceId, acceptedBookings)
    }
}

private data class MonthCell(val label: String, val isRented: Boolean, val priceUsd: Double)

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

@Composable
private fun MonthlyAvailabilityTable(
    division: DivisionInfo,
    spaceId: String,
    acceptedBookings: List<RentalBookingRequest>
) {
    val config = division.pricing.monthly
    if (config == null) {
        Text("No monthly pricing configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val divisionBookings = remember(acceptedBookings, division.id, spaceId) {
        acceptedBookings.filter {
            it.formula.type == RentalFormulaType.FULL_MONTH &&
                (it.subdivisionId ?: spaceId) == division.id
        }
    }

    val monthRows = remember(config, divisionBookings) {
        val rows = mutableListOf<Pair<Int, Int>>()
        var m = config.fromMonth
        var y = config.fromYear
        if (config.isIndefinite) {
            // A genuinely open-ended term has no natural end to list — show a
            // rolling 12-month window starting from whichever is later: the
            // configured start, or right now (so a term that started long ago
            // doesn't scroll a manager through a year of already-past months).
            val now = Calendar.getInstance()
            val curM = now.get(Calendar.MONTH) + 1
            val curY = now.get(Calendar.YEAR)
            if (y < curY || (y == curY && m < curM)) {
                m = curM; y = curY
            }
            repeat(12) {
                rows.add(m to y)
                m++; if (m > 12) { m = 1; y++ }
            }
        } else {
            val endM = config.toMonth ?: m
            val endY = config.toYear ?: y
            var guard = 0
            while ((y < endY || (y == endY && m <= endM)) && guard < 36) {
                rows.add(m to y)
                m++; if (m > 12) { m = 1; y++ }
                guard++
            }
        }
        rows.map { (mm, yy) ->
            val isExcluded = config.excludedRanges.any { r ->
                (yy > r.fromYear || (yy == r.fromYear && mm >= r.fromMonth)) &&
                    (yy < r.toYear || (yy == r.toYear && mm <= r.toMonth))
            }
            val isRented = !isExcluded && divisionBookings.any { b -> monthIsWithinBooking(b, mm, yy) }
            MonthCell(
                label = "${MONTH_NAMES[mm - 1]} $yy",
                isRented = isRented,
                priceUsd = config.rateUsd
            )
        }
    }

    if (monthRows.isEmpty()) {
        Text("No active or upcoming term configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        monthRows.forEach { cell ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (cell.isRented) OxfordBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cell.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$${cell.priceUsd.toInt()}/mo", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f))
                ProStatusBadge(
                    type = if (cell.isRented) ProBadgeType.ACCEPTED_LOCKED else ProBadgeType.CUSTOM_SUCCESS,
                    customText = if (cell.isRented) "Rented" else "Available"
                )
            }
        }
    }
}

/** Whether calendar month [mm]/[yy] falls inside [booking]'s real term
 *  (its startDate through startDate + durationMonths, exclusive) — the only
 *  place a FULL_MONTH booking's occupied months are derived, since neither
 *  the booking nor the slot model tracks month occupancy directly. */
private fun monthIsWithinBooking(booking: RentalBookingRequest, mm: Int, yy: Int): Boolean {
    val parts = booking.startDate.trim().split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return false
    val startYear = parts[0]
    val startMonth = parts[1]
    val startIndex = startYear * 12 + (startMonth - 1)
    val endIndexExclusive = startIndex + booking.durationMonths.coerceAtLeast(1)
    val targetIndex = yy * 12 + (mm - 1)
    return targetIndex in startIndex until endIndexExclusive
}

@Composable
private fun WeeklyAvailabilityTable(
    division: DivisionInfo,
    spaceId: String,
    acceptedBookings: List<RentalBookingRequest>
) {
    val allSlots = remember(division) {
        SpaceCalculationUtils.buildBookableSlots(division.pricing, division.schedule, division.id, division.name)
    }
    val rowsByDay = remember(allSlots) {
        allSlots.groupBy { it.day }.toList().sortedBy { (day, _) -> WEEKDAY_ORDER.indexOf(day).let { if (it < 0) 99 else it } }
    }

    if (rowsByDay.isEmpty()) {
        Text("No slots configured yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val rowScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rowsByDay.forEach { (day, slots) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(day, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rowScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slots.sortedBy { it.startTime }.forEach { slot ->
                        AvailabilitySlotCell(slot = slot, spaceId = spaceId, acceptedBookings = acceptedBookings)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailabilitySlotCell(
    slot: RentableSlot,
    spaceId: String,
    acceptedBookings: List<RentalBookingRequest>
) {
    val isRented = remember(slot, acceptedBookings) {
        SpaceCalculationUtils.isSlotLocked(slot, spaceId, acceptedBookings)
    }
    val price = slot.pricesByRecurrence[com.example.ui.util.BookingRecurrence.FLAT] ?: 0.0
    val timingLabel = when (slot.strategyType) {
        RentalStrategyType.SHIFT_BASED -> slot.groupLabel.substringAfter("• ")
        RentalStrategyType.DAY_BASED -> "Full Day"
        else -> "${slot.startTime}-${slot.endTime}"
    }

    Surface(
        color = if (isRented) OxfordBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.width(84.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(timingLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("$${price.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (isRented) "Rented" else "Open",
                style = MaterialTheme.typography.labelSmall,
                color = if (isRented) OxfordBlue else FreshGreen,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
