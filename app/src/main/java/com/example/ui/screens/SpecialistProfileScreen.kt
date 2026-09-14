package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
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

// Published as ProHost's real support/data-privacy contact on the public
// privacy page (public/privacy.html) — kept here as the single source of
// truth for the in-app "Contact Support" action below, rather than a second
// hardcoded copy that could drift from the published one.
private const val SUPPORT_EMAIL = "geo.elnajjar@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
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
    var idDocState by remember { mutableStateOf(com.example.ui.components.DocumentPickerState()) }
    var isUploadingIdDoc by remember { mutableStateOf(false) }
    // uploadAndGetUrl (FirebaseStorageService) already catches its own failures and
    // returns null rather than throwing — these surface that instead of leaving the
    // picker looking like it silently did nothing, same pattern as
    // CreateListingDialog's photoUploadError/ownershipUploadError.
    var idDocUploadError by remember { mutableStateOf<String?>(null) }
    var profilePicUploadError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient)
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
            // 1. WELCOME BOX WITH REAL, LATEST-UPDATE-DRIVEN STATUS (SPECIALIST)
            // =========================================================================
            // The identity card + Sign Out action that used to live here moved to the
            // side drawer (DrawerIdentityCard) — every role now sees it there instead
            // of duplicated in a different visual style on this page.
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
                                Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
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
            val pricingState by viewModel.pricingState.collectAsState()
            val packagePlans by viewModel.packagePlans.collectAsState()
            val practitionerBookingsForStats by viewModel.practitionerBookings.collectAsState()
            val ownerIncomingRequests by viewModel.ownerIncomingRequests.collectAsState()

            val activeLeasesCount = practitionerBookingsForStats.count { it.status == BookingRequestStatus.ACCEPTED }
            val pendingApplicationsCount = practitionerBookingsForStats.count { it.status == BookingRequestStatus.PENDING }
            val ownerActiveListings = if (ownerSpaces.isNotEmpty()) ownerSpaces else if (user.role == UserRole.ADMIN) allSpacesList else emptyList()
            val estimatedYieldUsd = ownerActiveListings.sumOf { it.baseMonthlyRateUsd }
            // Real per-user package lookup (SubscriptionRenewalDialog.kt's established pattern) —
            // this used to read the global, admin-wide pricingState.monthlySubscriptionFeeUsd, which
            // showed a live legacy fee (e.g. "$2.50") even for a user with no active plan at all.
            val ownerPackagePlan = user.ownerPackageId?.let { packagePlans.packages[it] }
            val ownerPackageExpired = user.ownerPackageExpiryMillis?.let { it <= System.currentTimeMillis() } ?: false
            val ownerActivePackagePlan = if (ownerPackageExpired) null else ownerPackagePlan

            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProSectionHeader(
                        title = when (user.role) {
                            UserRole.SPECIALIST -> "Practitioner Rental Activity"
                            UserRole.PRO_HOST -> "Host Performance & Yield"
                            UserRole.ADMIN -> "Central Platform Governance"
                        },
                        subtitle = when (user.role) {
                            UserRole.SPECIALIST -> "Active leases, pending bookings, and Syndicate standing"
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
                                    iconTint = FreshGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Pending Requests",
                                    value = "$pendingApplicationsCount",
                                    subtitle = "Awaiting host approval",
                                    icon = Icons.Default.PendingActions,
                                    iconTint = BrightOrange,
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
                                    iconTint = VibrantBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Phone Status",
                                    value = if (user.isVerified) "Verified" else "Unverified",
                                    subtitle = "Firebase SMS verification",
                                    icon = Icons.Default.VerifiedUser,
                                    iconTint = FreshGreen,
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
                                    iconTint = CarnationOrange,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Tenant Inquiries",
                                    value = "${ownerIncomingRequests.size}",
                                    subtitle = "Applications received",
                                    icon = Icons.Default.Inbox,
                                    iconTint = VibrantBlue,
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
                                    iconTint = FreshGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Active Package",
                                    value = ownerActivePackagePlan?.let { "$${it.priceUsd.toInt()} USD" } ?: "No Plan",
                                    subtitle = ownerActivePackagePlan?.let { "${it.name} · ${it.validityDays}d" } ?: "No active package",
                                    icon = Icons.Default.Payment,
                                    iconTint = CarnationOrangeDark,
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
                                    iconTint = OxfordBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Governorates",
                                    value = "${Governorate.entries.size}",
                                    subtitle = "National coverage",
                                    icon = Icons.Default.Map,
                                    iconTint = VibrantBlue,
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
                                    iconTint = FreshGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ProMetricTile(
                                    title = "Fee Corridor",
                                    value = "$${pricingState.monthlySubscriptionFeeUsd} USD",
                                    subtitle = "Host subscription rate",
                                    icon = Icons.Default.Security,
                                    iconTint = AmberWarning,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 4. SPECIALIST PERFORMANCE — the 3 stat boxes formerly pinned to the top
            // of My Bookings (Active Leases / This Month Spent / Pending Host Reply),
            // moved here per the user's request. Shown for every SPECIALIST; a
            // PRO_HOST who also books space elsewhere as a specialist sees this
            // section too, below their own Host Performance section above.
            // =========================================================================
            if (user.role == UserRole.SPECIALIST || user.role == UserRole.PRO_HOST) {
                val allBookingRequests by viewModel.bookingRequests.collectAsState()
                // Same 3-condition + ADMIN-passthrough filter MyBookingsScreen used for
                // this same data, so the numbers stay consistent between screens.
                val userOwnBookings = remember(allBookingRequests, user) {
                    if (user.role == UserRole.ADMIN) {
                        allBookingRequests
                    } else {
                        allBookingRequests.filter {
                            it.practitionerId == user.id ||
                                it.practitionerEmail.equals(user.email, ignoreCase = true) ||
                                it.practitionerName.contains(user.fullName, ignoreCase = true)
                        }
                    }
                }
                val specialistActiveLeases = userOwnBookings.count { it.status == BookingRequestStatus.ACCEPTED }
                val specialistPendingCount = userOwnBookings.count { it.status == BookingRequestStatus.PENDING }
                // Real current-calendar-month spend, same logic MyBookingsScreen used.
                val specialistThisMonthSpendUsd = remember(userOwnBookings) {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val now = java.util.Calendar.getInstance()
                    userOwnBookings.filter { it.status == BookingRequestStatus.ACCEPTED }.sumOf { booking ->
                        try {
                            val start = java.util.Calendar.getInstance().apply {
                                time = dateFormat.parse(booking.startDate) ?: return@sumOf 0.0
                            }
                            val end = (start.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, booking.durationMonths) }
                            if (!now.before(start) && now.before(end)) booking.formula.rateUsd else 0.0
                        } catch (e: Exception) {
                            0.0
                        }
                    }
                }

                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProSectionHeader(
                            title = "Specialist Performance",
                            subtitle = "Your own leases, spend, and pending requests as a renting specialist",
                            icon = Icons.Default.EventAvailable
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProMetricTile(
                                title = "Active Leases",
                                value = "$specialistActiveLeases",
                                subtitle = "Confirmed workspace slots",
                                icon = Icons.Default.Verified,
                                iconTint = OxfordBlue,
                                modifier = Modifier.weight(1f)
                            )
                            ProMetricTile(
                                title = "This Month",
                                value = "$${String.format(java.util.Locale.US, "%.0f", specialistThisMonthSpendUsd)}",
                                subtitle = "Spent this calendar month",
                                icon = Icons.Default.AttachMoney,
                                iconTint = FreshGreen,
                                modifier = Modifier.weight(1f)
                            )
                            ProMetricTile(
                                title = "Pending Host",
                                value = "$specialistPendingCount",
                                subtitle = "Awaiting host reply",
                                icon = Icons.Default.Schedule,
                                iconTint = BrightOrange,
                                modifier = Modifier.weight(1f)
                            )
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
                        ProSectionHeader(
                            title = "My Rented Workspaces & Schedules",
                            subtitle = "Confirmed schedules, pending rental requests, and leases",
                            icon = Icons.Default.EventAvailable,
                            trailingContent = {
                                if (practitionerBookings.isNotEmpty()) {
                                    ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "${practitionerBookings.size} total")
                                }
                            }
                        )

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
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when (req.status) {
                                                BookingRequestStatus.ACCEPTED -> StatusInfoContainer
                                                BookingRequestStatus.PENDING -> StatusWarningContainer
                                                BookingRequestStatus.REJECTED -> StatusErrorContainer
                                                BookingRequestStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            when (req.status) {
                                                BookingRequestStatus.ACCEPTED -> StatusInfo
                                                BookingRequestStatus.PENDING -> StatusWarning
                                                BookingRequestStatus.REJECTED -> StatusError
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

                                            val chosenDaysStr = if (req.selectedDays.isNotEmpty()) req.selectedDays.joinToString(", ") else req.formula.daysOfWeek.joinToString(", ")
                                            val chosenHoursStr = if (req.selectedStartHour.isNotBlank() && req.selectedEndHour.isNotBlank()) "${req.selectedStartHour} - ${req.selectedEndHour}" else "${req.formula.startHour} - ${req.formula.endHour}"
                                            val shiftDetail = if (req.selectedShift.isNotBlank()) " (${req.selectedShift})" else ""

                                            Text(
                                                text = "📑 Formula: ${req.formula.type.displayName} • $chosenDaysStr @ $chosenHoursStr$shiftDetail",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "🗓️ Starting: ${req.startDate} (${req.durationMonths} mo term) • Total: $${req.totalAmountUsd.toInt()} USD",
                                                style = MaterialTheme.typography.bodySmall
                                            )

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
                                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                                        contentPadding = PaddingValues(vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                                        Text("WhatsApp Host", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
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
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProSectionHeader(
                        title = when (user.role) {
                            UserRole.SPECIALIST -> "Practitioner Details"
                            UserRole.PRO_HOST -> "Host Business Details"
                            UserRole.ADMIN -> "Super Administrator Identity"
                        },
                        subtitle = "Ensure your WhatsApp booking contact is up to date",
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
                    if (profilePicUploadError != null) {
                        Text(
                            profilePicUploadError!!,
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

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (user.idDocumentUrl != null) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (user.idDocumentUrl != null) StatusSuccess else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (user.idDocumentUrl != null) "ID document on file" else "No ID document on file",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Real upload control — idDocumentUrl used to only ever be set once,
                    // by the registration Cloud Function; there was no way for any
                    // account to set/replace it afterward, which permanently stuck any
                    // account that skips registration (most notably an Admin created via
                    // bootstrapSuperAdmin/grantAdminRole) at "No ID document on file"
                    // with no recourse. firestore.rules already permits this self-write.
                    com.example.ui.components.DocumentPickerField(
                        label = if (user.idDocumentUrl != null) "Replace ID Document" else "Upload ID Document",
                        helperText = "PDF, JPG, or PNG",
                        state = idDocState,
                        onStateChanged = { newState ->
                            idDocState = newState
                            val uri = newState.uri
                            if (uri != null) {
                                coroutineScope.launch {
                                    isUploadingIdDoc = true
                                    idDocUploadError = null
                                    val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                                    val ext = newState.fileName?.substringAfterLast('.', "pdf") ?: "pdf"
                                    val url = storageService.uploadIdDocument(user.id, uri, ext)
                                    if (url != null) {
                                        if (!viewModel.updateIdDocument(url)) {
                                            idDocUploadError = "Uploaded, but couldn't save it to your profile. Please try again."
                                        }
                                    } else {
                                        idDocUploadError = "Couldn't upload that document. Check your connection and try again."
                                    }
                                    isUploadingIdDoc = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        required = false
                    )
                    if (isUploadingIdDoc) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (idDocUploadError != null) {
                        Text(
                            idDocUploadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    ProPrimaryButton(
                        text = if (isSavingProfile) "Saving..." else "Save Profile Changes",
                        enabled = !isSavingProfile,
                        isLoading = isSavingProfile,
                        onClick = {
                            isSavingProfile = true
                            profilePicUploadError = null
                            coroutineScope.launch {
                                var profilePictureUrl: String? = null
                                val localPicUri = pendingProfilePicUri
                                var pictureUploadFailed = false
                                if (localPicUri != null) {
                                    val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                                    val mime = context.contentResolver.getType(localPicUri)
                                    val ext = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
                                    profilePictureUrl = storageService.uploadProfilePicture(user.id, localPicUri, ext)
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
                            }
                        },
                        icon = Icons.Default.Save,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // =========================================================================
            // 7. GOOGLE PLAY IN-APP UPDATES & APP INTEGRITY
            // =========================================================================
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "App Version & In-App Updates",
                        subtitle = "Google Play Core update management & release integrity",
                        icon = Icons.Default.SystemUpdate
                    )

                    val updateState by (inAppUpdateManager?.updateState?.collectAsState() ?: remember { mutableStateOf(UpdateState.UP_TO_DATE) })
                    val downloadProgress by (inAppUpdateManager?.downloadProgress?.collectAsState() ?: remember { mutableStateOf(0f) })

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Installed Version: 1.0.0 (Build 1)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when (updateState) {
                                    UpdateState.IDLE -> "Play Store check idle"
                                    UpdateState.CHECKING -> "Checking Google Play Store..."
                                    UpdateState.UPDATE_AVAILABLE_FLEXIBLE -> "New release available on Play Store"
                                    UpdateState.UPDATE_AVAILABLE_IMMEDIATE -> "Mandatory update available"
                                    UpdateState.DOWNLOADING -> "Downloading: ${(downloadProgress * 100).toInt()}%"
                                    UpdateState.DOWNLOADED -> "Update downloaded! Ready to install."
                                    UpdateState.FAILED -> "Update failed to download"
                                    UpdateState.UP_TO_DATE -> "ProHost is up to date"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (updateState) {
                                    UpdateState.DOWNLOADED -> FreshGreen
                                    UpdateState.UPDATE_AVAILABLE_FLEXIBLE, UpdateState.UPDATE_AVAILABLE_IMMEDIATE -> BrightOrange
                                    UpdateState.FAILED -> CrimsonRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        if (updateState == UpdateState.DOWNLOADED) {
                            Button(
                                onClick = { inAppUpdateManager?.completeUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = FreshGreen),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Restart & Install", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (inAppUpdateManager != null) {
                                        inAppUpdateManager.checkForAppUpdate(preferImmediate = false)
                                        Toast.makeText(context, "Checking Google Play for updates...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "ProHost is up to date (Version 1.0.0)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Check Updates", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 8. CONTACT SUPPORT — the in-app support channel this screen's own Legal
            // documents (Privacy Policy Section 11) already claim exists. Opens the
            // device's own email app addressed to ProHost's published support contact
            // (the same geo.elnajjar@gmail.com address already listed as "ProHost Data
            // Privacy & Support" on the public privacy page) — this app has no backend
            // ticketing/chat system, so a real mail composer is the honest channel to
            // offer rather than inventing one that doesn't exist.
            // =========================================================================
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Contact Support",
                        subtitle = "Questions, a data request, or something not working right",
                        icon = Icons.AutoMirrored.Filled.Help
                    )
                    OutlinedButton(
                        onClick = {
                            val subject = Uri.encode("ProHost Support — ${user.role.name} account")
                            val body = Uri.encode("Account: ${user.fullName} (${user.email})\nUser ID: ${user.id}\n\nDescribe your question or issue below:\n")
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=$subject&body=$body")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found — you can also reach us at $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Email Support ($SUPPORT_EMAIL)")
                    }
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
                            "Permanently removes your profile, uploaded documents, and every listing you own"
                        else
                            "Permanently removes your profile and uploaded documents",
                        icon = Icons.Default.DeleteForever
                    )
                    Text(
                        text = "This cannot be undone. Your booking history stays on file for the other party's records, but you will no longer be able to sign back in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                AlertDialog(
                    onDismissRequest = { if (!isDeletingAccount) showDeleteConfirmation = false },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Delete your account?") },
                    text = {
                        Text(
                            if (user.role == UserRole.PRO_HOST)
                                "This permanently deletes your profile, uploaded ID document, and every listing you own. This cannot be undone."
                            else
                                "This permanently deletes your profile and uploaded ID document. This cannot be undone."
                        )
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
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            "Couldn't delete your account — please check your connection and try again.",
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

            if (pendingCancelRequest != null) {
                val target = pendingCancelRequest!!
                AlertDialog(
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
