package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.BookingRequestStatus
import com.example.data.model.SpaceListing
import com.example.data.model.UserRole
import com.example.ui.components.*
import com.example.ui.components.dialogs.DrawerDialogsHandler
import com.example.ui.components.drawer.AdminDrawerContent
import com.example.ui.components.drawer.SpecialistDrawerContent
import com.example.ui.screens.*
import com.example.ui.theme.CarnationOrange
import com.example.ui.theme.VibrantBlue
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.ProHostViewModel
import com.example.util.InAppUpdateManager
import com.example.util.UpdateState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch

/**
 * The bottom-nav tabs for SPECIALIST.
 */
private val SPECIALIST_BOTTOM_TABS = listOf(
    AppNavTab.SearchMap,
    AppNavTab.ProfessionalRentals,
    AppNavTab.ProfessionalProfile
)

/**
 * The bottom-nav tabs for PRO_HOST.
 */
private val PRO_HOST_BOTTOM_TABS = listOf(
    AppNavTab.ManageListings,
    AppNavTab.OwnerRentingProgress,
    AppNavTab.ProfessionalProfile
)

/**
 * Pro Host destinations — reachable only via the drawer's "Pro Host" section,
 * rendered full-screen (see fullScreenDrawerTab below) with no bottom nav.
 * Only a PRO_HOST account can reach all of these; a SPECIALIST can reach only
 * OwnerSubscriptions, as the "Become a Pro Host" package-purchase entry point.
 * Admin also reaches all but OwnerSubscriptions (see ADMIN_FULLSCREEN_TABS) —
 * Admin's listing/booking capability is unconditional, never a purchased package.
 */
private val PRO_HOST_FULLSCREEN_TABS = listOf(
    AppNavTab.OwnerRentalRequests,
    AppNavTab.Stats,
    AppNavTab.OwnerSubscriptions
)

/**
 * Admin's own side-menu destinations, rendered full-screen the same way Pro Host's
 * are. "Admin Console" is a single side-menu entry (its own inner tabs are its
 * sub-tabs — Revenue, Users, Listings, Owners & Payments, Schema, Security Audit —
 * not separate peer destinations); "Security ID" is the same personal-profile
 * screen every role has. Admin has no bottom nav at all.
 */
private val ADMIN_FULLSCREEN_TABS = listOf(
    AppNavTab.AdminConsole,
    AppNavTab.AdminProfile
)

/** Reachable via the drawer by every role — unlike PRO_HOST_FULLSCREEN_TABS
 * (Pro-Host-and-Admin-only, except OwnerSubscriptions) and ADMIN_FULLSCREEN_TABS
 * (Admin-only), My Favorites is a SPECIALIST-level feature every role keeps. */
private val SHARED_FULLSCREEN_TABS = listOf(
    AppNavTab.MyFavorites
)

private val FULLSCREEN_TAB_IDS: Set<String> =
    (PRO_HOST_FULLSCREEN_TABS + ADMIN_FULLSCREEN_TABS + SHARED_FULLSCREEN_TABS).map { it.id }.toSet()

/**
 * The complete set of tab ids a given role may ever land on — bottom-nav tabs
 * plus drawer-only destinations. This is the single source of truth for
 * validating externally-supplied tab ids (see below).
 */
