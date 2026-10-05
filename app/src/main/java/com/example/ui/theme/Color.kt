package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// ==========================================
// Official ProHost Brand Palette
// Defined strictly in brand identity & guidelines:
// - Oxford Blue (#384152): App Bar, Headers, Primary Navy
// - Carnation Orange (#F25F4C): Primary CTA, Booking Highlights
// - Fresh Green (#4CAF72): Availability, Success Badges
// - Cool Gray (#283544): Primary Text, Titles, Secondary Surfaces
// - Light Gray (#E2E4E8): Backgrounds, Cards, Subtle UI borders
// - White (#FFFFFF): Clean surfaces, CTA Text
// - Steel Blue (#2B5A8C): Specialist brand / primary, icons, location badges
// - Bright Orange (#F98B1D): Pending / Warning Badges
// - Crimson Red (#D32F2F): Cancelled / Error Badges
// ==========================================

// Core Brand Primitives
val OxfordBlue = Color(0xFF384152)             // Primary background, headers, app bar (#384152)
val CarnationOrange = Color(0xFFF25F4C)        // Accent for CTAs, highlights, booking confirmations (#F25F4C)
val FreshGreen = Color(0xFF4CAF72)             // Success states, availability indicators (#4CAF72)
val CoolGray = Color(0xFF283544)               // Neutral surfaces, dividers, primary text, titles (#283544)
val LightGray = Color(0xFFE2E4E8)              // Backgrounds, cards, subtle UI (#E2E4E8)
val PureWhite = Color(0xFFFFFFFF)              // Text on dark backgrounds, clean space (#FFFFFF)
val SteelBlue = Color(0xFF2B5A8C)              // Specialist brand / primary (#2B5A8C) — calmer next to the orange than the old #246BEE
val SteelBlueLight = Color(0xFF4A78AB)         // Pressed states, gradient ends
val SteelBlueContainer = Color(0xFFE5EDF6)     // Tonal fills behind Steel Blue content
val BrightOrange = Color(0xFFF98B1D)           // Warning / pending reservation badge (#F98B1D)
val CrimsonRed = Color(0xFFD32F2F)             // Error / cancelled badge (#D32F2F)

// ProHost Derived Tonal Scales
val OxfordBlueLight = Color(0xFF5A667A)
val OxfordBlueDark = Color(0xFF272F3C)
val OxfordBlueContainer = Color(0xFFECEFF4)

val CarnationOrangeLight = Color(0xFFFF8575)
val CarnationOrangeDark = Color(0xFFD44432)
val CarnationOrangeContainer = Color(0xFFFFECE9)

val CoolGrayLight = Color(0xFF435266)
val CoolGrayDark = Color(0xFF1B242F)
val CoolGrayContainer = Color(0xFFE6EAEF)

val LightGrayCardBorder = Color(0xFFD1D5DC)
val LightGraySurface = Color(0xFFF8F9FA)

// Secondary Text Helper (#384152 at 70% opacity)
val ProHostSecondaryText = OxfordBlue.copy(alpha = 0.70f)

// Light page background: white, shading to a whisper of Steel Blue at the bottom. The fine
// texture on top comes from Modifier.proHostScreenBackground() (Theme.kt).
val PremiumBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFBFCFE),
        Color(0xFFF2F5FA)
    )
)

val ProAIGradient = Brush.linearGradient(
    colors = listOf(
        SteelBlue,
        CarnationOrange
    )
)

// Regional & brand-partner colors
val LebaneseCedarGreen = FreshGreen
val LebaneseCedarContainer = Color(0xFFE8F6ED)
val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppDarkGreen = Color(0xFF1E7E34)
val InstagramPink = Color(0xFFE1306C)

// Semantic Status Colors & Containers
val StatusSuccess = FreshGreen
val StatusSuccessContainer = Color(0xFFEAF7EE)
val StatusOnSuccessContainer = Color(0xFF1B5E20)

val StatusWarning = BrightOrange
val StatusWarningContainer = Color(0xFFFEF3E7)
val StatusOnWarningContainer = Color(0xFF8A4805)

val StatusError = CrimsonRed
val StatusErrorContainer = Color(0xFFFDE8E8)
val StatusOnErrorContainer = Color(0xFF781212)

val StatusInfo = SteelBlue
val StatusInfoContainer = SteelBlueContainer
val StatusOnInfoContainer = Color(0xFF173452)

val StatusLocked = CoolGray
val StatusLockedContainer = LightGray

// Neutral Scales
val NeutralGray50 = PureWhite
val NeutralGray100 = LightGraySurface
val NeutralGray200 = LightGray
val NeutralGray300 = LightGrayCardBorder
val NeutralGray400 = Color(0xFF9EA7B4)
val NeutralGray500 = ProHostSecondaryText
val NeutralGray600 = CoolGrayLight
val NeutralGray700 = OxfordBlue
val NeutralGray800 = CoolGray
val NeutralGray900 = CoolGrayDark

// Surfaces & Outlines (Bright Mode)
val BackgroundLight = Color(0xFFFBFCFE)
val SurfaceLight = PureWhite
val SurfaceVariantLight = OxfordBlueContainer
val CardBorderLight = LightGrayCardBorder
val OutlineVariantLight = LightGray
val CardShadowLight = Color(0x14283544)

