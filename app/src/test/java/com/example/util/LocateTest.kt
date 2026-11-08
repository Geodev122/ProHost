package com.example.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2.5e: `locate()` is the decision both map screens made inline. Each outcome below is what those screens did: the picker never fell back to the last
 * known position and stayed silent when there was no fix; the map canvas fell back, said "cannot determine" when nothing was known, and stayed silent when
 * only the fallback failed; both said "GPS signal unavailable" when the fresh fix failed.
 */
class LocateTest {
    private class Fake(
        private val current: () -> GeoPoint?,
        private val last: () -> GeoPoint? = { error("last location must not be read") }
    ) : LocationProvider {
        var lastRead = false
        override fun hasPermission() = true
        override suspend fun currentLocation(): GeoPoint? = current()
        override suspend fun lastLocation(): GeoPoint? { lastRead = true; return last() }
    }

    private val here = GeoPoint(33.89, 35.50)
    private val before = GeoPoint(34.0, 35.6)

    @Test
    fun aFreshFixIsUsedAndTheLastKnownOneIsNotRead() = runBlocking {
        val p = Fake(current = { here })
        assertEquals(LocateResult.Found(here), locate(p, fallbackToLast = true))
        assertFalse(p.lastRead)
    }

    @Test
    fun noFreshFixFallsBackToTheLastKnownPositionWhenAsked() = runBlocking {
        val p = Fake(current = { null }, last = { before })
        assertEquals(LocateResult.Found(before), locate(p, fallbackToLast = true))
    }

    @Test
    fun noFixAndNothingKnownIsNoFix() = runBlocking {
        assertEquals(LocateResult.NoFix, locate(Fake(current = { null }, last = { null }), fallbackToLast = true))
    }

    @Test
    fun withoutFallbackNoFreshFixIsNoFixAndTheLastPositionIsNeverRead() = runBlocking {
        val p = Fake(current = { null })
        assertEquals(LocateResult.NoFix, locate(p, fallbackToLast = false))
        assertFalse(p.lastRead)
    }

    @Test
    fun aFailedFreshFixIsUnavailableWithOrWithoutFallback() = runBlocking {
        assertEquals(LocateResult.Unavailable, locate(Fake(current = { throw IllegalStateException("no GPS") }), fallbackToLast = true))
        assertEquals(LocateResult.Unavailable, locate(Fake(current = { throw IllegalStateException("no GPS") }), fallbackToLast = false))
    }

    @Test
    fun aFailedFallbackIsSilent() = runBlocking {
        val p = Fake(current = { null }, last = { throw SecurityException("revoked") })
        assertEquals(LocateResult.Silent, locate(p, fallbackToLast = true))
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        var thrown = false
        try {
            runBlocking { locate(Fake(current = { throw CancellationException("scope closed") }), fallbackToLast = true) }
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun theSpinnerCallbackRunsAfterTheFreshAttemptAndBeforeTheFallbackRead() = runBlocking {
        val order = mutableListOf<String>()
        val p = Fake(current = { order += "fresh"; null }, last = { order += "last"; before })
        locate(p, fallbackToLast = true, onFreshFixDone = { order += "done" })
        assertEquals(listOf("fresh", "done", "last"), order)
        order.clear()
        locate(Fake(current = { order += "fresh"; throw IllegalStateException("x") }), fallbackToLast = true, onFreshFixDone = { order += "done" })
        assertEquals(listOf("fresh", "done"), order)
    }
}
