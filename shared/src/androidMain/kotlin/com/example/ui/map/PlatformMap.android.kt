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

// Marker look (owner, 28 Nov 2026: "more modern and dynamic", same pin and bubble). Mirrored point for point in
// ios/ProHost/Core/Platform/GoogleMapViewBridge.swift (MarkerImages): keep both identical.
private fun createCustomMarker(context: Context, topColor: Int, baseColor: Int, isSelected: Boolean): BitmapDescriptor {
    val scale = context.resources.displayMetrics.density
    val pinScale = if (isSelected) 1.25f else 1.0f
    val width = (36 * scale * pinScale).toInt()
    val height = (54 * scale * pinScale).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Same 200x300 viewBox as before: the tip stays at (100, 255), so the anchor is unchanged.
    canvas.scale(width / 200f, height / 300f)

    // Soft ground shadow under the tip
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(70, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawOval(android.graphics.RectF(62f, 246f, 138f, 266f), shadowPaint)

    // Selected: a translucent halo around the head
    if (isSelected) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = topColor
            alpha = 70
        }
        canvas.drawCircle(100f, 95f, 96f, halo)
    }

    val path = android.graphics.Path().apply {
        moveTo(30.72f, 135f)
        arcTo(android.graphics.RectF(20f, 15f, 180f, 175f), 180f, 180f, false)
        lineTo(100f, 255f)
        close()
    }

    // Body: top-to-bottom gradient
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.LinearGradient(100f, 15f, 100f, 255f, topColor, baseColor, android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawPath(path, paint)

    // Soft top sheen (subtler than the old gloss spot)
    val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = android.graphics.LinearGradient(
            100f, 15f, 100f, 110f,
            android.graphics.Color.argb(90, 255, 255, 255), android.graphics.Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawPath(path, sheen)

    // Crisp white outline so pins read on any map colour
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 10f
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(path, outline)

    // Two-tone centre: white disc with a dot in the pin colour
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = android.graphics.Color.WHITE }
    canvas.drawCircle(100f, 95f, 34f, dot)
    dot.color = baseColor
    canvas.drawCircle(100f, 95f, if (isSelected) 16f else 12f, dot)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Cluster bubble side in dp: grows with the number of listings it holds. */
private fun clusterSideDp(count: Int): Int = when {
    count < 10 -> 46
    count < 50 -> 54
    else -> 62
}

private fun createClusterMarker(context: Context, count: Int, hasSelected: Boolean): BitmapDescriptor {
    val scale = context.resources.displayMetrics.density
    val side = clusterSideDp(count)
    val size = (side * scale).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val outer = cx - (1 * scale)
    val radius = cx - (7 * scale)

    val bgColor = if (hasSelected) android.graphics.Color.parseColor("#FFB300") else android.graphics.Color.parseColor("#2B5A8C")
    val lightColor = if (hasSelected) android.graphics.Color.parseColor("#FFCA4D") else android.graphics.Color.parseColor("#4A7DB3")

    // Translucent halo ring
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = bgColor; alpha = 64 }
    canvas.drawCircle(cx, cy, outer, paint)

    // Bubble with a light-to-brand gradient
    paint.alpha = 255
    paint.shader = android.graphics.LinearGradient(cx, cy - radius, cx, cy + radius, lightColor, bgColor, android.graphics.Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, radius, paint)
    paint.shader = null

    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 2.5f * scale
    canvas.drawCircle(cx, cy, radius, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = (if (side >= 62) 16 else if (side >= 54) 15 else 14) * scale
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(if (count > MAX_CLUSTER_LABEL) "$MAX_CLUSTER_LABEL+" else count.toString(), cx, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
