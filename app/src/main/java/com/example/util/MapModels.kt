package com.example.util

import androidx.compose.runtime.saveable.Saver

/**
 * A map camera in platform-neutral terms: where it looks, how far in, and its tilt and bearing in degrees. View models keep this
 * (Explore remembers the camera across a visit to a listing); each platform's map view converts it to its own camera type.
 */
data class MapCamera(val target: GeoPoint, val zoom: Float, val tilt: Float = 0f, val bearing: Float = 0f)

/** Saves a nullable [GeoPoint] across configuration changes and process death as a (latitude, longitude) pair. */
val GeoPointSaver: Saver<GeoPoint?, Any> = Saver(
    save = { point -> point?.let { arrayListOf(it.latitude, it.longitude) } ?: arrayListOf() },
    restore = { saved ->
        val list = (saved as? List<*>)?.filterIsInstance<Double>().orEmpty()
        if (list.size == 2) GeoPoint(list[0], list[1]) else null
    }
)
