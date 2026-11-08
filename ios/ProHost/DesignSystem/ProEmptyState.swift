import SwiftUI

/// Centered empty/no-results state (`ProEmptyState` on Android).
struct ProEmptyState: View {
    let title: String
    let description: String
    var icon: PHIcon = .filledSearchOff
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 0) {
            PHIconView(icon: icon, size: 32, tint: PH.onSurfaceVariant)
                .frame(width: 64, height: 64)
                .background(PH.surfaceVariant, in: Circle())
            Spacer().frame(height: Spacing.lg)
            Text(title).phText(PHType.titleMedium.weighted(.bold)).multilineTextAlignment(.center)
            Spacer().frame(height: Spacing.sm)
            Text(description).phText(PHType.bodySmall, color: PH.onSurfaceVariant).multilineTextAlignment(.center)
            if let actionTitle, let action {
                Spacer().frame(height: Spacing.lg)
                Button(action: action) {
                    Text(actionTitle).phText(PHType.labelMedium, color: PH.onSecondary)
                        .padding(.horizontal, 24).padding(.vertical, 8)
                        .frame(minHeight: 40)
                        .background(PH.secondary, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity)
    }
}
