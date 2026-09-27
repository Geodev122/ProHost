package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.ProHostViewModel

/**
 * Subscription renewal/upgrade carousel. Launches Google Play Billing for all
 * plans — the same flow used by OwnerSubscriptionsScreen's primary Subscribe CTA.
 * Whish is not a valid payment path for in-app digital subscriptions per Play policy.
 */
@Composable
fun SubscriptionRenewalDialog(
    currentUser: AppUser,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx as Activity
            ctx = ctx.baseContext
        }
        null
    }

    val packagePlans by viewModel.packagePlans.collectAsState()
    val playBillingProducts by viewModel.playBillingProducts.collectAsState()
    val playProductMap = remember(playBillingProducts) { playBillingProducts.associateBy { it.productId } }
    fun productOf(plan: PackagePlan) = playProductMap[plan.googlePlayProductId.ifBlank { plan.id }]
    fun priceOf(plan: PackagePlan): String =
        PackagePlan.displayPrice(plan, com.example.data.billing.PlayOfferText.recurringPrice(productOf(plan)))
    fun termsOf(plan: PackagePlan): String =
        com.example.data.billing.PlayOfferText.describe(productOf(plan)) ?: priceOf(plan)

    val enabledPlans = remember(packagePlans) {
        packagePlans.purchasablePlans()
    }
    val currentPlan = currentUser.ownerPackageId?.let { packagePlans.packages[it] }
    var selectedPlan by remember(currentPlan, enabledPlans) {
        mutableStateOf(currentPlan ?: enabledPlans.firstOrNull())
    }

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
                    Text(
                        "Membership Plans",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = "Choose your plan to renew or upgrade your ProHost subscription:",
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
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                                color = FreshGreen,
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
                                        text = "Unlimited listings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = priceOf(plan),
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

                val targetPlan = selectedPlan
                val canSubscribe = targetPlan != null && activity != null

                Button(
                    onClick = {
                        if (activity != null && targetPlan != null) {
                            viewModel.launchGooglePaySubscription(activity, targetPlan.id)
                            onDismiss()
                        }
                    },
                    enabled = canSubscribe,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = selectedPlan?.let {
                            "Subscribe with Google Play — ${termsOf(it)}"
                        } ?: "Subscribe with Google Play",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = "Renews automatically at the price and period shown until you cancel. " +
                        "Cancel anytime in Google Play › Payments & subscriptions; access continues until the paid period ends.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
