package com.example.ui.components.drawer

import com.example.ui.util.localizedLabel
import com.example.ui.components.rememberCountryDisplayName
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
import com.example.ui.components.Button
import com.example.ui.components.ProHostButtonDefaults
import com.example.ui.components.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.ui.components.ProHostBrandLogo
import com.example.ui.components.ProHostDialog
import com.example.ui.components.ProHostDrawerAvatar
import com.example.ui.components.ProHostDrawerDivider
import com.example.ui.components.ProHostDrawerHeader
import com.example.ui.components.ProHostDrawerHighlight
import com.example.ui.components.ProHostDrawerItem
import com.example.ui.components.ProHostDrawerSectionLabel
import com.example.ui.components.ProHostRolePill
import com.example.ui.components.ProHostCedarBadge
import com.example.ui.theme.*
import com.example.shared.resources.Res
import com.example.shared.resources.become_a_pro_host
import com.example.shared.resources.browse_and_book_verified_spaces
import com.example.shared.resources.cancel
import com.example.shared.resources.contact_support
import com.example.shared.resources.explore_workspaces
import com.example.shared.resources.financials
import com.example.shared.resources.legal_privacy_terms_policies
import com.example.shared.resources.more
import com.example.shared.resources.plan_x
import com.example.shared.resources.pro_host
import com.example.shared.resources.prohost_lebanon
import com.example.shared.resources.prohost_premium
import com.example.shared.resources.renting_progress
import com.example.shared.resources.renting_requests
import com.example.shared.resources.saved_2
import com.example.shared.resources.sign_out
import com.example.shared.resources.sign_out_confirm
import com.example.shared.resources.sign_out_confirm_email
import com.example.shared.resources.sign_out_of_prohost
import com.example.shared.resources.specialist
import com.example.shared.resources.x_market
import com.example.ui.util.appString
import com.example.ui.util.sharedText

// Published as ProHost's real support/data-privacy contact on the public
// privacy page (public/privacy.html) — single source of truth for the
// drawer's "Contact Support" action, rather than a second hardcoded copy
// that could drift from the published one.

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
fun DrawerExploreHighlight(isActive: Boolean, onClick: () -> Unit) {
    ProHostDrawerHighlight(
        title = appString(Res.string.explore_workspaces),
        subtitle = appString(Res.string.browse_and_book_verified_spaces),
        isActive = isActive,
        onClick = onClick
    )
}

