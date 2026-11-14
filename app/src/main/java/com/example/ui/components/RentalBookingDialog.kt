package com.example.ui.components

import com.example.ui.util.timeRange
import com.example.ui.util.locationLine
import com.example.ui.util.uiPlural
import androidx.compose.ui.res.pluralStringResource
import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import androidx.compose.ui.res.stringResource
import com.example.ui.util.UiMessages
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
import com.example.ui.util.BookingDates
import com.example.ui.util.BookingRecurrence
import com.example.ui.util.IsoDate
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import java.text.NumberFormat
import java.util.*

/** UTC-based, matching the date picker's own UTC millis — see [rememberDatePickerState]. (Date helpers: ui/util/BookingDates.kt.) */
private fun weekdayAbbreviation(utcTimeMillis: Long): String = BookingDates.weekdayAbbreviation(utcTimeMillis)

private fun isoDateString(utcTimeMillis: Long): String = BookingDates.isoDateString(utcTimeMillis)

private fun weekdayAbbreviationFromIso(iso: String): String = BookingDates.weekdayAbbreviationFromIso(iso)

/** Midnight UTC tomorrow — the earliest selectable date across every real calendar-date picker in this dialog. */
private fun tomorrowUtcMillis(): Long = BookingDates.tomorrowUtcMillis()

