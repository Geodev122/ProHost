package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.Spacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

data class PickedListingLocation(
    val lat: Double,
    val lng: Double,
    val addressLine: String?,
    val district: String?,
    val city: String? = null,
    val country: String? = null,
    val governorate: String? = null
)

/**
 * Tactile Gear Stick / Joystick Map Navigation Control.
 * A circle inside a larger semi-transparent circle. User presses and drags
 * the inner knob up/down/left/right to pan and navigate the map continuously.
 */
@Composable
fun MapJoystickNavigator(
    onPan: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isDragging, dragOffset) {
        if (isDragging && dragOffset != Offset.Zero) {
            while (isActive) {
                val normX = (dragOffset.x / maxRadiusPx).coerceIn(-1f, 1f)
                val normY = (dragOffset.y / maxRadiusPx).coerceIn(-1f, 1f)
                onPan(normX, normY)
                delay(16) // ~60fps smooth panning
            }
        }
    }

    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = dragOffset + dragAmount
                        val distance = newOffset.getDistance()
                        dragOffset = if (distance > maxRadiusPx) {
                            newOffset * (maxRadiusPx / distance)
                        } else {
                            newOffset
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Direction arrows
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.TopCenter).size(14.dp)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter).size(14.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterStart).size(14.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterEnd).size(14.dp)
        )

        // Inner Draggable Joystick Knob
        val knobOffsetDpX = with(LocalDensity.current) { dragOffset.x.toDp() }
        val knobOffsetDpY = with(LocalDensity.current) { dragOffset.y.toDp() }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 6.dp,
            modifier = Modifier
                .offset(x = knobOffsetDpX, y = knobOffsetDpY)
                .size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.OpenWith,
                    contentDescription = "Joystick Navigation Knob",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Marker-drop location picker for listing creation.
 * Supports reverse geocoding to Country, City, District, Street Address,
 * "Use Marked Location" action button, high-accuracy GPS, and Joystick Gear Stick navigation.
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
    var isLocating by remember { mutableStateOf(false) }

    var hasPlacedPin by remember { mutableStateOf(pickedLatLng != null) }
    var markerPosition by remember { mutableStateOf(pickedLatLng ?: initialCenter) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

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

                    val street = listOfNotNull(addr?.subThoroughfare, addr?.thoroughfare)
                        .joinToString(" ")
                        .ifBlank { addr?.getAddressLine(0) }

                    val districtName = addr?.subLocality ?: addr?.locality ?: addr?.subAdminArea
                    val cityName = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                    val countryName = addr?.countryName ?: "Lebanon"
                    val govName = addr?.adminArea

                    PickedListingLocation(
                        lat = point.latitude,
                        lng = point.longitude,
                        addressLine = street,
                        district = districtName,
                        city = cityName,
                        country = countryName,
                        governorate = govName
                    )
                } catch (e: Exception) {
                    PickedListingLocation(
                        lat = point.latitude,
                        lng = point.longitude,
                        addressLine = null,
                        district = null,
                        city = null,
                        country = "Lebanon",
                        governorate = null
                    )
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

    LaunchedEffect(pickedLatLng) {
        val target = pickedLatLng
        if (target != null && markerPosition != target) {
            markerPosition = target
        }
    }

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
                .height(240.dp)
                .clipToBounds()
        ) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
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

                // High-Accuracy GPS Locate Button (Top Right)
                IconButton(
                    onClick = {
                        val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
                            isLocating = true
                            val cancelToken = CancellationTokenSource()
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelToken.token)
                                .addOnSuccessListener { location: Location? ->
                                    isLocating = false
                                    if (location != null) {
                                        val geo = GeoPoint(location.latitude, location.longitude)
                                        hasPlacedPin = true
                                        markerPosition = geo
                                        mapView.controller.setZoom(16.0)
                                        mapView.controller.animateTo(geo)
                                        Toast.makeText(context, "High-accuracy GPS location locked!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Acquiring GPS signal...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnFailureListener {
                                    isLocating = false
                                    Toast.makeText(context, "GPS signal unavailable", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(context, "Please grant location permissions", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = "Locate Me", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Joystick Gear Stick Controller (Bottom Right)
                MapJoystickNavigator(
                    onPan = { dx, dy ->
                        val center = mapView.mapCenter
                        val curLat = center.latitude
                        val curLng = center.longitude
                        val zoomLevel = mapView.zoomLevelDouble.coerceAtLeast(1.0)
                        val panStep = 0.00015 * (15.0 / zoomLevel)
                        val newLat = curLat - (dy * panStep)
                        val newLng = curLng + (dx * panStep)
                        mapView.controller.setCenter(GeoPoint(newLat, newLng))
                        mapView.invalidate()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )

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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Pinned: ${String.format(Locale.US, "%.5f", pos.latitude)}, ${String.format(Locale.US, "%.5f", pos.longitude)}",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val resolved = lastResolved
                    if (resolved != null) {
                        val fullAddressText = listOfNotNull(
                            resolved.addressLine,
                            resolved.district,
                            resolved.city,
                            resolved.country
                        ).filter { it.isNotBlank() }.distinct().joinToString(", ")

                        if (fullAddressText.isNotBlank()) {
                            Text(
                                "Address: $fullAddressText",
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Explicit "Use Marked Location" button
                        Button(
                            onClick = {
                                onLocationPicked(resolved)
                                Toast.makeText(context, "Address & coordinates applied to fields below!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use Marked Location", fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.Bold)
                        }
                    } else if (isResolving) {
                        Text("Resolving address details…", fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