// Refined Surface & Text Tokens (Dark Mode)
val DarkBackground = Color(0xFF121824)        // Rich deep navy dark background (#121824)
val DarkSurface = Color(0xFF1E2638)           // Elevated dark card surface (#1E2638)
val DarkSurfaceVariant = Color(0xFF28344A)    // Secondary elevated dark container (#28344A)
val DarkOnSurface = Color(0xFFF0F4FC)         // Primary text in dark mode (#F0F4FC)
val DarkOnSurfaceVariant = Color(0xFFA0ACBE)  // Secondary text in dark mode (#A0ACBE)
val DarkOutline = Color(0xFF3B485E)           // Card borders & dividers in dark mode (#3B485E)
val DarkPrimary = Color(0xFF93B8E0)          // Steel Blue for dark mode
val DarkPrimaryContainer = Color(0xFF223048)  // Primary container in dark mode
val DarkSecondary = CarnationOrangeLight     // Warm accent orange in dark mode
val DarkSecondaryContainer = Color(0xFF4A1D17)

// Backward Compatibility Aliases
val ProTealDark = OxfordBlue
val ProTealPrimary = SteelBlue
val ProTealLight = SteelBlue
val ProTealContainer = Color(0xFFE8F0FD)
val ProOnTealContainer = Color(0xFF123B82)
val SandstonePrimary = CarnationOrange
val SandstoneDark = CarnationOrangeDark
val SandstoneLight = CarnationOrangeLight
val SandstoneContainer = CarnationOrangeContainer

val ClinicalBluePrimary = OxfordBlue
val ClinicalBlueDark = CoolGray
val ClinicalBlueDarkNavy = CoolGrayDark
val ClinicalBlueLight = SteelBlue
val ClinicalIceBlue = OxfordBlueContainer
val ClinicalSoftBlueContainer = LightGray
val ClinicalDeepBlue = CoolGray
val NavyDark = CoolGrayDark
val AmberWarning = BrightOrange
val ClinicalSlate = OxfordBlue
val ClinicalSlateLight = ProHostSecondaryText
val ClinicalSlateContainer = LightGray
val ClinicalOnSlate = CoolGray
val ProSlate = CoolGray
val ProSlateLight = ProHostSecondaryText
val ProSlateContainer = LightGray
val ProOnSlateContainer = CoolGray

val BackgroundDark = CoolGrayDark
val SurfaceDark = CoolGray
val SurfaceVariantDark = OxfordBlue
val CardBorderDark = OxfordBlueLight
val OutlineVariantDark = OxfordBlue

val SuperAdminBadgeBg = CarnationOrangeContainer
val SuperAdminBadgeText = CarnationOrangeDark
val SecurityShieldGreen = FreshGreen
val GoldTier = BrightOrange

// ==========================================
// Contrast-audited action / status tokens (WCAG 2.1 ratios, text on fill)
// Light: white on CarnationOrangeAction 4.58:1, white on SteelBlue 4.76:1,
//        white on StatusSuccessStrong 5.05:1, white on StatusWarningStrong 4.61:1
// Dark:  BlueDarkMode on DarkSurface 6.33:1, OrangeDarkMode on DarkSurface 6.58:1
// CarnationOrange (#F25F4C) is only 3.22:1 against white, so it is reserved for
// icons, indicators and large decorative fills (3:1 graphics threshold); filled
// controls that carry text use CarnationOrangeAction.
// ==========================================
val CarnationOrangeAction = Color(0xFFD2432F)
val BlueDarkMode = Color(0xFF93B8E0)
val BlueDarkModeContainer = Color(0xFF1B3047)
val OrangeDarkMode = Color(0xFFFF8A75)
val OrangeDarkModeContainer = Color(0xFF4A1D17)
val StatusSuccessStrong = Color(0xFF2E7D4F)
val StatusWarningStrong = Color(0xFFB35F00)
val StatusSuccessDarkMode = Color(0xFF7DD9A0)
val StatusWarningDarkMode = Color(0xFFFFB866)
val StatusInfoDarkMode = BlueDarkMode

// Listing Type Brand Colors (ST-01 to ST-06)
val MarkerBlueTop = Color(0xFF6E97C4)
val MarkerBlueBase = SteelBlue // Private Office (ST-01)

val MarkerOrangeTop = Color(0xFFFF8F73)
val MarkerOrangeBase = Color(0xFFF25F4C) // Center (ST-02)

val MarkerGreenTop = Color(0xFF7DD9A0)
val MarkerGreenBase = Color(0xFF4CAF72) // Polyclinic (ST-03)

val MarkerVioletTop = Color(0xFFB197FC)
val MarkerVioletBase = Color(0xFF8B5CF6) // Co-working Space (ST-04)

val MarkerGoldTop = Color(0xFFE8C468)
val MarkerGoldBase = Color(0xFFC99A2E) // Executive Boardroom (ST-05)

val MarkerTealTop = Color(0xFF5CE0D0)
val MarkerTealBase = Color(0xFF14B8A6) // Consultation Suite (ST-06)

