package com.example.ui.components

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

/**
 * The one look for every in-app permission or consent prompt, matching Android's own runtime permission
 * dialog (Android 12+): centred icon, centred title with the app name in bold, optional detail, and
 * full-width stacked choices of equal weight. The system dialogs (notifications, location) and these
 * pre-/post-prompts therefore read as one family instead of mixing old AlertDialog styles.
 */
@Composable
fun SystemStylePermissionDialog(
    icon: ImageVector,
    title: AnnotatedString,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    message: String? = null,
    allowLabel: String = "Allow",
    denyLabel: String = "Don't allow",
    dismissible: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDeny() },
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible)
    ) {
        Surface(
            shape = Radius.Lg,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elevation.level3,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                extra?.invoke(this)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PermissionChoice(allowLabel, onAllow)
                    PermissionChoice(denyLabel, onDeny)
                }
            }
        }
    }
}

@Composable
private fun PermissionChoice(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = Radius.Md,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

/** "Allow **ProHost** to …?" — the system dialog's title pattern. */
fun permissionTitle(action: String): AnnotatedString = buildAnnotatedString {
    append("Allow ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("ProHost") }
    append(" to $action?")
}

private fun Activity?.hasLocation(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Location for the "locate me" buttons, all through native Android UI:
 * 1. permission — the system prompt (after a one-time "why" in [SystemStylePermissionDialog] when Android
 *    says a rationale is due);
 * 2. blocked for good ("Don't allow" twice) — a prompt that opens the app's system settings;
 * 3. device location off — Google Play services' own "turn on location" dialog.
 * [onReady] runs once location can actually be read. Returns the action to call from the button.
 */
@Composable
fun rememberLocationAccess(onReady: () -> Unit): () -> Unit {
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
            title = permissionTitle("access this device's location"),
            message = "Used only to centre the map on where you are. ProHost never tracks your location.",
            allowLabel = "Continue",
            denyLabel = "Not now",
            onAllow = { showRationale = false; permissionLauncher.launch(locationPermissions) },
            onDeny = { showRationale = false }
        )
    }
    if (showSettings) {
        SystemStylePermissionDialog(
            icon = Icons.Default.LocationOn,
            title = permissionTitle("access this device's location"),
            message = "Location is turned off for ProHost. Open Settings › Permissions › Location and choose \"Allow only while using the app\".",
            allowLabel = "Open settings",
            denyLabel = "Not now",
            onAllow = {
                showSettings = false
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
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
