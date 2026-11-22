package com.example.ios

import com.example.data.auth.BoolCallback
import com.example.util.GeoPoint
import com.example.util.LocationProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One position answer from Core Location: [found] with a coordinate, or an [error] message. Built by Swift. */
class LocationReply(val found: Boolean, val latitude: Double, val longitude: Double, val error: String?)

/** Core Location (Swift `LocationBridgeImpl`). Completions run on the main thread. */
interface LocationBridge {
    /** 0 = not asked yet, 1 = allowed (When In Use or Always), 2 = denied or restricted, 3 = Location Services switched off. */
    fun locationStatus(): Int
    /** Shows the system "Allow ProHost to use your location?" prompt; completes with whether it was allowed. */
    fun requestWhenInUse(completion: BoolCallback)
    fun currentFix(completion: (LocationReply) -> Unit)
    fun lastFix(completion: (LocationReply) -> Unit)
}

/** [LocationProvider] over the Swift bridge, with the Android provider's contract (null = no fix, throw = provider failed). */
class IosLocationProvider(private val bridge: LocationBridge) : LocationProvider {
    override fun hasPermission(): Boolean = bridge.locationStatus() == 1

    override suspend fun currentLocation(): GeoPoint? = read { bridge.currentFix(it) }

    override suspend fun lastLocation(): GeoPoint? = read { bridge.lastFix(it) }

    private suspend fun read(call: ((LocationReply) -> Unit) -> Unit): GeoPoint? {
        val reply = suspendCancellableCoroutine<LocationReply> { cont -> call { cont.resume(it) } }
        reply.error?.let { throw IllegalStateException(it) }
        return if (reply.found) GeoPoint(reply.latitude, reply.longitude) else null
    }
}
