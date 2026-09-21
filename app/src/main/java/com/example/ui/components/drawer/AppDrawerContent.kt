package com.example.ui.components.drawer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.ui.components.ProHostCedarBadge
import com.example.ui.components.ProHostBrandLogo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.ui.components.ProHostBrandLogo
import com.example.ui.theme.*

// Published as ProHost's real support/data-privacy contact on the public
// privacy page (public/privacy.html) — single source of truth for the
// drawer's "Contact Support" action, rather than a second hardcoded copy
// that could drift from the published one.
private const val SUPPORT_EMAIL = "geo.elnajjar@gmail.com"

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
        // Branded drawer header: logo + user name + country market label
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
            color = OxfordBlue
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProHostBrandLogo(size = 28.dp)
                    Text(
                        text = currentUser?.fullName ?: "ProHost",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${currentUser?.country?.ifBlank { "Lebanon" } ?: "Lebanon"} Market",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolGray.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isProHost) {
            Text(
                text = "PRO HOST",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = FreshGreen,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            NavigationDrawerItem(
                label = { Text("Financials", fontWeight = FontWeight.Bold) },
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
                label = { Text("Subscriptions", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "owner_subscriptions",
                onClick = { onTabSelected("owner_subscriptions") },
                icon = { Icon(Icons.Default.Layers, contentDescription = null, tint = if (activeProHostTabId == "owner_subscriptions") FreshGreen else OxfordBlue) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Transactions", fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = { onDrawerAction("owner_whish") },
                icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = OxfordBlue) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                    selectedTextColor = OxfordBlue,
                    unselectedTextColor = CoolGray
                )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 0.5.dp,
            color = OxfordBlue.copy(alpha = 0.08f)
        )

        // "PRACTICE RESOURCES" and "CONFIGURATION & SETTINGS" used to be two
        // separate sections for what's really one kind of destination — things a
        // specialist reaches occasionally, not core daily workflow. Merged into one
        // "MORE" section (also now home to Contact Support, relocated from the
        // Profile screen) so the drawer reads as fewer, clearer groups.
        Text(
            text = "MORE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CarnationOrange,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // "Pending Requests" and "Payment Due Reminders" used to live here as their
        // own drawer shortcuts, routing to a pre-filtered view of My Bookings. My
        // Bookings' own filter chips (Pending/Accepted/etc.) already cover exactly
        // that, and it's one tap away from the bottom nav at all times — a drawer
        // shortcut to it added nothing. Removed rather than kept as a redundant
        // second path to the same screen.
        if (!isProHost) {
            NavigationDrawerItem(
                label = { Text("My Favorites", fontWeight = FontWeight.SemiBold) },
                selected = activeProHostTabId == "my_favorites",
                onClick = { onTabSelected("my_favorites") },
                icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = OxfordBlue) }
            )
        }

        if (isProHost) {
            NavigationDrawerItem(
                label = { Text("Explore Workspaces", fontWeight = FontWeight.SemiBold) },
                selected = false, // Since it's not a tab for ProHost, it acts as an action
                onClick = { onTabSelected("search_map") },
                icon = { Icon(Icons.Default.TravelExplore, contentDescription = null, tint = OxfordBlue) }
            )
        }

        NavigationDrawerItem(
            label = { Text("Legal (Privacy, Terms & Policies)", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("legal_documents") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = OxfordBlue) }
        )

        val context = LocalContext.current
        NavigationDrawerItem(
            label = { Text("Contact Support", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = {
                val subject = Uri.encode("ProHost Support — ${currentRole.name} account")
                val body = Uri.encode(
                    "Account: ${currentUser?.fullName ?: ""} (${currentUser?.email ?: ""})\n" +
                        "User ID: ${currentUser?.id ?: ""}\n\nDescribe your question or issue below:\n"
                )
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$SUPPORT_EMAIL?subject=$subject&body=$body")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No email app found — you can also reach us at $SUPPORT_EMAIL", Toast.LENGTH_LONG).show()
                }
            },
            icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = OxfordBlue) }
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
        // Branded drawer header: logo + user name + country market label
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = OxfordBlue
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProHostBrandLogo(size = 28.dp)
                    Text(
                        text = currentUser?.fullName ?: "System Admin",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${currentUser?.country?.ifBlank { "Lebanon" } ?: "Lebanon"} Market",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoolGray.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CENTRAL SECURITY CORES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            letterSpacing = 1.2.sp,
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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 0.5.dp,
            color = OxfordBlue.copy(alpha = 0.08f)
        )

        // Admin gets every Pro Host capability unconditionally — unlimited listings,
        // no package to buy (see ProHostRepository's admin bypass) — so these route
        // through the exact same screens a fully-entitled Pro Host uses.
        Text(
            text = "PRO HOST ACCESS (UNLIMITED)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OxfordBlue,
            letterSpacing = 1.2.sp,
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
            label = { Text("Explore Workspaces", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "search_map",
            onClick = { onTabSelected("search_map") },
            icon = { Icon(Icons.Default.TravelExplore, contentDescription = null, tint = if (activeTabId == "search_map") CarnationOrange else OxfordBlue) }
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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 0.5.dp,
            color = OxfordBlue.copy(alpha = 0.08f)
        )

        Text(
            text = "SYSTEM AUDIT & PRICING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OxfordBlue,
            letterSpacing = 1.2.sp,
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
        // every other destination. Debug-build-only, on top of the admin-role gate
        // this whole drawer is already behind — a live Firebase/system diagnostics
        // panel has no end-user purpose in a release build, and gating it only by
        // role would leave it reachable by any account ever promoted to ADMIN.
        if (com.example.BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Cloud & System Debugger", fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = { onDrawerAction("system_debugger") },
                icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = AmberWarning) }
            )
        }

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
