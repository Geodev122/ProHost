package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

@Composable
fun OwnerRentingProgressScreen(
    viewModel: ProHostViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()
    val hasLoadedBookingsOnce by viewModel.hasLoadedBookingsOnce.collectAsState()

    val ownerSpaces = remember(spaces, currentUser) {
        val user = currentUser
        if (user != null) {
            spaces.filter { it.ownerId == user.id || it.ownerEmail.equals(user.email, ignoreCase = true) || it.ownerName.contains(user.fullName, ignoreCase = true) || user.role == UserRole.ADMIN }
        } else {
            emptyList()
        }
    }

    val activeBookings = remember(allBookingRequests, currentUser) {
        val user = currentUser
        val list = if (user != null) {
            allBookingRequests.filter { it.ownerId == user.id || it.ownerName.contains(user.fullName, ignoreCase = true) || user.role == UserRole.ADMIN }
        } else {
            emptyList()
        }
        list.filter { it.status == BookingRequestStatus.ACCEPTED }
    }

    var cancelTargetBooking by remember { mutableStateOf<BookingRequest?>(null) }
    if (cancelTargetBooking != null) {
        val bkg = cancelTargetBooking!!
        com.example.ui.components.CancelAcceptedBookingDialog(
            spaceTitle = bkg.spaceTitle,
            partyLabel = bkg.practitionerName,
            onDismiss = { cancelTargetBooking = null },
            onConfirm = { reasonCode, note ->
                viewModel.cancelAcceptedBooking(bkg.id, reasonCode, note, context)
                cancelTargetBooking = null
            }
        )
    }

    OwnerRentingProgressScreenContent(
        ownerSpaces = ownerSpaces,
        activeBookings = activeBookings,
        monthlySubscriptionFeeUsd = pricingState.monthlySubscriptionFeeUsd,
        hasLoadedBookingsOnce = hasLoadedBookingsOnce,
        onWhatsAppPractitioner = { booking ->
            viewModel.launchWhatsAppToPractitioner(context, booking)
        },
        onSendPaymentReminder = { booking ->
            // Real cross-device FCM push (sendPaymentReminder Cloud Function) — the same
            // path Renting Requests' "Send Payment Reminder" already uses, consolidating
            // what used to be a second, same-device-only local alert here.
            viewModel.sendPaymentReminder(booking.id, booking.practitionerName, context)
        },
        onCancelAcceptedBooking = { booking ->
            cancelTargetBooking = booking
        },
        onMarkPaid = { booking ->
            viewModel.acknowledgePayment(booking.id, asHost = true, context)
        }
    )
}

