package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.BookingRecurrence
import com.example.ui.util.RentableSlot
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import com.example.util.ShareLinks
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailsScreen(
    space: SpaceListing,
    viewModel: ProHostViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()
    val liveSpace = allSpaces.find { it.id == space.id } ?: space
    val allBookingRequests by viewModel.bookingRequests.collectAsState()

    val acceptedBookings = remember(allBookingRequests, liveSpace.id) {
        allBookingRequests.filter { it.spaceId == liveSpace.id && it.status == BookingRequestStatus.ACCEPTED }
    }

    val myRequestsForThisSpace = remember(allBookingRequests, currentUser, liveSpace.id) {
        val user = currentUser
        if (user != null) {
            allBookingRequests.filter {
                it.spaceId == liveSpace.id && (it.practitionerId == user.id || it.practitionerEmail.equals(user.email, ignoreCase = true))
            }
        } else emptyList()
    }

    // Real "Views" engagement count — fires once per detail-screen entry, not on
    // every recomposition. Skipped when the viewer is the listing's own owner so a
    // host's own visits don't inflate their own stats.
    LaunchedEffect(liveSpace.id) {
        if (currentUser?.id != liveSpace.ownerId) {
            viewModel.registerSpaceView(liveSpace.id)
        }
    }

    // Seeded from the real, live pricing config (SpaceCalculationUtils.buildAllSlotsForSpace)
    // instead of the legacy rentalFormulas list — see SpaceDetailsScreenContent's
    // "Renting Options" card, which drives this same selection with real slots.
    var selectedFormula by remember(liveSpace.id) {
        mutableStateOf<RentalFormula?>(
            SpaceCalculationUtils.buildAllSlotsForSpace(liveSpace).firstOrNull()?.let { slot ->
                // Every strategy prices under a single FLAT key now — Shift-Based
                // (item 7b) and Day-Based (this session) both retired their 3-tier
                // recurrence maps, so there's no "prefer weekly" choice left to make.
                SpaceCalculationUtils.representativeFormula(listOf(slot), BookingRecurrence.FLAT)
            }
        )
    }
    SpaceDetailsScreenContent(
        space = liveSpace,
        viewModel = viewModel,
        acceptedBookings = acceptedBookings,
        myRequestsForThisSpace = myRequestsForThisSpace,
        selectedFormula = selectedFormula,
        currentUserRole = currentUser?.role,
        onSelectFormula = { selectedFormula = it },
        onWhatsAppClick = {
            viewModel.launchWhatsAppInquiry(context, liveSpace, selectedFormula)
        },
        onShareClick = {
            val shareUrl = ShareLinks.forListing(liveSpace.id)
            val shareText = buildString {
                append("🏢 ${liveSpace.title}")
                append("\n📍 ${liveSpace.district}, ${liveSpace.governorate.displayName}")
                if (liveSpace.description.isNotBlank()) {
                    append("\n${liveSpace.description}")
                }
                append("\n\n$shareUrl")
            }
            viewModel.shareListing(context, liveSpace.title, shareText)
        },
        onCopyLinkClick = {
            viewModel.copyListingLink(context, ShareLinks.forListing(liveSpace.id))
        },
        isSaved = currentUser?.savedSpaceIds?.contains(liveSpace.id) == true,
        onToggleSave = { viewModel.toggleSavedSpace(liveSpace.id) },
        onBack = onBack
    )
}

