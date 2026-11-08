package com.example.ui.components

import com.example.util.GeoPoint
import com.example.util.MapCamera
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

/** Android map types ⇄ the platform-neutral models view models and logic use. */
fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

fun CameraPosition.toMapCamera() = MapCamera(target.toGeoPoint(), zoom, tilt, bearing)

fun MapCamera.toCameraPosition(): CameraPosition =
    CameraPosition.Builder().target(target.toLatLng()).zoom(zoom).tilt(tilt).bearing(bearing).build()
