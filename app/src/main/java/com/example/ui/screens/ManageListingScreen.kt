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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.ChangePriceSheet
import com.example.ui.components.ProSectionHeader
import com.example.ui.components.ProSurfaceCard
import com.example.ui.theme.*
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

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
    var showChangePrice by remember { mutableStateOf(false) }
    if (showChangePrice) {
        ChangePriceSheet(viewModel = viewModel, onDismiss = { showChangePrice = false }, initialSpaceId = liveSpace.id)
    }
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
                ProSurfaceCard(onClick = { showChangePrice = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Change price", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Update a slot's price — tenants and pending requests are asked about and notified",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

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
    // The same date-aware cells the availability table below draws (next 7 days, or the
    // monthly term's months), so the card and the table can never disagree.
    val cells = remember(division, acceptedBookings) { occupancyCells(division, spaceId, acceptedBookings) }
    val rentedCount = cells.count { it }
    val occupancyPct = if (cells.isEmpty()) 0 else (rentedCount * 100) / cells.size
    val unitWord = if (division.pricing.strategyType == RentalStrategyType.MONTHLY) "months" else "slots this week"

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
                                cells.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                                occupancyPct >= 66 -> MaterialTheme.proColors.success
                                occupancyPct >= 33 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            if (cells.isEmpty()) "No slots configured" else "$rentedCount of ${cells.size} $unitWord booked",
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
                                tint = if (delta >= 0) MaterialTheme.proColors.success else MaterialTheme.colorScheme.error,
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

    val monthRows = remember(config, divisionBookings) { monthlyCells(config, divisionBookings) }

    if (monthRows.isEmpty()) {
        Text("No active or upcoming term configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OccupancyLegend()
    Spacer(modifier = Modifier.height(6.dp))

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
                    .background(occupancyColor(cell.isRented), MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cell.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = onOccupancyColor(cell.isRented), modifier = Modifier.weight(1f))
                Text("$${cell.priceUsd.toInt()}${SpaceCalculationUtils.strategyUnitLabel(RentalStrategyType.MONTHLY)}",
                    style = MaterialTheme.typography.labelSmall, color = onOccupancyColor(cell.isRented))
            }
        }
    }
}

