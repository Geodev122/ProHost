package com.example.analytics

import android.content.Context
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

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString(KEY_VERSION, null)
        val stored = prefs.getString(KEY_STATE, null)
            ?.let { runCatching { ConsentState.valueOf(it) }.getOrNull() }
        _state.value = if (version == POLICY_VERSION && stored != null) stored else ConsentState.UNKNOWN
        decidedAtMillis = prefs.getLong(KEY_AT, 0L)
        AnalyticsTracker.applyConsent(_state.value == ConsentState.GRANTED, reset = false)
    }

    fun grant(context: Context) = set(context, ConsentState.GRANTED)

    fun deny(context: Context) = set(context, ConsentState.DENIED)

    private fun set(context: Context, newState: ConsentState) {
        val wasGranted = _state.value == ConsentState.GRANTED
        decidedAtMillis = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, newState.name)
            .putString(KEY_VERSION, POLICY_VERSION)
            .putLong(KEY_AT, decidedAtMillis)
            .apply()
        _state.value = newState
        AnalyticsTracker.applyConsent(newState == ConsentState.GRANTED, reset = wasGranted && newState == ConsentState.DENIED)
    }
}
