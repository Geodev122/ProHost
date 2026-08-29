package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.Governorate
import com.example.data.model.SpaceListing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
    return mapView
}

@Composable
fun LebanonMapCanvas(
    spaces: List<SpaceListing>,
    selectedSpace: SpaceListing?,
    onSpaceSelected: (SpaceListing?) -> Unit,
    onNavigateToDetails: (SpaceListing) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Pure Google Maps integration
    
    // GPS Proximity coordinates
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var sortedSpaces by remember { mutableStateOf(spaces) }
    var isLocating by remember { mutableStateOf(false) }
    
    // Fused Location Provider client
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    // Request location permissions launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            isLocating = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    isLocating = false
                    if (location != null) {
                        userLocation = LatLng(location.latitude, location.longitude)
                        Toast.makeText(context, "Location updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Fallback to Beirut centre if GPS signal is mock/unavailable in container
                        userLocation = LatLng(33.8886, 35.5184)
                        Toast.makeText(context, "GPS active. Position locked on Beirut.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SecurityException) {
                isLocating = false
            }
        } else {
            Toast.makeText(context, "Location permission declined. Nearby features unavailable.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Distance calculator helper
    fun calculateDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
    
    // Sort listings automatically whenever spaces or user coordinates change
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

    var activePinSpace by remember { mutableStateOf<SpaceListing?>(selectedSpace) }

    LaunchedEffect(selectedSpace) {
        activePinSpace = selectedSpace
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0))
    ) {
        // NATIVE INTERACTIVE GOOGLE MAPS INTEGRATION ONLY
        val mapView = rememberMapViewWithLifecycle()
        
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        ) { mapV ->
            mapV.getMapAsync { googleMap ->
                googleMap.clear()
                
                // Map configurations
                googleMap.uiSettings.isZoomControlsEnabled = true
                googleMap.uiSettings.isMyLocationButtonEnabled = true
                googleMap.uiSettings.isMapToolbarEnabled = true
                
                // Add markers for all active workspace spaces
                spaces.forEach { space ->
                    val pos = LatLng(space.lat, space.lng)
                    val isSel = activePinSpace?.id == space.id
                    
                    val markerOptions = MarkerOptions()
                        .position(pos)
                        .title(space.title)
                        .snippet("$${space.baseMonthlyRateUsd.toInt()}/mo • ${space.spaceType.displayName}")
                    
                    // Highlight active/selected space pin with customized color
                    if (isSel) {
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    } else if (space.isActiveSubscription) {
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    } else {
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                    }
                    
                    val marker = googleMap.addMarker(markerOptions)
                    marker?.tag = space
                }
                
                // Add blue marker for user's GPS local area lock
                userLocation?.let { uLoc ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(uLoc)
                            .title("Your Location")
                            .snippet("Finding nearest spaces...")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    
                    // Centring camera on user's location
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(uLoc, 11f))
                } ?: run {
                    // Default focus framing the whole Lebanon region
                    val lebanonCenter = LatLng(33.8886, 35.5184)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lebanonCenter, 9.0f))
                }
                
                // Selection handling
                googleMap.setOnMarkerClickListener { marker ->
                    val space = marker.tag as? SpaceListing
                    if (space != null) {
                        activePinSpace = space
                        onSpaceSelected(space)
                    }
                    false
                }
                
                googleMap.setOnMapClickListener {
                    activePinSpace = null
                    onSpaceSelected(null)
                }
            }
        }

        // TOP INTERACTIVE OVERLAY CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Current Coordinates & Info HUD
            Surface(
                color = Color(0xEE1E293B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF06B6D4),
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
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // FLOATING GPS SEARCH CONTROLLER (Bottom Right)
        FloatingActionButton(
            onClick = {
                val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
                    isLocating = true
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                            isLocating = false
                            if (location != null) {
                                userLocation = LatLng(location.latitude, location.longitude)
                                Toast.makeText(context, "Location updated successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                userLocation = LatLng(33.8886, 35.5184)
                                Toast.makeText(context, "GPS locked on Beirut center", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: SecurityException) {
                        isLocating = false
                    }
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
                .padding(end = 16.dp, bottom = if (activePinSpace != null) 340.dp else 100.dp)
                .shadow(8.dp, CircleShape),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = "Find Spaces Near Me")
            }
        }

        // FLOATING SELECTED WORKSPACE CARD PREVIEW (Slide-up menu)
        AnimatedVisibility(
            visible = activePinSpace != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            activePinSpace?.let { space ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clickable { onNavigateToDetails(space) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = space.spaceType.displayName,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$${space.baseMonthlyRateUsd.toInt()}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " /mo",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Distance badge if GPS is loaded
                        userLocation?.let { uLoc ->
                            val dist = calculateDistanceKm(uLoc.latitude, uLoc.longitude, space.lat, space.lng)
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "📍 Near You (${String.format(Locale.US, "%.1f", dist)} km away)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = space.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )

                        Text(
                            text = "📍 ${space.district}, ${space.governorate.displayName}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fast Highlights Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            space.essentialFacilities.take(2).forEach { facility ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = facility,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { activePinSpace = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Close")
                            }

                            Button(
                                onClick = { onNavigateToDetails(space) },
                                modifier = Modifier.weight(2f),
                                shape = RoundedCornerShape(8.dp)
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
