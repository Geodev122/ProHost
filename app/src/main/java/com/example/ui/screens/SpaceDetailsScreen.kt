package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
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

    var selectedFormula by remember { mutableStateOf<RentalFormula?>(liveSpace.rentalFormulas.firstOrNull()) }
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
    onBack: () -> Unit
) {
    val liveSpace = space

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.9f)) {
                        val price = selectedFormula?.rateUsd ?: liveSpace.baseMonthlyRateUsd
                        Text(
                            text = "$${price.toInt()} USD",
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
            // Hero Visual Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val firstImage = liveSpace.imageUrls.firstOrNull()
                if (firstImage != null) {
                    coil.compose.AsyncImage(
                        model = firstImage,
                        contentDescription = liveSpace.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Semi-transparent overlay to ensure text is fully legible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
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
                            text = "${liveSpace.spaceType.displayName} • ${if (liveSpace.isShared) "Shared Co-Working Space" else "Private Studio / Office"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = liveSpace.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = "📍 ${liveSpace.district}, ${liveSpace.governorate.displayName} • ${liveSpace.floorInfo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xCCFFFFFF)
                    )
                }

                // 10-sec video tour badge
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = Color(0xCC000000),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("10s Video Tour", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
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
                            size = 40.dp,
                            modifier = Modifier.weight(1f)
                        )

                        ProStatusBadge(ProBadgeType.ACTIVE_30D)
                    }
                }

                // Smart Availability & Operating Schedule Calendar View
                SpaceAvailabilityCalendarView(
                    space = liveSpace,
                    acceptedBookings = acceptedBookings
                )

                // Weekly Availability Matrix (Visitor Tap-to-Book Heatmap)
                WeeklyAvailabilityMatrix(
                    space = liveSpace,
                    acceptedBookings = acceptedBookings,
                    onCellClicked = { formula, day, shiftName ->
                        onSelectFormula(formula)
                        onRequestRentClick()
                    }
                )

                // Flexible Renting Formulas Selector (with Attached Defined Hours)
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProSectionHeader(
                            title = "Choose Renting Formula",
                            subtitle = "Select desired time commitment with defined schedule slots",
                            icon = Icons.Default.Tune
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            liveSpace.rentalFormulas.forEach { formula ->
                                val isSelected = selectedFormula?.id == formula.id || selectedFormula?.type == formula.type
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

                                            ProCurrencyTag(rateUsd = formula.rateUsd, isPerMonth = true)
                                        }

                                        // Attached Hours Tag
                                        if (formula.daysOfWeek.isNotEmpty() && formula.startHour.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                                                        text = "${formula.daysOfWeek.joinToString()} • ${formula.startHour} - ${formula.endHour} (${formula.totalWeeklyHours} hrs/wk)",
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
                                        shape = RoundedCornerShape(6.dp)
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
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = spec,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
