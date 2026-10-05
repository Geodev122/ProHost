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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.SchemaItem
import com.example.data.model.SpaceListing
import com.example.data.model.SpaceType
import com.example.data.model.Subdivision
import com.example.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.google.android.gms.maps.CameraUpdateFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.example.util.PhoneCountryDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

private data class MarkerPalette(val topColor: Int, val baseColor: Int)

// Hard cap on pins drawn at once; each marker is a native Maps SDK object.
private const val MAX_MAP_PINS = 200
private const val MAX_CLUSTER_LABEL = 99

// A fixed rotation of palettes assigned deterministically by spaceCategoryId (via a
// stable hash) so every admin-defined category — old or new, without the app ever
// needing a code change when an admin adds one — gets a consistent, distinct marker
// color. The legacy SpaceType-keyed palette below is kept only as a fallback for
// listings saved before spaceCategoryId existed (spaceCategoryId == null).
private val SCHEMA_MARKER_PALETTES = listOf(
    MarkerPalette(android.graphics.Color.parseColor("#6E97C4"), android.graphics.Color.parseColor("#2B5A8C")), // Blue
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
    SpaceType.COWORKING_SPACE -> SCHEMA_MARKER_PALETTES[4]
}

private fun getMarkerPaletteFallback(space: SpaceListing): MarkerPalette {
    val categoryId = space.spaceCategoryId
    if (!categoryId.isNullOrBlank()) {
        val index = (categoryId.hashCode() and Int.MAX_VALUE) % SCHEMA_MARKER_PALETTES.size
        return SCHEMA_MARKER_PALETTES[index]
    }
    return legacyMarkerPalette(space.spaceType)
}

private fun getMarkerPalette(space: SpaceListing, isSelected: Boolean, schema: List<SchemaItem> = emptyList()): MarkerPalette {
    if (isSelected) {
        return MarkerPalette(android.graphics.Color.parseColor("#FFE082"), android.graphics.Color.parseColor("#FFB300"))
    }
    val schemaItem = schema.firstOrNull { item ->
        (!space.spaceCategoryId.isNullOrBlank() && item.id == space.spaceCategoryId) ||
        item.name.equals(space.spaceType.displayName, ignoreCase = true)
    }
    val hexColor = schemaItem?.markerColor
    if (!hexColor.isNullOrBlank()) {
        return try {
            val color = android.graphics.Color.parseColor(hexColor)
            val darkened = android.graphics.Color.argb(
                255,
                (android.graphics.Color.red(color) * 0.72).toInt(),
                (android.graphics.Color.green(color) * 0.72).toInt(),
                (android.graphics.Color.blue(color) * 0.72).toInt()
            )
            MarkerPalette(color, darkened)
        } catch (e: Exception) {
            getMarkerPaletteFallback(space)
        }
    }
    return getMarkerPaletteFallback(space)
}

