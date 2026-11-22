import GoogleSignIn
import SwiftUI

@main
struct ProHostApp: App {
    @UIApplicationDelegateAdaptor(ProHostAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            Group {
                if AppInfo.isRunningUnitTests {
                    Color.clear
                } else {
                    RootView()
                        // Google Sign-In returns to the app through its reversed-client-id URL scheme.
                        // Google Sign-In's redirect first; every other URL (Universal Links, prohost://) goes to the shared router.
                        .onOpenURL { url in
                            if !GIDSignIn.sharedInstance.handle(url) { IosPush.shared.openUrl(url: url.absoluteString) }
                        }
                }
            }
        }
    }
}
