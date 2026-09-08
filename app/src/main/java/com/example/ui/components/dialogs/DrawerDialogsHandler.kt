package com.example.ui.components.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.components.drawer.LawBulletinCard
import com.example.ui.theme.LebaneseCedarGreen
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.viewmodel.ProHostViewModel
import com.example.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerDialogsHandler(
    dialogId: String?,
    viewModel: ProHostViewModel,
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

    if (dialogId == "legal_documents") {
        com.example.ui.components.LegalDocumentsMenu(onDismiss = onDismiss)
        return
    }

    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val fcmAlerts by viewModel.fcmAlerts.collectAsState()

    // Audit Logs date-range filter (System Audit Logs dialog) — millis, inclusive.
    var auditFromMillis by remember { mutableStateOf<Long?>(null) }
    var auditToMillis by remember { mutableStateOf<Long?>(null) }
    var showAuditFromPicker by remember { mutableStateOf(false) }
    var showAuditToPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
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
                        "pro_laws" -> "Lebanese Rent Laws"
                        "owner_whish" -> "Whish Money Transactions"
                        "owner_guidelines" -> "Practice Guidelines"
                        "admin_audit" -> "Central Security Audits"
                        "admin_gov" -> "Governorate Node Status"
                        "admin_app_updates" -> "App Version & In-App Updates"
                        "pro_app_updates" -> "App Version & In-App Updates"
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
                        // "Renting Requests"/"Renting Progress" (Pro Host side) and
                        // "Pending Requests"/"Payment Due Reminders" (this section, both
                        // roles) all used to open their own cramped read-only dialogs —
                        // duplicates of content already on the real My Bookings / Renting
                        // Requests / Renting Progress screens, with weaker actions (a
                        // "Send Payment Reminder" that was Toast-only fakery, a WhatsApp
                        // button with no cancellation option, etc.). All four now route
                        // straight to the real screen instead (see AppDrawerContent.kt).
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
                                    title = "Specialist Practice Spaces",
                                    content = "Guarantees professionals the right to rent dedicated shared offices without creating standard full-lease tenant property titles, facilitating flexible multi-day shifts."
                                )
                                LawBulletinCard(
                                    number = "Sublease Safety",
                                    title = "Host Verification Protection",
                                    content = "All hosts on ProHost are legally bound to verify they own or hold exclusive rights to sublease workspace hours, protecting renters from arbitrary closures."
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
                            val sdfShort = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
                            val filteredAuditLogs = remember(auditLogs, auditFromMillis, auditToMillis) {
                                auditLogs.filter { log ->
                                    (auditFromMillis == null || log.timestamp >= auditFromMillis!!) &&
                                        (auditToMillis == null || log.timestamp <= auditToMillis!!)
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                // Date-range filter bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = { showAuditFromPicker = true },
                                        label = { Text(auditFromMillis?.let { "From: ${sdfShort.format(Date(it))}" } ?: "From: Any", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    AssistChip(
                                        onClick = { showAuditToPicker = true },
                                        label = { Text(auditToMillis?.let { "To: ${sdfShort.format(Date(it))}" } ?: "To: Any", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    if (auditFromMillis != null || auditToMillis != null) {
                                        IconButton(onClick = { auditFromMillis = null; auditToMillis = null }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear date filter", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${filteredAuditLogs.size} of ${auditLogs.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(
                                        onClick = {
                                            val csv = viewModel.repository.exportAuditLogsToCsv(auditFromMillis, auditToMillis)
                                            viewModel.shareExportData(context, "Audit Logs", csv)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text("Export CSV", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                    }
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                                ) {
                                    items(filteredAuditLogs) { log ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color.Black)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(log.actionType, color = Color.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                    Text(log.severity, color = if (log.severity == "SECURE") Color.Red else Color.Yellow, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                }
                                                Text(log.details, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                                Text(
                                                    "Actor: ${log.actorEmail} • ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(log.timestamp))}",
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (showAuditFromPicker) {
                                val pickerState = rememberDatePickerState(initialSelectedDateMillis = auditFromMillis)
                                DatePickerDialog(
                                    onDismissRequest = { showAuditFromPicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            auditFromMillis = pickerState.selectedDateMillis
                                            showAuditFromPicker = false
                                        }) { Text("OK") }
                                    },
                                    dismissButton = { TextButton(onClick = { showAuditFromPicker = false }) { Text("Cancel") } }
                                ) {
                                    DatePicker(state = pickerState)
                                }
                            }

                            if (showAuditToPicker) {
                                val pickerState = rememberDatePickerState(initialSelectedDateMillis = auditToMillis)
                                DatePickerDialog(
                                    onDismissRequest = { showAuditToPicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            // Inclusive end-of-day so "To: today" also includes today's entries.
                                            auditToMillis = pickerState.selectedDateMillis?.plus(24L * 60 * 60 * 1000 - 1)
                                            showAuditToPicker = false
                                        }) { Text("OK") }
                                    },
                                    dismissButton = { TextButton(onClick = { showAuditToPicker = false }) { Text("Cancel") } }
                                ) {
                                    DatePicker(state = pickerState)
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
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text("$listingCount active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
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
                                        Spacer(modifier = Modifier.height(Spacing.sm))
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
                                                                tint = if (alert.category == "BOOKING_ACCEPTANCE") StatusSuccess else MaterialTheme.colorScheme.error,
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
                                                                Text("Mark Read", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else {
                                                            Text(
                                                                text = "Read",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                                    Text(
                                                        text = alert.body,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (alert.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Spacer(modifier = Modifier.height(Spacing.xs))
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
                                                                            if (currentUser?.role == UserRole.SPECIALIST) "pro_rentals" else "owner_requests"
                                                                        } else {
                                                                            "owner_progress"
                                                                        }
                                                                        onNavigateToTab(targetTab)
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    modifier = Modifier.height(28.dp)
                                                                ) {
                                                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                                                    Text("Open Screen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                            // A "WhatsApp" quick-reply button used to live here, but FCMAlert
                                                            // carries no related contact/phone at all — it always messaged a
                                                            // single hardcoded number regardless of which alert or user it
                                                            // was for. Removed rather than left sending real messages to an
                                                            // unrelated number; "Open Screen" above still routes to the real
                                                            // screen the alert is about.
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "admin_app_updates" -> {
                            AppUpdatesInfo(profileTabId = "admin_profile", onNavigateToTab = onNavigateToTab, onDismiss = onDismiss)
                        }
                        "pro_app_updates" -> {
                            AppUpdatesInfo(profileTabId = "pro_profile", onNavigateToTab = onNavigateToTab, onDismiss = onDismiss)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close Panel")
                }
            }
        }
    }

}

/**
 * All three role-specific "App Version & Updates" drawer dialogs used to be an
 * identical fake update checker: a hardcoded "1.0.0" version and a button that
 * always Toasted "up to date," completely disconnected from the real
 * InAppUpdateManager already wired into the app's persistent update banner and
 * SpecialistProfileScreen's genuine update UI. Rather than duplicate that real
 * check here too (this dialog has no InAppUpdateManager instance available),
 * this honestly points to where the real status actually lives.
 */
@Composable
private fun AppUpdatesInfo(
    profileTabId: String,
    onNavigateToTab: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("App Updates", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(
            "Update availability and version info are shown on your Profile tab, along with an in-app download banner whenever a new version is ready via Google Play.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onNavigateToTab != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Button(
                onClick = {
                    onNavigateToTab(profileTabId)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Go to Profile", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
