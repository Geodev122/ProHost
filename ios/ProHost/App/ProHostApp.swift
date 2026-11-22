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
                        .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
                }
            }
        }
    }
}
