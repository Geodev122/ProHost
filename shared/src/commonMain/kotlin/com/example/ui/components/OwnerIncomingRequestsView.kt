package com.example.ui.components

import com.example.ui.util.localizedLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import com.example.shared.resources.Res
import com.example.shared.resources.accept
import com.example.shared.resources.accept_booking
import com.example.shared.resources.accept_share_rules
import com.example.shared.resources.booking_request
import com.example.shared.resources.can_t_reach_server
import com.example.shared.resources.cancel
import com.example.shared.resources.change_price
import com.example.shared.resources.change_request
import com.example.shared.resources.check_your_connection_cached_requests_may
import com.example.shared.resources.confirm_decline
import com.example.shared.resources.decline_booking_request
import com.example.shared.resources.declined_x
import com.example.shared.resources.details_and_releases_the_old_booking
import com.example.shared.resources.e_g_space_already_reserved_for_client_work
import com.example.shared.resources.incoming_requests
import com.example.shared.resources.message_on_whatsapp
import com.example.shared.resources.more_options_menu
import com.example.shared.resources.no_x_requests
import com.example.shared.resources.optionally_share_your_space_rules_entry_in
import com.example.shared.resources.pdf_jpg_or_png_the_specialist_can_view_thi
import com.example.shared.resources.provide_a_note_or_scheduling_reason_for_th
import com.example.shared.resources.reason_optional
import com.example.shared.resources.reject
import com.example.shared.resources.review_and_confirm_professional_rental_con
import com.example.shared.resources.space_rules_access_guidance_optional
import com.example.shared.resources.this_specialist_wants_to_change_a_booking
import com.example.shared.resources.whatsapp
import com.example.shared.resources.x_total
import com.example.shared.resources.x_usd
import com.example.shared.resources.you_can_skip_this_the_specialist_will_be_i
import com.example.ui.util.appString
import com.example.ui.util.sharedPlural
import com.example.ui.util.sharedText

/**
 * Dedicated UI Component for Space Owners to view, review, accept, or reject incoming BookingRequests.
 * Automatically marks accepted hours as unavailable for public display.
 */
