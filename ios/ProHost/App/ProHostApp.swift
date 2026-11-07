import SwiftUI

@main
struct ProHostApp: App {
    var body: some Scene {
        WindowGroup {
            SplashView()
                .dynamicTypeSize(...TextScale.maxSize)
        }
    }
}
