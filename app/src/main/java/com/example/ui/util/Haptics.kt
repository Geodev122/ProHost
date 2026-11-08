package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The confirming tick under the finger when a request lands: Android's dedicated CONFIRM pattern on API 30+, a context click before that.
 * (The other haptics in the app use Compose's own `LocalHapticFeedback`, which is already common.)
 */
@Composable
fun rememberConfirmHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                else android.view.HapticFeedbackConstants.CONTEXT_CLICK
            )
        }
    }
}
