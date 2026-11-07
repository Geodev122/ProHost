import SwiftUI

/// Loading placeholder shimmer. Static when Reduce Motion is on.
struct Shimmer: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .redacted(reason: .placeholder)
            .overlay {
                if !reduceMotion {
                    GeometryReader { proxy in
                        LinearGradient(
                            colors: [.clear, PH.surface.opacity(0.55), .clear],
                            startPoint: .leading, endPoint: .trailing
                        )
                        .frame(width: proxy.size.width * 0.6)
                        .offset(x: proxy.size.width * phase)
                    }
                    .allowsHitTesting(false)
                    .clipped()
                }
            }
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.linear(duration: 1.3).repeatForever(autoreverses: false)) { phase = 1.4 }
            }
    }
}

extension View {
    func shimmering() -> some View { modifier(Shimmer()) }
}

/// First-load list placeholder (`ShimmerLoadingList` on Android): never a bare spinner.
struct ShimmerLoadingList: View {
    var rows = 4

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ForEach(0..<rows, id: \.self) { _ in
                ProCard(variant: .outlined) {
                    RoundedRectangle(cornerRadius: Radius.sm, style: .continuous)
                        .fill(PH.surfaceContainerHigh)
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    Text("Loading title placeholder").phText(PHType.titleMedium)
                    Text("Loading details placeholder text").phText(PHType.bodyMedium, color: PH.onSurfaceVariant)
                }
                .shimmering()
            }
        }
        .accessibilityHidden(true)
    }
}
