package com.example.ui.screens

import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.*
import com.example.data.billing.PlayOfferText
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
import com.example.ui.components.ProHostDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import java.text.SimpleDateFormat
import java.util.Date
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
    var showKycDialog by remember { mutableStateOf(false) }
    var pendingProductId by remember { mutableStateOf<String?>(null) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var redeemCodeInput by remember { mutableStateOf("") }
    val currentUser by viewModel.currentUser.collectAsState()
    val packagePlans by viewModel.packagePlans.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()
    val billingActivationPending by viewModel.billingActivationPending.collectAsState()
    val billingError by viewModel.billingError.collectAsState()
    val billingSuccess by viewModel.billingSuccess.collectAsState()
    val playBillingProducts by viewModel.playBillingProducts.collectAsState()
    val billingConnected by viewModel.playBillingConnected.collectAsState()
    val playActivePurchases by viewModel.playActivePurchases.collectAsState()

    // Refresh purchases every time the screen resumes (e.g. returning from Play Store).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPlayPurchases(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show Play's native in-app subscription messages on screen entry (grace period, hold, etc.).
    LaunchedEffect(Unit) {
        activity?.let { viewModel.showBillingInAppMessages(it) }
    }

    // SO3: Play branded chime when a purchase completes (billingSuccess transitions to non-null).
    // The banners sit at the top of a scrolling screen, out of view from the plan
    // cards — also toast errors so a failed tap never looks like nothing happened.
    LaunchedEffect(billingError) {
        billingError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(billingSuccess) {
        if (billingSuccess != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                val mp = MediaPlayer.create(context, com.example.R.raw.purchase_success)
                mp?.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                mp?.setOnCompletionListener { it.release() }
                mp?.start()
            } catch (e: Exception) {
                android.util.Log.e("SoundPlayback", "Failed to play sound", e)
            }
        }
    }

    val enabledPlans = remember(packagePlans) { packagePlans.purchasablePlans() }
    val currentPlan = currentUser?.ownerPackageId?.let { packagePlans.packages[it] }

    // Map Play product ID → live formatted price string (e.g. "$4.99") from the Play Store catalog.
    // PackagePlan.displayPrice falls back when Play hasn't loaded yet.
    val playProductMap = remember(playBillingProducts) { playBillingProducts.associateBy { it.productId } }
    val pricesLoading = !billingConnected && playBillingProducts.isEmpty()
    // One view_plans per visit, once Play has had a moment to answer.
    val latestPlanCount by rememberUpdatedState(enabledPlans.size)
    val latestProductCount by rememberUpdatedState(playBillingProducts.size)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3_000L)
        com.example.analytics.AnalyticsTracker.viewPlans(latestPlanCount, latestProductCount)
    }

    val expiryMillis = currentUser?.ownerPackageExpiryMillis
    val now = System.currentTimeMillis()
    val remainingDays = if (expiryMillis != null && expiryMillis > now) {
        ((expiryMillis - now) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        null
    }
    val isSubscriptionExpired = currentPlan != null && remainingDays == null
    val isLifetimeGrant = PackagePlan.isLifetimeExpiry(expiryMillis)
    val expiryDateString = remember(expiryMillis, isLifetimeGrant) {
        if (expiryMillis != null && !isLifetimeGrant) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expiryMillis))
        } else null
    }
    // Find the Play purchase for the current plan so we can read isAutoRenewing.
    val currentPlayPurchase = remember(playActivePurchases, currentPlan) {
        val pid = currentPlan?.googlePlayProductId?.ifBlank { currentPlan.id } ?: return@remember null
        playActivePurchases.firstOrNull { p -> p.products.contains(pid) }
    }
    val isAutoRenewing = currentPlayPurchase?.isAutoRenewing ?: true

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
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        "You have a saved Draft waiting on a purchase. Buy the package it needs below and it will publish automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (billingActivationPending) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.proColors.success.copy(alpha = 0.12f)
            ) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.proColors.success, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                "Activating your subscription — this usually takes a few seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.dismissBillingActivationPending() }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        "Taking too long? Contact support via WhatsApp.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        billingSuccess?.let { msg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.proColors.success.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.proColors.success, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { viewModel.clearBillingMessages() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        billingError?.let { err ->
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
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    IconButton(onClick = { viewModel.clearBillingMessages() }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
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
            color = MaterialTheme.proColors.brandHeaderStart,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = currentPlan?.badgeName?.ifBlank { currentPlan.name } ?: "No Package",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                    if (currentPlan != null) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.proColors.headerSuccess, modifier = Modifier.size(24.dp))
                    }
                }

                Text(
                    currentPlan?.name ?: "No Active Package",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.proColors.onBrandHeader
                )
                Text(
                    currentPlan?.description?.ifBlank { null } ?: "Choose a package below to start publishing workspace listings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f)
                )

                HorizontalDivider(color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.3f))

                if (currentPlan != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                when {
                                    isLifetimeGrant -> "Expires"
                                    !isAutoRenewing && remainingDays != null -> "Ends"
                                    else -> "Renews"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f)
                            )
                            Text(
                                when {
                                    isLifetimeGrant -> "Never"
                                    remainingDays == null -> "Expired"
                                    remainingDays == 0 -> "< 1 day"
                                    expiryDateString != null -> expiryDateString
                                    else -> "$remainingDays days"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingDays != null || isLifetimeGrant) MaterialTheme.proColors.onBrandHeader else MaterialTheme.proColors.headerError
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Listings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f))
                            Text(
                                "${ownerSpaces.size} · Unlimited",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.proColors.onBrandHeader
                            )
                        }
                    }
                    when {
                        isSubscriptionExpired -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.proColors.headerError, modifier = Modifier.size(14.dp))
                            Text(
                                "Subscription expired — your listings are hidden until you renew",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.proColors.headerError
                            )
                        }
                        currentPlan.isGrantOnly || isLifetimeGrant -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = MaterialTheme.proColors.headerSuccess, modifier = Modifier.size(14.dp))
                            Text(
                                "Complimentary access granted by ProHost",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.proColors.headerSuccess
                            )
                        }
                        !isAutoRenewing && remainingDays != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.proColors.headerWarning, modifier = Modifier.size(14.dp))
                            Text(
                                "Cancelled — access continues until $expiryDateString",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.proColors.headerWarning
                            )
                        }
                        remainingDays != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.proColors.headerSuccess, modifier = Modifier.size(14.dp))
                            Text(
                                "Renews automatically via Google Play",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.proColors.headerSuccess
                            )
                        }
                        else -> Unit
                    }
                }

                val activity = androidx.activity.compose.LocalActivity.current

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (currentPlan != null && !currentPlan.isGrantOnly && !isSubscriptionExpired && activity != null) {
                        TextButton(
                            onClick = { viewModel.openManageSubscriptions(activity, currentPlan.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.proColors.onBrandHeader)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manage in Play Store", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (activity != null) {
                        TextButton(
                            onClick = { showRedeemDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.proColors.onBrandHeader)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Redeem Code", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (activity != null) {
                        TextButton(
                            onClick = { viewModel.openPlayOrderHistory(activity) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.proColors.onBrandHeader)
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Order History", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                TextButton(
                    onClick = { viewModel.refreshPlayPurchases(context, userInitiated = true) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore Purchases", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f))
                }

                if ((currentPlan == null || isSubscriptionExpired) && enabledPlans.isNotEmpty()) {
                    // Renewal re-subscribes to the same plan; otherwise offer the admin's top-priority plan.
                    val upsellPlan = if (isSubscriptionExpired) {
                        enabledPlans.firstOrNull { it.id == currentPlan?.id } ?: enabledPlans.firstOrNull()
                    } else {
                        enabledPlans.firstOrNull()
                    }
                    CustomButton(
                        text = if (isSubscriptionExpired) "Renew Subscription" else "Choose a Package",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            upsellPlan?.let { plan ->
                                val productId = plan.googlePlayProductId.ifBlank { plan.id }
                                if (currentUser?.isKycComplete == false) {
                                    pendingProductId = productId
                                    showKycDialog = true
                                } else if (activity != null) {
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

        // Section header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CHOOSE YOUR PLAN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (pricesLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // Show a warning when billing is connected but returned zero products — lets the
        // host distinguish "not yet loaded" from "connected but misconfigured in Play Console".
        if (billingConnected && !pricesLoading && playBillingProducts.isEmpty() && enabledPlans.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Plans couldn't be loaded from Google Play.",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        "Make sure you're using the Play Store version of the app, then tap Retry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    OutlinedButton(
                        onClick = { viewModel.retryBillingQuery(context) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (enabledPlans.isEmpty()) {
            Text(
                "No packages available right now — check back soon.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            // Vertical list — all plans visible without horizontal scroll.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                enabledPlans.forEach { plan ->
                    val playProductId = plan.googlePlayProductId.ifBlank { plan.id }
                    val isCurrent = currentPlan?.id == plan.id && !isSubscriptionExpired
                    CompactPlanCard(
                        plan = plan,
                        isCurrent = isCurrent,
                        playProduct = playProductMap[playProductId],
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Same KYC gate as the primary CTA above — this per-plan
                            // "Select" button used to skip it entirely and let an
                            // un-verified user reach Google Pay billing directly.
                            if (currentUser?.isKycComplete == false) {
                                pendingProductId = playProductId
                                showKycDialog = true
                            } else if (activity != null) {
                                viewModel.launchGooglePaySubscription(activity, playProductId)
                            } else {
                                Toast.makeText(context, "Cannot launch Google Play on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            Text(
                "Subscriptions renew automatically at the price and period shown until you cancel. " +
                    "Cancel anytime in Google Play › Payments & subscriptions; you keep access until the end of the paid period.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
    }

    if (showKycDialog) {
        currentUser?.let { user ->
            com.example.ui.components.KycVerificationDialog(
                user = user,
                onDismiss = { showKycDialog = false },
                onKycCompleted = {
                    showKycDialog = false
                    pendingProductId?.let { pid ->
                        if (activity != null) {
                            viewModel.launchGooglePaySubscription(activity, pid)
                        }
                    }
                }
            )
        }
    }

    if (showRedeemDialog) {
        ProHostDialog(
            onDismissRequest = { showRedeemDialog = false; redeemCodeInput = "" },
            icon = { Icon(Icons.Default.CardGiftcard, contentDescription = null) },
            title = { Text("Redeem a Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your promo or gift code and we'll open Google Play with it pre-filled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = redeemCodeInput,
                        onValueChange = { redeemCodeInput = it.uppercase().trim() },
                        label = { Text("Promo code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = redeemCodeInput.trim()
                        showRedeemDialog = false
                        redeemCodeInput = ""
                        if (activity != null) {
                            viewModel.openRedeemPromoCode(activity, code.ifBlank { null })
                        }
                    }
                ) { Text(if (redeemCodeInput.isBlank()) "Open Play Store" else "Redeem") }
            },
            dismissButton = {
                TextButton(onClick = { showRedeemDialog = false; redeemCodeInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactPlanCard(
    plan: PackagePlan,
    isCurrent: Boolean,
    playProduct: com.android.billingclient.api.ProductDetails? = null,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val isFeatured = plan.isFeatured
    val trialLabel = PlayOfferText.trialLabel(playProduct)
    val introLabel = PlayOfferText.introLabel(playProduct)
    val periodLabel = PlayOfferText.billingPeriodLabel(playProduct)
    val priceText = PackagePlan.displayPrice(plan, PlayOfferText.recurringPrice(playProduct))

    Box(modifier = modifier) {
        Card(
            onClick = onSelect,
            enabled = !isCurrent,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCurrent -> MaterialTheme.proColors.brandHeaderStart
                    isFeatured -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            border = when {
                isCurrent -> null
                isFeatured -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            elevation = CardDefaults.cardElevation(if (isFeatured || isCurrent) 8.dp else 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Trial / offer badge row — shown at top when available
                val offerBadge = trialLabel ?: introLabel
                if (offerBadge != null && !isCurrent) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = offerBadge.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Plan name + active check
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = plan.badgeName.ifBlank { plan.name },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.proColors.onBrandHeader else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCurrent) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Active",
                            tint = MaterialTheme.proColors.success,
                            modifier = Modifier.size(18.dp).padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Price — large
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isCurrent) MaterialTheme.proColors.onBrandHeader else MaterialTheme.colorScheme.secondary
                )
                // Billing period sub-label
                if (periodLabel != null) {
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(y = (-4).dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = if (isCurrent) MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                // Feature rows — all benefits are unlimited; the value is in the plan period
                @Composable
                fun FeatureRow(text: String) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.CheckCircle, contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.proColors.headerSuccess else MaterialTheme.proColors.success,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) MaterialTheme.proColors.onBrandHeader else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                FeatureRow("Unlimited listings")
                Spacer(modifier = Modifier.height(4.dp))
                FeatureRow("Unlimited bookings")

                if (plan.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = plan.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // CTA button
                Button(
                    onClick = onSelect,
                    enabled = !isCurrent,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFeatured && !isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isFeatured && !isCurrent) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.14f)
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        if (isCurrent) Icons.Default.Verified else Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCurrent) "Active Plan" else "Subscribe",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // "Secured by Google Play" label
                if (!isCurrent) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "via Google Play",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFeatured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        // "Most Popular" badge on featured plans
        if (isFeatured && !isCurrent) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp),
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "★ POPULAR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