/** Every real calendar date matching [weekdayAbbrev], from tomorrow through [untilIso] inclusive — Day-Based's "recurring weekly until a date". */
private fun weeklyOccurrencesUntil(weekdayAbbrev: String, untilIso: String): List<String> = BookingDates.weeklyOccurrencesUntil(weekdayAbbrev, untilIso)

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
    attendeePackages: List<AttendeePackage> = emptyList(),
    initialAttendeeCount: Int = 0
) {
    com.example.ui.components.TrackScreen("rebook")
    BookingSlotSelectorDialog(
        target = BookingDialogTarget(space = space, initialFormula = initialFormula, replacesBookingId = null, initialAttendeeCount = initialAttendeeCount),
        viewModel = viewModel,
        copy = BookingDialogCopy(
            headerTitle = uiText(R.string.re_book_this_space),
            headerBadgeText = uiText(R.string.real_time_availability),
            primaryButtonText = uiText(R.string.request),
            secondaryButtonText = uiText(R.string.request_and_contact)
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
    attendeePackages: List<AttendeePackage> = emptyList(),
    initialAttendeeCount: Int = 0
) {
    com.example.ui.components.TrackScreen("edit_booking")
    BookingSlotSelectorDialog(
        target = BookingDialogTarget(space = space, initialFormula = initialFormula, replacesBookingId = replacesBookingId, initialAttendeeCount = initialAttendeeCount),
        viewModel = viewModel,
        copy = BookingDialogCopy(
            headerTitle = uiText(R.string.propose_a_booking_change),
            headerBadgeText = uiText(R.string.replaces_your_current_booking),
            primaryButtonText = uiText(R.string.submit_change),
            secondaryButtonText = uiText(R.string.submit_change_and_contact)
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
    val replacesBookingId: String?,
    val initialAttendeeCount: Int = 0
)

data class BookingDialogCopy(
    val headerTitle: String,
    val headerBadgeText: String,
    val primaryButtonText: String,
    val secondaryButtonText: String
)

/**
 * Re-book / Edit on real dates (dated booking, 14 Nov 2026): the same calendar-first pickers as the listing's
 * availability sheet ([DateSlotPicker], [MonthlyPeriodPicker]) and the same checks before sending
 * ([com.example.ui.util.DateSlots]). An edit is not blocked by the booking it replaces.
 */
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
    val replacesBookingId = target.replacesBookingId
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    val hasSubdivisions = space.subdivisions.isNotEmpty()
    var selectedSubdivision by remember { mutableStateOf(space.subdivisions.firstOrNull()) }
    LaunchedEffect(space.id) {
        com.example.analytics.AnalyticsTracker.beginBookingCheckout(space, selectedSubdivision)
    }

    val allBookings by viewModel.bookingRequests.collectAsState()
    // Other people's accepted bookings come from the public occupancy projection.
    DisposableEffect(space.id) {
        viewModel.watchOccupancy(space.id)
        onDispose { viewModel.unwatchOccupancy(space.id) }
    }
    val occupancy by viewModel.occupancy.collectAsState()
    val sub = selectedSubdivision
    val subId = sub?.id
    val roomBookings = remember(allBookings, occupancy, space.id, subId, replacesBookingId) {
        (allBookings.filter { it.spaceId == space.id && it.status == BookingRequestStatus.ACCEPTED } + occupancy[space.id].orEmpty())
            .distinctBy { it.id }
            .filter { b -> b.id != replacesBookingId && (if (subId == null) b.subdivisionId.isNullOrBlank() else b.subdivisionId == subId) }
    }

    val pricing = sub?.pricing ?: space.pricing
    val schedule = sub?.scheduleOverride ?: space.schedule
    val closedDates = space.schedule.closedDates + sub?.scheduleOverride?.closedDates.orEmpty()
    val isMonthly = pricing.strategyType == RentalStrategyType.MONTHLY
    val today = remember { com.example.ui.util.IsoDate.todayIso() }

    var datedSelection by remember(subId) { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var monthlyStart by remember(subId) { mutableStateOf<String?>(null) }
    var monthlyMonths by remember(subId) { mutableStateOf(1) }
    var notesText by remember { mutableStateOf("") }

    val attendeeSub = sub?.takeIf { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
    val isAttendeeMode = attendeeSub != null
    val attendeeTiers = remember(attendeeSub, attendeePackages) {
        attendeeSub?.let { com.example.ui.util.AttendeePricing.tiersFor(it, attendeePackages) } ?: emptyList()
    }
    val attendeeMin = attendeeSub?.let { com.example.ui.util.AttendeePricing.minAttendees(it, attendeeTiers) } ?: 1
    val attendeeMax = attendeeSub?.let { com.example.ui.util.AttendeePricing.maxAttendees(it, attendeeTiers) }
    var attendeeCount by remember(attendeeSub?.id) {
        mutableStateOf(target.initialAttendeeCount.takeIf { it >= attendeeMin && (attendeeMax == null || it <= attendeeMax) } ?: attendeeMin)
    }
    val attendeeQuote = remember(attendeeSub, attendeeCount, attendeePackages) {
        attendeeSub?.let { com.example.ui.util.AttendeePricing.quote(it, attendeeCount, attendeePackages) }
    }

    val pickedSlots = remember(datedSelection, pricing, schedule, closedDates, roomBookings, today) {
        datedSlotsOf(datedSelection, today, pricing, schedule, closedDates, roomBookings)
    }
    val pickedDates = pickedSlots.map { it.date }.distinct().size
    val monthlyRate = pricing.monthly?.rateUsd ?: 0.0
    val totalUsd = when {
        isMonthly -> monthlyRate * monthlyMonths
        isAttendeeMode -> attendeeQuote?.totalUsd ?: 0.0
        else -> pickedSlots.sumOf { it.priceUsd }
    }
    val hasSelection = if (isMonthly) monthlyStart != null else pickedSlots.isNotEmpty()
    val canSubmit = hasSelection && (!isAttendeeMode || attendeeQuote != null)
    val monthlyTerm = monthlyStart?.let { com.example.ui.util.DateSlots.monthlyTerm(it, monthlyMonths) }

    fun submit(alsoOpenWhatsApp: Boolean) {
        val start = monthlyStart
        val problem = if (isMonthly) {
            com.example.ui.util.DateSlots.monthlyProblem(start.orEmpty(), monthlyMonths, today, pricing, roomBookings)
        } else {
            com.example.ui.util.DateSlots.bookedSlotsProblem(pickedSlots, today, pricing, schedule, closedDates, roomBookings, checkPrices = !isAttendeeMode)
        }
        if (problem != null) {
            UiMessages.show(problem)
            return
        }
        val summary = if (isMonthly) "$start × $monthlyMonths" else com.example.ui.util.DateSlots.describe(pickedSlots)
        val hh = { hour: Int -> hour.toString().padStart(2, '0') + ":00" }
        val startHour = if (isMonthly) schedule.openingHour else hh(pickedSlots.minOf { it.startHour })
        val endHour = if (isMonthly) schedule.closingHour else hh(pickedSlots.maxOf { it.endHour })
        val weekdays = pickedSlots.map { com.example.ui.util.DateSlots.weekdayOf(it.date) }.distinct()
        val shiftLabel = pickedSlots.firstOrNull()?.shiftName
            ?.let { n -> ShiftName.entries.firstOrNull { it.name == n }?.displayName }.orEmpty()
        val formula = RentalFormula(
            type = SpaceCalculationUtils.legacyFormulaType(pricing.strategyType),
            rateUsd = if (isMonthly) monthlyRate else pickedSlots.firstOrNull()?.priceUsd ?: 0.0,
            scheduleDescription = summary,
            daysOfWeek = if (isMonthly) schedule.operatingDays else weekdays,
            startHour = startHour,
            endHour = endHour,
            shiftName = shiftLabel.ifBlank { "Full Day" }
        )
        val quote = attendeeQuote
        viewModel.submitBookingRequest(
            space = space,
            submission = ProHostViewModel.BookingSubmissionRequest(
                formula = formula,
                startDate = if (isMonthly) start.orEmpty() else pickedSlots.minOf { it.date },
                durationMonths = if (isMonthly) monthlyMonths else 1,
                notes = notesText,
                alsoOpenWhatsApp = alsoOpenWhatsApp,
                selectedDays = if (isMonthly) emptyList() else weekdays,
                selectedCalendarDates = pickedSlots.map { it.date }.distinct().sorted(),
                selectedStartHour = startHour,
                selectedEndHour = endHour,
                selectedShift = shiftLabel,
                calculatedTotalUsd = totalUsd,
                subdivisionId = sub?.id,
                subdivisionName = sub?.name,
                replacesBookingId = replacesBookingId,
                attendeeCount = if (isAttendeeMode) quote?.attendees ?: 0 else 0,
                selectedAttendeePackageId = if (isAttendeeMode) quote?.tier?.id else null,
                attendeePackageName = if (isAttendeeMode) quote?.tier?.name else null,
                attendeePackagePriceUsd = if (isAttendeeMode) quote?.tier?.pricePerAttendeeUsd ?: 0.0 else 0.0,
                bookedSlots = if (isMonthly) emptyList() else pickedSlots,
                dateModel = 2
            )
        )
        onRequestSubmitted()
        onDismiss()
    }

    // Same sheet shell as the listing's Check Availability sheet.
    ProHostBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0) },
        accentTint = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(text = copy.headerTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = copy.headerBadgeText)
                    Text(
                        text = "${space.title} • ${space.locationLine()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                if (hasSubdivisions) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        space.subdivisions.forEach { room ->
                            FilterChip(
                                selected = selectedSubdivision?.id == room.id,
                                onClick = { selectedSubdivision = room },
                                label = { Text(room.name, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }

                if (isAttendeeMode) {
                    AttendeeCountAndTiers(
                        count = attendeeCount,
                        min = attendeeMin,
                        max = attendeeMax,
                        tiers = attendeeTiers,
                        quote = attendeeQuote,
                        onCountChange = { attendeeCount = it }
                    )
                }

                Text(
                    text = if (isMonthly) stringResource(R.string.pick_start_date_and_length) else stringResource(R.string.pick_dates_then_times),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isMonthly) {
                    if (monthlyRate <= 0.0) {
                        Text(stringResource(R.string.no_slots_configured_for_this_space), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MonthlyPeriodPicker(
                            pricing = pricing,
                            bookings = roomBookings,
                            start = monthlyStart,
                            months = monthlyMonths,
                            onChange = { start, months -> monthlyStart = start; monthlyMonths = months.coerceAtLeast(1) },
                            today = today
                        )
                    }
                } else if (!pricing.hasRealPrice()) {
                    Text(stringResource(R.string.no_slots_configured_for_this_space), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DateSlotPicker(
                        pricing = pricing,
                        schedule = schedule,
                        closedDates = closedDates,
                        bookings = roomBookings,
                        selection = datedSelection,
                        onSelectionChange = { datedSelection = it },
                        showPrices = !isAttendeeMode,
                        today = today
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = stringResource(R.string.requirements_notes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InputField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = "",
                        placeholder = stringResource(R.string.intended_use_team_size_special_equipment_n),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            if (hasSelection) {
                val term = monthlyTerm
                val line = if (isMonthly && term != null) {
                    stringResource(
                        R.string.period_x_to_x,
                        datedLongLabel(term.first, locale),
                        datedLongLabel(com.example.ui.util.IsoDate.addDays(term.second, -1) ?: term.second, locale)
                    )
                } else {
                    stringResource(
                        R.string.dates_and_times_summary,
                        pluralStringResource(R.plurals.n_dates, pickedDates, pickedDates),
                        pluralStringResource(R.plurals.n_times, pickedSlots.size, pickedSlots.size)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(com.example.util.NumberFormats.usdAuto(totalUsd), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // Stacked full width: side by side, "Request and Contact" was cut off on small phones.
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProOutlinedButton(
                    text = copy.primaryButtonText,
                    onClick = { submit(alsoOpenWhatsApp = false) },
                    icon = Icons.AutoMirrored.Filled.Send,
                    compact = true,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                )
                CustomButton(
                    text = copy.secondaryButtonText,
                    onClick = { submit(alsoOpenWhatsApp = true) },
                    variant = CustomButtonVariant.WHATSAPP,
                    iconPainter = painterResource(id = R.drawable.ic_whatsapp),
                    compact = true,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Attendee count stepper (bounded by the room's min/max) plus the room's tiers, with
 * the tier this count falls in highlighted and the whole-booking total.
 * Shared by the booking dialog and the Check Availability sheet.
 */
@Composable
fun AttendeeCountAndTiers(
    count: Int,
    min: Int,
    max: Int?,
    tiers: List<AttendeePackage>,
    quote: com.example.ui.util.AttendeePricing.Quote?,
    onCountChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.attendees), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (max != null) stringResource(R.string.x_x_people, min, max) else stringResource(R.string.at_least_x, min),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > min,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) { Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.fewer_attendees), modifier = Modifier.size(18.dp)) }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.defaultMinSize(minWidth = 36.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            FilledIconButton(
                onClick = { onCountChange(count + 1) },
                enabled = max == null || count < max,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.more_attendees), modifier = Modifier.size(18.dp)) }
        }

        if (tiers.isEmpty()) {
            Text(
                stringResource(R.string.this_room_has_no_attendee_pricing_yet_cont),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            tiers.forEach { tier ->
                val active = quote?.tier?.id == tier.id
                val range = tier.maxAttendees?.let { "${tier.minAttendees}–$it" } ?: "${tier.minAttendees}+"
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (active) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tier.name.ifBlank { "$range people" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.x_people, range), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (tier.inclusions.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.includes_x, tier.inclusions.joinToString()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.x_person, com.example.ui.util.AttendeePricing.formatUsd(tier.pricePerAttendeeUsd)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (quote == null) {
                Text(
                    stringResource(R.string.no_tier_covers_x_attendees_adjust_the_numb, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (quote != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        com.example.ui.util.AttendeePricing.describe(quote),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$${com.example.ui.util.AttendeePricing.formatUsd(quote.totalUsd)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                stringResource(R.string.one_price_for_the_whole_booking_not_multip),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
