package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSubscriptionsScreen(
    viewModel: ProHostViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    // Set by OwnerHubScreen when a Publish attempt hit the listing limit or a
    // missing PAYG credit and got saved as a Draft instead — whichever purchase the
    // host makes next auto-publishes this exact Draft (see entitlements.ts's
    // autoPublishDraftIfNeeded), so surface that clearly rather than leaving the
    // host wondering what buying a slot here actually does for them right now.
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()
    // Admin-defined Space Category catalog — same fallback-to-legacy-4 pattern as
    // CreateListingDialog's picker, so the Buy dialog and the wizard never disagree
    // about what categories exist.
    val paygCategoryOptions = remember(architectureSchema) {
        architectureSchema.spaceTypes.filter { it.isEnabled }.ifEmpty {
            SpaceType.values().map { legacy -> SchemaItem(id = legacy.name, name = legacy.displayName, category = "SPACE_TYPE") }
        }
    }

    var showSubscribeDialog by remember { mutableStateOf(false) }
    var selectedTierToSubscribe by remember { mutableStateOf(OwnerPackageTier.LIMITED_3_TIER) }
    var showPaygBuyDialog by remember { mutableStateOf(false) }
    var selectedCategoryForPayg by remember(paygCategoryOptions) { mutableStateOf(paygCategoryOptions.firstOrNull()) }

    var payerName by remember { mutableStateOf(currentUser?.fullName ?: "") }
    var payerPhone by remember { mutableStateOf(currentUser?.phone ?: "+961 70 888 999") }

    val activeTier = currentUser?.ownerPackageTier ?: OwnerPackageTier.PAY_AS_YOU_GO
    val expiryMillis = currentUser?.ownerPackageExpiryMillis
    val remainingDays = if (expiryMillis != null && expiryMillis > System.currentTimeMillis()) {
        ((expiryMillis - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        30
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
                        "You have a saved Draft waiting on a purchase. Buy the plan or slot it needs below and it will publish automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OxfordBlue
                    )
                }
            }
        }

        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Subscription & Packages Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "Manage your ProHost hosting tiers, PAYG listings, and Whish billing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LightGray
                        )
                    }
                    Icon(Icons.Default.Layers, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Active Package Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = FreshGreen.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = FreshGreen, modifier = Modifier.padding(6.dp).size(20.dp))
                        }
                        Text(
                            text = "Active Hosting Subscription",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OxfordBlue
                        )
                    }
                    Surface(
                        color = CarnationOrange,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = activeTier.badgeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }

                HorizontalDivider(color = LightGray.copy(alpha = 0.5f))

                Text(
                    text = activeTier.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = OxfordBlue
                )
                Text(
                    text = activeTier.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Remaining Duration:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                        Text(text = "$remainingDays Days Remaining", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = FreshGreen)
                    }
                    if (activeTier == OwnerPackageTier.PAY_AS_YOU_GO) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "PAYG Listings Bought:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                            Text(text = "${currentUser?.paygListingsBoughtCount ?: 0} Slots", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OxfordBlue)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Listings Consumed:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                            val maxLimit = if (activeTier == OwnerPackageTier.LIMITED_3_TIER) 3 else Int.MAX_VALUE
                            val used = ownerSpaces.size
                            Text(text = "$used / ${if (maxLimit == Int.MAX_VALUE) "Unlimited" else maxLimit}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OxfordBlue)
                        }
                    }
                }

                if (activeTier == OwnerPackageTier.PAY_AS_YOU_GO) {
                    val unspentCredits = (currentUser?.paygCategoryCredits ?: emptyMap()).filter { it.value > 0 }
                    if (unspentCredits.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Unused paid slots by category:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                            unspentCredits.forEach { (categoryId, count) ->
                                val name = paygCategoryOptions.firstOrNull { it.id == categoryId }?.name ?: categoryId
                                Text("• $name: $count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = OxfordBlue)
                            }
                        }
                    }
                } else {
                    // Per-category quota display (spec): the overall cap is still
                    // whole-listing, not per-category — this breaks down how much of
                    // that shared cap each category is already using, it doesn't
                    // imply an independent limit per category.
                    val maxLimit = if (activeTier == OwnerPackageTier.LIMITED_3_TIER) 3 else Int.MAX_VALUE
                    val byCategory = ownerSpaces.groupingBy { it.spaceCategoryName ?: it.spaceType.displayName }.eachCount()
                    if (byCategory.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Listings by category:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                            byCategory.forEach { (name, count) ->
                                Text(
                                    "• $name: $count${if (maxLimit == Int.MAX_VALUE) "" else " / $maxLimit shared"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = OxfordBlue
                                )
                            }
                        }
                    }
                }

                if (activeTier == OwnerPackageTier.PAY_AS_YOU_GO) {
                    Button(
                        onClick = { showPaygBuyDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Buy New Listing Slot (PAYG)", fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                } else {
                    val maxLimit = if (activeTier == OwnerPackageTier.LIMITED_3_TIER) 3 else Int.MAX_VALUE
                    if (ownerSpaces.size >= maxLimit) {
                        Button(
                            onClick = { showPaygBuyDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Package Limit Reached — Buy Additional Listing Slot", fontWeight = FontWeight.Bold, color = PureWhite)
                        }
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

        // Package 1: PAYG
        PackageOptionCard(
            tier = OwnerPackageTier.PAY_AS_YOU_GO,
            priceDisplay = "$${String.format(Locale.US, "%.2f", pricingState.monthlySubscriptionFeeUsd)} base / per-listing type",
            benefits = listOf(
                "No monthly recurring commitment",
                "Pay per listing based on workspace type configured by admin",
                "Instant activation via Whish Pay API",
                "Full access to owner analytics & requests"
            ),
            isCurrent = activeTier == OwnerPackageTier.PAY_AS_YOU_GO,
            onSelect = {
                selectedTierToSubscribe = OwnerPackageTier.PAY_AS_YOU_GO
                showSubscribeDialog = true
            }
        )

        // Package 2: Limited 3 Tier
        PackageOptionCard(
            tier = OwnerPackageTier.LIMITED_3_TIER,
            priceDisplay = "$${String.format(Locale.US, "%.2f", pricingState.package2MonthlyFeeUsd)} / month",
            benefits = listOf(
                "Host & operate up to 3 active workspaces",
                "Bundled monthly fee with significant savings",
                "Verified host badge & priority placement in search",
                "Automatic monthly renewal via Whish Pay"
            ),
            isCurrent = activeTier == OwnerPackageTier.LIMITED_3_TIER,
            onSelect = {
                selectedTierToSubscribe = OwnerPackageTier.LIMITED_3_TIER
                showSubscribeDialog = true
            }
        )

        // Package 3: Unlimited Enterprise
        PackageOptionCard(
            tier = OwnerPackageTier.UNLIMITED_TIER,
            priceDisplay = "$${String.format(Locale.US, "%.2f", pricingState.package3MonthlyFeeUsd)} / month",
            benefits = listOf(
                "Publish unlimited active workspace listings",
                "Featured placement on ProHost explorer hero banners",
                "VIP commercial host support & verified badge",
                "Advanced analytics, campaign tools & direct inquiries"
            ),
            isCurrent = activeTier == OwnerPackageTier.UNLIMITED_TIER,
            onSelect = {
                selectedTierToSubscribe = OwnerPackageTier.UNLIMITED_TIER
                showSubscribeDialog = true
            }
        )
    }

    // Subscribe Dialog with Whish Pay
    if (showSubscribeDialog) {
        AlertDialog(
            onDismissRequest = { showSubscribeDialog = false },
            title = { Text("Subscribe to ${selectedTierToSubscribe.title}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Price: $${String.format(Locale.US, "%.2f", pricingState.getPackageFee(selectedTierToSubscribe))} for 30 Days")
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
                        viewModel.payOwnerPackageViaWhish(
                            tier = selectedTierToSubscribe,
                            payerName = payerName,
                            payerPhone = payerPhone,
                            paygCategoryId = if (selectedTierToSubscribe == OwnerPackageTier.PAY_AS_YOU_GO) selectedCategoryForPayg?.id else null,
                            context = context,
                            draftListingId = pendingAutoPublishDraftId
                        )
                        showSubscribeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FreshGreen)
                ) {
                    Text("Go to Whish Pay", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscribeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Buy PAYG Listing Slot Dialog
    if (showPaygBuyDialog) {
        AlertDialog(
            onDismissRequest = { showPaygBuyDialog = false },
            title = { Text("Buy New Listing Slot (PAYG)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Workspace Category for Per-Listing Pricing:")
                    paygCategoryOptions.forEach { category ->
                        // Falls back to the legacy 4-value fee switch only for a
                        // category the admin hasn't set a price on yet (shouldn't
                        // happen post-seed, but keeps this dialog from showing $0).
                        val legacyType = when (category.id) {
                            "ST-01", SpaceType.PRIVATE_OFFICE.name -> SpaceType.PRIVATE_OFFICE
                            "ST-02", SpaceType.CENTER.name -> SpaceType.CENTER
                            "ST-03", SpaceType.POLYCLINIC.name -> SpaceType.POLYCLINIC
                            "ST-04", SpaceType.COWORKING_SPACE.name -> SpaceType.COWORKING_SPACE
                            else -> null
                        }
                        val fee = category.priceUsd ?: legacyType?.let { pricingState.getPaygFeeForType(it) } ?: 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedCategoryForPayg?.id == category.id) OxfordBlue.copy(alpha = 0.1f) else Color.Transparent, MaterialTheme.shapes.small)
                                .padding(Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedCategoryForPayg?.id == category.id,
                                    onClick = { selectedCategoryForPayg = category }
                                )
                                Text(category.name, fontWeight = FontWeight.Medium)
                            }
                            Text("$${String.format(Locale.US, "%.2f", fee)}", fontWeight = FontWeight.Bold, color = CarnationOrange)
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
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
                        val categoryId = selectedCategoryForPayg?.id
                        if (categoryId != null) {
                            viewModel.payPaygListingViaWhish(
                                categoryId = categoryId,
                                payerName = payerName,
                                payerPhone = payerPhone,
                                context = context,
                                draftListingId = pendingAutoPublishDraftId
                            )
                        }
                        showPaygBuyDialog = false
                    },
                    enabled = selectedCategoryForPayg != null,
                    colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                ) {
                    Text("Go to Whish Pay", color = PureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaygBuyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PackageOptionCard(
    tier: OwnerPackageTier,
    priceDisplay: String,
    benefits: List<String>,
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
                    text = tier.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OxfordBlue
                )
                Text(
                    text = priceDisplay,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = CarnationOrange
                )
            }

            Text(
                text = tier.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CoolGray
            )

            HorizontalDivider(color = LightGray.copy(alpha = 0.5f))

            benefits.forEach { benefit ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(16.dp))
                    Text(text = benefit, style = MaterialTheme.typography.bodySmall, color = OxfordBlue)
                }
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
