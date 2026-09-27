package com.example.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.ui.util.findActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onManageSpace: (SpaceListing) -> Unit = onSelectSpace,
    onOpenSubscriptions: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isLoading by viewModel.isRestoringSession.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val packagePlans by viewModel.packagePlans.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()
    val architectureSchema by viewModel.spaceArchitectureSchema.collectAsState()
    val availableFacilities = remember(architectureSchema) {
        // Falls back to the old hardcoded catalog only if Firestore hasn't delivered
        // a real schema yet — never leaves the facility picker with zero options.
        architectureSchema.facilities.filter { it.isEnabled }.map { it.name }
            .ifEmpty { FacilityCatalog.standard }
    }

    // ownerId is the sole, authoritative match â€” a substring "ownerName contains
    // fullName" fallback used to sit here too, which is a real cross-tenant
    // privacy bug: any host whose name is a substring of another host's listed
    // owner name (e.g. "Sara" inside "Sara Khalil Clinic") would see that other
    // host's real listings merged into their own dashboard.
    val ownerSpaces = remember(spaces, currentUser) {
        val user = currentUser
        if (user != null) {
            spaces.filter { it.ownerId == user.id || user.role == UserRole.ADMIN }
                .sortedByDescending { it.createdAtMillis ?: 0L }
        } else {
            emptyList()
        }
    }

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

    fun playSound(resId: Int) {
        try {
            val mp = MediaPlayer.create(context, resId)
            mp?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (e: Exception) {
            android.util.Log.e("SoundPlayback", "Failed to play sound", e)
        }
    }

    // SO1: Branded chime when any owned listing transitions to ACTIVE (published).
    val prevSpaceStatuses = remember { mutableStateMapOf<String, ListingStatus>() }
    LaunchedEffect(ownerSpaces) {
        ownerSpaces.forEach { space ->
            val prev = prevSpaceStatuses[space.id]
            if (prev != null && prev != space.status && space.status == ListingStatus.ACTIVE) {
                playSound(com.example.R.raw.listing_published)
            }
            prevSpaceStatuses[space.id] = space.status
        }
    }

    // SO4: Branded ping when a new PENDING booking request arrives for this host.
    val prevPendingIds = remember { mutableSetOf<String>() }
    val pendingInitialized = remember { mutableStateOf(false) }
    LaunchedEffect(allBookingRequests) {
        val ownedIds = ownerSpaces.map { it.id }.toSet()
        val currentPending = allBookingRequests
            .filter { it.status == BookingRequestStatus.PENDING && ownedIds.contains(it.spaceId) }
            .map { it.id }.toSet()
        if (pendingInitialized.value && (currentPending - prevPendingIds).isNotEmpty()) {
            playSound(com.example.R.raw.booking_request_in)
        }
        prevPendingIds.clear()
        prevPendingIds.addAll(currentPending)
        pendingInitialized.value = true
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    OwnerHubScreenContent(
        ownerSpaces = ownerSpaces,
        allBookingRequests = allBookingRequests,
        currentPackage = currentUser?.ownerPackageId?.let { packagePlans.packages[it] },
        ownerPackageExpiryMillis = currentUser?.ownerPackageExpiryMillis,
        // Admin's listing/booking capability is unconditional â€” never a purchased
        // package (see ProHostNavGraph excluding OwnerSubscriptions from Admin's
        // allowed tabs) â€” so the package/renewal banner never shows for Admin.
        isAdminUnlimited = currentUser?.role == UserRole.ADMIN,
        atListingLimit = atListingLimit,
        onSelectSpace = onSelectSpace,
        onManageSpace = onManageSpace,
        onOpenWhishRenewal = {
            val expiry = currentUser?.ownerPackageExpiryMillis
            if (expiry != null && expiry > System.currentTimeMillis()) {
                // Active Play subscription â€” open Play Store subscription management
                // Include the specific product ID so Play Store deep-links directly
                // to this subscription rather than the generic subscriptions list.
                val productId = currentUser?.ownerPackageId
                    ?.let { packagePlans.packages[it]?.googlePlayProductId }
                    ?.ifBlank { null }
                val uri = if (productId != null)
                    "market://subscriptions?sku=$productId&package=app.geonajjar.prohost"
                else
                    "market://subscriptions?package=app.geonajjar.prohost"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                val activity = context.findActivity()
                if (activity != null) {
                    try {
                        activity.startActivity(intent)
                    } catch (_: Exception) {
                        onOpenSubscriptions?.invoke()
                    }
                } else {
                    onOpenSubscriptions?.invoke()
                }
            } else {
                // Expired or no subscription â€” navigate to subscriptions screen to pick a Play plan
                onOpenSubscriptions?.invoke()
            }
        },
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

    // Get Listing Verified â€” optional, not part of the create/publish flow. See
    // ListingVerificationDialog.kt.
    verifyingSpace?.let { space ->
        ListingVerificationDialog(
            space = space,
            viewModel = viewModel,
            onDismiss = { verifyingSpace = null }
        )
    }

    // Used by the package-limit rejection branch below: save exactly what the host
    // built as a Draft (reusing the same listingId Publish would have used) instead
    // of losing the whole wizard, then send them straight to whatever purchase
    // unblocks it â€” entitlements.ts auto-publishes this same Draft the moment that
    // payment settles (see ProHostViewModel.pendingAutoPublishDraftId).
    // Only actually redirects/arms the correlation once the Draft save is confirmed
    // persisted â€” a failed save here would otherwise point the correlation at a
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

    // Create Granular Space Listing Dialog â€” also reused for "Continue Editing" a
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
            availableAmenities = architectureSchema.amenities.filter { it.isEnabled },
            availableDivisionTypeSchema = architectureSchema.divisionTypes.filter { it.isEnabled },
            onAddCustomSchemaItem = { category, name, scopedToIds -> viewModel.addUserSuggestedSchemaItem(category, name, scopedToIds) },
            onDismiss = { showCreateListingDialog = false; draftToEdit = null },
            onSaveDraft = { updatedDraft ->
                coroutineScope.launch {
                    val success = viewModel.saveListingDraft(updatedDraft)
                    showCreateListingDialog = false
                    draftToEdit = null
                    android.widget.Toast.makeText(
                        context,
                        if (success) "Draft saved â€” continue it anytime from My Listings." else "Couldn't save this draft â€” please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            // Silent â€” unlike onSaveDraft above, never closes the dialog or toasts;
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
                        }
                        is ListingCreateResult.PackageLimitReached -> {
                            redirectBlockedListingToPayment(
                                newListing,
                                "Saved as a Draft â€” you've reached your listing limit. Upgrade your package to publish it automatically.",
                                "Couldn't save this as a Draft â€” check your connection and try Publish again once you've upgraded."
                            )
                        }
                        is ListingCreateResult.Failed -> {
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't publish this listing â€” check your connection and try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't publish this listing â€” check your connection and try again.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    // Edit Listing Dialog â€” reuses the real wizard (photos, subdivisions, pricing
    // config, ownership doc, everything), the same "existingDraft + onListingUpdated"
    // admin-edit mode CreateListingDialog already supports for the Admin Console's
    // own Edit action (AdminConsoleScreen.kt). This replaces the bare six-field
    // OwnerEditListingDialog (title/district/street/floor/price/phone only) that
    // used to be a host's sole edit path once a listing was past Draft â€” subdivisions,
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
            availableAmenities = architectureSchema.amenities.filter { it.isEnabled },
            availableDivisionTypeSchema = architectureSchema.divisionTypes.filter { it.isEnabled },
            onAddCustomSchemaItem = { category, name, scopedToIds -> viewModel.addUserSuggestedSchemaItem(category, name, scopedToIds) },
            onDismiss = { editingSpace = null },
            onListingCreated = {},
            onListingUpdated = { updated ->
                coroutineScope.launch {
                    val success = viewModel.updateOwnerListing(updated)
                    editingSpace = null
                    android.widget.Toast.makeText(
                        context,
                        if (success) "Listing updated successfully!" else "Failed to update listing â€” please try again",
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
                        if (success) "Listing removed" else "Failed to remove listing â€” please try again",
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
            CustomButton(
                text = "Delete",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER,
                icon = Icons.Default.DeleteForever,
                compact = true
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED,
                compact = true
            )
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
    onManageSpace: (SpaceListing) -> Unit = onSelectSpace,
    onOpenWhishRenewal: () -> Unit,
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
    val hubHaptic = LocalHapticFeedback.current
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
                                    color = CarnationOrange,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (currentPackage == null) {
                                            "No Active Package"
                                        } else {
                                            "$${String.format(Locale.US, "%.2f", currentPackage.priceUsd)} / mo"
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
                                text = "Unlimited Listings â€” Admin Access",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "No package or subscription applies to your account â€” every listing, of any type, is always active.",
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
                                    "${currentPackage.name} â€” renews in $daysRemaining days"
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
                                    "List your space, set subdivisions, choose renting modal, and keep your business active."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xCCFFFFFF),
                                lineHeight = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isActiveSubscription = ownerPackageExpiryMillis != null && ownerPackageExpiryMillis > System.currentTimeMillis()
                                Button(
                                    onClick = onOpenWhishRenewal,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = CarnationOrange)
                                ) {
                                    Icon(
                                        if (isActiveSubscription) Icons.Default.Settings else Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isActiveSubscription) "Manage" else "Renew",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val activeCount = ownerSpaces.count { it.status == ListingStatus.ACTIVE }
                                val limitText = currentPackage?.listingLimit?.let { "$activeCount / $it Consumed" } ?: "$activeCount Active"
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = limitText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Owner's Workspace Listings
        item {
            ProSectionHeader(
                title = "My Listings (${ownerSpaces.size})",
                subtitle = "Your listings & availability",
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
                val dailyH = (space.schedule.closingHour.substringBefore(":").toIntOrNull() ?: 20) -
                    (space.schedule.openingHour.substringBefore(":").toIntOrNull() ?: 8)
                val totalH = dailyH * totalOperatingDays
                val openH = (totalH - rentedH - blackoutH).coerceAtLeast(0)

                val statusAccent = when (space.status) {
                    ListingStatus.ACTIVE -> FreshGreen
                    ListingStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
                    ListingStatus.PAUSED -> BrightOrange
                }
                ProSurfaceCard(contentPadding = PaddingValues(0.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(statusAccent, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (space.status) {
                                ListingStatus.ACTIVE -> ProStatusBadge(ProBadgeType.CUSTOM_SUCCESS, customText = "Available")
                                ListingStatus.DRAFT -> ProStatusBadge(ProBadgeType.CUSTOM_INFO, customText = "Draft")
                                ListingStatus.PAUSED -> ProStatusBadge(ProBadgeType.CUSTOM_WARNING, customText = "Paused")
                            }

                            val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)
                            ProCurrencyTag(rateUsd = lowestPrice.amount, unitLabel = lowestPrice.unitLabel)
                        }

                        Column {
                            Text(
                                text = space.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${space.district}, ${space.governorate.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // The host's own lifecycle status â€” Active listings show no extra
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
                                        tint = if (space.status == ListingStatus.DRAFT) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            StatusOnWarningContainer
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = if (space.status == ListingStatus.DRAFT) {
                                                "Draft â€” not published yet"
                                            } else {
                                                "Paused â€” hidden from Discovery"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (space.status == ListingStatus.DRAFT) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                StatusOnWarningContainer
                                            }
                                        )
                                        // Set only when the server moved this back to Draft on its
                                        // own (onWorkspaceListingPublishValidation) â€” e.g. it was
                                        // auto-published after payment but never actually had real
                                        // pricing configured. Cleared automatically the next time
                                        // this listing is saved.
                                        if (space.status == ListingStatus.DRAFT && space.publishBlockedReasons.isNotEmpty()) {
                                            Text(
                                                text = "Reverted from Active â€” " + space.publishBlockedReasons.joinToString(" "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Listing Verified is genuinely earned now â€” see
                        // SpaceListing.isVerified's doc comment â€” so this is either a
                        // static confirmation or a tappable entry point, never a badge
                        // shown unconditionally.
                        val verificationPending = !space.isVerified && !space.verificationDocUrl.isNullOrBlank()
                        Surface(
                            color = when {
                                space.isVerified -> StatusSuccessContainer
                                verificationPending -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            },
                            shape = MaterialTheme.shapes.small,
                            border = if (space.isVerified) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                // Still tappable while pending, in case the host wants to
                                // re-submit a better document before Admin reviews it.
                                .let { if (space.isVerified) it else it.clickable { onOpenListingVerification(space) } }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        when {
                                            space.isVerified -> Icons.Default.Verified
                                            verificationPending -> Icons.Default.HourglassTop
                                            else -> Icons.Default.VerifiedUser
                                        },
                                        contentDescription = null,
                                        tint = if (space.isVerified) StatusSuccess else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = when {
                                            space.isVerified -> "Verified Listing"
                                            verificationPending -> "Pending Admin Review"
                                            else -> "Earn Verified Badge"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (space.isVerified) StatusOnSuccessContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (!space.isVerified) {
                                    Text(
                                        text = if (verificationPending) "Tap to Re-submit âž”" else "Tap to Submit Proof âž”",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (space.isOwnerSuspended) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Account suspended â€” this listing is hidden from Discovery until reactivated.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Smart Availability summary badges â€” 2 rows of 2 so the 4
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
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text("${space.schedule.openingHour}-${space.schedule.closingHour}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Rented: ${rentedH}h",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeutralGray500)
                                        Text("Closed: ${blackoutH}h", style = MaterialTheme.typography.labelSmall, color = NeutralGray500)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Box(modifier = Modifier.size(8.dp).background(StatusSuccess, CircleShape))
                                        Text(
                                            "Open: ${openH}h",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusSuccess
                                        )
                                    }
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
                                    Icon(
                                        Icons.Default.RemoveRedEye,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
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
                                    Text(
                                        "${space.avatarInquiryClicks} Inquiries",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Action buttons â€” a Draft has no live schedule to manage yet, so
                        // its row leads with "Continue Editing" (the same wizard, pre-
                        // populated) instead of Availability/Edit.
                        if (space.status == ListingStatus.DRAFT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CustomButton(
                                    text = "Details",
                                    onClick = { onSelectSpace(space) },
                                    variant = CustomButtonVariant.OUTLINED,
                                    icon = Icons.Default.Info,
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )

                                CustomButton(
                                    text = "Continue Editing",
                                    onClick = { onContinueDraft(space) },
                                    variant = CustomButtonVariant.PRIMARY,
                                    icon = Icons.Default.EditNote,
                                    compact = true,
                                    modifier = Modifier.weight(1.5f)
                                )

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
                                // Manage Button â€” opens the real performance + availability
                                // page (ManageListingScreen) for this listing: per-division
                                // occupancy, this-month-vs-last-month yield, and a calendar-
                                // style availability table, replacing the old read-only
                                // "Details" entry point.
                                CustomButton(
                                    text = "Manage",
                                    onClick = { onManageSpace(space) },
                                    variant = CustomButtonVariant.PRIMARY,
                                    icon = Icons.Default.Dashboard,
                                    compact = true,
                                    modifier = Modifier.weight(1.6f)
                                )

                                // Edit Listing Button â€” the sole entry point into the wizard
                                // for an already-published listing now (item 1): availability
                                // (operating hours/days, blackout slots, rental formulas,
                                // subdivisions) is entirely configured here, so there's no
                                // separate "Availability" control anymore. Icon-only, matching
                                // the Pause/Resume and Delete controls beside it.
                                IconButton(
                                    onClick = { onEditSpace(space) },
                                    modifier = Modifier.weight(0.6f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Listing", tint = MaterialTheme.colorScheme.primary)
                                }

                                // Pause/Resume Button â€” the host's own lifecycle control,
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

                                // Delete Listing Button â€” weighted like the other controls
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
        if (atListingLimit) {
            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 72.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Listing limit reached")
            }
        } else {
            FloatingActionButton(
                onClick = {
                    hubHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenCreateListing()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 72.dp, end = 16.dp),
                containerColor = CarnationOrange,
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.prohost_checkmark_logo),
                        contentDescription = "Add Listing",
                        modifier = Modifier.size(26.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(14.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = CarnationOrange,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
}
}
}
