import Foundation

/// What the root view shows. Cold start and every sign-in path decide through `SessionResolver` (one completeness rule).
enum SessionPhase: Equatable {
    case launching
    case signedOut
    /// Signed in but the profile is missing or has a blank full name: the registration form comes next.
    case needsRegistration(AppUser)
    case ready(AppUser)
    case suspended(String)
    case failed(String)
}

enum SessionResolver {
    /// Pure. `profile` is the `user_profiles/{uid}` document data, nil when the document does not exist.
    /// The server-assigned role (token claim) is authoritative: the profile's `role` mirror can lag.
    static func resolve(uid: String, email: String, role: UserRole, profile: [String: Any]?) -> SessionPhase {
        guard let profile else {
            return .needsRegistration(AppUser(id: uid, email: email, fullName: "", role: role))
        }
        var user = AppUser.fromProfile(id: uid, data: profile)
        user.role = role
        if user.email.isEmpty { user.email = email }
        return user.isProfileComplete ? .ready(user) : .needsRegistration(user)
    }
}

extension UserRole {
    /// The `role` custom claim on the ID token; nil when the claim is missing (a brand-new account before `assignInitialRole`).
    static func fromClaim(_ value: Any?) -> UserRole? {
        guard let raw = value as? String else { return nil }
        return UserRole(rawValue: raw)
    }
}

extension AppUser {
    /// Mirrors `AppUser.fromFirestore` on Android: a missing role reads as SPECIALIST, missing text as empty.
    static func fromProfile(id: String, data: [String: Any]) -> AppUser {
        let role = UserRole.fromClaim(data["role"]) ?? .specialist
        return AppUser(
            id: id,
            email: data["email"] as? String ?? "",
            fullName: data["fullName"] as? String ?? "",
            role: role,
            phone: data["phone"] as? String ?? ""
        )
    }
}

/// Bottom tabs per role (`SPECIALIST_BOTTOM_TABS` / `PRO_HOST_BOTTOM_TABS` in ProHostNavGraph.kt). There is no Admin console on iOS,
/// so an admin account sees the specialist tabs.
enum ShellTabs {
    static func tabs(for role: UserRole) -> [AppNavTab] {
        switch role {
        case .proHost:
            return [.manageListings, .searchMap, .professionalRentals, .professionalProfile]
        case .specialist, .admin:
            return [.searchMap, .myFavorites, .professionalRentals, .professionalProfile]
        }
    }

    /// Explore is the centre of the app for every role.
    static var defaultTabId: String { AppNavTab.searchMap.id }

    /// Pro Hosts get the orange accent, everyone else Steel Blue.
    static func usesSecondaryAccent(_ role: UserRole) -> Bool { role == .proHost }
}
