import FirebaseAuth
import Foundation
import ProHostShared

/// Firebase Auth for the shared Kotlin `BridgedAuthGateway`. Failures carry the Android error-code names
/// (`AuthError.codeName(forFirebaseCode:)` in AuthService.swift), so shared code maps them exactly like Android.
final class FirebaseAuthBridge: NSObject, AuthBridge {
    func currentUid() -> String? { Auth.auth().currentUser?.uid }

    func usesPassword() -> Bool {
        Auth.auth().currentUser?.providerData.contains { $0.providerID == "password" } ?? false
    }

    func signInWithPassword(email: String, password: String, completion: @escaping (AuthReply) -> Void) {
        Auth.auth().signIn(withEmail: email, password: password) { result, error in completion(Self.reply(result, error)) }
    }

    func createAccount(email: String, password: String, completion: @escaping (AuthReply) -> Void) {
        Auth.auth().createUser(withEmail: email, password: password) { result, error in completion(Self.reply(result, error)) }
    }

    func signInWithGoogle(idToken: String, completion: @escaping (AuthReply) -> Void) {
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: "")
        Auth.auth().signIn(with: credential) { result, error in completion(Self.reply(result, error)) }
    }

    func signInWithApple(idToken: String, rawNonce: String, givenName: String?, familyName: String?,
                         completion: @escaping (AuthReply) -> Void) {
        var name = PersonNameComponents()
        name.givenName = givenName
        name.familyName = familyName
        let credential = OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: rawNonce, fullName: name)
        Auth.auth().signIn(with: credential) { result, error in completion(Self.reply(result, error)) }
    }

    func sendPasswordResetEmail(email: String, completion: @escaping (AuthReply) -> Void) {
        Auth.auth().sendPasswordReset(withEmail: email) { error in completion(Self.reply(nil, error)) }
    }

    func reloadEmailVerified(completion: BoolCallback) {
        guard let user = Auth.auth().currentUser else { return completion.done(value: false) }
        user.reload { error in
            let verified = error == nil && Auth.auth().currentUser?.isEmailVerified == true
            guard verified else { return completion.done(value: false) }
            // The ID token only carries email_verified after a refresh; the server mirrors it from there (as on Android).
            Auth.auth().currentUser?.getIDTokenForcingRefresh(true) { _, _ in completion.done(value: true) }
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
    }

    func currentEmail() -> String? { Auth.auth().currentUser?.email }

    func currentPhoneNumber() -> String? { Auth.auth().currentUser?.phoneNumber }

    func readRoleClaim(forceRefresh: Bool, completion: @escaping (TokenReply) -> Void) {
        guard let user = Auth.auth().currentUser else {
            return completion(TokenReply(value: nil, error: "No signed-in Firebase user"))
        }
        user.getIDTokenResult(forcingRefresh: forceRefresh) { result, error in
            if let error { return completion(TokenReply(value: nil, error: error.localizedDescription)) }
            completion(TokenReply(value: result?.claims["role"] as? String, error: nil))
        }
    }

    func refreshIdToken(completion: @escaping (TokenReply) -> Void) {
        guard let user = Auth.auth().currentUser else { return completion(TokenReply(value: nil, error: nil)) }
        user.getIDTokenForcingRefresh(true) { _, error in
            completion(TokenReply(value: nil, error: error?.localizedDescription))
        }
    }

    private static func reply(_ result: AuthDataResult?, _ error: Error?) -> AuthReply {
        guard let error = error as NSError? else {
            return AuthReply(errorCode: nil, message: nil, isNewUser: result?.additionalUserInfo?.isNewUser ?? false)
        }
        let code: String
        if error.domain == NSURLErrorDomain {
            code = "ERROR_NETWORK_REQUEST_FAILED"
        } else if error.domain == "FIRAuthErrorDomain", let name = AuthError.codeName(forFirebaseCode: error.code) {
            code = name
        } else {
            code = "ERROR_UNKNOWN"
        }
        return AuthReply(errorCode: code, message: error.localizedDescription, isNewUser: false)
    }
}
