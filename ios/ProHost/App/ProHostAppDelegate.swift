import FirebaseAppCheck
import FirebaseCore
import UIKit

/// Firebase start-up. Analytics stays off until the person accepts (Info.plist keys + consent dialog later); unit tests never touch Firebase.
final class ProHostAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        guard ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] == nil else { return true }
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(ProHostAppCheckProviderFactory())
        #endif
        FirebaseApp.configure()
        return true
    }
}

/// Release builds attest with App Attest (iOS 14+), the iOS counterpart of Play Integrity.
final class ProHostAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}
