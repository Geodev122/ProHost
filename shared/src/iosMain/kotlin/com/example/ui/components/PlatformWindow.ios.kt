package com.example.ui.components

import androidx.compose.runtime.Composable

/**
 * iOS back = the edge swipe from the left (right in Arabic), which Compose Multiplatform turns into a back event when the
 * view controller enables it (`enableBackGesture`, `MainViewController`). Same handler contract as Android's system back.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.compose.ui.backhandler.BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun FullScreenDialogWindowSetup(lightBars: Boolean) = Unit

actual fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
