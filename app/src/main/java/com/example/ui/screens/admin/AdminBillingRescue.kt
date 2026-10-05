package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.auth.PendingPayment
import com.example.data.model.publicCode
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
import com.example.ui.components.ProHostAlertBanner
import com.example.ui.components.ProHostAlertSeverity
import com.example.ui.components.ProHostDialog
import com.example.ui.components.ProSectionHeader
import com.example.ui.components.ProSurfaceCard
import com.example.ui.state.AdminUiState
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun stamp(millis: Long?): String =
    millis?.takeIf { it > 0 }?.let { SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(it)) } ?: "never"

/**
 * Admin › Packages: can the server verify Google Play purchases, and which paid purchases
 * are not active yet. Play refunds a purchase left unacknowledged for 3 days, so each row
 * shows the hours left and offers Retry / Activate for an account (billingRescue.ts).
 */
@Composable
internal fun AdminBillingRescueCard(uiState: AdminUiState, adminViewModel: AdminViewModel) {
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProSectionHeader(
                title = "Billing health",
                subtitle = "Google Play verification and purchases needing attention",
                icon = Icons.Default.HealthAndSafety
            )
            val health = uiState.billingHealth
            when {
                health == null -> Text(
                    if (uiState.isCheckingBilling) "Checking Google Play…" else "Not checked yet.",
                    style = MaterialTheme.typography.bodySmall
                )
                health.isHealthy -> ProHostAlertBanner(
                    message = "${health.message} Last Play notification (RTDN): ${stamp(health.lastRtdnAt)}.",
                    severity = if (health.lastRtdnAt == null) ProHostAlertSeverity.WARNING else ProHostAlertSeverity.SUCCESS,
                    title = if (health.lastRtdnAt == null) "Play API OK — no Play notifications received yet" else "Google Play connected"
                )
                else -> {
                    ProHostAlertBanner(
                        title = if (health.playApi == "config") "Google Play access is not set up" else "Google Play unreachable",
                        message = health.message,
                        severity = ProHostAlertSeverity.ERROR
                    )
                    if (health.playApi == "config") {
                        SelectionContainer {
                            Text(
                                "Play Console › Users and permissions › Invite: " +
                                    (health.serviceAccount ?: "the Cloud Functions service account") +
                                    " with View financial data + Manage orders and subscriptions. Then enable the " +
                                    "Google Play Android Developer API in Google Cloud and tap Check again.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (health?.lastRtdnAt == null && health != null) {
                Text(
                    "Real-time notifications: Play Console › Monetize › Monetization setup › Real-time developer " +
                        "notifications → topic projects/<project>/topics/play-billing-rtdn.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { adminViewModel.refreshBilling() }, enabled = !uiState.isCheckingBilling) {
                Text(if (uiState.isCheckingBilling) "Checking…" else "Check again")
            }
        }
    }
}

@Composable
internal fun AdminPendingPaymentsCard(
    payments: List<PendingPayment>,
    uiState: AdminUiState,
    adminViewModel: AdminViewModel,
    title: String = "Payments needing attention"
) {
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProSectionHeader(
                title = "$title (${payments.size})",
                subtitle = "Paid in Google Play but not active yet",
                icon = Icons.Default.Payments
            )
            if (payments.isEmpty()) {
                Text("Nothing waiting.", style = MaterialTheme.typography.bodySmall)
            }
            payments.forEach { p -> PendingPaymentRow(p, uiState.activatingPaymentId == p.id, adminViewModel) }
        }
    }
}

@Composable
private fun PendingPaymentRow(payment: PendingPayment, busy: Boolean, adminViewModel: AdminViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${payment.userName}${payment.userCode.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""} — ${payment.productId}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Since ${stamp(payment.createdAt)} · ${payment.attempts} attempt(s)" +
                (payment.hoursLeft?.let { " · ~${it} h before Google Play refunds it" } ?: "") +
                (payment.orderId.takeIf { it.isNotBlank() }?.let { " · order $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = if ((payment.hoursLeft ?: 99) < 24) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(payment.hint, style = MaterialTheme.typography.bodySmall)
        if (payment.lastError.isNotBlank()) {
            Text(payment.lastError.take(200), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (payment.kind == "pending" && !payment.needsAdmin) {
                CustomButton(
                    text = if (busy) "Working…" else "Retry now",
                    onClick = { adminViewModel.retryPayment(payment) },
                    enabled = !busy,
                    variant = CustomButtonVariant.PRIMARY,
                    compact = true
                )
            }
            CustomButton(
                text = if (payment.userUid != null && payment.needsAdmin) "Activate for…" else "Activate for another account",
                onClick = { adminViewModel.startAssignPayment(payment) },
                enabled = !busy,
                variant = CustomButtonVariant.OUTLINED,
                icon = Icons.Default.SwapHoriz,
                compact = true
            )
            if (payment.kind == "pending" && payment.needsAdmin && payment.userUid != null) {
                CustomButton(
                    text = "Activate for ${payment.userName.take(14)}",
                    onClick = { adminViewModel.retryPayment(payment) },
                    enabled = !busy,
                    variant = CustomButtonVariant.PRIMARY,
                    compact = true
                )
            }
        }
    }
}

/** The account picker and the reassignment confirmation for "Activate for…". */
@Composable
internal fun AdminBillingDialogs(uiState: AdminUiState, adminViewModel: AdminViewModel) {
    uiState.reassignConfirm?.let { c ->
        ProHostDialog(
            onDismissRequest = { adminViewModel.cancelAssignPayment() },
            title = { Text("Move this purchase?") },
            text = {
                Text(
                    "Google Play records this purchase for ${c.taggedForName}. Activate it for ${c.targetName} instead? " +
                        "Renewals will follow ${c.targetName}. Only do this after confirming with the buyer."
                )
            },
            confirmButton = { TextButton(onClick = { adminViewModel.confirmReassign() }) { Text("Activate for ${c.targetName.take(20)}") } },
            dismissButton = { TextButton(onClick = { adminViewModel.cancelAssignPayment() }) { Text("Cancel") } }
        )
        return
    }
    val payment = uiState.assigningPayment ?: return
    var query by remember(payment.id) { mutableStateOf("") }
    ProHostDialog(
        onDismissRequest = { adminViewModel.cancelAssignPayment() },
        title = { Text("Activate for which account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Search by name, email, U- code or UID.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { adminViewModel.searchAssignTarget(query) }) { Text("Find") } }
                )
                uiState.assignCandidates.forEach { user ->
                    Text(
                        "${user.fullName.ifBlank { user.email }} · ${user.publicCode} · ${user.email}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = uiState.activatingPaymentId == null) { adminViewModel.activatePaymentFor(payment, user) }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { adminViewModel.cancelAssignPayment() }) { Text("Close") } }
    )
}
