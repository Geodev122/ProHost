package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.viewmodel.ProSpaceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerSubscriptionsScreen(
    viewModel: ProSpaceViewModel
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()

    var showSubscribeDialog by remember { mutableStateOf(false) }
    var selectedTierToSubscribe by remember { mutableStateOf(OwnerPackageTier.LIMITED_3_TIER) }
    var showPaygBuyDialog by remember { mutableStateOf(false) }
    var selectedSpaceTypeForPayg by remember { mutableStateOf(SpaceType.PRIVATE_OFFICE) }

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        Spacer(modifier = Modifier.height(4.dp))
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
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = activeTier.badgeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                    Button(
                        onClick = { showPaygBuyDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buy New Listing Slot (PAYG)", fontWeight = FontWeight.Bold, color = PureWhite)
                    }
                } else {
                    val maxLimit = if (activeTier == OwnerPackageTier.LIMITED_3_TIER) 3 else Int.MAX_VALUE
                    if (ownerSpaces.size >= maxLimit) {
                        Button(
                            onClick = { showPaygBuyDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
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
                    Text("Secure Whish Money Gateway Integration (Channel: 15462415)", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    OutlinedTextField(
                        value = payerName,
                        onValueChange = { payerName = it },
                        label = { Text("Payer Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payerPhone,
                        onValueChange = { payerPhone = it },
                        label = { Text("Whish Account Phone Number") },
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
                            spaceTypeForPayg = if (selectedTierToSubscribe == OwnerPackageTier.PAY_AS_YOU_GO) selectedSpaceTypeForPayg else null,
                            context = context
                        )
                        showSubscribeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FreshGreen)
                ) {
                    Text("Confirm Whish Payment", color = PureWhite, fontWeight = FontWeight.Bold)
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
                    Text("Select Workspace Type for Per-Listing Pricing:")
                    SpaceType.values().forEach { st ->
                        val fee = pricingState.getPaygFeeForType(st)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selectedSpaceTypeForPayg == st) OxfordBlue.copy(alpha = 0.1f) else Color.Transparent, MaterialTheme.shapes.small)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedSpaceTypeForPayg == st,
                                    onClick = { selectedSpaceTypeForPayg = st }
                                )
                                Text(st.displayName, fontWeight = FontWeight.Medium)
                            }
                            Text("$${String.format(Locale.US, "%.2f", fee)}", fontWeight = FontWeight.Bold, color = CarnationOrange)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = payerName,
                        onValueChange = { payerName = it },
                        label = { Text("Payer Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payerPhone,
                        onValueChange = { payerPhone = it },
                        label = { Text("Whish Phone Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.payPaygListingViaWhish(
                            spaceType = selectedSpaceTypeForPayg,
                            payerName = payerName,
                            payerPhone = payerPhone,
                            context = context
                        )
                        showPaygBuyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                ) {
                    Text("Pay via Whish Money", color = PureWhite, fontWeight = FontWeight.Bold)
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

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCurrent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrent) CoolGray else OxfordBlue
                ),
                shape = RoundedCornerShape(10.dp)
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
