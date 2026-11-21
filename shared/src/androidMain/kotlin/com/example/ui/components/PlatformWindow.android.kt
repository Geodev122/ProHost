package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun FullScreenDialogWindowSetup(lightBars: Boolean) {
    // The dialog window draws under the system bars, so it sets their icon colour for its own surface.
    val dialogView = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
            // Fill the screen and resize for the keyboard, so the footer stays visible above it.
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            @Suppress("DEPRECATION")
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            androidx.core.view.WindowCompat.getInsetsController(window, dialogView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }
}

actual fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
