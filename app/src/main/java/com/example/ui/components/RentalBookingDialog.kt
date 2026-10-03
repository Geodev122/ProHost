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
import androidx.compose.ui.graphics.compositeOver
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
import com.example.R
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.util.BookingRecurrence
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import java.text.NumberFormat
import java.util.*

private val calendarDayAbbreviations = mapOf(
    Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat"
)

/** UTC-based, matching the date picker's own UTC millis — see [rememberDatePickerState]. */
private fun weekdayAbbreviation(utcTimeMillis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = utcTimeMillis
    return calendarDayAbbreviations[cal.get(Calendar.DAY_OF_WEEK)] ?: "Mon"
}

private fun isoDateString(utcTimeMillis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = utcTimeMillis
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

private fun weekdayAbbreviationFromIso(iso: String): String {
    val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return "Mon"
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return calendarDayAbbreviations[cal.get(Calendar.DAY_OF_WEEK)] ?: "Mon"
}

/** Midnight UTC tomorrow — the earliest selectable date across every real
 * calendar-date picker in this dialog. */
private fun tomorrowUtcMillis(): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.add(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Every real calendar date matching [weekdayAbbrev], from tomorrow through
 * [untilIso] inclusive — Day-Based's "recurring weekly until a date" commitment
 * (the same request applied to Shift-Based's item 7b real-date approach). */
private fun weeklyOccurrencesUntil(weekdayAbbrev: String, untilIso: String): List<String> {
    val targetDow = calendarDayAbbreviations.entries.firstOrNull { it.value == weekdayAbbrev }?.key ?: return emptyList()
    val cursor = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = tomorrowUtcMillis() }
    while (cursor.get(Calendar.DAY_OF_WEEK) != targetDow) {
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    val untilParts = untilIso.split("-").mapNotNull { it.toIntOrNull() }
    if (untilParts.size != 3) return emptyList()
    val until = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(untilParts[0], untilParts[1] - 1, untilParts[2], 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dates = mutableListOf<String>()
    while (!cursor.after(until)) {
        dates.add(isoDateString(cursor.timeInMillis))
        cursor.add(Calendar.DAY_OF_MONTH, 7)
    }
    return dates
}

/**
 * Re-book/Extend entry point (My Rentals) — configures and submits a brand-new,
 * independent lease request using the same real slot-selection engine a first
 * booking uses. Never references an existing booking to replace.
 */
@Composable
fun RebookDialog(
    space: SpaceListing,
    initialFormula: RentalFormula?,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit,
    onRequestSubmitted: () -> Unit,
    attendeePackages: List<AttendeePackage> = emptyList()
) {
    BookingSlotSelectorDialog(
        target = BookingDialogTarget(space = space, initialFormula = initialFormula, replacesBookingId = null),
        viewModel = viewModel,
        copy = BookingDialogCopy(
            headerTitle = "Re-book This Space",
            headerBadgeText = "Real-Time Availability",
            primaryButtonText = "Request",
            secondaryButtonText = "Request and Contact"
        ),
        onDismiss = onDismiss,
        onRequestSubmitted = onRequestSubmitted,
        attendeePackages = attendeePackages
    )
}

/**
 * Edit Booking entry point (My Rentals, accepted bookings only) — proposes a new
 * slot for an already-ACCEPTED booking. Submitted as a new PENDING request that
 * references [replacesBookingId]; if the host accepts it,
 * ProHostRepository.acceptBookingRequest releases the old booking and this one
 * takes its place (see that function's own doc comment).
 */
@Composable
fun EditBookingDialog(
    space: SpaceListing,
    initialFormula: RentalFormula?,
    replacesBookingId: String,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit,
    onRequestSubmitted: () -> Unit,
    attendeePackages: List<AttendeePackage> = emptyList()
) {
    BookingSlotSelectorDialog(
        target = BookingDialogTarget(space = space, initialFormula = initialFormula, replacesBookingId = replacesBookingId),
        viewModel = viewModel,
        copy = BookingDialogCopy(
            headerTitle = "Propose a Booking Change",
            headerBadgeText = "Replaces Your Current Booking",
            primaryButtonText = "Submit Change",
            secondaryButtonText = "Submit Change and Contact"
        ),
        onDismiss = onDismiss,
        onRequestSubmitted = onRequestSubmitted,
        attendeePackages = attendeePackages
    )
}

/**
 * The shared slot-selection/pricing engine both [RebookDialog] and
 * [EditBookingDialog] are thin, purpose-built wrappers around — subdivision
 * picker, per-strategy availability customization, the 3 real-calendar date
 * pickers, pricing, and submission are identical real logic either caller needs;
 * only copy (header/button text) and [replacesBookingId] differ between them.
 */
data class BookingDialogTarget(
    val space: SpaceListing,
    val initialFormula: RentalFormula?,
    val replacesBookingId: String?
)

data class BookingDialogCopy(
    val headerTitle: String,
    val headerBadgeText: String,
    val primaryButtonText: String,
    val secondaryButtonText: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BookingSlotSelectorDialog(
    target: BookingDialogTarget,
    viewModel: ProHostViewModel,
    copy: BookingDialogCopy,
    onDismiss: () -> Unit,
    onRequestSubmitted: () -> Unit,
    attendeePackages: List<AttendeePackage> = emptyList()
) {
    val space = target.space
    val initialFormula = target.initialFormula
    val replacesBookingId = target.replacesBookingId
    val headerTitle = copy.headerTitle
    val headerBadgeText = copy.headerBadgeText
    val primaryButtonText = copy.primaryButtonText
    val secondaryButtonText = copy.secondaryButtonText
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
    // Slots an ACCEPTED booking already locks are hidden rather than offered — the
    // same rule the availability matrix greys them out with, so nothing the
    // specialist could see as taken there is bookable here. An edit of an accepted
    // booking (replacesBookingId) isn't blocked by the booking it's replacing.
    val allBookings by viewModel.bookingRequests.collectAsState()
    val acceptedForSpace = remember(allBookings, space.id) {
        allBookings.filter { it.spaceId == space.id && it.status == BookingRequestStatus.ACCEPTED }
    }
    val openSlots = remember(allSlots, acceptedForSpace, replacesBookingId) {
        allSlots.filterNot { SpaceCalculationUtils.isSlotLocked(it, space.id, acceptedForSpace, ignoreBookingId = replacesBookingId) }
    }
    val hiddenLockedCount = allSlots.size - openSlots.size
    val scopedSlots = remember(openSlots, hasSubdivisions, selectedSubdivision) {
        if (hasSubdivisions) {
            val subId = selectedSubdivision?.id
            if (subId != null) openSlots.filter { it.sourceFormulaId == subId } else emptyList()
        } else {
            openSlots
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

    // --- Shift-Based: pick a day, a real shift offered that day, then which other
    // days share that same shift type, then specific real calendar dates (item 7b:
    // one flat price per shift; the specialist configures real occurrences instead
    // of picking a one-time/weekly/monthly recurrence tier) ---
    var shiftDay by remember(strategySlots) {
        mutableStateOf(strategySlots.firstOrNull()?.day ?: allWeekDays.first())
    }
    val shiftDayOptions = remember(strategySlots) { strategySlots.map { it.day }.distinct() }
    val shiftsForDay = remember(strategySlots, shiftDay) { strategySlots.filter { it.day == shiftDay } }
    var selectedShiftSlot by remember(shiftsForDay) { mutableStateOf(shiftsForDay.firstOrNull()) }
    // Every day (not just $shiftDay) that offers the exact same shift type as the
    // one just picked — e.g. picking "Mon Morning" here also surfaces "Wed Morning"
    // and "Fri Morning" if the host offers Morning shifts those days too, so the
    // specialist can commit to a multi-day-per-week pattern, not just $shiftDay.
    val sameShiftTypeSlots = remember(strategySlots, selectedShiftSlot) {
        val label = selectedShiftSlot?.groupLabel
        if (label == null) emptyList() else strategySlots.filter { it.groupLabel == label }
    }
    val commitmentDayOptions = remember(sameShiftTypeSlots) { sameShiftTypeSlots.map { it.day }.distinct() }
    var selectedCommitmentDays by remember(selectedShiftSlot) {
        mutableStateOf(setOfNotNull(shiftDay.takeIf { d -> sameShiftTypeSlots.any { it.day == d } }))
    }
    // Real ISO dates the specialist has picked so far, constrained (via the date
    // picker's SelectableDates below) to weekdays in selectedCommitmentDays and not
    // already locked by another accepted booking. Resets when the commitment-days
    // set changes so a stale date that no longer matches a chosen weekday can't
    // silently remain selected.
    var selectedCalendarDates by remember(selectedCommitmentDays) { mutableStateOf(listOf<String>()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // --- Day-Based: pick one or more real priced days, then either specific
    // one-time dates or a weekly-recurring commitment bounded by an end date —
    // mirrors Shift-Based's real-date approach above (one flat price per day;
    // the specialist configures real occurrences instead of picking which of 3
    // pre-set recurrence tiers to pay under) ---
    var selectedDayBasedDays by remember(strategySlots) { mutableStateOf(setOf<String>()) }
    var dayBasedIsRecurring by remember(selectedStrategyType) { mutableStateOf(false) }
    // "Until when?" bound for the recurring case — every occurrence of each
    // selected weekday from tomorrow through this date (inclusive) is generated
    // automatically below, rather than picked one at a time.
    var dayBasedUntilDate by remember(selectedDayBasedDays) { mutableStateOf<String?>(null) }
    // One-time case: real dates picked one at a time via the date picker, same
    // interaction as Shift-Based's commitment dates.
    var dayBasedManualDates by remember(selectedDayBasedDays, dayBasedIsRecurring) { mutableStateOf(listOf<String>()) }
    val dayBasedGeneratedDates = remember(selectedDayBasedDays, dayBasedUntilDate, strategySlots, acceptedForSpace, replacesBookingId) {
        val until = dayBasedUntilDate
        if (until == null) emptyList() else selectedDayBasedDays.flatMap { day ->
            val slot = strategySlots.firstOrNull { it.day == day } ?: return@flatMap emptyList<String>()
            weeklyOccurrencesUntil(day, until).filterNot { iso ->
                SpaceCalculationUtils.isCalendarDateLocked(slot, iso, space.id, acceptedForSpace, ignoreBookingId = replacesBookingId)
            }
        }
    }
    val dayBasedCalendarDates = if (dayBasedIsRecurring) dayBasedGeneratedDates else dayBasedManualDates
    var showDayBasedDatePicker by remember { mutableStateOf(false) }
    var showDayBasedUntilPicker by remember { mutableStateOf(false) }

    // The real, non-fabricated selection driving both price and what gets submitted —
    // one branch per strategy, each sourced from real RentableSlots above.
    val selectedSlotsForPricing: List<RentableSlot> = when (selectedStrategyType) {
        RentalStrategyType.MONTHLY -> strategySlots
        RentalStrategyType.HOURLY -> selectedHourlyCells.toList()
        RentalStrategyType.SHIFT_BASED -> listOfNotNull(selectedShiftSlot)
        RentalStrategyType.DAY_BASED -> strategySlots.filter { it.day in selectedDayBasedDays }
        null -> emptyList()
    }

    // Start Date — declared before the total because the occurrence count for a
    // weekly recurrence depends on where the term actually starts.
    val dateOptions = listOf("Immediate (Tomorrow)", "Next Monday", "1st of Next Month", "Custom Date")
    var selectedDateOption by remember { mutableStateOf(dateOptions[0]) }
    var customStartDate by remember { mutableStateOf("2026-09-01") }
    val startDate = remember(selectedDateOption, customStartDate) {
        SpaceCalculationUtils.resolveStartDate(selectedDateOption, customStartDate)
    }

    // The term applies to Monthly (rate x months) and to Shift/Day-Based (a
    // recurrence price x the real number of occurrences inside the term, counted on
    // the calendar from startDate — see countRecurrenceOccurrences). Hourly stays a
    // one-off booking of the chosen cells, so it's the only strategy without a term.
    val durationOptions = listOf(1, 2, 3, 6, 12)
    var selectedDurationMonths by remember { mutableStateOf(1) }
    // Shift-Based and Day-Based no longer use a preset Start Date / Duration Term
    // at all — the real calendar dates picked below already are the specialist's
    // exact commitment, so there's nothing left for those two controls to mean.
    val usesTerm = selectedStrategyType == RentalStrategyType.MONTHLY
    // Every strategy prices under a single FLAT key now — Shift-Based (item 7b)
    // and Day-Based (this session's identical rework) both replaced their 3-tier
    // recurrence pricing with one flat price + real picked occurrences.
    val effectiveRecurrence = BookingRecurrence.FLAT
    val totalCalculatedUsd = remember(
        selectedSlotsForPricing,
        effectiveRecurrence,
        selectedDurationMonths,
        selectedStrategyType,
        startDate,
        selectedCalendarDates,
        dayBasedCalendarDates
    ) {
        when (selectedStrategyType) {
            RentalStrategyType.SHIFT_BASED -> {
                // Real occurrence count x the one flat shift price — no term/
                // recurrence approximation involved.
                val price = selectedShiftSlot?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0
                selectedCalendarDates.size * price
            }
            RentalStrategyType.DAY_BASED -> {
                // Each picked date can carry a different day's price, so sum per
                // date rather than a single count x single price.
                dayBasedCalendarDates.sumOf { iso ->
                    val day = weekdayAbbreviationFromIso(iso)
                    strategySlots.firstOrNull { it.day == day }?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0
                }
            }
            else -> SpaceCalculationUtils.calculateTotalRentalPrice(selectedSlotsForPricing, effectiveRecurrence, selectedDurationMonths, startDate)
        }
    }
    // Per-day "N occurrences x $price" breakdown so the whole-commitment total is
    // explainable, not a number that appears from nowhere.
    val occurrenceBreakdown = remember(
        selectedSlotsForPricing,
        effectiveRecurrence,
        selectedDurationMonths,
        selectedStrategyType,
        startDate,
        selectedCalendarDates,
        dayBasedCalendarDates
    ) {
        when (selectedStrategyType) {
            RentalStrategyType.SHIFT_BASED -> {
                if (selectedCalendarDates.isEmpty()) "" else {
                    val price = selectedShiftSlot?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0
                    "${selectedCalendarDates.size} occurrence${if (selectedCalendarDates.size == 1) "" else "s"} × $${price.toInt()}"
                }
            }
            RentalStrategyType.DAY_BASED -> {
                if (dayBasedCalendarDates.isEmpty()) "" else {
                    dayBasedCalendarDates.groupBy { weekdayAbbreviationFromIso(it) }
                        .toSortedMap()
                        .entries.joinToString("\n") { (day, dates) ->
                            val price = strategySlots.firstOrNull { it.day == day }?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0
                            "$day: ${dates.size} occurrence${if (dates.size == 1) "" else "s"} × $${price.toInt()}"
                        }
                }
            }
            else -> ""
        }
    }

    var clinicalNotes by remember { mutableStateOf("") }

    // Attendee-mode state — only active when selectedSubdivision?.pricingMode == PER_ATTENDEE
    val isAttendeeMode = selectedSubdivision?.pricingMode == SubdivisionPricingMode.PER_ATTENDEE
    val enabledAttendeePackages = remember(attendeePackages) { attendeePackages.filter { it.isEnabled } }
    var attendeeCount by remember(isAttendeeMode) { mutableStateOf(1) }
    var selectedAttendeePackage by remember(isAttendeeMode, enabledAttendeePackages) {
        mutableStateOf(enabledAttendeePackages.firstOrNull())
    }
    val attendeeTotalUsd = remember(isAttendeeMode, attendeeCount, selectedAttendeePackage) {
        if (isAttendeeMode) attendeeCount * (selectedAttendeePackage?.pricePerAttendeeUsd ?: 0.0)
        else 0.0
    }

    val chosenSlotSummary = remember(
        selectedStrategyType,
        selectedSlotsForPricing,
        effectiveRecurrence,
        selectedShiftSlot,
        selectedCalendarDates,
        selectedDayBasedDays,
        dayBasedCalendarDates
    ) {
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
                val slot = selectedShiftSlot
                when {
                    slot == null -> "No shift selected yet"
                    selectedCalendarDates.isEmpty() -> "${slot.groupLabel.substringAfter("• ")} • no dates selected yet"
                    else -> "${slot.groupLabel.substringAfter("• ")} • ${selectedCalendarDates.size} date${if (selectedCalendarDates.size == 1) "" else "s"}"
                }
            }
            RentalStrategyType.DAY_BASED -> {
                when {
                    selectedDayBasedDays.isEmpty() -> "No days selected yet"
                    dayBasedCalendarDates.isEmpty() -> "${selectedDayBasedDays.sorted().joinToString(", ")} • no dates selected yet"
                    else -> "${selectedDayBasedDays.sorted().joinToString(", ")} • " +
                        "${dayBasedCalendarDates.size} date${if (dayBasedCalendarDates.size == 1) "" else "s"}"
                }
            }
            null -> "No availability configured for this ${if (hasSubdivisions) "room" else "space"} yet"
        }
    }

    // Same sheet shell as SpaceDetailsScreen's Check Availability sheet, so rebooking
    // and editing look like the booking flow the user already knows.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = VibrantBlue.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.surface),
        tonalElevation = 0.dp,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = Spacing.md, bottom = Spacing.xs)
                    .width(40.dp)
                    .height(4.dp)
                    .background(VibrantBlue.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape)
            )
        }
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xl)
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
                                text = headerTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = headerBadgeText)
                        }
                        Text(
                            text = "${space.title} • ${space.district}, ${space.governorate.displayName}",
                            style = MaterialTheme.typography.labelMedium,
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

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                ) {
                    // 1. Choose Subdivision (if any)
                    if (hasSubdivisions) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "1. Choose Subdivision / Room to Rent",
                                style = MaterialTheme.typography.bodyMedium,
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
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
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
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Type: ${sub.type.displayName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (sub.amenities.isNotEmpty()) {
                                                Text(
                                                    text = "Amenities: ${sub.amenities.joinToString()}",
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

                    // 2a. Attendee-mode UI — shown instead of strategy/pricing when pricingMode = PER_ATTENDEE
                    if (isAttendeeMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "${if (hasSubdivisions) "2" else "1"}. Attendees & Package",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Attendee count stepper
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Attendees",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledIconButton(
                                        onClick = { if (attendeeCount > 1) attendeeCount-- },
                                        enabled = attendeeCount > 1,
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = attendeeCount.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.defaultMinSize(minWidth = 36.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            val cap = selectedSubdivision?.capacity
                                            if (cap == null || attendeeCount < cap) attendeeCount++
                                        },
                                        enabled = selectedSubdivision?.capacity?.let { attendeeCount < it } ?: true,
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            selectedSubdivision?.capacity?.let { cap ->
                                Text(
                                    "Max capacity: $cap attendees",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Package selector
                            Text(
                                "Select Package",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (enabledAttendeePackages.isEmpty()) {
                                Text(
                                    "No attendee packages configured yet.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    enabledAttendeePackages.forEach { pkg ->
                                        val isSelected = selectedAttendeePackage?.id == pkg.id
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable { selectedAttendeePackage = pkg },
                                            shape = MaterialTheme.shapes.medium,
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Row(modifier = Modifier.padding(Spacing.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedAttendeePackage = pkg },
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(pkg.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                    if (pkg.description.isNotBlank()) {
                                                        Text(
                                                            pkg.description,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (pkg.inclusions.isNotEmpty()) {
                                                        Text(
                                                            "Includes: ${pkg.inclusions.joinToString()}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "\$${pkg.pricePerAttendeeUsd.toInt()}/pp",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Attendee price total
                            if (selectedAttendeePackage != null) {
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$attendeeCount attendees × \$${selectedAttendeePackage!!.pricePerAttendeeUsd.toInt()} " +
                                            "${selectedAttendeePackage!!.name}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "\$${String.format("%.2f", attendeeTotalUsd)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            // Time slot picker still needed for availability
                            Text(
                                "Availability Slot",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. Choose Renting Strategy / Availability Slot Strategy
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${if (hasSubdivisions) "2" else "1"}. " +
                                "${if (isAttendeeMode) "Select Availability Slot Type" else "Select Renting Strategy"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (hiddenLockedCount > 0) {
                            Text(
                                text = "$hiddenLockedCount slot${if (hiddenLockedCount == 1) " is" else "s are"} already booked by another p" +
                                    "rofessional and not shown.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (availableStrategyTypes.isEmpty()) {
                            Text(
                                text = if (allSlots.isEmpty()) {
                                    "This ${if (hasSubdivisions) "room" else "space"} has no bookable availability configured yet."
                                } else {
                                    "Every slot for this ${if (hasSubdivisions) "room" else "space"} is already booked."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableStrategyTypes.forEach { strategy ->
                                    FilterChip(
                                        selected = selectedStrategyType == strategy,
                                        onClick = { selectedStrategyType = strategy },
                                        label = { Text(strategy.displayName, style = MaterialTheme.typography.labelMedium) }
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                when (selectedStrategyType) {
                                    RentalStrategyType.MONTHLY -> {
                                        val rate = strategySlots.firstOrNull()?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0
                                        Text(
                                            text = "Exclusive full-space access on all operating days " +
                                                "(${strategySlots.map { it.day }.distinct().joinToString(", ")}), " +
                                                "billed at $${rate.toInt()} USD per month.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    RentalStrategyType.HOURLY -> {
                                        Text("Choose a day:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        // Natural-width chips in a wrapping FlowRow, not
                                        // equal-weight in a fixed Row — up to 7 items
                                        // forced into equal fractions of the dialog's
                                        // width squeezed each label down to its bare
                                        // 3-letter abbreviation with no breathing room.
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            hourlyDayOptions.forEach { day ->
                                                FilterChip(
                                                    selected = hourlyDay == day,
                                                    onClick = { hourlyDay = day },
                                                    label = { Text(day, style = MaterialTheme.typography.labelMedium) }
                                                )
                                            }
                                        }
                                        Text("Choose one or more priced hours:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        if (hourlyCellsForDay.isEmpty()) {
                                            Text(
                                                "No priced hours on $hourlyDay.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
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
                                                    label = { Text("${cell.startTime} · $${price.toInt()}", style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }

                                    RentalStrategyType.SHIFT_BASED -> {
                                        Text("Choose a day:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            shiftDayOptions.forEach { day ->
                                                FilterChip(
                                                    selected = shiftDay == day,
                                                    onClick = { shiftDay = day },
                                                    label = { Text(day, style = MaterialTheme.typography.labelMedium) }
                                                )
                                            }
                                        }
                                        Text("Choose a shift:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        if (shiftsForDay.isEmpty()) {
                                            Text(
                                                "No shifts offered on $shiftDay.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            shiftsForDay.forEach { slot ->
                                                FilterChip(
                                                    selected = selectedShiftSlot == slot,
                                                    onClick = { selectedShiftSlot = slot },
                                                    label = { Text(slot.groupLabel.substringAfter("• "), style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        if (selectedShiftSlot != null) {
                                            val shiftPrice = selectedShiftSlot?.pricesByRecurrence?.get(BookingRecurrence.FLAT) ?: 0.0

                                            // Step 1: which days share this same shift type — defaults
                                            // to just the day already browsed above; a specialist who
                                            // only wants a single one-time shift needs no extra tap.
                                            Text(
                                                "Choose commitment — which days offer the same ${selectedShiftSlot?.groupLabel?.substringAfter("• ")} shift:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                commitmentDayOptions.forEach { day ->
                                                    val isOn = day in selectedCommitmentDays
                                                    FilterChip(
                                                        selected = isOn,
                                                        onClick = {
                                                            selectedCommitmentDays = if (isOn) {
                                                                selectedCommitmentDays - day
                                                            } else {
                                                                selectedCommitmentDays + day
                                                            }
                                                        },
                                                        label = { Text(day, style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                }
                                            }

                                            // Step 2: real calendar dates matching those weekdays —
                                            // one at a time via the platform date picker, constrained
                                            // (SelectableDates below) to the chosen weekdays and to
                                            // dates not already locked by another accepted booking.
                                            Text(
                                                "Pick specific dates ($${shiftPrice.toInt()} each):",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (selectedCalendarDates.isNotEmpty()) {
                                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    selectedCalendarDates.sorted().forEach { dateStr ->
                                                        FilterChip(
                                                            selected = true,
                                                            onClick = { selectedCalendarDates = selectedCalendarDates - dateStr },
                                                            label = { Text(dateStr, style = MaterialTheme.typography.labelSmall) },
                                                            trailingIcon = {
                                                                Icon(Icons.Default.Close, contentDescription = "Remove date", modifier = Modifier.size(14.dp))
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            CustomButton(
                                                text = "Add a date",
                                                onClick = { showDatePicker = true },
                                                variant = CustomButtonVariant.OUTLINED,
                                                icon = Icons.Default.Add,
                                                enabled = selectedCommitmentDays.isNotEmpty(),
                                                compact = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    RentalStrategyType.DAY_BASED -> {
                                        Text("Choose one or more priced days:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        if (strategySlots.isEmpty()) {
                                            Text(
                                                "No days priced for this ${if (hasSubdivisions) "room" else "space"}.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            strategySlots.forEach { slot ->
                                                val isSelected = slot.day in selectedDayBasedDays
                                                val price = slot.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedDayBasedDays = if (isSelected) {
                                                            selectedDayBasedDays - slot.day
                                                        } else {
                                                            selectedDayBasedDays + slot.day
                                                        }
                                                    },
                                                    label = { Text("${slot.day} · $${price.toInt()}", style = MaterialTheme.typography.labelMedium) }
                                                )
                                            }
                                        }

                                        if (selectedDayBasedDays.isNotEmpty()) {
                                            Text("Commitment:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = !dayBasedIsRecurring,
                                                    onClick = { dayBasedIsRecurring = false },
                                                    label = { Text("One-time", style = MaterialTheme.typography.labelMedium) }
                                                )
                                                FilterChip(
                                                    selected = dayBasedIsRecurring,
                                                    onClick = { dayBasedIsRecurring = true },
                                                    label = { Text("Recurring, every week", style = MaterialTheme.typography.labelMedium) }
                                                )
                                            }

                                            if (dayBasedIsRecurring) {
                                                Text(
                                                    "Repeats on the chosen day(s) every week — until when?",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                CustomButton(
                                                    text = dayBasedUntilDate?.let { "Until $it" } ?: "Pick an end date",
                                                    onClick = { showDayBasedUntilPicker = true },
                                                    variant = CustomButtonVariant.OUTLINED,
                                                    icon = Icons.Default.DateRange,
                                                    compact = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                if (dayBasedUntilDate != null && dayBasedGeneratedDates.isEmpty()) {
                                                    Text(
                                                        "No open dates before that end date for the chosen day(s).",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "Pick specific dates:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (dayBasedManualDates.isNotEmpty()) {
                                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        dayBasedManualDates.sorted().forEach { dateStr ->
                                                            FilterChip(
                                                                selected = true,
                                                                onClick = { dayBasedManualDates = dayBasedManualDates - dateStr },
                                                                label = { Text(dateStr, style = MaterialTheme.typography.labelSmall) },
                                                                trailingIcon = {
                                                                    Icon(
                                                                        Icons.Default.Close,
                                                                        contentDescription = "Remove date",
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                OutlinedButton(
                                                    shape = MaterialTheme.shapes.medium,
                                                    onClick = { showDayBasedDatePicker = true },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Add a date", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }

                                    null -> {}
                                }
                            }
                        }
                    }

                    // 4. Start Date Selector — superseded by the real calendar-date
                    // picker above for Shift-Based (item 7b) and Day-Based (this
                    // session's identical rework): the dates picked there already
                    // are the exact commitment, so a separate abstract "start date"
                    // preset would mean nothing for either strategy.
                    if (selectedStrategyType != RentalStrategyType.SHIFT_BASED && selectedStrategyType != RentalStrategyType.DAY_BASED) {
                        Column {
                            Text(
                                text = "Select Starting Date",
                                style = MaterialTheme.typography.bodyMedium,
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
                                        label = { Text(option, style = MaterialTheme.typography.labelMedium) },
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
                    }

                    // 5. Rental Duration Term — every strategy except Hourly (see
                    // usesTerm above for what the term means per strategy).
                    if (usesTerm) {
                        Column {
                            Text(
                                text = "Rental Duration Term",
                                style = MaterialTheme.typography.bodyMedium,
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
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusSuccess
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "• Space hours remain AVAILABLE to other professionals until the space owner accepts your request.\n" +
                                       "• Once accepted by the owner, your chosen schedule ($chosenSlotSummary) is locked exclusively for your use.\n" +
                                       "• Payment is settled directly with the space owner (Cash / Wire Transfer).",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusOnSuccessContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Financial Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Total for this request",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    if (isAttendeeMode && selectedAttendeePackage != null) {
                                        Text(
                                            text = "$attendeeCount attendees × \$${selectedAttendeePackage!!.pricePerAttendeeUsd.toInt()} " +
                                                selectedAttendeePackage!!.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Text(
                                            text = "Based on your selection above",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Text(
                                    text = "$${(if (isAttendeeMode) attendeeTotalUsd else totalCalculatedUsd).toInt()} USD",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CarnationOrange
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                            if (!isAttendeeMode && occurrenceBreakdown.isNotBlank()) {
                                val breakdownHeader = if (
                                    selectedStrategyType == RentalStrategyType.SHIFT_BASED ||
                                        selectedStrategyType == RentalStrategyType.DAY_BASED
                                ) {
                                    "Your selected dates:"
                                } else {
                                    "Over $selectedDurationMonths month${if (selectedDurationMonths > 1) "s" else ""} from ${selectedDateOption.lowercase()}:"
                                }
                                Text(
                                    text = "$breakdownHeader\n$occurrenceBreakdown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    lineHeight = 16.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Selected Slot: $chosenSlotSummary",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Shift-Based and Day-Based have no abstract "start date" preset
                    // anymore — the earliest real calendar date picked is the most
                    // meaningful stand-in for this field.
                    val computedDate = when {
                        selectedStrategyType == RentalStrategyType.SHIFT_BASED -> selectedCalendarDates.sorted().firstOrNull() ?: ""
                        selectedStrategyType == RentalStrategyType.DAY_BASED -> dayBasedCalendarDates.sorted().firstOrNull() ?: ""
                        selectedDateOption == "Custom Date" -> customStartDate
                        else -> selectedDateOption
                    }
                    val canSubmit = selectedStrategyType != null && selectedSlotsForPricing.isNotEmpty() &&
                        (selectedStrategyType != RentalStrategyType.SHIFT_BASED || selectedCalendarDates.isNotEmpty()) &&
                        (selectedStrategyType != RentalStrategyType.DAY_BASED || dayBasedCalendarDates.isNotEmpty()) &&
                        (!isAttendeeMode || (attendeeCount > 0 && selectedAttendeePackage != null))

                    fun buildFormulaForSubmission(): RentalFormula? {
                        // Same synthesis SpaceDetailsScreen's renting-option preview and
                        // Check Availability panel use — see its doc comment for why
                        // this is shared rather than independent approximations. Only
                        // the description is overridden here, since this dialog
                        // already has a more specific, live-updating summary than the
                        // shared helper's generic slot label.
                        return SpaceCalculationUtils.representativeFormula(selectedSlotsForPricing, effectiveRecurrence)
                            ?.copy(scheduleDescription = chosenSlotSummary)
                    }

                    // In-App Only Request Button
                    CustomButton(
                        text = primaryButtonText,
                        variant = CustomButtonVariant.PRIMARY,
                        onClick = {
                            val formula = buildFormulaForSubmission()
                            if (formula == null) {
                                Toast.makeText(context, "Please select an available slot first.", Toast.LENGTH_SHORT).show()
                                return@CustomButton
                            }
                            viewModel.submitBookingRequest(
                                space = space,
                                context = context,
                                submission = ProHostViewModel.BookingSubmissionRequest(
                                    formula = formula,
                                    startDate = computedDate,
                                    durationMonths = if (usesTerm) selectedDurationMonths else 1,
                                    notes = clinicalNotes,
                                    alsoOpenWhatsApp = false,
                                    selectedDays = formula.daysOfWeek,
                                    selectedCalendarDates = when (selectedStrategyType) {
                                        RentalStrategyType.SHIFT_BASED -> selectedCalendarDates
                                        RentalStrategyType.DAY_BASED -> dayBasedCalendarDates
                                        else -> emptyList()
                                    },
                                    selectedStartHour = formula.startHour,
                                    selectedEndHour = formula.endHour,
                                    selectedShift = if (selectedStrategyType == RentalStrategyType.SHIFT_BASED) formula.shiftName else "",
                                    calculatedTotalUsd = if (isAttendeeMode) attendeeTotalUsd else totalCalculatedUsd,
                                    subdivisionId = selectedSubdivision?.id,
                                    subdivisionName = selectedSubdivision?.name,
                                    replacesBookingId = replacesBookingId,
                                    attendeeCount = if (isAttendeeMode) attendeeCount else 0,
                                    selectedAttendeePackageId = if (isAttendeeMode) selectedAttendeePackage?.id else null,
                                    attendeePackageName = if (isAttendeeMode) selectedAttendeePackage?.name else null,
                                    attendeePackagePriceUsd = if (isAttendeeMode) selectedAttendeePackage?.pricePerAttendeeUsd ?: 0.0 else 0.0
                                )
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
                        text = secondaryButtonText,
                        onClick = {
                            val formula = buildFormulaForSubmission() ?: return@CustomButton
                            viewModel.submitBookingRequest(
                                space = space,
                                context = context,
                                submission = ProHostViewModel.BookingSubmissionRequest(
                                    formula = formula,
                                    startDate = computedDate,
                                    durationMonths = if (usesTerm) selectedDurationMonths else 1,
                                    notes = clinicalNotes,
                                    alsoOpenWhatsApp = true,
                                    selectedDays = formula.daysOfWeek,
                                    selectedCalendarDates = when (selectedStrategyType) {
                                        RentalStrategyType.SHIFT_BASED -> selectedCalendarDates
                                        RentalStrategyType.DAY_BASED -> dayBasedCalendarDates
                                        else -> emptyList()
                                    },
                                    selectedStartHour = formula.startHour,
                                    selectedEndHour = formula.endHour,
                                    selectedShift = if (selectedStrategyType == RentalStrategyType.SHIFT_BASED) formula.shiftName else "",
                                    calculatedTotalUsd = if (isAttendeeMode) attendeeTotalUsd else totalCalculatedUsd,
                                    subdivisionId = selectedSubdivision?.id,
                                    subdivisionName = selectedSubdivision?.name,
                                    replacesBookingId = replacesBookingId,
                                    attendeeCount = if (isAttendeeMode) attendeeCount else 0,
                                    selectedAttendeePackageId = if (isAttendeeMode) selectedAttendeePackage?.id else null,
                                    attendeePackageName = if (isAttendeeMode) selectedAttendeePackage?.name else null,
                                    attendeePackagePriceUsd = if (isAttendeeMode) selectedAttendeePackage?.pricePerAttendeeUsd ?: 0.0 else 0.0
                                )
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

    // Real calendar-date picker for a Shift-Based commitment (item 7b) — the
    // platform's own Material3 DatePicker, constrained via SelectableDates rather
    // than a hand-rolled calendar grid, since that's a real, well-tested date-math
    // implementation instead of a from-scratch one prone to timezone/month-
    // boundary bugs. One date at a time, added to the running list above; the
    // specialist repeats "Add a date" for a multi-date commitment.
    if (showDatePicker && selectedShiftSlot != null) {
        val tomorrowUtcMillis = remember {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tomorrowUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    if (utcTimeMillis < tomorrowUtcMillis) return false
                    val dayAbbrev = weekdayAbbreviation(utcTimeMillis)
                    if (dayAbbrev !in selectedCommitmentDays) return false
                    val iso = isoDateString(utcTimeMillis)
                    if (iso in selectedCalendarDates) return false
                    val slotForDay = sameShiftTypeSlots.firstOrNull { it.day == dayAbbrev } ?: return false
                    return !SpaceCalculationUtils.isCalendarDateLocked(
                        slotForDay, iso, space.id, acceptedForSpace, ignoreBookingId = replacesBookingId
                    )
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val iso = isoDateString(millis)
                            if (iso !in selectedCalendarDates) selectedCalendarDates = selectedCalendarDates + iso
                        }
                        showDatePicker = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(shape = MaterialTheme.shapes.medium, onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Real calendar-date picker for Day-Based's one-time commitment — same
    // platform DatePicker/SelectableDates pattern as Shift-Based's above. One
    // date at a time, added to dayBasedManualDates; the specialist repeats
    // "Add a date" for a multi-date one-time commitment.
    if (showDayBasedDatePicker && selectedDayBasedDays.isNotEmpty()) {
        val tomorrowMillis = remember { tomorrowUtcMillis() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tomorrowMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    if (utcTimeMillis < tomorrowMillis) return false
                    val dayAbbrev = weekdayAbbreviation(utcTimeMillis)
                    if (dayAbbrev !in selectedDayBasedDays) return false
                    val iso = isoDateString(utcTimeMillis)
                    if (iso in dayBasedManualDates) return false
                    val slotForDay = strategySlots.firstOrNull { it.day == dayAbbrev } ?: return false
                    return !SpaceCalculationUtils.isCalendarDateLocked(
                        slotForDay, iso, space.id, acceptedForSpace, ignoreBookingId = replacesBookingId
                    )
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDayBasedDatePicker = false },
            confirmButton = {
                TextButton(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val iso = isoDateString(millis)
                            if (iso !in dayBasedManualDates) dayBasedManualDates = dayBasedManualDates + iso
                        }
                        showDayBasedDatePicker = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(shape = MaterialTheme.shapes.medium, onClick = { showDayBasedDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // "Until when?" bound for Day-Based's recurring commitment — any future date;
    // dayBasedGeneratedDates above walks from tomorrow to this date on the real
    // calendar, one occurrence per selected weekday per week.
    if (showDayBasedUntilPicker) {
        val tomorrowMillis = remember { tomorrowUtcMillis() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = tomorrowMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= tomorrowMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDayBasedUntilPicker = false },
            confirmButton = {
                TextButton(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis -> dayBasedUntilDate = isoDateString(millis) }
                        showDayBasedUntilPicker = false
                    }
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(shape = MaterialTheme.shapes.medium, onClick = { showDayBasedUntilPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
