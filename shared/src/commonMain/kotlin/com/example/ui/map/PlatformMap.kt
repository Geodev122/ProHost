package com.example.ui.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.example.util.GeoPoint
import com.example.util.MapCamera

/**
 * A latitude/longitude rectangle with the same rules as Google's `LatLngBounds`: [contains] and [center] handle a box that crosses
 * the antimeridian (southwest longitude greater than northeast longitude).
 */
data class GeoBounds(val southwest: GeoPoint, val northeast: GeoPoint) {
    fun contains(point: GeoPoint): Boolean {
        val p = normalized(point)
        val latOk = southwest.latitude <= p.latitude && p.latitude <= northeast.latitude
        val lng = p.longitude
        val lngOk = if (southwest.longitude <= northeast.longitude) southwest.longitude <= lng && lng <= northeast.longitude
        else southwest.longitude <= lng || lng <= northeast.longitude
        return latOk && lngOk
    }

    val center: GeoPoint
        get() {
            val lat = (southwest.latitude + northeast.latitude) / 2.0
            val lng1 = southwest.longitude
            val lng2 = northeast.longitude
            val lng = if (lng1 <= lng2) (lng2 + lng1) / 2.0 else (lng2 + 360.0 + lng1) / 2.0
            return normalized(GeoPoint(lat, lng))
        }

    private companion object {
        /** `LatLng`'s constructor rules: latitude clamped to [-90, 90], longitude wrapped into [-180, 180). */
        fun normalized(p: GeoPoint): GeoPoint {
            val lng = p.longitude
            val wrapped = if (-180.0 <= lng && lng < 180.0) lng else ((lng - 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            return GeoPoint(p.latitude.coerceIn(-90.0, 90.0), wrapped)
        }
    }
}

/** What a marker looks like; each platform draws it (Android: the teardrop pin and count bubble bitmaps). */
sealed interface MapIcon {
    /** The teardrop pin in a gradient from [topArgb] to [baseArgb]; [selected] draws it 1.25× larger. */
    data class Pin(val topArgb: Int, val baseArgb: Int, val selected: Boolean) : MapIcon

    /** A count bubble; [label] above 99 prints "99+". [hasSelected] turns it gold. */
    data class Cluster(val label: Int, val hasSelected: Boolean) : MapIcon
}

/** One marker. [key] keeps its identity across recompositions; [onClick] returns true when it consumed the tap. */
class MapMarkerSpec(
    val key: Any,
    val position: GeoPoint,
    val title: String,
    val snippet: String,
    val icon: MapIcon,
    val anchorX: Float,
    val anchorY: Float,
    val zIndex: Float,
    val onClick: () -> Boolean,
)

/** The map camera as the shared screens read and drive it. All values are snapshot state (recomposition follows them). */
@Stable
expect class PlatformMapState {
    val camera: MapCamera
    val isMoving: Boolean
    /** True when the current or last camera move was started by the person's gesture. */
    val movedByGesture: Boolean
    /** The visible region once the map has laid out; null before. */
    val visibleBounds: GeoBounds?
    suspend fun animateTo(target: GeoPoint, zoom: Float? = null)
}

@Composable
expect fun rememberPlatformMapState(initial: MapCamera): PlatformMapState

/** The platform map (Android: Google Maps Compose; iOS: Google Maps SDK in milestone C3, blank until then). */
@Composable
expect fun PlatformMap(
    state: PlatformMapState,
    markers: List<MapMarkerSpec>,
    contentPadding: PaddingValues,
    myLocationEnabled: Boolean,
    onMapLoaded: () -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Android's "open in Maps" toolbar on marker taps (Explore turns it off; the pin picker kept the default, on). */
    mapToolbarEnabled: Boolean = false,
)
