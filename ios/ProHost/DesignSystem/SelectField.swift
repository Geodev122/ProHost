import SwiftUI

/// Single-choice dropdown (`SelectField`): a read-only `InputField` with an arrow that opens a menu (max 360 pt) with a tick on the selection.
struct SelectField<T: Hashable>: View {
    let label: String
    let options: [T]
    let selected: T?
    let optionLabel: (T) -> String
    let onSelect: (T) -> Void
    var placeholder = ""

    @State private var expanded = false

    var body: some View {
        InputField(
            label: label,
            text: .constant(selected.map(optionLabel) ?? ""),
            placeholder: placeholder,
            trailing: AnyView(PHIconView(icon: expanded ? .filledArrowDropUp : .filledArrowDropDown, size: 24, tint: PH.onSurfaceVariant)),
            readOnly: true
        )
        .overlay { Color.clear.contentShape(Rectangle()).onTapGesture { if !options.isEmpty { expanded = true } } }
        .popover(isPresented: $expanded, attachmentAnchor: .point(.bottom), arrowEdge: .top) {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(options, id: \.self) { option in
                        Button {
                            expanded = false
                            onSelect(option)
                        } label: {
                            HStack {
                                Text(optionLabel(option)).phText(PHType.bodyLarge).lineLimit(1)
                                Spacer(minLength: Spacing.lg)
                                if option == selected { PHIconView(icon: .filledCheck, size: 24, tint: PH.primary) }
                            }
                            .padding(.horizontal, Spacing.lg)
                            .frame(minHeight: 48)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(minWidth: 220, maxHeight: 360)
            .background(PH.surfaceContainer)
            .presentationCompactAdaptation(.popover)
        }
    }
}
