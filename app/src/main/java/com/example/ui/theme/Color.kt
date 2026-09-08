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
// - Vibrant Blue (#246BEE): Icons, Brand Accent, Location Badges
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
val VibrantBlue = Color(0xFF246BEE)            // Icons, illustrations, branding accents (#246BEE)
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

val PremiumBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        PureWhite,
        LightGraySurface,
        LightGray.copy(alpha = 0.40f)
    )
)

val ProAIGradient = Brush.linearGradient(
    colors = listOf(
        VibrantBlue,
        CarnationOrange
    )
)

// Regional & Lebanese Payment / Syndicate Colors
val LebaneseCedarGreen = FreshGreen
val LebaneseCedarContainer = Color(0xFFE8F6ED)
val WhishRed = CrimsonRed
val WhishRedContainer = Color(0xFFFDE8E8)
// Whish's own merchant brand red (#E2001A) — distinct from the app's semantic
// StatusError/CrimsonRed; used only for Whish-branded UI chrome (payment sheet).
val WhishBrandRed = Color(0xFFE2001A)
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

val StatusInfo = VibrantBlue
val StatusInfoContainer = Color(0xFFE8F0FD)
val StatusOnInfoContainer = Color(0xFF123B82)

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

// Surfaces & Outlines
val BackgroundLight = PureWhite
val SurfaceLight = PureWhite
val SurfaceVariantLight = LightGray
val CardBorderLight = LightGrayCardBorder
val OutlineVariantLight = LightGray
val CardShadowLight = Color(0x14283544)

// Backward Compatibility Aliases
val ProTealDark = OxfordBlue
val ProTealPrimary = VibrantBlue
val ProTealLight = VibrantBlue
val ProTealContainer = Color(0xFFE8F0FD)
val ProOnTealContainer = Color(0xFF123B82)
val SandstonePrimary = CarnationOrange
val SandstoneDark = CarnationOrangeDark
val SandstoneLight = CarnationOrangeLight
val SandstoneContainer = CarnationOrangeContainer

val ClinicalBluePrimary = OxfordBlue
val ClinicalBlueDark = CoolGray
val ClinicalBlueDarkNavy = CoolGrayDark
val ClinicalBlueLight = VibrantBlue
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
