package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppNavTab(
    val id: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    // Professional tabs
    object SearchMap : AppNavTab("search_map", "Explore", Icons.Filled.Search, Icons.Outlined.Search)
    object ProfessionalRentals : AppNavTab("pro_rentals", "My Bookings", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong)
    object ProfessionalProfile : AppNavTab("pro_profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Owner tabs
    object ManageListings : AppNavTab("manage_listings", "My Listings", Icons.Filled.HomeWork, Icons.Outlined.HomeWork)
    object OwnerRentalRequests : AppNavTab("owner_requests", "Renting Requests", Icons.Filled.Inbox, Icons.Outlined.Inbox)
    object OwnerRentingProgress : AppNavTab("owner_progress", "Renting Progress", Icons.Filled.Schedule, Icons.Outlined.Schedule)
    object Stats : AppNavTab("stats", "Stats", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    object OwnerProfile : AppNavTab("owner_profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Super Admin tab (restricted exclusively to Super Admin role)
    object AdminConsole : AppNavTab("admin_console", "Admin Console", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    object AdminProfile : AppNavTab("admin_profile", "Security ID", Icons.Filled.Shield, Icons.Outlined.Shield)
}
