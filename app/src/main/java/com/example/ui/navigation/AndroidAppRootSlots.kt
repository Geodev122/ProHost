package com.example.ui.navigation

import androidx.compose.runtime.Composable
import com.example.data.model.AppUser
import com.example.data.model.FCMAlert
import com.example.ui.components.dialogs.DrawerDialogsHandler
import com.example.ui.components.drawer.AdminDrawerContent
import com.example.ui.screens.AdminAnalyticsScreen
import com.example.ui.screens.AdminConsoleScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.ProHostViewModel

/** The Android app's splash and admin screens, rendered by the shared shell exactly where it used to call them. */
object AndroidAppRootSlots : AppRootSlots {
    @Composable
    override fun Splash(onSplashCompleted: () -> Unit) {
        SplashScreen(onSplashCompleted = onSplashCompleted)
    }

    @Composable
    override fun AdminDrawer(
        currentUser: AppUser?,
        pendingRequestsCount: Int,
        activeTabId: String?,
        onTabSelected: (String) -> Unit,
        onDrawerAction: (String) -> Unit,
        onSignOut: () -> Unit
    ) {
        AdminDrawerContent(
            currentUser = currentUser,
            pendingRequestsCount = pendingRequestsCount,
            activeTabId = activeTabId,
            onTabSelected = onTabSelected,
            onDrawerAction = onDrawerAction,
            onSignOut = onSignOut
        )
    }

    @Composable
    override fun AdminAnalytics() {
        AdminAnalyticsScreen()
    }

    @Composable
    override fun AdminConsole(viewModel: ProHostViewModel) {
        AdminConsoleScreen(viewModel = viewModel)
    }

    @Composable
    override fun DrawerDialogs(
        dialogId: String?,
        viewModel: ProHostViewModel,
        onNavigateToTab: (String) -> Unit,
        onOpenNotification: (FCMAlert, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        DrawerDialogsHandler(
            dialogId = dialogId,
            viewModel = viewModel,
            onNavigateToTab = onNavigateToTab,
            onOpenNotification = onOpenNotification,
            onDismiss = onDismiss
        )
    }
}
