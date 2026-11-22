import AuthenticationServices
import CryptoKit
import FirebaseAuth
import ProHostShared
import UIKit

/// Sign in with Apple for the shared sign-in screen. The request carries SHA-256 of the shared code's raw nonce; Firebase
/// gets the raw one (`FirebaseAuthBridge.signInWithApple`) and checks it against the ID token.
final class AppleSignInBridgeImpl: NSObject, AppleSignInBridge, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var completion: ((AppleSignInReply) -> Void)?
    /// Set while the sheet runs for token revocation before account deletion (not a sign-in).
    private var revokeDone: RevokeCallback?
    // RevokeCallback outcomes (Kotlin RevokeCallback.DONE / CANCELLED / FAILED).
    private static let done: Int32 = 0
    private static let cancelled: Int32 = 1
    private static let failed: Int32 = 2

    func signInWithAppleSheet(rawNonce: String, completion: @escaping (AppleSignInReply) -> Void) {
        DispatchQueue.main.async {
            self.completion = completion
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            request.nonce = Self.sha256(rawNonce)
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func revokeAppleTokenIfNeeded(done: RevokeCallback) {
        guard let user = Auth.auth().currentUser,
              user.providerData.contains(where: { $0.providerID == "apple.com" }) else {
            return done.finished(outcome: Self.done)
        }
        DispatchQueue.main.async {
            self.revokeDone = done
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = []
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let done = revokeDone {
            revokeDone = nil
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let data = credential.authorizationCode, let code = String(data: data, encoding: .utf8) else {
                return done.finished(outcome: Self.failed)
            }
            Auth.auth().revokeToken(withAuthorizationCode: code) { error in
                done.finished(outcome: error == nil ? Self.done : Self.failed)
            }
            return
        }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let data = credential.identityToken, let token = String(data: data, encoding: .utf8) else {
            return finish(AppleSignInReply(idToken: nil, givenName: nil, familyName: nil, cancelled: false,
                                           error: "Apple returned no identity token"))
        }
        finish(AppleSignInReply(idToken: token, givenName: credential.fullName?.givenName,
                                familyName: credential.fullName?.familyName, cancelled: false, error: nil))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        if let done = revokeDone {
            revokeDone = nil
            return done.finished(outcome: cancelled ? Self.cancelled : Self.failed)
        }
        finish(AppleSignInReply(idToken: nil, givenName: nil, familyName: nil, cancelled: cancelled,
                                error: error.localizedDescription))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    private func finish(_ reply: AppleSignInReply) {
        let done = completion
        completion = nil
        done?(reply)
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