private fun allowedTabIdsForRole(role: UserRole): Set<String> {
    // My Favorites is SPECIALIST-only — neither the Pro Host nor the Admin drawer
    // exposes an entry point to it (both intentionally hide the "My Favorites"
    // NavigationDrawerItem, AppDrawerContent.kt), so it must not be a landable
    // tab id for those roles either, or it becomes reachable only via a raw
    // deep link with no in-app way back to it.
    val shared = SHARED_FULLSCREEN_TABS.map { it.id }.toSet()
    return when (role) {
        UserRole.SPECIALIST -> SPECIALIST_BOTTOM_TABS.map { it.id }.toSet() + AppNavTab.OwnerSubscriptions.id + shared
        UserRole.PRO_HOST -> PRO_HOST_BOTTOM_TABS.map { it.id }.toSet() + PRO_HOST_FULLSCREEN_TABS.map { it.id }.toSet() + AppNavTab.SearchMap.id
        UserRole.ADMIN -> ADMIN_FULLSCREEN_TABS.map { it.id }.toSet() +
            (PRO_HOST_FULLSCREEN_TABS.map { it.id }.toSet() - AppNavTab.OwnerSubscriptions.id) + AppNavTab.ManageListings.id + AppNavTab.OwnerRentingProgress.id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProHostAppRoot(
    deepLinkTab: String? = null,
    deepLinkBookingId: String? = null,
    deepLinkSpaceId: String? = null,
    emailVerifiedDeepLink: Boolean = false,
    inAppUpdateManager: InAppUpdateManager? = null,
    viewModel: ProHostViewModel = viewModel()
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val currentUser by viewModel.currentUser.collectAsState()
    val drawerPackagePlans by viewModel.packagePlans.collectAsState()
    val deepLinkSpaces by viewModel.spaces.collectAsState()
    // Drives the red dot on the drawer's "Renting Requests" item (PRO_HOST/ADMIN)
    // so a new booking request is visible at a glance, alongside the existing
    // push notification, without opening the tab.
    val ownerIncomingRequestsForBadge by viewModel.ownerIncomingRequests.collectAsState()
    val pendingIncomingRequestsCount = remember(ownerIncomingRequestsForBadge) {
        ownerIncomingRequestsForBadge.count { it.status == BookingRequestStatus.PENDING }
    }
    var detailedSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var managingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var activeTabId by remember { mutableStateOf("search_map") }
    var activeDrawerTabDialog by remember { mutableStateOf<String?>(null) }
    // Non-null while a drawer-only destination is open — a Pro Host destination, or
    // (for Admin, who has no bottom nav at all) Admin Console/Security ID. These
    // render full-screen (no bottom nav, just a top bar with the screen's title +
    // menu icon) since they live outside the unified Specialist/Pro Host bottom nav —
    // see FULLSCREEN_TAB_IDS.
    var fullScreenDrawerTab by remember { mutableStateOf<String?>(null) }

    // Becoming a Pro Host used to be completely silent — grantProHostRoleIfNeeded
    // (functions/src/lib/entitlements.ts) fires the moment a package/PAYG payment
    // settles, and the live currentUser sync (ProHostRepository's onUsersUpdated)
    // picks the new role up automatically, but nothing ever told the user their
    // role actually changed — the drawer/nav just started quietly showing new
    // items. Only fires for a transition observed WITHIN this session (a
    // returning Pro Host whose role is already PRO_HOST on cold start correctly
    // sees nothing) — set once the previous role is known, so the very first
    // composition (previousRole still null) never counts as a transition.
    var previousRole by remember { mutableStateOf<UserRole?>(null) }
    var showProHostWelcome by remember { mutableStateOf(false) }
    LaunchedEffect(currentUser?.role) {
        val previous = previousRole
        val current = currentUser?.role
        if (previous != null && previous != UserRole.PRO_HOST && current == UserRole.PRO_HOST) {
            showProHostWelcome = true
        }
        previousRole = current
    }

    // Routes to any tab id, transparently choosing full-screen presentation vs. the
    // regular bottom-nav tab switch — the single place that decides how a given
    // destination id gets shown, used by the drawer, FCM alert taps, the
    // payment-return deep link, and initial role-based routing alike.
    fun navigateTo(targetTabId: String) {
        if (targetTabId in FULLSCREEN_TAB_IDS) {
            fullScreenDrawerTab = targetTabId
        } else {
            fullScreenDrawerTab = null
            activeTabId = targetTabId
        }
    }

    // Synchronize initial tab based on user role or incoming deep link
    LaunchedEffect(currentUser?.role, deepLinkTab) {
        val role = currentUser?.role
        if (deepLinkTab == "payment_return") {
            // Returned via the Whish payment App Link (hopebearer-award.com/payment/...).
            // No specific screen is encoded in the URL — route to wherever each role
            // settles Whish payments, mirroring the alert-tap routing in
            // DrawerDialogsHandler. The actual result comes from checkWhishStatus
            // polling already running in that screen, not from this navigation event.
            navigateTo(if (role == UserRole.SPECIALIST) "pro_rentals" else "owner_progress")
        } else if (!deepLinkTab.isNullOrBlank() && role != null && deepLinkTab in allowedTabIdsForRole(role)) {
            // MainActivity is an exported activity (required for the launcher intent
            // and the Whish payment App Link) and reads "target_tab" straight from an
            // Intent extra for FCM-notification-tap deep links. Without this check,
            // any other app on the device could launch MainActivity with
            // target_tab=admin_console and force a signed-in non-admin user into the
            // Admin Console UI — only ever accept a deep-linked tab id that's actually
            // valid for this user's current role.
            navigateTo(deepLinkTab)
        } else {
            when (currentUser?.role) {
                UserRole.ADMIN -> navigateTo("admin_console")
                UserRole.PRO_HOST -> navigateTo("search_map")
                UserRole.SPECIALIST -> navigateTo("search_map")
                null -> activeTabId = "auth"
            }
        }
    }

    // Email verification deep link: prohost://verify-email/success — show a one-shot toast
    // once the user is signed in, so they know their address is now verified.
    val emailVerifiedConsumed = remember { mutableStateOf(false) }
    LaunchedEffect(emailVerifiedDeepLink, currentUser?.id) {
        if (emailVerifiedDeepLink && !emailVerifiedConsumed.value && currentUser != null) {
            emailVerifiedConsumed.value = true
            android.widget.Toast.makeText(
                appContext,
                "Email verified! Your account is now Level 2.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Opens the listing detail modal for a tapped share link
    // (pro-host.tech/listing/{spaceId} — see MainActivity's
    // handleIncomingIntent and functions/src/listings/shareLanding.ts). Only
    // reachable once the user is signed in (the app gates everything behind
    // login) and the live spaces list has loaded — re-runs whenever either
    // changes, so a link tapped from a cold start (spaces not loaded yet) or
    // before sign-in still resolves the moment both are ready, without
    // needing a one-shot Firestore fetch just for this case.
    var consumedDeepLinkSpaceId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deepLinkSpaceId, deepLinkSpaces, currentUser?.id) {
        if (deepLinkSpaceId.isNullOrBlank() || currentUser == null) return@LaunchedEffect
        if (consumedDeepLinkSpaceId == deepLinkSpaceId) return@LaunchedEffect
        val match = deepLinkSpaces.find { it.id == deepLinkSpaceId }
        if (match != null) {
            consumedDeepLinkSpaceId = deepLinkSpaceId
            detailedSpace = match
            navigateTo("search_map")
        }
    }

    // Routes a booking-id deep link (FCM notification tap) to the appropriate booking
    // tab for this role. The destination tab shows all bookings; the ID is surfaced via
    // the existing search/filter fields on those screens rather than a separate detail
    // modal, since there is no standalone booking-detail route yet. (H4)
    var consumedDeepLinkBookingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deepLinkBookingId, currentUser?.id) {
        if (deepLinkBookingId.isNullOrBlank() || currentUser == null) return@LaunchedEffect
        if (consumedDeepLinkBookingId == deepLinkBookingId) return@LaunchedEffect
        consumedDeepLinkBookingId = deepLinkBookingId
        val role = currentUser?.role
        when (role) {
            UserRole.SPECIALIST -> navigateTo("pro_rentals")
            UserRole.PRO_HOST -> navigateTo("owner_rental_requests")
            else -> {}
        }
    }

    // Splash stays up until BOTH its own fixed animation delay finishes AND a
    // cold-start session restore (see ProHostViewModel's init block) has resolved —
    // without the second condition, a returning user's app used to flash the login
    // screen for a moment before currentUser got rehydrated from their still-valid
    // Firebase session.
    val isRestoringSession by viewModel.isRestoringSession.collectAsState()
    var splashTimerDone by remember { mutableStateOf(false) }
    val showSplash = !splashTimerDone || isRestoringSession

    if (showSplash) {
        SplashScreen(
            onSplashCompleted = {
                splashTimerDone = true
            }
        )
    } else if (currentUser == null) {
        // Non-null only for a stranded mid-registration account found during
        // cold-start restoration (see ProHostViewModel's init block) — routes
        // straight to the registration form, skipping phone/OTP entry, since
        // Firebase Auth already has a valid verified session for this number.
        val pendingRegistrationPhone by viewModel.pendingRegistrationPhone.collectAsState()
        val sessionRestoreError by viewModel.sessionRestoreError.collectAsState()
        Column {
            if (sessionRestoreError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            sessionRestoreError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            LoginAuthScreen(
                resumeAtRegistration = pendingRegistrationPhone != null,
                resumePhoneE164 = pendingRegistrationPhone,
                onCancelResume = { viewModel.clearPendingRegistrationPhone() },
                onLoginSuccess = {
                    viewModel.clearPendingRegistrationPhone()
                    // Handled via LaunchedEffect
                }
            )
        }
    } else if (currentUser?.isSuspended == true) {
        // Before this, nothing reacted to a mid-session suspension at all —
        // _currentUser (ProHostRepository's onUsersUpdated) already syncs
        // isSuspended live from Firestore the instant an Admin flips it, but
        // with no gate here the app just kept rendering as normal until the
        // suspended user's next write hit firestore.rules' isSuspended()
        // check and failed with whatever generic "check your connection"
        // message that particular screen happened to show — nothing told
        // them their account itself was the reason.
        SuspendedAccountScreen(
            onSignOut = {
                viewModel.logout()
                activeTabId = "auth"
            }
        )
    } else {
        val pinReauthRequired by viewModel.pinReauthRequired.collectAsState()
        val discoveryViewModel: DiscoveryViewModel = viewModel()
        val isMapViewActive by discoveryViewModel.isMapViewActive.collectAsState()
        val currentRole = currentUser?.role ?: UserRole.SPECIALIST
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Session re-auth overlay — shown when the app returns from background after >60s.
        if (pinReauthRequired) {
            PinReauthOverlay(
                viewModel = viewModel,
                onAuthenticated = { viewModel.clearPinReauth() },
                onSignOut = { viewModel.logout() }
            )
        } else {

        // Determine visible bottom-nav tabs strictly according to role
        val roleTabs: List<AppNavTab> = when (currentRole) {
            UserRole.SPECIALIST -> SPECIALIST_BOTTOM_TABS
            UserRole.PRO_HOST -> PRO_HOST_BOTTOM_TABS
            UserRole.ADMIN -> emptyList()
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false, // Edge-swipe gestures disabled; user opens drawer manually via header 3-dots icon
            scrimColor = Color.Black.copy(alpha = 0.35f),
            drawerContent = {
                ModalDrawerSheet(
                    // Was a hard 310.dp — on narrow phones that alone ate most of the
                    // screen width and left the identity card's inner rows almost no
                    // room to breathe. Scales with the screen instead, capped so it
                    // doesn't get oversized on tablets.
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 320.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerTonalElevation = 2.dp
                ) {
                    when (currentRole) {
                        UserRole.SPECIALIST, UserRole.PRO_HOST -> {
                            SpecialistDrawerContent(
                                currentUser = currentUser,
                                packagePlans = drawerPackagePlans,
                                currentRole = currentRole,
                                pendingRequestsCount = pendingIncomingRequestsCount,
                                activeProHostTabId = fullScreenDrawerTab,
                                onTabSelected = { tabId ->
                                    navigateTo(tabId)
                                    scope.launch { drawerState.close() }
                                },
                                onDrawerAction = { actionId ->
                                    activeDrawerTabDialog = actionId
                                    scope.launch { drawerState.close() }
                                },
                                onSignOut = {
                                    scope.launch { drawerState.close() }
                                    viewModel.logout()
                                    activeTabId = "auth"
                                }
                            )
                        }
                        UserRole.ADMIN -> {
                            AdminDrawerContent(
                                currentUser = currentUser,
                                pendingRequestsCount = pendingIncomingRequestsCount,
                                activeTabId = fullScreenDrawerTab,
                                onTabSelected = { tabId ->
                                    navigateTo(tabId)
                                    scope.launch { drawerState.close() }
                                },
                                onDrawerAction = { actionId ->
                                    activeDrawerTabDialog = actionId
                                    scope.launch { drawerState.close() }
                                },
                                onSignOut = {
                                    scope.launch { drawerState.close() }
                                    viewModel.logout()
                                    activeTabId = "auth"
                                }
                            )
                        }
                    }
                }
            }
        ) {
            // Defense in depth: never render a full-screen Pro Host destination
            // outside the current role's allowed set — same rule that already
            // gates activeTabId below, applied to fullScreenDrawerTab too, since
            // this is the actual authorization boundary for what renders (the
            // drawer only controls what's offered, not what can render).
            val safeFullScreenDrawerTab = fullScreenDrawerTab?.takeIf {
                it in allowedTabIdsForRole(currentRole)
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (detailedSpace == null) {
                        if (safeFullScreenDrawerTab != null) {
                            val title = (PRO_HOST_FULLSCREEN_TABS + ADMIN_FULLSCREEN_TABS + SHARED_FULLSCREEN_TABS)
                                .firstOrNull { it.id == safeFullScreenDrawerTab }?.title
                                ?: "Pro Host"
                            ProHostFullScreenTopAppBar(
                                title = title,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        } else if (activeTabId == AppNavTab.SearchMap.id) {
                            // Explore tab — keep title for context but suppress brand/logo
                            val alertsList = viewModel.fcmAlerts.collectAsState().value
                            val unreadCount = alertsList.count { !it.isRead }
                            ProHostTopAppBar(
                                currentRole = currentRole,
                                unreadAlertCount = unreadCount,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onAlertsClick = { activeDrawerTabDialog = "fcm_alerts" },
                                pageTitle = "Explore",
                                showBrand = false
                            )
                        } else {
                            val alertsList = viewModel.fcmAlerts.collectAsState().value
                            val unreadCount = alertsList.count { !it.isRead }

                            val currentPageTitle = roleTabs.firstOrNull { it.id == activeTabId }?.title
                            ProHostTopAppBar(
                                currentRole = currentRole,
                                unreadAlertCount = unreadCount,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onAlertsClick = { activeDrawerTabDialog = "fcm_alerts" },
                                pageTitle = currentPageTitle
                            )
                        }
                    }
                },
                bottomBar = {
                    // No bottom nav while a full-screen drawer destination is open, and
                    // none at all for Admin (roleTabs is empty for that role) — only the
                    // top bar's menu icon (reopen the drawer) is offered either way.
                    if (detailedSpace == null && safeFullScreenDrawerTab == null && roleTabs.isNotEmpty() &&
                        !(isMapViewActive && activeTabId == AppNavTab.SearchMap.id)) {
                        val roleAccentColor = if (currentRole == UserRole.PRO_HOST) CarnationOrange else VibrantBlue
                        Column {
                            // Glowing role-colored strip at the very top of the nav bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                roleAccentColor.copy(alpha = 0.6f),
                                                roleAccentColor,
                                                roleAccentColor.copy(alpha = 0.6f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            // Soft glow bloom below the strip
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                roleAccentColor.copy(alpha = 0.14f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                modifier = Modifier.testTag("bottom_navigation_bar")
                            ) {
                                roleTabs.forEach { tab ->
                                    val isSelected = activeTabId == tab.id
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { activeTabId = tab.id },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title
                                            )
                                        },
                                        label = { Text(tab.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = roleAccentColor,
                                            selectedTextColor = roleAccentColor,
                                            indicatorColor = roleAccentColor.copy(alpha = 0.12f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("nav_item_${tab.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                val updateState by (inAppUpdateManager?.updateState?.collectAsState() ?: remember { mutableStateOf(UpdateState.IDLE) })
                val downloadProgress by (inAppUpdateManager?.downloadProgress?.collectAsState() ?: remember { mutableStateOf(0f) })
                val isOfflineMode by viewModel.isOfflineMode.collectAsState()
                val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()

                // Admin has no bottom-nav "home" tab to fall back to (roleTabs is empty
                // for that role, and AdminConsole itself only ever renders via the
                // fullscreen branch below, never the regular activeTabId switch) — so
                // unlike Pro Host/Specialist, back must route Admin BACK INTO the
                // fullscreen branch (at AdminConsole), not out of it. The old code set
                // activeTabId = AdminConsole.id while also nulling fullScreenDrawerTab,
                // which landed on the regular switch's else-branch (DiscoveryScreen) —
                // a screen that doesn't belong to Admin's role at all — since
                // AdminConsole.id has no case there. Already at AdminConsole itself,
                // there's nowhere further back to go, so the handler is disabled there
                // and the system default (minimize/exit) applies, same as it always has
                // for Pro Host/Specialist sitting on their own root tab.
                val isAdminAtRoot = currentRole == UserRole.ADMIN && safeFullScreenDrawerTab == AppNavTab.AdminConsole.id
                BackHandler(enabled = managingSpace != null || detailedSpace != null || (safeFullScreenDrawerTab != null && !isAdminAtRoot)) {
                    if (managingSpace != null) {
                        managingSpace = null
                    } else if (detailedSpace != null) {
                        detailedSpace = null
                    } else if (safeFullScreenDrawerTab != null) {
                        if (currentRole == UserRole.ADMIN) {
                            fullScreenDrawerTab = AppNavTab.AdminConsole.id
                        } else {
                            fullScreenDrawerTab = null
                            activeTabId = when (currentRole) {
                                UserRole.PRO_HOST -> AppNavTab.ManageListings.id
                                else -> AppNavTab.SearchMap.id
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // In-App Update persistent banner when downloading or downloaded
                        InAppUpdateBanner(
                            updateState = updateState,
                            downloadProgress = downloadProgress,
                            onCompleteUpdate = { inAppUpdateManager?.completeUpdate() }
                        )
                        OfflineStatusBanner(
                            isOffline = isOfflineMode,
                            syncStatusMessage = syncStatusMessage
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (managingSpace != null) {
                                ManageListingScreen(
                                    space = managingSpace!!,
                                    viewModel = viewModel,
                                    onBack = { managingSpace = null }
                                )
                            } else if (detailedSpace != null) {
                                SpaceDetailsScreen(
                                    space = detailedSpace!!,
                                    viewModel = viewModel,
                                    onBack = { detailedSpace = null }
                                )
                            } else if (safeFullScreenDrawerTab != null) {
                                when (safeFullScreenDrawerTab) {
                                    AppNavTab.MyFavorites.id -> MyFavoritesScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpace = it },
                                        onNavigateToExplore = { navigateTo(AppNavTab.SearchMap.id) }
                                    )
                                    AppNavTab.OwnerRentalRequests.id -> OwnerRentalRequestsScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.Stats.id -> OwnerAnalyticsScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.OwnerSubscriptions.id -> OwnerSubscriptionsScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.AdminConsole.id -> AdminConsoleScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.AdminProfile.id -> SpecialistProfileScreen(
                                        viewModel = viewModel,
                                        inAppUpdateManager = inAppUpdateManager,
                                        onNavigateToTab = { tabId -> navigateTo(tabId) }
                                    )
                                }
                            } else {
                                // Defense in depth: even though activeTabId's only
                                // externally-influenced source (the deep-link branch
                                // above) is already role-validated, never render a
                                // screen outside the current role's allowed set —
                                // this is the actual authorization boundary for what
                                // the user sees, not the bottom nav/drawer (which
                                // only control what's offered, not what can render).
                                val safeActiveTabId = if (activeTabId in allowedTabIdsForRole(currentRole)) {
                                    activeTabId
                                } else {
                                    roleTabs.firstOrNull()?.id ?: activeTabId
                                }
                                when (safeActiveTabId) {
                                    AppNavTab.SearchMap.id -> DiscoveryScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpace = it },
                                        discoveryViewModel = discoveryViewModel
                                    )
                                    AppNavTab.ManageListings.id -> OwnerHubScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpace = it },
                                        onManageSpace = { managingSpace = it },
                                        onOpenSubscriptions = { navigateTo(AppNavTab.OwnerSubscriptions.id) }
                                    )
                                    AppNavTab.OwnerRentingProgress.id -> OwnerRentingProgressScreen(
                                        viewModel = viewModel,
                                        onOpenRequests = { navigateTo(AppNavTab.OwnerRentalRequests.id) }
                                    )
                                    AppNavTab.ProfessionalRentals.id -> MyBookingsScreen(
                                        viewModel = viewModel,
                                        onNavigateToDiscovery = { activeTabId = AppNavTab.SearchMap.id },
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpace = it }
                                    )
                                    AppNavTab.ProfessionalProfile.id -> SpecialistProfileScreen(
                                        viewModel = viewModel,
                                        inAppUpdateManager = inAppUpdateManager,
                                        onNavigateToTab = { tabId -> navigateTo(tabId) }
                                    )
                                    else -> DiscoveryScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpace = it },
                                        discoveryViewModel = discoveryViewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Handler for role-based custom dialog sheets
        DrawerDialogsHandler(
            dialogId = activeDrawerTabDialog,
            viewModel = viewModel,
            onNavigateToTab = { targetTab ->
                navigateTo(targetTab)
                activeDrawerTabDialog = null
            },
            onDismiss = { activeDrawerTabDialog = null }
        )

        if (showProHostWelcome) {
            AlertDialog(
                onDismissRequest = { showProHostWelcome = false },
                icon = { Icon(Icons.Default.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("You're Now a Pro Host!") },
                text = {
                    Text(
                        "Your package is active. Publish a workspace listing, respond to booking " +
                            "requests, and track your rentals — all from the Pro Host tools in the drawer."
                    )
                },
                confirmButton = {
                    Button(onClick = { showProHostWelcome = false }) {
                        Text("Let's Go")
                    }
                }
            )
        }
        } // end main app (pinReauthRequired == false) branch
    }
}

@Composable
private fun PinReauthOverlay(
    viewModel: ProHostViewModel,
    onAuthenticated: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Session Locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("The app was in the background. Tap Unlock Session to continue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAuthenticated,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Unlock Session") }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSignOut) { Text("Sign Out") }
        }
    }
}

/**
 * Blocking, full-screen — a suspended account has no partial access to the
 * app; every write it could attempt is denied server-side anyway
 * (firestore.rules' isSuspended()), so showing the normal UI underneath
 * would just be a maze of dead-end actions. The only way out is Sign Out;
 * reactivation is an Admin action on another device, which the live
 * currentUser sync (ProHostRepository's onUsersUpdated) picks up
 * automatically on this user's next sign-in.
 */
@Composable
private fun SuspendedAccountScreen(onSignOut: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Account Suspended",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your account has been suspended by ProHost. You can't book, publish, " +
                    "or manage listings while suspended. Contact support if you believe this is a mistake.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onSignOut) {
                Text("Sign Out")
            }
        }
    }
}
