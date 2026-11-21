package com.example.util

import kotlinx.coroutines.CancellationException

/**
 * Reading the device position. One implementation per platform (Android below, on Google Play services' fused client; iOS later on Core Location).
 * Asking for permission and turning location on stay in `rememberLocationAccess`; this is only the read.
 */
interface LocationProvider {
    /** Whether the app may read the location (fine or coarse permission). */
    fun hasPermission(): Boolean

    /** One fresh high-accuracy fix; null when the device reports none. Throws when the provider fails. */
    suspend fun currentLocation(): GeoPoint?

    /** The last known position; null when there is none. Throws when the provider fails. */
    suspend fun lastLocation(): GeoPoint?

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @kotlin.concurrent.Volatile
        var current: LocationProvider? = null

        fun require(): LocationProvider = current ?: error("LocationProvider is not initialised (ProHostApplication.onCreate)")
    }
}

/** What a "locate me" press found. The screens map each outcome to what they always did (animate the map, show a message, or nothing). */
sealed interface LocateResult {
    data class Found(val point: GeoPoint) : LocateResult

    /** The device reported no position (and, when asked, no last known one either). */
    data object NoFix : LocateResult

    /** The provider failed while taking the fresh fix (the screens say "GPS signal unavailable"). */
    data object Unavailable : LocateResult

    /** Only the last-known fallback failed: the screens never reported that, they just stopped. */
    data object Silent : LocateResult
}

/**
 * The decision behind every "locate me" button: take a fresh fix; if the device has none and [fallbackToLast] is set, use the last known one.
 * Exactly the sequence both map screens implemented inline (picker: no fallback; map canvas: fallback).
 * [onFreshFixDone] runs as soon as the fresh attempt has ended, before the fallback read: the screens switch their "locating" spinner off there, as they did.
 */
suspend fun locate(provider: LocationProvider, fallbackToLast: Boolean, onFreshFixDone: () -> Unit = {}): LocateResult {
    val fix = try {
        provider.currentLocation()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFreshFixDone()
        return LocateResult.Unavailable
    }
    onFreshFixDone()
    if (fix != null) return LocateResult.Found(fix)
    if (!fallbackToLast) return LocateResult.NoFix
    val last = try {
        provider.lastLocation()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return LocateResult.Silent
    }
    return if (last != null) LocateResult.Found(last) else LocateResult.NoFix
}
