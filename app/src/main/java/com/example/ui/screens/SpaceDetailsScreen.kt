package com.example.ui.screens

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
            allBookingRequests.filter { it.spaceId == liveSpace.id && (it.practitionerId == user.id || it.practitionerEmail.equals(user.email, ignoreCase = true)) }
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
    var showAvailabilityPanel by remember { mutableStateOf(false) }
    // The slot a specialist has tapped inside the Check Availability sheet —
    // driving the "Send Request" bar and the confirm popup below. Cleared
    // whenever the sheet closes so a stale selection never survives a re-open.
    var selectedAvailableSlot by remember { mutableStateOf<RentableSlot?>(null) }
    var selectedHourlySlots by remember { mutableStateOf(setOf<RentableSlot>()) }
    var selectedSubdivisionId by remember { mutableStateOf<String?>(null) }
    var showSendConfirm by remember { mutableStateOf(false) }
    var isSendingSlotRequest by remember { mutableStateOf(false) }

    // Real, live pricing config (SpaceCalculationUtils.buildAllSlotsForSpace) — the
    // single source both the Renting Options cards and the Check Availability sheet
    // below read from, so they can never disagree.
    val availableSlots = remember(liveSpace) { SpaceCalculationUtils.buildAllSlotsForSpace(liveSpace) }
    val strategyPreviewGroups = remember(availableSlots) {
        availableSlots.filter { it.strategyType != null }
            .groupBy { it.strategyType!! }
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
                title = { Text(space.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
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
                // Peek tap bar — only for guests (not for hosts/admins who see Preview Mode)
                if (currentUserRole != UserRole.PRO_HOST && currentUserRole != UserRole.ADMIN) {
                    Surface(
                        color = VibrantBlue,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = strategyPreviewGroups.isNotEmpty()) { showAvailabilityPanel = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = Spacing.lg),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Press to see option availability",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // Main bottom strip
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
                            val price = selectedFormula?.rateUsd ?: liveSpace.baseMonthlyRateUsd
                            val priceUnit = selectedFormula?.let { SpaceCalculationUtils.rateUnitLabel(it.type) } ?: "/mo"
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
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
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
                                text = "${liveSpace.spaceCategoryName ?: liveSpace.spaceType.displayName} • ${if (liveSpace.isShared) "Shared Co-Working Space" else "Private Studio / Office"}",
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
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        Text(
                            "This host is on verification process — their listing is temporarily hidden from new searches, but your existing access here is unaffected.",
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
                                        text = "Confirmed! Space hours (${req.formula.daysOfWeek.joinToString()} ${req.formula.startHour}-${req.formula.endHour}) are locked.",
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
                            val allLocked = wholeSpaceSlots.isNotEmpty() && wholeSpaceSlots.all { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                            val priceSummary = run {
                                val st = strategyPreviewGroups.firstOrNull()?.first
                                when (st) {
                                    RentalStrategyType.MONTHLY -> "$${liveSpace.pricing.monthly?.rateUsd?.toInt() ?: liveSpace.baseMonthlyRateUsd.toInt()}/mo"
                                    RentalStrategyType.HOURLY -> "from $${liveSpace.pricing.hourly?.cellPrices?.values?.minOrNull()?.toInt() ?: 0}/hr"
                                    RentalStrategyType.SHIFT_BASED -> "from $${liveSpace.pricing.shiftBased?.shifts?.filter { !it.isUnavailable }?.minOfOrNull { it.price }?.toInt() ?: 0}/shift"
                                    RentalStrategyType.DAY_BASED -> "from $${liveSpace.pricing.dayBased?.distribution?.values?.minOfOrNull { it.price }?.toInt() ?: 0}/day"
                                    null -> "$${liveSpace.baseMonthlyRateUsd.toInt()}/mo"
                                }
                            }
                            SubdivisionRentalCard(
                                name = liveSpace.title,
                                typeBadge = "Whole Space",
                                imageUrls = liveSpace.imageUrls,
                                amenities = liveSpace.essentialFacilities,
                                hashtags = emptyList(),
                                priceSummary = priceSummary,
                                isOccupied = allLocked,
                                onClick = {
                                    selectedSubdivisionId = null
                                    showAvailabilityPanel = true
                                }
                            )
                        } else {
                            liveSpace.subdivisions.forEach { sub ->
                                val subSlots = availableSlots.filter { it.sourceFormulaId == sub.id }
                                val isOccupied = subSlots.isNotEmpty() && subSlots.all { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                val priceSummary = when (sub.pricing.strategyType) {
                                    RentalStrategyType.MONTHLY -> "$${sub.pricing.monthly?.rateUsd?.toInt() ?: 0}/mo"
                                    RentalStrategyType.HOURLY -> "from $${sub.pricing.hourly?.cellPrices?.values?.minOrNull()?.toInt() ?: 0}/hr"
                                    RentalStrategyType.SHIFT_BASED -> "from $${sub.pricing.shiftBased?.shifts?.filter { !it.isUnavailable }?.minOfOrNull { it.price }?.toInt() ?: 0}/shift"
                                    RentalStrategyType.DAY_BASED -> "from $${sub.pricing.dayBased?.distribution?.values?.minOfOrNull { it.price }?.toInt() ?: 0}/day"
                                }
                                SubdivisionRentalCard(
                                    name = sub.name,
                                    typeBadge = sub.type.displayName,
                                    imageUrls = sub.imageUrls,
                                    amenities = sub.amenities,
                                    hashtags = sub.hashtags,
                                    priceSummary = "${sub.pricing.strategyType.displayName} · $priceSummary",
                                    isOccupied = isOccupied,
                                    onClick = {
                                        val formula = SpaceCalculationUtils.representativeFormula(subSlots, BookingRecurrence.FLAT)
                                        if (formula != null) onSelectFormula(formula)
                                        selectedSubdivisionId = sub.id
                                        showAvailabilityPanel = true
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

    // Sends a booking request straight off a tapped, live slot — the specialist
    // never leaves the availability sheet to fill out a separate form. Reuses the
    // exact repository call RentalBookingDialog itself calls (createBookingRequest)
    // so an ACCEPTED request locks this same slot everywhere else that reads
    // isSlotLocked/isCalendarDateLocked, including the host's own Manage Listing
    // screen.
    fun sendSlotRequest(slot: RentableSlot) {
        val user = currentUser
        val strategyType = slot.strategyType
        if (user == null || strategyType == null) return
        // B4: KYC gate — require at least a profile picture before booking
        if (user.kycLevel < 1) {
            android.widget.Toast.makeText(context, "Please add a profile picture before making booking requests.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        isSendingSlotRequest = true
        coroutineScope.launch {
            val formula = SpaceCalculationUtils.representativeFormula(listOf(slot), BookingRecurrence.FLAT)
            if (formula == null) {
                isSendingSlotRequest = false
                return@launch
            }
            val isoDate = SpaceCalculationUtils.nextDateForWeekday(slot.day)
            val subdivision = liveSpace.subdivisions.find { it.id == slot.sourceFormulaId }
            val price = slot.pricesByRecurrence[BookingRecurrence.FLAT] ?: formula.rateUsd
            val shiftLabel = if (strategyType == RentalStrategyType.SHIFT_BASED) slot.groupLabel.substringAfter("• ") else ""
            val (request, synced) = viewModel.repository.createBookingRequest(
                space = liveSpace,
                formula = formula,
                practitioner = user,
                startDate = isoDate,
                durationMonths = 1,
                notes = "Requested via live slot selection",
                selectedDays = listOf(slot.day),
                selectedCalendarDates = if (strategyType == RentalStrategyType.MONTHLY) emptyList() else listOf(isoDate),
                selectedStartHour = slot.startTime,
                selectedEndHour = slot.endTime,
                selectedShift = shiftLabel,
                calculatedTotalUsd = price,
                subdivisionId = subdivision?.id,
                subdivisionName = subdivision?.name
            )
            isSendingSlotRequest = false
            showSendConfirm = false
            if (synced) {
                selectedAvailableSlot = null
                showAvailabilityPanel = false
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

    // Check Availability slide-up panel (item 3.2), reworked into a real booking
    // entry point: the real per-cell rented/available matrix for whichever renting
    // option is currently selected above (reusing the exact same shared slot data,
    // SpaceCalculationUtils.buildAllSlotsForSpace/isSlotLocked, the Renting Options
    // cards already use), but every open slot is now tappable — tapping one surfaces
    // a "Send Request" bar, which opens the confirm popup below.
    if (showAvailabilityPanel) {
        // Per-subdivision slot filter: when user tapped a specific subdivision card,
        // restrict the slot list to that subdivision's slots.
        val panelSlots = remember(availableSlots, selectedSubdivisionId) {
            if (selectedSubdivisionId != null)
                availableSlots.filter { it.sourceFormulaId == selectedSubdivisionId }
            else availableSlots
        }
        val panelGroups = remember(panelSlots) {
            panelSlots.filter { it.strategyType != null }
                .groupBy { it.strategyType!! }
                .toList()
                .sortedBy { (type, _) -> type.ordinal }
        }

        // Which strategy accordion is open. Auto-open the first group.
        var expandedStrategy by remember(panelGroups) {
            mutableStateOf(panelGroups.firstOrNull()?.first)
        }

        ModalBottomSheet(
            onDismissRequest = {
                showAvailabilityPanel = false
                selectedAvailableSlot = null
                selectedHourlySlots = emptySet()
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProSectionHeader(
                    title = "Option Availability",
                    subtitle = if (selectedSubdivisionId != null)
                        liveSpace.subdivisions.find { it.id == selectedSubdivisionId }?.name?.let { "Showing slots for: $it" }
                            ?: "Filtered by subdivision"
                    else "Tap a slot to select it, then send a booking request.",
                    icon = Icons.Default.EventAvailable
                )

                if (panelGroups.isEmpty()) {
                    Text(
                        "No availability data for this option yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    panelGroups.forEach { (strategyType, slots) ->
                        val isExpanded = expandedStrategy == strategyType
                        val lockedCount = slots.count { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                        val availableCount = slots.size - lockedCount

                        // Accordion header
                        Surface(
                            onClick = { expandedStrategy = if (isExpanded) null else strategyType },
                            color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = if (isExpanded) RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp) else MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        strategyType.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        color = if (availableCount > 0) StatusSuccessContainer else StatusErrorContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "$availableCount open",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (availableCount > 0) StatusSuccess else StatusError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (isExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Accordion body
                        if (isExpanded) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    when (strategyType) {
                                        RentalStrategyType.MONTHLY -> {
                                            val slot = slots.first()
                                            val isLocked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                            val isSelected = selectedAvailableSlot == slot
                                            Text(
                                                "Full-month exclusive lease • $${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}/mo",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Surface(
                                                onClick = { if (!isLocked) selectedAvailableSlot = if (isSelected) null else slot },
                                                enabled = !isLocked,
                                                color = when {
                                                    isLocked -> StatusErrorContainer
                                                    isSelected -> FreshGreen.copy(alpha = 0.22f)
                                                    else -> StatusSuccessContainer
                                                },
                                                shape = MaterialTheme.shapes.small,
                                                border = if (isSelected) BorderStroke(1.5.dp, FreshGreen) else null
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isLocked) Icons.Default.Lock else if (isSelected) Icons.Default.CheckCircle else Icons.Default.EventAvailable,
                                                        contentDescription = null,
                                                        tint = if (isLocked) StatusError else if (isSelected) FreshGreen else StatusSuccess,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        if (isLocked) "Currently Rented" else if (isSelected) "Selected" else "Available Now",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isLocked) StatusError else if (isSelected) FreshGreen else StatusSuccess
                                                    )
                                                }
                                            }
                                        }
                                        RentalStrategyType.HOURLY -> {
                                            // Multi-select: each day's hours are individually toggleable.
                                            val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun",
                                                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                                            slots.groupBy { it.day }.toList()
                                                .sortedBy { (day, _) -> dayOrder.indexOfFirst { it.equals(day, ignoreCase = true) }.let { if (it < 0) 99 else it } }
                                                .forEach { (day, daySlots) ->
                                                    Text(
                                                        day,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        items(daySlots.sortedBy { it.startTime }) { slot ->
                                                            val locked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                                            val isSelected = slot in selectedHourlySlots
                                                            Surface(
                                                                onClick = {
                                                                    if (!locked) {
                                                                        selectedHourlySlots = if (isSelected)
                                                                            selectedHourlySlots - slot
                                                                        else
                                                                            selectedHourlySlots + slot
                                                                    }
                                                                },
                                                                enabled = !locked,
                                                                color = when {
                                                                    locked -> StatusErrorContainer
                                                                    isSelected -> FreshGreen.copy(alpha = 0.22f)
                                                                    else -> StatusSuccessContainer
                                                                },
                                                                shape = MaterialTheme.shapes.extraSmall,
                                                                border = if (isSelected) BorderStroke(1.5.dp, FreshGreen) else null
                                                            ) {
                                                                Text(
                                                                    slot.startTime,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (locked) StatusError else if (isSelected) FreshGreen else StatusSuccess,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            if (selectedHourlySlots.isNotEmpty()) {
                                                val totalHourlyPrice = selectedHourlySlots.sumOf {
                                                    it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0
                                                }
                                                Text(
                                                    "${selectedHourlySlots.size} slot${if (selectedHourlySlots.size == 1) "" else "s"} selected · $${totalHourlyPrice.toInt()} USD total",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = FreshGreen
                                                )
                                            }
                                        }
                                        RentalStrategyType.SHIFT_BASED -> {
                                            val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                            slots.groupBy { it.day }.toList().sortedBy { (day, _) ->
                                                val idx = dayOrder.indexOfFirst { it.equals(day, ignoreCase = true) }
                                                if (idx >= 0) idx else 99
                                            }.forEach { (day, daySlots) ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
                                                ) {
                                                    Text(
                                                        text = day,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                                daySlots.forEach { slot ->
                                                    val locked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                                    val isSelected = selectedAvailableSlot == slot
                                                    val displayShiftLabel = slot.label
                                                        .replace(Regex("^${Regex.escape(day)}\\s*[-–•]?\\s*", RegexOption.IGNORE_CASE), "")
                                                        .trim().ifBlank { slot.label }
                                                    Surface(
                                                        onClick = { if (!locked) selectedAvailableSlot = if (isSelected) null else slot },
                                                        enabled = !locked,
                                                        color = when {
                                                            locked -> StatusErrorContainer.copy(alpha = 0.5f)
                                                            isSelected -> FreshGreen.copy(alpha = 0.2f)
                                                            else -> MaterialTheme.colorScheme.surface
                                                        },
                                                        shape = MaterialTheme.shapes.small,
                                                        border = BorderStroke(
                                                            if (isSelected) 1.5.dp else 1.dp,
                                                            if (isSelected) FreshGreen else if (locked) StatusError.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 10.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (locked) StatusError else if (isSelected) FreshGreen else StatusSuccess))
                                                                Text(displayShiftLabel, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                                            }
                                                            Text(
                                                                if (locked) "Rented" else if (isSelected) "Selected" else "Available",
                                                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                                                color = if (locked) StatusError else if (isSelected) FreshGreen else StatusSuccess
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        RentalStrategyType.DAY_BASED -> {
                                            slots.sortedBy { it.day }.forEach { slot ->
                                                val locked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                                val isSelected = selectedAvailableSlot == slot
                                                Surface(
                                                    onClick = { if (!locked) selectedAvailableSlot = if (isSelected) null else slot },
                                                    enabled = !locked,
                                                    color = when {
                                                        locked -> StatusErrorContainer.copy(alpha = 0.5f)
                                                        isSelected -> FreshGreen.copy(alpha = 0.2f)
                                                        else -> MaterialTheme.colorScheme.surface
                                                    },
                                                    shape = MaterialTheme.shapes.small,
                                                    border = BorderStroke(
                                                        if (isSelected) 1.5.dp else 1.dp,
                                                        if (isSelected) FreshGreen else if (locked) StatusError.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (locked) StatusError else if (isSelected) FreshGreen else StatusSuccess))
                                                            Text(slot.day, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Text(
                                                            if (locked) "Rented" else if (isSelected) "Selected" else "Available",
                                                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                                            color = if (locked) StatusError else if (isSelected) FreshGreen else StatusSuccess
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

                    // Send Request bar — shown when any slot is selected
                    val hasSelection = selectedAvailableSlot != null || selectedHourlySlots.isNotEmpty()
                    if (hasSelection) {
                        val chosenSlot = selectedAvailableSlot
                        val slotLabel = if (selectedHourlySlots.isNotEmpty()) {
                            "${selectedHourlySlots.size} hour slot${if (selectedHourlySlots.size == 1) "" else "s"}"
                        } else {
                            chosenSlot?.label ?: ""
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (selectedHourlySlots.isNotEmpty()) {
                                        // Use the first hourly slot as the representative for the confirm dialog
                                        selectedAvailableSlot = selectedHourlySlots.first()
                                    }
                                    showSendConfirm = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Request", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val message = "Hello, I am interested in booking '${liveSpace.title}' for: $slotLabel. Is it still available?"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com/send?phone=${liveSpace.ownerPhone}&text=${java.net.URLEncoder.encode(message, "UTF-8")}"))
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
                }
            }
        }
    }

    // Confirm-and-send popup, gated on a slot actually being selected — shows the
    // real request details (space, location, room, chosen slot, price) before
    // anything is submitted.
    val slotPendingConfirm = selectedAvailableSlot
    if (showSendConfirm && slotPendingConfirm != null) {
        val subdivision = liveSpace.subdivisions.find { it.id == slotPendingConfirm.sourceFormulaId }
        val price = slotPendingConfirm.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0
        AlertDialog(
            onDismissRequest = { if (!isSendingSlotRequest) showSendConfirm = false },
            icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
            title = { Text("Confirm Your Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Space" to liveSpace.title,
                        "Location" to "${liveSpace.district}, ${liveSpace.governorate.displayName}",
                        "Room" to (subdivision?.name ?: "Whole Space"),
                        "Time Slot" to slotPendingConfirm.label,
                        "Price" to "$${price.toInt()} USD"
                    ).forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { sendSlotRequest(slotPendingConfirm) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubdivisionRentalCard(
    name: String,
    typeBadge: String,
    imageUrls: List<String>,
    amenities: List<String>,
    hashtags: List<String>,
    priceSummary: String,
    isOccupied: Boolean,
    onClick: () -> Unit
) {
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
