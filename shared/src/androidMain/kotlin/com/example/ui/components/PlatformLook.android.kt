package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.Country

@Composable
actual fun rememberCountryDisplayName(): (Country) -> String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { { country: Country -> country.displayName(locale) } }
}

/** [Country]'s name for [locale] (the app drawer passes its own locale). */
fun Country.displayName(locale: java.util.Locale): String =
    if (locale.language == "en") name
    else java.util.Locale("", isoCode).getDisplayCountry(locale).ifBlank { name }

@Composable
actual fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
    }
}

@Composable
actual fun rememberScreenHeightDp(): Int = LocalConfiguration.current.screenHeightDp
