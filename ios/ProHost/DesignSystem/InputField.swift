import SwiftUI

/// The one text field (`InputField` on Android): Material outlined field, 12 pt radius, floating label that notches the border,
/// outline `outlineVariant` / steel blue on focus / red on error, helper or error line underneath.
struct InputField: View {
    let label: String
    @Binding var text: String
    var placeholder: String?
    var prefix: String?
    var suffix: String?
    var leadingIcon: PHIcon?
    var trailing: AnyView?
    var isError = false
    var errorMessage: String?
    var helperText: String?
    var singleLine = true
    var minLines = 1
    var readOnly = false
    var enabled = true
    var isSecure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?
    var capitalization: TextInputAutocapitalization = .sentences
    var submitLabel: SubmitLabel = .done
    var onSubmit: (() -> Void)?

    @FocusState private var focused: Bool

    private var floated: Bool { focused || !text.isEmpty }
    private var borderColor: Color {
        if isError { return PH.error }
        return focused ? PH.primary : PH.outlineVariant
    }
    private var labelColor: Color {
        if isError { return PH.error }
        return focused ? PH.primary : PH.onSurfaceVariant
    }
    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: Radius.sm, style: .continuous) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                HStack(spacing: Spacing.sm) {
                    if let leadingIcon {
                        PHIconView(icon: leadingIcon, size: 20, tint: isError ? PH.error : PH.primary)
                    }
                    if let prefix, floated {
                        Text(prefix).phText(PHType.bodyMedium.weighted(.semibold))
                    }
                    inputView
                    if let suffix, floated {
                        Text(suffix).phText(PHType.bodyMedium, color: PH.onSurfaceVariant)
                    }
                    if let trailing { trailing }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, 8)
                .frame(minHeight: 56)
                .background(PH.surface, in: shape)
                .overlay(shape.stroke(borderColor, lineWidth: focused || isError ? 2 : 1))
                .contentShape(shape)
                .onTapGesture { if enabled && !readOnly { focused = true } }

                if !label.isEmpty {
                    Text(label)
                        .phText(floated ? PHType.labelSmall : PHType.bodyMedium, color: labelColor)
                        .lineLimit(1)
                        .padding(.horizontal, floated ? 4 : 0)
                        .background(floated ? PH.surface : .clear)
                        .padding(.leading, floated ? 12 : (leadingIcon == nil ? Spacing.lg : Spacing.lg + 28))
                        .offset(y: floated ? -8 : 18)
                        .allowsHitTesting(false)
                        .animation(.easeOut(duration: 0.15), value: floated)
                }
            }
            .opacity(enabled ? 1 : 0.6)

            if isError, let errorMessage, !errorMessage.isEmpty {
                HStack(spacing: Spacing.xs) {
                    PHIconView(icon: .filledErrorOutline, size: 14, tint: PH.error)
                    Text(errorMessage).phText(PHType.bodySmall, color: PH.error)
                }
                .padding(.top, Spacing.xs).padding(.leading, 6)
            } else if let helperText, !helperText.isEmpty {
                Text(helperText).phText(PHType.bodySmall, color: PH.onSurfaceVariant)
                    .padding(.top, Spacing.xs).padding(.leading, 6)
            }
        }
    }

    @ViewBuilder
    private var inputView: some View {
        let hint = (focused && text.isEmpty) ? (placeholder ?? "") : ""
        Group {
            if isSecure {
                SecureField(hint, text: $text)
            } else if singleLine {
                TextField(hint, text: $text)
            } else {
                TextField(hint, text: $text, axis: .vertical).lineLimit(minLines...max(minLines, 8))
            }
        }
        .focused($focused)
        .keyboardType(keyboard)
        .textContentType(contentType)
        .textInputAutocapitalization(capitalization)
        .autocorrectionDisabled(isSecure || keyboard == .emailAddress)
        .submitLabel(submitLabel)
        .onSubmit { onSubmit?() }
        .disabled(!enabled || readOnly)
        .font(PHType.bodyLarge.font())
        .foregroundStyle(enabled ? PH.onSurface : PH.onSurfaceVariant.opacity(0.6))
        .tint(PH.primary)
    }
}
