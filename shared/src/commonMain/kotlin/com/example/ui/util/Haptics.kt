package com.example.ui.util

import androidx.compose.runtime.Composable

/**
 * The confirming tick under the finger when a request lands (Android: the CONFIRM haptic on API 30+, a context click before).
 * The other haptics in the app use Compose's own `LocalHapticFeedback`, which is already common.
 */
@Composable
expect fun rememberConfirmHaptic(): () -> Unit
