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
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

private data class MarkerPalette(val topColor: Int, val baseColor: Int)

// A fixed rotation of palettes assigned deterministically by spaceCategoryId (via a
// stable hash) so every admin-defined category — old or new, without the app ever
// needing a code change when an admin adds one — gets a consistent, distinct marker
// color. The legacy SpaceType-keyed palette below is kept only as a fallback for
// listings saved before spaceCategoryId existed (spaceCategoryId == null).
private val SCHEMA_MARKER_PALETTES = listOf(
    MarkerPalette(android.graphics.Color.parseColor("#5B9BFF"), android.graphics.Color.parseColor("#246BEE")), // Blue
    MarkerPalette(android.graphics.Color.parseColor("#FF8F73"), android.graphics.Color.parseColor("#F25F4C")), // Orange
    MarkerPalette(android.graphics.Color.parseColor("#7DD9A0"), android.graphics.Color.parseColor("#4CAF72")), // Green
    MarkerPalette(android.graphics.Color.parseColor("#B197FC"), android.graphics.Color.parseColor("#8B5CF6")), // Violet
    MarkerPalette(android.graphics.Color.parseColor("#E8C468"), android.graphics.Color.parseColor("#C99A2E")), // Gold
    MarkerPalette(android.graphics.Color.parseColor("#6FE3E3"), android.graphics.Color.parseColor("#2FB6B6")), // Teal
)

private fun legacyMarkerPalette(spaceType: SpaceType): MarkerPalette = when (spaceType) {
    SpaceType.PRIVATE_OFFICE -> SCHEMA_MARKER_PALETTES[0]
    SpaceType.CENTER -> SCHEMA_MARKER_PALETTES[1]
    SpaceType.POLYCLINIC -> SCHEMA_MARKER_PALETTES[2]
    SpaceType.COWORKING_SPACE -> SCHEMA_MARKER_PALETTES[3]
    else -> SCHEMA_MARKER_PALETTES[4]
}

private fun getMarkerPalette(space: SpaceListing, isSelected: Boolean): MarkerPalette {
    if (isSelected) {
        return MarkerPalette(android.graphics.Color.parseColor("#FFE082"), android.graphics.Color.parseColor("#FFB300"))
    }
    val categoryId = space.spaceCategoryId
    if (!categoryId.isNullOrBlank()) {
        val index = (categoryId.hashCode() and Int.MAX_VALUE) % SCHEMA_MARKER_PALETTES.size
        return SCHEMA_MARKER_PALETTES[index]
    }
    return legacyMarkerPalette(space.spaceType)
}

private fun createCustomMarker(context: Context, space: SpaceListing, isSelected: Boolean): BitmapDescriptor {
    val scale = context.resources.displayMetrics.density
    val pinScale = if (isSelected) 1.25f else 1.0f
    val width = (36 * scale * pinScale).toInt()
    val height = (54 * scale * pinScale).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Scale canvas to match SVG 200x300 viewBox
    canvas.scale(width / 200f, height / 300f)

    // Ground shadow — drawn first so the pin renders on top
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(55, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawOval(android.graphics.RectF(65f, 248f, 135f, 265f), shadowPaint)

    val palette = getMarkerPalette(space, isSelected)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Teardrop path: M 30.72,135 A 80,80 0 1 1 169.28,135 L 100,255 Z
    val path = android.graphics.Path().apply {
        moveTo(30.72f, 135f)
        arcTo(android.graphics.RectF(20f, 15f, 180f, 175f), 180f, 180f, false)
        lineTo(100f, 255f)
        close()
    }

    // Top-to-bottom linear gradient
    paint.shader = android.graphics.LinearGradient(
        100f, 15f, 100f, 255f,
        palette.topColor, palette.baseColor,
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawPath(path, paint)

    // Upper-left gloss highlight
    val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.RadialGradient(
            76f, 70f, 45f,
            android.graphics.Color.WHITE,
            android.graphics.Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP
        )
        alpha = 130
    }
    canvas.drawPath(path, glossPaint)

    // White center circle (hole-punch accent)
    paint.shader = null
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(100f, 95f, 30f, paint)

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
    var activePinSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var isStripCollapsed by remember { mutableStateOf(false) }
    val arrivedPinIds = remember { mutableStateSetOf<String>() }
    
    val defaultCenter = LatLng(33.8886, 35.5184) // Beirut
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 10f)
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

    // Viewport Culling
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

    // Stagger-drop each visible pin when the set of visible spaces changes.
    LaunchedEffect(visibleSpaces) {
        arrivedPinIds.clear()
        visibleSpaces.forEachIndexed { index, space ->
            launch {
                delay(index * 40L)
                arrivedPinIds.add(space.id)
            }
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
            onMapClick = {
                activePinSpace = null
                onSpaceSelected(null)
            }
        ) {
            visibleSpaces.forEach { space ->
                key(space.id) {
                    val isSelected = activePinSpace?.id == space.id
                    val pinAlpha by animateFloatAsState(
                        targetValue = if (space.id in arrivedPinIds) 1f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "pin_alpha"
                    )
                    Marker(
                        state = MarkerState(position = LatLng(space.lat, space.lng)),
                        title = space.title,
                        snippet = "$${space.baseMonthlyRateUsd.toInt()}/mo • ${space.spaceType.displayName}",
                        icon = createCustomMarker(context, space, isSelected),
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
                        alpha = pinAlpha,
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
                .padding(end = 16.dp, bottom = if (activePinSpace != null || !isStripCollapsed) 135.dp else 24.dp)
                .shadow(8.dp, CircleShape),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VibrantBlue)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = "High-Accuracy GPS Locate")
            }
        }

        // MARKER ULTRA CARD (Detailed card when a specific marker is pressed)
        AnimatedVisibility(
            visible = activePinSpace != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            activePinSpace?.let { space ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, MaterialTheme.shapes.large),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = space.spaceType.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (space.isVerified) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = LebaneseCedarGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { activePinSpace = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = space.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "📍 ${space.district}, ${space.governorate.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$${lowestPrice.amount.toInt()}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = " ${lowestPrice.unitLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val cleanPhone = space.ownerPhone.filter { it.isDigit() }.let { if (it.length in 7..8) "961$it" else it }
                                        val url = "https://wa.me/$cleanPhone"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not launch WhatsApp", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Contact", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onNavigateToDetails(space) },
                                modifier = Modifier.weight(1.5f),
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM COLLAPSIBLE STRIP (Horizontal Slidable Ultracards with Collapse Toggle)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.10f))
                .padding(bottom = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Collapse / Expand Pill Toggle
            if (sortedSpaces.isNotEmpty()) {
                Surface(
                    onClick = { isStripCollapsed = !isStripCollapsed },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isStripCollapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isStripCollapsed) "Show Workspaces (${sortedSpaces.size})" else "Collapse Map List",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isStripCollapsed && sortedSpaces.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sortedSpaces, key = { it.id }) { space ->
                        val isSelected = activePinSpace?.id == space.id
                        val lowestPrice = com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)
                        val minPrice = lowestPrice.amount
                        val minUnit = lowestPrice.unitLabel

                        val typePalette = getMarkerPalette(space, false)
                        Card(
                            modifier = Modifier
                                .width(260.dp)
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
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.90f)
                            ),
                            border = if (isSelected) BorderStroke(2.dp, Color(typePalette.baseColor)) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
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
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = space.spaceType.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
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
                            }
                        }
                    }
                }
            }
        }
    }
}
