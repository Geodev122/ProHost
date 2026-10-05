package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Official ProHost Material 3 Light ColorScheme
 * Primary: Steel Blue (#2B5A8C) - navigation, focus, links, key actions (dark mode #93B8E0)
 * Secondary: Carnation Orange, action shade (#D2432F) - CTAs, highlights (white text 4.58:1)
 * Tertiary: Fresh Green (#4CAF72)
 * Surface: Pure White (#FFFFFF) / Light Gray (#E2E4E8)
 * OnSurface / Text: Cool Gray (#283544)
 */
private val LightColorScheme = lightColorScheme(
    primary = SteelBlue,
    onPrimary = PureWhite,
    primaryContainer = StatusInfoContainer,
    onPrimaryContainer = StatusOnInfoContainer,
    inversePrimary = BlueDarkMode,

    secondary = CarnationOrangeAction,
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
    surfaceTint = SteelBlue,
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
    primary = BlueDarkMode,
    onPrimary = Color(0xFF0E2236),
    primaryContainer = BlueDarkModeContainer,
    onPrimaryContainer = Color(0xFFD3E3F5),
    inversePrimary = SteelBlue,

    secondary = OrangeDarkMode,
    onSecondary = Color(0xFF3A0D06),
    secondaryContainer = OrangeDarkModeContainer,
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
    surfaceTint = BlueDarkMode,
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
 * Page background for every screen: the theme gradient plus, in light theme only, a faint
 * staggered dot texture (Steel Blue at ~4%) so white pages read as paper rather than flat
 * grey. The texture is one small tile drawn once and repeated by a shader, so scrolling
 * content never redraws it dot by dot. Dark theme keeps its plain gradient.
 */
@Composable
fun Modifier.proHostScreenBackground(): Modifier {
    val brush = premiumBackgroundBrush()
    val light = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val dot = MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
    val base = this.background(brush)
    if (!light) return base
    return base.drawWithCache {
        val step = 12.dp.toPx().coerceAtLeast(8f)
        val radius = 0.9.dp.toPx().coerceAtLeast(1f)
        val tileSize = (step * 2).toInt().coerceAtLeast(2)
        val tile = ImageBitmap(tileSize, tileSize)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(tile), Size(tileSize.toFloat(), tileSize.toFloat())) {
            drawCircle(dot, radius, Offset(step * 0.5f, step * 0.5f))
            drawCircle(dot, radius, Offset(step * 1.5f, step * 1.5f))
        }
        val texture = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        onDrawBehind { drawRect(texture) }
    }
}

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

    CompositionLocalProvider(
        LocalProHostColors provides if (darkTheme) DarkProHostColors else LightProHostColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

