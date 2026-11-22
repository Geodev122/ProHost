package com.example.ios

import com.example.util.Geocoding
import com.example.util.PlaceHit
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import kotlin.coroutines.resume

/**
 * iOS [Geocoding] on CLGeocoder (Android: `android.location.Geocoder`). First match or null; never throws. A new CLGeocoder per
 * call, because one instance runs a single request at a time.
 */
@OptIn(ExperimentalForeignApi::class)
object IosGeocoding : Geocoding {
    override suspend fun reverse(latitude: Double, longitude: Double, languageTag: String): PlaceHit? =
        suspendCancellableCoroutine { cont ->
            try {
                CLGeocoder().reverseGeocodeLocation(
                    CLLocation(latitude = latitude, longitude = longitude),
                    preferredLocale = NSLocale(localeIdentifier = languageTag)
                ) { placemarks, _ ->
                    if (cont.isActive) cont.resume((placemarks?.firstOrNull() as? CLPlacemark)?.toHit(latitude, longitude))
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    override suspend fun forward(query: String, languageTag: String): PlaceHit? =
        suspendCancellableCoroutine { cont ->
            try {
                CLGeocoder().geocodeAddressString(query, inRegion = null, preferredLocale = NSLocale(localeIdentifier = languageTag)) { placemarks, _ ->
                    if (cont.isActive) cont.resume((placemarks?.firstOrNull() as? CLPlacemark)?.toHit(null, null))
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    override fun englishCountryName(countryCode: String): String? =
        NSLocale(localeIdentifier = "en_US").displayNameForKey(NSLocaleCountryCode, countryCode.uppercase())?.takeIf { it.isNotBlank() }

    private fun CLPlacemark.toHit(lat: Double?, lng: Double?): PlaceHit? {
        val coordinate = location?.coordinate?.useContents { latitude to longitude }
        val la = coordinate?.first ?: lat ?: return null
        val ln = coordinate?.second ?: lng ?: return null
        return PlaceHit(
            latitude = la,
            longitude = ln,
            thoroughfare = thoroughfare,
            subThoroughfare = subThoroughfare,
            subLocality = subLocality,
            subAdminArea = subAdministrativeArea,
            locality = locality,
            countryCode = ISOcountryCode,
            countryName = country
        )
    }
}
