import SwiftUI

@main
struct ProHostApp: App {
    @UIApplicationDelegateAdaptor(ProHostAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            SplashView()
                .dynamicTypeSize(...TextScale.maxSize)
        }
    }
}
