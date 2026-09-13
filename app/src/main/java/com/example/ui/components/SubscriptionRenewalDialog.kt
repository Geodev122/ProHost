package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AppUser
import com.example.data.model.PackagePlan
import com.example.ui.theme.Spacing
import com.example.ui.theme.WhishRed
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

/**
 * Replaces the old WhishPayModal-based "Renew" flow, which always charged the flat
 * legacy monthlySubscriptionFeeUsd regardless of the host's real package state. This
 * is an owner-level entitlement dialog, not tied to any one listing:
 *  - No active package (ownerPackageId == null): pick one of the admin-enabled
 *    packages below, then pay for it.
 *  - Has an active package: shows its real name/price, with a "Renew via Whish"
 *    button that buys the same package again (ProHostViewModel.payOwnerPackageViaWhish
 *    with the current package id — extends ownerPackageExpiryMillis by its
 *    validityDays, per entitlements.ts's grantEntitlement).
 */
@Composable
fun SubscriptionRenewalDialog(
    currentUser: AppUser,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packagePlans by viewModel.packagePlans.collectAsState()
    val isWhishCheckoutInFlight by viewModel.isWhishCheckoutInFlight.collectAsState()
    var payerName by remember { mutableStateOf(currentUser.fullName) }
    var payerPhone by remember { mutableStateOf(currentUser.phone) }

    val enabledPlans = remember(packagePlans) { packagePlans.packages.values.filter { it.isEnabled }.sortedBy { it.sortOrder } }
    val currentPlan = currentUser.ownerPackageId?.let { packagePlans.packages[it] }
    var selectedPlan by remember(currentPlan, enabledPlans) { mutableStateOf(currentPlan ?: enabledPlans.firstOrNull()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Renew Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                if (currentPlan == null) {
                    Text(
                        "No active package — choose one below to get started.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    if (enabledPlans.isEmpty()) {
                        Text(
                            "No packages are available right now — please check back later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    enabledPlans.forEach { plan ->
                        PackagePickerRow(
                            plan = plan,
                            isSelected = selectedPlan?.id == plan.id,
                            onSelect = { selectedPlan = plan }
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(currentPlan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (currentPlan.description.isNotBlank()) {
                                Text(currentPlan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                "$${String.format(Locale.US, "%.2f", currentPlan.priceUsd)} / ${currentPlan.validityDays} days",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                OutlinedTextField(
                    value = payerName,
                    onValueChange = { payerName = it },
                    label = { Text("Payer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = payerPhone,
                    onValueChange = { payerPhone = it },
                    label = { Text("Payer Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                val targetPlan = currentPlan ?: selectedPlan
                val canSubmit = payerName.isNotBlank() && payerPhone.isNotBlank() &&
                    targetPlan != null && !isWhishCheckoutInFlight

                Button(
                    onClick = {
                        targetPlan?.let {
                            viewModel.payOwnerPackageViaWhish(it.id, payerName, payerPhone, context = context)
                        }
                        onDismiss()
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = WhishRed)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Pay by Whish", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PackagePickerRow(
    plan: PackagePlan,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    plan.listingLimit?.let { "Up to $it listing${if (it == 1) "" else "s"} · ${plan.validityDays} days" }
                        ?: "Unlimited listings · ${plan.validityDays} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$${String.format(Locale.US, "%.2f", plan.priceUsd)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
