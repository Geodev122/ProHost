package com.example.ui.components.drawer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.model.publicCode
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
 *      (My Bookings/Profile). The one deliberate exception is Explore: it is
 *      pinned, highlighted, at the top of every role's drawer
 *      (DrawerExploreHighlight) as the app's primary destination.
 *   2. Never keep a drawer item that's just a pre-filtered view of a screen
 *      already reachable another way — "Pending Requests"/"Payment Due
 *      Reminders" used to route here to a filtered version of My Bookings;
 *      that's what My Bookings' own filter chips are for.
 *   3. Every remaining item represents a genuinely distinct workflow or a
 *      piece of content that lives nowhere else (Subscription & Packages,
 *      Stats, Billing, the two static-content bulletins,
 *      Legal). "PRO HOST" is the only section whose contents differ by
 *      role — a single "Become a Pro Host" CTA for a SPECIALIST (opens
 *      package purchasing full-screen), or the real Pro Host destination
 *      list for a PRO_HOST (each also opens full-screen — see
 *      PRO_HOST_FULLSCREEN_TABS in ProHostNavGraph.kt). Host-only resource
 *      links (billing, guidelines) only show once actually
 *      promoted to PRO_HOST; everything else here is genuinely cross-role
 *      (rent-law reference content, app updates, legal documents), so it's
 *      shown to both rather than hidden for one and not the other.
 */