@Composable
fun OwnerIncomingRequestsView(
    requests: List<BookingRequest>,
    spaces: List<SpaceListing>,
    viewModel: ProHostViewModel,
    modifier: Modifier = Modifier
) {
    val hasLoadedBookingsOnce by viewModel.hasLoadedBookingsOnce.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    var selectedFilter by remember { mutableStateOf("PENDING") } // PENDING, ACCEPTED, REJECTED
    var rejectingRequestId by remember { mutableStateOf<String?>(null) }
    var rejectionReasonInput by remember { mutableStateOf("") }
    var acceptingRequestId by remember { mutableStateOf<String?>(null) }
    var agreementDocState by remember { mutableStateOf(DocumentPickerState()) }
    var detailRequest by remember { mutableStateOf<BookingRequest?>(null) }
    var changePriceFor by remember { mutableStateOf<BookingRequest?>(null) }

    val pendingCount = requests.count { it.status == BookingRequestStatus.PENDING }
    val acceptedCount = requests.count { it.status == BookingRequestStatus.ACCEPTED }
    val rejectedCount = requests.count { it.status == BookingRequestStatus.REJECTED }

    val filteredRequests = remember(requests, selectedFilter) {
        when (selectedFilter) {
            "ACCEPTED" -> requests.filter { it.status == BookingRequestStatus.ACCEPTED }
            "REJECTED" -> requests.filter { it.status == BookingRequestStatus.REJECTED }
            else -> requests.filter { it.status == BookingRequestStatus.PENDING }
        }
    }

    ProSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Header with badge
            ProSectionHeader(
                title = appString(Res.string.incoming_requests),
                subtitle = appString(Res.string.review_and_confirm_professional_rental_con),
                icon = Icons.Default.Inbox,
                trailingContent = {
                    if (pendingCount > 0) {
                        ProStatusBadge(type = ProBadgeType.CUSTOM_WARNING, customText = sharedText("x_action_needed", pendingCount))
                    } else {
                        ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = sharedText("x_total_2", requests.size))
                    }
                }
            )

            // Filter Chips Bar — a LazyRow (rather than a fixed-width Row) so the
            // chips scroll horizontally instead of compressing/clipping on narrow
            // screens, since "Declined (N)" was the one getting squeezed last.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(
                    listOf(
                        "PENDING" to "Pending ($pendingCount)",
                        "ACCEPTED" to "Accepted ($acceptedCount)",
                        "REJECTED" to "Declined ($rejectedCount)"
                    )
                ) { (filterKey, label) ->
                    val isSelected = selectedFilter == filterKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filterKey },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (!hasLoadedBookingsOnce && !isOffline) {
                // The first Firestore snapshot hasn't arrived yet — without this,
                // an owner with real incoming requests briefly saw "No Requests"
                // before the real list streamed in.
                ShimmerLoadingList(count = 2, itemHeight = 96.dp)
            } else if (!hasLoadedBookingsOnce && isOffline) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            appString(Res.string.can_t_reach_server),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            appString(Res.string.check_your_connection_cached_requests_may),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (filteredRequests.isEmpty()) {
                ProEmptyState(
                    title = appString(Res.string.no_x_requests, selectedFilter.lowercase().replaceFirstChar { it.uppercase() }),
                    description = if (selectedFilter == "PENDING") {
                        sharedText("all_incoming_booking_requests_are_processe")
                    } else {
                        sharedText("no_rental_booking_requests_found_under_thi")
                    },
                    icon = Icons.Default.Inbox,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    filteredRequests.forEach { request ->
                        OwnerBookingRequestCard(
                            request = request,
                            onClick = { detailRequest = request }
                        )
                    }
                }
                // Detail sheet
                detailRequest?.let { req ->
                    OwnerRequestDetailSheet(
                        request = req,
                        viewModel = viewModel,
                        onDismiss = { detailRequest = null },
                        onAccept = {
                            detailRequest = null
                            acceptingRequestId = req.id
                            agreementDocState = DocumentPickerState()
                        },
                        onReject = {
                            detailRequest = null
                            rejectingRequestId = req.id
                            rejectionReasonInput = ""
                        },
                        onWhatsApp = {
                            viewModel.launchWhatsAppToPractitioner(req)
                        },
                        onChangePrice = {
                            detailRequest = null
                            changePriceFor = req
                        }
                    )
                }
                changePriceFor?.let { req ->
                    ChangePriceSheet(viewModel = viewModel, onDismiss = { changePriceFor = null }, booking = req)
                }
            }
        }
    }

    // Rejection Reason Modal Dialog
    if (rejectingRequestId != null) {
        ProHostDialog(
            onDismissRequest = { rejectingRequestId = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(appString(Res.string.decline_booking_request), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = appString(Res.string.provide_a_note_or_scheduling_reason_for_th),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InputField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = appString(Res.string.reason_optional),
                        placeholder = appString(Res.string.e_g_space_already_reserved_for_client_work),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                CustomButton(
                    text = appString(Res.string.confirm_decline),
                    onClick = {
                        val reqId = rejectingRequestId
                        if (reqId != null) {
                            viewModel.rejectBookingRequest(reqId, rejectionReasonInput.ifBlank { sharedText("declined_by_space_owner") })
                        }
                        rejectingRequestId = null
                    },
                    variant = CustomButtonVariant.DANGER,
                    compact = true
                )
            },
            dismissButton = {
                TextButton(onClick = { rejectingRequestId = null }) {
                    Text(appString(Res.string.cancel))
                }
            }
        )
    }

    // Accept Booking Dialog — upload of space rules/access info is optional
    if (acceptingRequestId != null) {
        val reqId = acceptingRequestId!!
        ProHostDialog(
            onDismissRequest = { acceptingRequestId = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(appString(Res.string.accept_booking), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = appString(Res.string.optionally_share_your_space_rules_entry_in) +
                            appString(Res.string.you_can_skip_this_the_specialist_will_be_i),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DocumentPickerField(
                        label = appString(Res.string.space_rules_access_guidance_optional),
                        helperText = appString(Res.string.pdf_jpg_or_png_the_specialist_can_view_thi),
                        state = agreementDocState,
                        onStateChanged = { agreementDocState = it },
                        modifier = Modifier.fillMaxWidth(),
                        required = false
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = proPrimaryButtonColors(),
                    onClick = {
                        viewModel.acceptBookingRequest(reqId, agreementDocState.file)
                        acceptingRequestId = null
                    }
                ) {
                    Text(if (agreementDocState.isSelected) appString(Res.string.accept_share_rules) else appString(Res.string.accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { acceptingRequestId = null }) {
                    Text(appString(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
fun OwnerBookingRequestCard(
    request: BookingRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (request.status) {
        BookingRequestStatus.PENDING -> MaterialTheme.proColors.warning
        BookingRequestStatus.ACCEPTED -> MaterialTheme.proColors.info
        BookingRequestStatus.REJECTED -> MaterialTheme.colorScheme.error
        BookingRequestStatus.CANCELLED -> MaterialTheme.colorScheme.outlineVariant
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = request.spaceTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = request.practitionerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                val attendeeLine = com.example.ui.util.AttendeePricing.bookingSummary(request)
                Text(
                    text = if (attendeeLine != null) appString(Res.string.x_total, attendeeLine) else appString(Res.string.x_usd, request.totalAmountUsd.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = sharedText("pending"))
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = sharedText("declined"))
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = sharedText("cancelled"))
                }
                // A change to a booking this specialist already has: accepting replaces the old one.
                if (!request.replacesBookingId.isNullOrBlank() && request.status == BookingRequestStatus.PENDING) {
                    ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = sharedText("change_request_2"))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerRequestDetailSheet(
    request: BookingRequest,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onWhatsApp: () -> Unit,
    onChangePrice: (() -> Unit)? = null,
    // Report / Block the person who sent the request (three-dot menu in the header); null hides it.
    viewModel: ProHostViewModel? = null
) {
    val formattedTime = remember(request.createdAt) { com.example.ui.util.IsoDate.usShortDateTime(request.createdAt) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val attendeeLine = com.example.ui.util.AttendeePricing.bookingSummary(request)
    val chosenDays = request.selectedDays.takeIf { it.isNotEmpty() } ?: request.formula.daysOfWeek
    val chosenHours = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank())
        "${request.selectedStartHour}–${request.selectedEndHour}" else "${request.formula.startHour}–${request.formula.endHour}"

    ProHostBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (request.replacesBookingId.isNullOrBlank()) appString(Res.string.booking_request) else appString(Res.string.change_request),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (viewModel != null && request.practitionerId.isNotBlank()) {
                    var showReportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showReportMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = appString(Res.string.more_options_menu))
                        }
                        ReportBlockMenu(
                            viewModel = viewModel,
                            target = ProHostViewModel.ReportTarget(targetUid = request.practitionerId, bookingId = request.id, name = request.practitionerName),
                            menuExpanded = showReportMenu,
                            onMenuDismiss = { showReportMenu = false }
                        )
                    }
                }
                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = sharedText("pending_approval"))
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = sharedText("declined"))
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = sharedText("cancelled"))
                }
            }

            ProMemberAvatar(
                name = request.practitionerName,
                specialty = request.practitionerSpecialty,
                isVerified = true,
                size = 44.dp
            )
            if (!request.replacesBookingId.isNullOrBlank() && request.status == BookingRequestStatus.PENDING) {
                Text(
                    appString(Res.string.this_specialist_wants_to_change_a_booking) +
                        appString(Res.string.details_and_releases_the_old_booking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Details
            @Composable
            fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            DetailRow(Icons.Default.Apartment, sharedText("space"), request.spaceTitle)
            request.subdivisionName?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.MeetingRoom, sharedText("room_area"), it) }
            DetailRow(Icons.Default.AccessTime, sharedText("schedule"), request.selectedDateTimeRange.takeIf { request.dateModel == 2 && it.isNotBlank() }?.let { "\u2066$it\u2069" } ?: "${chosenDays.joinToString(", ") { localizedLabel(it) }} · \u2066$chosenHours\u2069${if (request.selectedShift.isNotBlank()) " · ${localizedLabel(request.selectedShift)}" else ""}")
            DetailRow(Icons.Default.CalendarToday, sharedText("start_date"), "\u2066${request.startDate}\u2069${if (request.durationMonths > 0) " · " + sharedPlural("x_month_term", request.durationMonths, request.durationMonths) else ""}")
            DetailRow(Icons.Default.AttachMoney, sharedText("total"), "\u2066${formatUsd(request.totalAmountUsd)} USD\u2069${if (attendeeLine != null) " · $attendeeLine" else ""}")
            request.lastPriceChange?.let { PriceChangeNote(it, Modifier.fillMaxWidth()) }
            DetailRow(Icons.Default.Schedule, sharedText("submitted"), formattedTime)
            if (request.clinicalNotes.isNotBlank()) DetailRow(Icons.AutoMirrored.Filled.Notes, sharedText("notes"), request.clinicalNotes)

            val rejectionReason = request.rejectionReason // local copy: the model lives in :shared (no smart cast across modules)
            if (request.status == BookingRequestStatus.REJECTED && !rejectionReason.isNullOrBlank()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = appString(Res.string.declined_x, rejectionReason),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalDivider()

            if (onChangePrice != null && (request.status == BookingRequestStatus.PENDING || request.status == BookingRequestStatus.ACCEPTED)) {
                ProOutlinedButton(
                    text = appString(Res.string.change_price),
                    onClick = onChangePrice,
                    icon = Icons.Default.LocalOffer,
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action buttons
            if (request.status == BookingRequestStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    CustomButton(
                        text = appString(Res.string.accept),
                        onClick = onAccept,
                        variant = CustomButtonVariant.SUCCESS,
                        icon = Icons.Default.Check,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = appString(Res.string.reject),
                        onClick = onReject,
                        variant = CustomButtonVariant.DANGER,
                        icon = Icons.Default.Close,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = appString(Res.string.whatsapp),
                        onClick = onWhatsApp,
                        variant = CustomButtonVariant.WHATSAPP,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        compact = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            } else {
                CustomButton(
                    text = appString(Res.string.message_on_whatsapp),
                    onClick = onWhatsApp,
                    variant = CustomButtonVariant.WHATSAPP,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
