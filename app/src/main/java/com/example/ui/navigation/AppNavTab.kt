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
    // Unified bottom-nav tabs — identical set for SPECIALIST and PRO_HOST (a Pro
    // Host is still a Specialist underneath; only the additional Pro Host section
    // in the drawer differs between the two roles). See allowedTabIdsForRole /
    // roleTabs in ProHostNavGraph.kt.
    object SearchMap : AppNavTab("search_map", "Explore", Icons.Filled.Search, Icons.Outlined.Search)
    object ProfessionalRentals : AppNavTab("pro_rentals", "My Rentals", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong)
    object ProfessionalProfile : AppNavTab("pro_profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    // Pro Host destinations — reachable only from the drawer's "Pro Host" section,
    // never from the bottom nav. Each opens full-screen (see
    // ProHostAppRoot's fullScreenProHostTab) with no bottom bar. OwnerSubscriptions
    // doubles as the "Become a Pro Host" package-purchase destination for a
    // SPECIALIST who hasn't been promoted yet.
    object ManageListings : AppNavTab("manage_listings", "My Listings", Icons.Filled.HomeWork, Icons.Outlined.HomeWork)
    object OwnerRentalRequests : AppNavTab("owner_requests", "Renting Requests", Icons.Filled.Inbox, Icons.Outlined.Inbox)
    object OwnerRentingProgress : AppNavTab("owner_progress", "Renting Progress", Icons.Filled.Schedule, Icons.Outlined.Schedule)
    object Stats : AppNavTab("stats", "Financials", Icons.Filled.Analytics, Icons.Outlined.Analytics)
    object OwnerSubscriptions : AppNavTab("owner_subscriptions", "ProHost Premium", Icons.Filled.Layers, Icons.Outlined.Layers)

    // Shared between SPECIALIST and PRO_HOST — a Pro Host is still fundamentally a
    // Specialist underneath and browses/saves spaces the same way. Drawer-only
    // (not a bottom-nav tab), rendered full-screen like the Pro Host destinations
    // above — see PRO_HOST_FULLSCREEN_TABS's own doc comment in ProHostNavGraph.kt.
    object MyFavorites : AppNavTab("my_favorites", "My Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)

    // Admin's own side-menu destinations — reachable only via the drawer (no bottom
    // nav for Admin at all), rendered full-screen the same way Pro Host's are.
    // "Admin Console" is the single entry point; its own inner tabs (including the
    // Revenue & Run-Rate / Transactions ones — see AdminConsoleScreen) are its
    // sub-tabs, not separate peer destinations like this used to have.
    object AdminConsole : AppNavTab("admin_console", "Admin Console", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    object AdminProfile : AppNavTab("admin_profile", "Security ID", Icons.Filled.Shield, Icons.Outlined.Shield)
}
