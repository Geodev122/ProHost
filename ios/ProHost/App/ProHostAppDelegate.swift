import FirebaseAppCheck
import FirebaseCore
import GoogleSignIn
import ProHostShared
import UIKit

/// Firebase start-up. Analytics stays off until the person accepts (Info.plist keys + consent dialog later); unit tests never touch Firebase.
final class ProHostAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        guard !AppInfo.isRunningUnitTests else { return true }
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(ProHostAppCheckProviderFactory())
        #endif
        FirebaseApp.configure()
        // Shared Kotlin gateways (C1): Cloud Functions, Auth, Firestore and Storage.
        IosPlatform.shared.installFunctions(transport: FirebaseCallableTransport())
        IosPlatform.shared.installAuth(bridge: FirebaseAuthBridge())
        IosPlatform.shared.installFirestore(bridge: FirebaseFirestoreBridge())
        IosPlatform.shared.installStorage(native: FirebaseStorageBridge())
        // Google Sign-In uses the iOS OAuth client of GoogleService-Info.plist (its reversed id is the URL scheme in project.yml).
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            IosPlatform.shared.installGoogleSignIn(bridge: GoogleSignInBridgeImpl())
        }
        return true
    }
}

/// Release builds attest with App Attest (iOS 14+), the iOS counterpart of Play Integrity.
final class ProHostAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}
