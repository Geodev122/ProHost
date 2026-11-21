package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberPhoneNumberHint(onNumber: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onUnavailable)
    return { latest.value() }
}

actual val phoneNumberHintAvailable: Boolean = false
