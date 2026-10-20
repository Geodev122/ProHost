package com.example.ui.screens

import com.example.ui.util.localizedLabel
import com.example.ui.util.uiText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.Radius
import com.example.ui.theme.Elevation
import com.example.ui.util.UiMessages
import kotlinx.coroutines.flow.first
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.*
import com.example.data.billing.PlayCatalog
import com.example.data.billing.PlayOfferText
import com.example.ui.components.CustomButton
import com.example.ui.components.InputField
import com.example.ui.components.CustomButtonVariant
import com.example.ui.components.ProHostDialog
import com.example.ui.components.ProHostAlertBanner
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.ProHostAlertSeverity
import com.example.ui.components.PromoCountdownCard
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
    // Play promo offer (card in the Redeem sheet): the confirm step before checkout, and a checkout waiting on verification.
    var showPromoHowTo by remember { mutableStateOf(false) }
    var pendingPromoCheckout by remember { mutableStateOf(false) }
    // A code that arrived by promo link opens the Redeem sheet pre-filled.
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
    // The promo comes only from Play: the offer tagged `promo` that Play returned for this account (PlayPromo).
    val promoOffer = remember(playBillingProducts) {
        com.example.data.billing.PlayPromo.find(playBillingProducts.firstOrNull { it.productId == PlayCatalog.PRODUCT_ID })
    }
    // Ticks once a second only while the promo has a live countdown.
    var promoNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(promoOffer) {
        while (promoOffer?.endsAtMillis != null && promoOffer.isLive(System.currentTimeMillis())) {
            promoNow = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
        promoNow = System.currentTimeMillis()
    }

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
        billingError?.let { UiMessages.show(context, it, long = true) }
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
    // Monthly / yearly by billing period, whatever the ids are called in Play Console.
    val plansByKind = remember(premiumProduct) { PlayOfferText.plansByKind(premiumProduct) }
    val yearlySavings = remember(premiumProduct) { PlayOfferText.yearlySavingsPercent(premiumProduct) }
    // The offer each base plan's purchase sheet will use, as Play returned it for this account.
    val offerSummaries = remember(premiumProduct) {
        plansByKind.mapValues { (_, planId) -> PlayOfferText.summary(premiumProduct, planId) }
    }
    val currentPlanId = currentUser?.ownerPackageId
    val isForcedUpgrade = PlayCatalog.isForcedUpgrade(currentPlanId)

    val pricesLoading = !billingConnected && premiumProduct == null
    // One premium_page_viewed per visit: source = the screen the person came from (read before this page's own
    // screen_view), sent as soon as Play answers (or after 3 s with however many plans loaded).
    val premiumEntrySource = remember { com.example.analytics.AnalyticsTracker.currentScreen ?: "direct" }
    val latestPlanCount by rememberUpdatedState(enabledPlans.size)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withTimeoutOrNull(3_000L) {
            androidx.compose.runtime.snapshotFlow { latestPlanCount }.first { count -> count > 0 }
        }
        com.example.analytics.AnalyticsTracker.premiumPageViewed(premiumEntrySource, latestPlanCount)
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
    // Promo codes are for new subscribers: members with a live Play subscription see the card disabled.
    val isPlayMember = currentUser?.entitlementSource == "google_play" && !isForcedUpgrade && !isSubscriptionExpired
    val livePromo = promoOffer?.takeIf { it.isLive(promoNow) }
    val canUsePromo = livePromo != null && !isPlayMember && !isForcedUpgrade

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        if (pendingAutoPublishDraftId != null) {
            item {
                ProHostAlertBanner(
                    message = stringResource(R.string.you_have_a_saved_draft_waiting_on_a_subscr),
                    severity = ProHostAlertSeverity.INFO
                )
            }
        }
        if (billingActivationPending) {
            item {
                ProHostAlertBanner(
                    title = stringResource(R.string.activating_your_subscription),
                    message = stringResource(R.string.this_usually_takes_a_few_seconds_taking_to),
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
                currentPrice = currentPlanId?.takeIf { it in enabledPlans }
                    ?.let { PlayOfferText.describe(premiumProduct, it) },
                renewLabel = when {
                    isLifetimeGrant -> uiText(R.string.expires)
                    !isAutoRenewing && remainingDays != null -> uiText(R.string.ends)
                    else -> uiText(R.string.renews)
                },
                renewValue = when {
                    isLifetimeGrant -> uiText(R.string.never)
                    remainingDays == null -> uiText(R.string.expired)
                    remainingDays == 0 -> uiText(R.string.t_1_day)
                    expiryDateString != null -> expiryDateString
                    else -> "$remainingDays days"
                },
                renewOk = remainingDays != null || isLifetimeGrant,
                listingCount = ownerSpaces.size,
                statusLine = when {
                    currentPlanId == null -> null
                    isSubscriptionExpired -> HeroStatus(Icons.Default.Warning, uiText(R.string.expired_your_listings_are_hidden_until_you), HeroTone.ERROR)
                    isForcedUpgrade || isLifetimeGrant -> HeroStatus(Icons.Default.CardGiftcard, uiText(R.string.complimentary_access_granted_by_prohost), HeroTone.SUCCESS)
                    !isAutoRenewing && remainingDays != null -> HeroStatus(Icons.Default.EventBusy, uiText(R.string.cancelled_access_continues_until_x, expiryDateString), HeroTone.WARNING)
                    remainingDays != null -> HeroStatus(Icons.Default.Autorenew, uiText(R.string.renews_automatically_via_google_play), HeroTone.SUCCESS)
                    else -> null
                }
            )
        }
        item {
            // One row of quick actions (was four stacked buttons inside the hero).
            val act = activity
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (act != null) {
                    PremiumActionChip(Icons.Default.Settings, uiText(R.string.manage)) {
                        viewModel.billing.openManageSubscriptions(act, PlayCatalog.PRODUCT_ID)
                    }
                    PremiumActionChip(Icons.Default.CardGiftcard, uiText(R.string.redeem_code), dot = livePromo != null) { showRedeemDialog = true }
                    PremiumActionChip(Icons.Default.Receipt, uiText(R.string.orders)) { viewModel.billing.openPlayOrderHistory(act) }
                }
                PremiumActionChip(Icons.Default.Sync, uiText(R.string.restore)) {
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
                    text = if (currentPlanId != null && !isSubscriptionExpired) stringResource(R.string.your_plan) else stringResource(R.string.choose_your_plan),
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
                    title = stringResource(R.string.plans_couldn_t_be_loaded_from_google_play),
                    message = stringResource(R.string.make_sure_you_re_using_the_play_store_vers),
                    severity = ProHostAlertSeverity.ERROR,
                    action = {
                        TextButton(onClick = { viewModel.billing.retryBillingQuery(context) }) {
                            Text(stringResource(R.string.retry), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
        item {
            if (enabledPlans.isEmpty() && pricesLoading) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    repeat(2) { ShimmerLoadingCard(height = 132.dp, modifier = Modifier.weight(1f)) }
                }
            } else if (enabledPlans.isEmpty()) {
                Text(
                    stringResource(R.string.prohost_premium_plans_will_appear_here_onc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                val hasLivePlaySubscription = currentUser?.entitlementSource == "google_play" &&
                    !isForcedUpgrade && !isSubscriptionExpired
                val currentKind = currentPlanId?.let { PlayOfferText.kindOf(premiumProduct, it) }
                fun isCurrentPlan(id: String) = hasLivePlaySubscription &&
                    (id == currentPlanId || PlayOfferText.kindOf(premiumProduct, id) == currentKind)
                // Selected tile: the yearly plan by default (best value) unless it is the current one.
                var selectedPlan by rememberSaveable(plansByKind) {
                    mutableStateOf(
                        plansByKind[PlayCatalog.PlanKind.YEARLY]?.takeIf { !isCurrentPlan(it) }
                            ?: plansByKind.values.firstOrNull { !isCurrentPlan(it) }
                            ?: plansByKind.values.firstOrNull()
                            ?: enabledPlans.first()
                    )
                }
                val selectedKind = PlayOfferText.kindOf(premiumProduct, selectedPlan)
                val selectedBadge = selectedKind?.let(PlayCatalog::badge) ?: uiText(R.string.premium)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    // Offers show on the plan cards (new subscriptions only); members switching plans see regular prices.
                    // Always exactly two tiles: Monthly and Yearly. A kind Play didn't return is a placeholder.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        PlayCatalog.PlanKind.entries.forEach { kind ->
                            val plan = plansByKind[kind]
                            if (plan != null) {
                                PlanTile(
                                    kind = kind,
                                    summary = offerSummaries[kind],
                                    selected = plan == selectedPlan,
                                    isCurrent = isCurrentPlan(plan),
                                    showOffer = !hasLivePlaySubscription,
                                    savingsPercent = if (kind == PlayCatalog.PlanKind.YEARLY) yearlySavings else null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = {
                                        com.example.analytics.AnalyticsTracker.premiumPlanSelect(
                                            PlayCatalog.planInterval(plan) ?: plan,
                                            hasOffer = offerSummaries[kind]?.hasOffer == true && !hasLivePlaySubscription
                                        )
                                        selectedPlan = plan
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                )
                            } else {
                                UnavailablePlanTile(
                                    kind = kind,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }

                    PremiumBenefits()

                    val isCurrent = isCurrentPlan(selectedPlan)
                    val selectedSummary = selectedKind?.let { offerSummaries[it] }
                        ?.takeIf { it.hasOffer && !hasLivePlaySubscription && !isCurrent }
                    // Play: make the current plan and the options to change it obvious. Upgrades
                    // (to yearly) apply now with credit for unused time; downgrades start at renewal.
                    val isUpgrade = selectedKind == PlayCatalog.PlanKind.YEARLY
                    val actionLabel = when {
                        isForcedUpgrade -> uiText(R.string.included_in_your_access)
                        isCurrent -> uiText(R.string.your_current_plan)
                        hasLivePlaySubscription -> uiText(R.string.switch_to_x, selectedBadge)
                        isSubscriptionExpired -> uiText(R.string.renew_x, selectedBadge)
                        selectedSummary?.trialSpan != null && selectedSummary.introSpan == null -> uiText(R.string.start_free_trial_x, selectedBadge)
                        selectedSummary?.discountPercent != null -> uiText(R.string.get_x_off_x, selectedSummary.discountPercent, selectedBadge)
                        else -> uiText(R.string.subscribe_x, selectedBadge)
                    }
                    val enabled = !isForcedUpgrade && !isCurrent
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // KYC gate before Google Play (photo, verified phone, address).
                            if (currentUser?.canHost() == false) {
                                pendingBasePlanId = selectedPlan
                                showKycDialog = true
                            } else if (activity != null) {
                                viewModel.billing.launchGooglePaySubscription(activity, selectedPlan)
                            } else {
                                UiMessages.show(context, context.getString(R.string.cannot_launch_google_play_on_this_device))
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = Radius.Md,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(if (enabled) Icons.Default.WorkspacePremium else Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(actionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    // How it works for the selected plan (either tile): with an offer today's price, the date
                    // the regular price starts and how to cancel; without one the plain renewal terms.
                    if (enabled && !hasLivePlaySubscription) {
                        (selectedSummary ?: selectedKind?.let { offerSummaries[it] })?.let { PlanExplainer(it, hasOffer = selectedSummary != null) }
                    }
                    // Terms right under the purchase button (Play policy), switch timing, trial terms.
                    // A plan switch is charged the plain base plan (OfferChoice.BasePlanOnly), so its terms are too.
                    val terms = PlayOfferText.describe(premiumProduct, selectedPlan, basePlanOnly = hasLivePlaySubscription)
                    val notes = buildList {
                        if (enabled && terms != null) add(uiText(R.string.x_renews_automatically_until_you_cancel_vi, terms))
                        if (enabled && hasLivePlaySubscription) add(
                            if (isUpgrade) uiText(R.string.you_pay_the_yearly_price_now_and_your_year) +
                                uiText(R.string.your_current_month_are_added_to_it_by_goog)
                            else "Starts on your renewal date${expiryDateString?.let { " ($it)" }.orEmpty()}; your current plan runs until then."
                        )
                        if (enabled && !hasLivePlaySubscription && PlayOfferText.trialLabel(premiumProduct, selectedPlan) != null) add(
                            uiText(R.string.no_charge_during_the_free_trial_cancel_in) +
                                uiText(R.string.google_play_may_place_a_temporary_verifica)
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
                onDismiss = { showKycDialog = false; pendingPromoCheckout = false },
                onReady = {
                    showKycDialog = false
                    pendingBasePlanId?.let { pid ->
                        if (activity != null) {
                            if (pendingPromoCheckout && livePromo != null && livePromo.basePlanId == pid) {
                                viewModel.billing.launchPromoCheckout(activity, livePromo)
                            } else {
                                viewModel.billing.launchGooglePaySubscription(activity, pid)
                            }
                        }
                    }
                    pendingPromoCheckout = false
                }
            )
        }
    }

    if (showLegalDocuments) {
        com.example.ui.components.LegalDocumentsMenu(onDismiss = { showLegalDocuments = false })
    }

    if (showPromoHowTo && livePromo != null) {
        // The offer is applied by Play itself: checkout opens on the promo offer, nothing to type.
        // Play subscriptions policy: the date the paid price starts and how to avoid it, before purchase.
        val promoPaidFrom = remember(livePromo) {
            java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(Date(PlayOfferText.regularPriceStartsAt(System.currentTimeMillis(), livePromo.summary.introPeriods)))
        }
        ProHostDialog(
            onDismissRequest = { showPromoHowTo = false },
            icon = { Icon(Icons.Default.Redeem, contentDescription = null) },
            title = { Text(stringResource(R.string.use_x, livePromo.code)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(
                        "Google Play's checkout opens with the ${livePromo.code} offer: ${livePromo.benefit.replaceFirstChar { it.lowercase() }}.",
                        "A payment method is required. Google Play shows today's price and the date the regular price starts before you confirm.",
                        "From $promoPaidFrom it renews automatically at ${livePromo.summary.recurring} / ${livePromo.summary.recurringPeriod}. " +
                            "Cancel in Google Play › Payments & subscriptions before $promoPaidFrom and you won't be charged."
                    ).forEachIndexed { i, line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPromoHowTo = false
                    if (currentUser?.canHost() == false) {
                        pendingBasePlanId = livePromo.basePlanId
                        pendingPromoCheckout = true
                        showKycDialog = true
                    } else if (activity != null) {
                        viewModel.billing.launchPromoCheckout(activity, livePromo)
                    }
                }) { Text(stringResource(R.string.continue_to_google_play), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showPromoHowTo = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showRedeemDialog) {
        com.example.ui.components.TrackScreen("premium_redeem")
        // "Redeem code": the Play promo card (when Play returned one) and the one-time-code field.
        com.example.ui.components.ProHostBottomSheet(
            onDismissRequest = { showRedeemDialog = false; redeemCodeInput = "" },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (livePromo != null) {
                    PromoCountdownCard(
                        promo = livePromo,
                        nowMillis = promoNow,
                        enabled = canUsePromo,
                        onCopy = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ProHost promo code", livePromo.code))
                            UiMessages.show(context, context.getString(R.string.code_x_copied, livePromo.code))
                        },
                        onUse = { showRedeemDialog = false; redeemCodeInput = ""; showPromoHowTo = true }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (livePromo != null) stringResource(R.string.have_another_code) else stringResource(R.string.redeem_a_code),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    stringResource(R.string.enter_a_promo_or_gift_code_and_we_ll_open) +
                        stringResource(R.string.redeem_it_with_the_same_google_account_as) +
                        stringResource(R.string.your_pro_host_plan_activates_on_this_proho),
                    style = MaterialTheme.typography.bodySmall
                )
                InputField(
                    value = redeemCodeInput,
                    onValueChange = { redeemCodeInput = com.example.data.billing.PendingPromoCode.sanitize(it).orEmpty() },
                    label = stringResource(R.string.promo_code),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val code = redeemCodeInput.trim()
                        showRedeemDialog = false
                        redeemCodeInput = ""
                        if (activity != null) {
                            viewModel.billing.openRedeemPromoCode(activity, code.ifBlank { null })
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (redeemCodeInput.isBlank()) stringResource(R.string.open_google_play_redeem) else stringResource(R.string.redeem_on_google_play)) }
            }
        }
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
        shape = Radius.Md,
        color = Color.Transparent,
        shadowElevation = Elevation.level3
    ) {
        Column(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(MaterialTheme.proColors.brandHeaderStart, MaterialTheme.proColors.brandHeaderEnd)
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Surface(shape = CircleShape, color = on.copy(alpha = 0.16f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (currentPlanId != null) PlayCatalog.planLabel(currentPlanId) else stringResource(R.string.prohost_premium),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = on,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            currentPrice != null -> currentPrice
                            currentPlanId != null -> uiText(R.string.unlimited_listings_and_booking_requests)
                            else -> playDescription ?: uiText(R.string.publish_your_workspaces_and_take_bookings)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = on,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (currentPlanId != null) {
                    Icon(Icons.Default.Verified, contentDescription = stringResource(R.string.active), tint = MaterialTheme.proColors.headerSuccess, modifier = Modifier.size(22.dp))
                }
            }
            if (currentPlanId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HeroStat(renewLabel, renewValue, if (renewOk) on else MaterialTheme.proColors.headerError, Modifier.weight(1f))
                    HeroStat(uiText(R.string.listings), uiText(R.string.x_unlimited, listingCount), on, Modifier.weight(1f))
                }
            }
            statusLine?.let { st ->
                val tint = when (st.tone) {
                    HeroTone.SUCCESS -> MaterialTheme.proColors.headerSuccess
                    HeroTone.WARNING -> MaterialTheme.proColors.headerWarning
                    HeroTone.ERROR -> MaterialTheme.proColors.headerError
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
    Surface(modifier = modifier, shape = Radius.Sm, color = on.copy(alpha = 0.12f)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = on)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PremiumActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    dot: Boolean = false,
    onClick: () -> Unit
) {
    Box {
        AssistChip(
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50),
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        )
        // A live promo campaign: red dot on "Redeem code".
        if (dot) com.example.ui.components.ProHostDotBadge(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 2.dp))
    }
}

/**
 * One selectable base-plan card; every price and label comes from Google Play ([PlayOfferText.OfferSummary]).
 * With an offer it leads with the price paid now, the regular price struck through, the discount and
 * what it renews at; without one it shows the regular price (and ≈ per month for yearly).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanTile(
    kind: PlayCatalog.PlanKind,
    summary: PlayOfferText.OfferSummary?,
    selected: Boolean,
    isCurrent: Boolean,
    savingsPercent: Int?,
    modifier: Modifier = Modifier,
    showOffer: Boolean = true,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.secondary
    val shape = Radius.Md
    val hasOffer = summary?.hasOffer == true && !isCurrent && showOffer
    val fill = when {
        selected -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), scheme.surface))
        else -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(scheme.surface, scheme.surfaceVariant.copy(alpha = 0.55f)))
    }
    Surface(
        onClick = onClick,
        // Announced like a radio option: "Yearly, selected".
        modifier = modifier.semantics { this.selected = selected; role = androidx.compose.ui.semantics.Role.RadioButton },
        shape = shape,
        color = scheme.surface, // under the gradient; keeps the elevation shadow solid
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else scheme.outlineVariant),
        shadowElevation = if (selected) 10.dp else 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().background(fill)) {
            // Ribbon: the strongest reason to pick this card.
            val ribbon: Pair<String, Boolean>? = when {
                isCurrent -> uiText(R.string.ribbon_your_plan) to false
                hasOffer && summary?.discountPercent != null -> uiText(R.string.ribbon_x_off, summary.discountPercent) to true
                hasOffer && summary?.trialSpan != null -> "${summary.trialSpan.uppercase()} FREE" to true
                kind == PlayCatalog.PlanKind.YEARLY && savingsPercent != null -> uiText(R.string.ribbon_best_value) to true
                // Both cards always carry a ribbon so they line up and read as a pair.
                kind == PlayCatalog.PlanKind.MONTHLY -> uiText(R.string.ribbon_flexible) to false
                else -> null
            }
            // Only the selected card is orange; the other stays neutral (steel tint) so the screen isn't all orange.
            val ribbonOn = ribbon?.second == true && selected
            val ribbonContent = when {
                ribbon == null -> scheme.onSurface
                ribbonOn -> scheme.onSecondary
                ribbon.second -> scheme.primary
                else -> scheme.onPrimaryContainer
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .background(
                        when {
                            ribbon == null -> Color.Transparent
                            ribbonOn -> accent
                            else -> scheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (ribbon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(
                            if (ribbon.second) Icons.Default.LocalOffer else Icons.Default.Verified,
                            contentDescription = null,
                            tint = ribbonContent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            ribbon.first,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = ribbonContent
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        localizedLabel(PlayCatalog.badge(kind)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) stringResource(R.string.selected) else null,
                        tint = if (selected) accent else scheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (summary == null) {
                    Text("—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                } else if (hasOffer) {
                    // Offer: price paid now, the regular price struck through, how long, then what it renews at.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            summary.headline,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (selected) accent else scheme.onSurface,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        // A free trial strikes through the regular price it replaces.
                        (summary.original ?: summary.recurring.takeIf { summary.trialSpan != null && summary.introSpan == null })?.let { was ->
                            Text(
                                was,
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurfaceVariant,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                modifier = Modifier.padding(bottom = 4.dp),
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        when {
                            summary.introSpan != null -> uiText(R.string.for_your_x, summary.introSpan)
                            summary.trialSpan != null -> "for ${summary.trialSpan}"
                            else -> "per ${summary.recurringPeriod}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    summary.headlinePerMonth?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = if (selected) accent else scheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        stringResource(R.string.then_x_x, summary.recurring, summary.recurringPeriod),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        summary.recurring,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = scheme.onSurface,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(stringResource(R.string.per_x, summary.recurringPeriod), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    summary.headlinePerMonth?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (selected) accent else scheme.primary)
                    }
                    if (kind == PlayCatalog.PlanKind.MONTHLY) {
                        Text(stringResource(R.string.billed_monthly), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (selected) accent else scheme.primary)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // Bottom chip: the plan's second reason, so both cards end on the same line.
                val chip: String? = when {
                    isCurrent -> null
                    kind == PlayCatalog.PlanKind.YEARLY && savingsPercent != null -> uiText(R.string.save_x_vs_monthly, savingsPercent)
                    hasOffer && summary?.trialSpan != null && summary.introSpan == null -> uiText(R.string.nothing_to_pay_today)
                    kind == PlayCatalog.PlanKind.MONTHLY -> uiText(R.string.cancel_anytime)
                    else -> null
                }
                chip?.let {
                    val strong = hasOffer || kind == PlayCatalog.PlanKind.YEARLY
                    PlanChip(
                        it,
                        if (strong) scheme.tertiaryContainer else scheme.surfaceVariant,
                        if (strong) scheme.onTertiaryContainer else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * "How your offer works" for the selected plan: today's price, the date the regular price starts
 * (computed from the offer's phases), and how to cancel before it. Play subscriptions policy:
 * offer terms, renewal price and timing visible before purchase.
 */
@Composable
internal fun PlanExplainer(summary: PlayOfferText.OfferSummary, hasOffer: Boolean) {
    if (!hasOffer) {
        RenewalExplainer(summary)
        return
    }
    val startsAt = remember(summary) {
        java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(PlayOfferText.regularPriceStartsAt(System.currentTimeMillis(), summary.introPeriods)))
    }
    val today = when {
        summary.introSpan != null -> buildString {
            summary.trialSpan?.let { append(uiText(R.string.free_for_x_then, it)) }
            append(if (summary.trialSpan != null) uiText(R.string.x_for_your_x, summary.headline, summary.introSpan) else uiText(R.string.pay_x_for_your_x, summary.headline, summary.introSpan))
            if (summary.discountPercent != null) {
                append(" — ${summary.discountPercent}% off")
                summary.original?.let { append(uiText(R.string.the_regular_x, it)) }
            }
            append(".")
        }
        else -> uiText(R.string.free_for_x_nothing_to_pay_today, summary.trialSpan)
    }
    ExplainerSteps(uiText(R.string.how_your_offer_works), listOf(
        Triple(Icons.Default.LocalOffer, "Today", today),
        Triple(Icons.Default.Autorenew, "From $startsAt", "${summary.recurring} / ${summary.recurringPeriod}, renewing automatically."),
        Triple(
            Icons.Default.EventBusy, "Cancel anytime",
            if (summary.trialSpan != null && summary.introSpan == null)
                "Cancel in Google Play before $startsAt and you won't be charged."
            else "Cancel in Google Play before $startsAt to avoid the renewal; Premium stays until then."
        )
    ))
}

/** No offer for this account: the plain terms, all from Play (price, period) plus the first renewal date. */
@Composable
private fun RenewalExplainer(summary: PlayOfferText.OfferSummary) {
    val renewsAt = remember(summary) {
        // The plain base plan has a single (recurring) phase: the first renewal is one period from today.
        val period = when (summary.recurringPeriod) { "year" -> "P1Y"; "week" -> "P1W"; "day" -> "P1D"; else -> "P1M" }
        java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(PlayOfferText.regularPriceStartsAt(System.currentTimeMillis(), listOf(period to 1))))
    }
    ExplainerSteps(uiText(R.string.how_it_works), listOf(
        Triple(Icons.Default.Payments, "Today", "${summary.recurring} for your first ${summary.recurringPeriod}, paid through Google Play."),
        Triple(Icons.Default.Autorenew, "From $renewsAt", "${summary.recurring} / ${summary.recurringPeriod}, renewing automatically."),
        Triple(Icons.Default.EventBusy, "Cancel anytime", "Cancel in Google Play before $renewsAt to avoid the renewal; Premium stays until then.")
    ))
}

@Composable
private fun ExplainerSteps(heading: String, steps: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String>>) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.Md,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(heading, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = scheme.primary)
            steps.forEach { (icon, title, body) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = scheme.primaryContainer, modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(15.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                        Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A plan kind Google Play didn't return (base plan not active, or not offered in this country/store). */
@Composable
private fun UnavailablePlanTile(kind: PlayCatalog.PlanKind, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = Radius.Md,
        color = scheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(localizedLabel(PlayCatalog.badge(kind)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Text(stringResource(R.string.not_available_yet), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Text(
                stringResource(R.string.google_play_doesn_t_offer_this_plan_in_you),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanChip(text: String, container: Color, content: Color) {
    Surface(color = container, shape = Radius.Xs) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = content,
            // Two lines, centred: "NOTHING TO PAY TODAY" / "SAVE 26% VS MONTHLY" never get cut in a half-width tile.
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** What Premium unlocks — compact checklist. Play's own product description, when set, is the hero's subtitle. */
@Composable
private fun PremiumBenefits() {
    val items = listOf(
        stringResource(R.string.benefit_publish_listings),
        stringResource(R.string.benefit_manage_requests),
        stringResource(R.string.benefit_dashboard),
        stringResource(R.string.benefit_explore)
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Radius.Md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            uiText(R.string.payment_problem),
            uiText(R.string.google_play_couldn_t_renew_your_subscripti),
            uiText(R.string.fix_payment)
        )
        "ON_HOLD" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.ERROR,
            uiText(R.string.subscription_on_hold),
            uiText(R.string.your_payment_didn_t_go_through_so_pro_host),
            uiText(R.string.fix_payment)
        )
        "PAUSED" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.INFO,
            uiText(R.string.subscription_paused),
            uiText(R.string.pro_host_access_and_your_listings_return_w),
            uiText(R.string.resume)
        )
        "CANCELED" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.WARNING,
            uiText(R.string.subscription_canceled),
            "It won't renew. You keep Pro Host access${expiryDate?.let { " until $it" } ?: " until the end of the paid period"}. Changed your mind? Resubscribe in Google Play.",
            uiText(R.string.resubscribe)
        )
        "PENDING" -> StatusCopy(
            com.example.ui.components.ProHostAlertSeverity.INFO,
            uiText(R.string.payment_processing),
            uiText(R.string.google_play_is_still_processing_your_payme),
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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            stringResource(R.string.browsing_and_booking_workspaces_is_free_pr),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.payment_is_charged_to_your_google_play_acc) +
                stringResource(R.string.period_shown_until_you_cancel_cancel_anyti) +
                stringResource(R.string.access_until_the_end_of_the_paid_period),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onOpenLegal, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(stringResource(R.string.terms_privacy), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Close button for a dismissible [ProHostAlertBanner]; inherits the banner's content color. */
@Composable
private fun BannerDismiss(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss), modifier = Modifier.size(16.dp))
    }
}