private fun createCustomMarker(context: Context, palette: MarkerPalette, isSelected: Boolean): BitmapDescriptor {
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

private fun createClusterMarker(context: Context, count: Int, hasSelected: Boolean): BitmapDescriptor {
    val scale = context.resources.displayMetrics.density
    val size = (52 * scale).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val radius = cx - (4 * scale)

    val bgColor = if (hasSelected)
        android.graphics.Color.parseColor("#FFB300")
    else
        android.graphics.Color.parseColor("#2B5A8C")
    val borderColor = android.graphics.Color.WHITE

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bgColor
    }
    canvas.drawCircle(cx, cy, radius, paint)

    paint.style = Paint.Style.STROKE
    paint.color = borderColor
    paint.strokeWidth = 3 * scale
    canvas.drawCircle(cx, cy, radius, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 15 * scale
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(if (count > MAX_CLUSTER_LABEL) "$MAX_CLUSTER_LABEL+" else count.toString(), cx, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
fun LebanonMapCanvas(
    spaces: List<SpaceListing>,
    onSpaceSelected: (SpaceListing?) -> Unit,
    onNavigateToDetails: (SpaceListing) -> Unit,
    modifier: Modifier = Modifier,
    spaceTypeSchema: List<SchemaItem> = emptyList(),
    // Fired only when the user taps a specific division/subdivision card in the
    // bottom strip — the one case where a tap should navigate straight to the
    // listing page instead of just updating the local marker preview (see
    // onSpaceSelected's doc note above the LazyRow card below).
    onDivisionSelected: (SpaceListing, String) -> Unit = { _, _ -> },
    onCenterCountryDetected: (String) -> Unit = {},
    // "Search this area" tapped: lets Explore load another page of listings when the
    // loaded page may not cover the new area.
    onSearchArea: () -> Unit = {},
    // Restores the camera after a round-trip to a listing; [onCameraSaved] reports it on leave.
    initialCamera: CameraPosition? = null,
    onCameraSaved: (CameraPosition) -> Unit = {},
    // Receives the "Search this area" pill to place under the controls.
    topControls: (@Composable BoxScope.(searchAreaPill: @Composable () -> Unit) -> Unit)? = null,
    // Space the app's floating bottom navigation takes; the carousel and locate button sit above it.
    bottomInset: Dp = 0.dp,
    // The person panned or zoomed by hand (Explore folds the bottom navigation away).
    onUserGesture: () -> Unit = {}
) {
    val currentOnUserGesture by rememberUpdatedState(onUserGesture)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var activePinSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var isStripCollapsed by remember { mutableStateOf(false) }
    
    val defaultCenter = LatLng(33.8886, 35.5184) // Beirut
    val cameraPositionState = rememberCameraPositionState {
        position = initialCamera ?: CameraPosition.fromLatLngZoom(defaultCenter, 10f)
    }
    val latestOnCameraSaved by rememberUpdatedState(onCameraSaved)
    DisposableEffect(cameraPositionState) {
        onDispose { latestOnCameraSaved(cameraPositionState.position) }
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

    // Recomputed only when the camera settles (and once the map has loaded).
    // Keying this on cameraPositionState.position re-ran filtering, sorting and the
    // carousel list on every animation frame while panning.
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    // The area the pins and carousel show. It follows the camera only when the person taps
    // "Search this area" (after panning or zooming by hand), so swiping the carousel or a
    // small nudge never reshuffles the results.
    var searchedBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    var movedByGesture by remember { mutableStateOf(false) }
    var detectedCenterCountry by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving) {
            if (cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
                movedByGesture = true
                currentOnUserGesture()
            }
        } else {
            val center = cameraPositionState.position.target
            cameraPositionState.projection?.visibleRegion?.latLngBounds?.let {
                visibleBounds = it
                if (searchedBounds == null) searchedBounds = it
            }
            val country = PhoneCountryDetector.detectCountryAtCoordinates(context, center.latitude, center.longitude)
            if (!country.isNullOrBlank() && country != detectedCenterCountry) {
                detectedCenterCountry = country
                onCenterCountryDetected(country)
            }
        }
    }
    val showSearchArea = movedByGesture && visibleBounds.let { current ->
        current != null && searchedBounds.let { it == null || boundsDiffer(it, current) }
    }
    val visibleSpaces = remember(spaces, searchedBounds) {
        val bounds = searchedBounds
        // contains() handles viewports that cross the antimeridian.
        val inView = if (bounds != null) spaces.filter { bounds.contains(LatLng(it.lat, it.lng)) } else emptyList()
        inView.ifEmpty { spaces }.take(MAX_MAP_PINS)
    }

    val sortedSpaces = remember(visibleSpaces, userLocation) {
        val userLoc = userLocation
        if (userLoc == null) visibleSpaces
        else visibleSpaces.sortedBy { calculateDistanceKm(userLoc.latitude, userLoc.longitude, it.lat, it.lng) }
    }

    // Flat list: one card per subdivision (or one space card if no subdivisions)
    val divisionCards = remember(sortedSpaces) {
        sortedSpaces.flatMap { space ->
            if (space.subdivisions.isNotEmpty()) {
                space.subdivisions.map { sub -> space to sub }
            } else {
                listOf(space to null)
            }
        }
    }

    // Carousel swipe → select that listing's pin. Reacts only to a finished scroll,
    // never to the list changing underneath it: re-running on every list change
    // moved the camera, which changed the list again (a camera/carousel loop).
    val currentCards by rememberUpdatedState(divisionCards)
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .collect { isScrolling ->
                if (isScrolling) return@collect
                val space = currentCards.getOrNull(listState.firstVisibleItemIndex)?.first
                if (space != null && activePinSpace?.id != space.id) {
                    activePinSpace = space
                    // Separate job: a user gesture cancels animate() with a
                    // CancellationException, which must not end this collector.
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLng(LatLng(space.lat - 0.012, space.lng))
                        )
                    }
                }
            }
    }

    // BitmapDescriptors are registered natively and never freed, so build one per
    // distinct look (palette + selection) instead of one per marker per recomposition.
    val markerIconCache = remember { HashMap<Pair<MarkerPalette, Boolean>, BitmapDescriptor>() }
    val clusterIconCache = remember { HashMap<Pair<Int, Boolean>, BitmapDescriptor>() }
    fun markerIconFor(space: SpaceListing, isSelected: Boolean): BitmapDescriptor {
        val palette = getMarkerPalette(space, isSelected, spaceTypeSchema)
        return markerIconCache.getOrPut(palette to isSelected) { createCustomMarker(context, palette, isSelected) }
    }
    fun clusterIconFor(count: Int, hasSelected: Boolean): BitmapDescriptor {
        val label = count.coerceAtMost(MAX_CLUSTER_LABEL + 1)
        return clusterIconCache.getOrPut(label to hasSelected) { createClusterMarker(context, label, hasSelected) }
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds()
    ) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            // Keeps the Google logo (attribution) and the compass clear of Explore's floating
            // controls at the top and the listing strip + bottom navigation below.
            contentPadding = PaddingValues(
                top = if (topControls != null) 112.dp else 0.dp,
                bottom = (if (!isStripCollapsed) 150.dp else 40.dp) + bottomInset
            ),
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
            onMapLoaded = {
                cameraPositionState.projection?.visibleRegion?.latLngBounds?.let {
                    visibleBounds = it
                    if (searchedBounds == null) searchedBounds = it
                }
            },
            onMapClick = {
                activePinSpace = null
                onSpaceSelected(null)
            }
        ) {
            // Group markers at same ~100m location (3 decimal lat/lng precision).
            val markerGroups = remember(visibleSpaces) {
                visibleSpaces.groupBy { space -> (space.lat * 1000).toLong() to (space.lng * 1000).toLong() }
            }

            markerGroups.forEach { (cell, group) ->
                if (group.size == 1) {
                    val space = group.first()
                    key(space.id) {
                        val isSelected = activePinSpace?.id == space.id
                        val markerState = remember(space.id, space.lat, space.lng) {
                            MarkerState(position = LatLng(space.lat, space.lng))
                        }
                        Marker(
                            state = markerState,
                            title = space.title,
                            snippet = space.spaceType.displayName,
                            icon = markerIconFor(space, isSelected),
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f),
                            zIndex = if (isSelected) 2f else 1f,
                            onClick = {
                                activePinSpace = space
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLng(LatLng(space.lat - 0.012, space.lng))
                                    )
                                    val idx = divisionCards.indexOfFirst { it.first.id == space.id }
                                    if (idx >= 0) listState.animateScrollToItem(idx)
                                }
                                true
                            }
                        )
                    }
                } else {
                    // Cluster marker for co-located spaces, keyed by its grid cell.
                    val centLat = group.map { it.lat }.average()
                    val centLng = group.map { it.lng }.average()
                    val hasSelected = group.any { it.id == activePinSpace?.id }
                    key("cluster_${cell.first}_${cell.second}") {
                        val markerState = remember(centLat, centLng) { MarkerState(position = LatLng(centLat, centLng)) }
                        Marker(
                            state = markerState,
                            title = "${group.size} workspaces here",
                            snippet = group.take(3).joinToString(", ") { it.title },
                            icon = clusterIconFor(group.size, hasSelected),
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                            zIndex = if (hasSelected) 3f else 1.5f,
                            onClick = {
                                val representative = group.firstOrNull { it.id == activePinSpace?.id } ?: group.first()
                                activePinSpace = representative
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(centLat - 0.008, centLng), 16f)
                                    )
                                }
                                true
                            }
                        )
                    }
                }
            }
        }

        // FLOATING CENTER COUNTRY BADGE — below Explore's header (which carries the centre
        // logo), never on top of it; hidden while "Search this area" takes that spot.
        if (!detectedCenterCountry.isNullOrBlank() && !(topControls != null && showSearchArea)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (topControls != null) 124.dp else 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Map Center: $detectedCenterCountry",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                .padding(end = 16.dp, bottom = (if (!isStripCollapsed) 135.dp else 24.dp) + bottomInset)
                .shadow(8.dp, CircleShape),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            if (isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = SteelBlue)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = "High-Accuracy GPS Locate")
            }
        }

        // BOTTOM COLLAPSIBLE STRIP (Horizontal Slidable Ultracards with Collapse Toggle)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.10f))
                .padding(bottom = Spacing.sm + bottomInset),
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
                            text = if (isStripCollapsed) {
                                "Show Workspaces (${sortedSpaces.sumOf { if (it.subdivisions.isNotEmpty()) it.subdivisions.size else 1 }})"
                            } else {
                                "Collapse Map List"
                            },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(divisionCards, key = { (space, sub) -> "${space.id}_${sub?.id ?: "whole"}" }) { (space, sub) ->
                        val isSelected = activePinSpace?.id == space.id
                        val cardScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "card_scale"
                        )
                        val typePalette = getMarkerPalette(space, false, spaceTypeSchema)
                        val displayName = sub?.name ?: space.title
                        val typeBadge = sub?.type?.displayName ?: space.spaceType.displayName
                        val lowestPrice = if (sub != null) {
                            com.example.ui.util.SpaceCalculationUtils.findLowestPriceForSubdivision(sub)
                        } else {
                            com.example.ui.util.SpaceCalculationUtils.findLowestConfiguredPrice(space)
                        }

                        Card(
                            modifier = Modifier
                                .width(260.dp)
                                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                                .shadow(if (isSelected) 8.dp else 4.dp, MaterialTheme.shapes.medium)
                                .clickable {
                                    activePinSpace = space
                                    coroutineScope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(space.lat - 0.012, space.lng), 14f))
                                    }
                                    if (sub != null) {
                                        onDivisionSelected(space, sub.id)
                                    } else {
                                        onNavigateToDetails(space)
                                    }
                                },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f)
                                } else {
                                    Color.White.copy(alpha = 0.90f)
                                }
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
                                    // A room card shows the room's own photo (the listing's when it has none).
                                    val thumb = sub?.imageUrls?.firstOrNull { it.isNotBlank() }
                                        ?: space.imageUrls.firstOrNull { it.isNotBlank() }
                                    if (thumb != null) {
                                        coil.compose.AsyncImage(
                                            model = coil.request.ImageRequest.Builder(context).data(thumb).size(200).build(),
                                            contentDescription = displayName,
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
                                                text = typeBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "$${lowestPrice.amount.toInt()}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = lowestPrice.unitLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (sub != null) {
                                        Text(
                                            text = space.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Select choices, and Request",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlay slot for controls that must render above the AndroidView GoogleMap layer.
        topControls?.invoke(this) {
            SearchThisAreaPill(
                visible = showSearchArea,
                onClick = {
                    searchedBounds = visibleBounds
                    onSearchArea()
                    movedByGesture = false
                    activePinSpace = null
                    coroutineScope.launch { runCatching { listState.scrollToItem(0) } }
                    com.example.analytics.AnalyticsTracker.filterApply("search_area", "map")
                }
            )
        }
    }
}

/** True when the camera moved or zoomed enough that the shown results no longer fit it. */
private fun boundsDiffer(a: LatLngBounds, b: LatLngBounds): Boolean {
    val latSpan = (a.northeast.latitude - a.southwest.latitude).coerceAtLeast(1e-6)
    val lngSpanA = ((a.northeast.longitude - a.southwest.longitude + 360.0) % 360.0).coerceAtLeast(1e-6)
    val lngSpanB = ((b.northeast.longitude - b.southwest.longitude + 360.0) % 360.0).coerceAtLeast(1e-6)
    val dLat = kotlin.math.abs(a.center.latitude - b.center.latitude)
    val dLng = kotlin.math.abs(a.center.longitude - b.center.longitude).let { if (it > 180.0) 360.0 - it else it }
    val zoomRatio = lngSpanB / lngSpanA
    return dLat > latSpan * 0.2 || dLng > lngSpanA * 0.2 || zoomRatio > 1.6 || zoomRatio < 0.6
}

/** Warm "Search this area" pill under the Explore controls (map mode). */
@Composable
private fun SearchThisAreaPill(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { -it / 2 }
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Search this area", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
