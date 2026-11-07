import Foundation

/// Sign-up password policy. Mirrors `util/PasswordRules.kt` and the Identity Platform policy (min 8, a lowercase letter, a number);
/// keep all three identical. Length counts UTF-16 units like Kotlin's `String.length`.
enum PasswordRules {
    static let minLength = 8

    struct Check: Equatable {
        let longEnough: Bool
        let hasLowercase: Bool
        let hasNumber: Bool
        var ok: Bool { longEnough && hasLowercase && hasNumber }
    }

    static func check(_ password: String) -> Check {
        Check(
            longEnough: password.utf16.count >= minLength,
            hasLowercase: password.contains { $0.isLowercase },
            hasNumber: password.unicodeScalars.contains { $0.properties.generalCategory == .decimalNumber }
        )
    }

    static func isValid(_ password: String) -> Bool { check(password).ok }
}
