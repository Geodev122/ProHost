package com.example.ui.components

import com.example.ui.theme.proColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.auth.GrantLookupResult
import com.example.data.billing.PlayCatalog
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin → Packages: the only admin billing action, "Force Upgrade → ProHost". Find a user
 * by email or account code → verify identity → confirm. Sent for the exact UID the admin
 * verified, never re-resolved from the email. Permanent until revoked (Users tab).
 */
@Composable
fun AdminGrantAccessCard(adminViewModel: AdminViewModel) {
    val state by adminViewModel.grantAccess.collectAsState()
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val target = state.target
    val alreadyProHost = target?.role == "PRO_HOST" || target?.role == "ADMIN"
    val canUpgrade = target != null && target.hasProfile && !target.isDisabled && !alreadyProHost && !state.isGranting

    fun runLookup() {
        focusManager.clearFocus()
        adminViewModel.lookupUserForGrant(email)
    }

    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProSectionHeader(
                title = "Force Upgrade → ProHost",
                subtitle = "VIP access, customer-service recovery or internal testing. Permanent until revoked in Users.",
                icon = Icons.Default.WorkspacePremium
            )

            // Step 1 — find
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email or account code") },
                    singleLine = true,
                    enabled = !state.isLookingUp && !state.isGranting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (email.isNotBlank()) runLookup() }),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { runLookup() },
                    enabled = email.isNotBlank() && !state.isLookingUp && !state.isGranting
                ) {
                    if (state.isLookingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Find user")
                    }
                }
            }

            state.error?.let { ErrorLine(it) }

            if (target != null) {
                // Step 2 — verify
                VerificationPanel(target = target)

                if (!target.hasProfile) {
                    ErrorLine("This user hasn't completed registration, so they can't be upgraded yet.")
                }
                if (target.isDisabled) {
                    ErrorLine("This account is disabled in Firebase Auth.")
                }
                if (target.isSuspended) {
                    ErrorLine("This account is suspended. The upgrade will apply, but their listings stay hidden until the suspension is lifted.")
                }
                if (alreadyProHost && state.lastGrant == null) {
                    Text(
                        "This account already has ${roleLabel(target.role)} access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Step 3 — upgrade
                Button(
                    onClick = { showConfirm = true },
                    enabled = canUpgrade,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isGranting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Force Upgrade → ProHost", fontWeight = FontWeight.Bold)
                    }
                }

                if (state.lastGrant != null) {
                    Surface(color = MaterialTheme.proColors.success.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.proColors.success)
                            Text(
                                "Upgraded to Pro Host (never expires). Their app updates immediately.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                TextButton(onClick = {
                    adminViewModel.resetGrantAccess()
                    email = ""
                }) { Text("Clear / find another user") }
            }
        }
    }

    if (showConfirm && target != null) {
        ProHostDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
            title = { Text("Force upgrade to Pro Host?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${target.fullName.ifBlank { "(no name)" }} · ${target.email}")
                    Text("Account: ${target.displayCode.ifBlank { target.uid }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Role: ${roleLabel(target.role)} → Pro Host, with no Google Play subscription and no expiry. Revoke it from the Users tab.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    adminViewModel.forceUpgrade(targetUid = target.uid)
                }) { Text("Force Upgrade") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VerificationPanel(target: GrantLookupResult) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Verify this is the right person", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(target.fullName.ifBlank { "(no name on profile)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(target.email, style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Text("Account: ${target.displayCode.ifBlank { target.uid }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            val access = target.ownerPackageId?.let { PlayCatalog.planLabel(it) } ?: "None"
            Text(
                "Role: ${roleLabel(target.role)} · Access: $access" +
                    (if (target.ownerPackageId != null) " (${formatExpiry(target.ownerPackageExpiryMillis)})" else "") +
                    " · Active listings: ${target.activeListingCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorLine(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private fun roleLabel(role: String): String = when (role) {
    "PRO_HOST" -> "Pro Host"
    "ADMIN" -> "Admin"
    "SPECIALIST" -> "Specialist"
    else -> role
}

private fun formatExpiry(expiryMillis: Long?): String = when {
    expiryMillis == null -> "no expiry set"
    PlayCatalog.isLifetimeExpiry(expiryMillis) -> "never expires"
    else -> "until " + SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expiryMillis))
}
