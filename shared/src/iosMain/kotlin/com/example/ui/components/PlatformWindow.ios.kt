package com.example.ui.components

import androidx.compose.runtime.Composable

/**
 * iOS back = the edge swipe from the left (right in Arabic), which Compose Multiplatform 1.12 turns into a back event for
 * BackHandler by default. Same handler contract as Android's system back.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) =
    androidx.compose.ui.backhandler.BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun FullScreenDialogWindowSetup(lightBars: Boolean) = Unit

actual fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
