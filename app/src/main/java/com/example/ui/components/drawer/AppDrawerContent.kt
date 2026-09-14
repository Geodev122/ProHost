package com.example.ui.components.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.ui.components.ProHostBrandLogo
import com.example.ui.components.ProHostCedarBadge
import com.example.ui.theme.*

/**
 * The unified drawer for both SPECIALIST and PRO_HOST.
 *
 * Design policy for what belongs here (kept deliberately lean — a PRO_HOST
 * account is still a Specialist underneath, §1 of the workflow doc, so without
 * this discipline the drawer would show both roles' full item sets at once):
 *   1. Never duplicate a destination the bottom nav already shows at all times
 *      — the three bottom-nav tabs (Explore/My Bookings/Profile) are NOT
 *      repeated here; there used to be a "PRIMARY CORES" section that did
 *      exactly that, adding a second, always-visible way to reach a
 *      screen that's one tap away regardless of whether the drawer is open.
 *   2. Never keep a drawer item that's just a pre-filtered view of a screen
 *      already reachable another way — "Pending Requests"/"Payment Due
 *      Reminders" used to route here to a filtered version of My Bookings;
 *      that's what My Bookings' own filter chips are for.
 *   3. Every remaining item represents a genuinely distinct workflow or a
 *      piece of content that lives nowhere else (Subscription & Packages,
 *      Stats, Whish Money Transactions, the two static-content bulletins,
 *      Legal). "PRO HOST" is the only section whose contents differ by
 *      role — a single "Become a Pro Host" CTA for a SPECIALIST (opens
 *      package purchasing full-screen), or the real Pro Host destination
 *      list for a PRO_HOST (each also opens full-screen — see
 *      PRO_HOST_FULLSCREEN_TABS in ProHostNavGraph.kt). Host-only resource
 *      links (Whish transactions, guidelines) only show once actually
 *      promoted to PRO_HOST; everything else here is genuinely cross-role
 *      (rent-law reference content, app updates, legal documents), so it's
 *      shown to both rather than hidden for one and not the other.
 */
/** A drawer nav-item icon with a small red dot in the corner when [showDot] is
 * true — the "new pending request" indicator on "Renting Requests", alongside
 * the existing push notification for the same event. */
