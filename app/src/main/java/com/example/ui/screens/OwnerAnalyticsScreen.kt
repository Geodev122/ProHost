package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SpaceListing
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel

@Composable
fun OwnerAnalyticsScreen(
    viewModel: ProHostViewModel
) {
    val ownerSpaces by viewModel.ownerSpaces.collectAsState()
    val bookingRequests by viewModel.bookingRequests.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val pricingState by viewModel.pricingState.collectAsState()

    // Used to silently fall back to up to 3 arbitrary OTHER owners' listings when this
    // owner had none of their own — a real data leak (their pricing/occupancy shown
    // under a "Workspace Analytics" header with no indication it wasn't the viewer's
    // own data) as well as misleading UX. An owner with no listings now sees a real
    // empty state instead (below).
    val displayListings = ownerSpaces
    val totalRevenuePotential = displayListings.sumOf { it.baseMonthlyRateUsd }
    val totalInquiries = displayListings.sumOf { it.avatarInquiryClicks }

    val ownerSpaceIds = remember(displayListings) { displayListings.map { it.id }.toSet() }
    val specialtyDemand = remember(bookingRequests, ownerSpaceIds) {
        bookingRequests
            .filter { it.spaceId in ownerSpaceIds }
            .groupingBy { it.practitionerSpecialty.ifBlank { "Unspecified" } }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    if (displayListings.isEmpty()) {
        ProEmptyState(
            title = "No listings yet",
            description = "Publish a workspace listing to start seeing real analytics here.",
            icon = Icons.Default.Analytics,
            modifier = Modifier.fillMaxSize().testTag("owner_analytics_screen")
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient)
            .testTag("owner_analytics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProSectionHeader(
                        title = "Workspace Analytics & Revenue",
                        subtitle = "Performance Telemetry • ${user?.fullName ?: "Workspace Host"}",
                        icon = Icons.Default.Analytics,
                        trailingContent = {
                            ProStatusBadge(type = ProBadgeType.CUSTOM_INFO, customText = "Live Telemetry")
                        }
                    )

                    // 4 Quick Metric Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProMetricTile(
                            title = "Listings",
                            value = "${displayListings.size}",
                            subtitle = "Active spaces",
                            icon = Icons.Default.HomeWork,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Inquiries",
                            value = "$totalInquiries",
                            subtitle = "WhatsApp Leads",
                            icon = Icons.AutoMirrored.Filled.Chat,
                            iconTint = WhatsAppGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProMetricTile(
                            title = "Est. MRR",
                            value = "$${totalRevenuePotential.toInt()}",
                            subtitle = "Monthly Potential",
                            icon = Icons.Default.AttachMoney,
                            iconTint = StatusSuccess,
                            modifier = Modifier.weight(1f)
                        )
                        ProMetricTile(
                            title = "Whish Fee",
                            value = "$${pricingState.monthlySubscriptionFeeUsd}",
                            subtitle = "Per Space / Month",
                            icon = Icons.Default.Payment,
                            iconTint = WhishRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Listing Occupancy & Inquiry Breakdown — computed from real booking requests
        // against this owner's own listings (previously five hardcoded percentages
        // that never changed, badged "Live Telemetry" as if real).
        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProSectionHeader(
                        title = "Specialist Demand by Discipline",
                        subtitle = "Based on booking requests received for your listings",
                        icon = Icons.Default.LocalHospital
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    if (specialtyDemand.isEmpty()) {
                        Text(
                            text = "No booking requests yet — demand by discipline will appear here once practitioners start booking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val demandColors = listOf(OxfordBlue, VibrantBlue, CarnationOrange, BrightOrange, LebaneseCedarGreen)
                        val maxCount = specialtyDemand.first().second
                        specialtyDemand.forEachIndexed { index, (specialty, count) ->
                            DisciplineDemandBar(
                                name = specialty,
                                percentage = if (maxCount > 0) (count * 100 / maxCount) else 0,
                                color = demandColors[index % demandColors.size]
                            )
                        }
                    }
                }
            }
        }

        // Active Listings Health & Whish Status
        item {
            ProSectionHeader(
                title = "Listing Entitlement Health",
                subtitle = "Whish subscription status and syndication readiness",
                icon = Icons.Default.Shield
            )
        }

        items(displayListings, key = { it.id }) { space ->
            ListingHealthCard(space = space)
        }
    }
}

@Composable
fun DisciplineDemandBar(name: String, percentage: Int, color: Color) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$percentage% demand",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ListingHealthCard(space: SpaceListing) {
    ProSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (space.isActiveSubscription) StatusSuccessContainer else StatusErrorContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (space.isActiveSubscription) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (space.isActiveSubscription) StatusSuccess else StatusError,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.md))

                Column {
                    Text(
                        text = space.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "📍 ${space.district} • $${space.baseMonthlyRateUsd.toInt()}/mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (space.isActiveSubscription) "✅ Whish Subscription Active" else "⚠️ Needs Whish Renewal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (space.isActiveSubscription) StatusSuccess else StatusError
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = space.spaceType.displayName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
