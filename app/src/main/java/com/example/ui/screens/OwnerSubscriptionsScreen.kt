package com.example.ui.screens

import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.sp
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
    val billingActivationPending by viewModel.billing.billingActivationPending.collectAsState()
    // A plan that activated without the pre-purchase KYC step (promo redemption, restore)
    // asks for the missing details right away; nothing is purchased after it.
    val kycPromptAfterActivation by viewModel.billing.kycPromptAfterActivation.collectAsState()
    LaunchedEffect(kycPromptAfterActivation) {
        if (kycPromptAfterActivation) {
            viewModel.billing.consumeKycPromptAfterActivation()
            pendingBasePlanId = null
            showKycDialog = true
        }
    }
    val billingError by viewModel.billing.billingError.collectAsState()
    val billingSuccess by viewModel.billing.billingSuccess.collectAsState()
    val playBillingProducts by viewModel.billing.playBillingProducts.collectAsState()
    val billingConnected by viewModel.billing.playBillingConnected.collectAsState()
    val playActivePurchases by viewModel.billing.playActivePurchases.collectAsState()

    // Refresh purchases every time the screen resumes (e.g. returning from Play Store).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.billing.refreshPlayPurchases(context)
                // Fresh ProductDetails each visit: stale ones can make launchBillingFlow fail.
                viewModel.billing.retryBillingQuery(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show Play's native in-app subscription messages on screen entry (grace period, hold, etc.).
    LaunchedEffect(Unit) {
        activity?.let { viewModel.billing.showBillingInAppMessages(it) }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (pendingAutoPublishDraftId != null) {
            item {
                ProHostAlertBanner(
                    message = "You have a saved Draft waiting on a subscription. Subscribe below and it will publish automatically.",
                    severity = ProHostAlertSeverity.INFO
                )
            }
        }
        if (billingActivationPending) {
            item {
                ProHostAlertBanner(
                    title = "Activating your subscription",
                    message = "This usually takes a few seconds. Taking too long? Contact support via WhatsApp.",
                    severity = ProHostAlertSeverity.SUCCESS,
                    icon = Icons.Default.HourglassTop,
                    action = { BannerDismiss { viewModel.billing.dismissBillingActivationPending() } }
                )
            }
        }
        // Only the lifecycle states SubscriptionStatusBanner renders get a row (no empty gap).
        if (currentUser?.billingStatus in setOf("GRACE_PERIOD", "ON_HOLD", "PAUSED", "CANCELED", "PENDING")) {
            item {
                // Play's lifecycle states (grace period, account hold, paused, canceled, pending),
                // each with the action Google recommends — fixing payment or resubscribing happens in
                // Google Play's subscription center, deep-linked to this subscription.
                SubscriptionStatusBanner(
                    billingStatus = currentUser?.billingStatus,
                    expiryDate = expiryDateString,
                    onOpenPlay = activity?.let { act -> { viewModel.billing.openManageSubscriptions(act, PlayCatalog.PRODUCT_ID) } }
                )

            }
        }
        billingSuccess?.let { msg ->
            item {
                ProHostAlertBanner(
                    message = msg,
                    severity = ProHostAlertSeverity.SUCCESS,
                    action = { BannerDismiss { viewModel.billing.clearBillingMessages() } }
                )
            }
        }
        billingError?.let { err ->
            item {
                ProHostAlertBanner(
                    message = err,
                    severity = ProHostAlertSeverity.ERROR,
                    action = { BannerDismiss { viewModel.billing.clearBillingMessages() } }
                )
            }
        }
        item {
            PremiumHero(
                currentPlanId = currentPlanId,
                playDescription = premiumProduct?.description?.trim()?.takeIf { it.isNotEmpty() },
                currentPrice = currentPlanId?.takeIf { it in PlayCatalog.BASE_PLANS }
                    ?.let { PlayOfferText.describe(premiumProduct, it) },
                renewLabel = when {
                    isLifetimeGrant -> "Expires"
                    !isAutoRenewing && remainingDays != null -> "Ends"
                    else -> "Renews"
                },
                renewValue = when {
                    isLifetimeGrant -> "Never"
                    remainingDays == null -> "Expired"
                    remainingDays == 0 -> "< 1 day"
                    expiryDateString != null -> expiryDateString
                    else -> "$remainingDays days"
                },
                renewOk = remainingDays != null || isLifetimeGrant,
                listingCount = ownerSpaces.size,
                statusLine = when {
                    currentPlanId == null -> null
                    isSubscriptionExpired -> HeroStatus(Icons.Default.Warning, "Expired — your listings are hidden until you renew", HeroTone.ERROR)
                    isForcedUpgrade || isLifetimeGrant -> HeroStatus(Icons.Default.CardGiftcard, "Complimentary access granted by ProHost", HeroTone.SUCCESS)
                    !isAutoRenewing && remainingDays != null -> HeroStatus(Icons.Default.EventBusy, "Cancelled — access continues until $expiryDateString", HeroTone.WARNING)
                    remainingDays != null -> HeroStatus(Icons.Default.Autorenew, "Renews automatically via Google Play", HeroTone.SUCCESS)
                    else -> null
                }
            )
        }
        item {
            // One row of quick actions (was four stacked buttons inside the hero).
            val act = activity
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (act != null) {
                    PremiumActionChip(Icons.Default.Settings, "Manage") {
                        viewModel.billing.openManageSubscriptions(act, PlayCatalog.PRODUCT_ID)
                    }
                    PremiumActionChip(Icons.Default.CardGiftcard, "Redeem code") { showRedeemDialog = true }
                    PremiumActionChip(Icons.Default.Receipt, "Orders") { viewModel.billing.openPlayOrderHistory(act) }
                }
                PremiumActionChip(Icons.Default.Sync, "Restore") {
                    viewModel.billing.refreshPlayPurchases(context, userInitiated = true)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (currentPlanId != null && !isSubscriptionExpired) "YOUR PLAN" else "CHOOSE YOUR PLAN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pricesLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        // Billing connected but Play returned no product: Play Console / install problem, not loading.
        if (billingConnected && !pricesLoading && premiumProduct == null) {
            item {
                ProHostAlertBanner(
                    title = "Plans couldn't be loaded from Google Play",
                    message = "Make sure you're using the Play Store version of the app, then tap Retry.",
                    severity = ProHostAlertSeverity.ERROR,
                    action = {
                        TextButton(onClick = { viewModel.billing.retryBillingQuery(context) }) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
        item {
            if (enabledPlans.isEmpty() && pricesLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { ShimmerLoadingCard(height = 132.dp, modifier = Modifier.weight(1f)) }
                }
            } else if (enabledPlans.isEmpty()) {
                Text(
                    "ProHost Premium plans will appear here once Google Play responds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                val hasLivePlaySubscription = currentPlanId in PlayCatalog.BASE_PLANS && !isSubscriptionExpired
                // Selected tile: the yearly plan by default (best value), else the first Play returned.
                var selectedPlan by rememberSaveable(enabledPlans) {
                    mutableStateOf(
                        enabledPlans.firstOrNull { it == PlayCatalog.BASE_PLAN_YEARLY && it != currentPlanId }
                            ?: enabledPlans.firstOrNull { it != currentPlanId || isSubscriptionExpired }
                            ?: enabledPlans.first()
                    )
                }
                // Tiles always show both base plans; one Play didn't return is a clear placeholder.
                val tiles = (PlayCatalog.BASE_PLANS + enabledPlans).distinct()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        tiles.forEach { plan ->
                            if (plan in enabledPlans) {
                                PlanTile(
                                    basePlanId = plan,
                                    playProduct = premiumProduct,
                                    selected = plan == selectedPlan,
                                    isCurrent = currentPlanId == plan && !isSubscriptionExpired,
                                    savingsPercent = if (plan == PlayCatalog.BASE_PLAN_YEARLY) yearlySavings else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = {
                                        selectedPlan = plan
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                )
                            } else {
                                UnavailablePlanTile(
                                    basePlanId = plan,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }

                    PremiumBenefits()

                    val isCurrent = currentPlanId == selectedPlan && !isSubscriptionExpired
                    // Play: make the current plan and the options to change it obvious. Upgrades
                    // (to yearly) apply now with credit for unused time; downgrades start at renewal.
                    val isUpgrade = selectedPlan == PlayCatalog.BASE_PLAN_YEARLY
                    val actionLabel = when {
                        isForcedUpgrade -> "Included in your access"
                        isCurrent -> "Your current plan"
                        hasLivePlaySubscription -> "Switch to ${PlayCatalog.planBadge(selectedPlan)}"
                        isSubscriptionExpired -> "Renew · ${PlayCatalog.planBadge(selectedPlan)}"
                        PlayOfferText.trialLabel(premiumProduct, selectedPlan) != null -> "Start free trial"
                        else -> "Subscribe · ${PlayCatalog.planBadge(selectedPlan)}"
                    }
                    val enabled = !isForcedUpgrade && !isCurrent
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // KYC gate before Google Play (photo, verified phone, address).
                            if (currentUser?.canHost(com.example.data.auth.PhoneLink.isLinked()) == false) {
                                pendingBasePlanId = selectedPlan
                                showKycDialog = true
                            } else if (activity != null) {
                                viewModel.billing.launchGooglePaySubscription(activity, selectedPlan)
                            } else {
                                Toast.makeText(context, "Cannot launch Google Play on this device", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(if (enabled) Icons.Default.WorkspacePremium else Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(actionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    // Terms right under the purchase button (Play policy), switch timing, trial terms.
                    val terms = PlayOfferText.describe(premiumProduct, selectedPlan)
                    val notes = buildList {
                        if (enabled && terms != null) add("$terms · renews automatically until you cancel · via Google Play")
                        if (enabled && hasLivePlaySubscription) add(
                            if (isUpgrade) "Starts now — Google Play credits the unused part of your current plan."
                            else "Starts on your renewal date${expiryDateString?.let { " ($it)" }.orEmpty()}; your current plan runs until then."
                        )
                        if (enabled && PlayOfferText.trialLabel(premiumProduct, selectedPlan) != null) add(
                            "No charge during the free trial. Cancel in Google Play before it ends and you won't be charged. " +
                                "Google Play may place a temporary verification hold on your payment method."
                        )
                    }
                    notes.forEach { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Play subscriptions policy: billing frequency, auto-renewal, how to cancel and
                    // whether a subscription is required — visible without any extra tap.
                    SubscriptionTermsFooter(onOpenLegal = { showLegalDocuments = true })
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
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
                            viewModel.billing.launchGooglePaySubscription(activity, pid)
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
                            viewModel.billing.openRedeemPromoCode(activity, code.ifBlank { null })
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

private enum class HeroTone { SUCCESS, WARNING, ERROR }

private data class HeroStatus(val icon: androidx.compose.ui.graphics.vector.ImageVector, val text: String, val tone: HeroTone)

/** Compact brand header: plan name, one line of value, and for members renewal + listings. */
@Composable
private fun PremiumHero(
    currentPlanId: String?,
    playDescription: String?,
    currentPrice: String?,
    renewLabel: String,
    renewValue: String,
    renewOk: Boolean,
    listingCount: Int,
    statusLine: HeroStatus?
) {
    val on = MaterialTheme.proColors.onBrandHeader
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(MaterialTheme.proColors.brandHeaderStart, MaterialTheme.proColors.brandHeaderEnd)
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = on.copy(alpha = 0.16f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (currentPlanId != null) PlayCatalog.planLabel(currentPlanId) else "ProHost Premium",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = on,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            currentPrice != null -> currentPrice
                            currentPlanId != null -> "Unlimited listings and booking requests"
                            else -> playDescription ?: "Publish your workspaces and take bookings"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = on.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (currentPlanId != null) {
                    Icon(Icons.Default.Verified, contentDescription = "Active", tint = MaterialTheme.proColors.headerSuccess, modifier = Modifier.size(22.dp))
                }
            }
            if (currentPlanId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat(renewLabel, renewValue, if (renewOk) on else MaterialTheme.proColors.headerError, Modifier.weight(1f))
                    HeroStat("Listings", "$listingCount · Unlimited", on, Modifier.weight(1f))
                }
            }
            statusLine?.let { st ->
                val tint = when (st.tone) {
                    HeroTone.SUCCESS -> MaterialTheme.proColors.headerSuccess
                    HeroTone.WARNING -> MaterialTheme.proColors.headerWarning
                    HeroTone.ERROR -> MaterialTheme.proColors.headerError
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(st.icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    Text(st.text, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    val on = MaterialTheme.proColors.onBrandHeader
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = on.copy(alpha = 0.12f)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.75f))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun PremiumActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        shape = RoundedCornerShape(50),
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    )
}

/** One selectable base-plan tile; every price and label comes from Google Play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanTile(
    basePlanId: String,
    playProduct: com.android.billingclient.api.ProductDetails?,
    selected: Boolean,
    isCurrent: Boolean,
    savingsPercent: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.secondary
    val trialLabel = PlayOfferText.trialLabel(playProduct, basePlanId) ?: PlayOfferText.introLabel(playProduct, basePlanId)
    val price = PlayOfferText.recurringPrice(playProduct, basePlanId) ?: "—"
    val period = when (basePlanId) {
        PlayCatalog.BASE_PLAN_YEARLY -> "per year"
        PlayCatalog.BASE_PLAN_MONTHLY -> "per month"
        else -> PlayOfferText.caption(playProduct, basePlanId).orEmpty()
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PlayCatalog.planBadge(basePlanId),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(price, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(period, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    isCurrent -> PlanChip("CURRENT", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    savingsPercent != null -> PlanChip("SAVE $savingsPercent%", accent, MaterialTheme.colorScheme.onSecondary)
                }
                if (trialLabel != null && !isCurrent) {
                    PlanChip(trialLabel.uppercase(), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

/** A base plan Google Play didn't return (not active, or not offered in this country/store). */
@Composable
private fun UnavailablePlanTile(basePlanId: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(PlayCatalog.planBadge(basePlanId), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Not available", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Google Play doesn't offer this plan in your country or store yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanChip(text: String, container: Color, content: Color) {
    Surface(color = container, shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** What Premium unlocks — compact checklist. Play's own product description, when set, is the hero's subtitle. */
@Composable
private fun PremiumBenefits() {
    val items = listOf(
        "Publish unlimited workspace listings",
        "Receive and manage booking requests",
        "Host dashboard, occupancy and analytics",
        "Shown on Explore's list and map"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.proColors.success, modifier = Modifier.size(16.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
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
        TextButton(onClick = onOpenLegal, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Terms & Privacy", style = MaterialTheme.typography.labelMedium)
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
