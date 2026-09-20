package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Official ProHost Material 3 Light ColorScheme
 * Primary: Oxford Blue (#384152)
 * Secondary: Carnation Orange (#F25F4C)
 * Tertiary: Fresh Green (#4CAF72)
 * Surface: Pure White (#FFFFFF) / Light Gray (#E2E4E8)
 * OnSurface / Text: Cool Gray (#283544)
 */
private val LightColorScheme = lightColorScheme(
    primary = OxfordBlue,
    onPrimary = PureWhite,
    primaryContainer = OxfordBlueContainer,
    onPrimaryContainer = OxfordBlueDark,
    inversePrimary = CarnationOrangeLight,

    secondary = CarnationOrange,
    onSecondary = PureWhite,
    secondaryContainer = CarnationOrangeContainer,
    onSecondaryContainer = CarnationOrangeDark,

    tertiary = FreshGreen,
    onTertiary = PureWhite,
    tertiaryContainer = StatusSuccessContainer,
    onTertiaryContainer = StatusOnSuccessContainer,

    background = LightGraySurface,
    onBackground = CoolGray,

    surface = LightGraySurface,
    onSurface = CoolGray,
    surfaceVariant = OxfordBlueContainer,
    onSurfaceVariant = ProHostSecondaryText,
    surfaceTint = OxfordBlue,
    inverseSurface = CoolGrayDark,
    inverseOnSurface = PureWhite,

    outline = LightGrayCardBorder,
    outlineVariant = LightGray,

    error = CrimsonRed,
    onError = PureWhite,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusOnErrorContainer,

    scrim = CoolGrayDark
)

/**
 * Official ProHost Material 3 Dark ColorScheme
 */
private val DarkColorScheme = darkColorScheme(
    primary = OxfordBlueLight,
    onPrimary = PureWhite,
    primaryContainer = OxfordBlue,
    onPrimaryContainer = PureWhite,
    inversePrimary = OxfordBlue,

    secondary = CarnationOrange,
    onSecondary = PureWhite,
    secondaryContainer = CarnationOrangeDark,
    onSecondaryContainer = CarnationOrangeContainer,

    tertiary = FreshGreen,
    onTertiary = PureWhite,
    tertiaryContainer = CoolGray,
    onTertiaryContainer = LightGray,

    background = CoolGrayDark,
    onBackground = PureWhite,

    surface = CoolGray,
    onSurface = PureWhite,
    surfaceVariant = OxfordBlue,
    onSurfaceVariant = LightGray,
    surfaceTint = OxfordBlueLight,
    inverseSurface = LightGraySurface,
    inverseOnSurface = CoolGrayDark,

    outline = OxfordBlueLight,
    outlineVariant = OxfordBlue,

    error = CrimsonRed,
    onError = PureWhite,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusOnErrorContainer,

    scrim = Color.Black
)

/**
 * ProHost Brand Theme
 * Oxford Blue (#384152), Carnation Orange (#F25F4C), Fresh Green (#4CAF72),
 * Cool Gray (#283544), Light Gray (#E2E4E8), and White (#FFFFFF).
 *
 * Follows the device's system dark-mode setting by default via
 * isSystemInDarkTheme(); pass darkTheme explicitly to override (e.g. for a
 * future in-app theme toggle).
 */
@Composable
fun ProHostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

