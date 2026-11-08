import SwiftUI

/// Initials avatar with optional photo and verified tick (`ProAvatar` on Android).
struct ProAvatar: View {
    let initials: String
    let size: CGFloat
    var imageUrl: String?
    var isVerified = false
    var containerColor: Color = PH.primary
    var contentColor: Color = PH.onPrimary

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if let imageUrl, !imageUrl.isEmpty, let url = URL(string: imageUrl) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let image): image.resizable().scaledToFill()
                        default: fallback
                        }
                    }
                } else {
                    fallback
                }
            }
            .frame(width: size, height: size)
            .clipShape(Circle())

            if isVerified {
                PHIconView(icon: .filledCheck, size: size * 0.25, tint: PH.onSuccess)
                    .frame(width: size * 0.4, height: size * 0.4)
                    .background(PH.success, in: Circle())
                    .overlay(Circle().stroke(PH.surface, lineWidth: 1.5))
                    .accessibilityLabel(L("verified"))
            }
        }
        .frame(width: size, height: size)
    }

    private var fallback: some View {
        Circle().fill(containerColor)
            .overlay(
                Text(initials)
                    .font(Font.custom("Roboto-Bold", size: size * 0.38))
                    .foregroundStyle(contentColor)
            )
    }

    /// First letters of the first two words, "PS" when there is no name (`ProMemberAvatar`).
    static func initials(from name: String) -> String {
        let letters = name.split(separator: " ").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            .prefix(2).compactMap { $0.first.map { String($0).uppercased() } }.joined()
        return letters.isEmpty ? "PS" : letters
    }
}

struct ProMemberAvatar: View {
    let name: String
    var specialty: String?
    var isVerified = true
    var size: CGFloat = 40
    var imageUrl: String?

    var body: some View {
        let avatar = ProAvatar(initials: ProAvatar.initials(from: name), size: size, imageUrl: imageUrl, isVerified: isVerified)
        if let specialty {
            HStack(spacing: Spacing.md) {
                avatar
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: Spacing.xs) {
                        Text(name).phText(PHType.titleMedium.weighted(.bold))
                        if isVerified {
                            PHIconView(icon: .filledVerified, size: 16, tint: PH.primary)
                                .accessibilityLabel(L("verified"))
                        }
                    }
                    Text(specialty).phText(PHType.bodySmall, color: PH.onSurfaceVariant)
                }
            }
        } else {
            avatar
        }
    }
}
