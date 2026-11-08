package com.example.util

import androidx.compose.runtime.saveable.SaverScope
import com.example.ui.components.toCameraPosition
import com.example.ui.components.toGeoPoint
import com.example.ui.components.toLatLng
import com.example.ui.components.toMapCamera
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2.5f-1: Explore's remembered camera and the listing wizard's pin are platform-neutral models now. Converting to the map's own types and back
 * must lose nothing (the camera is restored after a visit to a listing), and the pin must survive a configuration change as it did when it was a Parcelable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapModelsTest {
    private val scope = SaverScope { true }

    @Test
    fun latLngRoundTripsExactly() {
        val p = GeoPoint(33.888630123456, 35.495480654321)
        assertEquals(p, p.toLatLng().toGeoPoint())
        assertEquals(LatLng(-1.5, 179.999), GeoPoint(-1.5, 179.999).toLatLng())
    }

    @Test
    fun aCameraKeepsTargetZoomTiltAndBearing() {
        val cam = MapCamera(GeoPoint(33.8886, 35.5184), 13.25f, 30f, 45f)
        val position = cam.toCameraPosition()
        assertEquals(LatLng(33.8886, 35.5184), position.target)
        assertEquals(13.25f, position.zoom)
        assertEquals(30f, position.tilt)
        assertEquals(45f, position.bearing)
        assertEquals(cam, position.toMapCamera())
    }

    @Test
    fun theDefaultCameraEqualsFromLatLngZoom() {
        val expected = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
        assertEquals(expected, MapCamera(GeoPoint(20.0, 0.0), 2f).toCameraPosition())
    }

    @Test
    fun theWizardPinSurvivesSavingAndRestoring() {
        val saved = with(GeoPointSaver) { scope.save(GeoPoint(34.4367, 35.8497)) }!!
        assertEquals(GeoPoint(34.4367, 35.8497), GeoPointSaver.restore(saved))
    }

    @Test
    fun noPinSavesAndRestoresAsNoPin() {
        val saved = with(GeoPointSaver) { scope.save(null) }!!
        assertNull(GeoPointSaver.restore(saved))
        assertNull(GeoPointSaver.restore("garbage"))
    }
}
