package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.auth.GrantLookupResult
import com.example.data.model.PackagePlan
import com.example.data.model.PackagePlanCatalog
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_GRANT_DAYS = 3650

/**
 * Stand-alone "find by email → verify identity → pick package → grant" flow. The
 * grant is sent for the exact UID the admin verified, never re-resolved from the email.
 */
@Composable
fun AdminGrantAccessCard(
    adminViewModel: AdminViewModel,
    packagePlans: PackagePlanCatalog
) {
    val state by adminViewModel.grantAccess.collectAsState()
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var selectedPlanId by rememberSaveable { mutableStateOf(PackagePlan.UNLIMITED_GRANT_PLAN_ID) }
    var durationInput by rememberSaveable { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }

    val purchasablePlans = remember(packagePlans) {
        packagePlans.purchasablePlans()
    }
    val isUnlimited = selectedPlanId == PackagePlan.UNLIMITED_GRANT_PLAN_ID
    val selectedPlan = purchasablePlans.firstOrNull { it.id == selectedPlanId }
    LaunchedEffect(selectedPlanId) {
        selectedPlan?.let { durationInput = it.validityDays.toString() }
    }
    val durationDays = durationInput.toIntOrNull()
    val durationValid = isUnlimited || (durationDays != null && durationDays in 1..MAX_GRANT_DAYS)
    val target = state.target
    val canGrant = target != null && target.hasProfile && !target.isDisabled &&
        (isUnlimited || selectedPlan != null) && durationValid && !state.isGranting

    fun runLookup() {
        focusManager.clearFocus()
        adminViewModel.lookupUserForGrant(email)
    }

    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProSectionHeader(
                title = "Grant Pro Host Access",
                subtitle = "Find a user by email, verify it's them, then grant a package and the Pro Host role",
                icon = Icons.Default.WorkspacePremium
            )

            // Step 1 — find
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("User email") },
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
                VerificationPanel(target = target, packagePlans = packagePlans)

                if (!target.hasProfile) {
                    ErrorLine("This user hasn't completed registration, so no package can be granted yet.")
                }
                if (target.isDisabled) {
                    ErrorLine("This account is disabled in Firebase Auth.")
                }
                if (target.isSuspended) {
                    ErrorLine("This account is suspended. The grant will apply, but their listings stay hidden until the suspension is lifted.")
                }

                HorizontalDivider()

                // Step 3 — choose package
                Text("Package to grant", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                PlanOption(
                    selected = isUnlimited,
                    title = "Lifetime Pro Host",
                    detail = "Full Pro Host access · never expires",
                    highlight = true,
                    onSelect = { selectedPlanId = PackagePlan.UNLIMITED_GRANT_PLAN_ID }
                )
                purchasablePlans.forEach { plan ->
                    PlanOption(
                        selected = selectedPlanId == plan.id,
                        title = plan.name,
                        detail = "Pro Host access for a set number of days",
                        highlight = false,
                        onSelect = { selectedPlanId = plan.id }
                    )
                }

                if (!isUnlimited) {
                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) durationInput = v },
                        label = { Text("Duration (days)") },
                        supportingText = { Text("1 to $MAX_GRANT_DAYS days") },
                        isError = !durationValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = { showConfirm = true },
                    enabled = canGrant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isGranting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant", fontWeight = FontWeight.Bold)
                    }
                }

                state.lastGrant?.let { grant ->
                    val planName = packagePlans.packages[grant.packageId]?.name
                        ?: if (grant.packageId == PackagePlan.UNLIMITED_GRANT_PLAN_ID) "Lifetime Pro Host" else grant.packageId
                    Surface(color = StatusSuccess.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                            Text(
                                "Granted $planName (${formatExpiry(grant.expiryMillis)}). Role: ${roleLabel(grant.role)}." +
                                    (if (grant.restoredListings > 0) " ${grant.restoredListings} hidden listing(s) restored." else "") +
                                    " Their app updates immediately.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                TextButton(onClick = {
                    adminViewModel.resetGrantAccess()
                    email = ""
                    selectedPlanId = PackagePlan.UNLIMITED_GRANT_PLAN_ID
                }) { Text("Clear / find another user") }
            }
        }
    }

    if (showConfirm && target != null) {
        val planName = if (isUnlimited) "Lifetime (never expires)" else "${selectedPlan?.name} for $durationDays days"
        val newRole = if (target.role == "ADMIN") "Admin (unchanged)" else "Pro Host"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
            title = { Text("Confirm grant") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${target.fullName.ifBlank { "(no name)" }} · ${target.email}")
                    Text("UID: ${target.uid}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Package: $planName")
                    Text("Role: $newRole")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    adminViewModel.grantAccess(
                        targetUid = target.uid,
                        packageId = if (isUnlimited) null else selectedPlan?.id,
                        durationDays = if (isUnlimited) null else durationDays,
                        unlimited = isUnlimited
                    )
                }) { Text("Grant") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VerificationPanel(target: GrantLookupResult, packagePlans: PackagePlanCatalog) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Verify this is the right person", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(target.fullName.ifBlank { "(no name on profile)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(target.email, style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Text("UID: ${target.uid}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            val packageName = target.ownerPackageId?.let { packagePlans.packages[it]?.name ?: it } ?: "None"
            Text(
                "Role: ${roleLabel(target.role)} · Package: $packageName" +
                    (if (target.ownerPackageId != null) " (${formatExpiry(target.ownerPackageExpiryMillis)})" else "") +
                    " · Active listings: ${target.activeListingCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanOption(selected: Boolean, title: String, detail: String, highlight: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        if (highlight) {
            Icon(Icons.Default.AllInclusive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorLine(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = StatusError)
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
    PackagePlan.isLifetimeExpiry(expiryMillis) -> "never expires"
    else -> "until " + SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expiryMillis))
}
