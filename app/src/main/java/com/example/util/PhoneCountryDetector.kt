package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.model.Country
import com.example.data.model.findCountryByIsoCode
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Best-effort ISO-3166 country detection used to prefill the phone number field's dial
 * code, so a user never has to hunt through a country list themselves. Tries the SIM
 * card first (instant, needs no runtime permission, correct even with location off),
 * then the last known device location (only if location permission is already
 * granted), then the carrier network, then finally the device's configured locale —
 * every step is best-effort and falls through silently, so this always returns
 * something usable instead of blocking sign-in on a slow or denied source.
 */
object PhoneCountryDetector {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun simCountryIso(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return telephony.simCountryIso?.takeIf { it.length == 2 }
    }

    private fun networkCountryIso(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return telephony.networkCountryIso?.takeIf { it.length == 2 }
    }

    private suspend fun lastKnownLocationCountryIso(context: Context): String? {
        if (!hasLocationPermission(context)) return null
        val location = try {
            suspendCancellableCoroutine<Location?> { cont ->
                try {
                    LocationServices.getFusedLocationProviderClient(context).lastLocation
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } catch (e: Exception) {
            null
        } ?: return null
        // The legacy Geocoder.getFromLocation overload is a real synchronous/blocking
        // call (can take seconds on a slow network) — this whole function is called
        // from LoginAuthScreen's LaunchedEffect(Unit), i.e. on Dispatchers.Main, so
        // without this the login screen's very first frame could ANR. Same fix
        // already applied to ListingLocationMapPicker's own Geocoder call.
        return try {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.countryCode?.takeIf { it.length == 2 }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Detects the caller's current country. Never throws — always resolves to a real [Country]. */
    suspend fun detectCountry(context: Context): Country {
        // Device locale reflects the user's configured region (Lebanon ↔ LB) and is
        // preferred over SIM ISO, which reflects the physical SIM card's home network
        // (a Lebanese user with a Syrian roaming SIM would otherwise see Syria).
        // GPS geocoding stays highest priority when location permission is granted.
        val isoCode = lastKnownLocationCountryIso(context)
            ?: Locale.getDefault().country.takeIf { it.length == 2 }
            ?: simCountryIso(context)
            ?: networkCountryIso(context)
            ?: "LB"
        return findCountryByIsoCode(isoCode)
    }

    /**
     * Reverses lat/lng map center coordinates into a human-readable country name.
     * Uses Geocoder first, then falls back to MENA geographical bounding box rules.
     */
    suspend fun detectCountryAtCoordinates(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            val name = addresses?.firstOrNull()?.countryName
            if (!name.isNullOrBlank()) return@withContext name
        } catch (e: Exception) {
            // Geocoder offline or network error — fall through to bounding box
        }

        when {
            lat in 33.0..34.7 && lng in 35.0..36.6 -> "Lebanon"
            lat in 32.3..37.3 && lng in 35.6..42.4 -> "Syria"
            lat in 29.2..33.4 && lng in 34.9..39.3 -> "Jordan"
            lat in 22.5..26.1 && lng in 51.5..56.4 -> "United Arab Emirates"
            lat in 16.0..32.2 && lng in 34.5..55.7 -> "Saudi Arabia"
            lat in 24.5..26.2 && lng in 50.7..51.7 -> "Qatar"
            lat in 28.5..30.1 && lng in 46.5..48.5 -> "Kuwait"
            lat in 34.5..35.7 && lng in 32.2..34.6 -> "Cyprus"
            lat in 22.0..31.7 && lng in 24.7..36.9 -> "Egypt"
            lat in 35.8..42.1 && lng in 25.6..44.8 -> "Turkey"
            lat in 29.1..37.4 && lng in 38.8..48.6 -> "Iraq"
            lat in 16.6..26.4 && lng in 52.0..59.8 -> "Oman"
            else -> null
        }
    }
}
