package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.example.util.InAppUpdateManager
import com.example.util.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SpecialistProfileScreen(
    viewModel: ProHostViewModel,
    inAppUpdateManager: InAppUpdateManager? = null,
    onNavigateToTab: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()

    // No fabricated Super Admin fallback here anymore — a null currentUser means the
    // session genuinely isn't signed in (this screen used to bake in a real hardcoded
    // identity, geo.elnajjar@gmail.com as ADMIN, as its "no user yet" placeholder).
    val user = currentUser
    if (user == null) {
        ProEmptyState(
            title = "Not signed in",
            description = "Sign in to view your profile.",
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
    val profileDetailsRequester = remember { BringIntoViewRequester() }
    val scrollToProfileDetails: () -> Unit = { coroutineScope.launch { profileDetailsRequester.bringIntoView() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush())
            .testTag("specialist_profile_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 0. IDENTITY CARD  (formerly in side drawer — single source of truth)
            // =========================================================================
            DrawerIdentityCard(
                user = user,
                currentPlanId = user.ownerPackageId
            )

            // Readiness banner: the same checks as booking / hosting, completed in the shared sheet.
            if (user.role != UserRole.ADMIN) {
                com.example.ui.components.KycCompletionBanner(
                    user = user,
                    phoneLinked = com.example.data.auth.PhoneLink.isLinked(),
                    onComplete = { showRequirementsSheet = true }
                )
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

            // "Become a Pro Host" upgrade CTA — only for SPECIALIST users
            if (user.role == UserRole.SPECIALIST) {
                Surface(
                    onClick = { onNavigateToTab("owner_subscriptions") },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                        Text(
                            text = "Become a Pro Host",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // =========================================================================
            // 1. WELCOME BOX WITH REAL, LATEST-UPDATE-DRIVEN STATUS (SPECIALIST)
            // =========================================================================
            if (user.role == UserRole.SPECIALIST) {
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
                    .filter { !it.isRead && (it.category == "BOOKING_ACCEPTANCE" || it.category == "PAYMENT_REMINDER") }
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
                            days == 0L -> "Your booking at ${booking.spaceTitle} starts today."
                            days == 1L -> "Your booking at ${booking.spaceTitle} starts tomorrow."
                            else -> "Your booking at ${booking.spaceTitle} starts in $days days."
                        }
                    }
                    pendingCount > 0 -> "$pendingCount request${if (pendingCount != 1) "s" else ""} awaiting host response."
                    else -> "No updates right now — explore available workspaces."
                }

                ProSurfaceCard(
                    modifier = Modifier.shadow(4.dp, MaterialTheme.shapes.extraLarge),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(Spacing.lg)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.WavingHand,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Welcome back, ${user.fullName}",
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigateToTab("search_map") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Find Space", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = { onNavigateToTab("pro_rentals") },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Rentals", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

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

            // Specialist's own performance stats used to be shown twice — once here
            // and once, in full, on the My Rentals screen. This card is now
            // PRO_HOST/ADMIN-only; a Specialist opening Profile sees this section
            // skipped entirely rather than a duplicate summary.
            if (user.role != UserRole.SPECIALIST) {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProSectionHeader(
                        title = when (user.role) {
                            UserRole.SPECIALIST -> "Practitioner Rental Activity"
                            UserRole.PRO_HOST -> "Host Performance & Yield"
                            UserRole.ADMIN -> "Central Platform Governance"
                        },
                        subtitle = when (user.role) {
                            UserRole.SPECIALIST -> "Active leases and pending bookings"
                            UserRole.PRO_HOST -> "Managed spaces, incoming tenant inquiries, and MRR yield"
                            UserRole.ADMIN -> "System spaces, cloud sync status, and transaction integrity"
                        },
                        icon = Icons.Default.Analytics,
                        trailingContent = {
                            ProStatusBadge(
                                type = if (user.isVerified) ProBadgeType.CUSTOM_SUCCESS else ProBadgeType.CUSTOM_WARNING,
                                customText = if (user.isVerified) "Verified Status" else "Pending Review"
                            )
                        }
                    )

                    when (user.role) {
                        UserRole.SPECIALIST -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProMetricTile(
                                    title = "Active Leases",
                                    value = "$activeLeasesCount",
                                    subtitle = "Confirmed workspace slots",
                                    icon = Icons.Default.EventAvailable,
                                    iconTint = MaterialTheme.proColors.success,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Pending Requests",
                                    value = "$pendingApplicationsCount",
                                    subtitle = "Awaiting host approval",
                                    icon = Icons.Default.PendingActions,
                                    iconTint = MaterialTheme.proColors.warning,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProMetricTile(
                                    title = "Total Bookings",
                                    value = "${practitionerBookingsForStats.size}",
                                    subtitle = "All rental applications",
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Phone Status",
                                    value = if (user.isVerified) "Verified" else "Unverified",
                                    subtitle = "SMS Verification Status",
                                    icon = Icons.Default.VerifiedUser,
                                    iconTint = MaterialTheme.proColors.success,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        UserRole.PRO_HOST -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProMetricTile(
                                    title = "Active Listings",
                                    value = "${ownerActiveListings.size}",
                                    subtitle = "Commercial units live",
                                    icon = Icons.Default.HomeWork,
                                    iconTint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Tenant Inquiries",
                                    value = "${ownerIncomingRequests.size}",
                                    subtitle = "Applications received",
                                    icon = Icons.Default.Inbox,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProMetricTile(
                                    title = "Potential Yield",
                                    value = "$${estimatedYieldUsd.toInt()} USD",
                                    subtitle = "Monthly gross MRR",
                                    icon = Icons.Default.AttachMoney,
                                    iconTint = MaterialTheme.proColors.success,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "ProHost Premium",
                                    value = ownerActivePlanId?.let { com.example.data.billing.PlayCatalog.planBadge(it) } ?: "No Plan",
                                    subtitle = ownerActivePlanId?.let { com.example.data.billing.PlayCatalog.planLabel(it) } ?: "No active subscription",
                                    icon = Icons.Default.Payment,
                                    iconTint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        UserRole.ADMIN -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProMetricTile(
                                    title = "Catalog Spaces",
                                    value = "${allSpacesList.size}",
                                    subtitle = "All active listings in Lebanon",
                                    icon = Icons.Default.Layers,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Governorates",
                                    value = "${Governorate.entries.size}",
                                    subtitle = "National coverage",
                                    icon = Icons.Default.Map,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ProMetricTile(
                                    title = "Firestore Sync",
                                    value = "Live",
                                    subtitle = "Real-time bridge active",
                                    icon = Icons.Default.CloudDone,
                                    iconTint = MaterialTheme.proColors.success,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Published",
                                    value = "${allSpacesList.count { it.status == ListingStatus.ACTIVE }}",
                                    subtitle = "Live listings",
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

            // =========================================================================
            // 5. ROLE-SPECIFIC ACTIVITY CARDS (BOOKINGS / LEASES)
            // =========================================================================
            if (user.role == UserRole.SPECIALIST) {
                val practitionerBookings by viewModel.practitionerBookings.collectAsState()
                val spaces by viewModel.spaces.collectAsState()

                ProSurfaceCard(
                    modifier = Modifier.shadow(2.dp, MaterialTheme.shapes.large)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                Spacer(modifier = Modifier.width(6.dp))
                                ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "${practitionerBookings.size} total")
                            }
                        }

                        if (practitionerBookings.isEmpty()) {
                            ProEmptyState(
                                title = "No Active Workspace Leases",
                                description = "Explore available clinics, studios, and executive suites from Discovery / Map to submit direct rental requests.",
                                icon = Icons.Default.EventAvailable,
                                modifier = Modifier.padding(vertical = Spacing.sm)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Pending Host")
                                                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "Declined")
                                                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Cancelled")
                                                }
                                            }

                                            val chosenDaysStr = if (req.selectedDays.isNotEmpty()) {
                                                req.selectedDays.joinToString(", ")
                                            } else {
                                                req.formula.daysOfWeek.joinToString(", ")
                                            }
                                            val chosenHoursStr = if (req.selectedStartHour.isNotBlank() && req.selectedEndHour.isNotBlank()) {
                                                "${req.selectedStartHour} - ${req.selectedEndHour}"
                                            } else {
                                                "${req.formula.startHour} - ${req.formula.endHour}"
                                            }
                                            val shiftDetail = if (req.selectedShift.isNotBlank()) " (${req.selectedShift})" else ""

                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Assignment,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Formula: ${req.formula.type.displayName} • $chosenDaysStr @ $chosenHoursStr$shiftDetail",
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
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Starting: ${req.startDate} (${req.durationMonths} mo term) •" +
                                                        " Total: $${req.totalAmountUsd.toInt()} USD",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (targetSpace != null) {
                                                    Button(
                                                        onClick = {
                                                            viewModel.launchWhatsAppInquiry(context, targetSpace, req.formula, req)
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = MaterialTheme.shapes.small,
                                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
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
                                                            "WhatsApp Host",
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
                                                        Text("Cancel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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

            // =========================================================================
            // 6. EDIT PROFILE & MEMBER DETAILS FORM
            // =========================================================================
            ProSurfaceCard(
                modifier = Modifier.bringIntoViewRequester(profileDetailsRequester)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProSectionHeader(
                        title = when (user.role) {
                            UserRole.SPECIALIST -> "Practitioner Details"
                            UserRole.PRO_HOST -> "Host Business Details"
                            UserRole.ADMIN -> "Super Administrator Identity"
                        },
                        subtitle = "Contact & verification info",
                        icon = Icons.Default.Badge
                    )

                    var pendingProfilePicUri by remember { mutableStateOf<android.net.Uri?>(null) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfilePicturePickerField(
                            pictureUri = pendingProfilePicUri,
                            existingUrl = user.profilePictureUrl,
                            onPictureSelected = { pendingProfilePicUri = it }
                        )
                        Column {
                            Text("Profile Picture", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (user.profilePictureUrl != null || pendingProfilePicUri != null) "Tap to replace" else "Tap to add a photo",
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
                        label = "Full Name",
                        leadingIcon = Icons.Default.Person,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    InputField(
                        value = specialty,
                        onValueChange = { specialty = it },
                        label = when (user.role) {
                            UserRole.SPECIALIST -> "Profession / Job Title (Optional)"
                            UserRole.PRO_HOST -> "Host Category (Commercial Real Estate, Coworking, Clinic)"
                            UserRole.ADMIN -> "Super Administrator Role & Clearance"
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
                        label = "WhatsApp Contact Number",
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
                        label = "Governorate / Area",
                        leadingIcon = Icons.Default.LocationOn,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    InputField(
                        value = city,
                        onValueChange = { city = it },
                        label = "City",
                        leadingIcon = Icons.Default.LocationCity,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    ProPrimaryButton(
                        text = if (isSavingProfile) "Saving..." else "Save Profile Changes",
                        enabled = !isSavingProfile,
                        isLoading = isSavingProfile,
                        onClick = {
                            isSavingProfile = true
                            profilePicUploadError = null
                            coroutineScope.launch {
                                try {
                                    var profilePictureUrl: String? = null
                                    val localPicUri = pendingProfilePicUri
                                    var pictureUploadFailed = false
                                    if (localPicUri != null) {
                                        val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                                        profilePictureUrl = storageService.uploadProfilePicture(user.id, localPicUri)
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
                                        !success -> Toast.makeText(context, "Failed to update profile — please try again", Toast.LENGTH_SHORT).show()
                                        pictureUploadFailed -> {
                                            profilePicUploadError = "Profile saved, but the new photo couldn't be uploaded. Check your connection and try again."
                                            Toast.makeText(context, "Profile saved — photo upload failed, please retry", Toast.LENGTH_LONG).show()
                                        }
                                        else -> Toast.makeText(context, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    isSavingProfile = false
                                    Toast.makeText(context, "An error occurred — please try again", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        icon = Icons.Default.Save,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // =========================================================================
            // 7. COMPACT APP VERSION & UPDATE CHECK
            // =========================================================================
            var updateState by remember { mutableStateOf(UpdateState.UP_TO_DATE) }
            LaunchedEffect(inAppUpdateManager) {
                inAppUpdateManager?.updateState?.collect { updateState = it }
            }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "v${com.example.BuildConfig.VERSION_NAME} • ${when (updateState) {
                            UpdateState.CHECKING -> "Checking for updates…"
                            UpdateState.UPDATE_AVAILABLE_FLEXIBLE, UpdateState.UPDATE_AVAILABLE_IMMEDIATE -> "Update available"
                            UpdateState.DOWNLOADING -> "Downloading update…"
                            UpdateState.DOWNLOADED -> "Ready to install"
                            UpdateState.FAILED -> "Update check failed"
                            else -> "ProHost is up to date"
                        }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (updateState == UpdateState.DOWNLOADED) {
                        Button(
                            onClick = { inAppUpdateManager?.completeUpdate() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.proColors.success,
                                contentColor = MaterialTheme.proColors.onSuccess
                            ),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Install", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                if (inAppUpdateManager != null) {
                                    inAppUpdateManager.checkForAppUpdate(preferImmediate = false)
                                    Toast.makeText(context, "Checking Google Play for updates...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ProHost v${com.example.BuildConfig.VERSION_NAME} is up to date", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Check Update", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                val settingsActivity = androidx.activity.compose.LocalActivity.current
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProSectionHeader(
                            title = "ProHost Premium",
                            subtitle = user.ownerPackageId?.let { com.example.data.billing.PlayCatalog.planLabel(it) }
                                ?: "Not subscribed",
                            icon = Icons.Default.WorkspacePremium
                        )
                        TextButton(
                            onClick = {
                                settingsActivity?.let {
                                    viewModel.openManageSubscriptions(it, com.example.data.billing.PlayCatalog.PRODUCT_ID)
                                }
                            },
                            enabled = settingsActivity != null
                        ) {
                            Text("Manage subscription in Google Play", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProSectionHeader(
                        title = "Privacy",
                        subtitle = "Choose what usage data you share",
                        icon = Icons.Default.Shield
                    )
                    com.example.ui.components.AnalyticsConsentToggle()
                }
            }

            // =========================================================================
            // 9. DANGER ZONE — PERMANENT ACCOUNT DELETION
            // =========================================================================
            var showDeleteConfirmation by remember { mutableStateOf(false) }
            var isDeletingAccount by remember { mutableStateOf(false) }

            ProSurfaceCard(
                modifier = Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    MaterialTheme.shapes.large
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Delete Account",
                        subtitle = if (user.role == UserRole.PRO_HOST)
                            "Deletes your profile, documents, and listings — cannot be undone"
                        else
                            "Deletes your profile and documents — cannot be undone",
                        icon = Icons.Default.DeleteForever
                    )
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !isDeletingAccount,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDeletingAccount) "Deleting..." else "Delete My Account")
                    }
                }
            }

            if (showDeleteConfirmation) {
                ProHostDialog(
                    onDismissRequest = { if (!isDeletingAccount) showDeleteConfirmation = false },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Delete your account?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                (if (user.role == UserRole.PRO_HOST)
                                    "This permanently deletes your profile, uploaded ID document, and every listing you own. "
                                else
                                    "This permanently deletes your profile and uploaded ID document. ") +
                                    "Your booking history stays on file for the other party's records, but you won't be able to sign back in."
                            )
                            if (user.ownerPackageId != null) {
                                val activity = androidx.activity.compose.LocalActivity.current
                                Text(
                                    "Deleting your account does not cancel your Google Play subscription. " +
                                        "Cancel it in Google Play first to stop future charges.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (activity != null) {
                                    TextButton(onClick = { viewModel.openManageSubscriptions(activity, user.ownerPackageId) }) {
                                        Text("Manage subscription in Google Play")
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
                                        Toast.makeText(
                                            context,
                                            com.example.util.friendlyErrorMessage(
                                                error,
                                                "Couldn't delete your account. Please try again, or contact support."
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    // On success the screen unmounts on its own: currentUser
                                    // becomes null once repository.logout() runs inside
                                    // viewModel.deleteAccount(), and ProHostNavGraph renders
                                    // LoginAuthScreen for a null currentUser.
                                }
                            }
                        ) {
                            Text("Delete Permanently", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }, enabled = !isDeletingAccount) {
                            Text("Cancel")
                        }
                    }
                )
            }

            pendingCancelRequest?.let { target ->
                ProHostDialog(
                    onDismissRequest = { pendingCancelRequest = null },
                    icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Cancel this request?") },
                    text = { Text("Your rental request for \"${target.spaceTitle}\" will be withdrawn. The host will no longer be able to accept it.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.cancelBookingRequest(target.id, context)
                            pendingCancelRequest = null
                        }) {
                            Text("Cancel Request", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingCancelRequest = null }) {
                            Text("Keep Request")
                        }
                    }
                )
            }
        }
    }
}
