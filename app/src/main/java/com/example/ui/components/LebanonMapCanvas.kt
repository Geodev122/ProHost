package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.SpaceListing
import com.example.ui.theme.Spacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

private val MARKER_COLOR_SELECTED = android.graphics.Color.parseColor("#E53935")
private val MARKER_COLOR_ACTIVE_SUBSCRIPTION = android.graphics.Color.parseColor("#43A047")
private val MARKER_COLOR_DEFAULT = android.graphics.Color.parseColor("#FFB300")
private val MARKER_COLOR_USER = android.graphics.Color.parseColor("#1E88E5")

private fun tintedMarkerIcon(context: android.content.Context, tintColor: Int) =
    ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.apply {
        setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
    }

@Composable
fun LebanonMapCanvas(
    spaces: List<SpaceListing>,
    onSpaceSelected: (SpaceListing?) -> Unit,
    onNavigateToDetails: (SpaceListing) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // GPS Proximity coordinates
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var sortedSpaces by remember { mutableStateOf(spaces) }
    var isLocating by remember { mutableStateOf(false) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun requestHighAccuracyLocation() {
        val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
            isLocating = true
            val cancelToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelToken.token)
                .addOnSuccessListener { location: Location? ->
                    isLocating = false
                    if (location != null) {
                        userLocation = GeoPoint(location.latitude, location.longitude)
                        Toast.makeText(context, "High-accuracy GPS location locked!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Fallback to last known location
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                userLocation = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                                Toast.makeText(context, "Location updated from GPS cache.", Toast.LENGTH_SHORT).show()
                            } else {
                                userLocation = GeoPoint(33.8886, 35.5184)
                                Toast.makeText(context, "GPS active. Position locked on Beirut.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    isLocating = false
                    Toast.makeText(context, "GPS signal unavailable", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            requestHighAccuracyLocation()
        } else {
            Toast.makeText(context, "Location permission declined. Nearby features unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    fun calculateDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    LaunchedEffect(spaces, userLocation) {
        val userLoc = userLocation
        if (userLoc != null) {
            sortedSpaces = spaces.sortedBy { space ->
                calculateDistanceKm(userLoc.latitude, userLoc.longitude, space.lat, space.lng)
            }
        } else {
            sortedSpaces = spaces
        }
    }

    var activePinSpace by remember { mutableStateOf<SpaceListing?>(null) }
    val defaultCenter = GeoPoint(33.8886, 35.5184)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(true)
            controller.setZoom(10.0)
            controller.setCenter(defaultCenter)
        }
    }

    val spaceMarkersRef = remember { mutableListOf<Marker>() }
    var userMarker by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(mapView, lifecycleOwner) {
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                activePinSpace = null
                onSpaceSelected(null)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(0, eventsOverlay)

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

    LaunchedEffect(userLocation) {
        userLocation?.let { uLoc ->
            mapView.controller.setZoom(13.0)
            mapView.controller.animateTo(uLoc)
        }
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds()
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                spaceMarkersRef.forEach { view.overlays.remove(it) }
                spaceMarkersRef.clear()

                spaces.forEach { space ->
                    val isSel = activePinSpace?.id == space.id
                    val tint = when {
                        isSel -> MARKER_COLOR_SELECTED
                        space.isActiveSubscription -> MARKER_COLOR_ACTIVE_SUBSCRIPTION
                        else -> MARKER_COLOR_DEFAULT
                    }
                    val marker = Marker(view).apply {
                        position = GeoPoint(space.lat, space.lng)
                        title = space.title
                        snippet = "$${space.baseMonthlyRateUsd.toInt()}/mo • ${space.spaceType.displayName}"
                        icon = tintedMarkerIcon(context, tint)
                        setOnMarkerClickListener { _, _ ->
                            activePinSpace = space
                            onSpaceSelected(space)
                            true
                        }
                    }
                    view.overlays.add(marker)
                    spaceMarkersRef.add(marker)
                }

                val uLoc = userLocation
                if (uLoc != null) {
                    val marker = userMarker ?: Marker(view).also {
                        userMarker = it
                        view.overlays.add(it)
                    }
                    marker.position = uLoc
                    marker.title = "Your Location"
                    marker.snippet = "Finding nearest spaces..."
                    marker.icon = tintedMarkerIcon(context, MARKER_COLOR_USER)
                } else {
                    userMarker?.let { view.overlays.remove(it) }
                    userMarker = null
                }

                view.invalidate()
            }
        )

        // TOP INTERACTIVE OVERLAY CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = Spacing.md)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp)
                    )

                    val coordinatesText = if (userLocation != null) {
                        val distanceMsg = if (sortedSpaces.isNotEmpty()) {
                            val closest = sortedSpaces.first()
                            val dist = calculateDistanceKm(userLocation!!.latitude, userLocation!!.longitude, closest.lat, closest.lng)
                            "• Nearest: ${String.format(Locale.US, "%.1f", dist)}km"
                        } else ""
                        "GPS Locked: ${String.format(Locale.US, "%.4f", userLocation!!.latitude)}, ${String.format(Locale.US, "%.4f", userLocation!!.longitude)} $distanceMsg"
                    } else {
                        "Lebanon Workspace Map (${spaces.size} active)"
                    }

                    Text(
                        text = coordinatesText,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Joystick Gear Stick Navigation Controller (Bottom Left)
        MapJoystickNavigator(
            onPan = { dx, dy ->
                val center = mapView.mapCenter
                val curLat = center.latitude
                val curLng = center.longitude
                val zoomLevel = mapView.zoomLevelDouble.coerceAtLeast(1.0)
                val panStep = 0.00018 * (15.0 / zoomLevel)
                val newLat = curLat - (dy * panStep)
                val newLng = curLng + (dx * panStep)
                mapView.controller.setCenter(GeoPoint(newLat, newLng))
                mapView.invalidate()
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (activePinSpace != null) 340.dp else 24.dp)
        )

        // FLOATING HIGH-ACCURACY GPS CONTROLLER (Bottom Right)
        FloatingActionButton(
            onClick = {
                val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
                    requestHighAccuracyLocation()
                } else {
                    requestPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (activePinSpace != null) 340.dp else 24.dp)
                .shadow(8.dp, CircleShape),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = "High-Accuracy GPS Locate")
            }
        }

        // Required by OpenStreetMap's tile-usage policy
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        ) {
            Text(
                "© OpenStreetMap contributors",
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        // FLOATING SELECTED WORKSPACE CARD PREVIEW (Slide-up menu)
        AnimatedVisibility(
            visible = activePinSpace != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.lg)
        ) {
            activePinSpace?.let { space ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, MaterialTheme.shapes.large)
                        .clickable { onNavigateToDetails(space) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = space.spaceType.displayName,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$${space.baseMonthlyRateUsd.toInt()}",
                                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " /mo",
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        userLocation?.let { uLoc ->
                            val dist = calculateDistanceKm(uLoc.latitude, uLoc.longitude, space.lat, space.lng)
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "📍 Near You (${String.format(Locale.US, "%.1f", dist)} km away)",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = space.title,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )

                        Text(
                            text = "📍 ${space.district}, ${space.governorate.displayName}",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            space.essentialFacilities.take(2).forEach { facility ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = facility,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { activePinSpace = null },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Close")
                            }

                            Button(
                                onClick = { onNavigateToDetails(space) },
                                modifier = Modifier.weight(2f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Space")
                            }
                        }
                    }
                }
            }
        }
    }
}
