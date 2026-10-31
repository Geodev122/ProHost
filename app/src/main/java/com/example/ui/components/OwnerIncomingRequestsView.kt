package com.example.ui.components

import com.example.ui.util.uiPlural
import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current
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
                title = stringResource(R.string.incoming_requests),
                subtitle = stringResource(R.string.review_and_confirm_professional_rental_con),
                icon = Icons.Default.Inbox,
                trailingContent = {
                    if (pendingCount > 0) {
                        ProStatusBadge(type = ProBadgeType.CUSTOM_WARNING, customText = uiText(R.string.x_action_needed, pendingCount))
                    } else {
                        ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = uiText(R.string.x_total_2, requests.size))
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
                            stringResource(R.string.can_t_reach_server),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.check_your_connection_cached_requests_may),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (filteredRequests.isEmpty()) {
                ProEmptyState(
                    title = stringResource(R.string.no_x_requests, selectedFilter.lowercase().replaceFirstChar { it.uppercase() }),
                    description = if (selectedFilter == "PENDING") {
                        uiText(R.string.all_incoming_booking_requests_are_processe)
                    } else {
                        uiText(R.string.no_rental_booking_requests_found_under_thi)
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
                            viewModel.launchWhatsAppToPractitioner(context, req)
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
            title = { Text(stringResource(R.string.decline_booking_request), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = stringResource(R.string.provide_a_note_or_scheduling_reason_for_th),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InputField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = stringResource(R.string.reason_optional),
                        placeholder = stringResource(R.string.e_g_space_already_reserved_for_client_work),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                CustomButton(
                    text = stringResource(R.string.confirm_decline),
                    onClick = {
                        val reqId = rejectingRequestId
                        if (reqId != null) {
                            viewModel.rejectBookingRequest(reqId, rejectionReasonInput.ifBlank { uiText(R.string.declined_by_space_owner) }, context)
                        }
                        rejectingRequestId = null
                    },
                    variant = CustomButtonVariant.DANGER,
                    compact = true
                )
            },
            dismissButton = {
                TextButton(onClick = { rejectingRequestId = null }) {
                    Text(stringResource(R.string.cancel))
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
            title = { Text(stringResource(R.string.accept_booking), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = stringResource(R.string.optionally_share_your_space_rules_entry_in) +
                            stringResource(R.string.you_can_skip_this_the_specialist_will_be_i),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DocumentPickerField(
                        label = stringResource(R.string.space_rules_access_guidance_optional),
                        helperText = stringResource(R.string.pdf_jpg_or_png_the_specialist_can_view_thi),
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
                        viewModel.acceptBookingRequest(context, reqId, agreementDocState.uri)
                        acceptingRequestId = null
                    }
                ) {
                    Text(if (agreementDocState.isSelected) stringResource(R.string.accept_share_rules) else stringResource(R.string.accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { acceptingRequestId = null }) {
                    Text(stringResource(R.string.cancel))
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
                    text = if (attendeeLine != null) stringResource(R.string.x_total, attendeeLine) else stringResource(R.string.x_usd, request.totalAmountUsd.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = uiText(R.string.pending))
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = uiText(R.string.declined))
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = uiText(R.string.cancelled))
                }
                // A change to a booking this specialist already has: accepting replaces the old one.
                if (!request.replacesBookingId.isNullOrBlank() && request.status == BookingRequestStatus.PENDING) {
                    ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = uiText(R.string.change_request_2))
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
    onChangePrice: (() -> Unit)? = null
) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US) }
    val formattedTime = remember(request.createdAt) { sdf.format(Date(request.createdAt)) }
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
                    text = if (request.replacesBookingId.isNullOrBlank()) stringResource(R.string.booking_request) else stringResource(R.string.change_request),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = uiText(R.string.pending_approval))
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = uiText(R.string.declined))
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = uiText(R.string.cancelled))
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
                    stringResource(R.string.this_specialist_wants_to_change_a_booking) +
                        stringResource(R.string.details_and_releases_the_old_booking),
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

            DetailRow(Icons.Default.Apartment, uiText(R.string.space), request.spaceTitle)
            request.subdivisionName?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.MeetingRoom, uiText(R.string.room_area), it) }
            DetailRow(Icons.Default.AccessTime, uiText(R.string.schedule), "${chosenDays.joinToString(", ") { localizedLabel(it) }} · \u2066$chosenHours\u2069${if (request.selectedShift.isNotBlank()) " · ${localizedLabel(request.selectedShift)}" else ""}")
            DetailRow(Icons.Default.CalendarToday, uiText(R.string.start_date), "\u2066${request.startDate}\u2069${if (request.durationMonths > 0) " · " + uiPlural(R.plurals.x_month_term, request.durationMonths, request.durationMonths) else ""}")
            DetailRow(Icons.Default.AttachMoney, uiText(R.string.total), "\u2066${formatUsd(request.totalAmountUsd)} USD\u2069${if (attendeeLine != null) " · $attendeeLine" else ""}")
            request.lastPriceChange?.let { PriceChangeNote(it, Modifier.fillMaxWidth()) }
            DetailRow(Icons.Default.Schedule, uiText(R.string.submitted), formattedTime)
            if (request.clinicalNotes.isNotBlank()) DetailRow(Icons.AutoMirrored.Filled.Notes, uiText(R.string.notes), request.clinicalNotes)

            if (request.status == BookingRequestStatus.REJECTED && !request.rejectionReason.isNullOrBlank()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = stringResource(R.string.declined_x, request.rejectionReason),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalDivider()

            if (onChangePrice != null && (request.status == BookingRequestStatus.PENDING || request.status == BookingRequestStatus.ACCEPTED)) {
                ProOutlinedButton(
                    text = stringResource(R.string.change_price),
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
                        text = stringResource(R.string.accept),
                        onClick = onAccept,
                        variant = CustomButtonVariant.SUCCESS,
                        icon = Icons.Default.Check,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = stringResource(R.string.reject),
                        onClick = onReject,
                        variant = CustomButtonVariant.DANGER,
                        icon = Icons.Default.Close,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = stringResource(R.string.whatsapp),
                        onClick = onWhatsApp,
                        variant = CustomButtonVariant.WHATSAPP,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        compact = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            } else {
                CustomButton(
                    text = stringResource(R.string.message_on_whatsapp),
                    onClick = onWhatsApp,
                    variant = CustomButtonVariant.WHATSAPP,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
