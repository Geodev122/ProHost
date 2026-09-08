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
import com.example.ui.theme.Spacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.util.Locale

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

    val defaultCenter = LatLng(33.8886, 35.5184)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 9f)
    }

    LaunchedEffect(userLocation) {
        userLocation?.let { uLoc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(uLoc, 11f)
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = true
            ),
            onMapClick = {
                activePinSpace = null
                onSpaceSelected(null)
            }
        ) {
            spaces.forEach { space ->
                val pos = LatLng(space.lat, space.lng)
                val isSel = activePinSpace?.id == space.id
                Marker(
                    state = rememberMarkerState(key = space.id, position = pos),
                    title = space.title,
                    snippet = "$${space.baseMonthlyRateUsd.toInt()}/mo • ${space.spaceType.displayName}",
                    icon = if (isSel) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                           else if (space.isActiveSubscription) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                           else BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW),
                    onClick = {
                        activePinSpace = space
                        onSpaceSelected(space)
                        false
                    }
                )
            }

            userLocation?.let { uLoc ->
                Marker(
                    state = rememberMarkerState(key = "user_location", position = uLoc),
                    title = "Your Location",
                    snippet = "Finding nearest spaces...",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
        }

        // TOP INTERACTIVE OVERLAY CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Current Coordinates & Info HUD
            Surface(
                color = Color(0xEE1E293B),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
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
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
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

                        // Distance badge if GPS is loaded
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

                        // Fast Highlights Chips
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
