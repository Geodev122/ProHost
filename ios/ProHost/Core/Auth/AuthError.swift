import Foundation

/// What went wrong with a sign-in, in terms the screen can explain. Mirrors `data/auth/AuthErrors.kt`.
/// Firebase's own messages are English developer text and never reach people; enumeration protection stays on
/// (wrong email and wrong password read the same).
enum AuthError: CaseIterable, Equatable {
    case invalidCredentials, emailInUse, weakPassword, invalidEmail, tooManyRequests
    case userDisabled, network, unavailable, unknown

    /// Key in Localizable.xcstrings (same names as Android).
    var messageKey: String {
        switch self {
        case .invalidCredentials: return "err_invalid_credentials"
        case .emailInUse: return "err_email_in_use"
        case .weakPassword: return "password_doesnt_meet_rules"
        case .invalidEmail: return "please_enter_a_valid_email_address"
        case .tooManyRequests: return "err_too_many_requests"
        case .userDisabled: return "err_user_disabled"
        case .network: return "network_error_please_check_your_connection"
        case .unavailable: return "err_auth_unavailable"
        case .unknown: return "sign_in_failed_please_try_again"
        }
    }

    /// Localized text for the current app language.
    var message: String { Bundle.main.localizedString(forKey: messageKey, value: nil, table: nil) }

    /// Pure mapping of Firebase Auth error code names (the Android names; the iOS adapter converts `AuthErrorCode` to these).
    static func fromCode(_ code: String?) -> AuthError {
        switch code?.uppercased() {
        case "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_LOGIN_CREDENTIALS",
             "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_USER_MISMATCH":
            return .invalidCredentials
        case "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
            return .emailInUse
        case "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS", "PASSWORD_DOES_NOT_MEET_REQUIREMENTS":
            return .weakPassword
        case "ERROR_INVALID_EMAIL", "ERROR_MISSING_EMAIL":
            return .invalidEmail
        case "ERROR_TOO_MANY_REQUESTS":
            return .tooManyRequests
        case "ERROR_USER_DISABLED":
            return .userDisabled
        case "ERROR_NETWORK_REQUEST_FAILED":
            return .network
        case "ERROR_OPERATION_NOT_ALLOWED", "ERROR_INTERNAL_ERROR":
            return .unavailable
        default:
            return .unknown
        }
    }

    /// Newer SDKs put the server reason in the message ("PASSWORD_DOES_NOT_MEET_REQUIREMENTS: ...").
    static func fromCode(_ code: String?, message: String?) -> AuthError {
        let mapped = fromCode(code)
        if mapped == .unknown, message?.contains("PASSWORD_DOES_NOT_MEET_REQUIREMENTS") == true { return .weakPassword }
        return mapped
    }
}
