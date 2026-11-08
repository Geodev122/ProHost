import SwiftUI

struct Choice<T: Hashable>: Identifiable {
    let value: T
    let icon: PHIcon
    let title: String
    let hint: String
    var id: T { value }
}

/// Selectable tile (`ChoiceCard`): 12 pt radius, 2 pt steel-blue outline and tonal fill when selected.
struct ChoiceCard: View {
    let selected: Bool
    let icon: PHIcon
    let title: String
    let hint: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                HStack {
                    PHIconView(icon: icon, size: 22, tint: selected ? PH.onPrimaryContainer : PH.primary)
                    Spacer()
                    if selected { PHIconView(icon: .filledCheckCircle, size: 18, tint: PH.primary) }
                }
                Text(title).phText(PHType.labelLarge.weighted(.bold), color: selected ? PH.onPrimaryContainer : PH.onSurface)
                Text(hint).phText(PHType.bodySmall, color: selected ? PH.onPrimaryContainer : PH.onSurfaceVariant)
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(selected ? PH.primaryContainer : PH.surface, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                    .stroke(selected ? PH.primary : PH.outlineVariant, lineWidth: selected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Two-column grid of choices (`ChoiceGrid`).
struct ChoiceGrid<T: Hashable>: View {
    let choices: [Choice<T>]
    let selected: T
    let onSelect: (T) -> Void

    var body: some View {
        let rows = stride(from: 0, to: choices.count, by: 2).map { Array(choices[$0..<min($0 + 2, choices.count)]) }
        VStack(spacing: Spacing.sm) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: Spacing.sm) {
                    ForEach(row) { choice in
                        ChoiceCard(selected: choice.value == selected, icon: choice.icon, title: choice.title, hint: choice.hint) {
                            onSelect(choice.value)
                        }
                    }
                    if row.count == 1 { Spacer().frame(maxWidth: .infinity) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
