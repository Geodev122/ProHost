import SwiftUI

/// Main action button: orange fill, 12 pt radius, 48 pt minimum height (`ProPrimaryButton` on Android).
struct ProPrimaryButton: View {
    let title: LocalizedStringKey
    var isLoading = false
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Text(title)
                    .phText(PHType.labelLarge, color: PH.onSecondary)
                    .opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView().tint(PH.onSecondary)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 48)
        }
        .background(
            PH.secondary.opacity(isEnabled ? 1 : 0.4),
            in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
        )
        .disabled(!isEnabled || isLoading)
    }
}

/// Quiet alternative: steel-blue outline.
struct ProSecondaryButton: View {
    let title: LocalizedStringKey
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .phText(PHType.labelLarge, color: PH.primary)
                .frame(maxWidth: .infinity, minHeight: 48)
        }
        .overlay(
            RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                .stroke(PH.primary.opacity(isEnabled ? 1 : 0.4), lineWidth: 1)
        )
        .disabled(!isEnabled)
    }
}
