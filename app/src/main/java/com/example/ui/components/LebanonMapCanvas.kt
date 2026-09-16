package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.SpaceListing
import com.example.data.model.SpaceType
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

private val MARKER_COLOR_CLINIC = android.graphics.Color.parseColor("#E53935") // Red
private val MARKER_COLOR_STUDIO = android.graphics.Color.parseColor("#8E24AA") // Purple
private val MARKER_COLOR_OFFICE = android.graphics.Color.parseColor("#1E88E5") // Blue
private val MARKER_COLOR_DEFAULT = android.graphics.Color.parseColor("#43A047") // Green
private val MARKER_COLOR_SELECTED = android.graphics.Color.parseColor("#FFB300") // Amber

private fun createCustomMarker(context: Context, spaceType: SpaceType, isSelected: Boolean): BitmapDescriptor {
    val size = (46 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Base color by type
    paint.color = if (isSelected) {
        MARKER_COLOR_SELECTED
    } else {
        when (spaceType) {
            SpaceType.POLYCLINIC -> MARKER_COLOR_CLINIC
            SpaceType.CENTER -> MARKER_COLOR_STUDIO
            SpaceType.PRIVATE_OFFICE, SpaceType.COWORKING_SPACE -> MARKER_COLOR_OFFICE
            else -> MARKER_COLOR_DEFAULT
        }
    }
    
    // Draw outer pin circle
    canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
    
    // Draw inner white background circle
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 3.0f, paint)

    // Draw Tick Mark (Logo checkmark)
    paint.color = if (isSelected) MARKER_COLOR_SELECTED else MARKER_COLOR_DEFAULT
    paint.strokeWidth = 3.5f * context.resources.displayMetrics.density
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    
    val path = android.graphics.Path().apply {
        moveTo(size * 0.35f, size * 0.52f)
        lineTo(size * 0.46f, size * 0.62f)
        lineTo(size * 0.68f, size * 0.38f)
    }
    canvas.drawPath(path, paint)

    // Secondary overlay dot for type distinction
    paint.style = Paint.Style.FILL
    paint.color = paint.color
    canvas.drawCircle(size * 0.75f, size * 0.25f, size * 0.15f, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
fun LebanonMapCanvas(
    spaces: List<SpaceListing>,
    onSpaceSelected: (SpaceListing?) -> Unit,
    onNavigateToDetails: (SpaceListing) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var sortedSpaces by remember { mutableStateOf(spaces) }
    var isLocating by remember { mutableStateOf(false) }
    var activePinSpace by remember { mutableStateOf<SpaceListing?>(spaces.firstOrNull()) }
    var showSearchThisArea by remember { mutableStateOf(false) }
    
    val defaultCenter = LatLng(33.8886, 35.5184) // Beirut
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 10f)
    }

    // Trigger "Search this area" when map moves
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            showSearchThisArea = true
        }
    }

    val listState = rememberLazyListState()

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
                        val latLng = LatLng(location.latitude, location.longitude)
                        userLocation = latLng
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                        }
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                val latLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                userLocation = latLng
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                                }
                            } else {
                                Toast.makeText(context, "Cannot determine location.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Location permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    fun calculateDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // Viewport Culling: filter spaces visible within current camera bounds or radius
    val visibleSpaces = remember(spaces, cameraPositionState.position) {
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        if (bounds != null) {
            spaces.filter { space ->
                space.lat in bounds.southwest.latitude..bounds.northeast.latitude &&
                space.lng in bounds.southwest.longitude..bounds.northeast.longitude
            }.ifEmpty { spaces }
        } else {
            spaces
        }
    }

    LaunchedEffect(visibleSpaces, userLocation) {
        val userLoc = userLocation
        if (userLoc != null) {
            sortedSpaces = visibleSpaces.sortedBy { space ->
                calculateDistanceKm(userLoc.latitude, userLoc.longitude, space.lat, space.lng)
            }
        } else {
            sortedSpaces = visibleSpaces
        }
        if (activePinSpace == null && sortedSpaces.isNotEmpty()) {
            activePinSpace = sortedSpaces.first()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds()
    ) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    
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
                compassEnabled = true,
                mapToolbarEnabled = false
            ),
            onMapClick = {}
        ) {
            visibleSpaces.forEach { space ->
                val isSelected = activePinSpace?.id == space.id
                Marker(
                    state = MarkerState(position = LatLng(space.lat, space.lng)),
                    title = space.title,
                    snippet = "$${space.baseMonthlyRateUsd.toInt()}/mo • ${space.spaceType.displayName}",
                    icon = createCustomMarker(context, space.spaceType, isSelected),
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f), // Bottom corner / tip anchor point
                    zIndex = if (isSelected) 2f else 1f,
                    onClick = {
                        activePinSpace = space
                        onSpaceSelected(space)
                        coroutineScope.launch {
                            val projection = CameraUpdateFactory.newLatLng(LatLng(space.lat - 0.012, space.lng))
                            cameraPositionState.animate(projection)
                        }
                        true
                    }
                )
            }
        }

        // TOP INTERACTIVE OVERLAY CONTROLS ("Search this area" refresh button)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showSearchThisArea) {
                Button(
                    onClick = {
                        showSearchThisArea = false
                        Toast.makeText(context, "Refreshed visible workspaces (${visibleSpaces.size})", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.small,
                    elevation = ButtonDefaults.buttonElevation(4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search this area", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

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
                .padding(end = 16.dp, bottom = 145.dp)
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

        // BOTTOM DYNAMIC COLLAPSIBLE STRIP (Horizontal Slidable Ultracards)
        AnimatedVisibility(
            visible = sortedSpaces.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.md)
        ) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sortedSpaces, key = { it.id }) { space ->
                    val isSelected = activePinSpace?.id == space.id
                    val minPrice = space.baseMonthlyRateUsd
                    val minUnit = "/mo"

                    Card(
                        modifier = Modifier
                            .width(280.dp)
                            .shadow(if (isSelected) 8.dp else 4.dp, MaterialTheme.shapes.medium)
                            .clickable {
                                activePinSpace = space
                                onSpaceSelected(space)
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(space.lat - 0.012, space.lng), 14f))
                                }
                            },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (space.imageUrls.isNotEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = space.imageUrls.first(),
                                        contentDescription = space.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = space.spaceType.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (space.isVerified) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Verified",
                                                tint = LebaneseCedarGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "$${minPrice.toInt()}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = minUnit,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = space.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { onNavigateToDetails(space) },
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Check", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
