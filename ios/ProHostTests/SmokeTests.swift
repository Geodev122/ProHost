import XCTest
@testable import ProHost

final class SmokeTests: XCTestCase {
    func testAppName() {
        XCTAssertEqual(AppInfo.name, "ProHost")
    }

    func testBundleIdMatchesAndroidApplicationId() {
        XCTAssertEqual(AppInfo.bundleId, "app.geonajjar.prohost")
    }
}
