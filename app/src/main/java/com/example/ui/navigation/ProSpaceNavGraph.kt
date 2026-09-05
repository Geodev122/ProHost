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
import com.example.ui.components.drawer.OwnerDrawerContent
import com.example.ui.components.drawer.ProfessionalDrawerContent
import com.example.ui.screens.*
import com.example.ui.viewmodel.ProSpaceViewModel
import com.example.util.InAppUpdateManager
import com.example.util.UpdateState
import kotlinx.coroutines.launch

/**
 * The complete set of tab ids a given role may ever land on — bottom-nav tabs
 * plus drawer-only destinations (e.g. Owner's "stats"/"owner_requests" aren't
 * in the bottom nav but are still legitimately reachable). This is the single
 * source of truth for validating externally-supplied tab ids (see below).
 */
private fun allowedTabIdsForRole(role: UserRole): Set<String> = when (role) {
    UserRole.PROFESSIONAL -> setOf(
        AppNavTab.SearchMap.id,
        AppNavTab.ProfessionalRentals.id,
        AppNavTab.ProfessionalProfile.id
    )
    UserRole.SPACE_OWNER -> setOf(
        AppNavTab.ManageListings.id,
        AppNavTab.OwnerRentalRequests.id,
        AppNavTab.OwnerRentingProgress.id,
        AppNavTab.Stats.id,
        AppNavTab.OwnerSubscriptions.id,
        AppNavTab.OwnerProfile.id
    )
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

    // Synchronize initial tab based on user role or incoming deep link
    LaunchedEffect(currentUser?.role, deepLinkTab) {
        val role = currentUser?.role
        if (deepLinkTab == "payment_return") {
            // Returned via the Whish payment App Link (hopebearer-award.com/payment/...).
            // No specific screen is encoded in the URL — route to wherever each role
            // settles Whish payments, mirroring the alert-tap routing in
            // DrawerDialogsHandler. The actual result comes from checkWhishStatus
            // polling already running in that screen, not from this navigation event.
            activeTabId = if (role == UserRole.PROFESSIONAL) "pro_rentals" else "owner_progress"
        } else if (!deepLinkTab.isNullOrBlank() && role != null && deepLinkTab in allowedTabIdsForRole(role)) {
            // MainActivity is an exported activity (required for the launcher intent
            // and the Whish payment App Link) and reads "target_tab" straight from an
            // Intent extra for FCM-notification-tap deep links. Without this check,
            // any other app on the device could launch MainActivity with
            // target_tab=admin_console and force a signed-in non-admin user into the
            // Admin Console UI — only ever accept a deep-linked tab id that's actually
            // valid for this user's current role.
            activeTabId = deepLinkTab
        } else {
            when (currentUser?.role) {
                UserRole.ADMIN -> activeTabId = "admin_console"
                UserRole.SPACE_OWNER -> activeTabId = "manage_listings"
                UserRole.PROFESSIONAL -> activeTabId = "search_map"
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
            viewModel = viewModel,
            onLoginSuccess = {
                // Handled via LaunchedEffect
            }
        )
    } else {
        val currentRole = currentUser?.role ?: UserRole.PROFESSIONAL
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Determine visible tabs strictly according to role
        val roleTabs: List<AppNavTab> = when (currentRole) {
            UserRole.PROFESSIONAL -> listOf(
                AppNavTab.SearchMap,
                AppNavTab.ProfessionalRentals,
                AppNavTab.ProfessionalProfile
            )
            UserRole.SPACE_OWNER -> listOf(
                AppNavTab.ManageListings,
                AppNavTab.OwnerRentalRequests,
                AppNavTab.OwnerRentingProgress,
                AppNavTab.OwnerSubscriptions,
                AppNavTab.OwnerProfile
            )
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
                        UserRole.PROFESSIONAL -> {
                            ProfessionalDrawerContent(
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
                        UserRole.SPACE_OWNER -> {
                            OwnerDrawerContent(
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
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (detailedSpace == null) {
                        val alertsList = viewModel.fcmAlerts.collectAsState().value
                        val unreadCount = alertsList.count { !it.isRead }

                        ProSpaceTopAppBar(
                            currentRole = currentRole,
                            unreadAlertCount = unreadCount,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onAlertsClick = { activeDrawerTabDialog = "fcm_alerts" }
                        )
                    }
                },
                bottomBar = {
                    if (detailedSpace == null) {
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
                                     AppNavTab.ManageListings.id -> OwnerHubScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { detailedSpace = it },
                                        onOpenSubscriptions = { activeTabId = AppNavTab.OwnerSubscriptions.id }
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
                                    AppNavTab.AdminConsole.id -> AdminConsoleScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.AdminRevenue.id -> AdminRevenueScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.ProfessionalProfile.id,
                                    AppNavTab.OwnerProfile.id,
                                    AppNavTab.AdminProfile.id -> SpecialistProfileScreen(
                                        viewModel = viewModel,
                                        inAppUpdateManager = inAppUpdateManager,
                                        onSignOut = {
                                            viewModel.logout()
                                            activeTabId = "auth"
                                        }
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
                activeTabId = targetTab
                activeDrawerTabDialog = null
            },
            onDismiss = { activeDrawerTabDialog = null }
        )
    }
}
