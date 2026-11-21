package com.example.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.util.GeoPoint
import com.example.util.MapCamera

// Milestone C3 (iOS): GMSMapView through a Swift bridge, key MAPS_IOS_API_KEY. Until then the camera only moves on request.
@Stable
actual class PlatformMapState(initial: MapCamera) {
    actual var camera: MapCamera by mutableStateOf(initial)
        private set
    actual val isMoving: Boolean = false
    actual val movedByGesture: Boolean = false
    actual val visibleBounds: GeoBounds? = null

    actual suspend fun animateTo(target: GeoPoint, zoom: Float?) {
        camera = camera.copy(target = target, zoom = zoom ?: camera.zoom)
    }
}

@Composable
actual fun rememberPlatformMapState(initial: MapCamera): PlatformMapState = remember { PlatformMapState(initial) }

@Composable
actual fun PlatformMap(
    state: PlatformMapState,
    markers: List<MapMarkerSpec>,
    contentPadding: PaddingValues,
    myLocationEnabled: Boolean,
    onMapLoaded: () -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier)
}
