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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.LebaneseCedarGreen
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
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
    // A notification's "Open": its tab plus the booking it is about (role-checked by the caller).
    onOpenNotification: ((FCMAlert, String) -> Unit)? = null,
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
    val auditLogs by viewModel.auditLogs.collectAsState()
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
                .padding(vertical = Spacing.md)
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
                        "admin_audit" -> "Central Security Audits"
                        // One title carrying the live unread count — this used to be a
                        // separate chrome title ("Real-time Alerts Terminal") plus a
                        // second, redundant "Notifications" heading inside the content.
                        "fcm_alerts" -> {
                            val unreadCount = fcmAlerts.count { !it.isRead }
                            if (unreadCount > 0) "Notifications ($unreadCount new)" else "Notifications"
                        }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))

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
                                        label = {
                                            Text(
                                                auditFromMillis?.let { "From: ${sdfShort.format(Date(it))}" } ?: "From: Any",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                    AssistChip(
                                        onClick = { showAuditToPicker = true },
                                        label = {
                                            Text(
                                                auditToMillis?.let { "To: ${sdfShort.format(Date(it))}" } ?: "To: Any",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
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
                                    Text(
                                        "${filteredAuditLogs.size} of ${auditLogs.size} entries",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    CustomButton(
                                        text = "Export CSV",
                                        onClick = {
                                            val csv = viewModel.repository.exportAuditLogsToCsv(auditFromMillis, auditToMillis)
                                            viewModel.shareExportData(context, "Audit Logs", csv)
                                        },
                                        variant = CustomButtonVariant.PRIMARY,
                                        icon = Icons.Default.Download,
                                        compact = true
                                    )
                                }

                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                                ) {
                                    items(filteredAuditLogs, key = { it.id }) { log ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(Spacing.sm)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        log.actionType,
                                                        color = MaterialTheme.proColors.success,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Text(
                                                        log.severity,
                                                        color = if (log.severity == "SECURE") MaterialTheme.colorScheme.error else MaterialTheme.proColors.warning,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                Text(log.details, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
                                                Text(
                                                    "Actor: ${log.actorEmail} • " +
                                                        "${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(log.timestamp))}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall
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
                        "fcm_alerts" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (fcmAlerts.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.xl),
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
                                            text = "Real-time updates received via push messaging will appear here.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = Spacing.lg)
                                        )
                                    }
                                } else {
                                    if (fcmAlerts.any { !it.isRead }) {
                                        TextButton(
                                            onClick = { viewModel.markAllAlertsRead() },
                                            modifier = Modifier.align(Alignment.End)
                                        ) { Text("Mark all read", fontWeight = FontWeight.Bold) }
                                    }
                                    Text(
                                        "Notifications are kept for 48 hours.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                                    ) {
                                        items(fcmAlerts, key = { it.id }) { alert ->
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
                                                Column(modifier = Modifier.padding(Spacing.md)) {
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
                                                                imageVector = when (alert.category) {
                                                                    "ADMIN_SUBSCRIPTION" -> Icons.Default.AdminPanelSettings
                                                                    "BOOKING_REQUEST" -> Icons.Default.Inbox
                                                                    "BOOKING_UPDATE" -> Icons.AutoMirrored.Filled.EventNote
                                                                    "PACKAGE_ACTIVATED", "PACKAGE_RENEWED" -> Icons.Default.Verified
                                                                    "PACKAGE_EXPIRED" -> Icons.Default.EventBusy
                                                                    "PAYMENT_REMINDER" -> Icons.Default.CreditCard
                                                                    "LISTING_VERIFICATION", "LISTING_VERIFICATION_REQUEST" -> Icons.AutoMirrored.Filled.FactCheck
                                                                    else -> Icons.Default.Notifications
                                                                },
                                                                contentDescription = null,
                                                                tint = when (alert.category) {
                                                                    "PACKAGE_ACTIVATED", "PACKAGE_RENEWED" -> MaterialTheme.colorScheme.primary
                                                                    "PACKAGE_EXPIRED" -> MaterialTheme.colorScheme.error
                                                                    "PAYMENT_REMINDER" -> MaterialTheme.colorScheme.tertiary
                                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = alert.title,
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = if (alert.isRead) {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurface
                                                                }
                                                            )
                                                        }

                                                        if (!alert.isRead) {
                                                            TextButton(
                                                                onClick = { viewModel.markAlertAsRead(alert.id) },
                                                                contentPadding = PaddingValues(0.dp),
                                                                modifier = Modifier.height(24.dp)
                                                            ) {
                                                                Text(
                                                                    "Mark Read",
                                                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                                    fontWeight = FontWeight.Bold
                                                                )
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
                                                        color = if (alert.isRead) {
                                                            MaterialTheme.colorScheme.outline
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )

                                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = relativeTime(alert.timestamp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )

                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            if (onNavigateToTab != null || onOpenNotification != null) {
                                                                FilledTonalButton(
                                                                    onClick = {
                                                                        viewModel.markAlertAsRead(alert.id)
                                                                        // Prefer the server-provided targetTab; fall back to a
                                                                        // category-to-tab mapping for legacy notifications.
                                                                        val resolvedTab = alert.targetTab?.takeIf { it.isNotBlank() }
                                                                            ?: when (alert.category) {
                                                                                "ADMIN_SUBSCRIPTION" -> "admin_console"
                                                                                "BOOKING_REQUEST" -> "owner_requests"
                                                                                "PAYMENT_REMINDER", "PACKAGE_EXPIRED",
                                                                                "PACKAGE_ACTIVATED", "PACKAGE_RENEWED" -> "owner_subscriptions"
                                                                                "LISTING_VERIFICATION", "LISTING_VERIFICATION_REQUEST" -> "manage_listings"
                                                                                else ->
                                                                                    if (currentUser?.role == UserRole.SPECIALIST)
                                                                                        "pro_rentals"
                                                                                    else "owner_progress"
                                                                            }
                                                                        if (onOpenNotification != null) onOpenNotification(alert, resolvedTab)
                                                                        else onNavigateToTab?.invoke(resolvedTab)
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    modifier = Modifier.height(28.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.AutoMirrored.Filled.ArrowForward,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                                                    Text("Open", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }
    }

}

/** "Just now", "5 min ago", "3 h ago", "Yesterday 14:20", or a date for anything older. */
private fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - timestamp).coerceAtLeast(0) / 60_000
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    val today = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = today.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        today.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    today.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterday = today.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        today.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        sameDay -> "${minutes / 60} h ago"
        yesterday -> "Yesterday $time"
        else -> SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
