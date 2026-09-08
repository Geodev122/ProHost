package com.example.ui.components.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
@Composable
fun SpecialistDrawerContent(
    currentUser: AppUser?,
    currentRole: UserRole,
    activeProHostTabId: String?,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit
) {
    val isProHost = currentRole == UserRole.PRO_HOST
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header — adapts to whichever role is currently active
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.large,
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = if (isProHost) FreshGreen else CarnationOrange,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser?.fullName?.take(1) ?: if (isProHost) "H" else "P",
                            color = PureWhite,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = currentUser?.fullName ?: if (isProHost) "Workspace Host" else "Practitioner Member",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PureWhite
                )
                Text(
                    text = currentUser?.specialty ?: if (isProHost) "Commercial Host Node" else "Licensed Specialist",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Surface(
                    color = CoolGray.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isProHost) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = BrightOrange, modifier = Modifier.size(13.dp))
                            Text(
                                text = "Premium Host Node",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        } else {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(13.dp))
                            Text(
                                text = if (currentUser?.isVerified == true) "Phone Verified" else "Phone Unverified",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                    }
                }
            }
        }

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
                icon = { Icon(Icons.Default.Inbox, contentDescription = null, tint = if (activeProHostTabId == "owner_requests") FreshGreen else OxfordBlue) },
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
            NavigationDrawerItem(
                label = { Text("Become a Pro Host", fontWeight = FontWeight.Bold) },
                selected = false,
                onClick = { onTabSelected("owner_subscriptions") },
                icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = FreshGreen) },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = FreshGreen.copy(alpha = 0.10f),
                    unselectedTextColor = OxfordBlue
                )
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

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
            label = { Text("Lebanese Rent Laws", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("pro_laws") },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = OxfordBlue) }
        )

        if (isProHost) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

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
            Spacer(modifier = Modifier.height(Spacing.xs))
            NavigationDrawerItem(
                label = { Text("Practice Guidelines", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = { onDrawerAction("owner_guidelines") },
                icon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = OxfordBlue) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

        Text(
            text = "CONFIGURATION & SETTINGS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CarnationOrange,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("App Version & Updates", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("pro_app_updates") },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = VibrantBlue) }
        )

        NavigationDrawerItem(
            label = { Text("Legal (Privacy, Terms & Policies)", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("legal_documents") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = OxfordBlue) }
        )

        ProHostDrawerFooter()
    }
}

@Composable
fun AdminDrawerContent(
    currentUser: AppUser?,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Admin Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.large,
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = VibrantBlue,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser?.fullName?.take(1) ?: "A",
                            color = PureWhite,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = currentUser?.fullName ?: "Admin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PureWhite
                )
                Text(
                    text = "Platform Super Admin Node",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Surface(
                    color = CoolGray.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = BrightOrange, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Admin Access: Root Node",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }
        }

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
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Package Revenue & Performance", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_revenue",
            onClick = { onTabSelected("admin_revenue") },
            icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = if (activeTabId == "admin_revenue") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

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
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Firebase & System Debugger", fontWeight = FontWeight.Bold) },
            selected = false,
            onClick = { onDrawerAction("system_debugger") },
            icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = AmberWarning) }
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        NavigationDrawerItem(
            label = { Text("Legal (Privacy, Terms & Policies)", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("legal_documents") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = OxfordBlue) }
        )

        ProHostDrawerFooter()
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
            ProHostBrandLogo(
                size = 28.dp,
                roundedCorner = 7.dp,
                elevation = 1.dp,
                showBorder = true
            )
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

@Composable
fun LawBulletinCard(number: String, title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightGray.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, LightGrayCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = OxfordBlue,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(number, color = PureWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = OxfordBlue)
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(content, style = MaterialTheme.typography.bodySmall, color = CoolGray)
        }
    }
}
