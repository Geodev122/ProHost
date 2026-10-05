package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSelectSpace: (SpaceListing) -> Unit,
    // Opens on this booking: its tab is selected and the list scrolls to it ("View request"
    // after sending one, or a booking push). [onHighlightShown] clears it once done.
    highlightBookingId: String? = null,
    onHighlightShown: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentUser by viewModel.currentUser.collectAsState()
    val liveSpaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    // Explore loads public listings a page at a time: a booking's space may not be in that
    // page, so fetch the missing ones by id (Re-book/Edit must never refuse a live listing).
    val fetchedSpaces = remember { androidx.compose.runtime.mutableStateMapOf<String, SpaceListing>() }
    val missingSpaceIds = allBookingRequests.filter { it.practitionerId == currentUser?.id }.map { it.spaceId }.filter { id -> id.isNotBlank() && liveSpaces.none { it.id == id } }.toSet()
    LaunchedEffect(missingSpaceIds) {
        missingSpaceIds.filter { it !in fetchedSpaces }.forEach { id ->
            viewModel.repository.fetchListing(id)?.let { fetchedSpaces[id] = it }
        }
    }
    val allSpaces = liveSpaces + fetchedSpaces.values.filter { f -> liveSpaces.none { it.id == f.id } }
    val hasLoadedBookingsOnce by viewModel.hasLoadedBookingsOnce.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()

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

    val activeBookings = remember(userBookings) { userBookings.filter { it.status == BookingRequestStatus.ACCEPTED } }
    val pendingBookings = remember(userBookings) { userBookings.filter { it.status == BookingRequestStatus.PENDING } }
    val pastBookings = remember(userBookings) {
        userBookings.filter { it.status == BookingRequestStatus.REJECTED || it.status == BookingRequestStatus.CANCELLED }
    }

    // SO2: Play branded notification sound when booking transitions to ACCEPTED or REJECTED.
    val prevBookingStatuses = remember { mutableStateMapOf<String, BookingRequestStatus>() }
    LaunchedEffect(allBookingRequests) {
        allBookingRequests.forEach { booking ->
            val prev = prevBookingStatuses[booking.id]
            if (prev != null && prev != booking.status &&
                (booking.status == BookingRequestStatus.ACCEPTED || booking.status == BookingRequestStatus.REJECTED)) {
                try {
                    val mp = MediaPlayer.create(context, com.example.R.raw.booking_update)
                    mp?.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    mp?.setOnCompletionListener { it.release() }
                    mp?.start()
                } catch (e: Exception) {
                    android.util.Log.e("SoundPlayback", "Failed to play sound", e)
                }
            }
            prevBookingStatuses[booking.id] = booking.status
        }
    }

    // State — 0: Active, 1: Pending, 2: Past
    var selectedMainTab by remember { mutableStateOf(0) }

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
    var cancelTargetBooking by remember { mutableStateOf<BookingRequest?>(null) }
    var pendingCancelTarget by remember { mutableStateOf<BookingRequest?>(null) }

    val filteredBookings = when (selectedMainTab) {
        0 -> activeBookings
        1 -> pendingBookings
        else -> pastBookings
    }

    val bookingsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var flashBookingId by remember { mutableStateOf<String?>(null) }
    val latestBookings by rememberUpdatedState(userBookings)
    // Keyed on the id and on "bookings have loaded", not on every list change, so a live
    // update doesn't restart (and cancel) the scroll.
    LaunchedEffect(highlightBookingId, userBookings.isNotEmpty()) {
        val id = highlightBookingId ?: return@LaunchedEffect
        val target = latestBookings.firstOrNull { it.id == id } ?: return@LaunchedEffect
        selectedMainTab = when (target.status) {
            BookingRequestStatus.ACCEPTED -> 0
            BookingRequestStatus.PENDING -> 1
            else -> 2
        }
        flashBookingId = target.id
        kotlinx.coroutines.delay(150) // let the selected tab's list compose
        val list = latestBookings.filter {
            when (selectedMainTab) {
                0 -> it.status == BookingRequestStatus.ACCEPTED
                1 -> it.status == BookingRequestStatus.PENDING
                else -> it.status == BookingRequestStatus.REJECTED || it.status == BookingRequestStatus.CANCELLED
            }
        }
        val index = list.indexOfFirst { it.id == target.id }
        if (index >= 0) runCatching { bookingsListState.animateScrollToItem(index) }
        kotlinx.coroutines.delay(2_500)
        flashBookingId = null
        onHighlightShown()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .proHostScreenBackground()
            .testTag("my_bookings_screen")
    ) {
        // 3-tab segmented toggle: Active / Pending / Past
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SegmentedButton(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = { Text("Active (${activeBookings.size})", style = MaterialTheme.typography.labelSmall) }
                )
                SegmentedButton(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = { Text("Pending (${pendingBookings.size})", style = MaterialTheme.typography.labelSmall) }
                )
                SegmentedButton(
                    selected = selectedMainTab == 2,
                    onClick = { selectedMainTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = { Text("Past (${pastBookings.size})", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Main Bookings Content List
        val roleAccent = when (currentUser?.role) {
            UserRole.PRO_HOST -> MaterialTheme.colorScheme.secondary
            UserRole.ADMIN -> MaterialTheme.proColors.warning
            else -> MaterialTheme.colorScheme.primary
        }
        if (!hasLoadedBookingsOnce && !isOffline) {
            // The first Firestore snapshot hasn't arrived yet — without this, an
            // account with real bookings briefly showed "No bookings" before the
            // real list streamed in, indistinguishable from actually having none.
            ShimmerLoadingList(itemHeight = 140.dp)
        } else if (!hasLoadedBookingsOnce && isOffline) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "Can't reach server",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Check your connection and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
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
                        text = when (selectedMainTab) {
                            0 -> "No active reservations found"
                            1 -> "No pending requests"
                            else -> "No past reservation history"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Browse verified professional workspaces and send a booking request to get started.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(
                        onClick = onNavigateToDiscovery,
                        modifier = Modifier.testTag("empty_state_browse_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Browse Workspaces", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                state = bookingsListState,
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
                        canCreateBookings = currentUser?.role != UserRole.PRO_HOST,
                        modifier = if (booking.id == flashBookingId) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                        } else Modifier,
                        onRebook = {
                            // Only the booking's own workspace — never another listing (which
                            // would send the request to a different host).
                            if (space == null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "This workspace is no longer available to book.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                rebookTargetSpaceId = space.id
                                rebookSourceBooking = booking
                            }
                        },
                        onEditBooking = {
                            // Never fall back to firstOrNull: an edit request must reference
                            // the booking's own space (replacesBookingId ties them together),
                            // so opening the dialog for a different space is wrong.
                            editTargetSpaceId = space?.id
                            editSourceBooking = booking
                        },
                        onAddPaymentReminders = {
                            com.example.ui.util.PaymentCalendar.addPaymentReminders(context, booking, booking.publicCode)
                            com.example.analytics.AnalyticsTracker.calendarReminderAdd()
                        },
                        onViewAgreement = {
                            booking.agreementUrl?.let { url ->
                                try {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open the agreement.", Toast.LENGTH_SHORT).show()
                                }
                            }
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
        val targetSpace = allSpaces.find { it.id == rebookTargetSpaceId }
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
                },
                attendeePackages = architectureSchema.attendeePackages.filter { it.isEnabled },
                initialAttendeeCount = sourceBooking?.attendeeCount ?: 0
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
        // rebookTargetSpaceId's comment above for why. No firstOrNull fallback:
        // if the booking's space is gone from the live list the dialog must not
        // open at all (unlike Rebook, this dialog's replacesBookingId binds it
        // to the original space — submitting it against a different one is wrong).
        val targetSpace = allSpaces.find { it.id == editTargetSpaceId }
        val sourceBooking = editSourceBooking

        // EditBookingDialog requires a real replacesBookingId (non-null) — editSourceBooking
        // is always set alongside editTargetSpaceId by BookingReservationCard's
        // onEditBooking callback, but the type itself doesn't guarantee that, so this
        // guards it explicitly rather than force-unwrapping.
        if (targetSpace == null) {
            LaunchedEffect(editTargetSpaceId) {
                Toast.makeText(context, "This space is no longer available to edit.", Toast.LENGTH_SHORT).show()
                editTargetSpaceId = null
                editSourceBooking = null
            }
        } else if (sourceBooking != null) {
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
                },
                attendeePackages = architectureSchema.attendeePackages.filter { it.isEnabled },
                initialAttendeeCount = sourceBooking.attendeeCount
            )
        }
    }

    // Cancel PENDING Request confirmation — this used to fire the moment the button
    // was tapped, with no confirmation at all; a mis-tap silently withdrew a request
    // still awaiting the host's response with no way to undo it.
    pendingCancelTarget?.let { target ->
        ProHostDialog(
            onDismissRequest = { pendingCancelTarget = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Cancel this request?") },
            text = { Text("Your rental request for \"${target.spaceTitle}\" will be withdrawn. The host will no longer be able to accept it.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
    cancelTargetBooking?.let { bkg ->
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

}

@Composable
fun BookingReservationCard(
    booking: BookingRequest,
    space: SpaceListing?,
    onSelectSpace: () -> Unit,
    onRebook: () -> Unit,
    onEditBooking: () -> Unit,
    onAddPaymentReminders: () -> Unit,
    onViewAgreement: () -> Unit,
    onContactWhatsApp: () -> Unit,
    onCancelRequest: () -> Unit,
    onCancelAcceptedBooking: () -> Unit = {},
    onMarkPaid: () -> Unit = {},
    // Pro Hosts are landlord-only (firestore.rules booking create), so they can view
    // bookings made before upgrading but not re-book or edit them.
    canCreateBookings: Boolean = true,
    modifier: Modifier = Modifier
) {
    val statusAccent = when (booking.status) {
        BookingRequestStatus.ACCEPTED -> MaterialTheme.proColors.success
        BookingRequestStatus.PENDING -> MaterialTheme.proColors.warning
        BookingRequestStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        BookingRequestStatus.REJECTED -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.id}"),
        shape = MaterialTheme.shapes.large,
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
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified Space",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
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
                            text = "${booking.spaceDistrict}, ${booking.governorate.displayName} • Ref ${booking.publicCode}",
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
                BookingStatusProgressStepper(status = booking.status, startDate = booking.startDate)
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
                            val daysStr = if (booking.selectedDays.isNotEmpty()) {
                                booking.selectedDays.joinToString(", ")
                            } else {
                                booking.formula.daysOfWeek.joinToString(", ")
                            }
                            val timeStr = if (booking.selectedStartHour.isNotBlank()) {
                                "${booking.selectedStartHour} - ${booking.selectedEndHour}"
                            } else {
                                "${booking.formula.startHour} - ${booking.formula.endHour}"
                            }
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
                    com.example.ui.util.AttendeePricing.bookingSummary(booking)?.let { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (booking.status == BookingRequestStatus.ACCEPTED) {
                    if (booking.agreementUrl != null) {
                        Surface(color = MaterialTheme.proColors.successContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                "Space Rules On File",
                                color = MaterialTheme.proColors.onSuccessContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                            Text(
                                "No space rules or access guidance provided",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.proColors.success, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (booking.paymentAcknowledgedBySpecialist) "You marked this paid" else "Not marked paid yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (booking.paymentAcknowledgedBySpecialist) MaterialTheme.proColors.success else MaterialTheme.colorScheme.onSurfaceVariant
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
            // tappable (the two most common actions); everything else (payment
            // reminders, agreement, Edit Booking, Cancel Request, Cancel Accepted Booking) collapses
            // into one "More" overflow menu so this row never grows past 3 controls
            // regardless of a booking's status.
            var showMoreMenu by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Re-book / Extend Button (Prominent)
                if (canCreateBookings) Button(
                    onClick = onRebook,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("rebook_button_${booking.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
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
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            if (booking.status == BookingRequestStatus.ACCEPTED) {
                                // Due dates go to the user's own calendar app, which
                                // gives local reminders for each payment.
                                DropdownMenuItem(
                                    text = { Text("Add payment reminders") },
                                    leadingIcon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                                    onClick = { showMoreMenu = false; onAddPaymentReminders() }
                                )
                                if (booking.agreementUrl != null) {
                                    DropdownMenuItem(
                                        text = { Text("View Space Rules & Access") },
                                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                        onClick = { showMoreMenu = false; onViewAgreement() }
                                    )
                                }
                                // Edit Booking — submits a change for host approval;
                                // replaces this booking if/when accepted, distinct from
                                // Re-book/Extend (which creates an independent new lease).
                                if (canCreateBookings) DropdownMenuItem(
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
    startDate: String = "",
    modifier: Modifier = Modifier
) {
    val steps = listOf("Requested", "Host Review", "Confirmed", "Access Active")
    // An accepted booking is only "Access Active" once its start date arrives.
    val hasStarted = remember(startDate) {
        runCatching {
            val start = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(startDate)
            start == null || !start.after(java.util.Date())
        }.getOrDefault(true)
    }
    val currentStepIndex = when (status) {
        BookingRequestStatus.PENDING -> 1
        BookingRequestStatus.ACCEPTED -> if (hasStarted) 3 else 2
        BookingRequestStatus.CANCELLED, BookingRequestStatus.REJECTED -> 0
    }
    val active = status != BookingRequestStatus.CANCELLED && status != BookingRequestStatus.REJECTED

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        steps.forEachIndexed { index, stepName ->
            val isCompleted = active && index <= currentStepIndex
            val circleColor = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val labelColor = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Left connector (skip for first step)
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (active && index <= currentStepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(circleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(10.dp))
                        } else {
                            Text("${index + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Right connector (skip for last step)
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(if (active && index < currentStepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stepName,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
