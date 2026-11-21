package com.example.util

import kotlin.concurrent.Volatile

/**
 * The country (its English name, as listings store it) at a map position. Android installs its Geocoder lookup
 * (`PhoneCountryDetector.detectCountryAtCoordinates`); iOS will install CLGeocoder. Null when unknown.
 */
object CountryLookup {
    @Volatile
    var atPoint: suspend (latitude: Double, longitude: Double) -> String? = { _, _ -> null }
}
