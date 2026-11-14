package com.example.ui.screens

import com.example.ui.util.localizedLabel
import com.example.ui.util.locationLine
import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.Radius
import com.example.ui.util.UiMessages
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
import com.example.ui.components.Button
import com.example.ui.components.ProHostButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The "Check Availability" sheet of a listing, booking on real dates (owner decision 14 Nov 2026): room folder tabs, the
 * attendee block for per-person rooms, then the calendar-first [DateSlotPicker] (hourly / shift / day rooms) or the
 * [MonthlyPeriodPicker] (monthly rooms), the request bar and the send confirmation. State is owned by
 * SpaceDetailsScreenContent (so the page, bottom strip and sheet share one selection) and handed in as MutableState holders;
 * room choice and sending go back through the screen's selectRoom / requestWithRequirements / sendDatedRequest.
 * What a date offers comes from the shared [com.example.ui.util.DateSlots] engine, which the server re-checks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvailabilitySheet(
    liveSpace: SpaceListing,
    viewModel: ProHostViewModel,
    acceptedBookings: List<RentalBookingRequest>,
    architectureSchema: SpaceArchitectureSchema,
    isSpecialistViewer: Boolean,
    selectedSubdivisionId: String?,
    availabilitySheetState: SheetState,
    isSendingSlotRequest: Boolean,
    availabilityPanelStateHolder: MutableState<String>,
    datedSelectionState: MutableState<Map<String, Set<String>>>,
    monthlyStartState: MutableState<String?>,
    monthlyMonthsState: MutableState<Int>,
    showSendConfirmState: MutableState<Boolean>,
    onSelectRoom: (Subdivision) -> Unit,
    onRequest: () -> Unit,
    onSendRequest: (
        slots: List<BookedSlot>,
        monthlyStart: String?,
        months: Int,
        attendeeQuote: com.example.ui.util.AttendeePricing.Quote?
    ) -> Unit
) {
    com.example.ui.components.TrackScreen("availability")
    var availabilityPanelState by availabilityPanelStateHolder
    var datedSelection by datedSelectionState
    var monthlyStart by monthlyStartState
    var monthlyMonths by monthlyMonthsState
    var showSendConfirm by showSendConfirmState
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()

    val selectedSubdivision = remember(selectedSubdivisionId, liveSpace) {
        if (selectedSubdivisionId != null) liveSpace.subdivisions.find { it.id == selectedSubdivisionId } else null
    }
    // The room's own pricing and hours, or the whole place's when the listing has no rooms.
    val pricing = selectedSubdivision?.pricing ?: liveSpace.pricing
    val schedule = selectedSubdivision?.scheduleOverride ?: liveSpace.schedule
    val closedDates = remember(liveSpace, selectedSubdivision) {
        liveSpace.schedule.closedDates + selectedSubdivision?.scheduleOverride?.closedDates.orEmpty()
    }
    // Bookings that hold this room (or the whole place).
    val roomBookings = remember(acceptedBookings, selectedSubdivisionId, liveSpace.id) {
        acceptedBookings.filter { b ->
            b.spaceId == liveSpace.id && (if (selectedSubdivisionId == null) b.subdivisionId.isNullOrBlank() else b.subdivisionId == selectedSubdivisionId)
        }
    }
    val isMonthly = pricing.strategyType == RentalStrategyType.MONTHLY
    val today = remember { com.example.ui.util.IsoDate.todayIso() }

    val attendeeSub = selectedSubdivision?.takeIf { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
    val isAttendeeMode = attendeeSub != null
    val attendeeTiers = remember(attendeeSub, architectureSchema) {
        attendeeSub?.let { com.example.ui.util.AttendeePricing.tiersFor(it, architectureSchema.attendeePackages) } ?: emptyList()
    }
    val attendeeMin = attendeeSub?.let { com.example.ui.util.AttendeePricing.minAttendees(it, attendeeTiers) } ?: 1
    val attendeeMax = attendeeSub?.let { com.example.ui.util.AttendeePricing.maxAttendees(it, attendeeTiers) }
    var sheetAttendeeCount by remember(attendeeSub?.id) { mutableStateOf(attendeeMin) }
    val sheetAttendeeQuote = remember(attendeeSub, sheetAttendeeCount, architectureSchema) {
        attendeeSub?.let { com.example.ui.util.AttendeePricing.quote(it, sheetAttendeeCount, architectureSchema.attendeePackages) }
    }

    // The dated slots the current selection stands for, priced from what each date offers right now.
    val pickedSlots = remember(datedSelection, pricing, schedule, closedDates, roomBookings, today) {
        com.example.ui.components.datedSlotsOf(datedSelection, today, pricing, schedule, closedDates, roomBookings)
    }
    val pickedDates = pickedSlots.map { it.date }.distinct().size
    val monthlyRate = pricing.monthly?.rateUsd ?: 0.0
    val totalUsd = when {
        isMonthly -> monthlyRate * monthlyMonths
        isAttendeeMode -> sheetAttendeeQuote?.totalUsd ?: 0.0
        else -> pickedSlots.sumOf { it.priceUsd }
    }
    val hasSelection = if (isMonthly) monthlyStart != null else pickedSlots.isNotEmpty()
    val canSubmit = hasSelection && (!isAttendeeMode || sheetAttendeeQuote != null)
    // "Tue 17 Nov · 10:00–12:00" lines, consecutive hours merged.
    val selectionLines = com.example.ui.util.DateSlots.ranges(pickedSlots).map { (date, start, end) ->
        "${com.example.ui.components.datedLabel(date, locale)} · ${start.toString().padStart(2, '0')}:00–${end.toString().padStart(2, '0')}:00"
    }
    val monthlyTerm = monthlyStart?.let { com.example.ui.util.DateSlots.monthlyTerm(it, monthlyMonths) }
    val monthlyLine = monthlyTerm?.let {
        stringResource(
            R.string.period_x_to_x,
            com.example.ui.components.datedLongLabel(it.first, locale),
            com.example.ui.components.datedLongLabel(com.example.ui.util.IsoDate.addDays(it.second, -1) ?: it.second, locale)
        )
    }

    fun clearSelection() {
        datedSelection = emptyMap()
        monthlyStart = null
        monthlyMonths = 1
    }

    ResizableBottomSheet(
        onDismissRequest = {
            availabilityPanelState = if (selectedSubdivisionId != null) "peek" else "hidden"
            clearSelection()
        },
        sheetState = availabilitySheetState,
        // Blue wash (matches the "Press to see option availability" trigger bar)
        // so the trigger and the sheet it opens read as one consistent design.
        accentTint = true,
        // Pinned title; the rest scrolls. The standard sheet handle moves it half ↔ full.
        header = {
            ProSectionHeader(
                title = stringResource(R.string.check_availability),
                subtitle = if (isMonthly) stringResource(R.string.pick_start_date_and_length) else stringResource(R.string.pick_dates_then_times),
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Same folder tabs as the page: switching here switches the room the
            // sheet shows (and drops what was picked for the previous room).
            if (liveSpace.subdivisions.size > 1 && selectedSubdivisionId != null) {
                RoomFolderTabs(
                    rooms = liveSpace.subdivisions,
                    selectedId = selectedSubdivisionId.orEmpty(),
                    onSelect = { onSelectRoom(it) }
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            stringResource(R.string.attendee_details),
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

            if (isMonthly) {
                if (monthlyRate <= 0.0) {
                    Text(stringResource(R.string.no_slots_configured_for_this_space), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    com.example.ui.components.MonthlyPeriodPicker(
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
                com.example.ui.components.DateSlotPicker(
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

            // ── Action bar ───────────────────────────────────────────────
            if (!isSpecialistViewer) {
                Text(
                    stringResource(R.string.viewing_as_host_only_professionals_can_req),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (canSubmit) {
                val summary = if (isMonthly) monthlyLine.orEmpty() else stringResource(
                    R.string.dates_and_times_summary,
                    androidx.compose.ui.res.pluralStringResource(R.plurals.n_dates, pickedDates, pickedDates),
                    androidx.compose.ui.res.pluralStringResource(R.plurals.n_times, pickedSlots.size, pickedSlots.size)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(com.example.util.NumberFormats.usdAuto(totalUsd), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(
                        colors = proPrimaryButtonColors(),
                        onClick = { onRequest() },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(if (isMonthly) R.string.request_this_period else R.string.request_these_dates), fontWeight = FontWeight.Bold)
                    }
                    // WhatsApp: a structured message listing the chosen dates and times
                    Button(
                        onClick = {
                            val lines = (if (isMonthly) listOfNotNull(monthlyLine) else selectionLines)
                                .mapIndexed { i, line -> "  ${i + 1}. $line" }.joinToString("\n")
                            val attendeeBlock = sheetAttendeeQuote?.let { q ->
                                "\n\n👥 ${com.example.ui.util.AttendeePricing.describe(q)}" +
                                    uiText(R.string.total_for_the_booking_x_usd, com.example.ui.util.AttendeePricing.formatUsd(q.totalUsd))
                            } ?: ""
                            val message = uiText(R.string.hello_i_m_interested_in_booking_x, liveSpace.title) +
                                " ${liveSpace.locationLine()}" +
                                uiText(R.string.selected_slots_x_xx, if (isMonthly) 1 else selectionLines.size, lines, attendeeBlock) +
                                uiText(R.string.are_these_slots_still_available)
                            try {
                                val whatsappUrl = "https://wa.me/${viewModel.formatWhatsAppNumber(liveSpace.ownerPhone)}" +
                                    "?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
                                com.example.ui.util.PlatformActions.require().openUrl(whatsappUrl)
                            } catch (e: Exception) {
                                UiMessages.show(uiText(R.string.whatsapp_not_installed))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ProHostButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.whatsapp), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    // ── Confirm-and-send popup ───────────────────────────────────────────
    if (showSendConfirm) {
        com.example.ui.components.TrackScreen("booking_confirm", "Dialog")
        val dialogQuote = if (isAttendeeMode) sheetAttendeeQuote else null
        val dialogLines = if (isMonthly) listOfNotNull(monthlyLine) else selectionLines

        ProHostDialog(
            onDismissRequest = { if (!isSendingSlotRequest) showSendConfirm = false },
            icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
            title = { Text(stringResource(R.string.confirm_your_request)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Space / location / room summary
                    listOf(
                        "Space" to liveSpace.title,
                        "Location" to liveSpace.locationLine(),
                        "Room" to (selectedSubdivision?.name ?: "Whole Space")
                    ).forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider()
                    Text(stringResource(if (isMonthly) R.string.period else R.string.dates),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dialogLines.take(6).forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (dialogLines.size > 6) {
                        Text(stringResource(R.string.x_more_slots, dialogLines.size - 6), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Attendee block
                    if (dialogQuote != null) {
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.attendees), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.x_people, dialogQuote.attendees), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.price), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.x_person, com.example.ui.util.AttendeePricing.formatUsd(dialogQuote.tier.pricePerAttendeeUsd)) +
                                    dialogQuote.tier.name.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.estimated_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$${String.format(java.util.Locale.US, "%.2f", totalUsd)} USD",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = proPrimaryButtonColors(),
                    onClick = { onSendRequest(pickedSlots, monthlyStart, monthlyMonths, dialogQuote) },
                    enabled = !isSendingSlotRequest
                ) {
                    if (isSendingSlotRequest) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.send))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirm = false }, enabled = !isSendingSlotRequest) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
