package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.data.model.Country
import com.example.ui.util.AppLanguage
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberCountryDisplayName(): (Country) -> String {
    val language = AppLanguage.current().ifBlank { "en" }
    return remember(language) {
        { country: Country ->
            if (language == "en") country.name
            else NSLocale(localeIdentifier = language).displayNameForKey(NSLocaleCountryCode, country.isoCode)
                ?.takeIf { it.isNotBlank() } ?: country.name
        }
    }
}

@Composable
actual fun rememberReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
