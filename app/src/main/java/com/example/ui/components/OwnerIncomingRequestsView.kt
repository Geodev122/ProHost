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
import com.example.ui.viewmodel.ProSpaceViewModel
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
    viewModel: ProSpaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, ACCEPTED, REJECTED
    var rejectingRequestId by remember { mutableStateOf<String?>(null) }
    var rejectionReasonInput by remember { mutableStateOf("") }
    var acceptingRequestId by remember { mutableStateOf<String?>(null) }
    var agreementDocState by remember { mutableStateOf(DocumentPickerState()) }

    val pendingCount = requests.count { it.status == BookingRequestStatus.PENDING }
    val acceptedCount = requests.count { it.status == BookingRequestStatus.ACCEPTED }
    val rejectedCount = requests.count { it.status == BookingRequestStatus.REJECTED }

    val filteredRequests = remember(requests, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> requests.filter { it.status == BookingRequestStatus.PENDING }
            "ACCEPTED" -> requests.filter { it.status == BookingRequestStatus.ACCEPTED }
            "REJECTED" -> requests.filter { it.status == BookingRequestStatus.REJECTED }
            else -> requests
        }
    }

    ProSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with badge
            ProSectionHeader(
                title = "Incoming Booking Requests",
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

            // Filter Chips Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "All (${requests.size})",
                    "PENDING" to "Pending ($pendingCount)",
                    "ACCEPTED" to "Accepted ($acceptedCount)",
                    "REJECTED" to "Declined ($rejectedCount)"
                ).forEach { (filterKey, label) ->
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

            if (filteredRequests.isEmpty()) {
                ProEmptyState(
                    title = "No ${selectedFilter.lowercase().replaceFirstChar { it.uppercase() }} Requests",
                    description = if (selectedFilter == "PENDING") {
                        "All incoming booking requests are processed. New professional inquiries will appear here immediately."
                    } else {
                        "No rental booking requests found under this filter."
                    },
                    icon = Icons.Default.Inbox,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredRequests.forEach { request ->
                        OwnerBookingRequestCard(
                            request = request,
                            spaces = spaces,
                            onAccept = {
                                acceptingRequestId = request.id
                                agreementDocState = DocumentPickerState()
                            },
                            onReject = {
                                rejectingRequestId = request.id
                                rejectionReasonInput = ""
                            },
                            onWhatsAppProfessional = {
                                viewModel.launchWhatsAppToPractitioner(context, request)
                            },
                            onSendPaymentReminder = {
                                viewModel.sendPaymentReminder(request.id, request.practitionerName, context)
                            }
                        )
                    }
                }
            }
        }
    }

    // Rejection Reason Modal Dialog
    if (rejectingRequestId != null) {
        AlertDialog(
            onDismissRequest = { rejectingRequestId = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = StatusError) },
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
                Button(
                    onClick = {
                        val reqId = rejectingRequestId
                        if (reqId != null) {
                            viewModel.rejectBookingRequest(reqId, rejectionReasonInput.ifBlank { "Declined by space owner" }, context)
                        }
                        rejectingRequestId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Decline")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectingRequestId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Accept & Upload Agreement Dialog — accepting a request now means the host has
    // reached a real agreement with the specialist outside the app and is uploading
    // the signed lease as the record of that; there's no in-app payment step anymore.
    if (acceptingRequestId != null) {
        val reqId = acceptingRequestId!!
        AlertDialog(
            onDismissRequest = { acceptingRequestId = null },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Accept & Upload Agreement", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Accepting means you and the specialist have reached and signed a leasing agreement outside the app. Upload the signed document to finalize — this saves it as the official record and locks in the schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DocumentPickerField(
                        label = "Signed Leasing Agreement",
                        helperText = "PDF, JPG, or PNG — kept on file, links to the specialist's Digital Key Pass",
                        state = agreementDocState,
                        onStateChanged = { agreementDocState = it },
                        modifier = Modifier.fillMaxWidth(),
                        required = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = agreementDocState.uri
                        if (uri != null) {
                            viewModel.acceptBookingRequest(context, reqId, uri)
                            acceptingRequestId = null
                        } else {
                            Toast.makeText(context, "Please upload the signed agreement first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = agreementDocState.isSelected
                ) {
                    Text("Finalize")
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
    spaces: List<SpaceListing>,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onWhatsAppProfessional: () -> Unit,
    onSendPaymentReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US) }
    val formattedTime = remember(request.createdAt) { sdf.format(Date(request.createdAt)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = when (request.status) {
                BookingRequestStatus.PENDING -> StatusWarning
                BookingRequestStatus.ACCEPTED -> StatusInfo
                BookingRequestStatus.REJECTED -> StatusError
                BookingRequestStatus.CANCELLED -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Member profile header & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProMemberAvatar(
                    name = request.practitionerName,
                    specialty = request.practitionerSpecialty,
                    isVerified = true,
                    size = 36.dp,
                    modifier = Modifier.weight(1f)
                )

                when (request.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Pending Approval")
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "Declined")
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Cancelled")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Space & Formula Info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Apartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = request.spaceTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📑 Formula: ${request.formula.type.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ProCurrencyTag(rateUsd = request.formula.rateUsd, isPerMonth = true)
                }

                // Selected Date & Time Range Display
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val chosenDaysStr = if (request.selectedDays.isNotEmpty()) request.selectedDays.joinToString(", ") else request.formula.daysOfWeek.joinToString(", ")
                        val chosenHoursStr = if (request.selectedStartHour.isNotBlank() && request.selectedEndHour.isNotBlank()) "${request.selectedStartHour} - ${request.selectedEndHour}" else "${request.formula.startHour} - ${request.formula.endHour}"
                        val shiftDetail = if (request.selectedShift.isNotBlank()) " • ${request.selectedShift}" else ""

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Requested Slot: $chosenDaysStr ($chosenHoursStr)$shiftDetail",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "🗓️ Starting Date: ${request.startDate} (${request.durationMonths} month term) • Formula: ${request.formula.type.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Agreement Total & Note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Agreement: $${request.totalAmountUsd.toInt()} USD",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Submitted: $formattedTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (request.clinicalNotes.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💬 Requirements / Note: \"${request.clinicalNotes}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            // Unavailable Notice for Accepted Bookings
            if (request.status == BookingRequestStatus.ACCEPTED) {
                Surface(
                    color = StatusInfoContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = StatusInfo,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Locked & marked UNAVAILABLE for public discovery (${request.formula.totalWeeklyHours} hrs/wk deducted).",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusInfo,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (request.status == BookingRequestStatus.REJECTED && !request.rejectionReason.isNullOrBlank()) {
                Surface(
                    color = StatusErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🚫 Decline Reason: ${request.rejectionReason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusError,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // Action Buttons Bar
            if (request.status == BookingRequestStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Accept Button
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Accept",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reject Button
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reject",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // WhatsApp Button
                    Button(
                        onClick = onWhatsAppProfessional,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "WhatsApp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                // If already accepted, rejected, or cancelled, show action row
                if (request.status == BookingRequestStatus.ACCEPTED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // WhatsApp
                        Button(
                            onClick = onWhatsAppProfessional,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "WhatsApp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Send Payment Reminder FCM Button
                        Button(
                            onClick = onSendPaymentReminder,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1.5f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remind Payment",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onWhatsAppProfessional,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Message Specialist on WhatsApp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
