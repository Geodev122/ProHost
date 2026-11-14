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
                }
            }
            .dynamicTypeSize(...TextScale.maxSize)
        }
    }
}
