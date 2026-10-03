package com.example.ui.components

import android.widget.Toast
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with badge
            ProSectionHeader(
                title = "Incoming Requests",
                subtitle = "Review and confirm professional rental contracts",
                icon = Icons.Default.Inbox,
                trailingContent = {
                    if (pendingCount > 0) {
                        ProStatusBadge(type = ProBadgeType.CUSTOM_WARNING, customText = "$pendingCount Action Needed")
                    } else {
                        ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "${requests.size} Total")
                    }
                }
            )

            // Filter Chips Bar — a LazyRow (rather than a fixed-width Row) so the
            // chips scroll horizontally instead of compressing/clipping on narrow
            // screens, since "Declined (N)" was the one getting squeezed last.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
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
                            "Can't reach server",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Check your connection — cached requests may be stale.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (filteredRequests.isEmpty()) {
                ProEmptyState(
                    title = "No ${selectedFilter.lowercase().replaceFirstChar { it.uppercase() }} Requests",
                    description = if (selectedFilter == "PENDING") {
                        "All incoming booking requests are processed. New professional inquiries will appear here immediately."
                    } else {
                        "No rental booking requests found under this filter."
                    },
                    icon = Icons.Default.Inbox,
                    modifier = Modifier.padding(vertical = Spacing.md)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        }
                    )
                }
            }
        }
    }

    // Rejection Reason Modal Dialog
    if (rejectingRequestId != null) {
        ProHostDialog(
            onDismissRequest = { rejectingRequestId = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Decline Booking Request", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Provide a note or scheduling reason for the professional:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = { Text("Reason (Optional)") },
                        placeholder = { Text("e.g., Space already reserved for client workshops") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                CustomButton(
                    text = "Confirm Decline",
                    onClick = {
                        val reqId = rejectingRequestId
                        if (reqId != null) {
                            viewModel.rejectBookingRequest(reqId, rejectionReasonInput.ifBlank { "Declined by space owner" }, context)
                        }
                        rejectingRequestId = null
                    },
                    variant = CustomButtonVariant.DANGER,
                    compact = true
                )
            },
            dismissButton = {
                TextButton(onClick = { rejectingRequestId = null }) {
                    Text("Cancel")
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
            title = { Text("Accept Booking", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Optionally share your space rules, entry instructions, or any special access info with your tenant. " +
                            "You can skip this — the specialist will be informed if nothing is provided.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DocumentPickerField(
                        label = "Space Rules & Access Guidance (Optional)",
                        helperText = "PDF, JPG, or PNG — the specialist can view this from My Bookings",
                        state = agreementDocState,
                        onStateChanged = { agreementDocState = it },
                        modifier = Modifier.fillMaxWidth(),
                        required = false
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acceptBookingRequest(context, reqId, agreementDocState.uri)
                        acceptingRequestId = null
                    }
                ) {
                    Text(if (agreementDocState.isSelected) "Accept & Share Rules" else "Accept")
                }
            },
            dismissButton = {
                TextButton(onClick = { acceptingRequestId = null }) {
                    Text("Cancel")
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
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                    text = if (attendeeLine != null) "$attendeeLine total" else "\$${request.totalAmountUsd.toInt()} USD",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Pending")
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "Declined")
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Cancelled")
                }
                Icon(
                    Icons.Default.ChevronRight,
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
    onWhatsApp: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US) }
    val formattedTime = remember(request.createdAt) { sdf.format(Date(request.createdAt)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val attendeeLine = com.example.ui.util.AttendeePricing.bookingSummary(request)
    val chosenDays = request.selectedDays.takeIf { it.isNotEmpty() } ?: request.formula.daysOfWeek
    val chosenHours = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank())
        "${request.selectedStartHour}–${request.selectedEndHour}" else "${request.formula.startHour}–${request.formula.endHour}"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Booking Request",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Pending Approval")
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "Declined")
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Cancelled")
                }
            }

            ProMemberAvatar(
                name = request.practitionerName,
                specialty = request.practitionerSpecialty,
                isVerified = true,
                size = 44.dp
            )

            HorizontalDivider()

            // Details
            @Composable
            fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            DetailRow(Icons.Default.Apartment, "Space", request.spaceTitle)
            request.subdivisionName?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Default.MeetingRoom, "Room / Area", it) }
            DetailRow(Icons.Default.AccessTime, "Schedule", "${chosenDays.joinToString(", ")} · $chosenHours${if (request.selectedShift.isNotBlank()) " · ${request.selectedShift}" else ""}")
            DetailRow(Icons.Default.CalendarToday, "Start Date", "${request.startDate}${if (request.durationMonths > 0) " · ${request.durationMonths} month term" else ""}")
            DetailRow(Icons.Default.AttachMoney, "Total", "\$${request.totalAmountUsd.toInt()} USD${if (attendeeLine != null) " · $attendeeLine" else ""}")
            DetailRow(Icons.Default.Schedule, "Submitted", formattedTime)
            if (request.clinicalNotes.isNotBlank()) DetailRow(Icons.AutoMirrored.Filled.Notes, "Notes", request.clinicalNotes)

            if (request.status == BookingRequestStatus.REJECTED && !request.rejectionReason.isNullOrBlank()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = "Declined: ${request.rejectionReason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalDivider()

            // Action buttons
            if (request.status == BookingRequestStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomButton(
                        text = "Accept",
                        onClick = onAccept,
                        variant = CustomButtonVariant.SUCCESS,
                        icon = Icons.Default.Check,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = "Reject",
                        onClick = onReject,
                        variant = CustomButtonVariant.DANGER,
                        icon = Icons.Default.Close,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    CustomButton(
                        text = "WhatsApp",
                        onClick = onWhatsApp,
                        variant = CustomButtonVariant.WHATSAPP,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        compact = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            } else {
                CustomButton(
                    text = "Message on WhatsApp",
                    onClick = onWhatsApp,
                    variant = CustomButtonVariant.WHATSAPP,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
