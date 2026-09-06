package com.example.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.SpaceListing
import com.example.data.model.UserRole
import com.example.ui.components.*
import com.example.ui.components.dialogs.DrawerDialogsHandler
import com.example.ui.components.drawer.AdminDrawerContent
import com.example.ui.components.drawer.SpecialistDrawerContent
import com.example.ui.screens.*
import com.example.ui.viewmodel.ProSpaceViewModel
import com.example.util.InAppUpdateManager
import com.example.util.UpdateState
import kotlinx.coroutines.launch

/**
 * The bottom-nav tabs, identical for SPECIALIST and PRO_HOST — every account
 * keeps full Specialist capability (Explore/My Bookings/Profile) regardless of
 * whether it's also been promoted to Pro Host.
 */
private val SPECIALIST_BOTTOM_TABS = listOf(
    AppNavTab.SearchMap,
    AppNavTab.ProfessionalRentals,
    AppNavTab.ProfessionalProfile
)

/**
 * Pro Host destinations — reachable only via the drawer's "Pro Host" section,
 * rendered full-screen (see fullScreenProHostTab below) with no bottom nav.
 * Only a PRO_HOST account can reach all of these; a SPECIALIST can reach only
 * OwnerSubscriptions, as the "Become a Pro Host" package-purchase entry point.
 */
private val PRO_HOST_FULLSCREEN_TABS = listOf(
    AppNavTab.ManageListings,
    AppNavTab.OwnerRentalRequests,
    AppNavTab.OwnerRentingProgress,
    AppNavTab.Stats,
    AppNavTab.OwnerSubscriptions
)

private val PRO_HOST_FULLSCREEN_TAB_IDS: Set<String> = PRO_HOST_FULLSCREEN_TABS.map { it.id }.toSet()

/**
 * The complete set of tab ids a given role may ever land on — bottom-nav tabs
 * plus drawer-only destinations. This is the single source of truth for
 * validating externally-supplied tab ids (see below).
 */
