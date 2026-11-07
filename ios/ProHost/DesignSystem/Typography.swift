import SwiftUI

/// Type scale (`ui/theme/Type.kt`): Manrope for headings, system font for reading text and labels, 12 pt floor.
struct PHTextStyle {
    let size: CGFloat
    let weight: Font.Weight
    let manrope: Bool
    let relativeTo: Font.TextStyle

    func font() -> Font {
        if manrope {
            let name: String
            switch weight {
            case .heavy, .black: name = "Manrope-ExtraBold"
            case .semibold: name = "Manrope-SemiBold"
            default: name = "Manrope-Bold"
            }
            return Font.custom(name, size: size, relativeTo: relativeTo)
        }
        return Font.system(size: size, weight: weight)
    }
}

enum PHType {
    static let displayLarge = PHTextStyle(size: 36, weight: .heavy, manrope: true, relativeTo: .largeTitle)
    static let displayMedium = PHTextStyle(size: 32, weight: .heavy, manrope: true, relativeTo: .largeTitle)
    static let displaySmall = PHTextStyle(size: 28, weight: .heavy, manrope: true, relativeTo: .title)
    static let headlineLarge = PHTextStyle(size: 28, weight: .bold, manrope: true, relativeTo: .title)
    static let headlineMedium = PHTextStyle(size: 24, weight: .bold, manrope: true, relativeTo: .title2)
    static let headlineSmall = PHTextStyle(size: 20, weight: .bold, manrope: true, relativeTo: .title3)
    static let titleLarge = PHTextStyle(size: 18, weight: .bold, manrope: true, relativeTo: .headline)
    static let titleMedium = PHTextStyle(size: 16, weight: .semibold, manrope: false, relativeTo: .headline)
    static let titleSmall = PHTextStyle(size: 14, weight: .medium, manrope: false, relativeTo: .subheadline)
    static let bodyLarge = PHTextStyle(size: 16, weight: .regular, manrope: false, relativeTo: .body)
    static let bodyMedium = PHTextStyle(size: 14, weight: .regular, manrope: false, relativeTo: .callout)
    static let bodySmall = PHTextStyle(size: 13, weight: .regular, manrope: false, relativeTo: .footnote)
    static let labelLarge = PHTextStyle(size: 14, weight: .semibold, manrope: false, relativeTo: .subheadline)
    static let labelMedium = PHTextStyle(size: 12, weight: .medium, manrope: false, relativeTo: .caption)
    static let labelSmall = PHTextStyle(size: 12, weight: .medium, manrope: false, relativeTo: .caption2)

    static let all: [PHTextStyle] = [
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall
    ]
}

extension View {
    func phText(_ style: PHTextStyle, color: Color = PH.onSurface) -> some View {
        self.font(style.font()).foregroundStyle(color)
    }
}
