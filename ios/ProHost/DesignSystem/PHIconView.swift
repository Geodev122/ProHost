import SwiftUI

/// Material icon from the generated `PHIcon` set (same artwork as Android), tinted, flipped in right-to-left layouts when Android mirrors it.
struct PHIconView: View {
    let icon: PHIcon
    var size: CGFloat = 24
    var tint: Color = PH.onSurface

    var body: some View {
        Image(icon.rawValue)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(tint)
            .flipsForRightToLeftLayoutDirection(icon.autoMirrored)
            .accessibilityHidden(true)
    }
}
