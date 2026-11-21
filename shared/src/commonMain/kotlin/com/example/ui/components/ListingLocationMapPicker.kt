package com.example.ui.components

import com.example.ui.theme.Spacing

import com.example.ui.theme.Elevation
import com.example.ui.util.UiMessages
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.Geocoding
import com.example.util.GeoPoint
import com.example.util.MapCamera
import com.example.ui.map.PlatformMap
import com.example.ui.map.rememberPlatformMapState
import com.example.util.LocateResult
import com.example.util.LocationProvider
import com.example.util.ResolvedAddress
import com.example.util.locate
import com.example.util.resolvePin
import kotlinx.coroutines.launch
import com.example.shared.resources.Res
import com.example.shared.resources.center_crosshair
import com.example.shared.resources.locate_me
import com.example.shared.resources.mark_pin_location
import com.example.ui.util.appString
import com.example.ui.util.sharedText

@Composable
fun ListingLocationMapPicker(
    initialLat: Double?,
    initialLng: Double?,
    onLocationConfirmed: (ResolvedAddress) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    val defaultCenter = if (initialLat != null && initialLng != null) {
        GeoPoint(initialLat, initialLng)
    } else {
        GeoPoint(20.0, 0.0) // world center — no country assumption
    }

    val mapState = rememberPlatformMapState(MapCamera(defaultCenter, if (initialLat != null && initialLng != null) 15f else 2f))
    val appLanguageTag = rememberAppLanguageTag()

    fun requestHighAccuracyLocation() {
        val provider = LocationProvider.require()
        if (provider.hasPermission()) {
            isLocating = true
            coroutineScope.launch {
                when (val result = locate(provider, fallbackToLast = false, onFreshFixDone = { isLocating = false })) {
                    is LocateResult.Found -> mapState.animateTo(GeoPoint(result.point.latitude, result.point.longitude), 15f)
                    LocateResult.Unavailable -> UiMessages.show(sharedText("gps_signal_unavailable"))
                    LocateResult.NoFix, LocateResult.Silent -> Unit
                }
            }
        }
    }

    // Permission, blocked-permission and location-off prompts are all native (PermissionPrompts.kt).
    val requestLocation = rememberLocationAccess { requestHighAccuracyLocation() }

    suspend fun resolveLocationAndConfirm(point: GeoPoint) {
        val resolved = Geocoding.require().resolvePin(appLanguageTag, point.latitude, point.longitude)
        onLocationConfirmed(resolved)
    }

    Box(modifier = modifier.clipToBounds()) {
        val hasLocationPermission = LocationProvider.require().hasPermission()

        PlatformMap(
            state = mapState,
            markers = emptyList(),
            contentPadding = PaddingValues(),
            myLocationEnabled = hasLocationPermission,
            onMapLoaded = {},
            onMapClick = {},
            modifier = Modifier.fillMaxSize(),
            mapToolbarEnabled = true // the picker kept Maps' default here
        )
        
        // Custom Crosshair Center Pin Overlay
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = appString(Res.string.center_crosshair),
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
                    val currentCenter = mapState.camera.target
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
                    Text(appString(Res.string.mark_pin_location), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                Icon(Icons.Default.MyLocation, contentDescription = appString(Res.string.locate_me), modifier = Modifier.size(20.dp))
            }
        }
    }
}