/** The monthly term's months (a rolling 12 for an open-ended term), each booked or free. */
private fun monthlyCells(config: MonthlyConfig, divisionBookings: List<RentalBookingRequest>): List<MonthCell> {
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
    return rows.map { (mm, yy) ->
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

/** One upcoming calendar day of a weekly-strategy division: its date, label and slots. */
private data class DayRow(val isoDate: String, val label: String, val slots: List<RentableSlot>)

private val ISO_DATE = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
private val DAY_LABEL = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault())
private val WEEKDAY_SHORT = java.text.SimpleDateFormat("EEE", java.util.Locale.US)

/** The next 7 days (today first) with the division's slots for each weekday. */
private fun upcomingDays(division: DivisionInfo): List<DayRow> {
    val slotsByDay = SpaceCalculationUtils.buildBookableSlots(division.pricing, division.schedule, division.id, division.name)
        .groupBy { it.day }
    val cal = Calendar.getInstance()
    return (0 until 7).map {
        val date = cal.time
        val row = DayRow(
            isoDate = ISO_DATE.format(date),
            label = DAY_LABEL.format(date),
            slots = slotsByDay[WEEKDAY_SHORT.format(date)].orEmpty().sortedBy { s -> s.startTime }
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
        row
    }.filter { it.slots.isNotEmpty() }
}

/**
 * Whether [slot] is taken on [isoDate]: the shared per-date rule
 * (SpaceCalculationUtils.isCalendarDateLocked — specific dates, recurring weekdays, full
 * month) limited to bookings whose term covers that date, so past bookings stop counting.
 */
private fun isBookedOn(slot: RentableSlot, isoDate: String, spaceId: String, accepted: List<RentalBookingRequest>): Boolean =
    SpaceCalculationUtils.isCalendarDateLocked(slot, isoDate, spaceId, accepted.filter { bookingCoversDate(it, isoDate) })

private fun bookingCoversDate(booking: RentalBookingRequest, isoDate: String): Boolean {
    if (booking.selectedCalendarDates.isNotEmpty()) return isoDate in booking.selectedCalendarDates
    val start = booking.startDate.trim().take(10)
    if (start.length < 7) return true // no term recorded: treat as ongoing
    val startKey = if (start.length == 7) "$start-01" else start
    val end = runCatching {
        val cal = Calendar.getInstance().apply { time = ISO_DATE.parse(startKey)!! }
        cal.add(Calendar.MONTH, booking.durationMonths.coerceAtLeast(1))
        ISO_DATE.format(cal.time)
    }.getOrNull() ?: return true
    return isoDate >= startKey && isoDate < end
}

/** Booked/free for every cell the availability table shows (used by the performance card). */
private fun occupancyCells(division: DivisionInfo, spaceId: String, accepted: List<RentalBookingRequest>): List<Boolean> =
    if (division.pricing.strategyType == RentalStrategyType.MONTHLY) {
        val config = division.pricing.monthly
        if (config == null) emptyList() else monthlyCells(config, accepted.filter {
            it.formula.type == RentalFormulaType.FULL_MONTH && (it.subdivisionId ?: spaceId) == division.id
        }).map { it.isRented }
    } else {
        upcomingDays(division).flatMap { day -> day.slots.map { isBookedOn(it, day.isoDate, spaceId, accepted) } }
    }

@Composable
private fun occupancyColor(booked: Boolean): androidx.compose.ui.graphics.Color =
    if (booked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.proColors.successContainer

@Composable
private fun onOccupancyColor(booked: Boolean): androidx.compose.ui.graphics.Color =
    if (booked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.proColors.onSuccessContainer

@Composable
private fun OccupancyLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "Free", true to "Booked").forEach { (booked, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(occupancyColor(booked), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeeklyAvailabilityTable(
    division: DivisionInfo,
    spaceId: String,
    acceptedBookings: List<RentalBookingRequest>
) {
    val days = remember(division) { upcomingDays(division) }

    if (days.isEmpty()) {
        Text("No slots configured yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    OccupancyLegend()
    Spacer(modifier = Modifier.height(6.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        days.forEach { day ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(day.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    day.slots.forEach { slot ->
                        AvailabilitySlotCell(
                            slot = slot,
                            booked = remember(slot, day.isoDate, acceptedBookings) {
                                isBookedOn(slot, day.isoDate, spaceId, acceptedBookings)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** A slot as one soft-filled cell: green when free, red when booked (no status words). */
@Composable
private fun AvailabilitySlotCell(slot: RentableSlot, booked: Boolean) {
    val price = slot.pricesByRecurrence[com.example.ui.util.BookingRecurrence.FLAT] ?: 0.0
    val timingLabel = when (slot.strategyType) {
        RentalStrategyType.SHIFT_BASED -> slot.groupLabel.substringAfter("• ")
        RentalStrategyType.DAY_BASED -> "Full Day"
        else -> "${slot.startTime}-${slot.endTime}"
    }
    // Per-attendee rooms carry a 1.0 availability marker, not a price.
    val showPrice = price > com.example.ui.util.AttendeePricing.AVAILABILITY_MARKER_PRICE

    Surface(
        color = occupancyColor(booked),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .width(84.dp)
            .semantics { contentDescription = "$timingLabel ${if (booked) "booked" else "free"}" }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(timingLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                color = onOccupancyColor(booked), maxLines = 1)
            if (showPrice) {
                Text("$${price.toInt()}", style = MaterialTheme.typography.labelSmall, color = onOccupancyColor(booked))
            }
        }
    }
}
