package com.example.ui.components

import com.example.ui.util.uiText
import com.example.ui.theme.Spacing

import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.Elevation
import com.example.ui.util.UiMessages
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.Geocoding
import com.example.util.LocateResult
import com.example.util.LocationProvider
import com.example.util.ResolvedAddress
import com.example.util.locate
import com.example.util.resolvePin
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ListingLocationMapPicker(
    initialLat: Double?,
    initialLng: Double?,
    onLocationConfirmed: (ResolvedAddress) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    val defaultCenter = if (initialLat != null && initialLng != null) {
        LatLng(initialLat, initialLng)
    } else {
        LatLng(20.0, 0.0) // world center — no country assumption
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, if (initialLat != null && initialLng != null) 15f else 2f)
    }

    fun requestHighAccuracyLocation() {
        val provider = LocationProvider.require()
        if (provider.hasPermission()) {
            isLocating = true
            coroutineScope.launch {
                when (val result = locate(provider, fallbackToLast = false, onFreshFixDone = { isLocating = false })) {
                    is LocateResult.Found -> cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(result.point.latitude, result.point.longitude), 15f)
                    )
                    LocateResult.Unavailable -> UiMessages.show(uiText(R.string.gps_signal_unavailable))
                    LocateResult.NoFix, LocateResult.Silent -> Unit
                }
            }
        }
    }

    // Permission, blocked-permission and location-off prompts are all native (PermissionPrompts.kt).
    val requestLocation = rememberLocationAccess { requestHighAccuracyLocation() }

    suspend fun resolveLocationAndConfirm(latLng: LatLng) {
        val appLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val resolved = Geocoding.require().resolvePin(appLocale.toLanguageTag(), latLng.latitude, latLng.longitude)
        onLocationConfirmed(resolved)
    }

    Box(modifier = modifier.clipToBounds()) {
        val hasLocationPermission = LocationProvider.require().hasPermission()

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL
            ),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = true
            )
        )
        
        // Custom Crosshair Center Pin Overlay
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = stringResource(R.string.center_crosshair),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .offset(y = (-18).dp)
        )

        // Ultra-Compact Floating Action Controls (Inside bounds, zero overflow)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = {
                    val currentCenter = cameraPositionState.position.target
                    coroutineScope.launch {
                        resolveLocationAndConfirm(currentCenter)
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = Elevation.level3
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.mark_pin_location), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // GPS Locate FAB (Bottom Right inside bounds)
        FloatingActionButton(
            onClick = { requestLocation() },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(40.dp)
        ) {
            if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.locate_me), modifier = Modifier.size(20.dp))
            }
        }
    }
}
