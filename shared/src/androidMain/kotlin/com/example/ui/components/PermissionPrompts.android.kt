package com.example.ui.components

import com.example.ui.theme.Spacing
import com.example.ui.theme.Radius
import com.example.ui.theme.Elevation
import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.example.shared.resources.Res
import com.example.shared.resources.location_is_turned_off_for_prohost_open_se
import com.example.shared.resources.used_only_to_centre_the_map_on_where_you_a
import com.example.ui.util.appString
import com.example.ui.util.sharedText

private fun Activity?.hasLocation(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
actual fun rememberLocationAccess(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity: Activity? = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val latestOnReady by rememberUpdatedState(onReady)
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) latestOnReady()
    }

    fun ensureLocationOn() {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build())
            .setAlwaysShow(true)
            .build()
        LocationServices.getSettingsClient(context).checkLocationSettings(request)
            .addOnSuccessListener { latestOnReady() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    runCatching { settingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build()) }
                        .onFailure { latestOnReady() }
                } else {
                    latestOnReady() // can't tell: let the caller try and report its own result
                }
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            com.example.analytics.AnalyticsTracker.locationPermission("granted", "map")
            ensureLocationOn()
        } else if (activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            // Android won't show its prompt again: the only way back is the app's settings.
            com.example.analytics.AnalyticsTracker.locationPermission("blocked", "map")
            showSettings = true
        } else {
            com.example.analytics.AnalyticsTracker.locationPermission("denied", "map")
        }
    }
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    if (showRationale) {
        SystemStylePermissionDialog(
            icon = Icons.Default.LocationOn,
            title = permissionTitle(sharedText("access_this_device_s_location")),
            message = appString(Res.string.used_only_to_centre_the_map_on_where_you_a),
            allowLabel = sharedText("action_continue"),
            denyLabel = sharedText("not_now"),
            onAllow = { showRationale = false; permissionLauncher.launch(locationPermissions) },
            onDeny = { showRationale = false }
        )
    }
    if (showSettings) {
        SystemStylePermissionDialog(
            icon = Icons.Default.LocationOn,
            title = permissionTitle(sharedText("access_this_device_s_location")),
            message = appString(Res.string.location_is_turned_off_for_prohost_open_se),
            allowLabel = sharedText("open_settings"),
            denyLabel = sharedText("not_now"),
            onAllow = {
                showSettings = false
                runCatching {
                    com.example.ui.util.PlatformActions.require().openAppSettings()
                }
            },
            onDeny = { showSettings = false }
        )
    }

    return {
        when {
            activity.hasLocation(context) -> ensureLocationOn()
            activity != null && locationPermissions.any { activity.shouldShowRequestPermissionRationale(it) } -> showRationale = true
            else -> permissionLauncher.launch(locationPermissions)
        }
    }
}
