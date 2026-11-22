package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.example.data.auth.BoolCallback
import com.example.ios.IosPlatform
import com.example.shared.resources.Res
import com.example.shared.resources.location_is_turned_off_for_prohost_open_se
import com.example.ui.util.appString
import com.example.ui.util.sharedText

/**
 * iOS: Core Location's own prompt the first time; once allowed, [onReady]. When it was denied or Location Services is off, the same
 * "open settings" dialog as Android (iOS has no "ask again" and no in-app "turn on location" dialog).
 */
@Composable
actual fun rememberLocationAccess(onReady: () -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onReady)
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SystemStylePermissionDialog(
            icon = Icons.Default.LocationOn,
            title = permissionTitle(sharedText("access_this_device_s_location")),
            message = appString(Res.string.location_is_turned_off_for_prohost_open_se),
            allowLabel = sharedText("open_settings"),
            denyLabel = sharedText("not_now"),
            onAllow = {
                showSettings = false
                runCatching { com.example.ui.util.PlatformActions.require().openAppSettings() }
            },
            onDeny = { showSettings = false }
        )
    }

    return remember {
        {
            val bridge = IosPlatform.location
            when (bridge?.locationStatus()) {
                null -> Unit
                1 -> latest()
                0 -> bridge.requestWhenInUse(BoolCallback { granted -> if (granted) latest() })
                else -> showSettings = true
            }
        }
    }
}
