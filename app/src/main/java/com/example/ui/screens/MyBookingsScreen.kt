package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
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
    val hasLoadedBookingsOnce by viewModel.hasLoadedBookingsOnce.collectAsState()
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
                it.practitionerEmail.equals(user.email, ignoreCase = true)
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

    // Dialog state for Re-booking with interactive calendar. Stores just the
    // space's id, not the SpaceListing itself — RentalBookingDialog's own slot/
    // price computation is already correctly live (derived from the real
    // bookingRequests StateFlow), but a frozen SpaceListing captured once at tap
    // time would freeze the space's own pricing/schedule shape for as long as the
    // dialog stayed open, missing e.g. a host editing the listing's pricing while
    // a specialist has this dialog open. Re-deriving from the live allSpaces list
    // below every recomposition closes that gap.
    var rebookTargetSpaceId by remember { mutableStateOf<String?>(null) }
    var rebookSourceBooking by remember { mutableStateOf<BookingRequest?>(null) }
    // Editing an already-accepted booking is a separate flow from Re-book/Extend:
    // the submitted request references the booking it would replace (replacesBookingId)
    // and, if the host accepts it, actually replaces it (see ProHostRepository.acceptBookingRequest)
    // instead of coexisting alongside it as an independent new lease.
    var editTargetSpaceId by remember { mutableStateOf<String?>(null) }
    var editSourceBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var showDigitalPassBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var cancelTargetBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var pendingCancelTarget by remember { mutableStateOf<BookingRequest?>(null) }

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
                // Header Title
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "My Rentals",
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

                // Book Space — its own full-width row so it never competes with the
                // title block for space on narrow screens.
                FilledTonalButton(
                    onClick = onNavigateToDiscovery,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_new_spaces_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Book a Space", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                // Primary Tab Switcher: Upcoming & Active vs Past & History — a
                // segmented control (bordered container, filled + shadowed selected
                // pill) so the two tabs are visually distinguishable, not just a
                // color-only indicator underline.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        val tabs = listOf(
                            Triple(0, Icons.Default.Upcoming, "Upcoming & Active (${upcomingAndActiveBookings.size})"),
                            Triple(1, Icons.Default.History, "Past & History (${pastBookings.size})")
                        )
                        tabs.forEach { (index, icon, label) ->
                            val isSelected = selectedMainTab == index
                            Surface(
                                onClick = {
                                    selectedMainTab = index
                                    selectedFilterChip = "ALL"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (isSelected) Modifier.shadow(2.dp, MaterialTheme.shapes.small) else Modifier),
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
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
                        placeholder = { Text("Search...", style = MaterialTheme.typography.labelMedium) },
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
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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
        val roleAccent = when (currentUser?.role) {
            UserRole.PRO_HOST -> CarnationOrange
            UserRole.ADMIN -> BrightOrange
            else -> VibrantBlue
        }
        if (!hasLoadedBookingsOnce) {
            // The first Firestore snapshot hasn't arrived yet — without this, an
            // account with real bookings briefly showed "No bookings" before the
            // real list streamed in, indistinguishable from actually having none.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = roleAccent)
            }
        } else if (filteredBookings.isEmpty()) {
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
                        Text("Book a Space", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // bottom = 24.dp (rather than Spacing.md) so the last card always
                // clears the bottom nav bar with real breathing room.
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = 24.dp),
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
                            rebookTargetSpaceId = (space ?: allSpaces.firstOrNull())?.id
                            rebookSourceBooking = booking
                        },
                        onEditBooking = {
                            editTargetSpaceId = (space ?: allSpaces.firstOrNull())?.id
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
                            pendingCancelTarget = booking
                        },
                        onCancelAcceptedBooking = {
                            cancelTargetBooking = booking
                        },
                        onMarkPaid = {
                            viewModel.acknowledgePayment(booking.id, asHost = false, context)
                        }
                    )
                }
            }
        }
    }

    // Re-booking dialog — reuses the standard Rental Request flow so a re-book
    // is configured exactly the way a first booking is.
    if (rebookTargetSpaceId != null) {
        // Re-derived from the live allSpaces list on every recomposition, not a
        // snapshot frozen at tap time — see the state declaration's comment above.
        val targetSpace = allSpaces.find { it.id == rebookTargetSpaceId } ?: allSpaces.firstOrNull()
        val sourceBooking = rebookSourceBooking

        if (targetSpace != null) {
            RebookDialog(
                space = targetSpace,
                initialFormula = sourceBooking?.formula,
                viewModel = viewModel,
                onDismiss = {
                    rebookTargetSpaceId = null
                    rebookSourceBooking = null
                },
                // submitBookingRequest already confirms the request by toast, so this
                // just closes the dialog.
                onRequestSubmitted = {
                    rebookTargetSpaceId = null
                    rebookSourceBooking = null
                }
            )
        }
    }

    // Edit Active Booking dialog — the same Rental Request flow, pre-seeded with the
    // current accepted formula, submitted as a new PENDING request referencing the
    // booking it would replace. If the host accepts it, ProHostRepository.acceptBookingRequest
    // releases the old booking and this one takes its place; availability is always
    // computed live from ACCEPTED bookings, so nothing else needs recalculating by hand.
    if (editTargetSpaceId != null) {
        // Re-derived from the live allSpaces list on every recomposition — see
        // rebookTargetSpaceId's comment above for why.
        val targetSpace = allSpaces.find { it.id == editTargetSpaceId } ?: allSpaces.firstOrNull()
        val sourceBooking = editSourceBooking

        // EditBookingDialog requires a real replacesBookingId (non-null) — editSourceBooking
        // is always set alongside editTargetSpaceId by BookingReservationCard's
        // onEditBooking callback, but the type itself doesn't guarantee that, so this
        // guards it explicitly rather than force-unwrapping.
        if (targetSpace != null && sourceBooking != null) {
            EditBookingDialog(
                space = targetSpace,
                initialFormula = sourceBooking.formula,
                replacesBookingId = sourceBooking.id,
                viewModel = viewModel,
                onDismiss = {
                    editTargetSpaceId = null
                    editSourceBooking = null
                },
                // submitBookingRequest already confirms the request by toast, so this
                // just closes the dialog.
                onRequestSubmitted = {
                    editTargetSpaceId = null
                    editSourceBooking = null
                }
            )
        }
    }

    // Cancel PENDING Request confirmation — this used to fire the moment the button
    // was tapped, with no confirmation at all; a mis-tap silently withdrew a request
    // still awaiting the host's response with no way to undo it.
    if (pendingCancelTarget != null) {
        val target = pendingCancelTarget!!
        AlertDialog(
            onDismissRequest = { pendingCancelTarget = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Cancel this request?") },
            text = { Text("Your rental request for \"${target.spaceTitle}\" will be withdrawn. The host will no longer be able to accept it.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelBookingRequest(target.id, context)
                    pendingCancelTarget = null
                }) {
                    Text("Cancel Request", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelTarget = null }) {
                    Text("Keep Request")
                }
            }
        )
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
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Icons.Default.LocationOn to bkg.spaceTitle,
                                Icons.Default.Person to "Renter: ${bkg.practitionerName} (${bkg.practitionerSpecialty})",
                                Icons.Default.CalendarMonth to "Dates: ${bkg.startDate} → ${bkg.endDate}",
                                Icons.Default.Schedule to "Schedule: ${bkg.selectedDays.joinToString()} • ${bkg.selectedStartHour} - ${bkg.selectedEndHour}"
                            ).forEach { (icon, text) ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Smart Key Pass: PRO-PASS-${bkg.id.take(6).uppercase()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (bkg.agreementUrl != null) {
                        Text(
                            text = "This pass links to the signed leasing agreement your host uploaded when accepting.",
                            style = MaterialTheme.typography.labelSmall,
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
                                style = MaterialTheme.typography.labelSmall,
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
    onMarkPaid: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val statusAccent = when (booking.status) {
        BookingRequestStatus.ACCEPTED -> FreshGreen
        BookingRequestStatus.PENDING -> BrightOrange
        BookingRequestStatus.CANCELLED -> CoolGray
        BookingRequestStatus.REJECTED -> CrimsonRed
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusAccent, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            )
        Column(
            modifier = Modifier
                .weight(1f)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${booking.spaceDistrict}, ${booking.governorate.displayName} • Ref #${booking.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge — the shared ProStatusBadge component (already used for
                // this exact status enum on SpecialistProfileScreen's own booking
                // cards) instead of a second, independently hand-rolled Surface+Icon+
                // Text chip with its own separate color mapping.
                when (booking.status) {
                    BookingRequestStatus.ACCEPTED -> ProStatusBadge(ProBadgeType.ACCEPTED_LOCKED)
                    BookingRequestStatus.PENDING -> ProStatusBadge(ProBadgeType.PENDING)
                    BookingRequestStatus.CANCELLED -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Cancelled")
                    BookingRequestStatus.REJECTED -> ProStatusBadge(ProBadgeType.CUSTOM_ERROR, customText = "Declined")
                }
            }

            // Step Progress Tracker — only meaningful while a request is still
            // moving toward a live booking; a CANCELLED/REJECTED request has no
            // "progress" left to show.
            if (booking.status == BookingRequestStatus.ACCEPTED || booking.status == BookingRequestStatus.PENDING) {
                BookingStatusProgressStepper(status = booking.status)
            }

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
                                style = MaterialTheme.typography.labelSmall,
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
                                style = MaterialTheme.typography.labelSmall,
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

            // Mutual, independent "Mark as Paid" acknowledgment — record-keeping
            // only (rent settlement itself happens entirely outside the app).
            if (booking.status == BookingRequestStatus.ACCEPTED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (booking.paymentAcknowledgedBySpecialist) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (booking.paymentAcknowledgedBySpecialist) "You marked this paid" else "Not marked paid yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (booking.paymentAcknowledgedBySpecialist) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!booking.paymentAcknowledgedBySpecialist) {
                        TextButton(onClick = onMarkPaid) {
                            Text("Mark as Paid", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Interactive Actions Bar — Re-book and WhatsApp stay directly
            // tappable (the two most common actions); everything else (Digital
            // Pass, Edit Booking, Cancel Request, Cancel Accepted Booking) collapses
            // into one "More" overflow menu so this row never grows past 3 controls
            // regardless of a booking's status.
            var showMoreMenu by remember { mutableStateOf(false) }
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
                        style = MaterialTheme.typography.labelMedium
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

                val hasMoreActions = booking.status == BookingRequestStatus.ACCEPTED || booking.status == BookingRequestStatus.PENDING
                if (hasMoreActions) {
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Actions", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            if (booking.status == BookingRequestStatus.ACCEPTED) {
                                // Digital Key Pass — links to the signed agreement
                                DropdownMenuItem(
                                    text = { Text("Digital Pass") },
                                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                                    onClick = { showMoreMenu = false; onViewDigitalPass() }
                                )
                                // Edit Booking — submits a change for host approval;
                                // replaces this booking if/when accepted, distinct from
                                // Re-book/Extend (which creates an independent new lease).
                                DropdownMenuItem(
                                    text = { Text("Edit Booking") },
                                    leadingIcon = { Icon(Icons.Default.EditCalendar, contentDescription = null) },
                                    modifier = Modifier.testTag("edit_booking_button_${booking.id}"),
                                    onClick = { showMoreMenu = false; onEditBooking() }
                                )
                                // Cancel Accepted Booking (early termination)
                                DropdownMenuItem(
                                    text = { Text("Cancel Booking", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMoreMenu = false; onCancelAcceptedBooking() }
                                )
                            }
                            if (booking.status == BookingRequestStatus.PENDING) {
                                // Cancel Request — withdraws a not-yet-accepted request
                                DropdownMenuItem(
                                    text = { Text("Cancel Request", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMoreMenu = false; onCancelRequest() }
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
