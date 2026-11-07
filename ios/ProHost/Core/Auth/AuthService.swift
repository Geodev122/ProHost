import AuthenticationServices
import FirebaseAuth
import Foundation
import Observation

extension AuthError {
    /// Firebase Auth numeric codes (FIRAuthErrors.h, stable across SDKs) -> the Android code names `fromCode` understands.
    static func codeName(forFirebaseCode code: Int) -> String? {
        switch code {
        case 17004: return "ERROR_INVALID_CREDENTIAL"
        case 17005: return "ERROR_USER_DISABLED"
        case 17006: return "ERROR_OPERATION_NOT_ALLOWED"
        case 17007: return "ERROR_EMAIL_ALREADY_IN_USE"
        case 17008: return "ERROR_INVALID_EMAIL"
        case 17009: return "ERROR_WRONG_PASSWORD"
        case 17010: return "ERROR_TOO_MANY_REQUESTS"
        case 17011: return "ERROR_USER_NOT_FOUND"
        case 17012: return "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"
        case 17020: return "ERROR_NETWORK_REQUEST_FAILED"
        case 17024: return "ERROR_USER_MISMATCH"
        case 17025: return "ERROR_CREDENTIAL_ALREADY_IN_USE"
        case 17026: return "ERROR_WEAK_PASSWORD"
        case 17034: return "ERROR_MISSING_EMAIL"
        case 17056: return "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS"
        case 17999: return "ERROR_INTERNAL_ERROR"
        default: return nil
        }
    }

    /// Pure adapter over an NSError (testable without Firebase).
    static func from(domain: String, code: Int, message: String) -> AuthError {
        if domain == NSURLErrorDomain { return .network }
        guard domain == "FIRAuthErrorDomain" else { return .unknown }
        return fromCode(codeName(forFirebaseCode: code), message: message)
    }

    static func from(_ error: Error) -> AuthError {
        let ns = error as NSError
        return from(domain: ns.domain, code: ns.code, message: ns.localizedDescription)
    }
}

/// Email + password and Sign in with Apple over Firebase Auth, then `assignInitialRole` (the server decides the role).
/// The client never sets a role. Google sign-in joins in the next step.
@MainActor
@Observable
final class AuthService {
    private(set) var isSignedIn = false
    @ObservationIgnored private var listener: AuthStateDidChangeListenerHandle?

    init() {
        listener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in self?.isSignedIn = user != nil }
        }
    }

    deinit {
        if let listener { Auth.auth().removeStateDidChangeListener(listener) }
    }

    func signIn(email: String, password: String) async throws -> UserRole {
        do {
            _ = try await Auth.auth().signIn(withEmail: email, password: password)
        } catch {
            throw AuthError.from(error)
        }
        return try await finishSession()
    }

    func createAccount(email: String, password: String) async throws {
        do {
            _ = try await Auth.auth().createUser(withEmail: email, password: password)
        } catch {
            throw AuthError.from(error)
        }
    }

    /// Always completes without telling anyone whether the address exists (enumeration protection).
    func requestPasswordReset(email: String) async {
        do {
            try await FunctionsClient.shared.sendPasswordResetLink(email: email)
        } catch {
            try? await Auth.auth().sendPasswordReset(withEmail: email)
        }
    }

    /// `rawNonce` is the unhashed value whose SHA-256 went into the Apple request (`AuthNonce.sha256Hex`).
    func signInWithApple(idToken: String, rawNonce: String, fullName: PersonNameComponents?) async throws -> UserRole {
        guard AuthNonce.idTokenMatches(idToken, nonce: AuthNonce.sha256Hex(rawNonce)) else { throw AuthError.unknown }
        let credential = OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: rawNonce, fullName: fullName)
        do {
            _ = try await Auth.auth().signIn(with: credential)
        } catch {
            throw AuthError.from(error)
        }
        return try await finishSession()
    }

    func signOut() throws {
        try Auth.auth().signOut()
    }

    /// Role assignment, then a forced token refresh so the new claim is on the token the rest of the app uses.
    @discardableResult
    func finishSession(registration: [String: Any]? = nil) async throws -> UserRole {
        let role = try await FunctionsClient.shared.assignInitialRole(registration: registration)
        _ = try await Auth.auth().currentUser?.getIDTokenResult(forcingRefresh: true)
        return role
    }
}
