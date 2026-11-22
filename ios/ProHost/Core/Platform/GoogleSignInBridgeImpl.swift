import GoogleSignIn
import ProHostShared
import UIKit

/// Google Sign-In for the shared sign-in screen (`AuthCredentials` on iOS). The ID token carries the caller's nonce,
/// which the shared code checks before Firebase sees the token, exactly as on Android.
final class GoogleSignInBridgeImpl: NSObject, GoogleSignInBridge {
    func signIn(nonce: String, completion: @escaping (GoogleSignInReply) -> Void) {
        DispatchQueue.main.async {
            guard let presenter = Self.topViewController() else {
                return completion(Self.failure("No window to present Google sign-in"))
            }
            GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: nonce
            ) { result, error in
                if let error {
                    let cancelled = (error as? GIDSignInError)?.code == .canceled
                    return completion(GoogleSignInReply(
                        idToken: nil, displayName: nil, givenName: nil, familyName: nil, pictureUri: nil,
                        cancelled: cancelled, error: error.localizedDescription))
                }
                guard let user = result?.user, let token = user.idToken?.tokenString else {
                    return completion(Self.failure("Google returned no ID token"))
                }
                let profile = user.profile
                completion(GoogleSignInReply(
                    idToken: token,
                    displayName: profile?.name,
                    givenName: profile?.givenName,
                    familyName: profile?.familyName,
                    pictureUri: profile?.imageURL(withDimension: 256)?.absoluteString,
                    cancelled: false,
                    error: nil))
            }
        }
    }

    private static func failure(_ message: String) -> GoogleSignInReply {
        GoogleSignInReply(idToken: nil, displayName: nil, givenName: nil, familyName: nil, pictureUri: nil,
                          cancelled: false, error: message)
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
