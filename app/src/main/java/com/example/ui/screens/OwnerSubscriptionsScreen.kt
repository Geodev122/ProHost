package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import android.widget.Toast
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
    val context = LocalContext.current
    val activityFromLocal = androidx.activity.compose.LocalActivity.current
    val activity: android.app.Activity? = activityFromLocal ?: run {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is android.app.Activity) return@run ctx
            ctx = ctx.baseContext
        }
        null
    }
    val haptic = LocalHapticFeedback.current
    val currentUser by viewModel.currentUser.collectAsState()
    val packagePlans by viewModel.packagePlans.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()
    val billingActivationPending by viewModel.billingActivationPending.collectAsState()
    val billingError by viewModel.billingError.collectAsState()
    val billingSuccess by viewModel.billingSuccess.collectAsState()
    val playBillingProducts by viewModel.playBillingProducts.collectAsState()
    val billingConnected by viewModel.playBillingConnected.collectAsState()

    // SO3: Play a chime when a purchase completes (billingSuccess transitions to non-null).
    // Replace res/raw/purchase_success.mp3 with a custom asset for a branded sound.
    LaunchedEffect(billingSuccess) {
        if (billingSuccess != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                val uri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                val mp = MediaPlayer.create(context, uri)
                mp?.setOnCompletionListener { it.release() }
                mp?.start()
            } catch (_: Exception) {}
        }
    }

    val enabledPlans = remember(packagePlans) { packagePlans.packages.values.filter { it.isEnabled }.sortedBy { it.sortOrder } }
    val currentPlan = currentUser?.ownerPackageId?.let { packagePlans.packages[it] }

    // Map Play product ID → live formatted price string (e.g. "$4.99") from the Play Store catalog.
    // Falls back to PackagePlan.priceUsd when Play hasn't loaded yet.
    val playPriceMap: Map<String, String?> = remember(playBillingProducts) {
        playBillingProducts.associate { d ->
            d.productId to d.subscriptionOfferDetails
                ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        }
    }
    val pricesLoading = !billingConnected && playBillingProducts.isEmpty()

    val expiryMillis = currentUser?.ownerPackageExpiryMillis
    val remainingDays = if (expiryMillis != null && expiryMillis > System.currentTimeMillis()) {
        ((expiryMillis - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        null
    }
    // True when the host previously had a package that has now lapsed (expiry passed but the
    // sweep hasn't cleared ownerPackageId yet, or they just hit the limit cutover).
    val isSubscriptionExpired = currentPlan != null && remainingDays == null

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

        if (billingActivationPending) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = FreshGreen.copy(alpha = 0.12f)
            ) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = FreshGreen, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                "Activating your subscription — this usually takes a few seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OxfordBlue
                            )
                        }
                        IconButton(onClick = { viewModel.dismissBillingActivationPending() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CoolGray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        "Taking too long? Contact support via WhatsApp.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoolGray
                    )
                }
            }
        }

        if (billingSuccess != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = FreshGreen.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(billingSuccess!!, style = MaterialTheme.typography.bodySmall, color = OxfordBlue)
                    }
                    IconButton(onClick = { viewModel.clearBillingMessages() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CoolGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (billingError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(billingError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    IconButton(onClick = { viewModel.clearBillingMessages() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                    }
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
                            Text("Days Until Renewal", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            Text(
                                remainingDays?.let { if (it == 0) "< 1 day" else "$it days" } ?: "—",
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
                    when {
                        isSubscriptionExpired -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(14.dp))
                            Text(
                                "Subscription expired — your listings are hidden until you renew",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusError
                            )
                        }
                        remainingDays != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(14.dp))
                            Text(
                                "Auto-renews monthly via Google Play",
                                style = MaterialTheme.typography.labelSmall,
                                color = FreshGreen
                            )
                        }
                        else -> Unit
                    }
                }

                val atCap = currentPlan != null && currentPlan.listingLimit != null && ownerSpaces.size >= currentPlan.listingLimit
                val activity = androidx.activity.compose.LocalActivity.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentPlan != null && !isSubscriptionExpired && activity != null) {
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

                if ((currentPlan == null || atCap || isSubscriptionExpired) && enabledPlans.isNotEmpty()) {
                    // Renewal: re-subscribe to the same plan. Upsell: cheapest plan with more capacity.
                    val upsellPlan = if (isSubscriptionExpired) {
                        enabledPlans.firstOrNull { it.id == currentPlan?.id } ?: enabledPlans.firstOrNull()
                    } else {
                        enabledPlans
                            .filter { it.listingLimit == null || it.listingLimit > (currentPlan?.listingLimit ?: 0) }
                            .minByOrNull { it.priceUsd }
                            ?: enabledPlans.firstOrNull()
                    }
                    CustomButton(
                        text = when {
                            isSubscriptionExpired -> "Renew Subscription"
                            currentPlan == null -> "Choose a Package"
                            else -> "Package Limit Reached — Upgrade Package"
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            upsellPlan?.let { plan ->
                                val productId = plan.googlePlayProductId.ifBlank { plan.id }
                                if (activity != null) {
                                    viewModel.launchGooglePaySubscription(activity, productId)
                                } else {
                                    Toast.makeText(context, "Cannot launch Google Play on this device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.AddCircle,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // B3: KYC gate — full identity verification (level 3: profile pic + email + ID
        // upload) is required before purchasing a Pro Host package. Show a blocking
        // notice card describing the specific missing step so the user knows what to do.
        val kycLevel = currentUser?.kycLevel ?: 0
        if (kycLevel < 3) {
            val (kycTitle, kycBody) = when (kycLevel) {
                0 -> "Profile picture required" to
                        "Add a profile picture in the Security ID tab to continue."
                1 -> "Email verification required" to
                        "Verify your email address in the Security ID tab to continue."
                else -> "ID document required" to
                        "Upload a government-issued ID in the Security ID tab to unlock subscription purchases."
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            kycTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            kycBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        Text(
            text = "AVAILABLE SUBSCRIPTION PLANS",
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
            val playProductId = plan.googlePlayProductId.ifBlank { plan.id }
            PackageOptionCard(
                plan = plan,
                isCurrent = currentPlan?.id == plan.id && !isSubscriptionExpired,
                playFormattedPrice = playPriceMap[playProductId],
                priceLoading = pricesLoading,
                onSelect = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (activity != null) {
                        viewModel.launchGooglePaySubscription(activity, playProductId)
                    } else {
                        Toast.makeText(context, "Cannot launch Google Play on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
fun PackageOptionCard(
    plan: PackagePlan,
    isCurrent: Boolean,
    playFormattedPrice: String? = null,
    priceLoading: Boolean = false,
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
                if (priceLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = CarnationOrange
                    )
                } else {
                    // Prefer Play's live price; fall back to the admin-configured Firestore value
                    Text(
                        text = playFormattedPrice ?: "$${String.format(Locale.US, "%.2f", plan.priceUsd)} / mo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = CarnationOrange
                    )
                }
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

            CustomButton(
                text = if (isCurrent) "Current Active Package" else "Subscribe via Google Play",
                onClick = onSelect,
                variant = CustomButtonVariant.PRIMARY,
                icon = if (isCurrent) Icons.Default.Verified else Icons.Default.ShoppingCart,
                enabled = !isCurrent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
