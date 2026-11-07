import Foundation

/// Mirrors `UserRole` in DataModels.kt (raw values are what Firestore and the token claim store).
enum UserRole: String, Codable, CaseIterable {
    case specialist = "SPECIALIST"
    case proHost = "PRO_HOST"
    case admin = "ADMIN"
}

/// Subset of `AppUser` needed for sign-in routing (the full profile model comes with the data layer).
struct AppUser: Equatable {
    var id: String
    var email: String
    var fullName: String
    var role: UserRole
    var phone: String = ""

    /// One completeness rule for cold start and every sign-in path: a blank full name sends the person to the
    /// registration form; admins are provisioned server-side and exempt. Never gate on `phone` (email/Google users have none until KYC).
    var isProfileComplete: Bool {
        role == .admin || !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
