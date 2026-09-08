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
import com.example.ui.viewmodel.ProHostViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    viewModel: ProHostViewModel,
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
    // Editing an already-accepted booking is a separate flow from Re-book/Extend:
    // the submitted request references the booking it would replace (replacesBookingId)
    // and, if the host accepts it, actually replaces it (see ProHostRepository.acceptBookingRequest)
    // instead of coexisting alongside it as an independent new lease.
    var editTargetSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var editSourceBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var showDigitalPassBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var cancelTargetBooking by remember { mutableStateOf<BookingRequest?>(null) }

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
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${userBookings.size} Total",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
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
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("explore_new_spaces_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Book Space", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.Bold)
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
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Active Leases", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$totalActiveLeases Workspaces", fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Monthly Spend
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Monthly Rate", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$${String.format(Locale.US, "%.0f", totalMonthlySpendUsd)}/mo", fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Pending Review
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = BrightOrange, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Pending Host", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$pendingRequestsCount Requests", fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                        placeholder = { Text("Search...", fontSize = MaterialTheme.typography.labelMedium.fontSize) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
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
                            label = { Text(label, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.testTag("empty_state_browse_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Explore Workspaces", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
                        onEditBooking = {
                            editTargetSpace = space ?: allSpaces.firstOrNull()
                            editSourceBooking = booking
                        },
                        onViewDigitalPass = {
                            showDigitalPassBooking = booking
                        },
                        onContactWhatsApp = {
                            if (space != null) {
                                viewModel.launchWhatsAppInquiry(context, space, booking.formula, booking)
                            }
                        },
                        onCancelRequest = {
                            viewModel.cancelBookingRequest(booking.id, context)
                        },
                        onCancelAcceptedBooking = {
                            cancelTargetBooking = booking
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
                    .clip(MaterialTheme.shapes.extraLarge),
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
                                Spacer(modifier = Modifier.width(Spacing.sm))
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

    // Edit Active Booking Dialog with Interactive Calendar — pre-seeded with the
    // current accepted formula, submitted as a new PENDING request referencing the
    // booking it would replace. If the host accepts it, ProHostRepository.acceptBookingRequest
    // releases the old booking and this one takes its place; availability is always
    // computed live from ACCEPTED bookings, so nothing else needs recalculating by hand.
    if (editTargetSpace != null) {
        val targetSpace = editTargetSpace!!
        val sourceBooking = editSourceBooking
        val spaceAcceptedBookings = allBookingRequests.filter {
            it.spaceId == targetSpace.id && it.status == BookingRequestStatus.ACCEPTED && it.id != sourceBooking?.id
        }

        Dialog(
            onDismissRequest = {
                editTargetSpace = null
                editSourceBooking = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .clip(MaterialTheme.shapes.extraLarge),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = "Edit Active Booking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Editing: ${targetSpace.title} • ${targetSpace.district} — submitted for host approval, replaces your current booking once accepted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                editTargetSpace = null
                                editSourceBooking = null
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    WorkspaceInteractiveBookingCalendar(
                        space = targetSpace,
                        acceptedBookings = spaceAcceptedBookings,
                        initialFormula = sourceBooking?.formula,
                        onScheduleSelected = { startDate, endDate, durationMonths, selectedDays, startHour, endHour, selectedShift, totalUsd, isInstantAvailable ->
                            val formula = sourceBooking?.formula ?: targetSpace.rentalFormulas.firstOrNull() ?: RentalFormula(
                                type = RentalFormulaType.FULL_MONTH,
                                rateUsd = totalUsd / durationMonths.coerceAtLeast(1),
                                scheduleDescription = "Edited $selectedShift ($startHour - $endHour)",
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
                                notes = "Edit request for accepted booking #${sourceBooking?.id ?: "N/A"} — replaces it if approved.",
                                context = context,
                                alsoOpenWhatsApp = false,
                                selectedDays = selectedDays,
                                selectedStartHour = startHour,
                                selectedEndHour = endHour,
                                selectedShift = selectedShift,
                                calculatedTotalUsd = totalUsd,
                                replacesBookingId = sourceBooking?.id
                            )

                            if (created != null) {
                                editTargetSpace = null
                                editSourceBooking = null
                                Toast.makeText(context, "Edit Request #${created.id} submitted — awaiting host approval.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Cancel Accepted Booking Dialog — early termination, previously not possible
    // at all (only a not-yet-accepted PENDING request could be cancelled).
    if (cancelTargetBooking != null) {
        val bkg = cancelTargetBooking!!
        CancelAcceptedBookingDialog(
            spaceTitle = bkg.spaceTitle,
            partyLabel = bkg.ownerName,
            onDismiss = { cancelTargetBooking = null },
            onConfirm = { reasonCode, note ->
                viewModel.cancelAcceptedBooking(bkg.id, reasonCode, note, context)
                cancelTargetBooking = null
            }
        )
    }

    // Digital Access Pass Dialog — the QR-style card is now what it visually
    // claimed to be all along: a link to the real signed leasing agreement the
    // host uploaded when accepting (BookingRequest.agreementUrl), not a
    // decorative code nothing ever checks. "View Agreement" as a separate action
    // is gone — this is the one place to reach it now.
    if (showDigitalPassBooking != null) {
        val bkg = showDigitalPassBooking!!
        Dialog(onDismissRequest = { showDigitalPassBooking = null }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg)
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
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📍 ${bkg.spaceTitle}", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            Text("👤 Renter: ${bkg.practitionerName} (${bkg.practitionerSpecialty})", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            Text("🗓️ Dates: ${bkg.startDate} → ${bkg.endDate}", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            Text("⏰ Schedule: ${bkg.selectedDays.joinToString()} • ${bkg.selectedStartHour} - ${bkg.selectedEndHour}", fontSize = MaterialTheme.typography.labelMedium.fontSize)
                            Text("🔑 Smart Key Pass: PRO-PASS-${bkg.id.take(6).uppercase()}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                    }

                    if (bkg.agreementUrl != null) {
                        Text(
                            text = "This pass links to the signed leasing agreement your host uploaded when accepting.",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(bkg.agreementUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open the agreement.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Signed Agreement")
                        }
                    } else {
                        Surface(
                            color = StatusWarningContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No signed agreement on file for this booking yet — contact your host on WhatsApp.",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = StatusOnWarningContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = { showDigitalPassBooking = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
fun BookingReservationCard(
    booking: BookingRequest,
    space: SpaceListing?,
    onSelectSpace: () -> Unit,
    onRebook: () -> Unit,
    onEditBooking: () -> Unit,
    onViewDigitalPass: () -> Unit,
    onContactWhatsApp: () -> Unit,
    onCancelRequest: () -> Unit,
    onCancelAcceptedBooking: () -> Unit = {},
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
                .padding(Spacing.lg),
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
                            Spacer(modifier = Modifier.width(Spacing.xs))
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
                    shape = MaterialTheme.shapes.small,
                    color = when (booking.status) {
                        BookingRequestStatus.ACCEPTED -> StatusSuccessContainer
                        BookingRequestStatus.PENDING -> StatusWarningContainer
                        BookingRequestStatus.CANCELLED -> StatusErrorContainer
                        BookingRequestStatus.REJECTED -> CoolGrayContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
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
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Total Commitment Row — payment itself is handled entirely outside the
            // app now (see the host's uploaded agreement, not an in-app payment flag,
            // for the record that a real deal was reached).
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

                if (booking.status == BookingRequestStatus.ACCEPTED && booking.agreementUrl != null) {
                    Surface(color = StatusSuccessContainer, shape = MaterialTheme.shapes.small) {
                        Text("Agreement On File", color = StatusOnSuccessContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
                    shape = MaterialTheme.shapes.medium,
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
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                }

                // WhatsApp Host
                IconButton(
                    onClick = onContactWhatsApp,
                    modifier = Modifier
                        .size(40.dp)
                        .background(WhatsAppGreen.copy(alpha = 0.15f), MaterialTheme.shapes.medium)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp Host", tint = WhatsAppDarkGreen, modifier = Modifier.size(18.dp))
                }

                // Digital Key Pass (if accepted) — links to the signed agreement
                if (booking.status == BookingRequestStatus.ACCEPTED) {
                    IconButton(
                        onClick = onViewDigitalPass,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Digital Pass", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }

                    // Edit Booking (accepted only) — submits a change for host approval;
                    // replaces this booking if/when accepted, distinct from Re-book/Extend
                    // (which creates an independent new lease alongside this one).
                    IconButton(
                        onClick = onEditBooking,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .testTag("edit_booking_button_${booking.id}")
                    ) {
                        Icon(Icons.Default.EditCalendar, contentDescription = "Edit Booking", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }

                // Cancel Request (if pending)
                if (booking.status == BookingRequestStatus.PENDING) {
                    IconButton(
                        onClick = onCancelRequest,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Request", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }

                // Cancel Accepted Booking (early termination) — the gap this session
                // closed: previously there was no in-app way to end an active lease.
                if (booking.status == BookingRequestStatus.ACCEPTED) {
                    IconButton(
                        onClick = onCancelAcceptedBooking,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                    ) {
                        Icon(Icons.Default.EventBusy, contentDescription = "Cancel Booking", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
