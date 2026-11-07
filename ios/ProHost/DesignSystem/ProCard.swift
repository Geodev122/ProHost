import SwiftUI

enum CardVariant {
    case elevated, outlined, filled
}

/// The one card (`ProCard` on Android): 28 pt radius, 1 pt outline, soft shadow only for `.elevated`.
struct ProCard<Content: View>: View {
    var variant: CardVariant = .elevated
    var padding: CGFloat = Spacing.lg
    var action: (() -> Void)?
    @ViewBuilder var content: () -> Content

    var body: some View {
        if let action {
            Button(action: action) { card }.buttonStyle(.plain)
        } else {
            card
        }
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) { content() }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(variant == .filled ? PH.surfaceContainerLow : PH.surface, in: shape)
            .overlay {
                if variant != .filled {
                    shape.stroke(PH.outlineVariant.opacity(0.7), lineWidth: 1)
                }
            }
            .shadow(color: Color.black.opacity(variant == .elevated ? 0.06 : 0), radius: Elevation.level2, x: 0, y: 1)
    }
}
