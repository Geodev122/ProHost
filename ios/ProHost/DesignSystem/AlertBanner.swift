import SwiftUI

enum ProHostAlertSeverity {
    case info, success, warning, error
}

/// Inline notice (`ProHostAlertBanner` on Android): 12 pt radius, 1 pt border at 25% accent, accent icon, optional title and action.
struct ProHostAlertBanner<Action: View>: View {
    let message: String
    var severity: ProHostAlertSeverity = .info
    var title: String?
    var icon: PHIcon?
    var fullBleed = false
    @ViewBuilder var action: () -> Action

    private var tone: (container: Color, content: Color, accent: Color, icon: PHIcon) {
        switch severity {
        case .info: return (PH.infoContainer, PH.onInfoContainer, PH.info, .filledInfo)
        case .success: return (PH.successContainer, PH.onSuccessContainer, PH.success, .filledCheckCircle)
        case .warning: return (PH.warningContainer, PH.onWarningContainer, PH.warning, .filledWarningAmber)
        case .error: return (PH.errorContainer, PH.onErrorContainer, PH.error, .filledErrorOutline)
        }
    }

    var body: some View {
        let t = tone
        let shape = RoundedRectangle(cornerRadius: fullBleed ? 0 : Radius.sm, style: .continuous)
        HStack(spacing: Spacing.md) {
            PHIconView(icon: icon ?? t.icon, size: 20, tint: t.accent)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if let title, !title.isEmpty {
                    Text(title).phText(PHType.titleSmall, color: t.content)
                }
                Text(message).phText(PHType.bodySmall, color: t.content)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            action()
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .frame(maxWidth: .infinity)
        .background(t.container, in: shape)
        .overlay { if !fullBleed { shape.stroke(t.accent.opacity(0.25), lineWidth: 1) } }
        .accessibilityElement(children: .combine)
    }
}

extension ProHostAlertBanner where Action == EmptyView {
    init(message: String, severity: ProHostAlertSeverity = .info, title: String? = nil, icon: PHIcon? = nil, fullBleed: Bool = false) {
        self.init(message: message, severity: severity, title: title, icon: icon, fullBleed: fullBleed) { EmptyView() }
    }
}