/**
 * Dumb Presentation Screen for Space Details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailsScreenContent(
    space: SpaceListing,
    viewModel: ProHostViewModel,
    acceptedBookings: List<RentalBookingRequest>,
    myRequestsForThisSpace: List<RentalBookingRequest>,
    selectedFormula: RentalFormula?,
    currentUserRole: UserRole?,
    onSelectFormula: (RentalFormula) -> Unit,
    onWhatsAppClick: () -> Unit,
    onShareClick: () -> Unit,
    onCopyLinkClick: () -> Unit = {},
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    onBack: () -> Unit
) {
    val liveSpace = space
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showShareMenu by remember { mutableStateOf(false) }
    var showInquiryDialog by remember { mutableStateOf(false) }
    var inquiryMessage by remember { mutableStateOf("") }
    // "hidden" | "peek" | "full"
    // peek = division selected, animated slice visible above bottom of screen; press to expand
    // full = full ModalBottomSheet open
    var availabilityPanelState by remember { mutableStateOf("hidden") }
    val showAvailabilityPanel = availabilityPanelState == "full"
    // Multi-select: SHIFT_BASED, DAY_BASED, MONTHLY slots
    var selectedSlots by remember { mutableStateOf(setOf<RentableSlot>()) }
    // Multi-day HOURLY selection: day → set of selected start-hour strings
    var selectedHoursPerDay by remember { mutableStateOf(mapOf<String, Set<String>>()) }
    var showSendConfirm by remember { mutableStateOf(false) }
    var isSendingSlotRequest by remember { mutableStateOf(false) }
    var selectedSubdivisionId by remember { mutableStateOf<String?>(null) }
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()

    // Real, live pricing config (SpaceCalculationUtils.buildAllSlotsForSpace) — the
    // single source both the Renting Options cards and the Check Availability sheet
    // below read from, so they can never disagree.
    val availableSlots = remember(liveSpace) { SpaceCalculationUtils.buildAllSlotsForSpace(liveSpace) }
    val strategyPreviewGroups = remember(availableSlots) {
        availableSlots.mapNotNull { slot -> slot.strategyType?.let { type -> type to slot } }
            .groupBy({ (type, _) -> type }, { (_, slot) -> slot })
            .toList()
            .sortedBy { (type, _) -> type.ordinal }
    }

    Scaffold(
        // Nested inside the app-shell Scaffold's already-inset content area — see
        // MyFavoritesScreen's identical fix for why this is needed.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        space.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleSave) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isSaved) "Remove from Saved" else "Save Workspace",
                            tint = if (isSaved) CrimsonRed else LocalContentColor.current
                        )
                    }
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Share Listing") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showShareMenu = false
                                    onShareClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Link") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showShareMenu = false
                                    onCopyLinkClick()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Peek availability slice — slides up when a division card is tapped.
                // Specialists only; hidden for Pro Host / Admin preview.
                if (currentUserRole != UserRole.PRO_HOST && currentUserRole != UserRole.ADMIN) {
                    AnimatedVisibility(
                        visible = availabilityPanelState == "peek",
                        enter = slideInVertically { it } + fadeIn(animationSpec = spring()),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        val peekSub = liveSpace.subdivisions.firstOrNull { it.id == selectedSubdivisionId }
                        val peekSlotCount = if (peekSub != null) {
                            availableSlots.count { it.sourceFormulaId == peekSub.id && !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                        } else {
                            availableSlots.count { !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier.fillMaxWidth().clickable { availabilityPanelState = "full" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peekSub?.name ?: liveSpace.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (peekSlotCount > 0) "$peekSlotCount slot(s) available · Tap to view" else "Tap to view full availability",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    IconButton(
                                        onClick = { availabilityPanelState = "hidden" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Generic check-availability bar — visible only when no division is selected
                    AnimatedVisibility(
                        visible = availabilityPanelState == "hidden",
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Surface(
                            color = if (availableSlots.isNotEmpty()) VibrantBlue else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = availableSlots.isNotEmpty()) { availabilityPanelState = "full" }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = if (availableSlots.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (availableSlots.isNotEmpty()) "Press to see option availability" else "No slots configured",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (availableSlots.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                // Price + action strip
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 10.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val (fallbackPrice, fallbackUnit) = remember(liveSpace) { SpaceCalculationUtils.lowestPriceSummary(liveSpace) }
                            val price = selectedFormula?.rateUsd ?: fallbackPrice
                            val priceUnit = selectedFormula?.let { SpaceCalculationUtils.rateUnitLabel(it.type) } ?: fallbackUnit
                            Text(
                                text = "$${price.toInt()} USD$priceUnit",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = CarnationOrange
                            )
                            Text(
                                text = selectedFormula?.type?.displayName ?: "Full Month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentUserRole == UserRole.PRO_HOST || currentUserRole == UserRole.ADMIN) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text(
                                    text = "Preview Mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                )
                            }
                        } else {
                            Button(
                                onClick = onWhatsAppClick,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "WhatsApp",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Visual Card — swipeable through every photo, not just the first
            // (this used to hard-drop imageUrls[1..], the only place the rest of a
            // listing's photos were ever shown). Bottom-rounded so it tucks into the
            // content sheet below, and a bottom-anchored gradient scrim (rather than a
            // flat overlay across the whole photo) keeps the top of the image vivid
            // while still guaranteeing the title/location text stays legible.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                if (liveSpace.imageUrls.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { liveSpace.imageUrls.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        coil.compose.AsyncImage(
                            model = liveSpace.imageUrls[page],
                            contentDescription = liveSpace.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.65f)
                                    ),
                                    startY = 0f
                                )
                            )
                    )
                    if (liveSpace.imageUrls.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(liveSpace.imageUrls.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == pagerState.currentPage) Color.White
                                            else Color.White.copy(alpha = 0.5f)
                                        )
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        OxfordBlueDark,
                                        OxfordBlue
                                    )
                                )
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = Color(0x33FFFFFF),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${liveSpace.spaceCategoryName ?: liveSpace.spaceType.displayName} • " +
                                    "${if (liveSpace.isShared) "Shared Co-Working Space" else "Private Studio / Office"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                            )
                        }
                        if (liveSpace.isVerified) {
                            ProStatusBadge(type = ProBadgeType.VERIFIED_MEMBER, customText = "Verified")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = liveSpace.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 28.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${liveSpace.district}, ${liveSpace.governorate.displayName} • ${liveSpace.floorInfo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xCCFFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // The host's package lapsed with no renewal (expirePackages.ts) — the
            // listing is hidden from a fresh Discovery browse but never unpublished,
            // and a specialist who already has an accepted booking here (or anyone
            // who reached this screen via a direct link/share while it's hidden)
            // still gets full access, just told honestly why it won't show up if
            // they go looking for it again.
            if (liveSpace.isOwnerPackageLapsed) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "This host is on verification process — their listing is temporarily hidden from new searches, but your existing " +
                            "access here is unaffected.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                // Active In-App Booking Request Status (if any)
                myRequestsForThisSpace.forEach { req ->
                    ProSurfaceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Your In-App Rental Request #${req.id}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Formula: ${req.formula.scheduleDescription} • Start: ${req.startDate} (${req.durationMonths} mo)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (req.status == BookingRequestStatus.PENDING) {
                                    Text(
                                        text = "Awaiting owner acceptance. Space hours remain open to public until confirmed.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberWarning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (req.status == BookingRequestStatus.ACCEPTED) {
                                    Text(
                                        text = "Confirmed! Space hours (${req.formula.daysOfWeek.joinToString()}" +
                                            " ${req.formula.startHour}-${req.formula.endHour}) are locked" +
                                            ".",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusInfo,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            when (req.status) {
                                BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                                BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Pending")
                                BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "Declined")
                                BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Cancelled")
                            }
                        }
                    }
                }

                // Owner & Verification Card
                ProSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProMemberAvatar(
                            name = liveSpace.ownerName,
                            specialty = "Space Host • WhatsApp: ${liveSpace.ownerPhone}",
                            isVerified = liveSpace.isVerified,
                            isIdVerified = liveSpace.ownerIsIdVerified,
                            imageUrl = liveSpace.ownerProfilePictureUrl,
                            size = 40.dp,
                            modifier = Modifier.weight(1f)
                        )

                        ProStatusBadge(ProBadgeType.ACTIVE_30D)
                    }
                }

                // Description — exists on SpaceListing since Phase 2 but was never
                // rendered anywhere on this screen.
                if (liveSpace.description.isNotBlank()) {
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProSectionHeader(
                                title = "Description",
                                icon = Icons.Default.Description
                            )
                            Text(
                                text = liveSpace.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Smart Availability & Operating Schedule Calendar View
                SpaceAvailabilityCalendarView(
                    space = liveSpace,
                    acceptedBookings = acceptedBookings
                )

                // Rental Options — one card per configured subdivision (or a single
                // "Whole Space" card when no subdivisions are set up). Tapping an
                // available card pre-selects that subdivision and opens the availability sheet.
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProSectionHeader(
                            title = "Rental Options",
                            subtitle = if (liveSpace.subdivisions.isNotEmpty())
                                "${liveSpace.subdivisions.size} space${if (liveSpace.subdivisions.size == 1) "" else "s"} available — tap to see availability"
                            else
                                "Tap the card to check live availability",
                            icon = Icons.Default.Tune
                        )

                        if (liveSpace.subdivisions.isEmpty()) {
                            // Whole-Space card
                            val wholeSpaceSlots = availableSlots
                            val allLocked = wholeSpaceSlots.isNotEmpty() &&
                                wholeSpaceSlots.all { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                            val priceSummary = run {
                                val st = strategyPreviewGroups.firstOrNull()?.first
                                when (st) {
                                    RentalStrategyType.MONTHLY -> "$${liveSpace.pricing.monthly?.rateUsd?.toInt() ?: liveSpace.baseMonthlyRateUsd.toInt()}/mo"
                                    RentalStrategyType.HOURLY -> "from $${liveSpace.pricing.hourly?.cellPrices?.values?.minOrNull()?.toInt() ?: 0}/hr"
                                    RentalStrategyType.SHIFT_BASED -> {
                                        val minPrice = liveSpace.pricing.shiftBased?.shifts
                                            ?.filter { !it.isUnavailable }?.minOfOrNull { it.price }?.toInt() ?: 0
                                        "from $$minPrice/shift"
                                    }
                                    RentalStrategyType.DAY_BASED -> {
                                        val minPrice = liveSpace.pricing.dayBased?.distribution?.values?.minOfOrNull { it.price }?.toInt() ?: 0
                                        "from $$minPrice/day"
                                    }
                                    null -> "$${liveSpace.baseMonthlyRateUsd.toInt()}/mo"
                                }
                            }
                            SubdivisionRentalCard(
                                info = SubdivisionRentalCardInfo(
                                    name = liveSpace.title,
                                    typeBadge = "Whole Space",
                                    imageUrls = liveSpace.imageUrls,
                                    amenities = liveSpace.essentialFacilities,
                                    hashtags = emptyList(),
                                    priceSummary = priceSummary,
                                    isOccupied = allLocked
                                ),
                                onClick = {
                                    selectedSubdivisionId = null
                                    availabilityPanelState = "peek"
                                }
                            )
                        } else {
                            liveSpace.subdivisions.forEach { sub ->
                                val subSlots = availableSlots.filter { it.sourceFormulaId == sub.id }
                                val isOccupied = subSlots.isNotEmpty() &&
                                    subSlots.all { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                val priceSummary = when (sub.pricing.strategyType) {
                                    RentalStrategyType.MONTHLY -> "$${sub.pricing.monthly?.rateUsd?.toInt() ?: 0}/mo"
                                    RentalStrategyType.HOURLY -> "from $${sub.pricing.hourly?.cellPrices?.values?.minOrNull()?.toInt() ?: 0}/hr"
                                    RentalStrategyType.SHIFT_BASED -> {
                                        val minPrice = sub.pricing.shiftBased?.shifts?.filter { !it.isUnavailable }?.minOfOrNull { it.price }?.toInt() ?: 0
                                        "from $$minPrice/shift"
                                    }
                                    RentalStrategyType.DAY_BASED -> {
                                        val minPrice = sub.pricing.dayBased?.distribution?.values?.minOfOrNull { it.price }?.toInt() ?: 0
                                        "from $$minPrice/day"
                                    }
                                }
                                SubdivisionRentalCard(
                                    info = SubdivisionRentalCardInfo(
                                        name = sub.name,
                                        typeBadge = sub.type.displayName,
                                        imageUrls = sub.imageUrls,
                                        amenities = sub.amenities,
                                        hashtags = sub.hashtags,
                                        priceSummary = "${sub.pricing.strategyType.displayName} · $priceSummary",
                                        isOccupied = isOccupied
                                    ),
                                    onClick = {
                                        val formula = SpaceCalculationUtils.representativeFormula(subSlots, BookingRecurrence.FLAT)
                                        if (formula != null) onSelectFormula(formula)
                                        selectedSubdivisionId = sub.id
                                        availabilityPanelState = "peek"
                                    }
                                )
                            }
                        }
                    }
                }

                // Facilities & Utilities
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProSectionHeader(
                            title = "Facilities & Utilities",
                            subtitle = "${liveSpace.essentialFacilities.size} amenities included",
                            icon = Icons.Default.Business
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            liveSpace.essentialFacilities.forEach { facility ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = LebaneseCedarGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(facility, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                // Co-Working Synergy
                // Residents are computed from real confirmed bookings' specialties
                // (RentalBookingRequest.practitionerSpecialty), not liveSpace.
                // residentPractitioners — that field used to default to whoever last
                // saved the listing wizard ("Main Admin (...)"), never the actual
                // specialists renting here.
                val residentSpecialties = remember(acceptedBookings) {
                    acceptedBookings.map { it.practitionerSpecialty }.distinct()
                }
                if (liveSpace.complementarySpecialties.isNotEmpty() || residentSpecialties.isNotEmpty()) {
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProSectionHeader(
                                title = "Co-Working Synergy",
                                subtitle = "${residentSpecialties.size} peer${if (residentSpecialties.size == 1) "" else "s"} on premises",
                                icon = Icons.Default.Groups
                            )

                            if (residentSpecialties.isNotEmpty()) {
                                Text(
                                    text = "Residents:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    items(residentSpecialties) { spec ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = spec,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(Spacing.xs))
                            }

                            Text(
                                text = "Target Complementary Disciplines:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(liveSpace.complementarySpecialties) { spec ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = spec,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Sends one booking request covering all currently-selected slots (multi-select).
    // HOURLY multi-hour selections are treated as separate slot lines in the notes.
    // Attendee-mode data (count + package) are forwarded when the subdivision is PER_ATTENDEE.
    fun sendMultiSlotRequest(
        slotsToSend: List<RentableSlot>,
        hourlySelections: Map<String, Set<String>>,
        allAvailableSlots: List<RentableSlot>,
        attendeeCount: Int = 0,
        attendeePackage: AttendeePackage? = null
    ) {
        val user = currentUser
        if (user == null) return
        if (user.kycLevel < 1) {
            android.widget.Toast.makeText(context, "Please add a profile picture before making booking requests.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        // Flatten HOURLY hourlySelections to RentableSlot list
        val hourlySlotList = hourlySelections.flatMap { (day, hours) ->
            allAvailableSlots.filter { it.day == day && it.startTime in hours }
        }
        val allSlots = (slotsToSend + hourlySlotList).distinctBy { it.label + it.day + it.startTime }
        val primarySlot = allSlots.firstOrNull() ?: return
        val strategyType = primarySlot.strategyType ?: return
        isSendingSlotRequest = true
        coroutineScope.launch {
            val formula = SpaceCalculationUtils.representativeFormula(allSlots, BookingRecurrence.FLAT)
            if (formula == null) {
                isSendingSlotRequest = false
                return@launch
            }
            val isoDate = SpaceCalculationUtils.nextDateForWeekday(primarySlot.day)
            val subdivision = liveSpace.subdivisions.find { it.id == primarySlot.sourceFormulaId }
            val isAttendeeMode = subdivision?.pricingMode == SubdivisionPricingMode.PER_ATTENDEE
            val price = if (isAttendeeMode && attendeePackage != null) {
                attendeeCount * attendeePackage.pricePerAttendeeUsd
            } else {
                allSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: formula.rateUsd }
            }
            val shiftLabel = if (strategyType == RentalStrategyType.SHIFT_BASED) primarySlot.groupLabel.substringAfter("• ") else ""
            val slotSummary = allSlots.joinToString("; ") { it.label }
            val notes = buildString {
                append("Requested via live slot selection")
                if (allSlots.size > 1) append(" — $slotSummary")
                if (isAttendeeMode && attendeePackage != null) {
                    append(" | Attendees: $attendeeCount × ${attendeePackage.name} @ \$${attendeePackage.pricePerAttendeeUsd}/pp")
                }
            }
            val (request, synced) = viewModel.repository.createBookingRequest(
                space = liveSpace,
                formula = formula,
                practitioner = user,
                startDate = isoDate,
                durationMonths = 1,
                notes = notes,
                selectedDays = allSlots.map { it.day }.distinct(),
                selectedCalendarDates = if (strategyType == RentalStrategyType.MONTHLY) {
                    emptyList()
                } else {
                    allSlots.map { SpaceCalculationUtils.nextDateForWeekday(it.day) }.distinct()
                },
                selectedStartHour = primarySlot.startTime,
                selectedEndHour = primarySlot.endTime,
                selectedShift = shiftLabel,
                calculatedTotalUsd = price,
                subdivisionId = subdivision?.id,
                subdivisionName = subdivision?.name,
                attendeeCount = if (isAttendeeMode) attendeeCount else 0,
                selectedAttendeePackageId = if (isAttendeeMode) attendeePackage?.id else null,
                attendeePackageName = if (isAttendeeMode) attendeePackage?.name else null,
                attendeePackagePriceUsd = if (isAttendeeMode) (attendeePackage?.pricePerAttendeeUsd ?: 0.0) else 0.0
            )
            isSendingSlotRequest = false
            showSendConfirm = false
            if (synced) {
                selectedSlots = emptySet()
                selectedHoursPerDay = emptyMap()
                availabilityPanelState = "hidden"
                val result = snackbarHostState.showSnackbar(
                    message = "Request sent! The host has been notified.",
                    actionLabel = "WhatsApp Host",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.launchWhatsAppInquiry(context, liveSpace, formula, request)
                }
            } else {
                snackbarHostState.showSnackbar("Couldn't send your request — check your connection and try again.")
            }
        }
    }

    // Check Availability — per-day expand/collapse slide-up panel.
    // Each strategy section lists day bars independently expandable (green = available,
    // red = rented badges). "FULL" days are shown but cannot expand. Expanded day body
    // shows only available slots as selectable cells. Attendee-mode block at top when
    // subdivision pricingMode == PER_ATTENDEE.
    if (showAvailabilityPanel) {
        val sheetSlotGroups = remember(strategyPreviewGroups, selectedSubdivisionId) {
            if (selectedSubdivisionId != null) {
                strategyPreviewGroups.map { (type, slots) ->
                    type to slots.filter { it.sourceFormulaId == selectedSubdivisionId }
                }.filter { (_, slots) -> slots.isNotEmpty() }
            } else {
                strategyPreviewGroups
            }
        }
        val selectedSubdivision = remember(selectedSubdivisionId, liveSpace) {
            if (selectedSubdivisionId != null) liveSpace.subdivisions.find { it.id == selectedSubdivisionId } else null
        }
        val isAttendeeMode = selectedSubdivision?.pricingMode == SubdivisionPricingMode.PER_ATTENDEE
        val enabledPackages = remember(architectureSchema) { architectureSchema.attendeePackages.filter { it.isEnabled } }

        // Per-strategy, per-day expand state
        var expandedDaysByStrategy by remember(sheetSlotGroups) { mutableStateOf(mapOf<RentalStrategyType, Set<String>>()) }
        // Attendee mode fields (scoped to sheet lifetime)
        var sheetAttendeeCount by remember { mutableStateOf(1) }
        var sheetAttendeePackage by remember(enabledPackages) { mutableStateOf(enabledPackages.firstOrNull()) }

        val dayOrder = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday","Mon","Tue","Wed","Thu","Fri","Sat","Sun")

        // Total selected slot count across strategies
        val totalSelectedSlots = selectedSlots.size + selectedHoursPerDay.values.sumOf { it.size }
        val hasSelection = totalSelectedSlots > 0

        // Attendee total cost
        val attendeeTotalUsd = if (isAttendeeMode)
            sheetAttendeePackage?.let { sheetAttendeeCount * it.pricePerAttendeeUsd } ?: 0.0 else 0.0

        val canSubmit = hasSelection && (!isAttendeeMode || (sheetAttendeeCount > 0 && sheetAttendeePackage != null))

        ModalBottomSheet(
            onDismissRequest = {
                availabilityPanelState = "hidden"
                selectedSlots = emptySet()
                selectedHoursPerDay = emptyMap()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            scrimColor = Color.Black.copy(alpha = 0.35f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(28.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProSectionHeader(
                    title = "Check Availability",
                    subtitle = if (isAttendeeMode) "Enter attendee count, then tap days to select slots." else "Tap a day to expand and select open slots.",
                    icon = Icons.Default.EventAvailable
                )

                // ── Attendee mode block ──────────────────────────────────────
                if (isAttendeeMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Attendee Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            // Attendee count stepper
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Number of Attendees", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledIconButton(
                                        onClick = { if (sheetAttendeeCount > 1) sheetAttendeeCount-- },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp)) }
                                    Text(
                                        sheetAttendeeCount.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.widthIn(min = 32.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            val cap = selectedSubdivision?.capacity ?: 999
                                            if (sheetAttendeeCount < cap) sheetAttendeeCount++
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp)) }
                                }
                            }
                            // Package selector
                            if (enabledPackages.isNotEmpty()) {
                                Text("Select Package", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(enabledPackages) { pkg ->
                                        val isSelected = sheetAttendeePackage?.id == pkg.id
                                        Surface(
                                            onClick = { sheetAttendeePackage = pkg },
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.medium,
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 140.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    pkg.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    "$${pkg.pricePerAttendeeUsd.toInt()}/pp",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Total amount
                            sheetAttendeePackage?.let { pkg ->
                                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Estimated Total", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "$${String.format("%.2f", attendeeTotalUsd)} USD",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    "$sheetAttendeeCount attendees × $${pkg.pricePerAttendeeUsd.toInt()} ${pkg.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                if (sheetSlotGroups.isEmpty()) {
                    Text("No slots configured for this space.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    sheetSlotGroups.forEach { (strategyType, slots) ->
                        // Strategy section label
                        Text(
                            if (isAttendeeMode) "Availability — ${strategyType.displayName}" else strategyType.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Group slots by day
                        val slotsByDay = slots.groupBy { it.day }.toList()
                            .sortedBy { (day, _) -> dayOrder.indexOfFirst { it.equals(day, ignoreCase = true) }.let { if (it >= 0) it else 99 } }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            slotsByDay.forEach { (day, daySlots) ->
                                val lockedInDay = daySlots.count { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                val availInDay = daySlots.size - lockedInDay
                                val isFull = availInDay == 0
                                val expandedDays = expandedDaysByStrategy[strategyType] ?: emptySet()
                                val isDayExpanded = expandedDays.contains(day)

                                // ── Day bar ──────────────────────────────────────
                                Surface(
                                    onClick = {
                                        if (!isFull) {
                                            val current = expandedDaysByStrategy[strategyType] ?: emptySet()
                                            expandedDaysByStrategy = expandedDaysByStrategy + (strategyType to
                                                if (isDayExpanded) current - day else current + day)
                                        }
                                    },
                                    enabled = !isFull,
                                    color = when {
                                        isFull -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        isDayExpanded -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = if (isDayExpanded) RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp) else MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Day name
                                        Text(
                                            day,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFull) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                else if (isDayExpanded) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Badges row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isFull) {
                                                Surface(
                                                    color = StatusErrorContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("FULL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                                                        color = StatusError, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            } else {
                                                // Green available badge
                                                Surface(color = FreshGreen.copy(alpha = 0.15f), shape = CircleShape) {
                                                    Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(FreshGreen))
                                                        Text(
                                                            "$availInDay",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = LebaneseCedarGreen
                                                        )
                                                    }
                                                }
                                                // Red rented badge
                                                if (lockedInDay > 0) {
                                                    Surface(color = StatusError.copy(alpha = 0.12f), shape = CircleShape) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StatusError))
                                                            Text(
                                                                "$lockedInDay",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = StatusError
                                                            )
                                                        }
                                                    }
                                                }
                                                Icon(
                                                    imageVector = if (isDayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (isDayExpanded) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // ── Expanded day body (available slots only) ──────
                                if (isDayExpanded && !isFull) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val availableSlots = daySlots.filter { !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                            when (strategyType) {
                                                RentalStrategyType.MONTHLY -> {
                                                    availableSlots.forEach { slot ->
                                                        val isSelected = selectedSlots.contains(slot)
                                                        Surface(
                                                            onClick = { selectedSlots = if (isSelected) selectedSlots - slot else selectedSlots + slot },
                                                            color = if (isSelected) FreshGreen.copy(alpha = 0.18f) else StatusSuccessContainer,
                                                            shape = MaterialTheme.shapes.small,
                                                            border = if (isSelected) BorderStroke(1.5.dp, FreshGreen) else null
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                                Icon(
                                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.EventAvailable,
                                                                    contentDescription = null,
                                                                    tint = if (isSelected) FreshGreen else StatusSuccess,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column {
                                                                    Text(
                                                                        if (isSelected) "Selected — Monthly Lease" else "Available — Monthly Lease",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) FreshGreen else StatusSuccess
                                                                    )
                                                                    Text(
                                                                        "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}/mo",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                RentalStrategyType.HOURLY -> {
                                                    Text(
                                                        "Select time slots for $day:",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        items(availableSlots.sortedBy { it.startTime }) { slot ->
                                                            val isSelected = selectedHoursPerDay[day]?.contains(slot.startTime) == true
                                                            Surface(
                                                                onClick = {
                                                                    val current = selectedHoursPerDay[day] ?: emptySet()
                                                                    selectedHoursPerDay = selectedHoursPerDay + (day to
                                                                        if (isSelected) current - slot.startTime else current + slot.startTime)
                                                                },
                                                                color = if (isSelected) FreshGreen.copy(alpha = 0.22f) else StatusSuccessContainer,
                                                                shape = MaterialTheme.shapes.extraSmall,
                                                                border = if (isSelected) BorderStroke(1.5.dp, FreshGreen) else null
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Text(
                                                                        slot.startTime,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) FreshGreen else StatusSuccess
                                                                    )
                                                                    Text(
                                                                        "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = if (isSelected) {
                                                                            FreshGreen.copy(alpha = 0.8f)
                                                                        } else {
                                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                RentalStrategyType.SHIFT_BASED -> {
                                                    availableSlots.forEach { slot ->
                                                        val isSelected = selectedSlots.contains(slot)
                                                        val displayLabel = slot.label
                                                            .replace(
                                                                Regex("^${Regex.escape(day)}\\s*[-–•]?\\s*", RegexOption.IGNORE_CASE),
                                                                ""
                                                            ).trim().ifBlank { slot.label }
                                                        Surface(
                                                            onClick = { selectedSlots = if (isSelected) selectedSlots - slot else selectedSlots + slot },
                                                            color = if (isSelected) FreshGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                                            shape = MaterialTheme.shapes.small,
                                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                                                                if (isSelected) FreshGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 12.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier.size(8.dp).clip(CircleShape)
                                                                            .background(if (isSelected) FreshGreen else StatusSuccess)
                                                                    )
                                                                    Text(
                                                                        displayLabel,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                                    )
                                                                }
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(
                                                                        if (isSelected) "Selected" else "Available",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) FreshGreen else StatusSuccess
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                RentalStrategyType.DAY_BASED -> {
                                                    availableSlots.forEach { slot ->
                                                        val isSelected = selectedSlots.contains(slot)
                                                        Surface(
                                                            onClick = { selectedSlots = if (isSelected) selectedSlots - slot else selectedSlots + slot },
                                                            color = if (isSelected) FreshGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                                            shape = MaterialTheme.shapes.small,
                                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                                                                if (isSelected) FreshGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 12.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier.size(8.dp).clip(CircleShape)
                                                                            .background(if (isSelected) FreshGreen else StatusSuccess)
                                                                    )
                                                                    Text(slot.day, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                                }
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(
                                                                        if (isSelected) "Selected" else "Available",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) FreshGreen else StatusSuccess
                                                                    )
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

                    // ── Action bar ───────────────────────────────────────────────
                    if (canSubmit) {
                        val slotLabel = if (totalSelectedSlots == 1) "1 Slot" else "$totalSelectedSlots Slots"
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showSendConfirm = true },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request $slotLabel", fontWeight = FontWeight.Bold)
                            }
                            // WhatsApp: compose structured message listing all selected slots
                            Button(
                                onClick = {
                                    val slotLines = buildString {
                                        selectedSlots.forEachIndexed { i, s ->
                                            val priceSuffix = s.pricesByRecurrence[BookingRecurrence.FLAT]?.let { " — \$${it.toInt()}" } ?: ""
                                            appendLine("  ${i + 1}. ${s.label}$priceSuffix")
                                        }
                                        selectedHoursPerDay.entries.forEachIndexed { di, (day, hrs) ->
                                            hrs.forEachIndexed { hi, hr ->
                                                appendLine("  ${selectedSlots.size + di * 100 + hi + 1}. $day $hr")
                                            }
                                        }
                                    }.trimEnd()
                                    val attendeeBlock = if (isAttendeeMode)
                                        sheetAttendeePackage?.let { ap -> "\n\n👥 Attendees: $sheetAttendeeCount\n📦 Package: ${ap.name}" +
                                            " (\$${ap.pricePerAttendeeUsd.toInt()}/pp)\n💰 Estimated Tota" +
                                            "l: \$${String.format("%.2f", attendeeTotalUsd)} USD" } ?: ""
                                    else ""
                                    val message = "Hello! I'm interested in booking *${liveSpace.title}*.\n\n📍" +
                                        " ${liveSpace.district}, ${liveSpace.governorate.displayName}" +
                                        "\n\n🗓 Selected Slots ($totalSelectedSlots):\n$slotLines$att" +
                                        "endeeBlock\n\nAre these slots still available?"
                                    try {
                                        val whatsappUrl = "https://api.whatsapp.com/send?phone=${liveSpace.ownerPhone}" +
                                            "&text=${java.net.URLEncoder.encode(message, "UTF-8")}"
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(whatsappUrl)
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Persist attendee state for the confirm popup
                    LaunchedEffect(sheetAttendeeCount, sheetAttendeePackage) { /* captured by confirm popup via closure */ }
                }
            }
        }

        // ── Confirm-and-send popup ───────────────────────────────────────────
        if (showSendConfirm) {
            val selectedSubdivisionForDialog = if (selectedSubdivisionId != null)
                liveSpace.subdivisions.find { it.id == selectedSubdivisionId } else null
            val allSheetSlots = sheetSlotGroups.flatMap { (_, s) -> s }
            val isAttendeeModeDialog = selectedSubdivisionForDialog?.pricingMode == SubdivisionPricingMode.PER_ATTENDEE
            val enabledPkgs = architectureSchema.attendeePackages.filter { it.isEnabled }
            val pkgForDialog = enabledPkgs.firstOrNull {
                it.id == (enabledPkgs.firstOrNull { p -> p.id == sheetAttendeePackage?.id }?.id)
            } ?: sheetAttendeePackage

            val totalCostForDialog = if (isAttendeeModeDialog && pkgForDialog != null)
                sheetAttendeeCount * pkgForDialog.pricePerAttendeeUsd
            else
                selectedSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 } +
                selectedHoursPerDay.entries.sumOf { (day, hrs) ->
                    allSheetSlots.filter { it.day == day && it.startTime in hrs }.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
                }

            AlertDialog(
                onDismissRequest = { if (!isSendingSlotRequest) showSendConfirm = false },
                icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                title = { Text("Confirm Your Request") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Space / location / room summary
                        listOf(
                            "Space" to liveSpace.title,
                            "Location" to "${liveSpace.district}, ${liveSpace.governorate.displayName}",
                            "Room" to (selectedSubdivisionForDialog?.name ?: "Whole Space")
                        ).forEach { (label, value) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Divider()
                        // Slot summary
                        val allSelectedSlotLabels = buildList {
                            selectedSlots.forEach { add(it.label) }
                            selectedHoursPerDay.forEach { (day, hrs) -> hrs.forEach { hr -> add("$day $hr") } }
                        }
                        Text("Selected Slots (${allSelectedSlotLabels.size})",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        allSelectedSlotLabels.take(5).forEach { label ->
                            Text("• $label", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        if (allSelectedSlotLabels.size > 5) {
                            Text("+ ${allSelectedSlotLabels.size - 5} more slots", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Attendee block
                        if (isAttendeeModeDialog && pkgForDialog != null) {
                            Divider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Attendees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$sheetAttendeeCount pax", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Package", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(pkgForDialog.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Divider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimated Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$${String.format("%.2f", totalCostForDialog)} USD",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            sendMultiSlotRequest(
                                slotsToSend = selectedSlots.toList(),
                                hourlySelections = selectedHoursPerDay,
                                allAvailableSlots = sheetSlotGroups.flatMap { (_, s) -> s },
                                attendeeCount = if (isAttendeeModeDialog) sheetAttendeeCount else 0,
                                attendeePackage = if (isAttendeeModeDialog) pkgForDialog else null
                            )
                        },
                        enabled = !isSendingSlotRequest
                    ) {
                        if (isSendingSlotRequest) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Send")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSendConfirm = false }, enabled = !isSendingSlotRequest) { Text("Cancel") }
                }
            )
        }
    }

    // Email Inquiry Dialog
    if (showInquiryDialog) {
        AlertDialog(
            onDismissRequest = { showInquiryDialog = false; inquiryMessage = "" },
            title = { Text("Email the Host") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your message will be sent to the space owner by email. They can reply directly to you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inquiryMessage,
                        onValueChange = { inquiryMessage = it },
                        label = { Text("Message") },
                        placeholder = { Text("Hi, I'm interested in your space…") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendInquiryEmail(context, liveSpace.id, inquiryMessage)
                        showInquiryDialog = false
                        inquiryMessage = ""
                    },
                    enabled = inquiryMessage.trim().length >= 5
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInquiryDialog = false; inquiryMessage = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class SubdivisionRentalCardInfo(
    val name: String,
    val typeBadge: String,
    val imageUrls: List<String>,
    val amenities: List<String>,
    val hashtags: List<String>,
    val priceSummary: String,
    val isOccupied: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubdivisionRentalCard(
    info: SubdivisionRentalCardInfo,
    onClick: () -> Unit
) {
    val name = info.name
    val typeBadge = info.typeBadge
    val imageUrls = info.imageUrls
    val amenities = info.amenities
    val hashtags = info.hashtags
    val priceSummary = info.priceSummary
    val isOccupied = info.isOccupied
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isOccupied) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box {
            Column {
                // Photo carousel
                if (imageUrls.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        HorizontalPager(state = pagerState) { page ->
                            coil.compose.AsyncImage(
                                model = imageUrls[page],
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        if (imageUrls.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(imageUrls.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (pagerState.currentPage == index) 6.dp else 4.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = if (pagerState.currentPage == index) 1f else 0.5f))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)))
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.Center).size(32.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Name + type badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                typeBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Pricing
                    Text(
                        priceSummary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CarnationOrange
                    )

                    // Amenities chips (first 3 + overflow)
                    if (amenities.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val visible = amenities.take(3)
                            val overflow = amenities.size - visible.size
                            items(visible) { a ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        a,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                            if (overflow > 0) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "+$overflow more",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Hashtag chips (first 2 + overflow)
                    if (hashtags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val visible = hashtags.take(2)
                            val overflow = hashtags.size - visible.size
                            items(visible) { tag ->
                                Surface(
                                    color = ProTealContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ProOnTealContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (overflow > 0) {
                                item {
                                    Surface(
                                        color = ProTealContainer.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "[$overflow more]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ProOnTealContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Occupied overlay
            if (isOccupied) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Text(
                            "Currently Occupied",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