/** Pinned, highlighted entry to Explore at the top of every role's drawer. */
@Composable
private fun DrawerExploreHighlight(isActive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = if (isActive) BorderStroke(2.dp, CarnationOrange) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(VibrantBlueDark, VibrantBlue)))
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.TravelExplore, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Explore Workspaces", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Browse and book verified spaces", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

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
                    Brush.linearGradient(listOf(CoolGrayDark, OxfordBlue, VibrantBlueDark)),
                    RoundedCornerShape(bottomEnd = 24.dp)
                )
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl)
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
                            .background(CarnationOrange)
                            .border(2.dp, PureWhite.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = PureWhite
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentUser?.fullName ?: "ProHost User",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isProHost) CarnationOrange else VibrantBlue,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (isProHost) "PRO HOST" else "SPECIALIST",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        val planName = currentUser?.ownerPackageId
                            ?.let { packagePlans.packages[it]?.name }
                        Text(
                            text = if (planName != null) "Plan: $planName" else "${currentUser?.country?.ifBlank { "Lebanon" } ?: "Lebanon"} Market",
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

        Spacer(modifier = Modifier.height(Spacing.xl))

        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {

        DrawerExploreHighlight(
            isActive = activeProHostTabId == null && activeMainTabId == "search_map",
            onClick = { onTabSelected("search_map") }
        )
        Spacer(modifier = Modifier.height(Spacing.xl))

        if (isProHost) {
            Text(
                text = "PRO HOST",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CarnationOrange,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm)
            )

            NavigationDrawerItem(
                shape = MaterialTheme.shapes.medium,
                label = { Text("Renting Requests", fontWeight = FontWeight.SemiBold) },
                selected = activeProHostTabId == "owner_requests",
                onClick = { onTabSelected("owner_requests") },
                icon = {
                    DrawerBadgedIcon(
                        icon = Icons.Default.Inbox,
                        tint = if (activeProHostTabId == "owner_requests") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        showDot = pendingRequestsCount > 0
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            NavigationDrawerItem(

                shape = MaterialTheme.shapes.medium,
                label = { Text("Financials", fontWeight = FontWeight.SemiBold) },
                selected = activeProHostTabId == "stats",
                onClick = { onTabSelected("stats") },
                icon = {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = if (activeProHostTabId == "stats") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                shape = MaterialTheme.shapes.medium,
                label = { Text("Subscriptions", fontWeight = FontWeight.SemiBold) },
                selected = activeProHostTabId == "owner_subscriptions",
                onClick = { onTabSelected("owner_subscriptions") },
                icon = {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = if (activeProHostTabId == "owner_subscriptions") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                shape = MaterialTheme.shapes.medium,
                label = { Text("Billing", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = { onDrawerAction("owner_billing") },
                icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm)
        )

        // "Pending Requests" and "Payment Due Reminders" used to live here as their
        // own drawer shortcuts, routing to a pre-filtered view of My Bookings. My
        // Bookings' own filter chips (Pending/Accepted/etc.) already cover exactly
        // that, and it's one tap away from the bottom nav at all times — a drawer
        // shortcut to it added nothing. Removed rather than kept as a redundant
        // second path to the same screen.
        if (!isProHost) {
            NavigationDrawerItem(
                shape = MaterialTheme.shapes.medium,
                label = { Text("My Favorites", fontWeight = FontWeight.SemiBold) },
                selected = activeProHostTabId == "my_favorites",
                onClick = { onTabSelected("my_favorites") },
                icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }

        NavigationDrawerItem(

            shape = MaterialTheme.shapes.medium,
            label = { Text("Legal (Privacy, Terms & Policies)", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("legal_documents") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        )

        val context = LocalContext.current
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.medium,
            label = { Text("Contact Support", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = {
                val subject = Uri.encode("ProHost Support — ${currentRole.name} account")
                val body = Uri.encode(
                    "Account: ${currentUser?.fullName ?: ""} (${currentUser?.email ?: ""})\n" +
                        "Account code: ${currentUser?.publicCode ?: ""}\n\nDescribe your question or issue below:\n"
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
            icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                .background(
                    Brush.linearGradient(listOf(CoolGrayDark, OxfordBlue, VibrantBlueDark)),
                    RoundedCornerShape(bottomEnd = 24.dp)
                )
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl)
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
                        shape = CircleShape
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

        Spacer(modifier = Modifier.height(Spacing.xl))

        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {

        DrawerExploreHighlight(
            isActive = activeTabId == "search_map",
            onClick = { onTabSelected("search_map") }
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = "CENTRAL SECURITY CORES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm)
        )

        NavigationDrawerItem(

            shape = MaterialTheme.shapes.medium,
            label = { Text("System Admin Console", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "admin_console",
            onClick = { onTabSelected("admin_console") },
            icon = {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = if (activeTabId == "admin_console") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.medium,
            label = { Text("Security ID Card", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "admin_profile",
            onClick = { onTabSelected("admin_profile") },
            icon = {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (activeTabId == "admin_profile") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
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
            modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm)
        )

        NavigationDrawerItem(

            shape = MaterialTheme.shapes.medium,
            label = { Text("My Listings", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "manage_listings",
            onClick = { onTabSelected("manage_listings") },
            icon = {
                Icon(
                    Icons.Default.HomeWork,
                    contentDescription = null,
                    tint = if (activeTabId == "manage_listings") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.medium,
            label = { Text("Renting Progress", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "owner_progress",
            onClick = { onTabSelected("owner_progress") },
            icon = {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (activeTabId == "owner_progress") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.medium,
            label = { Text("Analytics", fontWeight = FontWeight.SemiBold) },
            selected = activeTabId == "stats",
            onClick = { onTabSelected("stats") },
            icon = {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = if (activeTabId == "stats") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Spacing.lg),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = "SYSTEM AUDIT & PRICING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.sm)
        )

        NavigationDrawerItem(

            shape = MaterialTheme.shapes.medium,
            label = { Text("System Audit Logs", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_audit") },
            icon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.medium,
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
                shape = MaterialTheme.shapes.medium,
                label = { Text("Cloud & System Debugger", fontWeight = FontWeight.SemiBold) },
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                ProHostCedarBadge(text = "v${com.example.BuildConfig.VERSION_NAME}", isCompact = true)
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

    HorizontalDivider(modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)

    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(vertical = Spacing.md)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text("Sign Out", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Sign Out of ProHost", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to sign out of your account${userEmail?.let { " ($it)" } ?: ""}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(shape = MaterialTheme.shapes.medium, onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
