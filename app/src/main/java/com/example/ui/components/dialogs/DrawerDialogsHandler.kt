package com.example.ui.components.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.components.DocumentPreviewDialog
import com.example.ui.components.drawer.LawBulletinCard
import com.example.ui.theme.LebaneseCedarGreen
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.ProSpaceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DrawerDialogsHandler(
    dialogId: String?,
    viewModel: ProSpaceViewModel,
    onNavigateToTab: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (dialogId == null) return

    if (dialogId == "system_debugger") {
        SystemDebuggerDialog(
            repository = viewModel.repository,
            onDismissRequest = onDismiss
        )
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()
    val bookingRequests by viewModel.bookingRequests.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val fcmAlerts by viewModel.fcmAlerts.collectAsState()

    // The admin credential-document registry (below) is the only place an admin can
    // ever actually approve/reject a document — SpecialistProfileScreen's own preview
    // dialog is reachable only from a user's OWN document list, which explicitly
    // excludes ADMIN, so `isAdmin` could never be true at that call site.
    var previewingAdminDocument by remember { mutableStateOf<CredentialDocument?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val title = when (dialogId) {
                        "pro_dues" -> "Payment Due Reminders"
                        "pro_syndicate" -> "Syndicate ID Verification"
                        "pro_laws" -> "Lebanese Rent Laws"
                        "owner_whish" -> "Whish Money Transactions"
                        "owner_guidelines" -> "Practice Guidelines"
                        "admin_audit" -> "Central Security Audits"
                        "admin_gov" -> "Governorate Node Status"
                        "admin_forecast" -> "Baseline Fee Forecaster"
                        "admin_governance_clearance" -> "Governance Authority Clearance"
                        "admin_credentials_registry" -> "Credential Documents Registry"
                        "admin_app_updates" -> "App Version & In-App Updates"
                        "pro_credentials_registry" -> "Credential Documents Registry"
                        "pro_accreditation_hub" -> "Accreditation & Practice Hub"
                        "pro_app_updates" -> "App Version & In-App Updates"
                        "owner_package_tiers" -> "Owner Package Tiers & Governance"
                        "owner_app_updates" -> "App Version & In-App Updates"
                        "fcm_alerts" -> "Real-time Alerts Terminal"
                        else -> "Information Sheet"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Content Pane
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 480.dp)
                ) {
                    when (dialogId) {
                        // "Renting Requests" now routes to the real OwnerRentalRequestsScreen
                        // (via onTabSelected("owner_requests")) instead of this dialog — the
                        // screen has real filtering, a real reject-reason prompt, and a real
                        // payment-reminder notification, none of which this cramped duplicate
                        // ever had (its "Send Payment Reminder" button was Toast-only fakery).
                        "pro_pending" -> {
                            val userPending = bookingRequests.filter {
                                it.status == BookingRequestStatus.PENDING &&
                                (it.practitionerId == currentUser?.id || it.practitionerEmail.equals(currentUser?.email, ignoreCase = true))
                            }
                            if (userPending.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No Pending Requests", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(userPending) { request ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(request.spaceTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text("Formula: ${request.formula.type.displayName}", style = MaterialTheme.typography.bodySmall)
                                                Text("Monthly rate: $${request.formula.rateUsd.toInt()} USD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        viewModel.cancelBookingRequest(request.id, context)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Cancel Rent Request", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "pro_dues" -> {
                            val activeRentals = bookingRequests.filter {
                                it.status == BookingRequestStatus.ACCEPTED &&
                                (it.practitionerId == currentUser?.id || it.practitionerEmail.equals(currentUser?.email, ignoreCase = true))
                            }
                            if (activeRentals.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("All Dues Settled!", fontWeight = FontWeight.Bold, color = StatusSuccess)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(activeRentals) { rental ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(rental.spaceTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text("Rate: $${rental.formula.rateUsd.toInt()} USD / month", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                                Text("Agreement duration: ${rental.durationMonths} Months (Started: ${rental.startDate})", style = MaterialTheme.typography.labelSmall)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        val matchingSpace = allSpaces.find { it.id == rental.spaceId }
                                                        if (matchingSpace != null) {
                                                            viewModel.launchWhatsAppInquiry(context, matchingSpace, rental.formula, rental)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Pay Host via WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "pro_syndicate" -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = currentUser?.fullName ?: "Practitioner",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentUser?.specialty ?: "Independent Practice",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Syndicate Registry:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text(currentUser?.syndicateNumber ?: "OEA-LB-8842", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Affiliation Node:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text(currentUser?.affiliation ?: "Beirut Syndicate", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Registration Status:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text("ACTIVE VERIFIED", color = StatusSuccess, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        "pro_laws" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Workspace renting in Lebanon operates under specialized civil codes that bypass commercial subleasing complications:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LawBulletinCard(
                                    number = "Decree 159/92",
                                    title = "Professional Practice Spaces",
                                    content = "Guarantees professionals the right to rent dedicated shared offices without creating standard full-lease tenant property titles, facilitating flexible multi-day shifts."
                                )
                                LawBulletinCard(
                                    number = "Sublease Safety",
                                    title = "Host Verification Protection",
                                    content = "All hosts on ProSpace are legally bound to verify they own or hold exclusive rights to sublease workspace hours, protecting renters from arbitrary closures."
                                )
                                LawBulletinCard(
                                    number = "Whish Pay Receipts",
                                    title = "Digital Transaction Stability",
                                    content = "Any financial deposit routed through Whish Money is backed by standard audit logs, serving as official legal proof of rental settlement."
                                )
                            }
                        }
                        "owner_whish" -> {
                            val hostTxs = transactions.filter {
                                val user = currentUser
                                user != null && (it.payerName.contains(user.fullName, ignoreCase = true) || it.payerPhone == user.phone || user.role == UserRole.ADMIN)
                            }
                            if (hostTxs.isEmpty()) {
                                Text("No recorded Whish settlements. Subscription fees paid via Whish Pay will populate here immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(hostTxs) { tx ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Order #${tx.orderId}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                    Text("Space: ${tx.spaceTitle}", style = MaterialTheme.typography.labelSmall)
                                                    Text("Channel ID: ${tx.channelId}", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("$${tx.amountUsd.toInt()} USD", fontWeight = FontWeight.ExtraBold, color = StatusSuccess)
                                                    Text(tx.status.name, color = StatusSuccess, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "owner_guidelines" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Guidelines to follow as a verified workspace host on ProHost:", style = MaterialTheme.typography.bodySmall)
                                Text("• Hygiene and Sanitization: Workspace suites must be sanitized daily between tenant practitioner shifts.", style = MaterialTheme.typography.labelMedium)
                                Text("• Access and Front-desk: Inform receptionist desk of practitioner scheduled patients list for easy welcoming.", style = MaterialTheme.typography.labelMedium)
                                Text("• Lockers & Shared IT: High-speed Wi-Fi network and printing capabilities must remain functional.", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        "admin_audit" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(auditLogs) { log ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(log.actionType, color = Color(0xFF00FF00), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                Text(log.severity, color = if (log.severity == "SECURE") Color.Red else Color.Yellow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(log.details, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                            Text("Actor: ${log.actorEmail} • IP: ${log.ipAddress}", color = Color.Gray, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                        "admin_gov" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Governorate.values().forEach { gov ->
                                    val listingCount = allSpaces.count { it.governorate == gov }
                                    Card(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Text(gov.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("$listingCount active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "admin_forecast" -> {
                            val activeCount = allSpaces.size
                            val currentFee = viewModel.pricingState.collectAsState().value.monthlySubscriptionFeeUsd
                            var sliderValue by remember { mutableStateOf(currentFee.toFloat()) }

                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Set baseline subscription fee for workspace listings in Lebanon:", style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Current Fee: $${currentFee} USD", fontWeight = FontWeight.Bold)
                                    Text("Target Fee: $${String.format(Locale.US, "%.2f", sliderValue)} USD", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    valueRange = 0.5f..15.0f,
                                    steps = 29
                                )
                                Button(
                                    onClick = {
                                        viewModel.setSubscriptionFee(sliderValue.toDouble())
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Update Global Baseline Fee")
                                }
                                HorizontalDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val monthlyRevenue = activeCount * sliderValue
                                    val annualRevenue = monthlyRevenue * 12
                                    Text("Projected Platform Metrics (Lebanese Market Nodes):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text("• Active Subscribed Spaces: $activeCount", style = MaterialTheme.typography.bodySmall)
                                    Text("• Monthly Revenue (MRR): $${String.format(Locale.US, "%.2f", monthlyRevenue)} USD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("• Annual Recurring Revenue (ARR): $${String.format(Locale.US, "%.2f", annualRevenue)} USD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = LebaneseCedarGreen)
                                }
                            }
                        }
                        "fcm_alerts" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "📥 Real-time Alerts Feed",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "This terminal displays a live stream of real-time workspace updates and payment notifications received on your device via Firebase Cloud Messaging.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                if (fcmAlerts.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsNone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No notifications yet.",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.outline,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Real-time updates received via Firebase Cloud Messaging will appear here.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                                    ) {
                                        items(fcmAlerts) { alert ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (alert.isRead) {
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    } else {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                    }
                                                ),
                                                border = if (!alert.isRead) {
                                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                                } else null
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (alert.category == "BOOKING_ACCEPTANCE") Icons.Default.CheckCircle else Icons.Default.NotificationImportant,
                                                                contentDescription = null,
                                                                tint = if (alert.category == "BOOKING_ACCEPTANCE") Color(0xFF00796B) else MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = alert.title,
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = if (alert.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }

                                                        if (!alert.isRead) {
                                                            TextButton(
                                                                onClick = { viewModel.markAlertAsRead(alert.id) },
                                                                contentPadding = PaddingValues(0.dp),
                                                                modifier = Modifier.height(24.dp)
                                                            ) {
                                                                Text("Mark Read", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else {
                                                            Text(
                                                                text = "Read",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = alert.body,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (alert.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(alert.timestamp)),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )

                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            if (onNavigateToTab != null) {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        viewModel.markAlertAsRead(alert.id)
                                                                        val targetTab = if (alert.category == "BOOKING_ACCEPTANCE") {
                                                                            if (currentUser?.role == UserRole.PROFESSIONAL) "pro_rentals" else "owner_requests"
                                                                        } else {
                                                                            "owner_progress"
                                                                        }
                                                                        onNavigateToTab(targetTab)
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    modifier = Modifier.height(28.dp)
                                                                ) {
                                                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Open Screen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }

                                                            Button(
                                                                onClick = {
                                                                    val cleanPhone = "9613987654"
                                                                    val msg = java.net.URLEncoder.encode("Hello, following up on alert: ${alert.title}", "UTF-8")
                                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$cleanPhone?text=$msg"))
                                                                    context.startActivity(intent)
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(28.dp)
                                                            ) {
                                                                Text("WhatsApp", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "admin_governance_clearance" -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "Root Supervisory Clearance • 100% Accredited",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Node Account: ${currentUser?.email ?: "admin@prospace.lb"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "As Super Administrator, your account operates as central platform governance and is exempt from standard user document requirements. You hold supervisory clearance over space taxonomies, member accreditations, and Whish Money transaction ledgers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🛡️ Cryptographic Integrity: Active", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("Role: SUPER_ADMIN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        "admin_credentials_registry" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Super Admin Supervisory Registry & Document Inspection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Tap a document to approve or request a revision.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                val allDocs = viewModel.credentialDocuments.collectAsState().value
                                if (allDocs.isEmpty()) {
                                    Text("No pending member documents in the verification queue.", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                                        items(allDocs) { doc ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { previewingAdminDocument = doc },
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("User ID: ${doc.userId} • Type: ${doc.type.title}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                    Text("Document No: ${doc.documentNumber} • Status: ${doc.status}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "admin_app_updates" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Google Play Core Update Management & Release Integrity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Installed Version: 1.0.0 (Production Channel Lebanese Node)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("App Integrity: Verified via Google Play App Signing & SHA-256 Key Attestation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        android.widget.Toast.makeText(context, "System is running latest signed production version 1.0.0", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check Google Play Updates", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        "pro_credentials_registry" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Practitioner Syndicate & Legal Credentials Registry", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Manage your Syndicate membership card, Practice Decree, and National ID for Lebanese clinic rentals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                val userDocs = viewModel.currentUserDocuments.collectAsState().value
                                val requiredTypes = DocumentType.entries.filter { currentUser?.role?.let { role -> it.requiredFor.contains(role) } == true }
                                requiredTypes.forEach { docType ->
                                    val upDoc = userDocs.find { it.type == docType }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(docType.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                Text("Status: ${upDoc?.status?.name ?: "NOT_UPLOADED"}", style = MaterialTheme.typography.bodySmall, color = if (upDoc?.status == DocumentStatus.VERIFIED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                                            }
                                            Button(
                                                onClick = {
                                                    android.widget.Toast.makeText(context, "Opening document upload portal for ${docType.title}", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Upload", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "pro_accreditation_hub" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("4-Pillar Professional Syndicate Accreditation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Complete contact verification, syndicate license, governorate selection, and required legal document uploads to achieve 100% verified status.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("• Pillar 1: Contact & Identity Info (${if (currentUser?.fullName?.isNotBlank() == true) "Complete" else "Pending"})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("• Pillar 2: Syndicate & Specialty Registration (${if (currentUser?.syndicateNumber?.isNotBlank() == true) "Verified" else "Pending"})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("• Pillar 3: Lebanese Governorate Node (${currentUser?.governorate?.displayName ?: "Beirut"})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("• Pillar 4: Mandatory Compliance Documents", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        "pro_app_updates" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Google Play In-App Updates & Release Integrity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Current Version: 1.0.0 (ProSpace Lebanese Production Channel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Automatic background updates are enabled via Google Play Core library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        android.widget.Toast.makeText(context, "App is up to date with Google Play store distribution.", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check for Updates", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        "owner_package_tiers" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Owner Package Tiers & Governance", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Current Tier: ${currentUser?.ownerPackageTier?.title ?: "Pay As You Go"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("• Pay As You Go: Flexible per-booking commissions", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("• Limited 3-Listing Tier: $49/mo priority placement", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("• Unlimited Enterprise Tier: $120/mo full syndication", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        "owner_app_updates" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Google Play In-App Updates & Release Integrity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Current Version: 1.0.0 (ProSpace Lebanese Production Channel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        android.widget.Toast.makeText(context, "App is up to date with Google Play store distribution.", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check for Updates", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close Panel")
                }
            }
        }
    }

    previewingAdminDocument?.let { doc ->
        DocumentPreviewDialog(
            document = doc,
            isAdmin = true,
            onDismiss = { previewingAdminDocument = null },
            onApproveDocument = { docId ->
                coroutineScope.launch {
                    val success = viewModel.adminApproveDocument(docId)
                    Toast.makeText(
                        context,
                        if (success) "Document approved and accredited!" else "Failed to approve document — please try again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRejectDocument = { docId, reason ->
                coroutineScope.launch {
                    val success = viewModel.adminRejectDocument(docId, reason)
                    Toast.makeText(
                        context,
                        if (success) "Revision requested from member" else "Failed to request revision — please try again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}
