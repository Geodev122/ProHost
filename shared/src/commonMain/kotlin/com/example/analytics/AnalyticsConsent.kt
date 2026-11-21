package com.example.analytics

import com.example.util.KeyValueStores
import com.example.util.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConsentState { UNKNOWN, GRANTED, DENIED }

/**
 * Device-level analytics opt-in. Collection is off in the manifest until [grant]; the
 * choice is mirrored onto the user profile (analyticsConsent) so server-side Measurement
 * Protocol events respect it too. Bump [POLICY_VERSION] to ask everyone again.
 */
object AnalyticsConsent {
    const val POLICY_VERSION = "2026-10"
    private const val PREFS = "analytics_consent"
    private const val KEY_STATE = "state"
    private const val KEY_VERSION = "version"
    private const val KEY_AT = "decided_at"

    private val _state = MutableStateFlow(ConsentState.UNKNOWN)
    val state: StateFlow<ConsentState> = _state.asStateFlow()

    var decidedAtMillis: Long = 0L
        private set

    fun load() {
        val prefs = KeyValueStores.open(PREFS)
        val version = prefs.getString(KEY_VERSION)
        val stored = prefs.getString(KEY_STATE)
            ?.let { runCatching { ConsentState.valueOf(it) }.getOrNull() }
        _state.value = if (version == POLICY_VERSION && stored != null) stored else ConsentState.UNKNOWN
        decidedAtMillis = prefs.getLong(KEY_AT, 0L)
        AnalyticsTracker.applyConsent(_state.value == ConsentState.GRANTED, reset = false)
    }

    fun grant() {
        set(ConsentState.GRANTED)
        // First event after opting in (denials are never sent: nothing is collected then).
        AnalyticsTracker.analyticsConsent(granted = true)
    }

    fun deny() = set(ConsentState.DENIED)

    private fun set(newState: ConsentState) {
        val wasGranted = _state.value == ConsentState.GRANTED
        decidedAtMillis = nowMillis()
        KeyValueStores.open(PREFS).put(
            strings = mapOf(KEY_STATE to newState.name, KEY_VERSION to POLICY_VERSION),
            longs = mapOf(KEY_AT to decidedAtMillis)
        )
        _state.value = newState
        AnalyticsTracker.applyConsent(newState == ConsentState.GRANTED, reset = wasGranted && newState == ConsentState.DENIED)
    }
}
