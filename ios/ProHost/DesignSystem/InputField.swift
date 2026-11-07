import SwiftUI

/// The one text field (`InputField` on Android): label above, 52 pt box, outline turns steel blue on focus and red on error.
struct InputField: View {
    let label: LocalizedStringKey
    @Binding var text: String
    var error: String?
    var isSecure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?
    var capitalization: TextInputAutocapitalization = .sentences

    @FocusState private var focused: Bool

    private var borderColor: Color {
        if error != nil { return PH.error }
        return focused ? PH.primary : PH.outline
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .phText(PHType.labelMedium, color: error == nil ? PH.onSurfaceVariant : PH.error)
            field
                .focused($focused)
                .keyboardType(keyboard)
                .textContentType(contentType)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled(isSecure || keyboard == .emailAddress)
                .font(PHType.bodyLarge.font())
                .foregroundStyle(PH.onSurface)
                .padding(.horizontal, Spacing.lg)
                .frame(minHeight: 52)
                .background(PH.surface, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                        .stroke(borderColor, lineWidth: focused || error != nil ? 2 : 1)
                )
            if let error {
                Text(error).phText(PHType.bodySmall, color: PH.error)
            }
        }
    }

    @ViewBuilder
    private var field: some View {
        if isSecure {
            SecureField("", text: $text)
        } else {
            TextField("", text: $text)
        }
    }
}
