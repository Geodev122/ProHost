package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android [LocationProvider] on Google Play services' fused location client (what the screens used directly before). */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val app = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(app) }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override suspend fun currentLocation(): GeoPoint? = suspendCancellableCoroutine { cont ->
        val cancel = CancellationTokenSource()
        cont.invokeOnCancellation { cancel.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) }) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    override suspend fun lastLocation(): GeoPoint? = suspendCancellableCoroutine { cont ->
        try {
            client.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) }) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }
}
