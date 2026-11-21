package com.example.ui.components

import com.example.ui.util.localizedLabel
import com.example.ui.theme.Elevation
import com.example.ui.util.UiMessages
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SchemaItem
import com.example.data.model.SpaceListing
import com.example.data.model.SpaceType
import com.example.data.model.Subdivision
import com.example.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.example.util.LocateResult
import com.example.util.GeoPoint
import com.example.util.CountryLookup
import com.example.ui.map.GeoBounds
import com.example.ui.map.MapIcon
import com.example.ui.map.MapMarkerSpec
import com.example.ui.map.PlatformMap
import com.example.ui.map.rememberPlatformMapState
import com.example.ui.map.parseColorOrNull
import com.example.ui.map.parseHexColorOrNull
import com.example.ui.map.argb
import com.example.ui.map.redOf
import com.example.ui.map.greenOf
import com.example.ui.map.blueOf
import com.example.util.MapCamera
import com.example.util.LocationProvider
import com.example.util.locate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import com.example.shared.resources.Res
import com.example.shared.resources.high_accuracy_gps_locate
import com.example.shared.resources.map_center_x
import com.example.shared.resources.search_this_area
import com.example.shared.resources.select_choices_and_request
import com.example.ui.util.appString
import com.example.ui.util.sharedText

private data class MarkerPalette(val topColor: Int, val baseColor: Int)

/** ARGB of a constant "#RRGGBB" (what Color.parseColor returned for these). */
private fun hexArgb(hex: String): Int = parseHexColorOrNull(hex) ?: error("bad colour constant $hex")

// Hard cap on pins drawn at once; each marker is a native Maps SDK object.
private const val MAX_MAP_PINS = 200
private const val MAX_CLUSTER_LABEL = 99

// Math.toRadians(x) = x * (PI / 180), the same double.
private const val DEG_TO_RAD = kotlin.math.PI / 180.0

