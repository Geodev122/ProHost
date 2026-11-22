package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/** iOS counterpart of Android's CONFIRM haptic: the system "success" notification feedback. */
@Composable
actual fun rememberConfirmHaptic(): () -> Unit = remember {
    val generator = UINotificationFeedbackGenerator()
    ({ generator.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess) })
}
