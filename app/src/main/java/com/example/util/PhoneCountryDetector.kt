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
        val isoCode = simCountryIso(context)
            ?: lastKnownLocationCountryIso(context)
            ?: networkCountryIso(context)
            ?: Locale.getDefault().country.takeIf { it.length == 2 }
            ?: "LB"
        return findCountryByIsoCode(isoCode)
    }
}
