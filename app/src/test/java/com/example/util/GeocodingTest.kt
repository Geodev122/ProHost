package com.example.util

import android.location.Address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * P2.5f-2: the address rules moved from the map picker into common code over [PlaceHit]. [referenceResolve] is the old picker mapping, copied verbatim
 * over `android.location.Address`; every combination below must give the same [ResolvedAddress] through the new path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeocodingTest {
    private fun address(
        thoroughfare: String? = null, subThoroughfare: String? = null, subLocality: String? = null, subAdminArea: String? = null,
        locality: String? = null, countryCode: String? = null, countryName: String? = null, lat: Double = 33.9, lng: Double = 35.5
    ) = Address(Locale.ENGLISH).also {
        it.thoroughfare = thoroughfare; it.subThoroughfare = subThoroughfare; it.subLocality = subLocality; it.subAdminArea = subAdminArea
        it.locality = locality; it.countryCode = countryCode; it.countryName = countryName; it.latitude = lat; it.longitude = lng
    }

    /** The English-name lookup both old and new use: the platform's own display name, as the Android implementation answers it. */
    private val names = object : Geocoding {
        override suspend fun reverse(latitude: Double, longitude: Double, languageTag: String): PlaceHit? = error("not used")
        override suspend fun forward(query: String, languageTag: String): PlaceHit? = error("not used")
        override fun englishCountryName(countryCode: String): String? =
            Locale("", countryCode).getDisplayCountry(Locale.ENGLISH).takeIf { it.isNotBlank() }
    }

    private fun referenceEnglishName(code: String, fallback: String?): String =
        code.takeIf { it.isNotBlank() }?.let { Locale("", it).getDisplayCountry(Locale.ENGLISH) }?.takeIf { it.isNotBlank() } ?: fallback.orEmpty()

    private fun referenceResolve(lat: Double, lng: Double, local: Address?, english: Address?): ResolvedAddress {
        val code = (english ?: local)?.countryCode.orEmpty()
        return ResolvedAddress(
            lat = lat, lng = lng,
            street = listOfNotNull(local?.thoroughfare, local?.subThoroughfare).filter { it.isNotBlank() }.joinToString(" "),
            area = (local?.subLocality ?: local?.subAdminArea).orEmpty(),
            city = (local?.locality ?: local?.subAdminArea).orEmpty(),
            country = referenceEnglishName(code, english?.countryName ?: local?.countryName),
            countryCode = code,
            cityEn = (english?.locality ?: english?.subAdminArea).orEmpty()
        )
    }

    @Test
    fun theMappingGivesTheOldResultForEveryShapeOfMatch() {
        val beirut = address("Hamra St", "12", "Hamra", "Beirut", "Beirut", "LB", "Lebanon")
        val arabic = address("شارع الحمرا", "١٢", "الحمرا", "بيروت", "بيروت", "LB", "لبنان")
        val bare = address(subAdminArea = "Metn", countryCode = "FR", countryName = "France")
        val blankParts = address("", " ", "", "", "", "", "Atlantis")
        val noCode = address(locality = "Nowhere", countryName = "Utopia")
        val unknownCode = address(locality = "X", countryCode = "ZZ", countryName = "Zedland")
        val all = listOf<Address?>(null, beirut, arabic, bare, blankParts, noCode, unknownCode)
        for (local in all) for (english in all) {
            assertEquals(
                "local=${local?.locality}/${local?.countryCode} english=${english?.locality}/${english?.countryCode}",
                referenceResolve(1.0, 2.0, local, english),
                names.toResolvedAddress(1.0, 2.0, local?.toPlaceHit(), english?.toPlaceHit())
            )
        }
    }

    private class Recording(
        private val answers: Map<String, PlaceHit?>,
        private val forwardHit: PlaceHit? = null
    ) : Geocoding {
        val calls = mutableListOf<String>()
        override suspend fun reverse(latitude: Double, longitude: Double, languageTag: String): PlaceHit? { calls += "reverse:$languageTag"; return answers[languageTag] }
        override suspend fun forward(query: String, languageTag: String): PlaceHit? { calls += "forward:$languageTag:$query"; return forwardHit }
        override fun englishCountryName(countryCode: String): String? = if (countryCode == "LB") "Lebanon" else null
    }

    private val ar = PlaceHit(33.9, 35.5, thoroughfare = "شارع", locality = "بيروت", countryCode = "LB", countryName = "لبنان")
    private val en = PlaceHit(33.9, 35.5, thoroughfare = "Street", locality = "Beirut", countryCode = "LB", countryName = "Lebanon")

    @Test
    fun anEnglishAppLooksUpOnce() = runBlocking {
        val g = Recording(mapOf("en-US" to en))
        val r = g.resolvePin("en-US", 1.0, 2.0)
        assertEquals(listOf("reverse:en-US"), g.calls)
        assertEquals("Beirut", r.city); assertEquals("Beirut", r.cityEn); assertEquals("Lebanon", r.country)
    }

    @Test
    fun anArabicAppLooksUpInArabicThenEnglishAndKeepsBoth() = runBlocking {
        val g = Recording(mapOf("ar" to ar, "en" to en))
        val r = g.resolvePin("ar", 1.0, 2.0)
        assertEquals(listOf("reverse:ar", "reverse:en"), g.calls)
        assertEquals("بيروت", r.city); assertEquals("Beirut", r.cityEn); assertEquals("Lebanon", r.country); assertEquals("LB", r.countryCode)
    }

    @Test
    fun whenTheEnglishLookupFailsTheLocalMatchIsUsedForBoth() = runBlocking {
        val g = Recording(mapOf("ar" to ar, "en" to null))
        val r = g.resolvePin("ar", 1.0, 2.0)
        assertEquals("بيروت", r.city); assertEquals("بيروت", r.cityEn); assertEquals("Lebanon", r.country)
    }

    @Test
    fun nothingFoundStillAnswersWithThePoint() = runBlocking {
        val r = Recording(emptyMap()).resolvePin("ar", 1.5, 2.5)
        assertEquals(ResolvedAddress(lat = 1.5, lng = 2.5), r)
    }

    @Test
    fun aTypedAddressIsFoundInEnglishThenResolvedLikeAPin() = runBlocking {
        val g = Recording(mapOf("ar" to ar, "en" to en), forwardHit = PlaceHit(34.0, 36.0))
        val r = g.resolveTypedAddress("ar", "Hamra, Beirut")!!
        assertEquals(listOf("forward:en:Hamra, Beirut", "reverse:ar", "reverse:en"), g.calls)
        assertEquals(34.0, r.lat, 0.0); assertEquals(36.0, r.lng, 0.0)
        assertNull(Recording(emptyMap()).resolveTypedAddress("ar", "nowhere"))
    }
}
