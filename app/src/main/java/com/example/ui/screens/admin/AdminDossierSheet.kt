package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.auth.AdminUserDossier
import com.example.data.model.UserRole
import com.example.data.model.publicCode
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
import com.example.ui.components.ProHostBottomSheet
import com.example.ui.components.ProSectionHeader
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun day(millis: Long?): String =
    millis?.takeIf { it > 0 }?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "—"

/**
 * Everything about one account, from adminUserDossier: identity (U- code + Firebase UID),
 * role and package, a Pro Host's listings with their room codes, the Play subscription
 * history (plans, states, dates, order ids — amounts live in Play Console) and a
 * specialist's rental history. The existing admin actions open from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminDossierSheet(dossier: AdminUserDossier, adminViewModel: AdminViewModel) {
    val user = dossier.profile
    ProHostBottomSheet(
        onDismissRequest = { adminViewModel.closeDossier() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            ProSectionHeader(
                title = user.fullName.ifBlank { "Unnamed account" },
                subtitle = "${user.publicCode} · ${user.role.name.replace("_", " ")}",
                icon = Icons.Default.Badge
            )

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Field("Display ID", user.publicCode)
                    Field("Firebase UID", user.id)
                    Field("Email", (dossier.authEmail ?: user.email).ifBlank { "—" } +
                        if (dossier.authEmailVerified || user.emailVerified) " (verified)" else "")
                    Field("Phone", (dossier.authPhone ?: user.phone).ifBlank { "—" } +
                        if (!dossier.authPhone.isNullOrBlank()) " (verified)" else "")
                    Field("Sign-in", dossier.providers.joinToString().ifBlank { "—" })
                    Field("Location", listOf(user.city, user.country).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" })
                    Field("Member since", day(user.createdAtMillis))
                    Field("Last sign-in", day(user.lastSignInAtMillis))
                    if (user.isSuspended) Field("Status", "SUSPENDED")
                    if (dossier.authMissing) Field("Auth account", "missing (profile only)")
                }
            }

            if (user.role == UserRole.PRO_HOST || user.ownerPackageId != null) {
                HorizontalDivider()
                Section("Package")
                Field("Plan", user.ownerPackageId ?: "—")
                Field("Source", user.entitlementSource ?: "—")
                Field("Billing status", user.billingStatus ?: "—")
                Field("Access until", day(user.ownerPackageExpiryMillis))
            }

            if (dossier.listings.isNotEmpty() || user.role == UserRole.PRO_HOST) {
                HorizontalDivider()
                Section("Listings (${dossier.listings.size}) · bookings received: ${dossier.hostBookingsCount}")
                dossier.listings.forEach { l ->
                    Text(
                        "${l.displayCode.ifBlank { l.id }} · ${l.title.ifBlank { "Untitled" }} · ${l.status}" +
                            if (l.isVerified) " · verified" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    l.rooms.forEach { r ->
                        Text(
                            "   ${r.displayCode.ifBlank { r.id }} · ${r.name.ifBlank { "Room" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()
            Section("Subscription history (${dossier.subscriptions.size})")
            if (dossier.subscriptions.isEmpty()) {
                Text("No Google Play subscriptions.", style = MaterialTheme.typography.bodySmall)
            }
            dossier.subscriptions.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${s.basePlanId.ifBlank { "Premium" }} · ${s.status}" + if (s.autoRenewing) " · auto-renews" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${day(s.startDate)} → ${day(s.expiryDate)} · order ${s.orderId.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "Amounts are in Play Console (Order management), looked up by order id.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (dossier.rentals.isNotEmpty() || user.role == UserRole.SPECIALIST) {
                HorizontalDivider()
                Section("Rental history (${dossier.rentals.size})")
                dossier.rentals.forEach { b ->
                    Text(
                        "${b.publicCode} · ${b.spaceTitle.ifBlank { "Listing" }} · ${b.startDate} · " +
                            "${b.status.name} · $${"%.2f".format(Locale.US, b.totalAmountUsd)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()
            Section("Actions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomButton(
                    text = "Edit",
                    onClick = { adminViewModel.openEditUserDialog(user) },
                    modifier = Modifier.weight(1f),
                    variant = CustomButtonVariant.OUTLINED,
                    compact = true
                )
                if (user.role != UserRole.ADMIN) {
                    CustomButton(
                        text = if (user.isSuspended) "Reactivate" else "Suspend",
                        onClick = { adminViewModel.openSuspendUserDialog(user) },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED,
                        compact = true
                    )
                }
                if (user.role == UserRole.PRO_HOST) {
                    CustomButton(
                        text = "Revoke Pro Host",
                        onClick = { adminViewModel.openRevokeProHostDialog(user) },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.DANGER,
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun Field(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
