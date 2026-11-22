package com.example.ui.navigation

import androidx.compose.runtime.Composable
import com.example.data.model.AppUser
import com.example.data.model.FCMAlert
import com.example.ui.viewmodel.ProHostViewModel

/**
 * Pieces of the app shell that stay platform-owned: the Android splash (its own `R` drawables) and the admin
 * screens, which remain in the Android app until the admin web cutover (docs/migration/ADMIN_PARITY.md).
 */
interface AppRootSlots {
    @Composable
    fun Splash(onSplashCompleted: () -> Unit)

    @Composable
    fun AdminDrawer(
        currentUser: AppUser?,
        pendingRequestsCount: Int,
        activeTabId: String?,
        onTabSelected: (String) -> Unit,
        onDrawerAction: (String) -> Unit,
        onSignOut: () -> Unit
    ) {}

    @Composable
    fun AdminAnalytics() {}

    @Composable
    fun AdminConsole(viewModel: ProHostViewModel) {}

    @Composable
    fun DrawerDialogs(
        dialogId: String?,
        viewModel: ProHostViewModel,
        onNavigateToTab: (String) -> Unit,
        onOpenNotification: (FCMAlert, String) -> Unit,
        onDismiss: () -> Unit
    ) {}
}
