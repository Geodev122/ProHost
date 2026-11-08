import SwiftUI

enum CardVariant {
    case elevated, outlined, filled
}

/// The one card (`ProCard` on Android): `MaterialTheme.shapes.large` (20 pt), 1 pt outline at 70%, 1 pt elevation shadow only for `.elevated`.
struct ProCard<Content: View>: View {
    var variant: CardVariant = .elevated
    var padding: CGFloat = Spacing.lg
    var containerColor: Color?
    var borderColor: Color = PH.outlineVariant.opacity(0.7)
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
        RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(containerColor ?? (variant == .filled ? PH.surfaceContainerLow : PH.surface), in: shape)
            .overlay {
                if variant != .filled {
                    shape.stroke(borderColor, lineWidth: 1)
                }
            }
            .shadow(color: Color.black.opacity(variant == .elevated ? 0.08 : 0), radius: Elevation.level1, x: 0, y: 1)
    }
}

/// `ProSurfaceCard`: outlined card (`ProCard` delegate on Android).
func ProSurfaceCard<Content: View>(
    padding: CGFloat = Spacing.lg,
    containerColor: Color = PH.surface,
    borderColor: Color = PH.outlineVariant.opacity(0.6),
    action: (() -> Void)? = nil,
    @ViewBuilder content: @escaping () -> Content
) -> some View {
    ProCard(variant: .outlined, padding: padding, containerColor: containerColor, borderColor: borderColor, action: action, content: content)
}
