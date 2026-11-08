import FirebaseFunctions
import Foundation

/// A callable failed because the account is suspended (`assignInitialRole` throws permission-denied "suspended").
struct AccountSuspendedError: Error {
    let message: String
}

/// Cloud Functions callables in europe-west1 (same region as Android). Failures reach people only through `userMessage(for:)`.
final class FunctionsClient {
    static let shared = FunctionsClient()
    private let functions = Functions.functions(region: "europe-west1")

    @discardableResult
    func call(_ name: String, _ data: [String: Any]? = nil) async throws -> [String: Any] {
        let result = try await functions.httpsCallable(name).call(data)
        return result.data as? [String: Any] ?? [:]
    }

    /// Server-side role assignment; a plain sign-in sends no body, a brand-new registration sends the draft (format-validated server-side).
    func assignInitialRole(registration: [String: Any]? = nil) async throws -> UserRole {
        let payload: [String: Any]? = registration.map { ["registration": $0] }
        do {
            let data = try await call("assignInitialRole", payload)
            guard let raw = data["role"] as? String, let role = UserRole(rawValue: raw) else {
                throw NSError(domain: "ProHost", code: 1, userInfo: [NSLocalizedDescriptionKey: "assignInitialRole returned no role."])
            }
            return role
        } catch {
            let ns = error as NSError
            if ns.domain == FunctionsErrorDomain, ns.code == FunctionsErrorCode.permissionDenied.rawValue,
               ns.localizedDescription.localizedCaseInsensitiveContains("suspended") {
                throw AccountSuspendedError(message: ns.localizedDescription)
            }
            throw error
        }
    }

    /// Forgot-password mail through the branded callable (always answers "if an account exists..." on the screen).
    func sendPasswordResetLink(email: String) async throws {
        try await call("sendPasswordResetLink", ["email": email])
    }

    func deleteOwnAccount() async throws {
        try await call("deleteOwnAccount")
    }

    /// Plain words for people: the function's own message where it is one of ours, a generic line for transport or internal failures.
    static func userMessage(for error: Error, fallbackKey: String = "sign_in_failed_please_try_again") -> String {
        let ns = error as NSError
        let generic = Bundle.main.localizedString(forKey: fallbackKey, value: nil, table: nil)
        if ns.domain == NSURLErrorDomain {
            return Bundle.main.localizedString(forKey: "network_error_please_check_your_connection", value: nil, table: nil)
        }
        guard ns.domain == FunctionsErrorDomain, let code = FunctionsErrorCode(rawValue: ns.code) else { return generic }
        switch code {
        case .internal, .unknown, .dataLoss, .deadlineExceeded:
            return generic
        default:
            let text = ns.localizedDescription
            return text.isEmpty ? generic : text
        }
    }
}
