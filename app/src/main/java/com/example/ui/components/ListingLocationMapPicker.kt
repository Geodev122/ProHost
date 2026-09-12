package com.example.ui.components

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.ui.theme.Spacing

data class PickedListingLocation(
    val lat: Double,
    val lng: Double,
    val addressLine: String?,
    val district: String?
)

/**
 * Real marker-drop location picker for listing creation — tap the map (or
 * drag the pin once it's placed) to set the listing's real geolocation,
 * reverse-geocoded live into a suggested street address/district the host
 * can accept with one tap (never silently overwrites whatever they've
 * already typed in the form fields below it). Replaces the previous
 * approach — geocoding the typed address string at submit time, falling
 * back to a random jitter around the governorate's center coordinate when
 * that failed — which never gave the host a real map or genuine control
 * over the pin at all.
 */
@Composable
fun ListingLocationMapPicker(
    initialCenter: LatLng,
    pickedLatLng: LatLng?,
    onLocationPicked: (PickedListingLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var lastResolved by remember { mutableStateOf<PickedListingLocation?>(null) }

    // A blank/placeholder Maps API key renders a silent blank grey map on a real
    // device — no crash, no error, just nothing — which is indistinguishable from a
    // real bug without checking this directly (same check AppSystemDebugger.kt's own
    // diagnostic screen runs). Read once; the key can't change without a fresh
    // process, so this doesn't need to be re-checked on every recomposition.
    val mapsKeyConfigured = remember {
        val configuredKey = runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
                .metaData
                ?.getString("com.google.android.geo.API_KEY")
        }.getOrNull()
        !configuredKey.isNullOrBlank() && configuredKey != "YOUR_GOOGLE_MAPS_API_KEY"
    }

    // Tracked separately from [pickedLatLng] (the parent's confirmed/resolved value,
    // which only updates once the async reverse-geocode call returns) so the pin and
    // map camera respond to a tap or drag immediately, not after a network round trip.
    var hasPlacedPin by remember { mutableStateOf(pickedLatLng != null) }
    val markerState = rememberMarkerState(position = pickedLatLng ?: initialCenter)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pickedLatLng ?: initialCenter, if (pickedLatLng != null) 15f else 11f)
    }

    // Keep the marker in sync when the parent resets pickedLatLng externally
    // (e.g. switching governorate before a pin is dropped) without treating
    // that as a user-driven drag.
    LaunchedEffect(pickedLatLng) {
        val target = pickedLatLng
        if (target != null && markerState.position != target) {
            markerState.position = target
        }
    }

    fun resolveAndEmit(latLng: LatLng) {
        isResolving = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    val addr = addresses?.firstOrNull()
                    val addressLine = addr?.let { a ->
                        (0..a.maxAddressLineIndex).mapNotNull { i -> a.getAddressLine(i) }.firstOrNull()
                    } ?: addr?.thoroughfare
                    PickedListingLocation(
                        lat = latLng.latitude,
                        lng = latLng.longitude,
                        addressLine = addressLine,
                        district = addr?.subLocality ?: addr?.locality
                    )
                } catch (e: Exception) {
                    PickedListingLocation(lat = latLng.latitude, lng = latLng.longitude, addressLine = null, district = null)
                }
            }
            lastResolved = result
            onLocationPicked(result)
            isResolving = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!mapsKeyConfigured) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(Spacing.lg)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                "Map unavailable — no Google Maps API key is configured for this build.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
                    onMapClick = { latLng ->
                        hasPlacedPin = true
                        markerState.position = latLng
                    }
                ) {
                    if (hasPlacedPin) {
                        Marker(
                            state = markerState,
                            draggable = true,
                            title = "Listing Location"
                        )
                    }
                }

                if (!hasPlacedPin) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tap the map to drop a pin at the exact location", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // Single source of truth for "the pin moved" — covers the initial tap-to-place
        // AND every subsequent drag, since both just mutate markerState.position. Guarded
        // by hasPlacedPin so the very first composition (no pin yet) never auto-resolves
        // the governorate's default center as if the host had tapped there.
        LaunchedEffect(markerState.position, hasPlacedPin) {
            if (hasPlacedPin && markerState.position != pickedLatLng) {
                resolveAndEmit(markerState.position)
            }
        }

        if (hasPlacedPin) {
            val pos = pickedLatLng ?: markerState.position
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Pinned: ${String.format(Locale.US, "%.5f", pos.latitude)}, ${String.format(Locale.US, "%.5f", pos.longitude)}",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val detected = lastResolved?.addressLine
                    if (detected != null) {
                        Text("Detected address: $detected", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (isResolving) {
                        Text("Resolving address…", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
