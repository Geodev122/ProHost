package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ListingCreateResult
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OwnerHubScreen(
    viewModel: ProHostViewModel,
    onSelectSpace: (SpaceListing) -> Unit,
    onOpenSubscriptions: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val packagePlans by viewModel.packagePlans.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    val availableFacilities = remember(architectureSchema) {
        // Falls back to the old hardcoded catalog only if Firestore hasn't delivered
        // a real schema yet (or an admin has disabled every amenity) — never leaves
        // the facility picker with zero options.
        architectureSchema.amenities.filter { it.isEnabled }.map { it.name }
            .ifEmpty { FacilityCatalog.standard }
    }

    // ownerId is the sole, authoritative match — a substring "ownerName contains
    // fullName" fallback used to sit here too, which is a real cross-tenant
    // privacy bug: any host whose name is a substring of another host's listed
    // owner name (e.g. "Sara" inside "Sara Khalil Clinic") would see that other
    // host's real listings merged into their own dashboard.
    val ownerSpaces = remember(spaces, currentUser) {
        val user = currentUser
        if (user != null) {
            spaces.filter { it.ownerId == user.id || user.role == UserRole.ADMIN }
        } else {
            emptyList()
        }
    }

    var showRenewalDialog by remember { mutableStateOf(false) }
    var selectedSpaceForSchedule by remember { mutableStateOf<SpaceListing?>(null) }
    var showCreateListingDialog by remember { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var deletingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var verifyingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var draftToEdit by remember { mutableStateOf<SpaceListing?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Checked once here (not inside the wizard) so a host who's already at their
    // package's cap sees that immediately on the "Add New Workspace Listing" card
    // instead of only discovering it after completing the whole multi-step form.
    val atListingLimit = remember(currentUser, packagePlans) { viewModel.isAtListingLimit() }

    OwnerHubScreenContent(
        ownerSpaces = ownerSpaces,
        allBookingRequests = allBookingRequests,
        currentPackage = currentUser?.ownerPackageId?.let { packagePlans.packages[it] },
        ownerPackageExpiryMillis = currentUser?.ownerPackageExpiryMillis,
        // Admin's listing/booking capability is unconditional — never a purchased
        // package (see ProHostNavGraph excluding OwnerSubscriptions from Admin's
        // allowed tabs) — so the package/renewal banner never shows for Admin.
        isAdminUnlimited = currentUser?.role == UserRole.ADMIN,
        atListingLimit = atListingLimit,
        onSelectSpace = onSelectSpace,
        onOpenWhishRenewal = { showRenewalDialog = true },
        onOpenScheduleEditor = { space -> selectedSpaceForSchedule = space },
        onOpenCreateListing = {
            viewModel.refreshTopHashtags()
            showCreateListingDialog = true
        },
        onOpenPackageSelection = { onOpenSubscriptions?.invoke() },
        onEditSpace = { space -> viewModel.refreshTopHashtags(); editingSpace = space },
        onDeleteSpace = { space -> deletingSpace = space },
        onOpenListingVerification = { space -> verifyingSpace = space },
        onContinueDraft = { space -> viewModel.refreshTopHashtags(); draftToEdit = space },
        onToggleListingStatus = { space ->
            val next = if (space.status == ListingStatus.PAUSED) ListingStatus.ACTIVE else ListingStatus.PAUSED
            viewModel.setListingStatus(space.id, next, context)
        }
    )

    // Get Listing Verified — optional, not part of the create/publish flow. See
    // ListingVerificationDialog.kt.
    verifyingSpace?.let { space ->
        ListingVerificationDialog(
            space = space,
            viewModel = viewModel,
            onDismiss = { verifyingSpace = null }
        )
    }

    // Owner-level entitlement renewal — a real PAYG cart or tiered-package renewal,
    // not tied to any one listing. See SubscriptionRenewalDialog's own doc comment
    // for why this replaced the old per-listing WhishPayModal/flat-fee flow.
    if (showRenewalDialog && currentUser != null) {
        SubscriptionRenewalDialog(
            currentUser = currentUser!!,
            viewModel = viewModel,
            onDismiss = { showRenewalDialog = false }
        )
    }

    // Space Availability Schedule Editor Dialog
    selectedSpaceForSchedule?.let { space ->
        SpaceScheduleEditorDialog(
            space = space,
            viewModel = viewModel,
            onDismiss = { selectedSpaceForSchedule = null }
        )
    }

    // Used by the package-limit rejection branch below: save exactly what the host
    // built as a Draft (reusing the same listingId Publish would have used) instead
    // of losing the whole wizard, then send them straight to whatever purchase
    // unblocks it — entitlements.ts auto-publishes this same Draft the moment that
    // payment settles (see ProHostViewModel.pendingAutoPublishDraftId).
    // Only actually redirects/arms the correlation once the Draft save is confirmed
    // persisted — a failed save here would otherwise point the correlation at a
    // Draft that doesn't exist yet.
    suspend fun redirectBlockedListingToPayment(newListing: SpaceListing, successMessage: String, failureMessage: String) {
        val saved = viewModel.saveListingDraft(newListing.copy(status = ListingStatus.DRAFT))
        showCreateListingDialog = false
        draftToEdit = null
        android.widget.Toast.makeText(context, if (saved) successMessage else failureMessage, android.widget.Toast.LENGTH_LONG).show()
        if (saved) {
            viewModel.setPendingAutoPublishDraft(newListing.id)
            onOpenSubscriptions?.invoke()
        }
    }

    // Create Granular Space Listing Dialog — also reused for "Continue Editing" a
    // Draft (draftToEdit), since both are the same multi-step wizard pre-populated
    // from an existing SpaceListing or not.
    if (showCreateListingDialog || draftToEdit != null) {
        val draft = draftToEdit
        val topHashtags by viewModel.topHashtags.collectAsState()
        CreateListingDialog(
            currentUser = currentUser,
            existingDraft = draft,
            suggestedHashtags = topHashtags,
            availableFacilities = availableFacilities,
            spaceCategories = architectureSchema.spaceTypes,
            onDismiss = { showCreateListingDialog = false; draftToEdit = null },
            onSaveDraft = { updatedDraft ->
                coroutineScope.launch {
                    val success = viewModel.saveListingDraft(updatedDraft)
                    showCreateListingDialog = false
                    draftToEdit = null
                    android.widget.Toast.makeText(
                        context,
                        if (success) "Draft saved — continue it anytime from My Listings." else "Couldn't save this draft — please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            // Silent — unlike onSaveDraft above, never closes the dialog or toasts;
            // just persists progress in the background while the host keeps typing.
            onAutoSaveDraft = { draft ->
                coroutineScope.launch { viewModel.saveListingDraft(draft) }
            },
            onListingCreated = { newListing ->
                coroutineScope.launch {
                    when (viewModel.createNewSpaceListing(newListing)) {
                        is ListingCreateResult.Success -> {
                            showCreateListingDialog = false
                            draftToEdit = null
                            android.widget.Toast.makeText(context, "Workspace listing published successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            // Straight into the real Availability Control editor (same one
                            // used to manage an existing listing) so the host sets operating
                            // hours, blackout slots, and any additional formulas right after
                            // publishing. Only reached once the listing is really persisted,
                            // since every save in that editor looks the space up first.
                            selectedSpaceForSchedule = newListing
                        }
                        is ListingCreateResult.PackageLimitReached -> {
                            redirectBlockedListingToPayment(
                                newListing,
                                "Saved as a Draft — you've reached your listing limit. Upgrade your package to publish it automatically.",
                                "Couldn't save this as a Draft — check your connection and try Publish again once you've upgraded."
                            )
                        }
                        is ListingCreateResult.Failed -> {
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't publish this listing — check your connection and try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't publish this listing — check your connection and try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    // Edit Listing Dialog — reuses the real wizard (photos, subdivisions, pricing
    // config, ownership doc, everything), the same "existingDraft + onListingUpdated"
    // admin-edit mode CreateListingDialog already supports for the Admin Console's
    // own Edit action (AdminConsoleScreen.kt). This replaces the bare six-field
    // OwnerEditListingDialog (title/district/street/floor/price/phone only) that
    // used to be a host's sole edit path once a listing was past Draft — subdivisions,
    // pricing strategies, photos, facilities, description, house rules, and equipment
    // were all simply unreachable on a host's own published listing.
    editingSpace?.let { space ->
        val editTopHashtags by viewModel.topHashtags.collectAsState()
        CreateListingDialog(
            currentUser = currentUser,
            existingDraft = space,
            suggestedHashtags = editTopHashtags,
            availableFacilities = availableFacilities,
            spaceCategories = architectureSchema.spaceTypes,
            onDismiss = { editingSpace = null },
            onListingCreated = {},
            onListingUpdated = { updated ->
                coroutineScope.launch {
                    val success = viewModel.updateOwnerListing(updated)
                    editingSpace = null
                    android.widget.Toast.makeText(
                        context,
                        if (success) "Listing updated successfully!" else "Failed to update listing — please try again",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // Delete Listing Confirmation Dialog
    deletingSpace?.let { space ->
        OwnerDeleteListingDialog(
            listing = space,
            onDismiss = { deletingSpace = null },
            onConfirm = {
                coroutineScope.launch {
                    val success = viewModel.deleteOwnerListing(space.id)
                    deletingSpace = null
                    android.widget.Toast.makeText(
                        context,
                        if (success) "Listing removed" else "Failed to remove listing — please try again",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun OwnerDeleteListingDialog(
    listing: SpaceListing,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete Listing?") },
        text = {
            Text("Are you sure you want to permanently remove '${listing.title}' from the platform? This cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OwnerHubScreenContent(
    ownerSpaces: List<SpaceListing>,
    allBookingRequests: List<BookingRequest>,
    currentPackage: PackagePlan?,
    ownerPackageExpiryMillis: Long?,
    onSelectSpace: (SpaceListing) -> Unit,
    onOpenWhishRenewal: () -> Unit,
    onOpenScheduleEditor: (SpaceListing) -> Unit,
    onOpenCreateListing: () -> Unit,
    onOpenPackageSelection: () -> Unit,
    isAdminUnlimited: Boolean = false,
    atListingLimit: Boolean = false,
    onEditSpace: (SpaceListing) -> Unit = {},
    onDeleteSpace: (SpaceListing) -> Unit = {},
    onOpenListingVerification: (SpaceListing) -> Unit = {},
    onContinueDraft: (SpaceListing) -> Unit = {},
    onToggleListingStatus: (SpaceListing) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Top Space Owner Subscription & Entitlement Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, MaterialTheme.shapes.extraLarge),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.img_owner_hero),
                        contentDescription = "Owner Dashboard Banner",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        OxfordBlueDark.copy(alpha = 0.90f),
                                        OxfordBlue.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0x33FFFFFF),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("ProHost Portal", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!isAdminUnlimited) {
                                Surface(
                                    color = WhishRed,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (currentPackage == null) {
                                            "No Active Package"
                                        } else {
                                            "$${String.format(Locale.US, "%.2f", currentPackage.priceUsd)} / ${currentPackage.validityDays}d"
                                        },
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                    )
                                }
                            }
                        }

                        if (isAdminUnlimited) {
                            Text(
                                text = "Unlimited Listings — Admin Access",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "No package or subscription applies to your account — every listing, of any type, is always active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xCCFFFFFF),
                                lineHeight = 16.sp
                            )
                        } else {
                            val daysRemaining = ownerPackageExpiryMillis?.let {
                                ((it - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
                            }
                            Text(
                                text = if (currentPackage == null) {
                                    "No Active Package"
                                } else if (daysRemaining != null) {
                                    "${currentPackage.name} — renews in $daysRemaining days"
                                } else {
                                    currentPackage.name
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (currentPackage == null) {
                                    "Choose a package below to start publishing workspace listings."
                                } else {
                                    "Manage smart availability, blackout offline hours, and keep your space active across Lebanon with Whish Pay."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xCCFFFFFF),
                                lineHeight = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onOpenWhishRenewal,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = WhishRed)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Renew", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onOpenPackageSelection,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Manage Packages", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Highlighted & Centered Add Workspace Action Button Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                contentAlignment = Alignment.Center
            ) {
                // At the Package-2 cap, this becomes an upsell instead of opening a
                // multi-step wizard that createNewSpaceListing will just reject at the
                // end — the host finds out immediately, not after filling the whole form.
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, MaterialTheme.shapes.large)
                        .clickable { if (atListingLimit) onOpenPackageSelection() else onOpenCreateListing() },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (atListingLimit) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                    ),
                    border = BorderStroke(2.dp, if (atListingLimit) AmberWarning else FreshGreen)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = if (atListingLimit) AmberWarning else MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (atListingLimit) Icons.Default.Lock else Icons.Default.AddBusiness,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.lg))
                        Column {
                            // Names the host's real, admin-configured package/limit
                            // instead of a hardcoded "3-Listing"/"Package 3" — those
                            // numbers are no longer fixed since packages became an
                            // arbitrary admin-authored catalog (Phase 15); a listing
                            // limit can be any number, or a package's own name can be
                            // anything, not necessarily "Package 3".
                            Text(
                                text = if (atListingLimit) {
                                    currentPackage?.listingLimit?.let { "You've Reached Your $it-Listing Limit" }
                                        ?: "You've Reached Your Listing Limit"
                                } else {
                                    "Add New Workspace Listing"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (atListingLimit) "Tap to upgrade your package for more listings" else "Publish clinic, office, or studio space with smart pricing formulas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Section Title: Owner's Workspace Listings
        item {
            ProSectionHeader(
                title = "My Listings (${ownerSpaces.size})",
                subtitle = "Manage facilities, pricing formulas, and availability schedules",
                icon = Icons.Default.HomeWork
            )
        }

        if (ownerSpaces.isEmpty()) {
            item {
                ProEmptyState(
                    title = "No Listings Published",
                    description = "You don't have any workspace listings yet. Click 'Add New Workspace Listing' above to publish your first office or clinic.",
                    icon = Icons.Default.HomeWork
                )
            }
        } else {
            // Listings List
            items(ownerSpaces, key = { it.id }) { space ->
                val spaceAcceptedBookings = allBookingRequests.filter { it.spaceId == space.id && it.status == BookingRequestStatus.ACCEPTED }
                val rentedH = spaceAcceptedBookings.sumOf { it.formula.totalWeeklyHours }
                val blackoutH = space.schedule.blackoutSlots.size * 2
                val totalOperatingDays = space.schedule.operatingDays.size
                val dailyH = (space.schedule.closingHour.substringBefore(":").toIntOrNull() ?: 20) - (space.schedule.openingHour.substringBefore(":").toIntOrNull() ?: 8)
                val totalH = dailyH * totalOperatingDays
                val openH = (totalH - rentedH - blackoutH).coerceAtLeast(0)

                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (space.isActiveSubscription) {
                                ProStatusBadge(ProBadgeType.ACTIVE_30D)
                            } else {
                                ProStatusBadge(ProBadgeType.EXPIRED)
                            }

                            ProCurrencyTag(rateUsd = space.baseMonthlyRateUsd, isPerMonth = true)
                        }

                        Column {
                            Text(
                                text = space.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "📍 ${space.district}, ${space.governorate.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // The host's own lifecycle status — Active listings show no extra
                        // badge here (the subscription badge above already covers that
                        // case); Draft and Paused are the two states worth calling out.
                        if (space.status != ListingStatus.ACTIVE) {
                            Surface(
                                color = if (space.status == ListingStatus.DRAFT) MaterialTheme.colorScheme.surfaceVariant else StatusWarningContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (space.status == ListingStatus.DRAFT) Icons.Default.EditNote else Icons.Default.PauseCircle,
                                        contentDescription = null,
                                        tint = if (space.status == ListingStatus.DRAFT) MaterialTheme.colorScheme.onSurfaceVariant else StatusOnWarningContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = if (space.status == ListingStatus.DRAFT) "Draft — not published yet" else "Paused — hidden from Discovery",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (space.status == ListingStatus.DRAFT) MaterialTheme.colorScheme.onSurfaceVariant else StatusOnWarningContainer
                                        )
                                        // Set only when the server moved this back to Draft on its
                                        // own (onWorkspaceListingPublishValidation) — e.g. it was
                                        // auto-published after payment but never actually had real
                                        // pricing configured. Cleared automatically the next time
                                        // this listing is saved.
                                        if (space.status == ListingStatus.DRAFT && space.publishBlockedReasons.isNotEmpty()) {
                                            Text(
                                                text = "Reverted from Active — " + space.publishBlockedReasons.joinToString(" "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Listing Verified is genuinely earned now — see
                        // SpaceListing.isVerified's doc comment — so this is either a
                        // static confirmation or a tappable entry point, never a badge
                        // shown unconditionally.
                        Surface(
                            color = if (space.isVerified) StatusSuccessContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (space.isVerified) it else it.clickable { onOpenListingVerification(space) } }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (space.isVerified) Icons.Default.Verified else Icons.Default.GppMaybe,
                                    contentDescription = null,
                                    tint = if (space.isVerified) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (space.isVerified) "Listing Verified" else "Not Listing Verified — tap to earn this badge (optional)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (space.isVerified) StatusOnSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (space.isOwnerSuspended) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Account suspended — this listing is hidden from Discovery until reactivated.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Smart Availability summary badges — 2 rows of 2 so the 4
                        // values wrap instead of squeezing into one line.
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🗓️ ${space.schedule.openingHour}-${space.schedule.closingHour}", style = MaterialTheme.typography.labelSmall)
                                    Text("🔒 Rented: ${rentedH}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🚫 Closed: ${blackoutH}h", style = MaterialTheme.typography.labelSmall, color = NeutralGray500)
                                    Text("🟢 Open: ${openH}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusSuccess)
                                }
                            }
                        }

                        // Engagement metrics snapshot
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.RemoveRedEye, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${space.avatarEngagementViews} Views", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp), tint = WhatsAppGreen)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${space.avatarInquiryClicks} Inquiries", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Action buttons — a Draft has no live schedule to manage yet, so
                        // its row leads with "Continue Editing" (the same wizard, pre-
                        // populated) instead of Availability/Edit.
                        if (space.status == ListingStatus.DRAFT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSelectSpace(space) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("Details", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = { onContinueDraft(space) },
                                    modifier = Modifier.weight(1.5f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Continue Editing", style = MaterialTheme.typography.labelMedium)
                                }

                                IconButton(
                                    onClick = { onDeleteSpace(space) },
                                    modifier = Modifier.weight(0.6f)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Draft", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // View Specs Button
                                OutlinedButton(
                                    onClick = { onSelectSpace(space) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("Details", style = MaterialTheme.typography.labelMedium)
                                }

                                // Availability & Schedule Control Button
                                Button(
                                    onClick = { onOpenScheduleEditor(space) },
                                    modifier = Modifier.weight(1.5f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Availability", style = MaterialTheme.typography.labelMedium)
                                }

                                // Edit Listing Button
                                OutlinedButton(
                                    onClick = { onEditSpace(space) },
                                    modifier = Modifier.weight(0.9f),
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Listing", modifier = Modifier.size(14.dp))
                                }

                                // Pause/Resume Button — the host's own lifecycle control,
                                // distinct from isActiveSubscription (billing), which the
                                // host doesn't control directly.
                                IconButton(
                                    onClick = { onToggleListingStatus(space) },
                                    modifier = Modifier.weight(0.6f)
                                ) {
                                    Icon(
                                        if (space.status == ListingStatus.PAUSED) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                                        contentDescription = if (space.status == ListingStatus.PAUSED) "Resume Listing" else "Pause Listing",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Delete Listing Button — weighted like the other controls
                                // so they all divide the row's width predictably instead of
                                // this one's intrinsic size squeezing the rest.
                                IconButton(
                                    onClick = { onDeleteSpace(space) },
                                    modifier = Modifier.weight(0.6f)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Listing", tint = MaterialTheme.colorScheme.error)
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
