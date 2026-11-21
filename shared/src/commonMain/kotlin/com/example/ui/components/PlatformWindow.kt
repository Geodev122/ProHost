package com.example.ui.components

import androidx.compose.runtime.Composable

/** System back (Android back button/gesture). Android: `androidx.activity.compose.BackHandler`; iOS has no back key yet (no-op). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * Inside a full-screen `Dialog`: make its window fill the screen, resize for the keyboard and use [lightBars] icons on the system
 * bars. Android sets this on the dialog window; iOS has no separate dialog window (no-op).
 */
@Composable
expect fun FullScreenDialogWindowSetup(lightBars: Boolean)
