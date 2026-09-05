package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProSpaceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    viewModel: ProSpaceViewModel,
    onNavigateToDiscovery: () -> Unit,
    onSelectSpace: (SpaceListing) -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allSpaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val pendingOfflineTx by viewModel.pendingOfflineTransactions.collectAsState()

    // Filter reservations belonging to the current logged-in user
    val userBookings = remember(allBookingRequests, currentUser) {
        val user = currentUser
        if (user == null) {
            emptyList()
        } else if (user.role == UserRole.ADMIN) {
            allBookingRequests
        } else {
            allBookingRequests.filter {
                it.practitionerId == user.id ||
                it.practitionerEmail.equals(user.email, ignoreCase = true) ||
                it.practitionerName.contains(user.fullName, ignoreCase = true)
            }
        }
    }

    // Partition upcoming / active vs past
    val upcomingAndActiveBookings = remember(userBookings) {
        userBookings.filter {
            it.status == BookingRequestStatus.ACCEPTED || it.status == BookingRequestStatus.PENDING
        }
    }

    val pastBookings = remember(userBookings) {
        userBookings.filter {
            it.status == BookingRequestStatus.REJECTED || it.status == BookingRequestStatus.CANCELLED
        }
    }

    // State
    var selectedMainTab by remember { mutableStateOf(0) } // 0: Upcoming & Active, 1: Past & History
    var selectedFilterChip by remember { mutableStateOf("ALL") } // ALL, ACCEPTED, PENDING, CANCELLED, REJECTED
    var searchQuery by remember { mutableStateOf("") }

    // Dialog state for Re-booking with interactive calendar
    var rebookTargetSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var rebookSourceBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var showDigitalPassBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var showAgreementSummaryBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var showWhishPaymentBooking by remember { mutableStateOf<BookingRequest?>(null) }

    // Filter current list
    val currentTabBookings = if (selectedMainTab == 0) upcomingAndActiveBookings else pastBookings
    val filteredBookings = remember(currentTabBookings, selectedFilterChip, searchQuery) {
        currentTabBookings.filter { booking ->
            val matchesFilter = when (selectedFilterChip) {
                "ALL" -> true
                "ACCEPTED" -> booking.status == BookingRequestStatus.ACCEPTED
                "PENDING" -> booking.status == BookingRequestStatus.PENDING
                "CANCELLED" -> booking.status == BookingRequestStatus.CANCELLED
                "REJECTED" -> booking.status == BookingRequestStatus.REJECTED
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    booking.spaceTitle.contains(searchQuery, ignoreCase = true) ||
                    booking.spaceDistrict.contains(searchQuery, ignoreCase = true) ||
                    booking.ownerName.contains(searchQuery, ignoreCase = true) ||
                    booking.id.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    // Summary calculations
    val totalActiveLeases = remember(userBookings) {
        userBookings.count { it.status == BookingRequestStatus.ACCEPTED }
    }
    val totalMonthlySpendUsd = remember(userBookings) {
        userBookings.filter { it.status == BookingRequestStatus.ACCEPTED }.sumOf { it.formula.rateUsd }
    }
    val pendingRequestsCount = remember(userBookings) {
        userBookings.count { it.status == BookingRequestStatus.PENDING }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient)
            .testTag("my_bookings_screen")
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Title & Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "My Bookings & Leases",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${userBookings.size} Total",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "${currentUser?.fullName ?: "Licensed Member"} • ${currentUser?.specialty ?: "Practitioner"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = onNavigateToDiscovery,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("explore_new_spaces_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Book Space", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Summary Key Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active Leases
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active Leases", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$totalActiveLeases Workspaces", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Monthly Spend
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Monthly Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$${String.format(Locale.US, "%.0f", totalMonthlySpendUsd)}/mo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Pending Review
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = BrightOrange, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pending Host", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$pendingRequestsCount Requests", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Primary Tab Switcher: Upcoming & Active vs Past & History
                TabRow(
                    selectedTabIndex = selectedMainTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedMainTab == 0,
                        onClick = {
                            selectedMainTab = 0
                            selectedFilterChip = "ALL"
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Upcoming, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Upcoming & Active (${upcomingAndActiveBookings.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedMainTab == 1,
                        onClick = {
                            selectedMainTab = 1
                            selectedFilterChip = "ALL"
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Past & History (${pastBookings.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                // Search Bar and Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by space or location...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // Filter Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterOptions = if (selectedMainTab == 0) {
                        listOf("ALL" to "All Bookings", "ACCEPTED" to "Active / Confirmed", "PENDING" to "Pending Review")
                    } else {
                        listOf("ALL" to "All History", "CANCELLED" to "Cancelled", "REJECTED" to "Declined / Expired")
                    }

                    items(filterOptions) { (key, label) ->
                        val isSelected = selectedFilterChip == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilterChip = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Main Bookings Content List
        if (filteredBookings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No bookings match '$searchQuery'"
                        else if (selectedMainTab == 0) "No active or upcoming reservations found"
                        else "No past reservation history",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Browse verified medical, legal, and engineering workspaces across Lebanon and book with instant availability checks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateToDiscovery,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("empty_state_browse_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explore Workspaces", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredBookings, key = { it.id }) { booking ->
                    val space = allSpaces.find { it.id == booking.spaceId }

                    BookingReservationCard(
                        booking = booking,
                        space = space,
                        onSelectSpace = {
                            if (space != null) onSelectSpace(space)
                        },
                        onRebook = {
                            rebookTargetSpace = space ?: allSpaces.firstOrNull()
                            rebookSourceBooking = booking
                        },
                        onViewDigitalPass = {
                            showDigitalPassBooking = booking
                        },
                        onViewAgreement = {
                            showAgreementSummaryBooking = booking
                        },
                        onContactWhatsApp = {
                            if (space != null) {
                                viewModel.launchWhatsAppInquiry(context, space, booking.formula, booking)
                            }
                        },
                        onCancelRequest = {
                            viewModel.cancelBookingRequest(booking.id, context)
                        },
                        onPayWithWhish = {
                            showWhishPaymentBooking = booking
                        }
                    )
                }
            }
        }
    }

    // Re-booking Dialog with Interactive Calendar
    if (rebookTargetSpace != null) {
        val targetSpace = rebookTargetSpace!!
        val sourceBooking = rebookSourceBooking
        val spaceAcceptedBookings = allBookingRequests.filter { it.spaceId == targetSpace.id && it.status == BookingRequestStatus.ACCEPTED }

        Dialog(
            onDismissRequest = {
                rebookTargetSpace = null
                rebookSourceBooking = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Re-book / Extend Workspace",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Re-booking: ${targetSpace.title} • ${targetSpace.district}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                rebookTargetSpace = null
                                rebookSourceBooking = null
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Interactive Calendar Embedded inside Re-booking
                    WorkspaceInteractiveBookingCalendar(
                        space = targetSpace,
                        acceptedBookings = spaceAcceptedBookings,
                        initialFormula = sourceBooking?.formula,
                        onScheduleSelected = { startDate, endDate, durationMonths, selectedDays, startHour, endHour, selectedShift, totalUsd, isInstantAvailable ->
                            val formula = sourceBooking?.formula ?: targetSpace.rentalFormulas.firstOrNull() ?: RentalFormula(
                                type = RentalFormulaType.FULL_MONTH,
                                rateUsd = totalUsd / durationMonths.coerceAtLeast(1),
                                scheduleDescription = "Re-booked $selectedShift ($startHour - $endHour)",
                                daysOfWeek = selectedDays,
                                startHour = startHour,
                                endHour = endHour,
                                totalWeeklyHours = 40
                            )

                            val created = viewModel.submitBookingRequest(
                                space = targetSpace,
                                formula = formula,
                                startDate = startDate,
                                durationMonths = durationMonths,
                                notes = "Re-booking reservation renewal. Previous Ref #${sourceBooking?.id ?: "N/A"}. Licensed practitioner.",
                                context = context,
                                alsoOpenWhatsApp = false,
                                selectedDays = selectedDays,
                                selectedStartHour = startHour,
                                selectedEndHour = endHour,
                                selectedShift = selectedShift,
                                calculatedTotalUsd = totalUsd
                            )

                            if (created != null) {
                                rebookTargetSpace = null
                                rebookSourceBooking = null
                                Toast.makeText(context, "Re-booking Request #${created.id} submitted successfully!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Digital Access Pass Dialog
    if (showDigitalPassBooking != null) {
        val bkg = showDigitalPassBooking!!
        Dialog(onDismissRequest = { showDigitalPassBooking = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                    Text("Digital Workspace Key Pass", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Booking Reference: #${bkg.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📍 ${bkg.spaceTitle}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("👤 Renter: ${bkg.practitionerName} (${bkg.practitionerSpecialty})", fontSize = 12.sp)
                            Text("🗓️ Dates: ${bkg.startDate} → ${bkg.endDate}", fontSize = 12.sp)
                            Text("⏰ Schedule: ${bkg.selectedDays.joinToString()} • ${bkg.selectedStartHour} - ${bkg.selectedEndHour}", fontSize = 12.sp)
                            Text("🔑 Smart Key Pass: PRO-PASS-${bkg.id.take(6).uppercase()}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = { showDigitalPassBooking = null },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    // Agreement Summary Dialog
    if (showAgreementSummaryBooking != null) {
        val bkg = showAgreementSummaryBooking!!
        Dialog(onDismissRequest = { showAgreementSummaryBooking = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rental Agreement Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Lebanese Civil Code & Syndicate Compliant Lease Summary", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• Workspace: ${bkg.spaceTitle} (${bkg.spaceDistrict}, ${bkg.governorate.displayName})", fontSize = 12.sp)
                        Text("• Host: ${bkg.ownerName} (${bkg.ownerPhone})", fontSize = 12.sp)
                        Text("• Practitioner: ${bkg.practitionerName} (Syndicate ID #${bkg.practitionerSyndicateNumber.ifEmpty { "VERIFIED" }})", fontSize = 12.sp)
                        Text("• Duration: ${bkg.durationMonths} Month(s) starting ${bkg.startDate}", fontSize = 12.sp)
                        Text("• Agreed Rate: $${String.format(Locale.US, "%.0f", bkg.totalAmountUsd)} USD", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("• Utilities: Guaranteed Generator 24/7 & Fiber Internet included", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showAgreementSummaryBooking = null },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Summary")
                    }
                }
            }
        }
    }

    // Whish Money Payment Dialog
    if (showWhishPaymentBooking != null) {
        val bkg = showWhishPaymentBooking!!
        val space = allSpaces.find { it.id == bkg.spaceId }
        if (space != null) {
            WhishPayModal(
                space = space,
                currentFeeUsd = if (bkg.totalAmountUsd > 0) bkg.totalAmountUsd else bkg.formula.rateUsd,
                booking = bkg,
                viewModel = viewModel,
                onDismiss = { showWhishPaymentBooking = null },
                onConfirmPayment = { _, _ ->
                    // Settlement isn't confirmed yet here — the app is only just opening
                    // Whish's checkout page. The Firestore listener reflects the real
                    // outcome once Whish confirms it; no success toast belongs here.
                    showWhishPaymentBooking = null
                }
            )
        }
    }
}

@Composable
fun BookingReservationCard(
    booking: BookingRequest,
    space: SpaceListing?,
    onSelectSpace: () -> Unit,
    onRebook: () -> Unit,
    onViewDigitalPass: () -> Unit,
    onViewAgreement: () -> Unit,
    onContactWhatsApp: () -> Unit,
    onCancelRequest: () -> Unit,
    onPayWithWhish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Space Title, District, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = booking.spaceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (space?.isVerified == true) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified Space", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        text = "📍 ${booking.spaceDistrict}, ${booking.governorate.displayName} • Ref #${booking.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (booking.status) {
                        BookingRequestStatus.ACCEPTED -> StatusSuccessContainer
                        BookingRequestStatus.PENDING -> StatusWarningContainer
                        BookingRequestStatus.CANCELLED -> StatusErrorContainer
                        BookingRequestStatus.REJECTED -> CoolGrayContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (booking.status) {
                                BookingRequestStatus.ACCEPTED -> Icons.Default.CheckCircle
                                BookingRequestStatus.PENDING -> Icons.Default.Schedule
                                BookingRequestStatus.CANCELLED -> Icons.Default.Cancel
                                BookingRequestStatus.REJECTED -> Icons.Default.Block
                            },
                            contentDescription = null,
                            tint = when (booking.status) {
                                BookingRequestStatus.ACCEPTED -> FreshGreen
                                BookingRequestStatus.PENDING -> BrightOrange
                                BookingRequestStatus.CANCELLED -> CrimsonRed
                                BookingRequestStatus.REJECTED -> CoolGray
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = booking.status.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (booking.status) {
                                BookingRequestStatus.ACCEPTED -> StatusOnSuccessContainer
                                BookingRequestStatus.PENDING -> StatusOnWarningContainer
                                BookingRequestStatus.CANCELLED -> StatusOnErrorContainer
                                BookingRequestStatus.REJECTED -> CoolGrayDark
                            }
                        )
                    }
                }
            }

            // Step Progress Tracker
            BookingStatusProgressStepper(status = booking.status)

            // Schedule & Formula Details Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("FORMULA & TIMING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = booking.formula.type.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val daysStr = if (booking.selectedDays.isNotEmpty()) booking.selectedDays.joinToString(", ") else booking.formula.daysOfWeek.joinToString(", ")
                            val timeStr = if (booking.selectedStartHour.isNotBlank()) "${booking.selectedStartHour} - ${booking.selectedEndHour}" else "${booking.formula.startHour} - ${booking.formula.endHour}"
                            Text(
                                text = "$daysStr @ $timeStr",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("DURATION & LEASE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${booking.durationMonths} Month(s)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Start: ${booking.startDate.ifEmpty { "Immediate" }}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Price & Payment Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Commitment", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$${String.format(Locale.US, "%.0f", booking.totalAmountUsd)} USD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (booking.isExternalPaymentSettled) {
                        Surface(color = StatusSuccessContainer, shape = RoundedCornerShape(6.dp)) {
                            Text("Whish Settled", color = StatusOnSuccessContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else if (booking.status == BookingRequestStatus.ACCEPTED) {
                        OutlinedButton(
                            onClick = onPayWithWhish,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pay Whish", fontSize = 11.sp)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Interactive Actions Bar (Re-book, Pass, WhatsApp, Details)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Re-book / Extend Button (Prominent)
                Button(
                    onClick = onRebook,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("rebook_button_${booking.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (booking.status == BookingRequestStatus.ACCEPTED) "Extend / Re-book" else "Re-book Space",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // WhatsApp Host
                IconButton(
                    onClick = onContactWhatsApp,
                    modifier = Modifier
                        .size(40.dp)
                        .background(WhatsAppGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp Host", tint = WhatsAppDarkGreen, modifier = Modifier.size(18.dp))
                }

                // Digital Key Pass (if accepted)
                if (booking.status == BookingRequestStatus.ACCEPTED) {
                    IconButton(
                        onClick = onViewDigitalPass,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Digital Pass", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }

                // Agreement Summary
                IconButton(
                    onClick = onViewAgreement,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Description, contentDescription = "View Terms", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }

                // Cancel Request (if pending)
                if (booking.status == BookingRequestStatus.PENDING) {
                    IconButton(
                        onClick = onCancelRequest,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Request", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BookingStatusProgressStepper(
    status: BookingRequestStatus,
    modifier: Modifier = Modifier
) {
    val steps = listOf("Requested", "Host Review", "Confirmed", "Access Active")
    val currentStepIndex = when (status) {
        BookingRequestStatus.PENDING -> 1
        BookingRequestStatus.ACCEPTED -> 3
        BookingRequestStatus.CANCELLED, BookingRequestStatus.REJECTED -> 0
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, stepName ->
            val isCompleted = index <= currentStepIndex && status != BookingRequestStatus.CANCELLED && status != BookingRequestStatus.REJECTED
            val isCurrent = index == currentStepIndex && status != BookingRequestStatus.CANCELLED && status != BookingRequestStatus.REJECTED

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circle Step
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                status == BookingRequestStatus.CANCELLED || status == BookingRequestStatus.REJECTED -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                    } else {
                        Text("${index + 1}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }

                // Connector line
                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (index < currentStepIndex && status != BookingRequestStatus.CANCELLED && status != BookingRequestStatus.REJECTED) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }
    }
}
