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
import com.example.data.billing.PlayCatalog
import com.example.data.billing.PlayOfferText
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
import com.example.ui.components.ProHostDialog
import com.example.ui.components.ProHostAlertBanner
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.ProHostAlertSeverity
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
    var pendingBasePlanId by remember { mutableStateOf<String?>(null) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var showLegalDocuments by remember { mutableStateOf(false) }
    var redeemCodeInput by remember { mutableStateOf("") }
    // A code that arrived by promo link opens the Redeem dialog pre-filled.
    val linkedPromoCode by com.example.data.billing.PendingPromoCode.code.collectAsState()
    LaunchedEffect(linkedPromoCode) {
        com.example.data.billing.PendingPromoCode.consume()?.let { code ->
            redeemCodeInput = code
            showRedeemDialog = true
        }
    }
    val currentUser by viewModel.currentUser.collectAsState()
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val pendingAutoPublishDraftId by viewModel.pendingAutoPublishDraftId.collectAsState()
    val billingActivationPending by viewModel.billingActivationPending.collectAsState()
    // A plan that activated without the pre-purchase KYC step (promo redemption, restore)
    // asks for the missing details right away; nothing is purchased after it.
    val kycPromptAfterActivation by viewModel.kycPromptAfterActivation.collectAsState()
    LaunchedEffect(kycPromptAfterActivation) {
        if (kycPromptAfterActivation) {
            viewModel.consumeKycPromptAfterActivation()
            pendingBasePlanId = null
            showKycDialog = true
        }
    }
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
                // Fresh ProductDetails each visit: stale ones can make launchBillingFlow fail.
                viewModel.retryBillingQuery(context)
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

    // Google Play is the only catalog: ProHost Premium (package_pro_mrr) and the base plans
    // Play returns for it (monthly / yearly). Nothing here comes from Firestore or an admin.
    val premiumProduct = remember(playBillingProducts) {
        playBillingProducts.firstOrNull { it.productId == PlayCatalog.PRODUCT_ID }
    }
    val enabledPlans = remember(premiumProduct) { PlayOfferText.basePlans(premiumProduct) }
    val yearlySavings = remember(premiumProduct) { PlayOfferText.yearlySavingsPercent(premiumProduct) }
    val currentPlanId = currentUser?.ownerPackageId
    val isForcedUpgrade = PlayCatalog.isForcedUpgrade(currentPlanId)

    val pricesLoading = !billingConnected && premiumProduct == null
    // One premium_page_viewed per visit, once Play has had a moment to answer.
    val latestPlanCount by rememberUpdatedState(enabledPlans.size)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3_000L)
        com.example.analytics.AnalyticsTracker.premiumPageViewed("subscriptions", latestPlanCount)
    }
    LaunchedEffect(enabledPlans) {
        enabledPlans.forEach { bp ->
            com.example.analytics.AnalyticsTracker.premiumPlanViewed(PlayCatalog.planInterval(bp) ?: bp)
        }
    }

    val expiryMillis = currentUser?.ownerPackageExpiryMillis
    val now = System.currentTimeMillis()
    val remainingDays = if (expiryMillis != null && expiryMillis > now) {
        ((expiryMillis - now) / (1000L * 60 * 60 * 24)).toInt()
    } else {
        null
    }
    val isSubscriptionExpired = currentPlanId != null && remainingDays == null
    val isLifetimeGrant = PlayCatalog.isLifetimeExpiry(expiryMillis)
    val expiryDateString = remember(expiryMillis, isLifetimeGrant) {
        if (expiryMillis != null && !isLifetimeGrant) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(expiryMillis))
        } else null
    }
    // Find the Play purchase for the current plan so we can read isAutoRenewing.
    val currentPlayPurchase = remember(playActivePurchases) {
        playActivePurchases.firstOrNull { p -> p.products.contains(PlayCatalog.PRODUCT_ID) }
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
            ProHostAlertBanner(
                message = "You have a saved Draft waiting on a subscription. Subscribe below and it will publish automatically.",
                severity = ProHostAlertSeverity.INFO
            )
        }

        if (billingActivationPending) {
            ProHostAlertBanner(
                title = "Activating your subscription",
                message = "This usually takes a few seconds. Taking too long? Contact support via WhatsApp.",
                severity = ProHostAlertSeverity.SUCCESS,
                icon = Icons.Default.HourglassTop,
                action = { BannerDismiss { viewModel.dismissBillingActivationPending() } }
            )
        }

        // Play's lifecycle states (grace period, account hold, paused, canceled, pending),
        // each with the action Google recommends — fixing payment or resubscribing happens in
        // Google Play's subscription center, deep-linked to this subscription.
        SubscriptionStatusBanner(
            billingStatus = currentUser?.billingStatus,
            expiryDate = expiryDateString,
            onOpenPlay = activity?.let { act -> { viewModel.openManageSubscriptions(act, PlayCatalog.PRODUCT_ID) } }
        )

        billingSuccess?.let { msg ->
            ProHostAlertBanner(
                message = msg,
                severity = ProHostAlertSeverity.SUCCESS,
                action = { BannerDismiss { viewModel.clearBillingMessages() } }
            )
        }

        billingError?.let { err ->
            ProHostAlertBanner(
                message = err,
                severity = ProHostAlertSeverity.ERROR,
                action = { BannerDismiss { viewModel.clearBillingMessages() } }
            )
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
                            text = if (currentPlanId != null) PlayCatalog.planBadge(currentPlanId) else "ProHost Premium",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                    if (currentPlanId != null) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.proColors.headerSuccess, modifier = Modifier.size(24.dp))
                    }
                }

                Text(
                    if (currentPlanId != null) PlayCatalog.planLabel(currentPlanId) else "Become a Pro Host",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.proColors.onBrandHeader
                )
                val currentPrice = currentPlanId?.takeIf { it in PlayCatalog.BASE_PLANS }
                    ?.let { PlayOfferText.describe(premiumProduct, it) }
                Text(
                    when {
                        currentPrice != null -> "$currentPrice · unlimited workspace listings"
                        currentPlanId != null -> "Unlimited workspace listings and booking requests."
                        else -> "Subscribe to ProHost Premium to publish unlimited workspace listings."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f)
                )

                HorizontalDivider(color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.3f))

                if (currentPlanId != null) {
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
                        isForcedUpgrade || isLifetimeGrant -> Row(
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
                    if ((currentPlayPurchase != null || currentPlanId in PlayCatalog.BASE_PLANS) && activity != null) {
                        TextButton(
                            onClick = { viewModel.openManageSubscriptions(activity, PlayCatalog.PRODUCT_ID) },
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

                // New members subscribe from the plan cards below (each shows its own price and
                // terms); this shortcut is only for renewing the plan that expired, with its
                // terms right under the button (Play policy: terms next to every purchase button).
                if (isSubscriptionExpired && enabledPlans.isNotEmpty()) {
                    val upsellPlan = currentPlanId?.takeIf { it in enabledPlans } ?: enabledPlans.lastOrNull()
                    CustomButton(
                        text = "Renew Subscription",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            upsellPlan?.let { plan ->
                                if (currentUser?.canHost(com.example.data.auth.PhoneLink.isLinked()) == false) {
                                    pendingBasePlanId = plan
                                    showKycDialog = true
                                } else if (activity != null) {
                                    viewModel.launchGooglePaySubscription(activity, plan)
                                } else {
                                    Toast.makeText(context, "Cannot launch Google Play on this device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.AddCircle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    upsellPlan?.let { plan ->
                        PlayOfferText.describe(premiumProduct, plan)?.let { terms ->
                            Text(
                                text = "$terms · renews automatically until you cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.proColors.onBrandHeader.copy(alpha = 0.8f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
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
        if (billingConnected && !pricesLoading && premiumProduct == null) {
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

        if (enabledPlans.isEmpty() && pricesLoading) {
            // Plan-card skeletons while Google Play answers the product query.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { ShimmerLoadingCard(height = 96.dp) }
            }
        } else if (enabledPlans.isEmpty()) {
            Text(
                if (pricesLoading) "Loading ProHost Premium from Google Play…" else "ProHost Premium plans will appear here once Google Play responds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            // Vertical list — all plans visible without horizontal scroll.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val hasLivePlaySubscription = currentPlanId in PlayCatalog.BASE_PLANS && !isSubscriptionExpired
                enabledPlans.forEach { plan ->
                    val isCurrent = currentPlanId == plan && !isSubscriptionExpired
                    // Play: make the current plan and the options to change it obvious. Upgrades
                    // (to yearly) apply now with credit for unused time; downgrades start at renewal.
                    val isUpgrade = plan == PlayCatalog.BASE_PLAN_YEARLY
                    val actionLabel = when {
                        isForcedUpgrade -> "Included in your access"
                        hasLivePlaySubscription && !isCurrent -> "Switch to ${PlayCatalog.planBadge(plan)}"
                        else -> "Subscribe"
                    }
                    val switchNote = if (hasLivePlaySubscription && !isCurrent) {
                        if (isUpgrade) "Starts now — Google Play credits the unused part of your current plan."
                        else "Starts on your renewal date${expiryDateString?.let { " ($it)" }.orEmpty()}; your current plan runs until then."
                    } else null
                    CompactPlanCard(
                        basePlanId = plan,
                        isCurrent = isCurrent,
                        playProduct = premiumProduct,
                        savingsPercent = if (plan == PlayCatalog.BASE_PLAN_YEARLY) yearlySavings else null,
                        actionLabel = actionLabel,
                        actionEnabled = !isForcedUpgrade,
                        footnote = switchNote,
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Same KYC gate as the primary CTA above — this per-plan
                            // "Select" button used to skip it entirely and let an
                            // un-verified user reach Google Pay billing directly.
                            if (currentUser?.canHost(com.example.data.auth.PhoneLink.isLinked()) == false) {
                                pendingBasePlanId = plan
                                showKycDialog = true
                            } else if (activity != null) {
                                viewModel.launchGooglePaySubscription(activity, plan)
                            } else {
                                Toast.makeText(context, "Cannot launch Google Play on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            // Play subscriptions policy: billing frequency, auto-renewal, how to cancel and
            // whether a subscription is required — visible without any extra tap.
            SubscriptionTermsFooter(
                onManage = activity?.let { act -> { viewModel.openManageSubscriptions(act, PlayCatalog.PRODUCT_ID) } },
                onOpenLegal = { showLegalDocuments = true }
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
    }

    if (showKycDialog) {
        currentUser?.let { user ->
            // Same sheet as booking (photo + verified phone) plus the hosting address step.
            com.example.ui.components.RequirementsSheet(
                user = user,
                viewModel = viewModel,
                requireAddress = true,
                onDismiss = { showKycDialog = false },
                onReady = {
                    showKycDialog = false
                    pendingBasePlanId?.let { pid ->
                        if (activity != null) {
                            viewModel.launchGooglePaySubscription(activity, pid)
                        }
                    }
                }
            )
        }
    }

    if (showLegalDocuments) {
        com.example.ui.components.LegalDocumentsMenu(onDismiss = { showLegalDocuments = false })
    }

    if (showRedeemDialog) {
        ProHostDialog(
            onDismissRequest = { showRedeemDialog = false; redeemCodeInput = "" },
            icon = { Icon(Icons.Default.CardGiftcard, contentDescription = null) },
            title = { Text("Redeem a Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your promo or gift code and we'll open Google Play with it pre-filled. " +
                            "Redeem it with the same Google account as this phone — when you come back, " +
                            "your Pro Host plan activates on this ProHost account automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = redeemCodeInput,
                        onValueChange = { redeemCodeInput = com.example.data.billing.PendingPromoCode.sanitize(it).orEmpty() },
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
    basePlanId: String,
    isCurrent: Boolean,
    playProduct: com.android.billingclient.api.ProductDetails? = null,
    savingsPercent: Int? = null,
    actionLabel: String = "Subscribe",
    actionEnabled: Boolean = true,
    footnote: String? = null,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    // The yearly plan is the highlighted one ("Save XX%" against twelve monthly payments).
    val isFeatured = basePlanId == PlayCatalog.BASE_PLAN_YEARLY
    val trialLabel = PlayOfferText.trialLabel(playProduct, basePlanId)
    val introLabel = PlayOfferText.introLabel(playProduct, basePlanId)
    // Full terms when there's a trial/intro ("Free for 1 month, then $4.99 / month"), else "per month".
    val periodLabel = PlayOfferText.caption(playProduct, basePlanId)
    val priceText = PlayOfferText.recurringPrice(playProduct, basePlanId) ?: "—"
    val planName = when (basePlanId) {
        PlayCatalog.BASE_PLAN_MONTHLY -> "Monthly"
        PlayCatalog.BASE_PLAN_YEARLY -> "Yearly"
        else -> basePlanId
    }

    Box(modifier = modifier) {
        Card(
            onClick = onSelect,
            enabled = !isCurrent && actionEnabled,
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
                        text = planName,
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

                // Free-trial terms Play asks apps to state: no charge during the trial, how to
                // avoid being charged, and the temporary payment-method check.
                if (trialLabel != null && !isCurrent && actionEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No charge during the free trial. Cancel in Google Play before it ends and you won't be charged. " +
                            "Google Play may place a temporary verification hold on your payment method.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                Spacer(modifier = Modifier.height(12.dp))

                // CTA button
                Button(
                    onClick = onSelect,
                    enabled = !isCurrent && actionEnabled,
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
                        text = if (isCurrent) "Current plan" else actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                footnote?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // "Secured by Google Play" label
                if (!isCurrent && actionEnabled) {
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

        // "Save XX%" on the yearly plan, computed from Google Play's own prices.
        if (isFeatured && !isCurrent && savingsPercent != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp),
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "SAVE $savingsPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * One banner per Google Play lifecycle state, with the action Play recommends. Built on the
 * shared [com.example.ui.components.ProHostAlertBanner].
 */
@Composable
private fun SubscriptionStatusBanner(
    billingStatus: String?,
    expiryDate: String?,
    onOpenPlay: (() -> Unit)?
) {
    data class StatusCopy(
        val severity: com.example.ui.components.ProHostAlertSeverity,
        val title: String,
        val message: String,
        val action: String?
    )
    val copy = when (billingStatus) {
        "GRACE_PERIOD" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.WARNING,
            "Payment problem",
            "Google Play couldn't renew your subscription. You still have Pro Host access for now — update your payment method to keep it.",
            "Fix payment"
        )
        "ON_HOLD" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.ERROR,
            "Subscription on hold",
            "Your payment didn't go through, so Pro Host access is paused and your listings are hidden. Fix your payment method in Google Play to restore them.",
            "Fix payment"
        )
        "PAUSED" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.INFO,
            "Subscription paused",
            "Pro Host access and your listings return when the subscription resumes. You can resume it now in Google Play.",
            "Resume"
        )
        "CANCELED" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.WARNING,
            "Subscription canceled",
            "It won't renew. You keep Pro Host access${expiryDate?.let { " until $it" } ?: " until the end of the paid period"}. Changed your mind? Resubscribe in Google Play.",
            "Resubscribe"
        )
        "PENDING" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.INFO,
            "Payment processing",
            "Google Play is still processing your payment. Pro Host access starts as soon as it completes — no need to pay again.",
            null
        )
        else -> null
    } ?: return

    val actionLabel = copy.action
    val actionContent: (@Composable () -> Unit)? = if (actionLabel != null && onOpenPlay != null) {
        { TextButton(onClick = onOpenPlay) { Text(actionLabel, fontWeight = FontWeight.Bold) } }
    } else null
    com.example.ui.components.ProHostAlertBanner(
        message = copy.message,
        title = copy.title,
        severity = copy.severity,
        action = actionContent
    )
}

/** Policy disclosures under the plans: renewal, cancellation, what needs a subscription, terms. */
@Composable
private fun SubscriptionTermsFooter(
    onManage: (() -> Unit)?,
    onOpenLegal: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Browsing and booking workspaces is free. ProHost Premium is needed only to publish listings.",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Payment is charged to your Google Play account. Subscriptions renew automatically at the price and " +
                "period shown until you cancel. Cancel anytime in Google Play › Payments & subscriptions; you keep " +
                "access until the end of the paid period.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onManage != null) {
                TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Manage subscription", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = onOpenLegal, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Terms & Privacy", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Close button for a dismissible [ProHostAlertBanner]; inherits the banner's content color. */
@Composable
private fun BannerDismiss(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
    }
}
