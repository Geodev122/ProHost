import SwiftUI

/// KPI tile with a 3 pt tinted top rule (`ProMetricTile` on Android).
struct ProMetricTile: View {
    let title: String
    let value: String
    var subtitle: String?
    let icon: PHIcon
    var iconTint: Color = PH.primary
    var containerColor: Color = PH.surface
    var borderColor: Color = PH.outlineVariant.opacity(0.5)
    var action: (() -> Void)?

    var body: some View {
        if let action {
            Button(action: action) { tile }.buttonStyle(.plain)
        } else {
            tile
        }
    }

    private var shape: RoundedRectangle { RoundedRectangle(cornerRadius: Radius.md, style: .continuous) }

    private var tile: some View {
        VStack(spacing: 0) {
            Rectangle().fill(iconTint).frame(height: 3)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                HStack {
                    Text(title).phText(PHType.labelMedium, color: PH.onSurfaceVariant)
                    Spacer()
                    PHIconView(icon: icon, size: 18, tint: iconTint)
                        .frame(width: 32, height: 32)
                        .background(iconTint.opacity(0.12), in: Circle())
                }
                Text(value).phText(PHType.headlineMedium.weighted(.heavy))
                if let subtitle, !subtitle.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(subtitle).phText(PHType.bodySmall, color: PH.onSurfaceVariant)
                }
            }
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(containerColor, in: shape)
        .clipShape(shape)
        .overlay(shape.stroke(borderColor, lineWidth: 1))
        .shadow(color: Color.black.opacity(0.08), radius: Elevation.level2, x: 0, y: 1)
    }
}
