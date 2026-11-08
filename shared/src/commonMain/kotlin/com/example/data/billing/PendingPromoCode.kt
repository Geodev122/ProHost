package com.example.data.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A promo code that arrived by link (prohost://redeem?code=XXXX), waiting for the
 * Subscriptions screen to open its Redeem dialog pre-filled.
 */
object PendingPromoCode {
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    /** Play promo codes are letters and digits; anything else is dropped. */
    fun sanitize(raw: String?): String? =
        raw?.uppercase()?.filter { it.isLetterOrDigit() }?.take(32)?.ifBlank { null }

    fun offer(raw: String?) {
        sanitize(raw)?.let { _code.value = it }
    }

    fun consume(): String? = _code.value.also { _code.value = null }
}
