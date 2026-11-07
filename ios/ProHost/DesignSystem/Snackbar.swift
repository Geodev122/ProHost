import SwiftUI
import Observation

struct SnackbarMessage: Identifiable {
    let id = UUID()
    let text: String
    var actionTitle: String?
    var action: (() -> Void)?
}

/// Feedback goes through here (`UiMessages` on Android), never an alert for plain confirmations. Reversible actions pass an Undo action.
@MainActor
@Observable
final class SnackbarCenter {
    static let shared = SnackbarCenter()
    private(set) var current: SnackbarMessage?
    private var dismissTask: Task<Void, Never>?

    func show(_ text: String, actionTitle: String? = nil, action: (() -> Void)? = nil) {
        current = SnackbarMessage(text: text, actionTitle: actionTitle, action: action)
        dismissTask?.cancel()
        dismissTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled { self?.current = nil }
        }
    }

    func dismiss() {
        dismissTask?.cancel()
        current = nil
    }
}

struct SnackbarHost: ViewModifier {
    var center: SnackbarCenter = .shared
    /// Keeps the bar above the floating tab pill.
    var bottomInset: CGFloat = 0

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let message = center.current {
                HStack(spacing: Spacing.md) {
                    Text(message.text).phText(PHType.bodyMedium, color: PH.background)
                    Spacer(minLength: Spacing.sm)
                    if let title = message.actionTitle, let action = message.action {
                        Button(title) { action(); center.dismiss() }
                            .phText(PHType.labelLarge, color: PH.primary)
                    }
                }
                .padding(Spacing.lg)
                .background(PH.onSurface, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .padding(.horizontal, Spacing.lg)
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
