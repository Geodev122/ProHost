package com.example.ui.components.drawer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.ui.components.ProHostBrandLogo
import com.example.ui.components.ProHostCedarBadge
import com.example.ui.theme.*

// Published as ProHost's real support/data-privacy contact on the public
// privacy page (public/privacy.html) — single source of truth for the
// drawer's "Contact Support" action, rather than a second hardcoded copy
// that could drift from the published one.
private const val SUPPORT_EMAIL = "admin@pro-host.tech"

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
    activeMainTabId: String? = null,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val isProHost = currentRole == UserRole.PRO_HOST
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Modern gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(OxfordBlue, OxfordBlue.copy(blue = 0.55f))),
                    RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar circle with initials
                    val initials = (currentUser?.fullName ?: "")
                        .split(" ").take(2)
                        .joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
                        .ifBlank { "P" }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(CarnationOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = PureWhite
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentUser?.fullName ?: "ProHost User",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isProHost) FreshGreen.copy(alpha = 0.22f) else CarnationOrange.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isProHost) "PRO HOST" else "SPECIALIST",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProHost) FreshGreen else CarnationOrange,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${currentUser?.country?.ifBlank { "Lebanon" } ?: "Lebanon"} Market",
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { onDrawerAction("close") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Collapse Drawer",
                        tint = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {

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
                icon = {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = if (activeProHostTabId == "stats") FreshGreen else MaterialTheme.colorScheme.primary
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Subscriptions", fontWeight = FontWeight.Bold) },
                selected = activeProHostTabId == "owner_subscriptions",
                onClick = { onTabSelected("owner_subscriptions") },
                icon = {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (activeProHostTabId == "owner_subscriptions") FreshGreen else MaterialTheme.colorScheme.primary
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Transactions", fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = { onDrawerAction("owner_whish") },
                icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
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
                icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
        }

        if (isProHost) {
            NavigationDrawerItem(
                label = { Text("Explore Workspaces", fontWeight = FontWeight.SemiBold) },
                selected = activeMainTabId == "search_map",
                onClick = { onTabSelected("search_map") },
                icon = { Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
        }

        NavigationDrawerItem(
            label = { Text("Legal (Privacy, Terms & Policies)", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("legal_documents") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
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
            icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )

        ProHostDrawerFooter()

        // Always the last element in the drawer — see DrawerSignOutButton's own doc
        // comment for why it lives here instead of on DrawerIdentityCard.
        DrawerSignOutButton(userEmail = currentUser?.email, onSignOut = onSignOut)
        } // end inner padding Column
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
    ) {
        // Modern gradient header (Admin variant — amber/orange shield accent)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(OxfordBlue, OxfordBlue.copy(blue = 0.55f))))
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AmberWarning),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = PureWhite, modifier = Modifier.size(28.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentUser?.fullName ?: "System Admin",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = AmberWarning.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SYSTEM ADMIN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${currentUser?.country?.ifBlank { "Lebanon" } ?: "Lebanon"} Market",
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = { onDrawerAction("close") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Collapse Drawer",
                        tint = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {

        Text(
            text = "CENTRAL SECURITY CORES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("System Admin Console", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_console",
            onClick = { onTabSelected("admin_console") },
            icon = {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (activeTabId == "admin_console") CarnationOrange else MaterialTheme.colorScheme.primary
                )
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Security ID Card", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_profile",
            onClick = { onTabSelected("admin_profile") },
            icon = {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (activeTabId == "admin_profile") CarnationOrange else MaterialTheme.colorScheme.primary
                )
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )

        // Admin gets every Pro Host capability unconditionally — unlimited listings,
        // no package to buy (see ProHostRepository's admin bypass) — so these route
        // through the exact same screens a fully-entitled Pro Host uses.
        Text(
            text = "PRO HOST ACCESS (UNLIMITED)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("My Listings", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "manage_listings",
            onClick = { onTabSelected("manage_listings") },
            icon = {
                Icon(
                    Icons.Default.HomeWork,
                    contentDescription = null,
                    tint = if (activeTabId == "manage_listings") CarnationOrange else MaterialTheme.colorScheme.primary
                )
            }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Explore Workspaces", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "search_map",
            onClick = { onTabSelected("search_map") },
            icon = {
                Icon(
                    Icons.Default.TravelExplore,
                    contentDescription = null,
                    tint = if (activeTabId == "search_map") CarnationOrange else MaterialTheme.colorScheme.primary
                )
            }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Renting Progress", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "owner_progress",
            onClick = { onTabSelected("owner_progress") },
            icon = {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (activeTabId == "owner_progress") CarnationOrange else MaterialTheme.colorScheme.primary
                )
            }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Stats", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "stats",
            onClick = { onTabSelected("stats") },
            icon = {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = if (activeTabId == "stats") CarnationOrange else MaterialTheme.colorScheme.primary
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )

        Text(
            text = "SYSTEM AUDIT & PRICING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("System Audit Logs", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_audit") },
            icon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
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
        } // end inner padding Column
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
        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
                    color = MaterialTheme.colorScheme.primary
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
