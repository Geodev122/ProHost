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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    // Set when the user tapped a specific division/subdivision card on Explore
    // (list or map) — pre-selects that division and opens its availability sheet
    // immediately instead of landing on the generic whole-space view.
    intendedSubdivisionId: String? = null,
    // Explore's "Check availability": arrive with that room's availability sheet open.
    openAvailability: Boolean = false,
    // After a request is sent: open My Rentals at that booking.
    onViewRequest: (bookingId: String) -> Unit = {},
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
            viewModel.shareListing(context, liveSpace.title, shareText, liveSpace)
        },
        onCopyLinkClick = {
            viewModel.copyListingLink(context, ShareLinks.forListing(liveSpace.id), liveSpace)
        },
        isSaved = currentUser?.savedSpaceIds?.contains(liveSpace.id) == true,
        onToggleSave = { viewModel.toggleSavedSpace(liveSpace.id) },
        intendedSubdivisionId = intendedSubdivisionId,
        openAvailability = openAvailability,
        onViewRequest = onViewRequest,
        onBack = onBack
    )
}

/**
 * Dumb Presentation Screen for Space Details.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    intendedSubdivisionId: String? = null,
    openAvailability: Boolean = false,
    onViewRequest: (bookingId: String) -> Unit = {},
    onBack: () -> Unit
) {
    val liveSpace = space
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showShareMenu by remember { mutableStateOf(false) }
    // "hidden" | "peek" | "full"
    // peek = division selected, animated slice visible above bottom of screen; press to expand
    // full = full ModalBottomSheet open
    val availabilityPanelStateHolder = remember { mutableStateOf("hidden") }
    var availabilityPanelState by availabilityPanelStateHolder
    val showAvailabilityPanel = availabilityPanelState == "full"
    // Opens straight to full height: a half-expanded first stop made the long, scrollable
    // content drag the sheet instead of scrolling it.
    val availabilitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Hosts and admins can look at availability but only specialists can request slots.
    val isSpecialistViewer = currentUserRole != UserRole.PRO_HOST && currentUserRole != UserRole.ADMIN
    // Multi-select: SHIFT_BASED, DAY_BASED, MONTHLY slots
    val selectedSlotsState = remember { mutableStateOf(setOf<RentableSlot>()) }
    var selectedSlots by selectedSlotsState
    // Multi-day HOURLY selection: day → set of selected start-hour strings
    val selectedHoursPerDayState = remember { mutableStateOf(mapOf<String, Set<String>>()) }
    var selectedHoursPerDay by selectedHoursPerDayState
    val showSendConfirmState = remember { mutableStateOf(false) }
    var showSendConfirm by showSendConfirmState
    var isSendingSlotRequest by remember { mutableStateOf(false) }
    // Photo + verified phone are checked in place (RequirementsSheet) when Request is tapped;
    // the selected slots stay put and the confirm step opens once both are done.
    var showRequirements by remember { mutableStateOf(false) }
    // The request just sent: drives the "Request sent" sheet (View request / WhatsApp host).
    var sentRequest by remember { mutableStateOf<RentalBookingRequest?>(null) }
    fun requestWithRequirements() {
        val user = currentUser ?: return
        if (user.canTransact(com.example.data.auth.PhoneLink.isLinked())) showSendConfirm = true else showRequirements = true
    }
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

    // The single way to choose a room. The folder tabs (page and availability sheet),
    // the Explore deep link and the card buttons all come through here, so the page,
    // the bottom strip and the availability sheet always agree on which room is active.
    // Switching rooms drops any slots picked for the previous one.
    fun selectRoom(sub: Subdivision, panelState: String? = null) {
        if (selectedSubdivisionId != sub.id) {
            selectedSlots = emptySet()
            selectedHoursPerDay = emptyMap()
        }
        // Show the room's lowest per-slot price on the bottom strip, not the sum of every
        // open slot — representativeFormula sums whatever it's given, so pass one slot.
        val cheapestSlot = availableSlots
            .filter { it.sourceFormulaId == sub.id }
            .minByOrNull { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: Double.MAX_VALUE }
        val formula = cheapestSlot?.let {
            SpaceCalculationUtils.representativeFormula(listOf(it), BookingRecurrence.FLAT)
        }
        if (formula != null) onSelectFormula(formula)
        if (selectedSubdivisionId != sub.id) com.example.analytics.AnalyticsTracker.selectRoom(liveSpace, sub)
        selectedSubdivisionId = sub.id
        if (panelState != null) availabilityPanelState = panelState
    }

    LaunchedEffect(availabilityPanelState == "full") {
        if (availabilityPanelState == "full") {
            val sub = liveSpace.subdivisions.firstOrNull { it.id == selectedSubdivisionId }
            val open = availableSlots.count {
                (sub == null || it.sourceFormulaId == sub.id) &&
                    !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings)
            }
            com.example.analytics.AnalyticsTracker.viewAvailability(liveSpace, sub, open)
        }
    }

    val roomsRequester = remember { BringIntoViewRequester() }

    // Arrived here via a division-card tap on Explore: that room's tab is selected and its
    // card scrolled into view. "Check availability" on the card also opens the sheet.
    LaunchedEffect(intendedSubdivisionId, liveSpace.id) {
        val sub = intendedSubdivisionId?.let { id -> liveSpace.subdivisions.firstOrNull { it.id == id } }
        if (sub != null) {
            selectRoom(sub, if (openAvailability && isSpecialistViewer) "full" else "peek")
            kotlinx.coroutines.delay(150)
            roomsRequester.bringIntoView()
        }
    }
    // A space with rooms always has one active, so the sheet is never a mixed view of
    // every room (per-attendee rooms need their own attendee count and tiers).
    val roomIds = liveSpace.subdivisions.map { it.id }
    LaunchedEffect(roomIds) {
        if (roomIds.isNotEmpty() && selectedSubdivisionId !in roomIds) {
            selectRoom(liveSpace.subdivisions.first())
        }
    }

    Scaffold(
        // Nested inside the app-shell Scaffold's already-inset content area — see
        // MyFavoritesScreen's identical fix for why this is needed.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // No TopAppBar — back/save/share are overlaid on the fullscreen hero instead.
        bottomBar = {
            if (!showAvailabilityPanel) {
                val stripSub = liveSpace.subdivisions.firstOrNull { it.id == selectedSubdivisionId }
                val openSlotCount = availableSlots.count {
                    (stripSub == null || it.sourceFormulaId == stripSub.id) &&
                        !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings)
                }
                val (fallbackPrice, fallbackUnit) = remember(liveSpace) { SpaceCalculationUtils.lowestPriceSummary(liveSpace) }
                // A per-attendee room's slot prices are availability markers; its real
                // price is per person for the whole booking.
                val stripAttendeeSub = stripSub?.takeIf { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
                val stripPerPerson = stripAttendeeSub?.let {
                    com.example.ui.util.AttendeePricing.lowestPricePerPerson(it, architectureSchema.attendeePackages)
                }
                DetailsBookingBar(
                    roomName = stripSub?.name,
                    showAvailability = isSpecialistViewer,
                    hasSchedule = availableSlots.any { stripSub == null || it.sourceFormulaId == stripSub.id },
                    openSlotCount = openSlotCount,
                    priceUsd = stripPerPerson ?: selectedFormula?.rateUsd ?: fallbackPrice,
                    pricePrefix = if (stripAttendeeSub != null) "from " else "",
                    priceUnit = if (stripAttendeeSub != null) {
                        SpaceCalculationUtils.PER_PERSON_UNIT
                    } else {
                        selectedFormula?.let { SpaceCalculationUtils.rateUnitLabel(it.type) } ?: fallbackUnit
                    },
                    formulaName = selectedFormula?.type?.displayName ?: "Full Month",
                    isPreview = currentUserRole == UserRole.PRO_HOST || currentUserRole == UserRole.ADMIN,
                    onOpenAvailability = { availabilityPanelState = "full" },
                    onMessage = onWhatsAppClick
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Fullscreen hero — fills width, extends to status bar. Back/save/share
            // buttons float over the top-start and top-end corners with a subtle scrim.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            ) {
                if (liveSpace.imageUrls.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { liveSpace.imageUrls.size })
                    val heroScope = rememberCoroutineScope()
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(liveSpace.imageUrls[page]).size(800).build(),
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
                        if (pagerState.currentPage > 0) {
                            IconButton(
                                onClick = { heroScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 4.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = "Previous photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (pagerState.currentPage < liveSpace.imageUrls.size - 1) {
                            IconButton(
                                onClick = { heroScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "Next photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                                        MaterialTheme.proColors.brandHeaderStart,
                                        MaterialTheme.proColors.brandHeaderEnd
                                    )
                                )
                            )
                    )
                }
                // Overlay nav strip — back (start) and save/share (end), always on top.
                // statusBarsPadding ensures buttons sit below system status bar while the
                // hero image bleeds behind it (full bleed behind the status bar).
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.40f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.40f))
                    ) {
                        Icon(
                            if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isSaved) "Unsave" else "Save",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(
                            onClick = { showShareMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.40f))
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Listing") },
                                onClick = { showShareMenu = false; onShareClick() },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Link") },
                                onClick = { showShareMenu = false; onCopyLinkClick() },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
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
                ProHostAlertBanner(
                    message = "This host is on verification process — their listing is temporarily hidden from new searches, but your existing " +
                        "access here is unaffected.",
                    severity = ProHostAlertSeverity.INFO,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                )
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
                                    text = "Your In-App Rental Request ${req.publicCode}",
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
                                        color = MaterialTheme.proColors.warning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (req.status == BookingRequestStatus.ACCEPTED) {
                                    Text(
                                        text = "Confirmed! Space hours (${req.formula.daysOfWeek.joinToString()}" +
                                            " ${req.formula.startHour}-${req.formula.endHour}) are locked" +
                                            ".",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.proColors.info,
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
                ProSurfaceCard(modifier = Modifier.bringIntoViewRequester(roomsRequester)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProSectionHeader(
                            title = "Rental Options",
                            subtitle = if (liveSpace.subdivisions.isNotEmpty())
                                "${liveSpace.subdivisions.size} space${if (liveSpace.subdivisions.size == 1) "" else "s"} — pick a tab to see that space and its availability"
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
                                    RentalStrategyType.MONTHLY -> "$${liveSpace.pricing.monthly?.rateUsd?.toInt() ?: liveSpace.baseMonthlyRateUsd.toInt()}${SpaceCalculationUtils.strategyUnitLabel(RentalStrategyType.MONTHLY)}"
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
                                    null -> SpaceCalculationUtils.lowestPriceSummary(liveSpace).let { (p, u) -> "$${p.toInt()}$u" }
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
                                },
                                onCheckAvailability = {
                                    selectedSubdivisionId = null
                                    availabilityPanelState = "full"
                                }
                            )
                        } else {
                            val rooms = liveSpace.subdivisions
                            val sub = rooms.firstOrNull { it.id == selectedSubdivisionId } ?: rooms.first()
                            RoomFolderTabs(
                                rooms = rooms,
                                selectedId = sub.id,
                                onSelect = { selectRoom(it) }
                            )
                            RoomFolderPanel(firstTabSelected = sub.id == rooms.first().id) {
                                val subSlots = availableSlots.filter { it.sourceFormulaId == sub.id }
                                val isOccupied = subSlots.isNotEmpty() &&
                                    subSlots.all { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                val perAttendeeFrom = if (com.example.ui.util.AttendeePricing.isPerAttendee(sub)) {
                                    com.example.ui.util.AttendeePricing.lowestPricePerPerson(sub, architectureSchema.attendeePackages)
                                } else null
                                val priceSummary = if (com.example.ui.util.AttendeePricing.isPerAttendee(sub)) {
                                    "from $${perAttendeeFrom?.toInt() ?: 0}/person"
                                } else when (sub.pricing.strategyType) {
                                    RentalStrategyType.MONTHLY -> "$${sub.pricing.monthly?.rateUsd?.toInt() ?: 0}${SpaceCalculationUtils.strategyUnitLabel(RentalStrategyType.MONTHLY)}"
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
                                        priceSummary = if (com.example.ui.util.AttendeePricing.isPerAttendee(sub)) {
                                            "Per attendee · ${sub.pricing.strategyType.displayName} slots · $priceSummary"
                                        } else "${sub.pricing.strategyType.displayName} · $priceSummary",
                                        isOccupied = isOccupied,
                                        capacity = sub.capacity,
                                        hasCustomHours = sub.scheduleOverride != null,
                                        isPerAttendee = com.example.ui.util.AttendeePricing.isPerAttendee(sub)
                                    ),
                                    onClick = { selectRoom(sub, "peek") },
                                    onCheckAvailability = { selectRoom(sub, "full") }
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
                                        tint = MaterialTheme.proColors.success,
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
    // Per-attendee rooms: the total is the attendee quote (one per-person price for the
    // whole booking), never the slots' availability-marker prices.
    fun sendMultiSlotRequest(
        slotsToSend: List<RentableSlot>,
        hourlySelections: Map<String, Set<String>>,
        allAvailableSlots: List<RentableSlot>,
        attendeeQuote: com.example.ui.util.AttendeePricing.Quote? = null
    ) {
        val user = currentUser
        if (user == null) return
        if (!user.canTransact(com.example.data.auth.PhoneLink.isLinked())) {
            showRequirements = true
            return
        }
        // Flatten HOURLY hourlySelections to RentableSlot list
        val hourlySlotList = hourlySelections.flatMap { (day, hours) ->
            allAvailableSlots.filter { it.day == day && it.startTime in hours }
        }
        val allSlots = (slotsToSend + hourlySlotList).distinctBy { it.label + it.day + it.startTime }
        val primarySlot = allSlots.firstOrNull() ?: return
        val strategyType = primarySlot.strategyType ?: return
        val targetSub = liveSpace.subdivisions.find { it.id == primarySlot.sourceFormulaId }
        if (com.example.ui.util.AttendeePricing.isPerAttendee(targetSub) && attendeeQuote == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Choose a valid number of attendees first.") }
            return
        }
        isSendingSlotRequest = true
        coroutineScope.launch {
            val formula = SpaceCalculationUtils.representativeFormula(allSlots, BookingRecurrence.FLAT)
            if (formula == null) {
                isSendingSlotRequest = false
                return@launch
            }
            val isoDate = SpaceCalculationUtils.nextDateForWeekday(primarySlot.day)
            val subdivision = liveSpace.subdivisions.find { it.id == primarySlot.sourceFormulaId }
            val isAttendeeMode = com.example.ui.util.AttendeePricing.isPerAttendee(subdivision) && attendeeQuote != null
            val price = if (isAttendeeMode) {
                attendeeQuote.totalUsd
            } else {
                allSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: formula.rateUsd }
            }
            val shiftLabel = if (strategyType == RentalStrategyType.SHIFT_BASED) primarySlot.groupLabel.substringAfter("• ") else ""
            val slotSummary = allSlots.joinToString("; ") { it.label }
            val notes = buildString {
                append("Requested via live slot selection")
                if (allSlots.size > 1) append(" — $slotSummary")
                if (isAttendeeMode) {
                    append(" | ${com.example.ui.util.AttendeePricing.describe(attendeeQuote!!)}")
                }
            }
            // createBookingRequest throws (e.g. "already have a pending request for this
            // space"); never let that reach the coroutine as an uncaught crash.
            val sendResult = try {
                viewModel.repository.createBookingRequest(
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
                attendeeCount = if (isAttendeeMode) attendeeQuote?.attendees ?: 0 else 0,
                selectedAttendeePackageId = if (isAttendeeMode) attendeeQuote?.tier?.id else null,
                attendeePackageName = if (isAttendeeMode) attendeeQuote?.tier?.name else null,
                attendeePackagePriceUsd = if (isAttendeeMode) attendeeQuote?.tier?.pricePerAttendeeUsd ?: 0.0 else 0.0
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                isSendingSlotRequest = false
                throw e
            } catch (e: Exception) {
                isSendingSlotRequest = false
                com.example.analytics.AnalyticsTracker.bookingRequestFailed(com.example.analytics.AnalyticsTracker.errorCode(e))
                snackbarHostState.showSnackbar(
                    e.message?.takeIf { (e is IllegalStateException || e is IllegalArgumentException) && it.isNotBlank() }
                        ?: "Couldn't send your request — check your connection and try again."
                )
                return@launch
            }
            val (request, synced) = sendResult
            isSendingSlotRequest = false
            showSendConfirm = false
            if (synced) {
                com.example.analytics.AnalyticsTracker.bookingRequest(
                    space = liveSpace,
                    sub = subdivision,
                    valueUsd = price,
                    strategy = formula.type.name,
                    attendeeCount = if (isAttendeeMode) attendeeQuote?.attendees else null,
                    isRebook = false
                )
                selectedSlots = emptySet()
                selectedHoursPerDay = emptyMap()
                availabilitySheetState.hide()
                availabilityPanelState = "hidden"
                sentRequest = request
            } else {
                com.example.analytics.AnalyticsTracker.bookingRequestFailed("offline")
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
        AvailabilitySheet(
            liveSpace = liveSpace,
            viewModel = viewModel,
            acceptedBookings = acceptedBookings,
            architectureSchema = architectureSchema,
            strategyPreviewGroups = strategyPreviewGroups,
            isSpecialistViewer = isSpecialistViewer,
            selectedSubdivisionId = selectedSubdivisionId,
            availabilitySheetState = availabilitySheetState,
            isSendingSlotRequest = isSendingSlotRequest,
            availabilityPanelStateHolder = availabilityPanelStateHolder,
            selectedSlotsState = selectedSlotsState,
            selectedHoursPerDayState = selectedHoursPerDayState,
            showSendConfirmState = showSendConfirmState,
            onSelectRoom = { selectRoom(it) },
            onRequest = { requestWithRequirements() },
            onSendRequest = { slots, hours, all, quote -> sendMultiSlotRequest(slots, hours, all, quote) }
        )
    }

    // "Request sent": the two next steps a specialist takes — see it in My Rentals, or
    // message the host right away (same WhatsApp text as the My Rentals card).
    sentRequest?.let { request ->
        ProHostBottomSheet(onDismissRequest = { sentRequest = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                ProSectionHeader(
                    title = "Request sent",
                    subtitle = "The host has been notified. You'll get a push when they answer.",
                    icon = Icons.Default.CheckCircle
                )
                ProPrimaryButton(
                    text = "View request",
                    onClick = {
                        sentRequest = null
                        onViewRequest(request.id)
                    },
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    modifier = Modifier.fillMaxWidth()
                )
                if (liveSpace.ownerPhone.isNotBlank()) {
                    ProOutlinedButton(
                        text = "Message host on WhatsApp",
                        onClick = {
                            sentRequest = null
                            viewModel.launchWhatsAppInquiry(context, liveSpace, request.formula, request)
                        },
                        icon = Icons.AutoMirrored.Filled.Chat,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Email Inquiry Dialog
    if (showRequirements) {
        currentUser?.let { user ->
            RequirementsSheet(
                user = user,
                viewModel = viewModel,
                onDismiss = { showRequirements = false },
                onReady = {
                    showRequirements = false
                    showSendConfirm = true
                }
            )
        }
    }

}
