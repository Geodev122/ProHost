package com.example.ios

import platform.UIKit.UIView

/** One marker as the Swift map draws it. [kind]: 0 = teardrop pin (colours, [selected]), 1 = count bubble ([label], [selected] = gold). */
class NativeMapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    val kind: Int,
    val topArgb: Int,
    val baseArgb: Int,
    val selected: Boolean,
    val label: Int,
    val anchorX: Float,
    val anchorY: Float,
    val zIndex: Float
)

/** The camera as GMSMapView reports it, with the visible region's corners. */
class NativeMapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val southLatitude: Double,
    val westLongitude: Double,
    val northLatitude: Double,
    val eastLongitude: Double
)

/** Events from the Swift map back to the shared state (method names unique across exported interfaces). */
interface NativeMapEvents {
    fun mapWillMove(byGesture: Boolean)
    fun mapCameraChanged(camera: NativeMapCamera)
    fun mapCameraIdle(camera: NativeMapCamera)
    fun mapMarkerTapped(markerId: String): Boolean
    fun mapTappedOutsideMarkers()
    fun mapFinishedLoading()
}

/** A Google Maps view (Swift `GoogleMapViewBridge`). */
interface NativeMapView {
    fun mapUIView(): UIView
    fun mapMoveCamera(latitude: Double, longitude: Double, zoom: Float, animated: Boolean)
    fun mapSetPadding(top: Double, left: Double, bottom: Double, right: Double)
    fun mapSetMyLocationEnabled(enabled: Boolean)
    fun mapSetToolbarEnabled(enabled: Boolean)
    fun mapSetMarkers(markers: List<NativeMapMarker>)
}

/** Creates map views; installed by the Swift app delegate only when a Maps SDK key is configured. */
interface NativeMapFactory {
    fun createMapView(latitude: Double, longitude: Double, zoom: Float, events: NativeMapEvents): NativeMapView
}
