import SwiftUI

/// The signed-in shell: one page at a time over a floating bottom pill (`ProHostBottomNavBar`), tabs by role.
/// Pages are filled in by the following slices (Explore + map first); until then each shows its title.
struct MainShell: View {
    let user: AppUser
    let onSignOut: () -> Void
    @State private var activeTabId = ShellTabs.defaultTabId
    @State private var navExpanded = true

    var body: some View {
        let tabs = ShellTabs.tabs(for: user.role)
        let active = tabs.first { $0.id == activeTabId } ?? tabs[0]
        ZStack(alignment: .bottom) {
            PH.background.ignoresSafeArea()
            VStack(spacing: 0) {
                if active.id != AppNavTab.searchMap.id {
                    ProHostTopAppBar(role: user.role, unreadAlertCount: 0, pageTitle: active.title, showMenu: false,
                                     onMenu: {}, onAlerts: {})
                }
                ShellPage(tab: active, user: user, onSignOut: onSignOut)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.bottom, navExpanded ? 88 : BottomNav.collapsedInset)
            }
            ProHostBottomNavBar(
                tabs: tabs, activeTabId: active.id, onTabSelected: { activeTabId = $0 },
                highlightWithSecondary: ShellTabs.usesSecondaryAccent(user.role),
                collapsed: !navExpanded, onExpandChange: { navExpanded = $0 }
            )
        }
    }
}

private struct ShellPage: View {
    let tab: AppNavTab
    let user: AppUser
    let onSignOut: () -> Void

    var body: some View {
        if tab.id == AppNavTab.professionalProfile.id {
            ProfilePlaceholder(user: user, onSignOut: onSignOut)
        } else {
            ProEmptyState(title: tab.title, description: AppInfo.versionLabel, icon: tab.selectedIcon)
        }
    }
}

/// Identity card and sign-out until the full Profile page is ported.
private struct ProfilePlaceholder: View {
    let user: AppUser
    let onSignOut: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                ProCard(variant: .outlined) {
                    HStack(spacing: Spacing.md) {
                        ProAvatar(initials: ProAvatar.initials(from: user.fullName), size: 56)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user.fullName).phText(PHType.titleMedium.weighted(.bold))
                            Text(user.email).phText(PHType.bodySmall, color: PH.onSurfaceVariant)
                        }
                        Spacer(minLength: 0)
                    }
                }
                CustomButton(text: L("sign_out"), variant: .outlined, fullWidth: true, action: onSignOut)
            }
            .padding(Spacing.lg)
        }
    }
}
