import AuthenticationServices
import CryptoKit
import ProHostShared
import UIKit

/// Sign in with Apple for the shared sign-in screen. The request carries SHA-256 of the shared code's raw nonce; Firebase
/// gets the raw one (`FirebaseAuthBridge.signInWithApple`) and checks it against the ID token.
final class AppleSignInBridgeImpl: NSObject, AppleSignInBridge, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var completion: ((AppleSignInReply) -> Void)?

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

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
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
