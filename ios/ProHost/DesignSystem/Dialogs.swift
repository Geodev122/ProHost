import SwiftUI

/// Material alert dialog (`ProHostDialog`): 20 pt radius surface, optional icon, title, text, end-aligned actions over a 32% scrim.
struct ProHostDialogContent<Confirm: View, Dismiss: View>: View {
    var icon: PHIcon?
    var title: String?
    var message: String?
    @ViewBuilder var confirm: () -> Confirm
    @ViewBuilder var dismiss: () -> Dismiss

    var body: some View {
        VStack(alignment: icon == nil ? .leading : .center, spacing: 0) {
            if let icon {
                PHIconView(icon: icon, size: 24, tint: PH.primary).padding(.bottom, 16)
            }
            if let title {
                Text(title).phText(PHType.headlineSmall).multilineTextAlignment(icon == nil ? .leading : .center)
                    .frame(maxWidth: .infinity, alignment: icon == nil ? .leading : .center).padding(.bottom, 16)
            }
            if let message {
                Text(message).phText(PHType.bodyMedium, color: PH.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.bottom, 24)
            }
            HStack(spacing: Spacing.sm) {
                Spacer(minLength: 0)
                dismiss()
                confirm()
            }
        }
        .padding(24)
        .frame(minWidth: 280, maxWidth: 560)
        .background(PH.surface, in: RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        .shadow(color: Color.black.opacity(0.18), radius: Elevation.level3, x: 0, y: 2)
        .padding(.horizontal, 24)
    }
}

/// Text action used inside dialogs (`TextButton` on Android).
struct DialogTextButton: View {
    let title: String
    var color: Color = PH.primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).phText(PHType.labelLarge, color: color).padding(.horizontal, 12).frame(minHeight: 40)
        }
        .buttonStyle(.plain)
    }
}

private struct DialogPresenter<Confirm: View, Dismiss: View>: ViewModifier {
    @Binding var isPresented: Bool
    var icon: PHIcon?
    var title: String?
    var message: String?
    var dismissOnOutsideTap = true
    @ViewBuilder var confirm: () -> Confirm
    @ViewBuilder var dismiss: () -> Dismiss

    func body(content: Content) -> some View {
        content.overlay {
            if isPresented {
                ZStack {
                    Color.black.opacity(0.32).ignoresSafeArea()
                        .onTapGesture { if dismissOnOutsideTap { isPresented = false } }
                    ProHostDialogContent(icon: icon, title: title, message: message, confirm: confirm, dismiss: dismiss)
                }
                .transition(.opacity)
                .accessibilityAddTraits(.isModal)
            }
        }
        .animation(.easeOut(duration: 0.18), value: isPresented)
    }
}

extension View {
    /// Attach once per screen; shows the Material-style dialog when `isPresented` is true.
    func proHostDialog<Confirm: View, Dismiss: View>(
        isPresented: Binding<Bool>, icon: PHIcon? = nil, title: String? = nil, message: String? = nil,
        dismissOnOutsideTap: Bool = true,
        @ViewBuilder confirm: @escaping () -> Confirm, @ViewBuilder dismiss: @escaping () -> Dismiss
    ) -> some View {
        modifier(DialogPresenter(isPresented: isPresented, icon: icon, title: title, message: message,
                                 dismissOnOutsideTap: dismissOnOutsideTap, confirm: confirm, dismiss: dismiss))
    }
}

/// Pre/post permission prompt in the Android 12 permission-dialog look (`SystemStylePermissionDialog`): centred icon, title, stacked equal choices.
struct SystemStylePermissionDialog<Extra: View>: View {
    let icon: PHIcon
    let title: String
    var message: String?
    var allowLabel = "Allow"
    var denyLabel = "Don't allow"
    let onAllow: () -> Void
    let onDeny: () -> Void
    @ViewBuilder var extra: () -> Extra

    var body: some View {
        ZStack {
            Color.black.opacity(0.32).ignoresSafeArea()
            VStack(spacing: Spacing.lg) {
                PHIconView(icon: icon, size: 28, tint: PH.primary)
                Text(title).phText(PHType.titleLarge).multilineTextAlignment(.center)
                if let message {
                    Text(message).phText(PHType.bodyMedium, color: PH.onSurfaceVariant).multilineTextAlignment(.center)
                }
                extra()
                VStack(spacing: Spacing.sm) {
                    choice(allowLabel, onAllow)
                    choice(denyLabel, onDeny)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(PH.surfaceContainerHigh, in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
            .shadow(color: Color.black.opacity(0.18), radius: Elevation.level3, x: 0, y: 2)
            .padding(.horizontal, 24)
        }
    }

    private func choice(_ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).phText(PHType.labelLarge.weighted(.semibold), color: PH.primary)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(PH.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

extension SystemStylePermissionDialog where Extra == EmptyView {
    init(icon: PHIcon, title: String, message: String? = nil, allowLabel: String = "Allow", denyLabel: String = "Don't allow",
         onAllow: @escaping () -> Void, onDeny: @escaping () -> Void) {
        self.init(icon: icon, title: title, message: message, allowLabel: allowLabel, denyLabel: denyLabel,
                  onAllow: onAllow, onDeny: onDeny) { EmptyView() }
    }
}