private fun allowedTabIdsForRole(role: UserRole): Set<String> = when (role) {
    UserRole.SPECIALIST -> SPECIALIST_BOTTOM_TABS.map { it.id }.toSet() + AppNavTab.OwnerSubscriptions.id
    UserRole.PRO_HOST -> SPECIALIST_BOTTOM_TABS.map { it.id }.toSet() + PRO_HOST_FULLSCREEN_TAB_IDS
    UserRole.ADMIN -> setOf(
        AppNavTab.AdminConsole.id,
        AppNavTab.AdminRevenue.id,
        AppNavTab.AdminProfile.id
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSpaceAppRoot(
    deepLinkTab: String? = null,
    deepLinkBookingId: String? = null,
    inAppUpdateManager: InAppUpdateManager? = null,
    viewModel: ProSpaceViewModel = viewModel()
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var detailedSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var activeTabId by remember { mutableStateOf("search_map") }
    var activeDrawerTabDialog by remember { mutableStateOf<String?>(null) }
    // Non-null while a Pro Host drawer destination is open. These render full-screen
    // (no bottom nav, just a top bar with the screen's title + menu icon) since they
    // live outside the unified Specialist/Pro Host bottom nav — see PRO_HOST_FULLSCREEN_TABS.
    var fullScreenProHostTab by remember { mutableStateOf<String?>(null) }

    // Routes to any tab id, transparently choosing full-screen Pro Host presentation
    // vs. the regular bottom-nav tab switch — the single place that decides how a
    // given destination id gets shown, used by the drawer, FCM alert taps, the
    // payment-return deep link, and initial role-based routing alike.
    fun navigateTo(targetTabId: String) {
        if (targetTabId in PRO_HOST_FULLSCREEN_TAB_IDS) {
            fullScreenProHostTab = targetTabId
        } else {
            fullScreenProHostTab = null
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

    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(
            onSplashCompleted = {
                showSplash = false
            }
        )
    } else if (currentUser == null) {
        LoginAuthScreen(
            onLoginSuccess = {
                // Handled via LaunchedEffect
            }
        )
    } else {
        val currentRole = currentUser?.role ?: UserRole.SPECIALIST
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Determine visible bottom-nav tabs strictly according to role — identical
        // for SPECIALIST and PRO_HOST (point 4 of the role-model spec: a unified
        // bottom nav for both). Pro Host destinations live in PRO_HOST_FULLSCREEN_TABS
        // instead, reachable only via the drawer's "Pro Host" section.
        val roleTabs: List<AppNavTab> = when (currentRole) {
            UserRole.SPECIALIST, UserRole.PRO_HOST -> SPECIALIST_BOTTOM_TABS
            UserRole.ADMIN -> listOf(
                AppNavTab.AdminConsole,
                AppNavTab.AdminRevenue,
                AppNavTab.AdminProfile
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = detailedSpace == null,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(310.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    drawerTonalElevation = 4.dp
                ) {
                    when (currentRole) {
                        UserRole.SPECIALIST, UserRole.PRO_HOST -> {
                            SpecialistDrawerContent(
                                currentUser = currentUser,
                                currentRole = currentRole,
                                activeTabId = if (fullScreenProHostTab == null) activeTabId else "",
                                activeProHostTabId = fullScreenProHostTab,
                                onTabSelected = { tabId ->
                                    navigateTo(tabId)
                                    scope.launch { drawerState.close() }
                                },
                                onDrawerAction = { actionId ->
                                    activeDrawerTabDialog = actionId
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                        UserRole.ADMIN -> {
                            AdminDrawerContent(
                                currentUser = currentUser,
                                activeTabId = activeTabId,
                                onTabSelected = { tabId ->
                                    activeTabId = tabId
                                    scope.launch { drawerState.close() }
                                },
                                onDrawerAction = { actionId ->
                                    activeDrawerTabDialog = actionId
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                }
            }
        ) {
            // Defense in depth: never render a full-screen Pro Host destination
            // outside the current role's allowed set — same rule that already
            // gates activeTabId below, applied to fullScreenProHostTab too, since
            // this is the actual authorization boundary for what renders (the
            // drawer only controls what's offered, not what can render).
            val safeFullScreenProHostTab = fullScreenProHostTab?.takeIf {
                it in allowedTabIdsForRole(currentRole)
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (detailedSpace == null) {
                        if (safeFullScreenProHostTab != null) {
                            val title = PRO_HOST_FULLSCREEN_TABS.firstOrNull { it.id == safeFullScreenProHostTab }?.title
                                ?: "Pro Host"
                            ProHostFullScreenTopAppBar(
                                title = title,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        } else {
                            val alertsList = viewModel.fcmAlerts.collectAsState().value
                            val unreadCount = alertsList.count { !it.isRead }

                            ProSpaceTopAppBar(
                                currentRole = currentRole,
                                unreadAlertCount = unreadCount,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onAlertsClick = { activeDrawerTabDialog = "fcm_alerts" }
                            )
                        }
                    }
                },
                bottomBar = {
                    // No bottom nav while a Pro Host destination is open full-screen —
                    // only the top bar's menu icon (reopen the drawer) is offered.
                    if (detailedSpace == null && safeFullScreenProHostTab == null) {
                        NavigationBar(
                            tonalElevation = 6.dp,
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
                                    label = { Text(tab.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.testTag("nav_item_${tab.id}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                val updateState by (inAppUpdateManager?.updateState?.collectAsState() ?: remember { mutableStateOf(UpdateState.IDLE) })
                val downloadProgress by (inAppUpdateManager?.downloadProgress?.collectAsState() ?: remember { mutableStateOf(0f) })

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

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (detailedSpace != null) {
                                SpaceDetailsScreen(
                                    space = detailedSpace!!,
                                    viewModel = viewModel,
                                    onBack = { detailedSpace = null }
                                )
                            } else if (safeFullScreenProHostTab != null) {
                                when (safeFullScreenProHostTab) {
                                    AppNavTab.ManageListings.id -> OwnerHubScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { detailedSpace = it },
                                        onOpenSubscriptions = { navigateTo(AppNavTab.OwnerSubscriptions.id) }
                                    )
                                    AppNavTab.OwnerRentalRequests.id -> OwnerRentalRequestsScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.OwnerRentingProgress.id -> OwnerRentingProgressScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.Stats.id -> OwnerAnalyticsScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.OwnerSubscriptions.id -> OwnerSubscriptionsScreen(
                                        viewModel = viewModel
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
                                        onSelectSpace = { detailedSpace = it }
                                    )
                                    AppNavTab.ProfessionalRentals.id -> MyBookingsScreen(
                                        viewModel = viewModel,
                                        onNavigateToDiscovery = { activeTabId = AppNavTab.SearchMap.id },
                                        onSelectSpace = { detailedSpace = it }
                                    )
                                    AppNavTab.AdminConsole.id -> AdminConsoleScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.AdminRevenue.id -> AdminRevenueScreen()
                                    AppNavTab.ProfessionalProfile.id,
                                    AppNavTab.AdminProfile.id -> SpecialistProfileScreen(
                                        viewModel = viewModel,
                                        inAppUpdateManager = inAppUpdateManager,
                                        onSignOut = {
                                            viewModel.logout()
                                            activeTabId = "auth"
                                        },
                                        onNavigateToTab = { tabId -> navigateTo(tabId) }
                                    )
                                    else -> DiscoveryScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { detailedSpace = it }
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
    }
}
