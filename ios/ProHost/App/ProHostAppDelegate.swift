import FirebaseAppCheck
import FirebaseCore
import FirebaseMessaging
import GoogleMaps
import GoogleSignIn
import ProHostShared
import UIKit
import UserNotifications

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
        IosPlatform.shared.installMediaPicker(bridge: MediaPickerBridgeImpl())
        IosPlatform.shared.installLocation(bridge: LocationBridgeImpl())
        // Google Maps: only when CI passed a key (GMSApiKey in Info.plist from MAPS_IOS_API_KEY); otherwise the map stays blank.
        if let key = Bundle.main.object(forInfoDictionaryKey: "GMSApiKey") as? String, !key.isEmpty, !key.hasPrefix("$(") {
            GMSServices.provideAPIKey(key)
            IosPlatform.shared.installMaps(factory: GoogleMapFactory())
        }
        // Google Sign-In uses the iOS OAuth client of GoogleService-Info.plist (its reversed id is the URL scheme in project.yml).
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
            IosPlatform.shared.installGoogleSignIn(bridge: GoogleSignInBridgeImpl())
        }
        // Push: FCM over APNs (the APNs key is uploaded in Firebase). Taps route like Android's notification extras.
        IosPush.shared.install(bridge: FcmTokenBridge())
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }
}

extension ProHostAppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken, !token.isEmpty else { return }
        IosPush.shared.tokenRefreshed(token: token)
    }
}

extension ProHostAppDelegate: UNUserNotificationCenterDelegate {
    /// Android shows the notification while the app is open too.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        IosPush.shared.openNotification(
            targetTab: info["targetTab"] as? String,
            bookingId: info["bookingId"] as? String,
            spaceId: info["spaceId"] as? String)
        completionHandler()
    }
}

/// The FCM token for the shared session code (`PushTokens`); nil until APNs has registered this device.
final class FcmTokenBridge: NSObject, PushTokenBridge {
    func fcmToken(completion: @escaping (String?) -> Void) {
        Messaging.messaging().token { token, _ in completion(token) }
    }
}

/// Release builds attest with App Attest (iOS 14+), the iOS counterpart of Play Integrity.
final class ProHostAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}
