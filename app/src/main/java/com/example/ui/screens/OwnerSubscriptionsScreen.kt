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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSubscriptionsScreen(
    viewModel: ProHostViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val packagePlans by viewModel.packagePlans.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    // Set by OwnerHubScreen when a Publish attempt hit the listing limit and got
    // saved as a Draft instead — whichever package the host buys next auto-publishes
    // this exact Draft (see entitlements.ts's autoPublishDraftIfNeeded), so surface
    // that clearly rather than leaving the host wondering what buying a package here
    // actually does for them right now.
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()
    val isCheckoutInFlight by viewModel.isWhishCheckoutInFlight.collectAsState()

    val enabledPlans = remember(packagePlans) { packagePlans.packages.values.filter { it.isEnabled }.sortedBy { it.sortOrder } }
    val currentPlan = currentUser?.ownerPackageId?.let { packagePlans.packages[it] }

    var showSubscribeDialog by remember { mutableStateOf(false) }
    var selectedPlanToSubscribe by remember { mutableStateOf<PackagePlan?>(null) }

    var payerName by remember { mutableStateOf(currentUser?.fullName ?: "") }
    var payerPhone by remember { mutableStateOf(currentUser?.phone ?: "+961 70 888 999") }

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
                if ((currentPlan == null || atCap) && enabledPlans.isNotEmpty()) {
                    Button(
                        onClick = {
                            // Prompt an upgrade to a higher package — the same
                            // subscribe dialog below, pre-aimed at the cheapest
                            // enabled package with more room than the current one.
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
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            if (currentPlan == null) "Choose a Package" else "Package Limit Reached — Upgrade Package",
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

    // Subscribe Dialog with Whish Pay — a real, server-priced purchase of whichever
    // admin-defined package the host tapped.
    if (showSubscribeDialog) {
        val targetPlan = selectedPlanToSubscribe
        AlertDialog(
            onDismissRequest = { showSubscribeDialog = false },
            title = { Text("Subscribe to ${targetPlan?.name ?: "Package"}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (targetPlan != null) {
                        Text("Price: $${String.format(Locale.US, "%.2f", targetPlan.priceUsd)} for ${targetPlan.validityDays} Days")
                    }
                    OutlinedTextField(
                        value = payerName,
                        onValueChange = { payerName = it },
                        label = { Text("Payer Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        targetPlan?.let {
                            viewModel.payOwnerPackageViaWhish(
                                packageId = it.id,
                                payerName = payerName,
                                payerPhone = payerPhone,
                                context = context,
                                draftListingId = pendingAutoPublishDraftId
                            )
                        }
                        showSubscribeDialog = false
                    },
                    enabled = targetPlan != null && !isCheckoutInFlight,
                    colors = ButtonDefaults.buttonColors(containerColor = FreshGreen)
                ) {
                    Text(if (isCheckoutInFlight) "Starting payment..." else "Go to Whish Pay", color = PureWhite, fontWeight = FontWeight.Bold)
                }
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