// A fixed rotation of palettes assigned deterministically by spaceCategoryId (via a
// stable hash) so every admin-defined category — old or new, without the app ever
// needing a code change when an admin adds one — gets a consistent, distinct marker
// color. The legacy SpaceType-keyed palette below is kept only as a fallback for
// listings saved before spaceCategoryId existed (spaceCategoryId == null).
private val SCHEMA_MARKER_PALETTES = listOf(
    MarkerPalette(hexArgb("#6E97C4"), hexArgb("#2B5A8C")), // Blue
    MarkerPalette(hexArgb("#FF8F73"), hexArgb("#F25F4C")), // Orange
    MarkerPalette(hexArgb("#7DD9A0"), hexArgb("#4CAF72")), // Green
    MarkerPalette(hexArgb("#B197FC"), hexArgb("#8B5CF6")), // Violet
    MarkerPalette(hexArgb("#E8C468"), hexArgb("#C99A2E")), // Gold
    MarkerPalette(hexArgb("#6FE3E3"), hexArgb("#2FB6B6")), // Teal
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
        return MarkerPalette(hexArgb("#FFE082"), hexArgb("#FFB300"))
    }
    val schemaItem = schema.firstOrNull { item ->
        (!space.spaceCategoryId.isNullOrBlank() && item.id == space.spaceCategoryId) ||
        item.name.equals(space.spaceType.displayName, ignoreCase = true)
    }
    val hexColor = schemaItem?.markerColor
    if (!hexColor.isNullOrBlank()) {
        val color = parseColorOrNull(hexColor) ?: return getMarkerPaletteFallback(space)
        val darkened = argb(
            255,
            (redOf(color) * 0.72).toInt(),
            (greenOf(color) * 0.72).toInt(),
            (blueOf(color) * 0.72).toInt()
        )
        return MarkerPalette(color, darkened)
    }
    return getMarkerPaletteFallback(space)
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
    initialCamera: MapCamera? = null,
    onCameraSaved: (MapCamera) -> Unit = {},
    // Receives the "Search this area" pill to place under the controls.
    topControls: (@Composable BoxScope.(searchAreaPill: @Composable () -> Unit) -> Unit)? = null,
    // Space the app's floating bottom navigation takes; the carousel and locate button sit above it.
    bottomInset: Dp = 0.dp,
    // The person panned or zoomed by hand (Explore folds the bottom navigation away).
    onUserGesture: () -> Unit = {}
) {
    val currentOnUserGesture by rememberUpdatedState(onUserGesture)
    val coroutineScope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var activePinSpace by remember { mutableStateOf<SpaceListing?>(null) }
    var isStripCollapsed by remember { mutableStateOf(false) }
    
    val defaultCenter = GeoPoint(33.8886, 35.5184) // Beirut
    val mapState = rememberPlatformMapState(initialCamera ?: MapCamera(defaultCenter, 10f))
    val latestOnCameraSaved by rememberUpdatedState(onCameraSaved)
    DisposableEffect(mapState) {
        onDispose { latestOnCameraSaved(mapState.camera) }
    }

    val listState = rememberLazyListState()

    fun requestHighAccuracyLocation() {
        val provider = LocationProvider.require()
        if (provider.hasPermission()) {
            isLocating = true
            coroutineScope.launch {
                when (val result = locate(provider, fallbackToLast = true, onFreshFixDone = { isLocating = false })) {
                    is LocateResult.Found -> {
                        val point = GeoPoint(result.point.latitude, result.point.longitude)
                        userLocation = point
                        mapState.animateTo(point, 14f)
                    }
                    LocateResult.NoFix -> UiMessages.show(sharedText("cannot_determine_location"))
                    LocateResult.Unavailable -> UiMessages.show(sharedText("gps_signal_unavailable"))
                    LocateResult.Silent -> Unit
                }
            }
        }
    }

    // Permission, blocked-permission and location-off prompts are all native (PermissionPrompts.kt).
    val requestLocation = rememberLocationAccess { requestHighAccuracyLocation() }

    fun calculateDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLng = (lng2 - lng1) * DEG_TO_RAD
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * DEG_TO_RAD) * cos(lat2 * DEG_TO_RAD) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // Recomputed only when the camera settles (and once the map has loaded).
    // Keying this on cameraPositionState.position re-ran filtering, sorting and the
    // carousel list on every animation frame while panning.
    var visibleBounds by remember { mutableStateOf<GeoBounds?>(null) }
    // The area the pins and carousel show. It follows the camera only when the person taps
    // "Search this area" (after panning or zooming by hand), so swiping the carousel or a
    // small nudge never reshuffles the results.
    var searchedBounds by remember { mutableStateOf<GeoBounds?>(null) }
    var movedByGesture by remember { mutableStateOf(false) }
    var detectedCenterCountry by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mapState.isMoving) {
        if (mapState.isMoving) {
            if (mapState.movedByGesture) {
                movedByGesture = true
                currentOnUserGesture()
            }
        } else {
            val center = mapState.camera.target
            mapState.visibleBounds?.let {
                visibleBounds = it
                if (searchedBounds == null) searchedBounds = it
            }
            val country = CountryLookup.atPoint(center.latitude, center.longitude)
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
        val inView = if (bounds != null) spaces.filter { bounds.contains(GeoPoint(it.lat, it.lng)) } else emptyList()
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
                        mapState.animateTo(GeoPoint(space.lat - 0.012, space.lng))
                    }
                }
            }
    }

    // Group markers at same ~100m location (3 decimal lat/lng precision).
    val markerGroups = remember(visibleSpaces) {
        visibleSpaces.groupBy { space -> (space.lat * 1000).toLong() to (space.lng * 1000).toLong() }
    }
    val markers = markerGroups.map { (cell, group) ->
        if (group.size == 1) {
            val space = group.first()
            val isSelected = activePinSpace?.id == space.id
            val palette = getMarkerPalette(space, isSelected, spaceTypeSchema)
            MapMarkerSpec(
                key = space.id,
                position = GeoPoint(space.lat, space.lng),
                title = space.title,
                snippet = localizedLabel(space.spaceType.displayName),
                icon = MapIcon.Pin(palette.topColor, palette.baseColor, isSelected),
                anchorX = 0.5f,
                anchorY = 1.0f,
                zIndex = if (isSelected) 2f else 1f,
                onClick = {
                    activePinSpace = space
                    coroutineScope.launch {
                        mapState.animateTo(GeoPoint(space.lat - 0.012, space.lng))
                        val idx = divisionCards.indexOfFirst { it.first.id == space.id }
                        if (idx >= 0) listState.animateScrollToItem(idx)
                    }
                    true
                }
            )
        } else {
            // Cluster marker for co-located spaces, keyed by its grid cell.
            val centLat = group.map { it.lat }.average()
            val centLng = group.map { it.lng }.average()
            val hasSelected = group.any { it.id == activePinSpace?.id }
            MapMarkerSpec(
                key = "cluster_${cell.first}_${cell.second}",
                position = GeoPoint(centLat, centLng),
                title = appString(Res.string.x_workspaces_here, group.size),
                snippet = group.take(3).joinToString(", ") { it.title },
                icon = MapIcon.Cluster(group.size.coerceAtMost(MAX_CLUSTER_LABEL + 1), hasSelected),
                anchorX = 0.5f,
                anchorY = 0.5f,
                zIndex = if (hasSelected) 3f else 1.5f,
                onClick = {
                    val representative = group.firstOrNull { it.id == activePinSpace?.id } ?: group.first()
                    activePinSpace = representative
                    coroutineScope.launch {
                        mapState.animateTo(GeoPoint(centLat - 0.008, centLng), 16f)
                    }
                    true
                }
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize().clipToBounds()
    ) {
        val hasLocationPermission = LocationProvider.require().hasPermission()

        PlatformMap(
            state = mapState,
            markers = markers,
            // Keeps the Google logo (attribution) and the compass clear of Explore's floating
            // controls at the top and the listing strip + bottom navigation below.
            contentPadding = PaddingValues(
                top = if (topControls != null) 112.dp else 0.dp,
                bottom = (if (!isStripCollapsed) 150.dp else 40.dp) + bottomInset
            ),
            myLocationEnabled = hasLocationPermission,
            onMapLoaded = {
                mapState.visibleBounds?.let {
                    visibleBounds = it
                    if (searchedBounds == null) searchedBounds = it
                }
            },
            onMapClick = {
                activePinSpace = null
                onSpaceSelected(null)
            },
            modifier = Modifier.fillMaxSize()
        )

        // FLOATING CENTER COUNTRY BADGE — below Explore's header (which carries the centre
        // logo), never on top of it; hidden while "Search this area" takes that spot.
        if (!detectedCenterCountry.isNullOrBlank() && !(topControls != null && showSearchArea)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = Elevation.level3,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (topControls != null) 124.dp else 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = appString(Res.string.map_center_x, detectedCenterCountry.toString()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // FLOATING HIGH-ACCURACY GPS CONTROLLER (Bottom Right)
        FloatingActionButton(
            onClick = { requestLocation() },
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
                Icon(Icons.Default.MyLocation, contentDescription = appString(Res.string.high_accuracy_gps_locate))
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
                    shadowElevation = Elevation.level2,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = if (isStripCollapsed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isStripCollapsed) {
                                sharedText("show_workspaces_x", sortedSpaces.sumOf { if (it.subdivisions.isNotEmpty()) it.subdivisions.size else 1 })
                            } else {
                                sharedText("collapse_map_list")
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
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
                        val typeBadge = sub?.type?.displayName ?: localizedLabel(space.spaceType.displayName)
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
                                        mapState.animateTo(GeoPoint(space.lat - 0.012, space.lng), 14f)
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
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
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
                                        coil3.compose.AsyncImage(
                                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current).data(thumb).size(200).build(),
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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = appString(Res.string.select_choices_and_request),
                                            style = MaterialTheme.typography.labelSmall,
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
private fun boundsDiffer(a: GeoBounds, b: GeoBounds): Boolean {
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
            shadowElevation = Elevation.level3,
            tonalElevation = Elevation.level2
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(appString(Res.string.search_this_area), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
