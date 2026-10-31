package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that Material 3's ColorScheme has no slot for (success, warning, info,
 * locked) plus the brand-orange "action" and the neutral shimmer pair. Resolved per
 * theme in [ProHostTheme], so shared components read `MaterialTheme.proColors.success`
 * instead of a fixed brand constant that would stay light-mode-tuned in dark mode.
 *
 * `brandHeader*` is the drawer/hero gradient: always a dark-enough field for `onBrandHeader`
 * (white on #1A4BB8 = 7.66:1 in light; #F0F4FC on #1B2D52 = 12.3:1 in dark).
 * `header{Success,Warning,Error}` are light tints for status text drawn ON that field
 * (>= 4.98:1 on the lightest part of the gradient).
 *
 * Every `on*` pair here is contrast-checked for body text (>= 4.5:1) in its own theme.
 */
@Immutable
data class ProHostColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val locked: Color,
    val onLocked: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val brandHeaderStart: Color,
    val brandHeaderEnd: Color,
    val onBrandHeader: Color,
    val headerSuccess: Color,
    val headerWarning: Color,
    val headerError: Color,
    val isDark: Boolean
)

internal val LightProHostColors = ProHostColors(
    success = StatusSuccessStrong,
    onSuccess = PureWhite,
    successContainer = StatusSuccessContainer,
    onSuccessContainer = StatusOnSuccessContainer,
    warning = StatusWarningStrong,
    onWarning = PureWhite,
    warningContainer = StatusWarningContainer,
    onWarningContainer = StatusOnWarningContainer,
    info = SteelBlue,
    onInfo = PureWhite,
    infoContainer = StatusInfoContainer,
    onInfoContainer = StatusOnInfoContainer,
    locked = OxfordBlue,
    onLocked = PureWhite,
    shimmerBase = LightGray,
    shimmerHighlight = PureWhite.copy(alpha = 0.90f),
    brandHeaderStart = OxfordBlue,
    brandHeaderEnd = SteelBlue,
    onBrandHeader = PureWhite,
    headerSuccess = Color(0xFFA8EEC4),
    headerWarning = Color(0xFFFFD9A0),
    headerError = Color(0xFFFFC2B8),
    isDark = false
)

internal val DarkProHostColors = ProHostColors(
    success = StatusSuccessDarkMode,
    onSuccess = Color(0xFF0B2A17),
    successContainer = Color(0xFF1A3824),
    onSuccessContainer = Color(0xFFA3E8B8),
    warning = StatusWarningDarkMode,
    onWarning = Color(0xFF3A2000),
    warningContainer = Color(0xFF3F2A0E),
    onWarningContainer = Color(0xFFFFDDB3),
    info = StatusInfoDarkMode,
    onInfo = Color(0xFF0E2236),
    infoContainer = BlueDarkModeContainer,
    onInfoContainer = Color(0xFFD3E3F5),
    locked = DarkOutline,
    onLocked = DarkOnSurface,
    shimmerBase = DarkSurfaceVariant,
    shimmerHighlight = DarkOutline,
    brandHeaderStart = BlueDarkModeContainer,
    brandHeaderEnd = DarkSurfaceVariant,
    onBrandHeader = DarkOnSurface,
    headerSuccess = Color(0xFFA8EEC4),
    headerWarning = Color(0xFFFFD9A0),
    headerError = Color(0xFFFFC2B8),
    isDark = true
)

val LocalProHostColors = staticCompositionLocalOf { LightProHostColors }

/** Theme-resolved extended palette: `MaterialTheme.proColors.success`. */
val MaterialTheme.proColors: ProHostColors
    @Composable
    @ReadOnlyComposable
    get() = LocalProHostColors.current
