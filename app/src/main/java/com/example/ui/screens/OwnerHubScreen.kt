package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import com.example.ui.viewmodel.ProSpaceViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun OwnerHubScreen(
    viewModel: ProSpaceViewModel,
    onSelectSpace: (SpaceListing) -> Unit,
    onOpenSubscriptions: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val allBookingRequests by viewModel.bookingRequests.collectAsState()

    val ownerSpaces = remember(spaces, currentUser) {
        val user = currentUser
        if (user != null) {
            spaces.filter { it.ownerId == user.id || it.ownerEmail.equals(user.email, ignoreCase = true) || it.ownerName.contains(user.fullName, ignoreCase = true) || user.role == UserRole.ADMIN }
        } else {
            emptyList()
        }
    }

    var selectedSpaceForWhish by remember { mutableStateOf<SpaceListing?>(null) }
    var selectedSpaceForSchedule by remember { mutableStateOf<SpaceListing?>(null) }
    var showCreateListingDialog by remember { mutableStateOf(false) }
    var showPackageSelectionDialog by remember { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var deletingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    val coroutineScope = rememberCoroutineScope()

    OwnerHubScreenContent(
        ownerSpaces = ownerSpaces,
        allSpaces = spaces,
        allBookingRequests = allBookingRequests,
        monthlySubscriptionFeeUsd = pricingState.monthlySubscriptionFeeUsd,
        onSelectSpace = onSelectSpace,
        onOpenWhishRenewal = { space -> selectedSpaceForWhish = space },
        onOpenScheduleEditor = { space -> selectedSpaceForSchedule = space },
        onOpenCreateListing = { showCreateListingDialog = true },
        onOpenPackageSelection = {
            if (onOpenSubscriptions != null) {
                onOpenSubscriptions()
            } else {
                showPackageSelectionDialog = true
            }
        },
        onEditSpace = { space -> editingSpace = space },
        onDeleteSpace = { space -> deletingSpace = space }
    )

    // Package Selection Dialog
    if (showPackageSelectionDialog) {
        Dialog(onDismissRequest = { showPackageSelectionDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Owner Package Tiers & Governance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Current Package: ${currentUser?.ownerPackageTier?.title ?: "Pay As You Go"} (${pricingState.governanceTag})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedCard(
                        onClick = {
                            viewModel.payOwnerPackageViaWhish(
                                tier = OwnerPackageTier.PAY_AS_YOU_GO,
                                payerName = currentUser?.fullName ?: "Space Owner",
                                payerPhone = currentUser?.phone ?: "+961 70 888 999",
                                spaceTypeForPayg = SpaceType.PRIVATE_OFFICE,
                                context = context
                            )
                            showPackageSelectionDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(OwnerPackageTier.PAY_AS_YOU_GO.title, fontWeight = FontWeight.Bold)
                            Text(OwnerPackageTier.PAY_AS_YOU_GO.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("PAYG fee per listing type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            viewModel.payOwnerPackageViaWhish(
                                tier = OwnerPackageTier.LIMITED_3_TIER,
                                payerName = currentUser?.fullName ?: "Space Owner",
                                payerPhone = currentUser?.phone ?: "+961 70 888 999",
                                spaceTypeForPayg = null,
                                context = context
                            )
                            showPackageSelectionDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(OwnerPackageTier.LIMITED_3_TIER.title, fontWeight = FontWeight.Bold)
                            Text(OwnerPackageTier.LIMITED_3_TIER.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$${pricingState.package2MonthlyFeeUsd} / month • Up to 3 active listings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            viewModel.payOwnerPackageViaWhish(
                                tier = OwnerPackageTier.UNLIMITED_TIER,
                                payerName = currentUser?.fullName ?: "Space Owner",
                                payerPhone = currentUser?.phone ?: "+961 70 888 999",
                                spaceTypeForPayg = null,
                                context = context
                            )
                            showPackageSelectionDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(OwnerPackageTier.UNLIMITED_TIER.title, fontWeight = FontWeight.Bold)
                            Text(OwnerPackageTier.UNLIMITED_TIER.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$${pricingState.package3MonthlyFeeUsd} / month • Unlimited active listings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Button(
                        onClick = { showPackageSelectionDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Whish Settlement Dialog for Renewal
    selectedSpaceForWhish?.let { space ->
        WhishPayModal(
            space = space,
            currentFeeUsd = pricingState.monthlySubscriptionFeeUsd,
            viewModel = viewModel,
            onDismiss = { selectedSpaceForWhish = null },
            onConfirmPayment = { _, _ ->
                // WhishPayModal's own "Authorize Settlement" button already calls
                // viewModel.paySubscriptionViaWhish and dismisses itself — calling it again
                // here used to double-fire the payment (two initiateWhishPayment calls, two
                // browser launches, two polling loops for one tap). This callback only needs
                // to clear local state now. Settlement isn't confirmed yet at this point —
                // the app is still opening Whish's checkout page — so no success toast here;
                // the Firestore listener reflects the real outcome once Whish confirms it.
                selectedSpaceForWhish = null
            }
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

    // Create Granular Space Listing Dialog
    if (showCreateListingDialog) {
        CreateListingDialog(
            currentUser = currentUser,
            onDismiss = { showCreateListingDialog = false },
            onListingCreated = { newListing ->
                val success = viewModel.createNewSpaceListing(newListing)
                if (success) {
                    showCreateListingDialog = false
                    android.widget.Toast.makeText(context, "Workspace listing published successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Package Limit Reached. Please upgrade your package tier in Owner Portal.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // Edit Listing Dialog — an owner previously had no way to correct a mistake in
    // their own published listing; updateSpaceListing was only ever called from the
    // Admin Console, even though Firestore rules already let the owning user update
    // their own workspace_listings document directly.
    editingSpace?.let { space ->
        OwnerEditListingDialog(
            listing = space,
            onDismiss = { editingSpace = null },
            onSave = { updated ->
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
private fun OwnerEditListingDialog(
    listing: SpaceListing,
    onDismiss: () -> Unit,
    onSave: (SpaceListing) -> Unit
) {
    var title by remember { mutableStateOf(listing.title) }
    var district by remember { mutableStateOf(listing.district) }
    var streetAddress by remember { mutableStateOf(listing.streetAddress) }
    var floorInfo by remember { mutableStateOf(listing.floorInfo) }
    var priceText by remember { mutableStateOf(listing.baseMonthlyRateUsd.toInt().toString()) }
    var ownerPhone by remember { mutableStateOf(listing.ownerPhone) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Edit Workspace Listing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("District") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = streetAddress, onValueChange = { streetAddress = it }, label = { Text("Street Address") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = floorInfo, onValueChange = { floorInfo = it }, label = { Text("Floor Info") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Monthly Rate (USD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = ownerPhone, onValueChange = { ownerPhone = it }, label = { Text("Contact Phone") }, modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(
                                listing.copy(
                                    title = title,
                                    district = district,
                                    streetAddress = streetAddress,
                                    floorInfo = floorInfo,
                                    baseMonthlyRateUsd = priceText.toDoubleOrNull() ?: listing.baseMonthlyRateUsd,
                                    ownerPhone = ownerPhone
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
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
    allSpaces: List<SpaceListing>,
    allBookingRequests: List<BookingRequest>,
    monthlySubscriptionFeeUsd: Double,
    onSelectSpace: (SpaceListing) -> Unit,
    onOpenWhishRenewal: (SpaceListing?) -> Unit,
    onOpenScheduleEditor: (SpaceListing) -> Unit,
    onOpenCreateListing: () -> Unit,
    onOpenPackageSelection: () -> Unit,
    onEditSpace: (SpaceListing) -> Unit = {},
    onDeleteSpace: (SpaceListing) -> Unit = {},
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
                    .shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text("Space Owner Portal", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                color = WhishRed,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", monthlySubscriptionFeeUsd)} /mo",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "30-Day Listing Entitlement",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Manage smart availability, blackout offline hours, and keep your space active across Lebanon with Whish Pay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xCCFFFFFF),
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val target = ownerSpaces.firstOrNull() ?: allSpaces.firstOrNull()
                                    onOpenWhishRenewal(target)
                                },
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

        // Highlighted & Centered Add Workspace Action Button Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, MaterialTheme.shapes.large)
                        .clickable { onOpenCreateListing() },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.lg))
                        Column {
                            Text(
                                text = "Add New Workspace Listing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Publish clinic, office, or studio space with smart pricing formulas",
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
                title = "My Workspace Listings (${ownerSpaces.size})",
                subtitle = "Manage facilities, pricing formulas, and availability schedules",
                icon = Icons.Default.HomeWork,
                trailingContent = {
                    TextButton(onClick = onOpenCreateListing) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("New Space", style = MaterialTheme.typography.labelMedium)
                    }
                }
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

                        // Smart Availability summary badges
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🗓️ ${space.schedule.openingHour}-${space.schedule.closingHour}", style = MaterialTheme.typography.labelSmall)
                                Text("🔒 Rented: ${rentedH}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("🚫 Closed: ${blackoutH}h", style = MaterialTheme.typography.labelSmall, color = NeutralGray500)
                                Text("🟢 Open: ${openH}h", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = StatusSuccess)
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
                                    modifier = Modifier.padding(8.dp),
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
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp), tint = WhatsAppGreen)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${space.avatarInquiryClicks} Inquiries", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // View Specs Button
                            OutlinedButton(
                                onClick = { onSelectSpace(space) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Details", style = MaterialTheme.typography.labelMedium)
                            }

                            // Availability & Schedule Control Button
                            Button(
                                onClick = { onOpenScheduleEditor(space) },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Availability", style = MaterialTheme.typography.labelMedium)
                            }

                            // Edit Listing Button
                            OutlinedButton(
                                onClick = { onEditSpace(space) },
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Listing", modifier = Modifier.size(14.dp))
                            }

                            // Delete Listing Button
                            IconButton(onClick = { onDeleteSpace(space) }) {
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
