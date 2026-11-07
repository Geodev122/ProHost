import SwiftUI

/// Red count bubble, "99+" over 99 (`ProHostCountBadge`).
struct ProHostCountBadge: View {
    let count: Int

    var body: some View {
        Text(count > 99 ? "99+" : String(count))
            .phText(PHType.labelSmall.weighted(.bold), color: PH.onError)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 4)
            .frame(minWidth: 16, minHeight: 16)
            .background(PH.error, in: Capsule())
    }
}

/// Red 9 pt dot (`ProHostDotBadge`).
struct ProHostDotBadge: View {
    var body: some View {
        Circle().fill(PH.error).frame(width: 9, height: 9)
    }
}
