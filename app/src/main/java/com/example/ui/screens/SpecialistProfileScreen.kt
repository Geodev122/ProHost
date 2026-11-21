package com.example.ui.screens

import com.example.ui.viewmodel.billing
import com.example.util.toPickedFile
import com.example.ui.util.timeRange
import com.example.ui.util.localizedLabel
import com.example.ui.theme.Elevation
import com.example.ui.util.UiMessages
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.components.drawer.DrawerIdentityCard
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.ui.components.Button
import com.example.ui.components.ProHostButtonDefaults
import com.example.ui.components.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel
import com.example.util.UpdateState
import kotlinx.coroutines.launch
import com.example.shared.resources.Res
import com.example.shared.resources.active_leases
import com.example.shared.resources.active_listings
import com.example.shared.resources.all_rental_applications
import com.example.shared.resources.app_language
import com.example.shared.resources.app_language_options
import com.example.shared.resources.applications_received
import com.example.shared.resources.awaiting_host_approval
import com.example.shared.resources.become_a_pro_host
import com.example.shared.resources.blocked_people
import com.example.shared.resources.cancel
import com.example.shared.resources.cancel_request
import com.example.shared.resources.cancel_this_request
import com.example.shared.resources.cancel_your_google_play_subscription_first
import com.example.shared.resources.check_update
import com.example.shared.resources.choose_what_usage_data_you_share
import com.example.shared.resources.city
import com.example.shared.resources.commercial_units_live
import com.example.shared.resources.confirmed_workspace_slots
import com.example.shared.resources.contact_support
import com.example.shared.resources.contact_verification_info
import com.example.shared.resources.covered_by_prohost
import com.example.shared.resources.delete
import com.example.shared.resources.delete_account
import com.example.shared.resources.deleting
import com.example.shared.resources.explore_available_clinics_studios_and_exec
import com.example.shared.resources.find_space
import com.example.shared.resources.for_space_owners
import com.example.shared.resources.formula_x_x_xx
import com.example.shared.resources.from_accepted_bookings
import com.example.shared.resources.full_name
import com.example.shared.resources.governorate_area
import com.example.shared.resources.help_legal_and_your_session
import com.example.shared.resources.hosts_reach_you_here
import com.example.shared.resources.install
import com.example.shared.resources.keep_request
import com.example.shared.resources.legal_privacy_terms_policies
import com.example.shared.resources.list_your_space_and_receive_booking_reques
import com.example.shared.resources.listed_and_active
import com.example.shared.resources.live_listings
import com.example.shared.resources.manage_subscription
import com.example.shared.resources.manage_subscription_in_google_play
import com.example.shared.resources.monthly_income
import com.example.shared.resources.more
import com.example.shared.resources.no_active_workspace_leases
import com.example.shared.resources.not_signed_in
import com.example.shared.resources.pending_requests
import com.example.shared.resources.privacy
import com.example.shared.resources.profile_picture_2
import com.example.shared.resources.prohost_premium
import com.example.shared.resources.prohost_up_to_date
import com.example.shared.resources.published
import com.example.shared.resources.regions
import com.example.shared.resources.resend_verification
import com.example.shared.resources.save_profile_changes
import com.example.shared.resources.saving_2
import com.example.shared.resources.sign_in_to_view_your_profile
import com.example.shared.resources.sign_out
import com.example.shared.resources.spaces_on_prohost
import com.example.shared.resources.starting_x_x_mo_term
import com.example.shared.resources.synced
import com.example.shared.resources.tap_to_add_a_photo
import com.example.shared.resources.tap_to_replace
import com.example.shared.resources.tenant_inquiries
import com.example.shared.resources.total_bookings
import com.example.shared.resources.total_x_usd
import com.example.shared.resources.up_to_date_on_all_devices
import com.example.shared.resources.update_available_short
import com.example.shared.resources.update_check_failed
import com.example.shared.resources.update_checking
import com.example.shared.resources.update_downloading
import com.example.shared.resources.update_ready_to_install
import com.example.shared.resources.verify_email_body_x
import com.example.shared.resources.verify_email_title
import com.example.shared.resources.view_rentals
import com.example.shared.resources.welcome_back_x
import com.example.shared.resources.whatsapp
import com.example.shared.resources.whatsapp_contact_number
import com.example.shared.resources.whatsapp_host
import com.example.shared.resources.your_profile_and_listings_are_deleted_perm
import com.example.shared.resources.your_profile_is_deleted_permanently
import com.example.shared.resources.your_rental_request_for_x_will_be_withdraw
import com.example.ui.util.appString
import com.example.ui.util.sharedPlural
import com.example.ui.util.sharedText

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpecialistProfileScreen(
    viewModel: ProHostViewModel,
    inAppUpdateManager: com.example.util.AppUpdater? = null,
    onNavigateToTab: (String) -> Unit = {},
    // Profile › More: what the drawer used to offer specialists (they have no drawer).
    onOpenLegal: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null
) {
    val needsEmailVerification by viewModel.needsEmailVerification.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    var showBlockedPeople by remember { mutableStateOf(false) }
    if (showBlockedPeople) com.example.ui.components.BlockedPeopleSheet(viewModel) { showBlockedPeople = false }

    // No fabricated Super Admin fallback here anymore — a null currentUser means the
    // session genuinely isn't signed in (this screen used to bake in a real hardcoded
    // identity, geo.elnajjar@gmail.com as ADMIN, as its "no user yet" placeholder).
    val user = currentUser
    if (user == null) {
        ProEmptyState(
            title = appString(Res.string.not_signed_in),
            description = appString(Res.string.sign_in_to_view_your_profile),
            icon = Icons.Default.PersonOff,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    var name by remember(user) { mutableStateOf(user.fullName) }
    var specialty by remember(user) { mutableStateOf(user.specialty) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    // Split into a real country-code picker + local digits, matching the same
    // pattern CreateListingDialog's owner-phone field and registration's phone
    // step already use — this used to be a bare text field with just a
    // "(+961 ...)" hint, so nothing ever enforced a country code, and a saved
    // number with none broke wa.me links downstream. Best-effort split of
    // whatever the profile already has: match its longest known dial-code
    // prefix, defaulting to Lebanon.
    var phoneCountry by remember(user) {
        mutableStateOf(
            COUNTRIES.filter { user.phone.trim().startsWith(it.dialCode) }
                .maxByOrNull { it.dialCode.length }
                ?: COUNTRIES.first { it.isoCode == "LB" }
        )
    }
    var phoneLocal by remember(user) {
        mutableStateOf(user.phone.trim().removePrefix(phoneCountry.dialCode).trim())
    }
    var selectedCountry by remember(user) { mutableStateOf(findCountryByName(user.country)) }
    var governorateArea by remember(user) { mutableStateOf(user.governorate) }
    var city by remember(user) { mutableStateOf(user.city) }
    var pendingCancelRequest by remember { mutableStateOf<RentalBookingRequest?>(null) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var profilePicUploadError by remember { mutableStateOf<String?>(null) }
    var showRequirementsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .proHostScreenBackground()
            .testTag("specialist_profile_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        // =========================================================================
        // 3. ROLE-SPECIFIC VITAL TELEMETRY & PERFORMANCE METRICS
        // =========================================================================
        val ownerSpaces by viewModel.ownerSpaces.collectAsState()
        val allSpacesList by viewModel.spaces.collectAsState()
        val practitionerBookingsForStats by viewModel.practitionerBookings.collectAsState()
        val ownerIncomingRequests by viewModel.ownerIncomingRequests.collectAsState()

        val activeLeasesCount = practitionerBookingsForStats.count { it.status == BookingRequestStatus.ACCEPTED }
        val pendingApplicationsCount = practitionerBookingsForStats.count { it.status == BookingRequestStatus.PENDING }
        val ownerActiveListings = if (ownerSpaces.isNotEmpty()) ownerSpaces else if (user.role == UserRole.ADMIN) allSpacesList else emptyList()
        val estimatedYieldUsd = ownerActiveListings.sumOf { it.baseMonthlyRateUsd }
        // The user's own entitlement: plan name only — prices live in Google Play, never here.
        val ownerPackageExpired = user.ownerPackageExpiryMillis?.let { it <= System.currentTimeMillis() } ?: false
        val ownerActivePlanId = if (ownerPackageExpired) null else user.ownerPackageId

        // =========================================================================
        // 7. COMPACT APP VERSION & UPDATE CHECK
        // =========================================================================
        var updateState by remember { mutableStateOf(UpdateState.UP_TO_DATE) }
        LaunchedEffect(inAppUpdateManager) {
            inAppUpdateManager?.updateState?.collect { updateState = it }
        }

        // =========================================================================
        // 9. DANGER ZONE — PERMANENT ACCOUNT DELETION
        // =========================================================================
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        var isDeletingAccount by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            item {
                // =========================================================================
                // 0. IDENTITY CARD  (formerly in side drawer — single source of truth)
                // =========================================================================
                DrawerIdentityCard(user = user)

            }
            // Readiness banner: the same checks as booking / hosting, completed in the shared sheet.
            if (user.role != UserRole.ADMIN) {
                item {
                    com.example.ui.components.KycCompletionBanner(
                        user = user,
                        onComplete = { showRequirementsSheet = true }
                    )
                }
            }
            // Email + password accounts that haven't confirmed their address yet: a reminder, never a gate.
            if (needsEmailVerification && !user.emailVerified) {
                item {
                    ProHostAlertBanner(
                        title = appString(Res.string.verify_email_title),
                        message = appString(Res.string.verify_email_body_x, user.email),
                        icon = Icons.Default.MarkEmailUnread,
                        modifier = Modifier.testTag("verify_email_banner"),
                        action = {
                            TextButton(onClick = { viewModel.resendEmailVerification() }) {
                                Text(appString(Res.string.resend_verification), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }
            }
            // "Become a Pro Host" upgrade CTA — only for SPECIALIST users
            if (user.role == UserRole.SPECIALIST) {
                item {
                    // A compact tag on the button's edge says who it is for: people who want to list a space.
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Surface(
                            onClick = { onNavigateToTab("owner_subscriptions") },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            shadowElevation = Elevation.level2,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appString(Res.string.become_a_pro_host),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Text(
                                        text = appString(Res.string.list_your_space_and_receive_booking_reques),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f)
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                            shadowElevation = Elevation.level2,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp).offset(y = (-10).dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(11.dp))
                                Text(
                                    appString(Res.string.for_space_owners),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }
            // =========================================================================
            // 1. WELCOME BOX WITH REAL, LATEST-UPDATE-DRIVEN STATUS (SPECIALIST)
            // =========================================================================
            if (user.role == UserRole.SPECIALIST) {
                item {
                    val fcmAlertsForWelcome by viewModel.fcmAlerts.collectAsState()
                    val bookingsForWelcome by viewModel.practitionerBookings.collectAsState()

                    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US) }
                    fun daysUntil(dateStr: String): Long? {
                        if (dateStr.isBlank()) return null
                        return try {
                            val target = dateFormat.parse(dateStr) ?: return null
                            val diffMs = target.time - System.currentTimeMillis()
                            diffMs / (24 * 60 * 60 * 1000)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val latestRelevantAlert = fcmAlertsForWelcome
                        .filter { !it.isRead && (it.category == "BOOKING_UPDATE" || it.category == "PAYMENT_REMINDER") }
                        .maxByOrNull { it.timestamp }

                    val nearestUpcomingBooking = bookingsForWelcome
                        .filter { it.status == BookingRequestStatus.ACCEPTED }
                        .mapNotNull { booking -> daysUntil(booking.startDate)?.let { booking to it } }
                        .filter { it.second >= 0 }
                        .minByOrNull { it.second }

                    val pendingCount = bookingsForWelcome.count { it.status == BookingRequestStatus.PENDING }

                    val updateLine: String = when {
                        latestRelevantAlert != null -> latestRelevantAlert.body.ifBlank { latestRelevantAlert.title }
                        nearestUpcomingBooking != null -> {
                            val (booking, days) = nearestUpcomingBooking
                            when {
                                days == 0L -> sharedText("your_booking_at_x_starts_today", booking.spaceTitle)
                                days == 1L -> sharedText("your_booking_at_x_starts_tomorrow", booking.spaceTitle)
                                else -> sharedText("your_booking_at_x_starts_in_x_days", booking.spaceTitle, days)
                            }
                        }
                        pendingCount > 0 -> sharedPlural("requests_awaiting_host", pendingCount, pendingCount)
                        else -> sharedText("no_updates_right_now_explore_available_wor")
                    }

                    ProSurfaceCard(
                        modifier = Modifier.shadow(4.dp, MaterialTheme.shapes.extraLarge),
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(Spacing.lg)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(
                                    Icons.Default.WavingHand,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = appString(Res.string.welcome_back_x, user.fullName),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = updateLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Button(
                                    colors = proPrimaryButtonColors(),
                                    onClick = { onNavigateToTab("search_map") },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(appString(Res.string.find_space), style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = { onNavigateToTab("pro_rentals") },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(appString(Res.string.view_rentals), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            // Specialist's own performance stats used to be shown twice — once here
            // and once, in full, on the My Rentals screen. This card is now
            // PRO_HOST/ADMIN-only; a Specialist opening Profile sees this section
            // skipped entirely rather than a duplicate summary.
            if (user.role != UserRole.SPECIALIST) {
                item {
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            ProSectionHeader(
                                title = when (user.role) {
                                    UserRole.SPECIALIST -> sharedText("practitioner_rental_activity")
                                    UserRole.PRO_HOST -> sharedText("host_performance_yield")
                                    UserRole.ADMIN -> sharedText("central_platform_governance")
                                },
                                subtitle = when (user.role) {
                                    UserRole.SPECIALIST -> sharedText("active_leases_and_pending_bookings")
                                    UserRole.PRO_HOST -> sharedText("managed_spaces_incoming_tenant_inquiries_a")
                                    UserRole.ADMIN -> sharedText("system_spaces_cloud_sync_status_and_transa")
                                },
                                icon = Icons.Default.Analytics,
                                trailingContent = {
                                    ProStatusBadge(
                                        type = if (user.isVerified) ProBadgeType.CUSTOM_SUCCESS else ProBadgeType.CUSTOM_WARNING,
                                        customText = if (user.isVerified) sharedText("verified_status") else sharedText("pending_review")
                                    )
                                }
                            )

                            when (user.role) {
                                UserRole.SPECIALIST -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ProMetricTile(
                                            title = appString(Res.string.active_leases),
                                            value = "$activeLeasesCount",
                                            subtitle = appString(Res.string.confirmed_workspace_slots),
                                            icon = Icons.Default.EventAvailable,
                                            iconTint = MaterialTheme.proColors.success,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProMetricTile(
                                            title = appString(Res.string.pending_requests),
                                            value = "$pendingApplicationsCount",
                                            subtitle = appString(Res.string.awaiting_host_approval),
                                            icon = Icons.Default.PendingActions,
                                            iconTint = MaterialTheme.proColors.warning,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ProMetricTile(
                                            title = appString(Res.string.total_bookings),
                                            value = "${practitionerBookingsForStats.size}",
                                            subtitle = appString(Res.string.all_rental_applications),
                                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                            iconTint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProMetricTile(
                                            title = appString(Res.string.whatsapp),
                                            value = if (user.hasWhatsAppNumber()) sharedText("added") else sharedText("missing"),
                                            subtitle = appString(Res.string.hosts_reach_you_here),
                                            icon = Icons.Default.VerifiedUser,
                                            iconTint = MaterialTheme.proColors.success,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                UserRole.PRO_HOST -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ProMetricTile(
                                            title = appString(Res.string.active_listings),
                                            value = "${ownerActiveListings.size}",
                                            subtitle = appString(Res.string.commercial_units_live),
                                            icon = Icons.Default.HomeWork,
                                            iconTint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProMetricTile(
                                            title = appString(Res.string.tenant_inquiries),
                                            value = "${ownerIncomingRequests.size}",
                                            subtitle = appString(Res.string.applications_received),
                                            icon = Icons.Default.Inbox,
                                            iconTint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ProMetricTile(
                                            title = appString(Res.string.monthly_income),
                                            value = sharedText("x_usd", estimatedYieldUsd.toInt()),
                                            subtitle = appString(Res.string.from_accepted_bookings),
                                            icon = Icons.Default.AttachMoney,
                                            iconTint = MaterialTheme.proColors.success,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProMetricTile(
                                            title = appString(Res.string.prohost_premium),
                                            value = ownerActivePlanId?.let { localizedLabel(com.example.data.billing.PlayCatalog.planBadge(it)) } ?: sharedText("no_plan"),
                                            subtitle = ownerActivePlanId?.let { localizedLabel(com.example.data.billing.PlayCatalog.planLabel(it)) } ?: sharedText("no_active_subscription"),
                                            icon = Icons.Default.Payment,
                                            iconTint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                UserRole.ADMIN -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ProMetricTile(
                                            title = appString(Res.string.spaces_on_prohost),
                                            value = "${allSpacesList.size}",
                                            subtitle = appString(Res.string.listed_and_active),
                                            icon = Icons.Default.Layers,
                                            iconTint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProMetricTile(
                                            title = appString(Res.string.regions),
                                            value = "${Governorate.entries.size}",
                                            subtitle = appString(Res.string.covered_by_prohost),
                                            icon = Icons.Default.Map,
                                            iconTint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        ProMetricTile(
                                            title = appString(Res.string.synced),
                                            value = sharedText("live"),
                                            subtitle = appString(Res.string.up_to_date_on_all_devices),
                                            icon = Icons.Default.CloudDone,
                                            iconTint = MaterialTheme.proColors.success,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ProMetricTile(
                                            title = appString(Res.string.published),
                                            value = "${allSpacesList.count { it.status == ListingStatus.ACTIVE }}",
                                            subtitle = appString(Res.string.live_listings),
                                            icon = Icons.Default.Storefront,
                                            iconTint = MaterialTheme.proColors.warning,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // =========================================================================
            // 5. ROLE-SPECIFIC ACTIVITY CARDS (BOOKINGS / LEASES)
            // =========================================================================
            if (user.role == UserRole.SPECIALIST) {
                item {
                    val practitionerBookings by viewModel.practitionerBookings.collectAsState()
                    val spaces by viewModel.spaces.collectAsState()

                    ProSurfaceCard(
                        modifier = Modifier.shadow(2.dp, MaterialTheme.shapes.large)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            // Title/subtitle dropped here on purpose — this same summary
                            // (count + status) already lives on the My Rentals screen, so
                            // this card keeps just the count badge and the list itself.
                            if (practitionerBookings.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.EventAvailable,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "${practitionerBookings.size} total")
                                }
                            }

                            if (practitionerBookings.isEmpty()) {
                                ProEmptyState(
                                    title = appString(Res.string.no_active_workspace_leases),
                                    description = appString(Res.string.explore_available_clinics_studios_and_exec),
                                    icon = Icons.Default.EventAvailable,
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    practitionerBookings.forEach { req ->
                                        val targetSpace = spaces.find { it.id == req.spaceId }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.large,
                                            colors = CardDefaults.cardColors(
                                                containerColor = when (req.status) {
                                                    BookingRequestStatus.ACCEPTED -> MaterialTheme.proColors.infoContainer
                                                    BookingRequestStatus.PENDING -> MaterialTheme.proColors.warningContainer
                                                    BookingRequestStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
                                                    BookingRequestStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                when (req.status) {
                                                    BookingRequestStatus.ACCEPTED -> MaterialTheme.proColors.info
                                                    BookingRequestStatus.PENDING -> MaterialTheme.proColors.warning
                                                    BookingRequestStatus.REJECTED -> MaterialTheme.colorScheme.error
                                                    BookingRequestStatus.CANCELLED -> MaterialTheme.colorScheme.outlineVariant
                                                }
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = req.spaceTitle,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    when (req.status) {
                                                        BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                                                        BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = sharedText("pending_host"))
                                                        BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = sharedText("declined"))
                                                        BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = sharedText("cancelled"))
                                                    }
                                                }

                                                val chosenDaysStr = if (req.selectedDays.isNotEmpty()) {
                                                    req.selectedDays.joinToString(", ")
                                                } else {
                                                    req.formula.daysOfWeek.joinToString(", ")
                                                }
                                                val chosenHoursStr = if (req.selectedStartHour.isNotBlank() && req.selectedEndHour.isNotBlank()) {
                                                    timeRange(req.selectedStartHour, req.selectedEndHour)
                                                } else {
                                                    timeRange(req.formula.startHour, req.formula.endHour)
                                                }
                                                val shiftDetail = if (req.selectedShift.isNotBlank()) " (${req.selectedShift})" else ""

                                                Row(verticalAlignment = Alignment.Top) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Assignment,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                                    Text(
                                                        text = req.selectedDateTimeRange.takeIf { req.dateModel == 2 && it.isNotBlank() }
                                                            ?.let { "${localizedLabel(req.formula.type.displayName)} · \u2066$it\u2069" }
                                                            ?: appString(Res.string.formula_x_x_xx, localizedLabel(req.formula.type.displayName), chosenDaysStr, chosenHoursStr, shiftDetail),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.Top) {
                                                    Icon(
                                                        Icons.Default.CalendarMonth,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                                    Text(
                                                        text = appString(Res.string.starting_x_x_mo_term, req.startDate, req.durationMonths) +
                                                            appString(Res.string.total_x_usd, req.totalAmountUsd.toInt()),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                                ) {
                                                    if (targetSpace != null) {
                                                        Button(
                                                            onClick = {
                                                                viewModel.launchWhatsAppInquiry(targetSpace, req.formula, req)
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            shape = MaterialTheme.shapes.small,
                                                            colors = ProHostButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
                                                            contentPadding = PaddingValues(vertical = 6.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.Chat,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(Spacing.xs))
                                                            Text(
                                                                appString(Res.string.whatsapp_host),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    if (req.status == BookingRequestStatus.PENDING) {
                                                        OutlinedButton(
                                                            onClick = { pendingCancelRequest = req },
                                                            modifier = Modifier.weight(0.6f),
                                                            shape = MaterialTheme.shapes.small,
                                                            contentPadding = PaddingValues(vertical = 6.dp)
                                                        ) {
                                                            Text(appString(Res.string.cancel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                // =========================================================================
                // 6. EDIT PROFILE & MEMBER DETAILS FORM
                // =========================================================================
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        ProSectionHeader(
                            title = when (user.role) {
                                UserRole.SPECIALIST -> sharedText("practitioner_details")
                                UserRole.PRO_HOST -> sharedText("host_business_details")
                                UserRole.ADMIN -> sharedText("super_administrator_identity")
                            },
                            subtitle = appString(Res.string.contact_verification_info),
                            icon = Icons.Default.Badge
                        )

                        var pendingProfilePic by remember { mutableStateOf<com.example.util.PickedFile?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            ProfilePicturePickerField(
                                pictureFile = pendingProfilePic,
                                existingUrl = user.profilePictureUrl,
                                onPictureSelected = { pendingProfilePic = it }
                            )
                            Column {
                                Text(appString(Res.string.profile_picture_2), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (user.profilePictureUrl != null || pendingProfilePic != null) appString(Res.string.tap_to_replace) else appString(Res.string.tap_to_add_a_photo),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        profilePicUploadError?.let { err ->
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        InputField(
                            value = name,
                            onValueChange = { name = it },
                            label = appString(Res.string.full_name),
                            leadingIcon = Icons.Default.Person,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        InputField(
                            value = specialty,
                            onValueChange = { specialty = it },
                            label = when (user.role) {
                                UserRole.SPECIALIST -> sharedText("profession_job_title_optional")
                                UserRole.PRO_HOST -> sharedText("host_category_commercial_real_estate_cowor")
                                UserRole.ADMIN -> sharedText("super_administrator_role_clearance")
                            },
                            leadingIcon = Icons.Default.Work,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        PhoneNumberField(
                            country = phoneCountry,
                            onCountryChange = {
                                phoneCountry = it
                                phone = formatToE164(it, phoneLocal)
                            },
                            number = phoneLocal,
                            onNumberChange = {
                                phoneLocal = it
                                phone = formatToE164(phoneCountry, it)
                            },
                            label = appString(Res.string.whatsapp_contact_number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        CountryDropdownField(
                            selectedCountry = selectedCountry,
                            onCountrySelected = { selectedCountry = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        InputField(
                            value = governorateArea,
                            onValueChange = { governorateArea = it },
                            label = appString(Res.string.governorate_area),
                            leadingIcon = Icons.Default.LocationOn,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        InputField(
                            value = city,
                            onValueChange = { city = it },
                            label = appString(Res.string.city),
                            leadingIcon = Icons.Default.LocationCity,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        ProPrimaryButton(
                            text = if (isSavingProfile) appString(Res.string.saving_2) else appString(Res.string.save_profile_changes),
                            enabled = !isSavingProfile,
                            isLoading = isSavingProfile,
                            onClick = {
                                isSavingProfile = true
                                profilePicUploadError = null
                                coroutineScope.launch {
                                    try {
                                        var profilePictureUrl: String? = null
                                        val localPic = pendingProfilePic
                                        var pictureUploadFailed = false
                                        if (localPic != null) {
                                            val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                                            profilePictureUrl = storageService.uploadProfilePicture(user.id, localPic)
                                            pictureUploadFailed = profilePictureUrl == null
                                        }
                                        val success = viewModel.updateProfile(
                                            name, specialty, phone,
                                            selectedCountry.name, governorateArea, city,
                                            profilePictureUrl
                                        )
                                        isSavingProfile = false
                                        // A failed picture upload must never read as a full success —
                                        // updateCurrentUserProfile falls back to the existing picture
                                        // when profilePictureUrl is null, so the rest of the edit still
                                        // saved; only the new picture didn't, and that needs its own
                                        // message rather than a blanket "Updated Successfully!".
                                        when {
                                            !success -> UiMessages.show(sharedText("failed_to_update_profile_please_try_again"))
                                            pictureUploadFailed -> {
                                                profilePicUploadError = sharedText("profile_saved_but_the_new_photo_couldn_t_b")
                                                UiMessages.show(sharedText("profile_saved_photo_upload_failed_please_r"), long = true)
                                            }
                                            else -> UiMessages.show(sharedText("profile_updated_successfully"))
                                        }
                                    } catch (e: Exception) {
                                        isSavingProfile = false
                                        UiMessages.show(sharedText("an_error_occurred_please_try_again"))
                                    }
                                }
                            },
                            icon = Icons.Default.Save,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The status text wraps; the button keeps its size (at large font it was cut to "Check Updat").
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "v${com.example.BuildConfig.VERSION_NAME} • ${when (updateState) {
                                UpdateState.CHECKING -> appString(Res.string.update_checking)
                                UpdateState.UPDATE_AVAILABLE_FLEXIBLE, UpdateState.UPDATE_AVAILABLE_IMMEDIATE -> appString(Res.string.update_available_short)
                                UpdateState.DOWNLOADING -> appString(Res.string.update_downloading)
                                UpdateState.DOWNLOADED -> appString(Res.string.update_ready_to_install)
                                UpdateState.FAILED -> appString(Res.string.update_check_failed)
                                else -> appString(Res.string.prohost_up_to_date)
                            }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Proper compact buttons (the audit's update-row fix): Install is the main action, Check update secondary.
                        if (updateState == UpdateState.DOWNLOADED) {
                            ProPrimaryButton(
                                text = appString(Res.string.install),
                                onClick = { inAppUpdateManager?.completeUpdate() }
                            )
                        } else {
                            ProOutlinedButton(
                                text = appString(Res.string.check_update),
                                onClick = {
                                    if (inAppUpdateManager != null) {
                                        inAppUpdateManager.checkForAppUpdate(preferImmediate = false)
                                        UiMessages.show(sharedText("checking_google_play_for_updates"))
                                    } else {
                                        UiMessages.show(sharedText("prohost_vx_is_up_to_date", com.example.BuildConfig.VERSION_NAME))
                                    }
                                },
                                compact = true
                            )
                        }
                    }
                }

            }
            // Contact Support moved to the drawer's merged "More" section
            // (AppDrawerContent.kt) — one fewer card on this already-long screen,
            // and it now sits alongside Legal/Favorites where a specialist already
            // looks for account-level actions.
            // Play: a settings-level link to manage the subscription (cancel, payment method,
            // pause, resubscribe), deep-linked to ProHost Premium in Google Play.
            if (user.role != UserRole.ADMIN) {
                item {
                    val settingsActivity = androidx.activity.compose.LocalActivity.current
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            ProSectionHeader(
                                title = appString(Res.string.prohost_premium),
                                subtitle = user.ownerPackageId?.let { localizedLabel(com.example.data.billing.PlayCatalog.planLabel(it)) }
                                    ?: sharedText("not_subscribed"),
                                icon = Icons.Default.WorkspacePremium
                            )
                            TextButton(
                                onClick = {
                                    settingsActivity?.let {
                                        viewModel.billing.openManageSubscriptions(it, com.example.data.billing.PlayCatalog.PRODUCT_ID)
                                    }
                                },
                                enabled = settingsActivity != null
                            ) {
                                Text(appString(Res.string.manage_subscription_in_google_play), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item {
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ProSectionHeader(
                            title = appString(Res.string.privacy),
                            subtitle = appString(Res.string.choose_what_usage_data_you_share),
                            icon = Icons.Default.Shield
                        )
                        com.example.ui.components.AnalyticsConsentToggle()
                    }
                }

            }
            if (onOpenLegal != null || onSignOut != null) {
                item {
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            ProSectionHeader(
                                title = appString(Res.string.more),
                                subtitle = appString(Res.string.help_legal_and_your_session),
                                icon = Icons.Default.MoreHoriz
                            )
                            if (onOpenLegal != null) {
                                ProfileMoreRow(
                                    icon = Icons.Default.Gavel,
                                    label = appString(Res.string.legal_privacy_terms_policies),
                                    onClick = onOpenLegal
                                )
                            }
                            // App language: Android 13+ has a per-app language screen; older versions follow the
                            // system language, so open the system language settings there.
                            ProfileMoreRow(
                                icon = Icons.Default.Language,
                                label = appString(Res.string.app_language),
                                supporting = appString(Res.string.app_language_options),
                                onClick = {
                                    runCatching { com.example.ui.util.PlatformActions.require().openAppLanguageSettings() }
                                }
                            )
                            ProfileMoreRow(
                                icon = Icons.Default.Block,
                                label = appString(Res.string.blocked_people),
                                onClick = { showBlockedPeople = true }
                            )
                            ProfileMoreRow(
                                icon = Icons.AutoMirrored.Filled.Help,
                                label = appString(Res.string.contact_support),
                                onClick = { com.example.ui.util.launchSupportEmail(user, user.role.name) }
                            )
                            if (onSignOut != null) {
                                ProfileMoreRow(
                                    icon = Icons.AutoMirrored.Filled.Logout,
                                    label = appString(Res.string.sign_out),
                                    supporting = user.email.takeIf { it.isNotBlank() },
                                    onClick = onSignOut
                                )
                            }
                        }
                    }
                }
            }
            item {
                // Deliberately small: one quiet text action at the very end of the profile.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !isDeletingAccount,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(if (isDeletingAccount) appString(Res.string.deleting) else appString(Res.string.delete_account), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (showRequirementsSheet) {
            com.example.ui.components.RequirementsSheet(
                user = user,
                viewModel = viewModel,
                requireAddress = user.role == UserRole.PRO_HOST,
                onDismiss = { showRequirementsSheet = false },
                onReady = { showRequirementsSheet = false }
            )
        }

        if (showDeleteConfirmation) {
            ProHostDialog(
                onDismissRequest = { if (!isDeletingAccount) showDeleteConfirmation = false },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(appString(Res.string.delete_account)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            if (user.role == UserRole.PRO_HOST) appString(Res.string.your_profile_and_listings_are_deleted_perm)
                            else appString(Res.string.your_profile_is_deleted_permanently),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (user.ownerPackageId != null) {
                            val activity = androidx.activity.compose.LocalActivity.current
                            Text(
                                appString(Res.string.cancel_your_google_play_subscription_first),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (activity != null) {
                                TextButton(onClick = { viewModel.billing.openManageSubscriptions(activity, user.ownerPackageId) }) {
                                    Text(appString(Res.string.manage_subscription))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isDeletingAccount,
                        onClick = {
                            isDeletingAccount = true
                            coroutineScope.launch {
                                val result = viewModel.deleteAccount()
                                isDeletingAccount = false
                                showDeleteConfirmation = false
                                result.exceptionOrNull()?.let { error ->
                                    UiMessages.show(com.example.util.friendlyErrorMessage(
                                            error,
                                            sharedText("couldn_t_delete_your_account_please_try_ag")
                                        ),
                                        long = true)
                                }
                                // On success the screen unmounts on its own: currentUser
                                // becomes null once repository.logout() runs inside
                                // viewModel.deleteAccount(), and ProHostNavGraph renders
                                // LoginAuthScreen for a null currentUser.
                            }
                        }
                    ) {
                        Text(if (isDeletingAccount) appString(Res.string.deleting) else appString(Res.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }, enabled = !isDeletingAccount) {
                        Text(appString(Res.string.cancel))
                    }
                }
            )
        }

        pendingCancelRequest?.let { target ->
            ProHostDialog(
                onDismissRequest = { pendingCancelRequest = null },
                icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(appString(Res.string.cancel_this_request)) },
                text = { Text(appString(Res.string.your_rental_request_for_x_will_be_withdraw, target.spaceTitle)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cancelBookingRequest(target.id)
                        pendingCancelRequest = null
                    }) {
                        Text(appString(Res.string.cancel_request), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCancelRequest = null }) {
                        Text(appString(Res.string.keep_request))
                    }
                }
            )
        }
    
    }
}

/** One tappable row of Profile › More. */
@Composable
private fun ProfileMoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
