package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel

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
    // "Choose Renting Option" card, which drives this same selection with real slots.
    var selectedFormula by remember(liveSpace.id) {
        mutableStateOf<RentalFormula?>(
            SpaceCalculationUtils.buildAllSlotsForSpace(liveSpace).firstOrNull()?.let { slot ->
                // Same recurrence preference as the "Choose Renting Option" cards
                // below (prefer weekly if the strategy offers it) — a Shift/Day-Based
                // slot has no FLAT price, so defaulting to FLAT here would seed a
                // misleading $0 before the specialist taps anything.
                val recurrence = slot.pricesByRecurrence.keys.let { keys ->
                    if (BookingRecurrence.SAME_DAY_EVERY_WEEK in keys) BookingRecurrence.SAME_DAY_EVERY_WEEK else keys.firstOrNull()
                } ?: BookingRecurrence.FLAT
                SpaceCalculationUtils.representativeFormula(listOf(slot), recurrence)
            }
        )
    }
    var showBookingDialog by remember { mutableStateOf(false) }

    if (showBookingDialog) {
        RentalBookingDialog(
            space = liveSpace,
            initialFormula = selectedFormula,
            viewModel = viewModel,
            onDismiss = { showBookingDialog = false },
            onRequestSubmitted = {
                showBookingDialog = false
            }
        )
    }

    SpaceDetailsScreenContent(
        space = liveSpace,
        acceptedBookings = acceptedBookings,
        myRequestsForThisSpace = myRequestsForThisSpace,
        selectedFormula = selectedFormula,
        onSelectFormula = { selectedFormula = it },
        onRequestRentClick = { showBookingDialog = true },
        onWhatsAppClick = {
            viewModel.launchWhatsAppInquiry(context, liveSpace, selectedFormula)
        },
        onShareClick = {
            val shareText = "🏢 ProHost: ${liveSpace.title}\n📍 ${liveSpace.district}, ${liveSpace.governorate.displayName}\n💰 $${liveSpace.baseMonthlyRateUsd.toInt()}/mo • WhatsApp: ${liveSpace.ownerPhone}"
            viewModel.shareExportData(context, "Listing", shareText)
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
    acceptedBookings: List<RentalBookingRequest>,
    myRequestsForThisSpace: List<RentalBookingRequest>,
    selectedFormula: RentalFormula?,
    onSelectFormula: (RentalFormula) -> Unit,
    onRequestRentClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onShareClick: () -> Unit,
    isSaved: Boolean = false,
    onToggleSave: () -> Unit = {},
    onBack: () -> Unit
) {
    val liveSpace = space

    Scaffold(
        // Nested inside the app-shell Scaffold's already-inset content area — see
        // MyFavoritesScreen's identical fix for why this is needed.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Workspace Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
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
                    Column(modifier = Modifier.weight(0.9f)) {
                        val price = selectedFormula?.rateUsd ?: liveSpace.baseMonthlyRateUsd
                        val priceUnit = selectedFormula?.let { SpaceCalculationUtils.rateUnitLabel(it.type) } ?: "/mo"
                        Text(
                            text = "$${price.toInt()} USD$priceUnit",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = selectedFormula?.type?.displayName ?: "Full Month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.weight(2.1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Request to Rent Button
                        Button(
                            onClick = onRequestRentClick,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Request Rent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        // WhatsApp Direct Inquiry Button
                        Button(
                            onClick = onWhatsAppClick,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("WhatsApp", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
            // listing's photos were ever shown).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
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
                    // Semi-transparent overlay to ensure text is fully legible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    if (liveSpace.imageUrls.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(liveSpace.imageUrls.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == pagerState.currentPage) Color.White
                                            else Color.White.copy(alpha = 0.4f)
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = liveSpace.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "📍 ${liveSpace.district}, ${liveSpace.governorate.displayName} • ${liveSpace.floorInfo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xCCFFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                // Weekly Availability Matrix (Visitor Tap-to-Book Heatmap)
                SpaceAvailabilityMatrixView(
                    space = liveSpace,
                    acceptedBookings = acceptedBookings,
                    onCellClicked = { formula, day, shiftName ->
                        onSelectFormula(formula)
                        onRequestRentClick()
                    }
                )

                // Renting Options Preview — built from the space's real live pricing
                // config (SpaceCalculationUtils.buildAllSlotsForSpace), not the legacy
                // rentalFormulas list, which only ever held one flattened, lossy
                // snapshot from publish time. One card per strategy actually
                // configured (a strategy nobody set up never shows as a fake option);
                // tapping picks a representative rate for the bottom bar/booking
                // dialog hint — the specialist chooses the exact real slot inside
                // RentalBookingDialog itself.
                val availableSlots = remember(liveSpace) { SpaceCalculationUtils.buildAllSlotsForSpace(liveSpace) }
                val strategyPreviewGroups = remember(availableSlots) {
                    availableSlots.filter { it.strategyType != null }
                        .groupBy { it.strategyType!! }
                        .toList()
                        .sortedBy { (type, _) -> type.ordinal }
                }
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProSectionHeader(
                            title = "Choose Renting Option",
                            subtitle = "Real availability & pricing configured by the host",
                            icon = Icons.Default.Tune
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            strategyPreviewGroups.forEach { (strategyType, slots) ->
                                val representativeSlot = slots.first()
                                val previewRecurrence = representativeSlot.pricesByRecurrence.keys.let { keys ->
                                    if (BookingRecurrence.SAME_DAY_EVERY_WEEK in keys) BookingRecurrence.SAME_DAY_EVERY_WEEK else keys.firstOrNull()
                                } ?: BookingRecurrence.FLAT
                                val formula = SpaceCalculationUtils.representativeFormula(listOf(representativeSlot), previewRecurrence)
                                    ?: return@forEach
                                val isSelected = selectedFormula?.type == formula.type
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectFormula(formula) },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = formula.type.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = formula.scheduleDescription,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            ProCurrencyTag(
                                                rateUsd = formula.rateUsd,
                                                unitLabel = SpaceCalculationUtils.rateUnitLabel(formula.type)
                                            )
                                        }

                                        // Attached Hours Tag
                                        if (formula.daysOfWeek.isNotEmpty() && formula.startHour.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.AccessTime,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                                    Text(
                                                        text = "${formula.daysOfWeek.joinToString()} • ${formula.startHour} - ${formula.endHour}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
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

                // Dedicated Availability section — one sub-view per configured renting
                // formula, each showing real rented-vs-available counts from the exact
                // same shared slot data (SpaceCalculationUtils.buildAllSlotsForSpace/
                // isSlotLocked) the generic calendar/matrix views above already use, but
                // grouped and rendered per formula type instead of one identical grid
                // for every strategy.
                if (strategyPreviewGroups.isNotEmpty()) {
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProSectionHeader(
                                title = "Availability",
                                subtitle = "Real rented vs. available slots per renting formula",
                                icon = Icons.Default.EventAvailable
                            )
                            strategyPreviewGroups.forEachIndexed { groupIndex, (strategyType, slots) ->
                                val lockedCount = slots.count { SpaceCalculationUtils.isSlotLocked(it, liveSpace.id, acceptedBookings) }
                                val availableCount = slots.size - lockedCount
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(strategyType.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "$availableCount available • $lockedCount rented",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (availableCount > 0) LebaneseCedarGreen else StatusError
                                        )
                                    }

                                    when (strategyType) {
                                        RentalStrategyType.MONTHLY -> {
                                            val slot = slots.first()
                                            val isLocked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                            Text(
                                                "Full-month exclusive lease • $${slot.pricesByRecurrence[BookingRecurrence.FLAT]?.toInt() ?: 0}/mo",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            ProStatusBadge(
                                                if (isLocked) ProBadgeType.CUSTOM_ERROR else ProBadgeType.CUSTOM_INFO,
                                                customText = if (isLocked) "Currently Rented" else "Available Now"
                                            )
                                        }
                                        RentalStrategyType.HOURLY -> {
                                            slots.groupBy { it.day }.toList().sortedBy { it.first }.forEach { (day, daySlots) ->
                                                Text(day, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    items(daySlots.sortedBy { it.startTime }) { slot ->
                                                        val locked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                                        Surface(
                                                            color = if (locked) StatusErrorContainer else StatusSuccessContainer,
                                                            shape = MaterialTheme.shapes.extraSmall
                                                        ) {
                                                            Text(
                                                                slot.startTime,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (locked) StatusError else StatusSuccess,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        RentalStrategyType.SHIFT_BASED -> {
                                            slots.groupBy { it.day }.toList().sortedBy { it.first }.forEach { (day, daySlots) ->
                                                Text(day, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                                daySlots.forEach { slot ->
                                                    val locked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(slot.label, style = MaterialTheme.typography.bodySmall)
                                                        Text(
                                                            if (locked) "Rented" else "Available",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (locked) StatusError else StatusSuccess
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        RentalStrategyType.DAY_BASED -> {
                                            slots.sortedBy { it.day }.forEach { slot ->
                                                val locked = SpaceCalculationUtils.isSlotLocked(slot, liveSpace.id, acceptedBookings)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(slot.day, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        if (locked) "Rented" else "Available",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (locked) StatusError else StatusSuccess
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (groupIndex < strategyPreviewGroups.lastIndex) {
                                    HorizontalDivider(color = LightGray.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }

                // Essential Facilities & Utilities
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProSectionHeader(
                            title = "Essential Facilities & Utilities",
                            subtitle = "Infrastructure and amenities available in the workspace",
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

                // Equipment Catalog
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProSectionHeader(
                            title = "Equipment & Facilities Catalog (${liveSpace.equipment.size})",
                            subtitle = "On-premises certified hardware and amenities ready for use",
                            icon = Icons.Default.Handyman
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            liveSpace.equipment.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (item.description.isNotBlank()) {
                                            Text(
                                                text = item.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "Qty: ${item.quantity}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (item != liveSpace.equipment.last()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }

                // Co-Sharing & Specialist Synergy
                if (liveSpace.complementarySpecialties.isNotEmpty() || liveSpace.residentPractitioners.isNotEmpty()) {
                    ProSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProSectionHeader(
                                title = "Co-Working & Specialist Synergy",
                                subtitle = "Networking and collaborative peers on premises",
                                icon = Icons.Default.Groups
                            )

                            if (liveSpace.residentPractitioners.isNotEmpty()) {
                                Text(
                                    text = "Resident Specialists & Teams On-Site:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                liveSpace.residentPractitioners.forEach { doc ->
                                    Text("• $doc", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
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

                // Premises Rules & Policies
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProSectionHeader(
                            title = "Rules & Premises Policies",
                            subtitle = "Code of conduct and facility access hours",
                            icon = Icons.Default.Policy
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "🚭 Smoking: ${if (liveSpace.rules.smokingAllowed) "Permitted in designated zone" else "Strictly Prohibited"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "☕ Food & Beverages: ${if (liveSpace.rules.foodAllowed) "Permitted in staff breakroom" else "Not allowed"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "🕒 Off-Hours Access: ${if (liveSpace.rules.offHoursAccess) "24/7 Keycard / Smart Access" else "Business Hours Only"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "👥 Visitor Policy: ${liveSpace.rules.visitorPolicy}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
