import SwiftUI

/// Destinations of the shell (`navigation/AppNavTab.kt`): same ids, titles, short labels and icons. Android shows these English literals in every language.
struct AppNavTab: Identifiable, Equatable {
    let id: String
    let title: String
    let selectedIcon: PHIcon
    let unselectedIcon: PHIcon
    var shortLabel: String

    init(_ id: String, _ title: String, _ selected: PHIcon, _ unselected: PHIcon, short: String? = nil) {
        self.id = id; self.title = title; self.selectedIcon = selected; self.unselectedIcon = unselected; self.shortLabel = short ?? title
    }

    static let searchMap = AppNavTab("search_map", "Explore", .filledSearch, .outlinedSearch)
    static let professionalRentals = AppNavTab("pro_rentals", "My Rentals", .filledReceiptLong, .outlinedReceiptLong)
    static let professionalProfile = AppNavTab("pro_profile", "Profile", .filledPerson, .outlinedPerson)
    static let manageListings = AppNavTab("manage_listings", "My Listings", .filledHomeWork, .outlinedHomeWork, short: "Listings")
    static let ownerRentalRequests = AppNavTab("owner_requests", "Renting Requests", .filledInbox, .outlinedInbox, short: "Requests")
    static let ownerRentingProgress = AppNavTab("owner_progress", "Renting Progress", .filledSchedule, .outlinedSchedule, short: "Progress")
    static let stats = AppNavTab("stats", "Financials", .filledAnalytics, .outlinedAnalytics)
    static let ownerSubscriptions = AppNavTab("owner_subscriptions", "ProHost Premium", .filledLayers, .outlinedLayers)
    static let myFavorites = AppNavTab("my_favorites", "Saved", .filledFavorite, .outlinedFavoriteBorder)
}

enum BottomNav {
    /// Space reserved when the pill is collapsed to its handle (`BottomNavCollapsedInset`).
    static let collapsedInset: CGFloat = 44
}

private struct NavPillItem: View {
    let tab: AppNavTab
    let selected: Bool
    let accent: Color
    let onAccent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                PHIconView(icon: selected ? tab.selectedIcon : tab.unselectedIcon, size: 22,
                           tint: selected ? onAccent : PH.onSurfaceVariant)
                if selected {
                    Text(tab.shortLabel)
                        .phText(PHType.labelLarge.weighted(.heavy).manropeStyle(), color: onAccent)
                        .lineLimit(1).fixedSize()
                }
            }
            .padding(.horizontal, selected ? Spacing.lg : Spacing.md)
            .frame(minHeight: 48)
            .background(selected ? accent : Color.clear, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: selected)
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

extension PHTextStyle {
    /// Same metrics set in Manrope (`labelLarge.copy(fontFamily = ManropeDisplay)`).
    func manropeStyle() -> PHTextStyle {
        PHTextStyle(size: size, lineHeight: lineHeight, weight: weight, manrope: true, tracking: tracking, relativeTo: relativeTo)
    }
}

/// Floating pill navigation (`ProHostBottomNavBar`): active tab = filled pill with its one-word label, others icons; collapses to a handle.
struct ProHostBottomNavBar: View {
    let tabs: [AppNavTab]
    let activeTabId: String
    let onTabSelected: (String) -> Void
    var highlightWithSecondary = false
    var collapsed = false
    var onExpandChange: ((Bool) -> Void)?

    private var accent: Color { highlightWithSecondary ? PH.secondary : PH.primary }
    private var onAccent: Color { highlightWithSecondary ? PH.onSecondary : PH.onPrimary }

    var body: some View {
        let showHandle = collapsed && onExpandChange != nil
        Group {
            if showHandle {
                Button { onExpandChange?(true) } label: {
                    PHIconView(icon: .filledKeyboardArrowUp, size: 24, tint: accent)
                        .frame(width: 64, height: 32)
                        .background(PH.surfaceContainerLowest, in: Capsule())
                        .overlay(Capsule().stroke(PH.outlineVariant.opacity(0.6), lineWidth: 1))
                        .shadow(color: Color.black.opacity(0.14), radius: Elevation.level3, x: 0, y: 2)
                }
                .buttonStyle(.plain)
                .gesture(DragGesture(minimumDistance: 6).onEnded { if $0.translation.height < -6 { onExpandChange?(true) } })
                .accessibilityLabel(L("show_navigation"))
            } else {
                HStack(spacing: 0) {
                    Spacer(minLength: 0)
                    ForEach(tabs) { tab in
                        NavPillItem(tab: tab, selected: tab.id == activeTabId, accent: accent, onAccent: onAccent) {
                            onTabSelected(tab.id)
                        }
                        Spacer(minLength: 0)
                    }
                    if let onExpandChange {
                        Button { onExpandChange(false) } label: {
                            PHIconView(icon: .filledKeyboardArrowDown, size: 24, tint: PH.onSurfaceVariant).frame(width: 40, height: 40)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L("hide_navigation"))
                    }
                }
                .padding(.horizontal, Spacing.sm).padding(.vertical, Spacing.sm)
                .frame(maxWidth: 480, minHeight: 64)
                .background(PH.surfaceContainerLowest, in: Capsule())
                .overlay(Capsule().stroke(PH.outlineVariant.opacity(0.6), lineWidth: 1))
                .shadow(color: Color.black.opacity(0.14), radius: Elevation.level3, x: 0, y: 2)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
        .animation(.easeInOut(duration: 0.2), value: showHandle)
    }
}
