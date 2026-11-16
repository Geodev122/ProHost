import XCTest

/// Launches the real app (Firebase configured, shared Compose UI and platform bridges installed) and checks it is still
/// running a few seconds later. Catches launch crashes (missing Info.plist keys, Compose resources, bridge set-up) in CI.
final class LaunchSmokeTests: XCTestCase {
    func testAppLaunchesAndKeepsRunning() {
        let app = XCUIApplication()
        app.launch()
        sleep(6)
        XCTAssertEqual(app.state, .runningForeground, "ProHost stopped within 6 s of launch")
    }
}
