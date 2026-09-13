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
import com.example.data.model.OwnerPackageTier
import com.example.data.model.SpaceArchitectureSchema
import com.example.ui.theme.Spacing
import com.example.ui.theme.WhishRed
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

/**
 * Replaces the old WhishPayModal-based "Renew" flow, which always charged the flat
 * legacy monthlySubscriptionFeeUsd regardless of the host's real package/PAYG state
 * (the "Financial Run-Rate" engine Phase 11 already retired from the Admin Console,
 * but which kept leaking into this Pro-Host-facing dialog). This is an owner-level
 * entitlement dialog, not tied to any one listing:
 *  - PAY_AS_YOU_GO: a real per-category quantity cart ([PaygCartSection]) — pricing
 *    genuinely depends on which listing type the host wants, per the user's own spec.
 *  - LIMITED_3_TIER / UNLIMITED_TIER: the real active package name and fee, with a
 *    "Renew via Whish" button that extends the same package for another 30 days
 *    (ProHostViewModel.payOwnerPackageViaWhish with the current tier — no separate
 *    "SUBSCRIPTION" purpose needed).
 */
@Composable
fun SubscriptionRenewalDialog(
    currentUser: AppUser,
    schema: SpaceArchitectureSchema,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isWhishCheckoutInFlight by viewModel.isWhishCheckoutInFlight.collectAsState()
    var payerName by remember { mutableStateOf(currentUser.fullName) }
    var payerPhone by remember { mutableStateOf(currentUser.phone) }
    var quantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val tier = currentUser.ownerPackageTier
    val isPayg = tier == OwnerPackageTier.PAY_AS_YOU_GO

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

                if (isPayg) {
                    Text(
                        "Pay-as-You-Go is active — pricing depends on the listing type you want.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    val priceableCategories = schema.spaceTypes.filter { it.isEnabled }
                    PaygCartSection(
                        categories = priceableCategories,
                        quantities = quantities,
                        onQuantityChange = { categoryId, newQty ->
                            quantities = quantities.toMutableMap().apply {
                                if (newQty <= 0) remove(categoryId) else put(categoryId, newQty)
                            }
                        }
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(tier.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(tier.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                "$${String.format(Locale.US, "%.2f", viewModel.pricingState.value.getPackageFee(tier))} / month",
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

                val cartItems = remember(quantities) { paygCartItems(quantities) }
                val canSubmit = payerName.isNotBlank() && payerPhone.isNotBlank() &&
                    (!isPayg || cartItems.isNotEmpty()) && !isWhishCheckoutInFlight

                Button(
                    onClick = {
                        if (isPayg) {
                            viewModel.payPaygCartViaWhish(cartItems, payerName, payerPhone, context)
                        } else {
                            viewModel.payOwnerPackageViaWhish(tier, payerName, payerPhone, paygCategoryId = null, context = context)
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
