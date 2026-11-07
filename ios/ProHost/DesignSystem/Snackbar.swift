import SwiftUI
import Observation

/// One feedback message (`UiMessages.Message`): `long` keeps it on screen for 10 s and adds a dismiss action when there is no other action.
struct SnackbarMessage: Identifiable {
    let id = UUID()
    let text: String
    var actionLabel: String?
    var onAction: (() -> Void)?
    var long = false
}

/// All feedback goes through here (`UiMessages.show` on Android), never an alert for plain confirmations. Reversible actions pass Undo.
@MainActor
@Observable
final class SnackbarCenter {
    static let shared = SnackbarCenter()
    private(set) var current: SnackbarMessage?
    @ObservationIgnored private var dismissTask: Task<Void, Never>?

    func show(_ text: String, actionLabel: String? = nil, long: Bool = false, onAction: (() -> Void)? = nil) {
        current = SnackbarMessage(text: text, actionLabel: actionLabel, onAction: onAction, long: long)
        dismissTask?.cancel()
        let seconds: UInt64 = (long || actionLabel != nil) ? 10 : 4
        dismissTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: seconds * 1_000_000_000)
            if !Task.isCancelled { self?.current = nil }
        }
    }

    func dismiss() {
        dismissTask?.cancel()
        current = nil
    }
}

/// Material snackbar (`ProHostSnackbarHost`): 12 pt radius, inverse surface, inverse-primary action, 12 pt side margins.
struct SnackbarHost: ViewModifier {
    var center: SnackbarCenter = .shared
    /// Keeps the bar above the floating tab pill.
    var bottomInset: CGFloat = 0

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let message = center.current {
                HStack(spacing: Spacing.md) {
                    Text(message.text).phText(PHType.bodyMedium, color: PH.inverseOnSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if let label = message.actionLabel {
                        Button(label) { message.onAction?(); center.dismiss() }
                            .phText(PHType.labelLarge, color: PH.inversePrimary)
                    } else if message.long {
                        Button { center.dismiss() } label: {
                            PHIconView(icon: .filledClose, size: 24, tint: PH.inverseOnSurface)
                        }
                        .accessibilityLabel(L("dismiss"))
                    }
                }
                .padding(.leading, Spacing.lg).padding(.trailing, Spacing.md).padding(.vertical, Spacing.md)
                .frame(minHeight: 48)
                .background(PH.inverseSurface, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .padding(.horizontal, Spacing.md)
                .padding(.bottom, Spacing.lg + bottomInset)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .accessibilityAddTraits(.updatesFrequently)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: center.current?.id)
    }
}

extension View {
    func snackbarHost(bottomInset: CGFloat = 0) -> some View { modifier(SnackbarHost(bottomInset: bottomInset)) }
}
