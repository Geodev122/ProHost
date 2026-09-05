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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    val userDocuments by viewModel.currentUserDocuments.collectAsState()

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
    var affiliation by remember(user) { mutableStateOf(user.affiliation) }
    var syndicateNumber by remember(user) { mutableStateOf(user.syndicateNumber) }
    var selectedGov by remember(user) { mutableStateOf(user.governorate) }

    // Dialog States
    var showUploadDialog by remember { mutableStateOf(false) }
    var selectedDocTypeForUpload by remember { mutableStateOf<DocumentType?>(null) }
    var previewingDocument by remember { mutableStateOf<CredentialDocument?>(null) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    // Required Documents Calculation
    val requiredDocTypes = remember(user.role) {
        DocumentType.entries.filter { it.requiredFor.contains(user.role) }
    }
    val optionalDocTypes = remember(user.role) {
        DocumentType.entries.filter { !it.requiredFor.contains(user.role) }
    }

    val verifiedDocsCount = remember(userDocuments, requiredDocTypes) {
        requiredDocTypes.count { req -> userDocuments.any { it.type == req && it.status == DocumentStatus.VERIFIED } }
    }
    val uploadedDocsCount = remember(userDocuments, requiredDocTypes) {
        requiredDocTypes.count { req -> userDocuments.any { it.type == req && it.status != DocumentStatus.NOT_UPLOADED } }
    }
    val verificationProgress = remember(verifiedDocsCount, requiredDocTypes) {
        if (requiredDocTypes.isEmpty()) 1f else (verifiedDocsCount.toFloat() / requiredDocTypes.size.toFloat())
    }

    // Role-specific theme accents
    val primaryAccent = when (user.role) {
        UserRole.ADMIN -> AmberWarning
        UserRole.SPACE_OWNER -> CarnationOrange
        UserRole.PROFESSIONAL -> OxfordBlue
    }

    val heroGradient = when (user.role) {
        UserRole.ADMIN -> Brush.linearGradient(listOf(OxfordBlueDark, OxfordBlue, CoolGrayDark))
        UserRole.SPACE_OWNER -> Brush.linearGradient(listOf(OxfordBlue, CarnationOrangeDark.copy(alpha = 0.85f), OxfordBlueDark))
        UserRole.PROFESSIONAL -> Brush.linearGradient(listOf(OxfordBlueDark, OxfordBlue, VibrantBlue.copy(alpha = 0.7f)))
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
                                    UserRole.SPACE_OWNER -> CarnationOrange.copy(alpha = 0.25f)
                                    UserRole.PROFESSIONAL -> VibrantBlue.copy(alpha = 0.25f)
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
                                            UserRole.SPACE_OWNER -> Icons.Default.HomeWork
                                            UserRole.PROFESSIONAL -> Icons.Default.VerifiedUser
                                        },
                                        contentDescription = null,
                                        tint = when (user.role) {
                                            UserRole.ADMIN -> AmberWarning
                                            UserRole.SPACE_OWNER -> CarnationOrangeLight
                                            UserRole.PROFESSIONAL -> Color.White
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when (user.role) {
                                            UserRole.ADMIN -> "Super Administrator Node"
                                            UserRole.SPACE_OWNER -> "Verified Space Host"
                                            UserRole.PROFESSIONAL -> "Practitioner / Specialist"
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

                        // Bottom Meta Row: Governorate + Trust Index + Syndicate ID
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
                                    text = user.governorate.displayName.split(" ").first(),
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
                                    text = if (user.syndicateNumber.isNotBlank()) user.syndicateNumber else "ID: ${user.id.take(10)}",
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
                                    text = "🛡️ Trust: ${user.trustScore}%",
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
            // happens server-side, via Firebase Auth + the requestRoleUpgrade/
            // grantAdminRole Cloud Functions. This used to be a tap-to-switch control
            // that let any signed-in user instantly become Admin with no server check.)
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
                                UserRole.PROFESSIONAL -> OxfordBlue
                                UserRole.SPACE_OWNER -> CarnationOrange
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
                                            UserRole.PROFESSIONAL -> Icons.Default.Work
                                            UserRole.SPACE_OWNER -> Icons.Default.HomeWork
                                            UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) roleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (role) {
                                            UserRole.PROFESSIONAL -> "Practitioner"
                                            UserRole.SPACE_OWNER -> "Host / Owner"
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
                            UserRole.PROFESSIONAL -> "Practitioner Rental Activity"
                            UserRole.SPACE_OWNER -> "Host Performance & Yield"
                            UserRole.ADMIN -> "Central Platform Governance"
                        },
                        subtitle = when (user.role) {
                            UserRole.PROFESSIONAL -> "Active leases, pending bookings, and Syndicate standing"
                            UserRole.SPACE_OWNER -> "Managed spaces, incoming tenant inquiries, and MRR yield"
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
                        UserRole.PROFESSIONAL -> {
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
                                    title = "Trust Score",
                                    value = "${user.trustScore}%",
                                    subtitle = user.verificationTier.badgeTitle,
                                    icon = Icons.Default.VerifiedUser,
                                    iconTint = FreshGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        UserRole.SPACE_OWNER -> {
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

            if (user.role == UserRole.PROFESSIONAL) {
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
                                            text = "Welcome back, Dr. / Specialist ${user.fullName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Syndicate No: ${user.syndicateNumber.ifBlank { "Pending Registration" }} • ${user.governorate.displayName}",
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

            // 4-PILLAR ACCREDITATION HUB FOR PRACTITIONERS & HOSTS — runs for every
            // role (the doc list itself is separately gated to exclude ADMIN below).
            // This used to be the `else` of the PROFESSIONAL check above, which meant
            // Specialists could never see this section or upload any verification
            // document at all — the only place that ever did anything with document
            // uploads was unreachable for the one role that most needs it.
            run {
                val isBasicInfoComplete = user.fullName.isNotBlank() && user.phone.isNotBlank()
                val isSyndicateComplete = user.specialty.isNotBlank() && user.syndicateNumber.isNotBlank()
                val isGovComplete = user.governorate.displayName.isNotBlank()
                val isDocsComplete = verifiedDocsCount >= requiredDocTypes.size && requiredDocTypes.isNotEmpty()

                val profileScore = (if (isBasicInfoComplete) 25 else 0) +
                        (if (isSyndicateComplete) 25 else 0) +
                        (if (isGovComplete) 25 else 0) +
                        (if (isDocsComplete) 25 else (uploadedDocsCount * 25 / (if (requiredDocTypes.isEmpty()) 1 else requiredDocTypes.size)))

                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (user.role == UserRole.SPACE_OWNER) "Commercial Host Accreditation" else "Specialist Syndicate Accreditation",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Complete the 4 verification pillars for trusted Lebanese workspace leasing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                color = if (profileScore >= 100) StatusSuccessContainer else OxfordBlueContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$profileScore% Verified",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (profileScore >= 100) StatusSuccess else OxfordBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { profileScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (profileScore >= 100) FreshGreen else CarnationOrange,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        // 4 Pillars Checklist Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pillar 1: Contact
                            Surface(
                                color = if (isBasicInfoComplete) StatusSuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isBasicInfoComplete) BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isBasicInfoComplete) Icons.Default.CheckCircle else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isBasicInfoComplete) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("1. Contact", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }

                            // Pillar 2: Syndicate / License
                            Surface(
                                color = if (isSyndicateComplete) StatusSuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isSyndicateComplete) BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isSyndicateComplete) Icons.Default.CheckCircle else Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = if (isSyndicateComplete) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        if (user.role == UserRole.SPACE_OWNER) "2. Register" else "2. Syndicate",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Pillar 3: Territory
                            Surface(
                                color = if (isGovComplete) StatusSuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isGovComplete) BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isGovComplete) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (isGovComplete) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("3. Territory", fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }

                            // Pillar 4: Docs
                            Surface(
                                color = if (isDocsComplete) StatusSuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                border = if (isDocsComplete) BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f)) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isDocsComplete) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = if (isDocsComplete) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        "4. Docs ($uploadedDocsCount/${requiredDocTypes.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                if (user.role != UserRole.ADMIN) {
                    // REQUIRED CREDENTIAL DOCUMENTS LIST
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProSectionHeader(
                                title = if (user.role == UserRole.SPACE_OWNER) "Property & Commercial Permits" else "Syndicate & Legal Credentials",
                                subtitle = if (user.role == UserRole.SPACE_OWNER)
                                    "Commercial Register (Sijil Tejari), Title Deed (Sanad Melkiyeh), and National ID"
                                else
                                    "Syndicate Membership Card, Practice Decree, and National ID",
                                icon = Icons.Default.VerifiedUser,
                                trailingContent = {
                                    ProStatusBadge(
                                        type = when (user.verificationStatus) {
                                            MemberVerificationStatus.VERIFIED -> ProBadgeType.CUSTOM_SUCCESS
                                            MemberVerificationStatus.PENDING_REVIEW -> ProBadgeType.CUSTOM_WARNING
                                            MemberVerificationStatus.ACTION_REQUIRED -> ProBadgeType.CUSTOM_ERROR
                                            MemberVerificationStatus.UNVERIFIED -> ProBadgeType.CUSTOM_INFO
                                        },
                                        customText = user.verificationStatus.displayName
                                    )
                                }
                            )

                            // Required documents
                            Text(
                                text = "Required Official Credentials (${user.role.displayName})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                requiredDocTypes.forEach { docType ->
                                    val uploadedDoc = userDocuments.find { it.type == docType && it.status != DocumentStatus.NOT_UPLOADED }
                                    CredentialDocumentItemCard(
                                        docType = docType,
                                        uploadedDoc = uploadedDoc,
                                        isRequired = true,
                                        onUploadClick = {
                                            selectedDocTypeForUpload = docType
                                            showUploadDialog = true
                                        },
                                        onPreviewClick = { doc ->
                                            previewingDocument = doc
                                        },
                                        onRemoveClick = { docId ->
                                            coroutineScope.launch {
                                                val success = viewModel.removeCredentialDocument(docId)
                                                Toast.makeText(
                                                    context,
                                                    if (success) "Document removed" else "Failed to remove document — please try again",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                }
                            }

                            // Optional / Supplementary documents
                            if (optionalDocTypes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Supplementary / Recommended Certificates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    optionalDocTypes.forEach { docType ->
                                        val uploadedDoc = userDocuments.find { it.type == docType && it.status != DocumentStatus.NOT_UPLOADED }
                                        CredentialDocumentItemCard(
                                            docType = docType,
                                            uploadedDoc = uploadedDoc,
                                            isRequired = false,
                                            onUploadClick = {
                                                selectedDocTypeForUpload = docType
                                                showUploadDialog = true
                                            },
                                            onPreviewClick = { doc ->
                                                previewingDocument = doc
                                            },
                                            onRemoveClick = { docId ->
                                                coroutineScope.launch {
                                                    val success = viewModel.removeCredentialDocument(docId)
                                                    Toast.makeText(
                                                        context,
                                                        if (success) "Document removed" else "Failed to remove document — please try again",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Upload New Credential Action Button
                            OutlinedButton(
                                onClick = {
                                    selectedDocTypeForUpload = null
                                    showUploadDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload Additional Supporting Document", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }

                            // Submit For Review Action (if not yet verified)
                            if (user.verificationStatus != MemberVerificationStatus.VERIFIED) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val success = viewModel.submitForVerification()
                                            Toast.makeText(
                                                context,
                                                if (success) "Verification package submitted for administrative compliance check!" else "Failed to submit verification package — please try again",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Submit Accreditation Package for Review", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Legal & Regulatory note
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SandstoneContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gavel,
                                        contentDescription = null,
                                        tint = SandstoneDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Lebanese Regulatory & Syndicate Compliance",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SandstoneDark
                                        )
                                        Text(
                                            text = if (user.role == UserRole.SPACE_OWNER)
                                                "Commercial property hosts must verify Sanad Melkiyeh (Title Deed) or official lease contract to ensure legal occupancy, valid subleasing, and generator power supply compliance."
                                            else
                                                "Under Lebanese syndicate regulations (OEA, LOP, BBA), practitioners renting professional clinic or studio suites must be accredited members in good standing for liability protection.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
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
            if (user.role == UserRole.PROFESSIONAL) {
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
                            UserRole.PROFESSIONAL -> "Practitioner & Syndicate Details"
                            UserRole.SPACE_OWNER -> "Host Business & Property Information"
                            UserRole.ADMIN -> "Super Administrator Identity"
                        },
                        subtitle = "Ensure your WhatsApp booking contact and credentials are up to date",
                        icon = Icons.Default.Badge
                    )

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
                            UserRole.PROFESSIONAL -> "Profession / Specialization / Syndicate Title"
                            UserRole.SPACE_OWNER -> "Host Category (Commercial Real Estate, Coworking, Clinic)"
                            UserRole.ADMIN -> "Super Administrator Role & Clearance"
                        },
                        leadingIcon = Icons.Default.Work,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    InputField(
                        value = affiliation,
                        onValueChange = { affiliation = it },
                        label = when (user.role) {
                            UserRole.PROFESSIONAL -> "Order / Hospital / Firm Affiliation"
                            UserRole.SPACE_OWNER -> "Building Name / Real Estate Enterprise / Network"
                            UserRole.ADMIN -> "Central Governance Organization"
                        },
                        leadingIcon = Icons.Default.Apartment,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    InputField(
                        value = syndicateNumber,
                        onValueChange = { syndicateNumber = it },
                        label = when (user.role) {
                            UserRole.PROFESSIONAL -> "Order / Syndicate / Specialist License ID (e.g. LOP / OEA)"
                            UserRole.SPACE_OWNER -> "Commercial Register / Property Sijil Tejari ID"
                            UserRole.ADMIN -> "Central Administrative Security Node ID"
                        },
                        leadingIcon = Icons.Default.Badge,
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

                    Text("Primary Governorate / Territory", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Governorate.entries) { gov ->
                            FilterChip(
                                selected = selectedGov == gov,
                                onClick = { selectedGov = gov },
                                label = { Text(gov.displayName.split(" ").first(), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    CustomButton(
                        text = "Save Profile Changes",
                        onClick = {
                            coroutineScope.launch {
                                val success = viewModel.updateProfile(name, specialty, phone, affiliation, syndicateNumber, selectedGov)
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

    // Credential Upload Modal Dialog
    if (showUploadDialog) {
        CredentialUploadDialog(
            initialType = selectedDocTypeForUpload,
            userRole = user.role,
            onDismiss = { showUploadDialog = false },
            onDocumentUploaded = { type, fileName, fileSizeKb, docNumber, issuingAuth, expiryDate, fileUri ->
                viewModel.uploadCredentialDocument(
                    type = type,
                    fileName = fileName,
                    fileSizeKb = fileSizeKb,
                    documentNumber = docNumber,
                    issuingAuthority = issuingAuth,
                    expiryDate = expiryDate,
                    fileUri = fileUri
                )
            }
        )
    }

    // Document Inspection & Preview Dialog
    previewingDocument?.let { doc ->
        DocumentPreviewDialog(
            document = doc,
            isAdmin = user.role == UserRole.ADMIN,
            onDismiss = { previewingDocument = null },
            onRemoveDocument = { docId ->
                coroutineScope.launch {
                    val success = viewModel.removeCredentialDocument(docId)
                    Toast.makeText(
                        context,
                        if (success) "Document removed" else "Failed to remove document — please try again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                previewingDocument = null
            },
            onApproveDocument = { docId ->
                previewingDocument = null
                coroutineScope.launch {
                    val success = viewModel.adminApproveDocument(docId)
                    Toast.makeText(
                        context,
                        if (success) "Document approved and accredited!" else "Failed to approve document — try again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRejectDocument = { docId, reason ->
                previewingDocument = null
                coroutineScope.launch {
                    val success = viewModel.adminRejectDocument(docId, reason)
                    Toast.makeText(
                        context,
                        if (success) "Revision requested from member" else "Failed to request revision — try again",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

/**
 * Individual Credential Document Item Card Component
 */
@Composable
fun CredentialDocumentItemCard(
    docType: DocumentType,
    uploadedDoc: CredentialDocument?,
    isRequired: Boolean,
    onUploadClick: () -> Unit,
    onPreviewClick: (CredentialDocument) -> Unit,
    onRemoveClick: (String) -> Unit
) {
    val isUploaded = uploadedDoc != null && uploadedDoc.status != DocumentStatus.NOT_UPLOADED
    val status = uploadedDoc?.status ?: DocumentStatus.NOT_UPLOADED

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = when (status) {
                    DocumentStatus.VERIFIED -> StatusSuccess.copy(alpha = 0.5f)
                    DocumentStatus.PENDING_REVIEW -> BrightOrange.copy(alpha = 0.5f)
                    DocumentStatus.REJECTED -> CrimsonRed.copy(alpha = 0.5f)
                    DocumentStatus.NOT_UPLOADED -> MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            ),
        color = when (status) {
            DocumentStatus.VERIFIED -> StatusSuccessContainer.copy(alpha = 0.25f)
            DocumentStatus.PENDING_REVIEW -> StatusWarningContainer.copy(alpha = 0.25f)
            DocumentStatus.REJECTED -> StatusErrorContainer.copy(alpha = 0.25f)
            DocumentStatus.NOT_UPLOADED -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (status) {
                            DocumentStatus.VERIFIED -> Icons.Default.Verified
                            DocumentStatus.PENDING_REVIEW -> Icons.Default.PendingActions
                            DocumentStatus.REJECTED -> Icons.Default.ErrorOutline
                            DocumentStatus.NOT_UPLOADED -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = when (status) {
                            DocumentStatus.VERIFIED -> StatusSuccess
                            DocumentStatus.PENDING_REVIEW -> BrightOrange
                            DocumentStatus.REJECTED -> CrimsonRed
                            DocumentStatus.NOT_UPLOADED -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = docType.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isRequired) {
                                Surface(
                                    color = OxfordBlueContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OxfordBlue,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = docType.officialLebaneseLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ProStatusBadge(
                    type = when (status) {
                        DocumentStatus.VERIFIED -> ProBadgeType.CUSTOM_SUCCESS
                        DocumentStatus.PENDING_REVIEW -> ProBadgeType.CUSTOM_WARNING
                        DocumentStatus.REJECTED -> ProBadgeType.CUSTOM_ERROR
                        DocumentStatus.NOT_UPLOADED -> ProBadgeType.CUSTOM_INFO
                    },
                    customText = status.displayName
                )
            }

            // Description / Guidance
            Text(
                text = docType.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            // Metadata if Uploaded
            if (uploadedDoc != null && isUploaded) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📄 ${uploadedDoc.fileName ?: "Document.pdf"}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uploadedDoc.fileSizeKb} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uploadedDoc.documentNumber.isNotBlank()) {
                            Text(
                                text = "ID / License #: ${uploadedDoc.documentNumber} • Valid thru: ${uploadedDoc.expiryDate.ifBlank { "N/A" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!uploadedDoc.rejectionReason.isNullOrBlank()) {
                            Text(
                                text = "⚠️ Action Needed: ${uploadedDoc.rejectionReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrimsonRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isUploaded) {
                    Button(
                        onClick = onUploadClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload Scan / PDF", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onPreviewClick(uploadedDoc) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Certificate", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onUploadClick,
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replace", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
