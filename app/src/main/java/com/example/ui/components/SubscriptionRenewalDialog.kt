package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AppUser
import com.example.data.model.PackagePlan
import com.example.ui.theme.Spacing
import com.example.ui.theme.WhishRed
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

/**
 * Replaces the old WhishPayModal-based "Renew" flow with an ultra-carousel dialog
 * displaying all available subscription packages side-by-side, pre-selecting the
 * current active or last subscribed plan for easy renewal while highlighting other
 * tiers for upselling.
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
                    Text("Membership Plans & Renewal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = "Choose your plan below to renew or upgrade your ProHost business tier:",
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
                } else {
                    // Ultra Carousel of all available plans (Renewal + Upselling)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(enabledPlans, key = { it.id }) { plan ->
                            val isSelected = selectedPlan?.id == plan.id
                            val isCurrent = currentPlan?.id == plan.id

                            Card(
                                onClick = { selectedPlan = plan },
                                modifier = Modifier
                                    .width(210.dp)
                                    .shadow(if (isSelected) 6.dp else 2.dp, MaterialTheme.shapes.medium),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = plan.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                color = WhishRed,
                                                shape = MaterialTheme.shapes.extraSmall
                                            ) {
                                                Text(
                                                    text = "Active",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 8.sp,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = plan.listingLimit?.let { "Up to $it listings · ${plan.validityDays}d" } ?: "Unlimited · ${plan.validityDays}d",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", plan.priceUsd)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

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

                val targetPlan = selectedPlan
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
                    Text(text = selectedPlan?.let { "Pay $${String.format(Locale.US, "%.2f", it.priceUsd)} by Whish" } ?: "Pay by Whish", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
