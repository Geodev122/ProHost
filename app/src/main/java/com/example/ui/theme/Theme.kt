package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ProHost Brand Theme on Android: the shared [ProHostThemeBase] (colour schemes, shapes, page background live in `:shared`)
 * with the Android Manrope fonts ([Typography]) and the window's status/navigation bar contrast.
 *
 * Follows the device's system dark-mode setting by default via isSystemInDarkTheme(); pass darkTheme explicitly to override.
 */
@Composable
fun ProHostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    ProHostThemeBase(darkTheme = darkTheme, typography = Typography, systemBars = { dark -> AndroidSystemBars(dark) }, content = content)
}

@Composable
private fun AndroidSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}
