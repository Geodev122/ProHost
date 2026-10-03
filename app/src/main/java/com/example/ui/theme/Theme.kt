package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
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
 * Primary: Vibrant Blue (#246BEE) — focus, selection, active navigation, links
 * Secondary: Carnation Orange (#F25F4C) — key CTAs and highlights
 * Tertiary: Fresh Green (#4CAF72)
 * Neutrals: Oxford Blue / Cool Gray text on white and cool off-white surfaces
 */
private val LightColorScheme = lightColorScheme(
    primary = VibrantBlue,
    onPrimary = PureWhite,
    primaryContainer = VibrantBlueContainer,
    onPrimaryContainer = VibrantBlueDark,
    inversePrimary = DarkPrimary,

    secondary = CarnationOrange,
    onSecondary = PureWhite,
    secondaryContainer = CarnationOrangeContainer,
    onSecondaryContainer = CarnationOrangeDark,

    tertiary = FreshGreen,
    onTertiary = PureWhite,
    tertiaryContainer = StatusSuccessContainer,
    onTertiaryContainer = StatusOnSuccessContainer,

    background = BackgroundLight,
    onBackground = CoolGrayDark,

    surface = SurfaceLight,
    onSurface = CoolGrayDark,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = ProHostSecondaryText,
    // Neutral, not primary: keeps tonal-elevation surfaces (top bar, drawer) clean
    // instead of washing them blue now that primary is the brand blue.
    surfaceTint = OxfordBlue,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,

    outline = CardBorderLight,
    outlineVariant = OutlineVariantLight,

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
    primary = DarkPrimary,
    onPrimary = CoolGrayDark,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnSurface,
    inversePrimary = OxfordBlue,

    secondary = DarkSecondary,
    onSecondary = CoolGrayDark,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = CarnationOrangeContainer,

    tertiary = FreshGreen,
    onTertiary = CoolGrayDark,
    tertiaryContainer = Color(0xFF1A3824),
    onTertiaryContainer = Color(0xFFA3E8B8),

    background = DarkBackground,
    onBackground = DarkOnSurface,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkOutline,
    inverseSurface = SurfaceLight,
    inverseOnSurface = CoolGrayDark,

    outline = DarkOutline,
    outlineVariant = DarkSurfaceVariant,

    error = Color(0xFFFF897A),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color.Black
)

private val PremiumDarkBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        DarkPrimaryContainer.copy(alpha = 0.35f),
        DarkBackground,
        DarkSurfaceVariant.copy(alpha = 0.55f)
    )
)

/**
 * Screen background gradient for the active theme. The light [PremiumBackgroundGradient]
 * alone left dark-theme text (light on light) unreadable.
 */
@Composable
fun premiumBackgroundBrush(): Brush =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) PremiumDarkBackgroundGradient
    else PremiumBackgroundGradient

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

