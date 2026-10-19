package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Manrope carries the brand on every heading role (display, headline, titleLarge); reading text and labels stay on
 * the system sans. Scale (docs/design/UI_AUDIT.md): headings shrink monotonically — titleLarge is never bigger than
 * headlineSmall — and nothing goes below 12 sp.
 */
val ManropeDisplay = FontFamily(
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

private fun manrope(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = ManropeDisplay, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp
)

private fun system(size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp
)

val Typography = Typography(
    displayLarge = manrope(36, 44, FontWeight.ExtraBold, -0.25),
    displayMedium = manrope(32, 40, FontWeight.ExtraBold, -0.2),
    displaySmall = manrope(28, 36, FontWeight.ExtraBold, -0.15),
    headlineLarge = manrope(28, 36, FontWeight.Bold, -0.15),
    headlineMedium = manrope(24, 32, FontWeight.Bold),
    headlineSmall = manrope(20, 28, FontWeight.Bold),
    titleLarge = manrope(18, 24, FontWeight.SemiBold),
    titleMedium = system(16, 22, FontWeight.SemiBold, 0.1),
    titleSmall = system(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = system(16, 24, FontWeight.Normal, 0.15),
    bodyMedium = system(14, 20, FontWeight.Normal, 0.15),
    bodySmall = system(13, 18, FontWeight.Normal, 0.2),
    labelLarge = system(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = system(12, 16, FontWeight.Medium, 0.4),
    labelSmall = system(12, 16, FontWeight.Medium, 0.5)
)
