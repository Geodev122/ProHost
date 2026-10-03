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
    onNavigateToProfile: () -> Unit = {},
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
        intendedSubdivisionId = intendedSubdivisionId,
        onNavigateToProfile = onNavigateToProfile,
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
    onNavigateToProfile: () -> Unit = {},
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
    var availabilityPanelState by remember { mutableStateOf("hidden") }
    val showAvailabilityPanel = availabilityPanelState == "full"
    // Opens straight to full height: a half-expanded first stop made the long, scrollable
    // content drag the sheet instead of scrolling it.
    val availabilitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Hosts and admins can look at availability but only specialists can request slots.
    val isSpecialistViewer = currentUserRole != UserRole.PRO_HOST && currentUserRole != UserRole.ADMIN
    // Multi-select: SHIFT_BASED, DAY_BASED, MONTHLY slots
    var selectedSlots by remember { mutableStateOf(setOf<RentableSlot>()) }
    // Multi-day HOURLY selection: day → set of selected start-hour strings
    var selectedHoursPerDay by remember { mutableStateOf(mapOf<String, Set<String>>()) }
    var showSendConfirm by remember { mutableStateOf(false) }
    var isSendingSlotRequest by remember { mutableStateOf(false) }
    var showProfilePicRequiredDialog by remember { mutableStateOf(false) }
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
        selectedSubdivisionId = sub.id
        if (panelState != null) availabilityPanelState = panelState
    }

    val roomsRequester = remember { BringIntoViewRequester() }

    // Arrived here via a division-card tap on Explore: that room's tab is selected,
    // its card is scrolled into view, and a specialist also gets its availability sheet.
    LaunchedEffect(intendedSubdivisionId, liveSpace.id) {
        val sub = intendedSubdivisionId?.let { id -> liveSpace.subdivisions.firstOrNull { it.id == id } }
        if (sub != null) {
            selectRoom(sub, if (isSpecialistViewer) "full" else "peek")
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
                            tint = if (isSaved) MaterialTheme.colorScheme.error else LocalContentColor.current
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
                // One availability trigger, same design whether or not a division is
                // selected — it used to switch between two differently styled bars.
                if (isSpecialistViewer && !showAvailabilityPanel) {
                    val stripSub = liveSpace.subdivisions.firstOrNull { it.id == selectedSubdivisionId }
                    val hasSlots = availableSlots.isNotEmpty()
                    val openSlotCount = availableSlots.count {
                        (stripSub == null || it.sourceFormulaId == stripSub.id) &&
                            !SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings)
                    }
                    val stripContent = if (hasSlots) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        color = if (hasSlots) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasSlots) { availabilityPanelState = "full" }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = stripContent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stripSub?.name ?: "Check availability",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = stripContent,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        !hasSlots -> "No bookable slots configured yet"
                                        openSlotCount > 0 -> "$openSlotCount slot(s) available · Tap to choose"
                                        else -> "Fully booked · Tap to see the schedule"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = stripContent.copy(alpha = 0.8f)
                                )
                            }
                            if (hasSlots) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open availability", tint = stripContent)
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
                            // A per-attendee room's slot prices are availability markers;
                            // its real price is per person for the whole booking.
                            val stripAttendeeSub = liveSpace.subdivisions
                                .find { it.id == selectedSubdivisionId }
                                ?.takeIf { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
                            val stripPerPerson = stripAttendeeSub?.let {
                                com.example.ui.util.AttendeePricing.lowestPricePerPerson(it, architectureSchema.attendeePackages)
                            }
                            val price = stripPerPerson ?: selectedFormula?.rateUsd ?: fallbackPrice
                            val priceUnit = if (stripAttendeeSub != null) {
                                SpaceCalculationUtils.PER_PERSON_UNIT
                            } else {
                                selectedFormula?.let { SpaceCalculationUtils.rateUnitLabel(it.type) } ?: fallbackUnit
                            }
                            Text(
                                text = (if (stripAttendeeSub != null) "from " else "") + "$${price.toInt()} USD$priceUnit",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
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
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
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
                    .height(180.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
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
        if (user.profilePictureUrl.isNullOrBlank()) {
            showProfilePicRequiredDialog = true
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
            val price = if (isAttendeeMode && attendeeQuote != null) {
                attendeeQuote.totalUsd
            } else {
                allSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: formula.rateUsd }
            }
            val shiftLabel = if (strategyType == RentalStrategyType.SHIFT_BASED) primarySlot.groupLabel.substringAfter("• ") else ""
            val slotSummary = allSlots.joinToString("; ") { it.label }
            val notes = buildString {
                append("Requested via live slot selection")
                if (allSlots.size > 1) append(" — $slotSummary")
                if (isAttendeeMode && attendeeQuote != null) {
                    append(" | ${com.example.ui.util.AttendeePricing.describe(attendeeQuote)}")
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
                attendeeCount = if (isAttendeeMode) attendeeQuote?.attendees ?: 0 else 0,
                selectedAttendeePackageId = if (isAttendeeMode) attendeeQuote?.tier?.id else null,
                attendeePackageName = if (isAttendeeMode) attendeeQuote?.tier?.name else null,
                attendeePackagePriceUsd = if (isAttendeeMode) attendeeQuote?.tier?.pricePerAttendeeUsd ?: 0.0 else 0.0
            )
            isSendingSlotRequest = false
            showSendConfirm = false
            if (synced) {
                selectedSlots = emptySet()
                selectedHoursPerDay = emptyMap()
                availabilitySheetState.hide()
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
        val attendeeSub = selectedSubdivision?.takeIf { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
        val isAttendeeMode = attendeeSub != null
        val attendeeTiers = remember(attendeeSub, architectureSchema) {
            attendeeSub?.let { com.example.ui.util.AttendeePricing.tiersFor(it, architectureSchema.attendeePackages) } ?: emptyList()
        }
        val attendeeMin = attendeeSub?.let { com.example.ui.util.AttendeePricing.minAttendees(it, attendeeTiers) } ?: 1
        val attendeeMax = attendeeSub?.let { com.example.ui.util.AttendeePricing.maxAttendees(it, attendeeTiers) }

        // Per-strategy, per-day expand state
        var expandedDaysByStrategy by remember(sheetSlotGroups) { mutableStateOf(mapOf<RentalStrategyType, Set<String>>()) }
        // Attendee mode fields (scoped to sheet lifetime)
        var sheetAttendeeCount by remember(attendeeSub?.id) { mutableStateOf(attendeeMin) }
        val sheetAttendeeQuote = remember(attendeeSub, sheetAttendeeCount, architectureSchema) {
            attendeeSub?.let { com.example.ui.util.AttendeePricing.quote(it, sheetAttendeeCount, architectureSchema.attendeePackages) }
        }

        val dayOrder = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday","Mon","Tue","Wed","Thu","Fri","Sat","Sun")

        // Total selected slot count across strategies
        val totalSelectedSlots = selectedSlots.size + selectedHoursPerDay.values.sumOf { it.size }
        val hasSelection = totalSelectedSlots > 0

        val canSubmit = hasSelection && (!isAttendeeMode || sheetAttendeeQuote != null)

        ProHostBottomSheet(
            onDismissRequest = {
                availabilityPanelState = if (selectedSubdivisionId != null) "peek" else "hidden"
                selectedSlots = emptySet()
                selectedHoursPerDay = emptyMap()
            },
            modifier = Modifier.shadow(16.dp, SheetShape),
            sheetState = availabilitySheetState,
            contentWindowInsets = { WindowInsets(0) },
            // Blue wash (matches the "Press to see option availability" trigger bar)
            // so the trigger and the sheet it opens read as one consistent design.
            accentTint = true
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProSectionHeader(
                    title = "Check Availability",
                    subtitle = if (isAttendeeMode) "Enter attendee count, then tap days to select slots." else "Tap a day to expand and select open slots.",
                    icon = Icons.Default.EventAvailable
                )

                // Same folder tabs as the page: switching here switches the room the
                // sheet shows (and drops slots picked for the previous room).
                if (liveSpace.subdivisions.size > 1 && selectedSubdivisionId != null) {
                    RoomFolderTabs(
                        rooms = liveSpace.subdivisions,
                        selectedId = selectedSubdivisionId.orEmpty(),
                        onSelect = { selectRoom(it) }
                    )
                }

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
                            com.example.ui.components.AttendeeCountAndTiers(
                                count = sheetAttendeeCount,
                                min = attendeeMin,
                                max = attendeeMax,
                                tiers = attendeeTiers,
                                quote = sheetAttendeeQuote,
                                onCountChange = { sheetAttendeeCount = it }
                            )
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
                                                    color = MaterialTheme.colorScheme.errorContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("FULL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            } else {
                                                // Green available badge
                                                Surface(color = MaterialTheme.proColors.success.copy(alpha = 0.15f), shape = CircleShape) {
                                                    Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.proColors.success))
                                                        Text(
                                                            "$availInDay",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.proColors.success
                                                        )
                                                    }
                                                }
                                                // Red rented badge
                                                if (lockedInDay > 0) {
                                                    Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = CircleShape) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                                            Text(
                                                                "$lockedInDay",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.error
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
                                                            color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.18f) else MaterialTheme.proColors.successContainer,
                                                            shape = MaterialTheme.shapes.small,
                                                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.proColors.success) else null
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                                Icon(
                                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.EventAvailable,
                                                                    contentDescription = null,
                                                                    tint = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column {
                                                                    Text(
                                                                        if (isSelected) "Selected — Monthly Lease" else "Available — Monthly Lease",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
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
                                                                color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.22f) else MaterialTheme.proColors.successContainer,
                                                                shape = MaterialTheme.shapes.extraSmall,
                                                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.proColors.success) else null
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Text(
                                                                        slot.startTime,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
                                                                    )
                                                                    Text(
                                                                        if (isAttendeeMode) "Open" else "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = if (isSelected) {
                                                                            MaterialTheme.proColors.success.copy(alpha = 0.8f)
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
                                                            color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                                            shape = MaterialTheme.shapes.small,
                                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                                                                if (isSelected) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                                                            .background(if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success)
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
                                                                        if (isAttendeeMode) "Open" else "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(
                                                                        if (isSelected) "Selected" else "Available",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
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
                                                            color = if (isSelected) MaterialTheme.proColors.success.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                                            shape = MaterialTheme.shapes.small,
                                                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp,
                                                                if (isSelected) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                                                            .background(if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success)
                                                                    )
                                                                    Text(slot.day, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                                }
                                                                Row(
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        if (isAttendeeMode) "Open" else "$${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(
                                                                        if (isSelected) "Selected" else "Available",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isSelected) MaterialTheme.proColors.success else MaterialTheme.proColors.success
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
                    if (!isSpecialistViewer) {
                        Text(
                            "Viewing as host — only professionals can request slots.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (canSubmit) {
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
                                            val unit = s.strategyType?.let { SpaceCalculationUtils.strategyUnitLabel(it) } ?: ""
                                            val priceSuffix = if (isAttendeeMode) "" else s.pricesByRecurrence[BookingRecurrence.FLAT]?.let { " — \$${it.toInt()}$unit" } ?: ""
                                            appendLine("  ${i + 1}. ${s.label}$priceSuffix")
                                        }
                                        selectedHoursPerDay.entries.forEachIndexed { di, (day, hrs) ->
                                            hrs.forEachIndexed { hi, hr ->
                                                appendLine("  ${selectedSlots.size + di * 100 + hi + 1}. $day $hr")
                                            }
                                        }
                                    }.trimEnd()
                                    val attendeeBlock = sheetAttendeeQuote?.let { q ->
                                        "\n\n👥 ${com.example.ui.util.AttendeePricing.describe(q)}" +
                                            "\n💰 Total for the booking: \$${com.example.ui.util.AttendeePricing.formatUsd(q.totalUsd)} USD"
                                    } ?: ""
                                    val message = "Hello! I'm interested in booking *${liveSpace.title}*.\n\n📍" +
                                        " ${liveSpace.district}, ${liveSpace.governorate.displayName}" +
                                        "\n\n🗓 Selected Slots ($totalSelectedSlots):\n$slotLines${attendeeBlock}" +
                                        "\n\nAre these slots still available?"
                                    try {
                                        val whatsappUrl = "https://wa.me/${viewModel.formatWhatsAppNumber(liveSpace.ownerPhone)}" +
                                            "?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
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
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppDarkGreen),
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

        // ── Confirm-and-send popup ───────────────────────────────────────────
        if (showSendConfirm) {
            val selectedSubdivisionForDialog = if (selectedSubdivisionId != null)
                liveSpace.subdivisions.find { it.id == selectedSubdivisionId } else null
            val allSheetSlots = sheetSlotGroups.flatMap { (_, s) -> s }
            val dialogQuote = if (com.example.ui.util.AttendeePricing.isPerAttendee(selectedSubdivisionForDialog)) sheetAttendeeQuote else null

            val totalCostForDialog = if (dialogQuote != null)
                dialogQuote.totalUsd
            else
                selectedSlots.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 } +
                selectedHoursPerDay.entries.sumOf { (day, hrs) ->
                    allSheetSlots.filter { it.day == day && it.startTime in hrs }.sumOf { it.pricesByRecurrence[BookingRecurrence.FLAT] ?: 0.0 }
                }

            ProHostDialog(
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
                        HorizontalDivider()
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
                        if (dialogQuote != null) {
                            HorizontalDivider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Attendees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${dialogQuote.attendees} people", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Price", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "$${com.example.ui.util.AttendeePricing.formatUsd(dialogQuote.tier.pricePerAttendeeUsd)}/person" +
                                        dialogQuote.tier.name.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        HorizontalDivider()
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
                                attendeeQuote = dialogQuote
                            )
                        },
                        enabled = !isSendingSlotRequest
                    ) {
                        if (isSendingSlotRequest) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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
    if (showProfilePicRequiredDialog) {
        ProHostDialog(
            onDismissRequest = { showProfilePicRequiredDialog = false },
            title = { Text("Profile Photo Required") },
            text = {
                Text(
                    "Please add a profile photo before sending a booking request. Hosts use your photo to verify your identity.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(shape = MaterialTheme.shapes.medium, onClick = { showProfilePicRequiredDialog = false; onNavigateToProfile() }) {
                    Text("Go to Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfilePicRequiredDialog = false }) {
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
    val isOccupied: Boolean,
    val capacity: Int? = null,
    val hasCustomHours: Boolean = false,
    val isPerAttendee: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubdivisionRentalCard(
    info: SubdivisionRentalCardInfo,
    onClick: () -> Unit,
    onCheckAvailability: () -> Unit
) {
    val name = info.name
    val typeBadge = info.typeBadge
    val imageUrls = info.imageUrls
    val amenities = info.amenities
    val hashtags = info.hashtags
    val priceSummary = info.priceSummary
    val isOccupied = info.isOccupied
    val capacity = info.capacity
    val hasCustomHours = info.hasCustomHours
    val isPerAttendee = info.isPerAttendee
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
                    val cardScope = rememberCoroutineScope()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        HorizontalPager(state = pagerState) { page ->
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current).data(imageUrls[page]).size(800).build(),
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        if (imageUrls.size > 1) {
                            if (pagerState.currentPage > 0) {
                                IconButton(
                                    onClick = { cardScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 2.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBackIos,
                                        contentDescription = "Previous photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (pagerState.currentPage < imageUrls.size - 1) {
                                IconButton(
                                    onClick = { cardScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 2.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = "Next photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
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
                        priceSummary + if (isPerAttendee) " / person" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    // Capacity / custom-hours / per-attendee badges — surfaced here so
                    // a user isn't left to discover them only after opening the sheet.
                    if (capacity != null || hasCustomHours || isPerAttendee) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (capacity != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Up to $capacity", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (hasCustomHours) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "Custom hours",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

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
                                    color = MaterialTheme.proColors.infoContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "#$tag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.proColors.onInfoContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (overflow > 0) {
                                item {
                                    Surface(
                                        color = MaterialTheme.proColors.infoContainer.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "[$overflow more]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.proColors.onInfoContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Explicit CTA — the whole card is also tappable (peek preview),
                    // but this button jumps straight to the full availability sheet
                    // so it's never left implicit that this card can be booked from.
                    if (!isOccupied) {
                        Button(
                            onClick = onCheckAvailability,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Availability", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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

/** Fill shared by the selected folder tab and the panel under it, so they read as one piece. */
@Composable
private fun folderColor(): Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)

/**
 * Folder-style tabs, one per room: the room's name with its type underneath. The selected
 * tab joins the [RoomFolderPanel] below it; the others sit behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomFolderTabs(
    rooms: List<Subdivision>,
    selectedId: String,
    onSelect: (Subdivision) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        rooms.forEach { room ->
            val selected = room.id == selectedId
            Surface(
                onClick = { onSelect(room) },
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = if (selected) folderColor() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.widthIn(min = 96.dp, max = 180.dp)
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            room.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            room.type.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomFolderPanel(firstTabSelected: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = folderColor(),
        shape = RoundedCornerShape(
            topStart = if (firstTabSelected) 0.dp else 12.dp,
            topEnd = 12.dp,
            bottomStart = 12.dp,
            bottomEnd = 12.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp), content = content)
    }
}
