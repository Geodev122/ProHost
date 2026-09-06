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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.viewmodel.ProSpaceViewModel
import com.example.util.InAppUpdateManager
import com.example.util.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialistProfileScreen(
    viewModel: ProSpaceViewModel,
    inAppUpdateManager: InAppUpdateManager? = null,
    onSignOut: () -> Unit = {},
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
    var selectedCountry by remember(user) { mutableStateOf(findCountryByName(user.country)) }
    var governorateArea by remember(user) { mutableStateOf(user.governorate) }
    var city by remember(user) { mutableStateOf(user.city) }

    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    // Role-specific theme accents
    val primaryAccent = when (user.role) {
        UserRole.ADMIN -> AmberWarning
        UserRole.PRO_HOST -> CarnationOrange
        UserRole.SPECIALIST -> OxfordBlue
    }

    val heroGradient = when (user.role) {
        UserRole.ADMIN -> Brush.linearGradient(listOf(OxfordBlueDark, OxfordBlue, CoolGrayDark))
        UserRole.PRO_HOST -> Brush.linearGradient(listOf(OxfordBlue, CarnationOrangeDark.copy(alpha = 0.85f), OxfordBlueDark))
        UserRole.SPECIALIST -> Brush.linearGradient(listOf(OxfordBlueDark, OxfordBlue, VibrantBlue.copy(alpha = 0.7f)))
    }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 1. HERO PROFILE IDENTITY HEADER CARD (ROLE-TAILORED)
            // =========================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(heroGradient)
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Top row: Role Pill + Sign Out
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = when (user.role) {
                                    UserRole.ADMIN -> AmberWarning.copy(alpha = 0.25f)
                                    UserRole.PRO_HOST -> CarnationOrange.copy(alpha = 0.25f)
                                    UserRole.SPECIALIST -> VibrantBlue.copy(alpha = 0.25f)
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (user.role) {
                                            UserRole.ADMIN -> Icons.Default.Shield
                                            UserRole.PRO_HOST -> Icons.Default.HomeWork
                                            UserRole.SPECIALIST -> Icons.Default.VerifiedUser
                                        },
                                        contentDescription = null,
                                        tint = when (user.role) {
                                            UserRole.ADMIN -> AmberWarning
                                            UserRole.PRO_HOST -> CarnationOrangeLight
                                            UserRole.SPECIALIST -> Color.White
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when (user.role) {
                                            UserRole.ADMIN -> "Super Administrator Node"
                                            UserRole.PRO_HOST -> "Verified Space Host"
                                            UserRole.SPECIALIST -> "Practitioner / Specialist"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = { showSignOutConfirmDialog = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign Out", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Middle row: Avatar + Name + Credentials
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar with glowing ring
                            Box(modifier = Modifier.size(64.dp)) {
                                Surface(
                                    color = primaryAccent,
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxSize(),
                                    border = BorderStroke(2.5.dp, Color.White.copy(alpha = 0.9f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user.fullName.split(" ")
                                                .filter { it.isNotBlank() }
                                                .take(2)
                                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                                .joinToString("")
                                                .ifEmpty { "PS" },
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp
                                        )
                                    }
                                }
                                if (user.isVerified) {
                                    Surface(
                                        color = FreshGreen,
                                        shape = CircleShape,
                                        border = BorderStroke(2.dp, OxfordBlueDark),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Verified",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = user.fullName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (user.isVerified) {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Verified Member",
                                            tint = if (user.role == UserRole.ADMIN) AmberWarning else FreshGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = user.specialty.ifBlank { user.role.displayName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        // Bottom Meta Row: Location + Member ID + Phone-Verified Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                                Text(
                                    text = user.city.ifBlank { user.governorate.ifBlank { user.country } },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                                Text(
                                    text = "ID: ${user.id.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (user.isVerified) "🛡️ Phone Verified" else "Phone Unverified",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. ACTIVE ROLE INDICATOR (informational only — a role change now only ever
            // happens server-side: grantAdminRole for Admin grants, or grantEntitlement()
            // promoting SPECIALIST to PRO_HOST the moment a package/listing payment settles.
            // This used to be a tap-to-switch control that let any signed-in user instantly
            // become Admin with no server check.)
            // =========================================================================
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Account Role",
                        subtitle = "Your verified role on ProHost",
                        icon = Icons.Default.SwapHoriz
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRole.entries.forEach { role ->
                            val isSelected = user.role == role
                            val roleColor = when (role) {
                                UserRole.SPECIALIST -> OxfordBlue
                                UserRole.PRO_HOST -> CarnationOrange
                                UserRole.ADMIN -> AmberWarning
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                color = if (isSelected) roleColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) BorderStroke(1.5.dp, roleColor) else null
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = when (role) {
                                            UserRole.SPECIALIST -> Icons.Default.Work
                                            UserRole.PRO_HOST -> Icons.Default.HomeWork
                                            UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) roleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (role) {
                                            UserRole.SPECIALIST -> "Practitioner"
                                            UserRole.PRO_HOST -> "Host / Owner"
                                            UserRole.ADMIN -> "Super Admin"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) roleColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
            val practitionerBookingsForStats by viewModel.practitionerBookings.collectAsState()
            val ownerIncomingRequests by viewModel.ownerIncomingRequests.collectAsState()

            val activeLeasesCount = practitionerBookingsForStats.count { it.status == BookingRequestStatus.ACCEPTED }
            val pendingApplicationsCount = practitionerBookingsForStats.count { it.status == BookingRequestStatus.PENDING }
            val ownerActiveListings = if (ownerSpaces.isNotEmpty()) ownerSpaces else if (user.role == UserRole.ADMIN) allSpacesList else emptyList()
            val estimatedYieldUsd = ownerActiveListings.sumOf { it.baseMonthlyRateUsd }

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
                                    title = "30-Day Listing Plan",
                                    value = "$${pricingState.monthlySubscriptionFeeUsd} USD",
                                    subtitle = "Whish Money direct rate",
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
            // 4. ROLE-TAILORED HUBS & ACCREDITATION SECTIONS
            // =========================================================================

            if (user.role == UserRole.SPECIALIST) {
                // SPECIALIST PRACTITIONER FRIENDLY WORKSPACE HUB
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProSectionHeader(
                            title = "Practitioner Workspace & Booking Hub",
                            subtitle = "Manage clinical and consulting room rentals across Lebanon with real-time availability",
                            icon = Icons.Default.MedicalServices,
                            trailingContent = {
                                ProStatusBadge(
                                    type = if (user.isVerified) ProBadgeType.CUSTOM_SUCCESS else ProBadgeType.CUSTOM_WARNING,
                                    customText = if (user.isVerified) "Syndicate Verified" else "Verification Pending"
                                )
                            }
                        )

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Welcome back, ${user.fullName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = listOf(user.city, user.governorate, user.country).filter { it.isNotBlank() }.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "You have $activeLeasesCount active workspace leases and $pendingApplicationsCount pending applications. All bookings factor in live operating hours, accepted tenant schedules, and host blackout maintenance slots.",
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
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Find Workspaces", style = MaterialTheme.typography.labelMedium)
                                    }

                                    OutlinedButton(
                                        onClick = { onNavigateToTab("pro_rentals") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View Bookings", style = MaterialTheme.typography.labelMedium)
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
                val practitionerBookings by viewModel.practitionerBookings.collectAsState()
                val spaces by viewModel.spaces.collectAsState()

                ProSurfaceCard {
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
                                modifier = Modifier.padding(vertical = 8.dp)
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
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                                        contentPadding = PaddingValues(vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("WhatsApp Host", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                if (req.status == BookingRequestStatus.PENDING) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.cancelBookingRequest(req.id, context) },
                                                        modifier = Modifier.weight(0.6f),
                                                        shape = RoundedCornerShape(8.dp),
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

                    InputField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "WhatsApp Contact Number (+961 ...)",
                        leadingIcon = Icons.Default.Phone,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
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

                    Spacer(modifier = Modifier.height(4.dp))

                    CustomButton(
                        text = "Save Profile Changes",
                        onClick = {
                            coroutineScope.launch {
                                var profilePictureUrl: String? = null
                                val localPicUri = pendingProfilePicUri
                                if (localPicUri != null) {
                                    val storageService = com.example.data.storage.FirebaseStorageService.getInstance()
                                    val mime = context.contentResolver.getType(localPicUri)
                                    val ext = mime?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
                                    profilePictureUrl = storageService.uploadProfilePicture(user.id, localPicUri, ext)
                                }
                                val success = viewModel.updateProfile(
                                    name, specialty, phone,
                                    selectedCountry.name, governorateArea, city,
                                    profilePictureUrl
                                )
                                Toast.makeText(
                                    context,
                                    if (success) "Profile Updated Successfully!" else "Failed to update profile — please try again",
                                    Toast.LENGTH_SHORT
                                ).show()
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
                                    UpdateState.UP_TO_DATE -> "ProSpace is up to date"
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
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Restart & Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (inAppUpdateManager != null) {
                                        inAppUpdateManager.checkForAppUpdate(preferImmediate = false)
                                        Toast.makeText(context, "Checking Google Play for updates...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "ProSpace is up to date (Version 1.0.0)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Updates", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS: UPLOAD, PREVIEW, AND SIGN OUT CONFIRMATION
    // =========================================================================

    // Sign Out Confirmation Dialog
    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = { Text("Sign Out of ProSpace", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out of your account (${user.email})?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        viewModel.logout()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
