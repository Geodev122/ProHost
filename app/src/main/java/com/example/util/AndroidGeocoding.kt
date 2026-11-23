package com.example.util

/** Android [Geocoding] on `android.location.Geocoder` (the legacy blocking overloads, run on the IO dispatcher as the screens did). */
class AndroidGeocoding(context: android.content.Context) : Geocoding {
    private val app = context.applicationContext

    @Suppress("DEPRECATION")
    override suspend fun reverse(latitude: Double, longitude: Double, languageTag: String): PlaceHit? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                android.location.Geocoder(app, java.util.Locale.forLanguageTag(languageTag)).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }.getOrNull()?.toPlaceHit()
        }

    @Suppress("DEPRECATION")
    override suspend fun forward(query: String, languageTag: String): PlaceHit? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                android.location.Geocoder(app, java.util.Locale.forLanguageTag(languageTag)).getFromLocationName(query, 1)?.firstOrNull()
            }.getOrNull()?.toPlaceHit()
        }

    override fun englishCountryName(countryCode: String): String? =
        java.util.Locale("", countryCode).getDisplayCountry(java.util.Locale.ENGLISH).takeIf { it.isNotBlank() }
}

internal fun android.location.Address.toPlaceHit() = PlaceHit(
    latitude = latitude,
    longitude = longitude,
    thoroughfare = thoroughfare,
    subThoroughfare = subThoroughfare,
    subLocality = subLocality,
    subAdminArea = subAdminArea,
    locality = locality,
    countryCode = countryCode,
    countryName = countryName,
    adminArea = adminArea
)