@Composable
private fun DrawerBadgedIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, showDot: Boolean) {
    BadgedBox(
        badge = {
            if (showDot) {
                Badge(containerColor = CrimsonRed)
            }
        }
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
fun SpecialistDrawerContent(
    currentUser: AppUser?,
    packagePlans: com.example.data.model.PackagePlanCatalog = com.example.data.model.PackagePlanCatalog(),
    currentRole: UserRole,
    // Count of PENDING booking requests against this host's own listings — drives
    // the red dot on "Renting Requests" below, alongside the existing push
    // notification, so a new request is visible at a glance without opening the tab.
    pendingRequestsCount: Int = 0,
    activeProHostTabId: String?,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val isProHost = currentRole == UserRole.PRO_HOST
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg)
    ) {
        DrawerIdentityCard(
            user = currentUser,
            currentPackage = currentUser?.ownerPackageId?.let { packagePlans.packages[it] },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "PRO HOST",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = FreshGreen,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        if (isProHost) {
            NavigationDrawerItem(
                label = { Text("My Workspace Listings", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "manage_listings",
                onClick = { onTabSelected("manage_listings") },
                icon = { Icon(Icons.Default.HomeWork, contentDescription = null, tint = if (activeProHostTabId == "manage_listings") FreshGreen else OxfordBlue) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Renting Requests", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "owner_requests",
                onClick = { onTabSelected("owner_requests") },
                icon = {
                    DrawerBadgedIcon(
                        icon = Icons.Default.Inbox,
                        tint = if (activeProHostTabId == "owner_requests") FreshGreen else OxfordBlue,
                        showDot = pendingRequestsCount > 0
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Renting Progress", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "owner_progress",
                onClick = { onTabSelected("owner_progress") },
                icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = if (activeProHostTabId == "owner_progress") FreshGreen else OxfordBlue) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Financial Stats & Yields", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "stats",
                onClick = { onTabSelected("stats") },
                icon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = if (activeProHostTabId == "stats") FreshGreen else OxfordBlue) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Subscription & Packages", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "owner_subscriptions",
                onClick = { onTabSelected("owner_subscriptions") },
                icon = { Icon(Icons.Default.Layers, contentDescription = null, tint = if (activeProHostTabId == "owner_subscriptions") FreshGreen else OxfordBlue) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
        } else {
            Surface(
                onClick = { onTabSelected("owner_subscriptions") },
                shape = MaterialTheme.shapes.medium,
                color = FreshGreen,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = PureWhite)
                    Text(
                        text = "Become a Pro Host",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), color = LightGray)

        Text(
            text = "PRACTICE RESOURCES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CarnationOrange,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // "Pending Requests" and "Payment Due Reminders" used to live here as their
        // own drawer shortcuts, routing to a pre-filtered view of My Bookings. My
        // Bookings' own filter chips (Pending/Accepted/etc.) already cover exactly
        // that, and it's one tap away from the bottom nav at all times — a drawer
        // shortcut to it added nothing. Removed rather than kept as a redundant
        // second path to the same screen.
        NavigationDrawerItem(
            label = { Text("My Favorites", fontWeight = FontWeight.SemiBold) },
            selected = activeProHostTabId == "my_favorites",
            onClick = { onTabSelected("my_favorites") },
            icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = OxfordBlue) }
        )

        if (isProHost) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Explore Workspaces", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = { onTabSelected("search_map") },
                icon = { Icon(Icons.Default.TravelExplore, contentDescription = null, tint = OxfordBlue) }
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("My Bookings", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = { onTabSelected("pro_rentals") },
                icon = { Icon(Icons.Default.EventAvailable, contentDescription = null, tint = OxfordBlue) }
            )
        }

        if (isProHost) {
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), color = LightGray)

            Text(
                text = "HOST FINANCE & RESOURCES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = FreshGreen,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            NavigationDrawerItem(
                label = { Text("Whish Money Transactions", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = { onDrawerAction("owner_whish") },
                icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = OxfordBlue) }
            )
            // "Owner Package Tiers & Governance" used to duplicate this same section's
            // own "Subscription & Packages" item above (same onTabSelected("owner_subscriptions")
            // destination) with a second, stale copy of the tier pricing — hardcoded
            // "$49/mo"/"$120/mo" figures that didn't even track the real admin-configurable
            // pricing the actual Subscription & Packages screen shows. Removed outright.
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), color = LightGray)

        Text(
            text = "CONFIGURATION & SETTINGS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CarnationOrange,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("Legal (Privacy, Terms & Policies)", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("legal_documents") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = OxfordBlue) }
        )

        ProHostDrawerFooter()

        // Always the last element in the drawer — see DrawerSignOutButton's own doc
        // comment for why it lives here instead of on DrawerIdentityCard.
        DrawerSignOutButton(userEmail = currentUser?.email, onSignOut = onSignOut)
    }
}

@Composable
fun AdminDrawerContent(
    currentUser: AppUser?,
    // Same badge signal as SpecialistDrawerContent's — Admin sees every listing's
    // requests (its own "owner_requests" reads all, not just Admin-owned ones).
    pendingRequestsCount: Int = 0,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg)
    ) {
        DrawerIdentityCard(
            user = currentUser,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "CENTRAL SECURITY CORES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("System Admin Console", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_console",
            onClick = { onTabSelected("admin_console") },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = if (activeTabId == "admin_console") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Security ID Card", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_profile",
            onClick = { onTabSelected("admin_profile") },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = if (activeTabId == "admin_profile") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), color = LightGray)

        // Admin gets every Pro Host capability unconditionally — unlimited listings,
        // no package to buy (see ProHostRepository's admin bypass) — so these route
        // through the exact same screens a fully-entitled Pro Host uses.
        Text(
            text = "PRO HOST ACCESS (UNLIMITED)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OxfordBlue,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("My Listings", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "manage_listings",
            onClick = { onTabSelected("manage_listings") },
            icon = { Icon(Icons.Default.HomeWork, contentDescription = null, tint = if (activeTabId == "manage_listings") CarnationOrange else OxfordBlue) }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Renting Requests", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "owner_requests",
            onClick = { onTabSelected("owner_requests") },
            icon = {
                DrawerBadgedIcon(
                    icon = Icons.Default.Inbox,
                    tint = if (activeTabId == "owner_requests") CarnationOrange else OxfordBlue,
                    showDot = pendingRequestsCount > 0
                )
            }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Renting Progress", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "owner_progress",
            onClick = { onTabSelected("owner_progress") },
            icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = if (activeTabId == "owner_progress") CarnationOrange else OxfordBlue) }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Stats", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "stats",
            onClick = { onTabSelected("stats") },
            icon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = if (activeTabId == "stats") CarnationOrange else OxfordBlue) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.lg), color = LightGray)

        Text(
            text = "SYSTEM AUDIT & PRICING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OxfordBlue,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("System Audit Logs", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_audit") },
            icon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = OxfordBlue) }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Governorate Nodes Status", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_gov") },
            icon = { Icon(Icons.Default.Dns, contentDescription = null, tint = VibrantBlue) }
        )

        // System Debugger stays at the very bottom of the admin side menu, below
        // every other destination.
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Firebase & System Debugger", fontWeight = FontWeight.Bold) },
            selected = false,
            onClick = { onDrawerAction("system_debugger") },
            icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = AmberWarning) }
        )

        ProHostDrawerFooter()

        // Always the last element in the drawer — see DrawerSignOutButton's own doc
        // comment for why it lives here instead of on DrawerIdentityCard.
        DrawerSignOutButton(userEmail = currentUser?.email, onSignOut = onSignOut)
    }
}

@Composable
fun ProHostDrawerFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = LightGray)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProHostBrandLogo(size = 28.dp)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ProHost Lebanon",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OxfordBlue
                    )
                    ProHostCedarBadge(text = "v2.5", isCompact = true)
                }
                Text(
                    text = "Verified Specialist Workspace Grid",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = CoolGray
                )
            }
        }
    }
}

/**
 * The drawer's Sign Out action — deliberately the very last element of both
 * SpecialistDrawerContent and AdminDrawerContent, below ProHostDrawerFooter, rather
 * than a button on DrawerIdentityCard at the top. A destructive, account-wide action
 * sitting right next to the role pill read as too easy to hit by accident while
 * reaching for something else in the identity card; putting it alone at the bottom,
 * full-width and outlined in the destructive color, both matches where a "leave this
 * screen" action usually lives in a drawer and keeps it deliberate to reach.
 */
@Composable
fun DrawerSignOutButton(userEmail: String?, onSignOut: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 16.dp), color = LightGray)

    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text("Sign Out", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Sign Out of ProHost", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out of your account${userEmail?.let { " ($it)" } ?: ""}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