@Composable
fun OwnerRentingProgressScreenContent(
    ownerSpaces: List<SpaceListing>,
    activeBookings: List<BookingRequest>,
    monthlySubscriptionFeeUsd: Double,
    hasLoadedBookingsOnce: Boolean = true,
    onWhatsAppPractitioner: (BookingRequest) -> Unit,
    onSendPaymentReminder: (BookingRequest) -> Unit,
    onCancelAcceptedBooking: (BookingRequest) -> Unit = {},
    onMarkPaid: (BookingRequest) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Generate Dynamic Reminders & Alerts
    val reminders = remember(ownerSpaces, activeBookings, monthlySubscriptionFeeUsd) {
        val list = mutableListOf<String>()

        // 1. Subscription expiration reminders
        ownerSpaces.forEach { space ->
            val daysLeft = ((space.subscriptionExpiryMillis - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)
            if (daysLeft <= 7) {
                list.add("⚠️ Subscription renewal due for '${space.title}' in $daysLeft days. Keep listing active with Whish Pay ($${String.format(Locale.US, "%.2f", monthlySubscriptionFeeUsd)}).")
            } else {
                list.add("📅 Listing subscription for '${space.title}' is active. Next renew cycle in $daysLeft days.")
            }
        }

        // 2. Outside payment reminders
        activeBookings.forEach { booking ->
            list.add("💰 Outside-App Rent due from Dr. ${booking.practitionerName} for slot '${booking.selectedDateTimeRange.ifBlank { booking.formula.scheduleDescription }}' (Amount: $${booking.totalAmountUsd.toInt()} USD).")
        }

        // 3. Scheduling checklist reminder
        activeBookings.forEach { booking ->
            val daysOfWeek = booking.selectedDays.ifEmpty { booking.formula.daysOfWeek }
            list.add("⏰ Practice Schedule Checklist: Dr. ${booking.practitionerName} has an upcoming shift on ${daysOfWeek.joinToString()} at '${booking.spaceTitle}'.")
        }

        if (list.isEmpty()) {
            list.add("✨ All clear! No pending payments or active contract alerts right now.")
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${activeBookings.size} Active Leases",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

        // Top Reminders Card
        item {
            ProSurfaceCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProSectionHeader(
                        title = "Alerts",
                        subtitle = "Real-time alerts for contracts, schedules & dues",
                        icon = Icons.Default.NotificationsActive
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminders.forEach { reminder ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (reminder.startsWith("⚠️") || reminder.startsWith("💰")) Icons.Default.PriorityHigh else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (reminder.startsWith("⚠️") || reminder.startsWith("💰")) StatusWarning else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = reminder,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Tenancies Section Header
        item {
            ProSectionHeader(
                title = "Active Tenancies (${activeBookings.size})",
                subtitle = "Contract timeline, elapsed days, and practitioner contact",
                icon = Icons.Default.Schedule
            )
        }

        if (!hasLoadedBookingsOnce) {
            // The first Firestore snapshot hasn't arrived yet — without this, a
            // host with real active tenancies briefly saw "No Active Tenancies"
            // before the real list streamed in.
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (activeBookings.isEmpty()) {
            item {
                ProEmptyState(
                    title = "No Active Tenancies Yet",
                    description = "When you approve rental booking requests, contract progress, remaining days, and practitioner communications will appear here.",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong
                )
            }
        } else {
            items(activeBookings, key = { it.id }) { booking ->
                val elapsedDays = ((System.currentTimeMillis() - booking.createdAt) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)
                val totalContractDays = (booking.durationMonths * 30).coerceAtLeast(1)
                val progressFraction = (elapsedDays.toFloat() / totalContractDays.toFloat()).coerceIn(0f, 1f)
                val remainingDays = (totalContractDays - elapsedDays).coerceAtLeast(0)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dr. ${booking.practitionerName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = booking.practitionerSpecialty,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Space: ${booking.spaceTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ProStatusBadge(type = ProBadgeType.CUSTOM_SUCCESS, customText = "$remainingDays Days Left")
                        }

                        // Shift details badge
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val chosenDaysStr = if (booking.selectedDays.isNotEmpty()) booking.selectedDays.joinToString(", ") else booking.formula.daysOfWeek.joinToString(", ")
                                val chosenHoursStr = if (booking.selectedStartHour.isNotBlank() && booking.selectedEndHour.isNotBlank()) "${booking.selectedStartHour} - ${booking.selectedEndHour}" else "${booking.formula.startHour} - ${booking.formula.endHour}"

                                Text(
                                    text = "🕒 Shift: $chosenDaysStr ($chosenHoursStr)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Rate: $${booking.formula.rateUsd.toInt()} USD / month • Agreement: $${booking.totalAmountUsd.toInt()} USD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Mutual, independent "Mark as Paid" acknowledgment — record-keeping
                        // only (rent settlement itself happens entirely outside the app).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (booking.paymentAcknowledgedByHost) "✓ You marked this paid" else "Not marked paid yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (booking.paymentAcknowledgedByHost) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!booking.paymentAcknowledgedByHost) {
                                TextButton(onClick = { onMarkPaid(booking) }) {
                                    Text("Mark as Paid", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Progress Bar & Percentage
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Duration: ${booking.durationMonths} Months (${elapsedDays}d elapsed)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(end = 6.dp)
                                )
                                Text(
                                    text = "${(progressFraction * 100).toInt()}% Elapsed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onWhatsAppPractitioner(booking) },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendPaymentReminder(booking) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1.3f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remind Dues", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            // Early termination — previously the only way to end an
                            // active lease was outside the app entirely.
                            IconButton(
                                onClick = { onCancelAcceptedBooking(booking) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                            ) {
                                Icon(Icons.Default.EventBusy, contentDescription = "Cancel Booking", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
}
