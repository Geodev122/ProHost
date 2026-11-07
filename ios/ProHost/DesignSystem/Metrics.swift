import SwiftUI

/// Corner radii (`ui/theme/Radius.kt`).
enum Radius {
    static let xs: CGFloat = 6
    static let sm: CGFloat = 12
    static let md: CGFloat = 20
    static let lg: CGFloat = 28
}

/// Spacing scale (`ui/theme/Spacing.kt`).
enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
}

/// Shadow levels (`ui/theme/Elevation.kt`): level 0 is flat, higher levels add a soft shadow.
enum Elevation {
    static let level0: CGFloat = 0
    static let level1: CGFloat = 1
    static let level2: CGFloat = 3
    static let level3: CGFloat = 6
}

/// Text never follows the system size past Android's cap (1.15), which is roughly iOS "xLarge".
enum TextScale {
    static let maxSize: DynamicTypeSize = .xLarge
    /// Smallest text size used anywhere (Android floor is 12 sp).
    static let minPointSize: CGFloat = 12
}
