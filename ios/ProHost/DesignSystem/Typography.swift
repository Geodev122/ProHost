import SwiftUI
import UIKit

/// Type scale (`ui/theme/Type.kt`): Manrope for headings, Roboto (Android's default font) for reading text and labels,
/// same sizes, line heights and tracking, 12 pt floor.
struct PHTextStyle {
    let size: CGFloat
    let lineHeight: CGFloat
    let weight: Font.Weight
    let manrope: Bool
    let tracking: CGFloat
    let relativeTo: Font.TextStyle

    /// PostScript name of the bundled font file for this style.
    var fontName: String {
        if manrope {
            switch weight {
            case .heavy, .black: return "Manrope-ExtraBold"
            case .semibold: return "Manrope-SemiBold"
            default: return "Manrope-Bold"
            }
        }
        switch weight {
        case .regular, .light, .thin, .ultraLight: return "Roboto-Regular"
        case .medium: return "Roboto-Medium"
        default: return "Roboto-Bold" // Roboto has no 600; Android resolves semibold to the heavier neighbour
        }
    }

    func font() -> Font {
        Font.custom(fontName, size: size, relativeTo: relativeTo)
    }

    /// Extra space between lines so the line box equals Android's `lineHeight`.
    var lineSpacing: CGFloat {
        let natural = UIFont(name: fontName, size: size)?.lineHeight ?? size * 1.2
        return max(0, lineHeight - natural)
    }
}

enum PHType {
    static let displayLarge = PHTextStyle(size: 36, lineHeight: 44, weight: .heavy, manrope: true, tracking: -0.25, relativeTo: .largeTitle)
    static let displayMedium = PHTextStyle(size: 32, lineHeight: 40, weight: .heavy, manrope: true, tracking: -0.2, relativeTo: .largeTitle)
    static let displaySmall = PHTextStyle(size: 28, lineHeight: 36, weight: .heavy, manrope: true, tracking: -0.15, relativeTo: .title)
    static let headlineLarge = PHTextStyle(size: 28, lineHeight: 36, weight: .bold, manrope: true, tracking: -0.15, relativeTo: .title)
    static let headlineMedium = PHTextStyle(size: 24, lineHeight: 32, weight: .bold, manrope: true, tracking: 0, relativeTo: .title2)
    static let headlineSmall = PHTextStyle(size: 20, lineHeight: 28, weight: .bold, manrope: true, tracking: 0, relativeTo: .title3)
    static let titleLarge = PHTextStyle(size: 18, lineHeight: 24, weight: .bold, manrope: true, tracking: 0, relativeTo: .headline)
    static let titleMedium = PHTextStyle(size: 16, lineHeight: 22, weight: .semibold, manrope: false, tracking: 0.1, relativeTo: .headline)
    static let titleSmall = PHTextStyle(size: 14, lineHeight: 20, weight: .medium, manrope: false, tracking: 0.1, relativeTo: .subheadline)
    static let bodyLarge = PHTextStyle(size: 16, lineHeight: 24, weight: .regular, manrope: false, tracking: 0.15, relativeTo: .body)
    static let bodyMedium = PHTextStyle(size: 14, lineHeight: 20, weight: .regular, manrope: false, tracking: 0.15, relativeTo: .callout)
    static let bodySmall = PHTextStyle(size: 13, lineHeight: 18, weight: .regular, manrope: false, tracking: 0.2, relativeTo: .footnote)
    static let labelLarge = PHTextStyle(size: 14, lineHeight: 20, weight: .semibold, manrope: false, tracking: 0.1, relativeTo: .subheadline)
    static let labelMedium = PHTextStyle(size: 12, lineHeight: 16, weight: .medium, manrope: false, tracking: 0.4, relativeTo: .caption)
    static let labelSmall = PHTextStyle(size: 12, lineHeight: 16, weight: .medium, manrope: false, tracking: 0.5, relativeTo: .caption2)

    static let all: [PHTextStyle] = [
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall
    ]
}

extension View {
    func phText(_ style: PHTextStyle, color: Color = PH.onSurface) -> some View {
        self.font(style.font())
            .tracking(style.tracking)
            .lineSpacing(style.lineSpacing)
            .foregroundStyle(color)
    }
}
