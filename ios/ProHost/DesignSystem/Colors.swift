import SwiftUI
import UIKit

extension Color {
    /// sRGB color from 0xRRGGBB.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1
        )
    }

    /// Appearance-aware color (light and dark 0xRRGGBB).
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            UIColor(Color(hex: traits.userInterfaceStyle == .dark ? dark : light))
        })
    }
}

/// ProHost color roles. Values mirror `ui/theme/Theme.kt` (Material 3 light and dark schemes); never use literal hex in screens.
enum PH {
    // Brand
    static let primary = Color(light: 0x2B5A8C, dark: 0x93B8E0)               // Steel Blue
    static let onPrimary = Color(light: 0xFFFFFF, dark: 0x0E2236)
    static let primaryContainer = Color(light: 0xE5EDF6, dark: 0x1B3047)
    static let onPrimaryContainer = Color(light: 0x173452, dark: 0xD3E3F5)

    // Main action color (orange): buttons, ribbons
    static let secondary = Color(light: 0xD2432F, dark: 0xFF8A75)
    static let onSecondary = Color(light: 0xFFFFFF, dark: 0x3A0D06)
    static let secondaryContainer = Color(light: 0xFFECE9, dark: 0x4A1D17)
    static let onSecondaryContainer = Color(light: 0xD44432, dark: 0xFFECE9)

    // Success
    static let tertiary = Color(light: 0x2E7D4F, dark: 0x4CAF72)
    static let onTertiary = Color(light: 0xFFFFFF, dark: 0x1B242F)
    static let tertiaryContainer = Color(light: 0xEAF7EE, dark: 0x1A3824)
    static let onTertiaryContainer = Color(light: 0x1B5E20, dark: 0xA3E8B8)

    // Surfaces
    static let background = Color(light: 0xFBFCFE, dark: 0x121824)
    static let onBackground = Color(light: 0x1B242F, dark: 0xF0F4FC)
    static let surface = Color(light: 0xFFFFFF, dark: 0x1E2638)
    static let onSurface = Color(light: 0x1B242F, dark: 0xF0F4FC)
    static let surfaceVariant = Color(light: 0xECEFF4, dark: 0x28344A)
    static let onSurfaceVariant = Color(light: 0x5A6372, dark: 0xB4BDCC)
    static let surfaceContainerLowest = Color(light: 0xFFFFFF, dark: 0x0F1420)
    static let surfaceContainerLow = Color(light: 0xF6F8FB, dark: 0x171E2C)
    static let surfaceContainer = Color(light: 0xF0F3F8, dark: 0x1B2333)
    static let surfaceContainerHigh = Color(light: 0xEAEEF4, dark: 0x222B3D)
    static let surfaceContainerHighest = Color(light: 0xE3E8F0, dark: 0x2A3447)
    static let outline = Color(light: 0xD1D5DC, dark: 0x3B485E)
    static let outlineVariant = Color(light: 0xE2E4E8, dark: 0x28344A)

    // Error
    static let error = Color(light: 0xD32F2F, dark: 0xFF897A)
    static let onError = Color(light: 0xFFFFFF, dark: 0x680003)
    static let errorContainer = Color(light: 0xFDE8E8, dark: 0x8C1D18)
    static let onErrorContainer = Color(light: 0x781212, dark: 0xFFDAD6)

    // Semantic status colors (`ProHostColors.kt`)
    static let success = Color(light: 0x2E7D4F, dark: 0x7DD9A0)
    static let onSuccess = Color(light: 0xFFFFFF, dark: 0x0B2A17)
    static let successContainer = Color(light: 0xEAF7EE, dark: 0x1A3824)
    static let onSuccessContainer = Color(light: 0x1B5E20, dark: 0xA3E8B8)
    static let warning = Color(light: 0xB35F00, dark: 0xFFB866)
    static let onWarning = Color(light: 0xFFFFFF, dark: 0x3A2000)
    static let warningContainer = Color(light: 0xFEF3E7, dark: 0x3F2A0E)
    static let onWarningContainer = Color(light: 0x8A4805, dark: 0xFFDDB3)
    static let info = Color(light: 0x2B5A8C, dark: 0x93B8E0)
    static let onInfo = Color(light: 0xFFFFFF, dark: 0x0E2236)
    static let infoContainer = Color(light: 0xE5EDF6, dark: 0x1B3047)
    static let onInfoContainer = Color(light: 0x173452, dark: 0xD3E3F5)
    static let locked = Color(light: 0x384152, dark: 0x3B485E)
    static let onLocked = Color(light: 0xFFFFFF, dark: 0xF0F4FC)
    static let shimmerBase = Color(light: 0xE2E4E8, dark: 0x28344A)
    static let shimmerHighlight = Color(light: 0xFFFFFF, dark: 0x3B485E)
    static let brandHeaderStart = Color(light: 0x384152, dark: 0x1B3047)
    static let brandHeaderEnd = Color(light: 0x2B5A8C, dark: 0x28344A)
    static let onBrandHeader = Color(light: 0xFFFFFF, dark: 0xF0F4FC)

    // Third-party brand colors used on specific buttons only
    static let whatsAppGreen = Color(hex: 0x25D366)
    static let whatsAppDarkGreen = Color(hex: 0x1E7E34)
}
