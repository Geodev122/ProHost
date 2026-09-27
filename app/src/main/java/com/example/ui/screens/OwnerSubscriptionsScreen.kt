package com.example.ui.screens

import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { viewModel.clearBillingMessages() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CoolGray, modifier = Modifier.size(16.dp))
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
                            Text(
                                when {
                                    isLifetimeGrant -> "Expires"
                                    !isAutoRenewing && remainingDays != null -> "Ends"
                                    else -> "Renews"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = LightGray
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
                                color = if (remainingDays != null || isLifetimeGrant) PureWhite else StatusError
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Listings", style = MaterialTheme.typography.bodySmall, color = LightGray)
                            Text(
                                "${ownerSpaces.size} · Unlimited",
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
                        currentPlan.isGrantOnly || isLifetimeGrant -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(14.dp))
                            Text(
                                "Complimentary access granted by ProHost",
                                style = MaterialTheme.typography.labelSmall,
                                color = FreshGreen
                            )
                        }
                        !isAutoRenewing && remainingDays != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(14.dp))
                            Text(
                                "Cancelled — access continues until $expiryDateString",
                                style = MaterialTheme.typography.labelSmall,
                                color = CarnationOrange
                            )
                        }
                        remainingDays != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(14.dp))
                            Text(
                                "Renews automatically via Google Play",
                                style = MaterialTheme.typography.labelSmall,
                                color = FreshGreen
                            )
                        }
                        else -> Unit
                    }
                }

                val activity = androidx.activity.compose.LocalActivity.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentPlan != null && !currentPlan.isGrantOnly && !isSubscriptionExpired && activity != null) {
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
                            onClick = { showRedeemDialog = true },
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

                    if (activity != null) {
                        OutlinedButton(
                            onClick = { viewModel.openPlayOrderHistory(activity) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                            border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Order History", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                TextButton(
                    onClick = { viewModel.refreshPlayPurchases(context) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = LightGray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore Purchases", style = MaterialTheme.typography.labelSmall, color = LightGray)
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
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CarnationOrange)
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
            // Featured plans first, then admin priority (PackagePlanCatalog.purchasablePlans).
            val sortedPlans = enabledPlans
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(sortedPlans) { plan ->
                    val playProductId = plan.googlePlayProductId.ifBlank { plan.id }
                    val isCurrent = currentPlan?.id == plan.id && !isSubscriptionExpired
                    CompactPlanCard(
                        plan = plan,
                        isCurrent = isCurrent,
                        playProduct = playProductMap[playProductId],
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
        AlertDialog(
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

@Composable
fun CompactPlanCard(
    plan: PackagePlan,
    isCurrent: Boolean,
    playProduct: com.android.billingclient.api.ProductDetails? = null,
    onSelect: () -> Unit
) {
    val cardWidth = 200.dp
    val isFeatured = plan.isFeatured

    Box(modifier = Modifier.width(cardWidth)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCurrent -> OxfordBlue
                    isFeatured -> OxfordBlue.copy(alpha = 0.06f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            border = when {
                isCurrent -> null
                isFeatured -> BorderStroke(2.dp, CarnationOrange)
                else -> BorderStroke(1.dp, LightGray.copy(alpha = 0.4f))
            },
            elevation = CardDefaults.cardElevation(if (isFeatured || isCurrent) 6.dp else 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Plan name + featured badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = plan.badgeName.ifBlank { plan.name },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) PureWhite else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (isCurrent) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(18.dp))
                    }
                }

                // Price — large and bold
                Text(
                    text = PackagePlan.displayPrice(plan, PlayOfferText.recurringPrice(playProduct)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isCurrent) PureWhite else CarnationOrange
                )
                Text(
                    text = PlayOfferText.caption(playProduct).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(y = (-6).dp)
                )

                HorizontalDivider(color = if (isCurrent) PureWhite.copy(alpha = 0.2f) else LightGray.copy(alpha = 0.5f))

                // Listings row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isCurrent) FreshGreen else FreshGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Unlimited listings",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) PureWhite else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (plan.description.isNotBlank()) {
                    Text(
                        text = plan.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // CTA button
                Button(
                    onClick = onSelect,
                    enabled = !isCurrent,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFeatured && !isCurrent) CarnationOrange else OxfordBlue,
                        disabledContainerColor = FreshGreen.copy(alpha = 0.18f)
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
                        text = if (isCurrent) "Active" else "Select",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) FreshGreen else PureWhite
                    )
                }
            }
        }

        // "Most Popular" badge on featured plans
        if (isFeatured && !isCurrent) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp),
                color = CarnationOrange,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "★ POPULAR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
