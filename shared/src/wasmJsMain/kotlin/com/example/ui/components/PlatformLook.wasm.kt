package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.model.Country

@Composable
actual fun rememberCountryDisplayName(): (Country) -> String = { it.name }

@Composable
actual fun rememberReducedMotion(): Boolean = false

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun rememberScreenHeightDp(): Int {
    val height = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    return (height / density).toInt()
}
