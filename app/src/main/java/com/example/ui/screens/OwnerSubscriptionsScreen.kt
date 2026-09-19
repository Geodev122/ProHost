package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSubscriptionsScreen(
    viewModel: ProHostViewModel
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val packagePlans by viewModel.packagePlans.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()

    val enabledPlans = remember(packagePlans) { packagePlans.packages.values.filter { it.isEnabled }.sortedBy { it.sortOrder } }
    val currentPlan = currentUser?.ownerPackageId?.let { packagePlans.packages[it] }

    var showSubscribeDialog by remember { mutableStateOf(false) }
    var selectedPlanToSubscribe by remember { mutableStateOf<PackagePlan?>(null) }

    val expiryMillis = currentUser?.ownerPackageExpiryMillis
    val remainingDays = if (expiryMillis != null && expiryMillis > System.currentTimeMillis()) {
        ((expiryMillis - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (pendingAutoPublishDraftId != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = CarnationOrangeContainer
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CarnationOrange)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        "You have a saved Draft waiting on a purchase. Buy the package it needs below and it will publish automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OxfordBlue
                    )
                }
            }
        }

        // Hero: the currently-active package, replacing what used to be a plain
        // "Subscription & Packages Hub" title/subtitle banner with no real data in
        // it, sitting above a separate status card repeating the same information.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = CarnationOrange, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = currentPlan?.badgeName?.ifBlank { currentPlan.name } ?: "No Package",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                    if (currentPlan != null) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(24.dp))
                    }
                }

                Text(
                    currentPlan?.name ?: "No Active Package",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = PureWhite
                )
                Text(
                    currentPlan?.description?.ifBlank { null } ?: "Choose a package below to start publishing workspace listings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LightGray
                )

                HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                if (currentPlan != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Remaining Duration", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            Text(
                                remainingDays?.let { "$it Days" } ?: "—",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Listings Consumed", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            val limit = currentPlan.listingLimit
                            Text(
                                "${ownerSpaces.size} / ${limit?.toString() ?: "Unlimited"}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                    }
                }

                val atCap = currentPlan != null && currentPlan.listingLimit != null && ownerSpaces.size >= currentPlan.listingLimit
                val activity = androidx.activity.compose.LocalActivity.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentPlan != null && activity != null) {
                        OutlinedButton(
                            onClick = { viewModel.openManageSubscriptions(activity, currentPlan.id) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage in Play Store", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (activity != null) {
                        OutlinedButton(
                            onClick = { viewModel.openRedeemPromoCode(activity) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Redeem Code", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if ((currentPlan == null || atCap) && enabledPlans.isNotEmpty()) {
                    Button(
                        onClick = {
                            selectedPlanToSubscribe = enabledPlans
                                .filter { it.listingLimit == null || it.listingLimit > (currentPlan?.listingLimit ?: 0) }
                                .minByOrNull { it.priceUsd }
                                ?: enabledPlans.firstOrNull()
                            showSubscribeDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            if (currentPlan == null) "Subscribe with Google Pay" else "Package Limit Reached — Upgrade via Google Pay",
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }
        }

        Text(
            text = "AVAILABLE ADMIN PUBLISHED PACKAGES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        if (enabledPlans.isEmpty()) {
            Text(
                "No packages are available right now — please check back later.",
                style = MaterialTheme.typography.bodySmall,
                color = CoolGray
            )
        }

        enabledPlans.forEach { plan ->
            PackageOptionCard(
                plan = plan,
                isCurrent = currentPlan?.id == plan.id,
                onSelect = {
                    selectedPlanToSubscribe = plan
                    showSubscribeDialog = true
                }
            )
        }
    }

    // Subscribe Dialog with Google Pay Billing
    if (showSubscribeDialog) {
        val targetPlan = selectedPlanToSubscribe
        val activity = androidx.activity.compose.LocalActivity.current
        AlertDialog(
            onDismissRequest = { showSubscribeDialog = false },
            title = { Text("Subscribe to ${targetPlan?.name ?: "Package"}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (targetPlan != null) {
                        Text("Price: $${String.format(Locale.US, "%.2f", targetPlan.priceUsd)} / ${targetPlan.validityDays} Days")
                        Text("Secured via Google Play Store & Google Pay. Cancel or manage anytime in Play Store settings.", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    }
                }
            },
            confirmButton = {
                CustomButton(
                    text = "Subscribe with Google Pay",
                    onClick = {
                        targetPlan?.let { plan ->
                            activity?.let { act ->
                                viewModel.launchGooglePaySubscription(act, plan.id)
                            }
                        }
                        showSubscribeDialog = false
                    },
                    variant = CustomButtonVariant.SUCCESS,
                    icon = Icons.Default.ShoppingCart,
                    enabled = targetPlan != null && activity != null,
                    compact = true
                )
            },
            dismissButton = {
                TextButton(onClick = { showSubscribeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PackageOptionCard(
    plan: PackagePlan,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) OxfordBlue.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isCurrent) BorderStroke(2.dp, CarnationOrange) else null,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OxfordBlue
                )
                Text(
                    text = "$${String.format(Locale.US, "%.2f", plan.priceUsd)} / ${plan.validityDays}d",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = CarnationOrange
                )
            }

            if (plan.description.isNotBlank()) {
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoolGray
                )
            }

            HorizontalDivider(color = LightGray.copy(alpha = 0.5f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = plan.listingLimit?.let { "Host up to $it active workspace${if (it == 1) "" else "s"}" } ?: "Unlimited active workspace listings",
                    style = MaterialTheme.typography.bodySmall,
                    color = OxfordBlue
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCurrent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrent) CoolGray else OxfordBlue
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (isCurrent) "Current Active Package" else "Subscribe / Activate Package",
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            }
        }
    }
}
