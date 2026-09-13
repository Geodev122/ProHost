package com.example.ui.components

import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
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
 * already typed in the form fields below it).
 *
 * Runs on OpenStreetMap (osmdroid) rather than Google Maps — no API key, no
 * billing account, no Cloud Console configuration to get wrong, which is
 * exactly the class of "map renders blank with zero diagnosable error" bug
 * that motivated retiring Google Maps entirely from this app. Reverse
 * geocoding still uses Android's own on-device Geocoder, which was never
 * Google-Maps-specific and needed no change here.
 */
@Composable
fun ListingLocationMapPicker(
    initialCenter: GeoPoint,
    pickedLatLng: GeoPoint?,
    onLocationPicked: (PickedListingLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var lastResolved by remember { mutableStateOf<PickedListingLocation?>(null) }

    // Tracked separately from [pickedLatLng] (the parent's confirmed/resolved value,
    // which only updates once the async reverse-geocode call returns) so the pin
    // responds to a tap or drag immediately, not after a network round trip.
    var hasPlacedPin by remember { mutableStateOf(pickedLatLng != null) }
    var markerPosition by remember { mutableStateOf(pickedLatLng ?: initialCenter) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(if (pickedLatLng != null) 15.0 else 11.0)
            controller.setCenter(pickedLatLng ?: initialCenter)
        }
    }

    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Listing Location"
            isDraggable = true
        }
    }

    fun resolveAndEmit(point: GeoPoint) {
        isResolving = true
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                    val addr = addresses?.firstOrNull()
                    val addressLine = addr?.let { a ->
                        (0..a.maxAddressLineIndex).mapNotNull { i -> a.getAddressLine(i) }.firstOrNull()
                    } ?: addr?.thoroughfare
                    PickedListingLocation(
                        lat = point.latitude,
                        lng = point.longitude,
                        addressLine = addressLine,
                        district = addr?.subLocality ?: addr?.locality
                    )
                } catch (e: Exception) {
                    PickedListingLocation(lat = point.latitude, lng = point.longitude, addressLine = null, district = null)
                }
            }
            lastResolved = result
            onLocationPicked(result)
            isResolving = false
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                hasPlacedPin = true
                markerPosition = p
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(0, eventsOverlay)
        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}
            override fun onMarkerDrag(marker: Marker) {}
            override fun onMarkerDragEnd(marker: Marker) {
                markerPosition = marker.position
            }
        })

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.overlays.remove(eventsOverlay)
            mapView.onDetach()
        }
    }

    // Keep the marker in sync when the parent resets pickedLatLng externally
    // (e.g. switching governorate before a pin is dropped) without treating
    // that as a user-driven drag.
    LaunchedEffect(pickedLatLng) {
        val target = pickedLatLng
        if (target != null && markerPosition != target) {
            markerPosition = target
        }
    }

    // Single source of truth for "the pin moved" — covers the initial tap-to-place
    // AND every subsequent drag, since both just mutate markerPosition. Guarded by
    // hasPlacedPin so the very first composition (no pin yet) never auto-resolves
    // the governorate's default center as if the host had tapped there.
    LaunchedEffect(markerPosition, hasPlacedPin) {
        if (hasPlacedPin && markerPosition != pickedLatLng) {
            resolveAndEmit(markerPosition)
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
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (hasPlacedPin) {
                            marker.position = markerPosition
                            if (marker !in view.overlays) view.overlays.add(marker)
                        } else if (marker in view.overlays) {
                            view.overlays.remove(marker)
                        }
                        view.invalidate()
                    }
                )

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

                // Required by OpenStreetMap's tile-usage policy for apps using its
                // default tile servers directly.
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        "© OpenStreetMap contributors",
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        if (hasPlacedPin) {
            val pos = pickedLatLng ?: markerPosition
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
