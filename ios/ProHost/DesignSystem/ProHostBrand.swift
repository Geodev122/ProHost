import SwiftUI

/// App logo (`ProHostBrandLogo`): the same PNG as Android, fitted into a square.
struct ProHostBrandLogo: View {
    var size: CGFloat = 38

    var body: some View {
        Image("ic_prohost_logo_brand")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel(L("prohost_logo"))
    }
}

/// Green shield chip (`ProHostCedarBadge`).
struct ProHostCedarBadge: View {
    var text = "Lebanon Verified"
    var isCompact = false

    var body: some View {
        HStack(spacing: Spacing.xs) {
            PHIconView(icon: .filledShield, size: isCompact ? 10 : 12, tint: PH.onSuccessContainer)
            Text(text)
                .font(Font.custom("Roboto-Bold", size: isCompact ? 10 : 11))
                .tracking(0.3)
                .foregroundStyle(PH.onSuccessContainer)
        }
        .padding(.horizontal, isCompact ? 5 : 7)
        .padding(.vertical, isCompact ? 2 : 3)
        .background(PH.successContainer, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(PH.success.opacity(0.35), lineWidth: 1))
    }
}
