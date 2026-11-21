package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

// Milestone C3: CLLocationManager authorisation (When In Use) before onReady.
@Composable
actual fun rememberLocationAccess(onReady: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onReady)
    return { latest.value() }
}
