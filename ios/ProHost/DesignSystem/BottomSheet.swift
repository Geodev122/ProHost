import SwiftUI

/// Modal bottom sheet (`ProHostBottomSheet`): 28 pt top corners, 36x4 handle, half and full stops, optional accent tint.
private struct ProHostSheetStyle<SheetContent: View>: ViewModifier {
    @Binding var isPresented: Bool
    var accentTint = false
    var detents: Set<PresentationDetent>
    @ViewBuilder var sheetContent: () -> SheetContent

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented) {
            VStack(spacing: 0) {
                Capsule()
                    .fill(accentTint ? PH.primary.opacity(0.6) : PH.outlineVariant)
                    .frame(width: 36, height: 4)
                    .padding(.vertical, Spacing.md)
                    .accessibilityHidden(true)
                sheetContent()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .presentationDetents(detents)
            .presentationDragIndicator(.hidden)
            .presentationCornerRadius(Radius.lg)
            .presentationBackground {
                ZStack {
                    PH.surface
                    if accentTint { PH.primary.opacity(0.06) }
                }
            }
        }
    }
}

extension View {
    func proHostBottomSheet<SheetContent: View>(
        isPresented: Binding<Bool>, accentTint: Bool = false,
        detents: Set<PresentationDetent> = [.medium, .large],
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        modifier(ProHostSheetStyle(isPresented: isPresented, accentTint: accentTint, detents: detents, sheetContent: content))
    }
}
