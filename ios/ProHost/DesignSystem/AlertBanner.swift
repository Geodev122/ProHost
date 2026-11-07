import SwiftUI

enum AlertKind {
    case info, success, warning, error

    var icon: String {
        switch self {
        case .info: return "info.circle.fill"
        case .success: return "checkmark.circle.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .error: return "xmark.octagon.fill"
        }
    }

    var container: Color {
        switch self {
        case .info: return PH.primaryContainer
        case .success: return PH.tertiaryContainer
        case .warning: return PH.warningContainer
        case .error: return PH.errorContainer
        }
    }

    var content: Color {
        switch self {
        case .info: return PH.onPrimaryContainer
        case .success: return PH.onTertiaryContainer
        case .warning: return PH.onWarningContainer
        case .error: return PH.onErrorContainer
        }
    }
}

/// Inline notice (`ProHostAlertBanner` on Android).
struct AlertBanner: View {
    var kind: AlertKind = .info
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: kind.icon)
                .foregroundStyle(kind.content)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(message).phText(PHType.bodyMedium, color: kind.content)
                if let actionTitle, let action {
                    Button(action: action) {
                        Text(actionTitle).phText(PHType.labelLarge, color: kind.content).underline()
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.lg)
        .background(kind.container, in: RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}
