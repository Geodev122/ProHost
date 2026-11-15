package com.example.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

val Typography = proHostTypography(ManropeDisplay)
