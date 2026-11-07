import SwiftUI

/// Section title with the orange accent bar (`ProSectionHeader` on Android).
struct ProSectionHeader<Trailing: View>: View {
    let title: String
    var subtitle: String?
    var icon: PHIcon?
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .center) {
            HStack(spacing: 0) {
                RoundedRectangle(cornerRadius: Radius.xs, style: .continuous)
                    .fill(PH.secondary)
                    .frame(width: 3, height: 20)
                Spacer().frame(width: Spacing.sm)
                if let icon {
                    PHIconView(icon: icon, size: 18, tint: PH.primary)
                        .frame(width: 32, height: 32)
                        .background(PH.primaryContainer, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                    Spacer().frame(width: Spacing.md)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).phText(PHType.titleLarge).lineLimit(2)
                    if let subtitle, !subtitle.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text(subtitle).phText(PHType.bodySmall, color: PH.onSurfaceVariant)
                    }
                }
            }
            Spacer(minLength: Spacing.sm)
            trailing()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

extension ProSectionHeader where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, icon: PHIcon? = nil) {
        self.init(title: title, subtitle: subtitle, icon: icon) { EmptyView() }
    }
}
