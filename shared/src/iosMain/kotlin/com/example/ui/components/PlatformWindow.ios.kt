package com.example.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun FullScreenDialogWindowSetup(lightBars: Boolean) = Unit
