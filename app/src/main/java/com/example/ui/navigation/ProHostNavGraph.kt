package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.proHostScreenBackground
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
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.ProHostViewModel
import com.example.util.InAppUpdateManager
import com.example.util.UpdateState
import kotlinx.coroutines.launch

/**
 * The bottom-nav tabs for SPECIALIST.
 */
private val SPECIALIST_BOTTOM_TABS = listOf(
    AppNavTab.SearchMap,
    // Saved is a bottom tab for specialists (one tap), not a drawer screen; Pro Hosts
    // keep reaching it from the drawer.
    AppNavTab.MyFavorites,
    AppNavTab.ProfessionalRentals,
    AppNavTab.ProfessionalProfile
)

/**
 * The bottom-nav tabs for PRO_HOST. A Pro Host is still a Specialist underneath and
 * can book other hosts' spaces, so their own bookings stay one tap away.
 */
private val PRO_HOST_BOTTOM_TABS = listOf(
    AppNavTab.ManageListings,
    AppNavTab.OwnerRentingProgress,
    AppNavTab.ProfessionalRentals,
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
 * sub-tabs — Packages, Users, Listings, Analytics, Schema, Security Audit, ID Review —
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
    // My Favorites is reachable by SPECIALIST and PRO_HOST (both drawers show it).
    // The Admin drawer has no entry point, so it must not be a landable tab id for
    // Admin, or it becomes reachable only via a raw deep link with no way back.
    val shared = SHARED_FULLSCREEN_TABS.map { it.id }.toSet()
    return when (role) {
        UserRole.SPECIALIST -> SPECIALIST_BOTTOM_TABS.map { it.id }.toSet() + AppNavTab.OwnerSubscriptions.id + shared
        UserRole.PRO_HOST -> PRO_HOST_BOTTOM_TABS.map { it.id }.toSet() + PRO_HOST_FULLSCREEN_TABS.map { it.id }.toSet() + AppNavTab.SearchMap.id + shared
        UserRole.ADMIN -> ADMIN_FULLSCREEN_TABS.map { it.id }.toSet() +
            (PRO_HOST_FULLSCREEN_TABS.map { it.id }.toSet() - AppNavTab.OwnerSubscriptions.id) + AppNavTab.ManageListings.id + AppNavTab.OwnerRentingProgress.id +
            AppNavTab.SearchMap.id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProHostAppRoot(
    deepLinkTab: String? = null,
    deepLinkBookingId: String? = null,
    deepLinkSpaceId: String? = null,
    emailVerifiedDeepLink: Boolean = false,
    emailSignInLink: String? = null,
    onEmailSignInLinkConsumed: () -> Unit = {},
    emailOtpToken: String? = null,
    onEmailOtpTokenConsumed: () -> Unit = {},
    inAppUpdateManager: InAppUpdateManager? = null,
    viewModel: ProHostViewModel = viewModel()
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val currentUser by viewModel.currentUser.collectAsState()
    val deepLinkSpaces by viewModel.spaces.collectAsState()
    // Drives the red dot on the drawer's "Renting Requests" item (PRO_HOST/ADMIN)
    // so a new booking request is visible at a glance, alongside the existing
    // push notification, without opening the tab.
    val ownerIncomingRequestsForBadge by viewModel.ownerIncomingRequests.collectAsState()
    val pendingIncomingRequestsCount = remember(ownerIncomingRequestsForBadge) {
        ownerIncomingRequestsForBadge.count { it.status == BookingRequestStatus.PENDING }
    }
    var detailedSpace by remember { mutableStateOf<SpaceListing?>(null) }
    // Paired with detailedSpace: set when the user tapped a specific division/
    // subdivision card (Explore list or map) so SpaceDetailsScreen can pre-select
    // that division and open its availability sheet immediately, instead of
    // landing on the generic whole-space view.
    var detailedSpaceSubdivisionId by remember { mutableStateOf<String?>(null) }
    // A room card tapped on Explore opens that room's availability sheet straight away.
    var detailedSpaceOpenAvailability by remember { mutableStateOf(false) }
    // My Rentals scrolls to this booking (after "View request", or a booking push).
    var rentalsHighlightBookingId by remember { mutableStateOf<String?>(null) }
    var managingSpace by remember { mutableStateOf<SpaceListing?>(null) }
    // Saveable: survives configuration changes the manifest doesn't handle (theme,
    // locale, fold) and process death, so the person isn't thrown back to Explore.
    var activeTabId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("search_map") }
    var activeDrawerTabDialog by remember { mutableStateOf<String?>(null) }

    // KYC gate: shown as a full-screen overlay when a user with no verified phone
    // tries to access booking/listing features. Admin is exempt — their identity
    // is established via admin provisioning, not phone KYC. The gate is shown
    // in place of the requested tab, not as a dialog on top, so that the user has
    // a clear "dismiss" path (back button / close icon → returns to where they were).
    var showKycGate by remember { mutableStateOf(false) }
    var kycReturnTab by remember { mutableStateOf<String?>(null) }

    /** Pro Host workspaces that require a verified phone. My Rentals never does — viewing your
     *  own bookings needs no verification; sending one is checked in place (canTransact). */
    val kycRequiredTabIds: Set<String> = setOf(
        AppNavTab.ManageListings.id,
        AppNavTab.OwnerRentingProgress.id,
        AppNavTab.OwnerRentalRequests.id
    )
    // Non-null while a drawer-only destination is open — a Pro Host destination, or
    // (for Admin, who has no bottom nav at all) Admin Console/Security ID. These
    // render full-screen (no bottom nav, just a top bar with the screen's title +
    // menu icon) since they live outside the unified Specialist/Pro Host bottom nav —
    // see FULLSCREEN_TAB_IDS.
    var fullScreenDrawerTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

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
    // destination id gets shown, used by the drawer, FCM alert taps, deep
    // links, and initial role-based routing alike.
    //
    // KYC gate: if the user has no verified phone and the target tab requires one,
    // show the KYC screen instead and remember where to route after completion.
    fun navigateTo(targetTabId: String) {
        // Never route to a destination the current role can't use (e.g. a stale
        // notification target after a role change).
        val role = currentUser?.role
        if (role != null && targetTabId !in allowedTabIdsForRole(role)) return
        val needsKyc = targetTabId in kycRequiredTabIds &&
            currentUser?.role != UserRole.ADMIN &&
            currentUser?.hasVerifiedPhone(com.example.data.auth.PhoneLink.isLinked()) != true
        if (needsKyc) {
            kycReturnTab = targetTabId
            showKycGate = true
            return
        }
        val isBottomTab = when (currentUser?.role) {
            UserRole.SPECIALIST -> SPECIALIST_BOTTOM_TABS.any { it.id == targetTabId }
            UserRole.PRO_HOST -> PRO_HOST_BOTTOM_TABS.any { it.id == targetTabId }
            else -> false
        }
        if (targetTabId in FULLSCREEN_TAB_IDS && !isBottomTab) {
            fullScreenDrawerTab = targetTabId
        } else {
            fullScreenDrawerTab = null
            activeTabId = targetTabId
        }
    }

    // Synchronize initial tab based on user role or incoming deep link
    // Saved (as the enum name) so recreation — theme/locale change, process death — keeps the
    // restored tab instead of routing back to the role's home screen.
    var lastRoutedRole by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUser?.role, deepLinkTab) {
        val role = currentUser?.role
        val previousRole = lastRoutedRole
        lastRoutedRole = role?.name
        if (!deepLinkTab.isNullOrBlank() && role != null && deepLinkTab in allowedTabIdsForRole(role)) {
            // MainActivity is an exported activity (required for the launcher intent
            // and App Links) and reads "target_tab" straight from an
            // Intent extra for FCM-notification-tap deep links. Without this check,
            // any other app on the device could launch MainActivity with
            // target_tab=admin_console and force a signed-in non-admin user into the
            // Admin Console UI — only ever accept a deep-linked tab id that's actually
            // valid for this user's current role.
            navigateTo(deepLinkTab)
        } else if (previousRole != null && role != null &&
            (fullScreenDrawerTab ?: activeTabId) in allowedTabIdsForRole(role)
        ) {
            // A role change mid-session (e.g. a plan just activated on Premium): stay where
            // the person is when the new role can still use it.
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

    // Firebase Auth email sign-in link deep link: arrives from MainActivity when the
    // user taps a sign-in link email on the same device. Completes the sign-in by
    // retrieving the pending email from SharedPreferences and calling signInWithEmailLink.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val authViewModelForEmailLink: com.example.ui.viewmodel.AuthViewModel = viewModel()
    // Every tapped link is handled (a returning user signs in again after signing out in the
    // same app session). The ViewModel ignores a repeat of the link it is already handling.
    val onLinkVerified: (Boolean) -> Unit = { needsRegistration -> if (needsRegistration) activeTabId = "auth" }
    LaunchedEffect(emailSignInLink) {
        val link = emailSignInLink ?: return@LaunchedEffect
        if (activity != null) authViewModelForEmailLink.onEmailLinkArrived(activity, link, onLinkVerified)
        onEmailSignInLinkConsumed()
    }
    // A link opened without a saved address (other device, cleared app data): ask for it.
    val linkAwaitingEmail by authViewModelForEmailLink.linkAwaitingEmail.collectAsState()
    if (linkAwaitingEmail != null && activity != null) {
        var confirmEmail by remember { mutableStateOf("") }
        com.example.ui.components.ProHostDialog(
            onDismissRequest = { authViewModelForEmailLink.dismissLinkAwaitingEmail() },
            title = { androidx.compose.material3.Text("Finish signing in") },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(
                        "Enter the email address the sign-in link was sent to.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = confirmEmail,
                        onValueChange = { confirmEmail = it.trim() },
                        label = { androidx.compose.material3.Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = android.util.Patterns.EMAIL_ADDRESS.matcher(confirmEmail).matches(),
                    onClick = { authViewModelForEmailLink.completeLinkWithEmail(activity, confirmEmail, onLinkVerified) }
                ) { androidx.compose.material3.Text("Sign in") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { authViewModelForEmailLink.dismissLinkAwaitingEmail() }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }

    // One-click email OTP magic link: prohost://emailotp/verified?token=<customToken>
    // Sent by clickEmailOtpLink CF; signs in using the custom token exactly as if
    // the user had entered the OTP code manually in the OTP entry screen.
    LaunchedEffect(emailOtpToken) {
        val token = emailOtpToken ?: return@LaunchedEffect
        if (activity != null) authViewModelForEmailLink.signInWithOtpToken(activity, token, onLinkVerified)
        onEmailOtpTokenConsumed()
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
        // Don't re-open a listing the user already dismissed — an auth state change
        // (e.g. token refresh) re-fires this effect; guard prevents a back-navigation
        // loop where the detail screen re-appears after the user pressed back.
        if (detailedSpace != null) return@LaunchedEffect
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
        // A notification that names its own target tab (handled by the role-validated
        // deep-link effect above) wins; this is only the fallback for a bare booking id.
        if (role != null && !deepLinkTab.isNullOrBlank() && deepLinkTab in allowedTabIdsForRole(role)) return@LaunchedEffect
        when (role) {
            UserRole.SPECIALIST -> {
                rentalsHighlightBookingId = deepLinkBookingId
                navigateTo("pro_rentals")
            }
            UserRole.PRO_HOST -> navigateTo("owner_requests")
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
                resumePhoneE164 = pendingRegistrationPhone?.ifBlank { null },
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
        val discoveryViewModel: DiscoveryViewModel = viewModel()
        val isMapViewActive by discoveryViewModel.isMapViewActive.collectAsState()
        val currentRole = currentUser?.role ?: UserRole.SPECIALIST
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Determine visible bottom-nav tabs strictly according to role
        val roleTabs: List<AppNavTab> = when (currentRole) {
            UserRole.SPECIALIST -> SPECIALIST_BOTTOM_TABS
            UserRole.PRO_HOST -> PRO_HOST_BOTTOM_TABS
            UserRole.ADMIN -> emptyList()
        }
        // Specialists have one screen with every route (Explore at the centre, bottom
        // nav, Profile › More) — no drawer and no menu button anywhere.
        val isSpecialistShell = currentRole == UserRole.SPECIALIST
        val navExpandedOnMap by discoveryViewModel.navExpandedOnMap.collectAsState()
        // Every other page: the same collapse/expand handle, and the bar tucks away while the
        // content scrolls down and comes back on scroll up (see bottomNavScrollConnection).
        var navExpandedOffMap by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
        val onExploreMap = isMapViewActive && activeTabId == AppNavTab.SearchMap.id
        val shellUnreadAlerts = viewModel.fcmAlerts.collectAsState().value.count { !it.isRead }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false, // Edge-swipe gestures disabled; user opens drawer manually via header 3-dots icon
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f),
            drawerContent = {
                // Width scales with the screen (86%, capped at 320dp) — see ProHostDrawerSheet.
                ProHostDrawerSheet {
                    when (currentRole) {
                        UserRole.SPECIALIST, UserRole.PRO_HOST -> {
                            SpecialistDrawerContent(
                                currentUser = currentUser,
                                currentRole = currentRole,
                                pendingRequestsCount = pendingIncomingRequestsCount,
                                activeProHostTabId = fullScreenDrawerTab,
                                activeMainTabId = activeTabId,
                                onTabSelected = { tabId ->
                                    navigateTo(tabId)
                                    scope.launch { drawerState.close() }
                                },
                                onDrawerAction = { actionId ->
                                    if (actionId != "close") activeDrawerTabDialog = actionId
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
                                    if (actionId != "close") activeDrawerTabDialog = actionId
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
                // White page with the light paper texture behind every tab (transparent scaffold).
                modifier = Modifier.fillMaxSize().proHostScreenBackground(),
                containerColor = Color.Transparent,
                topBar = {
                    if (detailedSpace == null) {
                        if (safeFullScreenDrawerTab != null) {
                            val title = (PRO_HOST_FULLSCREEN_TABS + ADMIN_FULLSCREEN_TABS + SHARED_FULLSCREEN_TABS)
                                .firstOrNull { it.id == safeFullScreenDrawerTab }?.title
                                ?: "Pro Host"
                            ProHostFullScreenTopAppBar(
                                title = title,
                                // Specialists have no drawer: back to the screen they came from.
                                onMenuClick = {
                                    if (isSpecialistShell) {
                                        fullScreenDrawerTab = null
                                        activeTabId = AppNavTab.ProfessionalProfile.id
                                    } else {
                                        scope.launch { drawerState.open() }
                                    }
                                },
                                isBackNavigation = isSpecialistShell
                            )
                        } else if (isSpecialistShell && activeTabId == AppNavTab.SearchMap.id) {
                            // Explore draws its own floating header (logo, map/list, search,
                            // notifications) — no app bar above it.
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
                                showBrand = true
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
                                pageTitle = currentPageTitle,
                                showMenu = !isSpecialistShell
                            )
                        }
                    }
                },
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
                val isAdminOnMainTab = currentRole == UserRole.ADMIN && safeFullScreenDrawerTab == null
                // Specialists: back from Saved / My Rentals / Profile returns to Explore (the
                // centre of their one-screen shell) instead of leaving the app.
                val isSpecialistOffExplore = isSpecialistShell && safeFullScreenDrawerTab == null &&
                    activeTabId != AppNavTab.SearchMap.id
                BackHandler(enabled = managingSpace != null || detailedSpace != null || isAdminOnMainTab ||
                    (safeFullScreenDrawerTab != null && !isAdminAtRoot) || isSpecialistOffExplore) {
                    if (managingSpace != null) {
                        managingSpace = null
                    } else if (detailedSpace != null) {
                        detailedSpace = null
                        detailedSpaceSubdivisionId = null
                    } else if (isAdminOnMainTab) {
                        fullScreenDrawerTab = AppNavTab.AdminConsole.id
                    } else if (safeFullScreenDrawerTab != null) {
                        if (currentRole == UserRole.ADMIN) {
                            fullScreenDrawerTab = AppNavTab.AdminConsole.id
                        } else {
                            fullScreenDrawerTab = null
                            activeTabId = when (currentRole) {
                                UserRole.PRO_HOST -> AppNavTab.ManageListings.id
                                // Same place the top bar's back arrow goes (Premium is opened from Profile).
                                else -> AppNavTab.ProfessionalProfile.id
                            }
                        }
                    } else if (isSpecialistOffExplore) {
                        activeTabId = AppNavTab.SearchMap.id
                    }
                }

                // The floating bottom nav overlays the content (no opaque Scaffold slot);
                // screens leave LocalBottomNavInset free at their bottom instead.
                val showBottomNav = detailedSpace == null && managingSpace == null &&
                    safeFullScreenDrawerTab == null && roleTabs.isNotEmpty()
                val bottomNavCollapsed = if (onExploreMap) !navExpandedOnMap else !navExpandedOffMap
                val bottomNavScrollConnection = remember(onExploreMap) {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPreScroll(
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                        ): androidx.compose.ui.geometry.Offset {
                            if (!onExploreMap) {
                                if (available.y < -12f) navExpandedOffMap = false
                                else if (available.y > 12f) navExpandedOffMap = true
                            }
                            return androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
                val bottomNavInset = when {
                    !showBottomNav -> 0.dp
                    bottomNavCollapsed -> BottomNavCollapsedInset
                    else -> BottomNavFloatingInset
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .then(
                            if (showBottomNav) {
                                Modifier.nestedScroll(bottomNavScrollConnection)
                            } else Modifier
                        )
                ) {
                  CompositionLocalProvider(LocalBottomNavInset provides bottomNavInset) {
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
                                    intendedSubdivisionId = detailedSpaceSubdivisionId,
                                    openAvailability = detailedSpaceOpenAvailability,
                                    onViewRequest = { bookingId ->
                                        detailedSpace = null
                                        detailedSpaceSubdivisionId = null
                                        rentalsHighlightBookingId = bookingId
                                        navigateTo(AppNavTab.ProfessionalRentals.id)
                                    },
                                    onBack = { detailedSpace = null; detailedSpaceSubdivisionId = null }
                                )
                            } else if (safeFullScreenDrawerTab != null) {
                                when (safeFullScreenDrawerTab) {
                                    AppNavTab.MyFavorites.id -> MyFavoritesScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpaceOpenAvailability = false; detailedSpace = it },
                                        onNavigateToExplore = { navigateTo(AppNavTab.SearchMap.id) }
                                    )
                                    AppNavTab.OwnerRentalRequests.id -> OwnerRentalRequestsScreen(
                                        viewModel = viewModel
                                    )
                                    AppNavTab.Stats.id -> if (currentUser?.role == UserRole.ADMIN) {
                                        AdminAnalyticsScreen()
                                    } else {
                                        OwnerAnalyticsScreen(viewModel = viewModel)
                                    }
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
                                // Explore handles the inset itself (the map runs under the bar).
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = if (safeActiveTabId == AppNavTab.SearchMap.id) 0.dp else bottomNavInset)
                                ) {
                                when (safeActiveTabId) {
                                    AppNavTab.SearchMap.id -> DiscoveryScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { space, subdivisionId ->
                                            scope.launch { drawerState.close() }
                                            detailedSpace = space
                                            detailedSpaceSubdivisionId = subdivisionId
                                            detailedSpaceOpenAvailability = false // a room tap lands on its card; the sheet opens only from Availability
                                        },
                                        discoveryViewModel = discoveryViewModel,
                                        // Specialists have no app bar on Explore: its floating
                                        // header carries the logo and notifications.
                                        unreadAlertCount = shellUnreadAlerts,
                                        onAlertsClick = if (isSpecialistShell) {
                                            { activeDrawerTabDialog = "fcm_alerts" }
                                        } else null
                                    )
                                    AppNavTab.ManageListings.id -> OwnerHubScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpaceOpenAvailability = false; detailedSpace = it },
                                        onManageSpace = { managingSpace = it },
                                        onOpenSubscriptions = { navigateTo(AppNavTab.OwnerSubscriptions.id) }
                                    )
                                    AppNavTab.OwnerRentingProgress.id -> OwnerRentingProgressScreen(
                                        viewModel = viewModel,
                                        onOpenRequests = { navigateTo(AppNavTab.OwnerRentalRequests.id) }
                                    )
                                    AppNavTab.MyFavorites.id -> MyFavoritesScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { detailedSpaceOpenAvailability = false; detailedSpace = it },
                                        onNavigateToExplore = { navigateTo(AppNavTab.SearchMap.id) }
                                    )
                                    AppNavTab.ProfessionalRentals.id -> MyBookingsScreen(
                                        viewModel = viewModel,
                                        highlightBookingId = rentalsHighlightBookingId,
                                        onHighlightShown = { rentalsHighlightBookingId = null },
                                        onNavigateToDiscovery = { activeTabId = AppNavTab.SearchMap.id },
                                        onSelectSpace = { scope.launch { drawerState.close() }; detailedSpaceOpenAvailability = false; detailedSpace = it }
                                    )
                                    AppNavTab.ProfessionalProfile.id -> SpecialistProfileScreen(
                                        viewModel = viewModel,
                                        inAppUpdateManager = inAppUpdateManager,
                                        onNavigateToTab = { tabId -> navigateTo(tabId) },
                                        onOpenLegal = { activeDrawerTabDialog = "legal_documents" },
                                        onSignOut = {
                                            viewModel.logout()
                                            activeTabId = "auth"
                                        }
                                    )
                                    else -> DiscoveryScreen(
                                        viewModel = viewModel,
                                        onSelectSpace = { space, subdivisionId ->
                                            scope.launch { drawerState.close() }
                                            detailedSpace = space
                                            detailedSpaceSubdivisionId = subdivisionId
                                            detailedSpaceOpenAvailability = false // a room tap lands on its card; the sheet opens only from Availability
                                        },
                                        discoveryViewModel = discoveryViewModel,
                                        // Specialists have no app bar on Explore: its floating
                                        // header carries the logo and notifications.
                                        unreadAlertCount = shellUnreadAlerts,
                                        onAlertsClick = if (isSpecialistShell) {
                                            { activeDrawerTabDialog = "fcm_alerts" }
                                        } else null
                                    )
                                }
                                }
                            }
                        }
                    }
                  }
                    if (showBottomNav) {
                        ProHostBottomNavBar(
                            tabs = roleTabs,
                            activeTabId = activeTabId,
                            // Through navigateTo so the phone-verification gate (kycRequiredTabIds)
                            // applies to bottom-nav taps too.
                            onTabSelected = {
                                navigateTo(it)
                                discoveryViewModel.setNavExpandedOnMap(false)
                            },
                            highlightWithSecondary = currentRole == UserRole.PRO_HOST,
                            collapsed = bottomNavCollapsed,
                            onExpandChange = { expanded ->
                                if (onExploreMap) discoveryViewModel.setNavExpandedOnMap(expanded)
                                else navExpandedOffMap = expanded
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        // KYC gate overlay: full-screen phone-verification step shown when a user
        // without a verified phone tries to access booking/listing features.
        // BackHandler here must fire BEFORE the outer NavGraph handler so pressing
        // back dismisses the KYC overlay rather than clearing detailedSpace.
        BackHandler(enabled = showKycGate) {
            showKycGate = false
            kycReturnTab = null
        }
        if (showKycGate) {
            KycScreen(
                onKycComplete = {
                    showKycGate = false
                    val returnTo = kycReturnTab
                    kycReturnTab = null
                    if (returnTo != null) navigateTo(returnTo)
                },
                onDismiss = {
                    showKycGate = false
                    kycReturnTab = null
                }
            )
        }

        // Handler for role-based custom dialog sheets
        DrawerDialogsHandler(
            dialogId = activeDrawerTabDialog,
            viewModel = viewModel,
            onNavigateToTab = { targetTab ->
                navigateTo(targetTab)
                activeDrawerTabDialog = null
            },
            // Same handling as tapping the push itself: role-checked tab (navigateTo ignores a
            // tab this role can't open), and a booking push highlights that booking in My Rentals.
            onOpenNotification = { alert, targetTab ->
                navigateTo(targetTab)
                if (targetTab == "pro_rentals" && alert.bookingId != null) rentalsHighlightBookingId = alert.bookingId
                activeDrawerTabDialog = null
            },
            onDismiss = { activeDrawerTabDialog = null }
        )

        if (showProHostWelcome) {
            ProHostDialog(
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
    }

    // Play Billing: query purchases whenever the app returns to the foreground, so purchases
    // made outside the app (promo codes redeemed in the Play Store, another device) and
    // PENDING payments that completed meanwhile get verified, granted and acknowledged by
    // the backend — without the person finding Subscriptions › Restore Purchases.
    val autoLinkUser = currentUser?.takeIf { it.role != UserRole.ADMIN }
    // Play subscribers (and those with a payment problem) also get Play's transactional
    // in-app message on app open — Play shows it at most once a day, so it's safe to ask.
    val hasPlaySubscription = currentUser?.entitlementSource == "google_play" ||
        com.example.data.billing.PlayCatalog.kindOf(currentUser?.ownerPackageId) != null ||
        currentUser?.billingStatus in setOf("GRACE_PERIOD", "ON_HOLD", "PAUSED")
    val rootActivity = androidx.activity.compose.LocalActivity.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, autoLinkUser?.id, hasPlaySubscription) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && autoLinkUser != null) {
                viewModel.billing.refreshPlayPurchases(appContext)
                if (hasPlaySubscription && rootActivity != null) viewModel.billing.showBillingInAppMessages(rootActivity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- Analytics: one screen_view hook for the whole hand-rolled navigation ----
    val analyticsConsent by com.example.analytics.AnalyticsConsent.state.collectAsState()
    val analyticsScreen = when {
        showSplash -> "splash"
        currentUser == null -> "auth"
        currentUser?.isSuspended == true -> "suspended"
        showKycGate -> "kyc"
        managingSpace != null -> "manage_listing"
        detailedSpace != null -> "space_details"
        else -> fullScreenDrawerTab ?: activeTabId
    }
    LaunchedEffect(analyticsScreen, analyticsConsent) {
        com.example.analytics.AnalyticsTracker.screen(analyticsScreen)
    }
    LaunchedEffect(activeDrawerTabDialog, analyticsConsent) {
        activeDrawerTabDialog?.let { com.example.analytics.AnalyticsTracker.screen("dialog_$it", "Dialog") }
    }
    LaunchedEffect(currentUser, analyticsConsent) {
        com.example.analytics.AnalyticsTracker.setUser(currentUser)
    }
    if (!showSplash && analyticsConsent == com.example.analytics.ConsentState.UNKNOWN) {
        com.example.ui.components.AnalyticsConsentDialog()
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
