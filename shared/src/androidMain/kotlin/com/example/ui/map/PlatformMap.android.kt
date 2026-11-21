package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.toCameraPosition
import com.example.ui.components.toGeoPoint
import com.example.ui.components.toLatLng
import com.example.ui.components.toMapCamera
import com.example.util.GeoPoint
import com.example.util.MapCamera
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

private const val MAX_CLUSTER_LABEL = 99

@Stable
actual class PlatformMapState(internal val cameraPositionState: CameraPositionState) {
    actual val camera: MapCamera get() = cameraPositionState.position.toMapCamera()
    actual val isMoving: Boolean get() = cameraPositionState.isMoving
    actual val movedByGesture: Boolean get() = cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
    actual val visibleBounds: GeoBounds?
        get() = cameraPositionState.projection?.visibleRegion?.latLngBounds?.let { GeoBounds(it.southwest.toGeoPoint(), it.northeast.toGeoPoint()) }

    actual suspend fun animateTo(target: GeoPoint, zoom: Float?) {
        cameraPositionState.animate(
            if (zoom == null) CameraUpdateFactory.newLatLng(target.toLatLng()) else CameraUpdateFactory.newLatLngZoom(target.toLatLng(), zoom)
        )
    }
}

@Composable
actual fun rememberPlatformMapState(initial: MapCamera): PlatformMapState {
    val cameraPositionState = rememberCameraPositionState { position = initial.toCameraPosition() }
    return remember(cameraPositionState) { PlatformMapState(cameraPositionState) }
}

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
    val context = LocalContext.current
    // BitmapDescriptors are registered natively and never freed, so build one per distinct look
    // (palette + selection, or cluster label + selection) instead of one per marker per recomposition.
    val iconCache = remember { HashMap<MapIcon, BitmapDescriptor>() }
    fun iconFor(icon: MapIcon): BitmapDescriptor = iconCache.getOrPut(icon) {
        when (icon) {
            is MapIcon.Pin -> createCustomMarker(context, icon.topArgb, icon.baseArgb, icon.selected)
            is MapIcon.Cluster -> createClusterMarker(context, icon.label, icon.hasSelected)
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = state.cameraPositionState,
        contentPadding = contentPadding,
        properties = MapProperties(
            isMyLocationEnabled = myLocationEnabled,
            mapType = MapType.NORMAL
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = mapToolbarEnabled
        ),
        onMapLoaded = onMapLoaded,
        onMapClick = { onMapClick() }
    ) {
        markers.forEach { spec ->
            key(spec.key) {
                val markerState = remember(spec.key, spec.position) { MarkerState(position = spec.position.toLatLng()) }
                Marker(
                    state = markerState,
                    title = spec.title,
                    snippet = spec.snippet,
                    icon = iconFor(spec.icon),
                    anchor = androidx.compose.ui.geometry.Offset(spec.anchorX, spec.anchorY),
                    zIndex = spec.zIndex,
                    onClick = { spec.onClick() }
                )
            }
        }
    }
}

private fun createCustomMarker(context: Context, topColor: Int, baseColor: Int, isSelected: Boolean): BitmapDescriptor {
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
        topColor, baseColor,
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
