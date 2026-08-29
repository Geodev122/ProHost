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
import com.example.ui.viewmodel.ProSpaceViewModel
import java.util.Locale

@Composable
fun OwnerRentingProgressScreen(
    viewModel: ProSpaceViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()

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

    val isOffline by viewModel.isOfflineMode.collectAsState()
    val syncStatus by viewModel.syncStatusMessage.collectAsState()
    val pendingOfflineTx by viewModel.pendingOfflineTransactions.collectAsState()

    OwnerRentingProgressScreenContent(
        ownerSpaces = ownerSpaces,
        activeBookings = activeBookings,
        monthlySubscriptionFeeUsd = pricingState.monthlySubscriptionFeeUsd,
        isOffline = isOffline,
        syncStatus = syncStatus,
        pendingOfflineCount = pendingOfflineTx.size,
        onRetrySync = { viewModel.retryOfflineSync() },
        onWhatsAppPractitioner = { booking ->
            viewModel.launchWhatsAppToPractitioner(context, booking)
        },
        onSendPaymentReminder = { booking ->
            viewModel.postNotificationAlert(
                title = "Payment Due Reminder 💳",
                body = "Friendly reminder to settle payment for your approved booking of '${booking.spaceTitle}' (Amount: $${booking.totalAmountUsd.toInt()} USD).",
                category = "PAYMENT_REMINDER",
                context = context,
                targetTab = "owner_progress",
                whatsAppPhone = booking.practitionerPhone,
                whatsAppMessage = "Hello ${booking.practitionerName}, sending a reminder regarding rent settlement for ${booking.spaceTitle}."
            )
        }
    )
}

@Composable
fun OwnerRentingProgressScreenContent(
    ownerSpaces: List<SpaceListing>,
    activeBookings: List<BookingRequest>,
    monthlySubscriptionFeeUsd: Double,
    isOffline: Boolean = false,
    syncStatus: String? = null,
    pendingOfflineCount: Int = 0,
    onRetrySync: () -> Unit = {},
    onWhatsAppPractitioner: (BookingRequest) -> Unit,
    onSendPaymentReminder: (BookingRequest) -> Unit,
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
                NetworkSyncResilienceBanner(
                    isOffline = isOffline,
                    statusMessage = syncStatus,
                    pendingOfflineCount = pendingOfflineCount,
                    onRetrySync = onRetrySync
                )
            }

        // Top Reminders Card
        item {
            ProSurfaceCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProSectionHeader(
                        title = "Practice Shifts & Payment Alerts",
                        subtitle = "Real-time alerts for contracts, schedules & dues",
                        icon = Icons.Default.NotificationsActive,
                        trailingContent = {
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "${activeBookings.size} Active Leases")
                        }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminders.forEach { reminder ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
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

        if (activeBookings.isEmpty()) {
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
                                    text = "${booking.practitionerSpecialty} • Syndicate: ${booking.practitionerSyndicateNumber}",
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
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                shape = RoundedCornerShape(10.dp),
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
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.3f),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remind Dues", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
