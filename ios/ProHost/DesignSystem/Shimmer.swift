import SwiftUI

/// Loading card (`ShimmerLoadingCard`): 20 pt radius, gradient sweeping every 1.2 s; static with Reduce Motion.
struct ShimmerLoadingCard: View {
    var height: CGFloat = 80
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let p = reduceMotion ? 0.5 : CGFloat((t.truncatingRemainder(dividingBy: 1.2)) / 1.2) * 2.0 - 0.5
            RoundedRectangle(cornerRadius: Radius.md, style: .continuous)
                .fill(LinearGradient(
                    colors: [PH.shimmerBase, PH.shimmerHighlight, PH.shimmerBase],
                    startPoint: UnitPoint(x: p - 0.5, y: 0.5), endPoint: UnitPoint(x: p + 0.5, y: 0.5)
                ))
        }
        .frame(height: height)
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}

/// First-load list placeholder (`ShimmerLoadingList`): never a bare spinner.
struct ShimmerLoadingList: View {
    var count = 4
    var itemHeight: CGFloat = 120

    var body: some View {
        VStack(spacing: Spacing.md) {
            ForEach(0..<count, id: \.self) { _ in ShimmerLoadingCard(height: itemHeight) }
        }
        .padding(16)
    }
}
