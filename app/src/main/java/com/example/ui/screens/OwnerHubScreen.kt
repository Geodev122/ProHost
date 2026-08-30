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
import java.util.Locale

@Composable
fun OwnerHubScreen(
    viewModel: ProSpaceViewModel,
    onSelectSpace: (SpaceListing) -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val campaigns by viewModel.avatarCampaigns.collectAsState()
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
    var selectedSpaceForAvatar by remember { mutableStateOf<SpaceListing?>(null) }
    var selectedSpaceForSchedule by remember { mutableStateOf<SpaceListing?>(null) }
    var activeCampaignForAvatar by remember { mutableStateOf<AvatarCampaign?>(null) }
    var showCreateListingDialog by remember { mutableStateOf(false) }
    var showPackageSelectionDialog by remember { mutableStateOf(false) }

    OwnerHubScreenContent(
        ownerSpaces = ownerSpaces,
        allSpaces = spaces,
        allBookingRequests = allBookingRequests,
        monthlySubscriptionFeeUsd = pricingState.monthlySubscriptionFeeUsd,
        onSelectSpace = onSelectSpace,
        onOpenWhishRenewal = { space -> selectedSpaceForWhish = space },
        onOpenScheduleEditor = { space -> selectedSpaceForSchedule = space },
        onOpenAvatarStudio = { space ->
            val cmp = campaigns.find { it.spaceId == space.id } ?: viewModel.generateAvatarCampaignForSpace(space)
            activeCampaignForAvatar = cmp
            selectedSpaceForAvatar = space
        },
        onOpenCreateListing = { showCreateListingDialog = true },
        onOpenPackageSelection = { showPackageSelectionDialog = true }
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
                            Text("$${pricingState.package3MonthlyFeeUsd} / month • Unlimited active workspaces", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Button(
                        onClick = { showPackageSelectionDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
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
                        "Package 2 Limit Reached (3 listings max). Please upgrade to Package 3 (Unlimited) in Owner Portal.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
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
    onOpenAvatarStudio: (SpaceListing) -> Unit,
    onOpenCreateListing: () -> Unit,
    onOpenPackageSelection: () -> Unit,
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
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Space Owner Portal", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                color = WhishRed,
                                shape = RoundedCornerShape(8.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WhishRed)
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Renew", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onOpenPackageSelection,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
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

        // Quick Actions Row: Add Listing & AI Avatar Studio
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add Listing Button Card
                ProSurfaceCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenCreateListing() }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add Workspace",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Granular Listing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // AI Avatar Marketing Studio Card
                ProSurfaceCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val target = ownerSpaces.firstOrNull() ?: allSpaces.firstOrNull()
                            target?.let { onOpenAvatarStudio(it) }
                        }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = InstagramPink.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = InstagramPink, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AI Avatar Studio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Instagram & Reels",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Space", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        if (ownerSpaces.isEmpty()) {
            item {
                ProEmptyState(
                    title = "No Listings Published",
                    description = "You don't have any workspace listings yet. Click 'Add Workspace' above to publish your first office or clinic.",
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

                        // Smart Availability summary badges
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(8.dp),
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
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Availability", style = MaterialTheme.typography.labelMedium)
                            }

                            // AI Reels Marketing Button
                            Button(
                                onClick = { onOpenAvatarStudio(space) },
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InstagramPink)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Reels", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
