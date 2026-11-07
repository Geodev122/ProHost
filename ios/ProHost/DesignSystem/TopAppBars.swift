import SwiftUI

/// 48 pt tap target around a 24 pt icon (Material `IconButton`).
struct PHIconButton: View {
    let icon: PHIcon
    var tint: Color = PH.onSurface
    var label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            PHIconView(icon: icon, size: 24, tint: tint).frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct BarSurface: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity)
            .background(PH.surface.ignoresSafeArea(edges: .top))
            .overlay(alignment: .bottom) { Rectangle().fill(PH.outlineVariant.opacity(0.5)).frame(height: 1) }
            .shadow(color: Color.black.opacity(0.08), radius: Elevation.level2, x: 0, y: 1)
    }
}

/// Shell header: menu, brand + role line, notification bell with count (`ProHostTopAppBar`).
struct ProHostTopAppBar: View {
    let role: UserRole
    let unreadAlertCount: Int
    var pageTitle: String?
    var showBrand = true
    var showMenu = true
    let onMenu: () -> Void
    let onAlerts: () -> Void

    private var roleText: String {
        switch role {
        case .admin: return L("admin")
        case .proHost: return L("host")
        case .specialist: return L("member")
        }
    }

    var body: some View {
        HStack {
            HStack(spacing: Spacing.sm) {
                if showMenu {
                    PHIconButton(icon: .filledMenu, label: L("open_menu"), action: onMenu)
                } else {
                    Spacer().frame(width: Spacing.xs)
                }
                if showBrand {
                    ProHostBrandLogo(size: 34)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(L("prohost")).phText(PHType.titleMedium.weighted(.heavy))
                        Text(pageTitle ?? roleText).phText(PHType.labelSmall.weighted(.semibold), color: PH.primary)
                    }
                }
            }
            Spacer()
            ZStack(alignment: .topTrailing) {
                PHIconButton(
                    icon: .filledNotifications, tint: PH.onSurfaceVariant,
                    label: unreadAlertCount > 0 ? L("notifications_x_unread", unreadAlertCount) : L("notifications"),
                    action: onAlerts
                )
                if unreadAlertCount > 0 {
                    ProHostCountBadge(count: unreadAlertCount).offset(x: -2, y: 2)
                }
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.sm)
        .modifier(BarSurface())
    }
}

/// Header for full-screen destinations: back arrow (or menu) and a title (`ProHostFullScreenTopAppBar`).
struct ProHostFullScreenTopAppBar: View {
    let title: String
    var isBackNavigation = false
    let onMenu: () -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            PHIconButton(
                icon: isBackNavigation ? .filledArrowBack : .filledMenu,
                label: isBackNavigation ? L("back") : L("open_side_navigation_drawer"),
                action: onMenu
            )
            Text(title).phText(PHType.titleMedium.weighted(.heavy))
            Spacer()
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.sm)
        .modifier(BarSurface())
    }
}

/// Round photo-gallery arrow: 48 pt target, 32 pt dark disc (`PhotoCarouselArrow`).
struct PhotoCarouselArrow: View {
    let forward: Bool
    var visualSize: CGFloat = 32
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            PHIconView(icon: forward ? .filledArrowForwardIos : .filledArrowBackIos, size: visualSize / 2, tint: .white)
                .frame(width: visualSize, height: visualSize)
                .background(Color.black.opacity(0.4), in: Circle())
                .frame(width: 48, height: 48)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(forward ? L("next_photo") : L("previous_photo"))
    }
}