@Composable
fun SpecialistDrawerContent(
    currentUser: AppUser?,
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
        // Brand gradient header (theme-aware via ProHostColors.brandHeader*)
        val initials = (currentUser?.fullName ?: "")
            .split(" ").take(2)
            .joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
            .ifBlank { "P" }
        val planName = currentUser?.ownerPackageId?.let { localizedLabel(com.example.data.billing.PlayCatalog.planLabel(it)) }
        ProHostDrawerHeader(
            title = currentUser?.fullName ?: sharedText("prohost_user"),
            subtitle = if (planName != null) appString(Res.string.plan_x, planName) else marketLabel(currentUser?.country),
            onClose = { onDrawerAction("close") },
            leading = { ProHostDrawerAvatar(initials = initials, imageUrl = currentUser?.profilePictureUrl) },
            pill = { ProHostRolePill(text = if (isProHost) appString(Res.string.pro_host) else appString(Res.string.specialist), emphasized = isProHost) }
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {

        DrawerExploreHighlight(
            isActive = activeProHostTabId == null && activeMainTabId == "search_map",
            onClick = { onTabSelected("search_map") }
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        if (isProHost) {
            ProHostDrawerSectionLabel(text = appString(Res.string.pro_host))

            ProHostDrawerItem(
                label = appString(Res.string.renting_requests),
                icon = Icons.Default.Inbox,
                selected = activeProHostTabId == "owner_requests",
                onClick = { onTabSelected("owner_requests") },
                selectedIconTint = MaterialTheme.proColors.success,
                badgeDot = pendingRequestsCount > 0,
                emphasized = true
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            // Was a bottom tab; Explore took its place there.
            ProHostDrawerItem(
                label = appString(Res.string.renting_progress),
                icon = Icons.Default.Schedule,
                selected = activeProHostTabId == "owner_progress",
                onClick = { onTabSelected("owner_progress") },
                selectedIconTint = MaterialTheme.proColors.success,
                emphasized = true
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            ProHostDrawerItem(
                label = appString(Res.string.financials),
                icon = Icons.Default.Analytics,
                selected = activeProHostTabId == "stats",
                onClick = { onTabSelected("stats") },
                selectedIconTint = MaterialTheme.proColors.success,
                emphasized = true
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            ProHostDrawerItem(
                label = appString(Res.string.prohost_premium),
                icon = Icons.Default.Layers,
                selected = activeProHostTabId == "owner_subscriptions",
                onClick = { onTabSelected("owner_subscriptions") },
                selectedIconTint = MaterialTheme.proColors.success,
                emphasized = true
            )

        } else {
            // Specialists: Explore, Saved, My Rentals and Profile are in the bottom bar, so
            // the drawer only adds the upgrade path, Legal and Support.
            ProHostDrawerItem(
                label = appString(Res.string.become_a_pro_host),
                icon = Icons.Default.WorkspacePremium,
                selected = activeProHostTabId == "owner_subscriptions",
                onClick = { onTabSelected("owner_subscriptions") },
                selectedIconTint = MaterialTheme.proColors.success,
                emphasized = true
            )
        }

        ProHostDrawerDivider()

        // "PRACTICE RESOURCES" and "CONFIGURATION & SETTINGS" used to be two
        // separate sections for what's really one kind of destination — things a
        // specialist reaches occasionally, not core daily workflow. Merged into one
        // "MORE" section (also now home to Contact Support, relocated from the
        // Profile screen) so the drawer reads as fewer, clearer groups.
        ProHostDrawerSectionLabel(text = appString(Res.string.more).uppercase())

        // "Pending Requests" and "Payment Due Reminders" used to live here as their
        // own drawer shortcuts, routing to a pre-filtered view of My Bookings. My
        // Bookings' own filter chips (Pending/Accepted/etc.) already cover exactly
        // that, and it's one tap away from the bottom nav at all times — a drawer
        // shortcut to it added nothing. Removed rather than kept as a redundant
        // second path to the same screen.
        // Specialists have Saved in the bottom bar; Pro Hosts reach it here.
        if (isProHost) {
            ProHostDrawerItem(
                label = appString(Res.string.saved_2),
                icon = Icons.Default.Favorite,
                selected = activeProHostTabId == "my_favorites",
                onClick = { onTabSelected("my_favorites") }
            )
        }

        ProHostDrawerItem(
            label = appString(Res.string.legal_privacy_terms_policies),
            icon = Icons.Default.Gavel,
            selected = false,
            onClick = { onDrawerAction("legal_documents") }
        )
        ProHostDrawerItem(
            label = appString(Res.string.contact_support),
            icon = Icons.AutoMirrored.Filled.Help,
            selected = false,
            onClick = { com.example.ui.util.launchSupportEmail(currentUser, currentRole.name) }
        )

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
            .padding(top = Spacing.lg, bottom = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ProHostBrandLogo(size = 28.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = appString(Res.string.prohost_lebanon),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                ProHostCedarBadge(text = "v${com.example.util.AppInfo.versionName}", isCompact = true)
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
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(vertical = Spacing.md)
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(appString(Res.string.sign_out), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }

    if (showConfirmDialog) {
        ProHostDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(appString(Res.string.sign_out_of_prohost), fontWeight = FontWeight.Bold) },
            text = { Text(userEmail?.let { appString(Res.string.sign_out_confirm_email, it) } ?: appString(Res.string.sign_out_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onSignOut()
                    },
                    colors = ProHostButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(appString(Res.string.sign_out), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(appString(Res.string.cancel))
                }
            }
        )
    }
}

/** "Lebanon market" in the app language; a blank (legacy) profile country counts as Lebanon, like Explore's filter. */
@Composable
fun marketLabel(country: String?): String {
    val name = country?.takeIf { it.isNotBlank() } ?: "Lebanon"
    val countryName = rememberCountryDisplayName()
    val display = com.example.data.model.COUNTRIES.firstOrNull { it.name == name }
        ?.let(countryName) ?: name
    return appString(Res.string.x_market, display)
}
