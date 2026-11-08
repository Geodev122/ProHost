package com.example.util

/** One geocoder match in platform-neutral terms (what `android.location.Address` carries that ProHost reads). */
data class PlaceHit(
    val latitude: Double,
    val longitude: Double,
    val thoroughfare: String? = null,
    val subThoroughfare: String? = null,
    val subLocality: String? = null,
    val subAdminArea: String? = null,
    val locality: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null
)

/**
 * Address lookups. One implementation per platform (Android below on `android.location.Geocoder`; iOS later on `CLGeocoder`).
 * Both lookups return the first match or null, and never throw (offline, no match and provider errors all read as null), exactly as the screens used them.
 */
interface Geocoding {
    /** The address at a point, described in the language of [languageTag] (a BCP-47 tag such as "ar" or "en-US"). */
    suspend fun reverse(latitude: Double, longitude: Double, languageTag: String): PlaceHit?

    /** The first match for a typed address. */
    suspend fun forward(query: String, languageTag: String): PlaceHit?

    /** A country's name in English for an ISO-3166 code ("LB" → "Lebanon"); null when the platform has none. */
    fun englishCountryName(countryCode: String): String?

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @Volatile
        var current: Geocoding? = null

        fun require(): Geocoding = current ?: error("Geocoding is not initialised (ProHostApplication.onCreate)")
    }
}

/**
 * What a pin resolved to. Street, area and city are in the app's language (an Arabic app fills Arabic); the country is its
 * standard English name (filters and search compare English names) and [cityEn] keeps an English copy of the city.
 */
data class ResolvedAddress(
    val lat: Double,
    val lng: Double,
    val street: String = "",
    val area: String = "",
    val city: String = "",
    val country: String = "",
    val countryCode: String = "",
    val cityEn: String = ""
)

/** English country name for an ISO code ("LB" → "Lebanon"), else [fallback]. */
fun Geocoding.englishCountryName(countryCode: String, fallback: String?): String =
    countryCode.takeIf { it.isNotBlank() }?.let { englishCountryName(it) }?.takeIf { it.isNotBlank() }
        ?: fallback.orEmpty()

/** Reverse-geocodes a pin twice when needed: once in the app's language for display, once in English for the copies. */
suspend fun Geocoding.resolvePin(appLanguageTag: String, lat: Double, lng: Double): ResolvedAddress {
    val local = reverse(lat, lng, appLanguageTag)
    val appIsEnglish = appLanguageTag.substringBefore('-').substringBefore('_').equals("en", ignoreCase = true)
    val english = if (appIsEnglish) local else reverse(lat, lng, "en") ?: local
    return toResolvedAddress(lat, lng, local, english)
}

/** Forward-geocodes a typed address (no pin dropped) the same way; null when nothing matches. */
suspend fun Geocoding.resolveTypedAddress(appLanguageTag: String, query: String): ResolvedAddress? {
    val hit = forward(query, "en") ?: return null
    return resolvePin(appLanguageTag, hit.latitude, hit.longitude)
}

/** The mapping from geocoder matches to the fields the wizard fills. Pure; the rules are the ones the picker always applied. */
fun Geocoding.toResolvedAddress(lat: Double, lng: Double, local: PlaceHit?, english: PlaceHit?): ResolvedAddress {
    val code = (english ?: local)?.countryCode.orEmpty()
    return ResolvedAddress(
        lat = lat,
        lng = lng,
        street = listOfNotNull(local?.thoroughfare, local?.subThoroughfare).filter { it.isNotBlank() }.joinToString(" "),
        area = (local?.subLocality ?: local?.subAdminArea).orEmpty(),
        city = (local?.locality ?: local?.subAdminArea).orEmpty(),
        country = englishCountryName(code, english?.countryName ?: local?.countryName),
        countryCode = code,
        cityEn = (english?.locality ?: english?.subAdminArea).orEmpty()
    )
}

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
    countryName = countryName
)
