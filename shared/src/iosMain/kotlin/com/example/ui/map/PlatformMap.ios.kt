package com.example.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.UIKitView
import com.example.ios.IosPlatform
import com.example.ios.NativeMapCamera
import com.example.ios.NativeMapEvents
import com.example.ios.NativeMapMarker
import com.example.ios.NativeMapView
import com.example.util.GeoPoint
import com.example.util.MapCamera
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS map state: the Google Maps SDK view (Swift `GoogleMapViewBridge`) reports camera moves and idles here; [animateTo]
 * drives it. Without a Maps key (no factory installed) the camera only moves on request and nothing is drawn.
 */
@Stable
actual class PlatformMapState(initial: MapCamera) {
    private var cameraState by mutableStateOf(initial)
    private var movingState by mutableStateOf(false)
    private var gestureState by mutableStateOf(false)
    private var boundsState by mutableStateOf<GeoBounds?>(null)
    internal var view: NativeMapView? = null

    actual val camera: MapCamera get() = cameraState
    actual val isMoving: Boolean get() = movingState
    actual val movedByGesture: Boolean get() = gestureState
    actual val visibleBounds: GeoBounds? get() = boundsState

    actual suspend fun animateTo(target: GeoPoint, zoom: Float?) {
        val z = zoom ?: cameraState.zoom
        val native = view
        if (native == null) {
            cameraState = cameraState.copy(target = target, zoom = z)
        } else {
            native.mapMoveCamera(target.latitude, target.longitude, z, animated = true)
        }
    }

    internal fun willMove(byGesture: Boolean) {
        movingState = true
        gestureState = byGesture
    }

    internal fun update(camera: NativeMapCamera, idle: Boolean) {
        cameraState = cameraState.copy(target = GeoPoint(camera.latitude, camera.longitude), zoom = camera.zoom)
        boundsState = GeoBounds(
            southwest = GeoPoint(camera.southLatitude, camera.westLongitude),
            northeast = GeoPoint(camera.northLatitude, camera.eastLongitude)
        )
        if (idle) movingState = false
    }
}

@Composable
actual fun rememberPlatformMapState(initial: MapCamera): PlatformMapState = remember { PlatformMapState(initial) }

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMap(
    state: PlatformMapState,
    markers: List<MapMarkerSpec>,
    contentPadding: PaddingValues,
    myLocationEnabled: Boolean,
    onMapLoaded: () -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier,
    mapToolbarEnabled: Boolean,
) {
    val factory = IosPlatform.maps
    if (factory == null) {
        Box(modifier)
        return
    }
    val currentMarkers by rememberUpdatedState(markers)
    val currentOnLoaded by rememberUpdatedState(onMapLoaded)
    val currentOnClick by rememberUpdatedState(onMapClick)
    val events = remember(state) {
        object : NativeMapEvents {
            override fun mapWillMove(byGesture: Boolean) = state.willMove(byGesture)
            override fun mapCameraChanged(camera: NativeMapCamera) = state.update(camera, idle = false)
            override fun mapCameraIdle(camera: NativeMapCamera) = state.update(camera, idle = true)
            override fun mapMarkerTapped(markerId: String): Boolean =
                currentMarkers.firstOrNull { it.key.toString() == markerId }?.onClick?.invoke() ?: false
            override fun mapTappedOutsideMarkers() = currentOnClick()
            override fun mapFinishedLoading() = currentOnLoaded()
        }
    }
    val native = remember(state) {
        val start = state.camera
        factory.createMapView(start.target.latitude, start.target.longitude, start.zoom, events).also { state.view = it }
    }
    DisposableEffect(state, native) {
        onDispose { if (state.view === native) state.view = null }
    }

    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(native, contentPadding, layoutDirection) {
        native.mapSetPadding(
            top = contentPadding.calculateTopPadding().value.toDouble(),
            left = contentPadding.calculateLeftPadding(layoutDirection).value.toDouble(),
            bottom = contentPadding.calculateBottomPadding().value.toDouble(),
            right = contentPadding.calculateRightPadding(layoutDirection).value.toDouble()
        )
    }
    LaunchedEffect(native, myLocationEnabled) { native.mapSetMyLocationEnabled(myLocationEnabled) }
    LaunchedEffect(native, mapToolbarEnabled) { native.mapSetToolbarEnabled(mapToolbarEnabled) }
    LaunchedEffect(native, markers) { native.mapSetMarkers(markers.map { it.toNative() }) }

    UIKitView(factory = { native.mapUIView() }, modifier = modifier)
}

private fun MapMarkerSpec.toNative(): NativeMapMarker = when (val i = icon) {
    is MapIcon.Pin -> NativeMapMarker(
        id = key.toString(), latitude = position.latitude, longitude = position.longitude, title = title, snippet = snippet,
        kind = 0, topArgb = i.topArgb, baseArgb = i.baseArgb, selected = i.selected, label = 0,
        anchorX = anchorX, anchorY = anchorY, zIndex = zIndex
    )
    is MapIcon.Cluster -> NativeMapMarker(
        id = key.toString(), latitude = position.latitude, longitude = position.longitude, title = title, snippet = snippet,
        kind = 1, topArgb = 0, baseArgb = 0, selected = i.hasSelected, label = i.label,
        anchorX = anchorX, anchorY = anchorY, zIndex = zIndex
    )
}
