package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.ui.components.PaygCartSection
import com.example.ui.components.paygCartItems
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
    val pricingState by viewModel.pricingState.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    // Set by OwnerHubScreen when a Publish attempt hit the listing limit or a
    // missing PAYG credit and got saved as a Draft instead — whichever purchase the
    // host makes next auto-publishes this exact Draft (see entitlements.ts's
    // autoPublishDraftIfNeeded), so surface that clearly rather than leaving the
    // host wondering what buying a slot here actually does for them right now.
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()
    val isCheckoutInFlight by viewModel.isWhishCheckoutInFlight.collectAsState()
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
    var showPaygCartDialog by remember { mutableStateOf(false) }
    var cartQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

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
                            text = activeTier.badgeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                    Icon(Icons.Default.Verified, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(24.dp))
                }

                Text(activeTier.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = PureWhite)
                Text(activeTier.subtitle, style = MaterialTheme.typography.bodyMedium, color = LightGray)

                HorizontalDivider(color = LightGray.copy(alpha = 0.3f))

                if (activeTier == OwnerPackageTier.PAY_AS_YOU_GO) {
                    val unspentCredits = (currentUser?.paygCategoryCredits ?: emptyMap()).filter { it.value > 0 }
                    Text(
                        text = "${currentUser?.paygListingsBoughtCount ?: 0} slots bought in total",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                    if (unspentCredits.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Unused paid slots by category:", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            unspentCredits.forEach { (categoryId, count) ->
                                val name = paygCategoryOptions.firstOrNull { it.id == categoryId }?.name ?: categoryId
                                Text("• $name: $count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = PureWhite)
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Remaining Duration", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            Text("$remainingDays Days", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = PureWhite)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Listings Consumed", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            val maxLimit = if (activeTier == OwnerPackageTier.LIMITED_3_TIER) 3 else Int.MAX_VALUE
                            Text(
                                "${ownerSpaces.size} / ${if (maxLimit == Int.MAX_VALUE) "Unlimited" else maxLimit}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                    }
                }

                val maxLimit = if (activeTier == OwnerPackageTier.LIMITED_3_TIER) 3 else Int.MAX_VALUE
                if (activeTier == OwnerPackageTier.PAY_AS_YOU_GO || ownerSpaces.size >= maxLimit) {
                    Button(
                        onClick = { showPaygCartDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            if (activeTier == OwnerPackageTier.PAY_AS_YOU_GO) "Buy Listing Slots" else "Package Limit Reached — Buy Additional Slot",
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

        // Package 1: PAYG — visually distinct from the two flat-fee tiers below
        // (no single price figure, since one doesn't apply — see PaygPackageCard).
        PaygPackageCard(
            categories = paygCategoryOptions,
            isCurrent = activeTier == OwnerPackageTier.PAY_AS_YOU_GO,
            onSelect = { showPaygCartDialog = true }
        )

        // Package 2: Limited 3 Tier
        PackageOptionCard(
            tier = OwnerPackageTier.LIMITED_3_TIER,
            priceDisplay = "$${String.format(Locale.US, "%.2f", pricingState.package2MonthlyFeeUsd)} / month",
            benefits = listOf(
                "Host up to 3 active workspaces",
                "Verified host badge & priority placement"
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
                "Unlimited active workspace listings",
                "Featured placement & VIP host support"
            ),
            isCurrent = activeTier == OwnerPackageTier.UNLIMITED_TIER,
            onSelect = {
                selectedTierToSubscribe = OwnerPackageTier.UNLIMITED_TIER
                showSubscribeDialog = true
            }
        )
    }

    // Subscribe Dialog with Whish Pay — tiered packages only now (Package 2/3);
    // PAYG activates purely through the cart dialog below, since "subscribing" to
    // PAYG has no single price to confirm here.
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
                            paygCategoryId = null,
                            context = context,
                            draftListingId = pendingAutoPublishDraftId
                        )
                        showSubscribeDialog = false
                    },
                    enabled = !isCheckoutInFlight,
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

    // Buy PAYG Listing Credits — a real multi-category quantity cart (see the
    // Renew popup on My Listings, which shares this exact same PaygCartSection),
    // replacing what used to be a single-category radio picker here.
    if (showPaygCartDialog) {
        AlertDialog(
            onDismissRequest = { showPaygCartDialog = false },
            title = { Text("Buy Listing Credits (PAYG)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaygCartSection(
                        categories = paygCategoryOptions,
                        quantities = cartQuantities,
                        onQuantityChange = { categoryId, newQty ->
                            cartQuantities = cartQuantities.toMutableMap().apply {
                                if (newQty <= 0) remove(categoryId) else put(categoryId, newQty)
                            }
                        }
                    )
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
                val cartItems = paygCartItems(cartQuantities)
                Button(
                    onClick = {
                        viewModel.payPaygCartViaWhish(cartItems, payerName, payerPhone, context)
                        cartQuantities = emptyMap()
                        showPaygCartDialog = false
                    },
                    enabled = cartItems.isNotEmpty() && !isCheckoutInFlight,
                    colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                ) {
                    Text(
                        if (isCheckoutInFlight) "Starting payment..." else "Go to Whish Pay",
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaygCartDialog = false }) {
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

/**
 * PAYG's card, deliberately shaped differently from [PackageOptionCard] — it never
 * had one real flat price to show next to a title the way Package 2/3 do (the old
 * design showed monthlySubscriptionFeeUsd there, wrongly implying PAYG has the same
 * kind of flat base fee the other two tiers do). Instead: a dashed border read as
 * "flexible," and a live preview of the real admin-set per-category prices this
 * plan actually charges.
 */
@Composable
fun PaygPackageCard(
    categories: List<SchemaItem>,
    isCurrent: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) OxfordBlue.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) CarnationOrange else CoolGray.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(20.dp))
                Text(
                    text = OwnerPackageTier.PAY_AS_YOU_GO.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OxfordBlue
                )
            }

            Text(
                text = "Priced per listing type — no monthly commitment.",
                style = MaterialTheme.typography.bodySmall,
                color = CoolGray
            )

            HorizontalDivider(color = LightGray.copy(alpha = 0.5f))

            if (categories.isEmpty()) {
                Text("No admin-published categories yet.", style = MaterialTheme.typography.bodySmall, color = CoolGray)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { category ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
                                Text(category.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    category.priceUsd?.let { "$${String.format(Locale.US, "%.2f", it)}" } ?: "—",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CarnationOrange
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrent) CoolGray else OxfordBlue
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (isCurrent) "Buy More Listing Slots" else "Activate Pay-As-You-Go",
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
            }
        }
    }
}
