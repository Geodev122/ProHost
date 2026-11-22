package com.example.ui.components.drawer

import com.example.ui.util.localizedLabel
import com.example.ui.components.displayName
import com.example.ui.util.uiText
import com.example.R
import androidx.compose.ui.res.stringResource
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
        // Brand gradient header (Admin variant)
        ProHostDrawerHeader(
            title = currentUser?.fullName ?: uiText(R.string.system_admin_2),
            subtitle = marketLabel(currentUser?.country),
            onClose = { onDrawerAction("close") },
            leading = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            pill = { ProHostRolePill(text = stringResource(R.string.system_admin), emphasized = true) }
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {

        DrawerExploreHighlight(
            isActive = activeTabId == "search_map",
            onClick = { onTabSelected("search_map") }
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        ProHostDrawerSectionLabel(text = stringResource(R.string.security))

        ProHostDrawerItem(
            label = stringResource(R.string.system_admin_console),
            icon = Icons.Default.AdminPanelSettings,
            selected = activeTabId == "admin_console",
            onClick = { onTabSelected("admin_console") },
            selectedIconTint = MaterialTheme.colorScheme.secondary,
            emphasized = true
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        ProHostDrawerItem(
            label = stringResource(R.string.security_id_card),
            icon = Icons.Default.Shield,
            selected = activeTabId == "admin_profile",
            onClick = { onTabSelected("admin_profile") },
            selectedIconTint = MaterialTheme.colorScheme.secondary,
            emphasized = true
        )

        ProHostDrawerDivider()

        // Admin gets every Pro Host capability unconditionally — unlimited listings,
        // no package to buy (see ProHostRepository's admin bypass) — so these route
        // through the exact same screens a fully-entitled Pro Host uses.
        ProHostDrawerSectionLabel(text = stringResource(R.string.pro_host_access))

        ProHostDrawerItem(
            label = stringResource(R.string.my_listings),
            icon = Icons.Default.HomeWork,
            selected = activeTabId == "manage_listings",
            onClick = { onTabSelected("manage_listings") },
            selectedIconTint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        ProHostDrawerItem(
            label = stringResource(R.string.renting_progress),
            icon = Icons.Default.Schedule,
            selected = activeTabId == "owner_progress",
            onClick = { onTabSelected("owner_progress") },
            selectedIconTint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        ProHostDrawerItem(
            label = stringResource(R.string.renting_requests),
            icon = Icons.Default.Inbox,
            selected = activeTabId == "owner_requests",
            onClick = { onTabSelected("owner_requests") },
            selectedIconTint = MaterialTheme.colorScheme.secondary,
            badgeDot = pendingRequestsCount > 0
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        ProHostDrawerItem(
            label = stringResource(R.string.analytics),
            icon = Icons.Default.Analytics,
            selected = activeTabId == "stats",
            onClick = { onTabSelected("stats") },
            selectedIconTint = MaterialTheme.colorScheme.secondary
        )

        ProHostDrawerDivider()

        ProHostDrawerSectionLabel(text = stringResource(R.string.billing_audit))

        ProHostDrawerItem(
            label = stringResource(R.string.system_audit_logs),
            icon = Icons.Default.Terminal,
            selected = false,
            onClick = { onDrawerAction("admin_audit") }
        )

        // System Debugger stays at the very bottom of the admin side menu, below
        // every other destination. Debug-build-only, on top of the admin-role gate
        // this whole drawer is already behind — a live Firebase/system diagnostics
        // panel has no end-user purpose in a release build, and gating it only by
        // role would leave it reachable by any account ever promoted to ADMIN.
        if (com.example.BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            ProHostDrawerItem(
                label = stringResource(R.string.cloud_system_debugger),
                icon = Icons.Default.BugReport,
                selected = false,
                onClick = { onDrawerAction("system_debugger") },
                emphasized = true
            )
        }

        ProHostDrawerFooter()

        // Always the last element in the drawer — see DrawerSignOutButton's own doc
        // comment for why it lives here instead of on DrawerIdentityCard.
        DrawerSignOutButton(userEmail = currentUser?.email, onSignOut = onSignOut)
        } // end inner padding Column
    }
}

