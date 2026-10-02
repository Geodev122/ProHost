package com.example.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Requests the POST_NOTIFICATIONS runtime permission (required on Android 13+) so
 * push notifications (booking alerts, payment reminders — see
 * ProHostMessagingService.showPhysicalNotification()) actually show. Without this,
 * NotificationManager.notify() silently no-ops until the user grants it manually via
 * system settings. Constructed once from MainActivity.onCreate() so it also covers an
 * already-signed-in returning user, not just the fresh sign-in flow in LoginAuthScreen
 * (which separately requests location permissions for the same reason).
 */
class NotificationPermissionManager(
    private val activity: ComponentActivity,
    private val onResult: () -> Unit = {},
) {
    private val tag = "NotificationPermissionManager"

    private val permissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(tag, "POST_NOTIFICATIONS permission result: $granted")
            onResult()
        }

    /** Calls [onResult] once the prompt is answered, or immediately when no prompt is needed. */
    fun requestIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isGranted(activity)) {
            onResult()
            return
        }
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        fun isGranted(context: android.content.Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
