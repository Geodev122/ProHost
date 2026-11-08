import SwiftUI

enum CustomButtonVariant {
    case primary, secondary, outlined, tonal, text, success, danger, whatsapp
}

private struct PressFade: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.86 : 1)
    }
}

/// The one button (`CustomButton` on Android): 48 pt (40 compact), 12 pt radius, labelLarge bold, 18 pt icons, disabled at 50%.
struct CustomButton: View {
    let text: String
    var variant: CustomButtonVariant = .primary
    var icon: PHIcon?
    var iconAsset: String?              // full-color brand glyph (Google "G", WhatsApp), drawn untinted
    var trailingIcon: PHIcon?
    var enabled = true
    var isLoading = false
    var compact = false
    var fullWidth = false
    var containerOverride: Color?
    var contentOverride: Color?
    var cornerRadius: CGFloat = Radius.sm
    let action: () -> Void

    private var container: Color {
        if let containerOverride, variant != .outlined { return containerOverride }
        switch variant {
        case .primary: return PH.secondary
        case .secondary: return PH.primary
        case .outlined, .text: return .clear
        case .tonal: return PH.surfaceVariant
        case .success: return PH.success
        case .danger: return PH.error
        case .whatsapp: return PH.whatsAppDarkGreen
        }
    }

    private var content: Color {
        if let contentOverride { return contentOverride }
        switch variant {
        case .primary: return PH.onSecondary
        case .secondary: return PH.onPrimary
        case .outlined, .text: return PH.primary
        case .tonal: return PH.onSurfaceVariant
        case .success: return PH.onSuccess
        case .danger: return PH.onError
        case .whatsapp: return .white
        }
    }

    private var isFilled: Bool { variant != .outlined && variant != .text }
    private var iconSize: CGFloat { compact ? 14 : 18 }
    private var gap: CGFloat { compact ? Spacing.xs : Spacing.sm }
    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: cornerRadius, style: .continuous) }

    var body: some View {
        Button(action: action) {
            HStack(spacing: gap) {
                if isLoading {
                    ProgressView().tint(content).frame(width: iconSize, height: iconSize)
                } else if let iconAsset {
                    Image(iconAsset).resizable().scaledToFit().frame(width: iconSize, height: iconSize)
                } else if let icon {
                    PHIconView(icon: icon, size: iconSize, tint: content)
                }
                Text(text)
                    .phText((compact ? PHType.labelMedium : PHType.labelLarge).weighted(.bold), color: content)
                    .lineLimit(1)
                    .truncationMode(.tail)
                if !isLoading, let trailingIcon {
                    PHIconView(icon: trailingIcon, size: iconSize, tint: content)
                }
            }
            .padding(.horizontal, compact ? 12 : 20)
            .padding(.vertical, compact ? 8 : 12)
            .frame(minHeight: compact ? 40 : 48)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .opacity(enabled ? 1 : (isFilled ? 0.7 : 0.5))
            .background(container.opacity(enabled ? 1 : 0.5), in: shape)
            .overlay {
                if variant == .outlined {
                    shape.stroke(enabled ? (containerOverride ?? PH.primary) : PH.outlineVariant, lineWidth: 1)
                }
            }
            .shadow(color: Color.black.opacity(variant == .primary && enabled ? 0.18 : 0), radius: 2, x: 0, y: 1)
            .contentShape(shape)
        }
        .buttonStyle(PressFade())
        .disabled(!enabled || isLoading)
    }
}

/// Main action: orange fill (`ProPrimaryButton`).
func ProPrimaryButton(
    _ text: String, icon: PHIcon? = nil, enabled: Bool = true, isLoading: Bool = false, fullWidth: Bool = false,
    action: @escaping () -> Void
) -> some View {
    CustomButton(text: text, variant: .primary, icon: icon, enabled: enabled, isLoading: isLoading, fullWidth: fullWidth, action: action)
}

/// Steel-blue outline (`ProOutlinedButton`).
func ProOutlinedButton(
    _ text: String, icon: PHIcon? = nil, enabled: Bool = true, compact: Bool = false, fullWidth: Bool = false,
    borderColor: Color = PH.primary, contentColor: Color = PH.primary, action: @escaping () -> Void
) -> some View {
    CustomButton(
        text: text, variant: .outlined, icon: icon, enabled: enabled, compact: compact, fullWidth: fullWidth,
        containerOverride: borderColor, contentOverride: contentColor, action: action
    )
}
