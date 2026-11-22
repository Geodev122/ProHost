import XCTest

/// App Store screenshots of the real app (workflow `ios-screenshots.yml`, iPhone 6.9" simulator). Runs only when the workflow
/// passes SHOT_LANG (en or ar) and SCREENSHOT_DIR; every other test run skips it. The English pass signs in with the App Review
/// Specialist demo account (DEMO_EMAIL / DEMO_PASSWORD, from server-only app_config/app_review); the Arabic pass reuses that
/// session on the same simulator. Navigation goes through the app's own deep links (prohost://discovery, my_bookings, profile,
/// owner_subscriptions and the listing share link), so no step depends on translated button text. Each step writes
/// NN_name.png plus NN_name.txt (the accessibility tree), so a step that missed can be fixed from text alone.
final class StoreScreenshotTests: XCTestCase {
    private let env = ProcessInfo.processInfo.environment
    private var outDir: URL { URL(fileURLWithPath: env["SCREENSHOT_DIR"] ?? NSTemporaryDirectory(), isDirectory: true) }
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUp() {
        continueAfterFailure = true
    }

    func testStoreScreenshots() throws {
        guard let lang = env["SHOT_LANG"], !lang.isEmpty else { throw XCTSkip("store screenshots run only from ios-screenshots.yml") }
        try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(\(lang))", "-AppleLocale", lang == "ar" ? "ar_SA" : "en_US"]
        app.launch()
        settle(app, 8)

        if lang == "en" {
            if element(app, ["I already have an account"], timeout: 4) != nil {
                shot(app, "00_welcome")
                signIn(app)
            }
        }
        settle(app, 8)

        open(app, "prohost://discovery")
        shot(app, "01_explore")
        if tap(app, lang == "ar" ? ["قائمة", "List"] : ["List"]) { settle(app, 4); shot(app, "02_explore_list") }
        if tap(app, lang == "ar" ? ["خريطة", "Map"] : ["Map"]) { settle(app, 4) }

        if let listing = env["LISTING_ID"], !listing.isEmpty {
            open(app, "https://pro-host.tech/listing/\(listing)")
            shot(app, "03_listing")
            if tap(app, lang == "ar" ? ["التوفر", "Availability"] : ["Availability", "Check Availability"]) { settle(app, 4); shot(app, "04_availability") }
        }

        open(app, "prohost://owner_subscriptions")
        shot(app, "05_premium")
        open(app, "prohost://my_bookings")
        shot(app, "06_rentals")
        open(app, "prohost://profile")
        shot(app, "07_profile")
    }

    // MARK: - steps

    private func signIn(_ app: XCUIApplication) {
        guard let email = env["DEMO_EMAIL"], let password = env["DEMO_PASSWORD"], !email.isEmpty, !password.isEmpty else {
            XCTFail("DEMO_EMAIL / DEMO_PASSWORD missing"); return
        }
        _ = tap(app, ["I already have an account"])
        settle(app, 3)
        _ = tap(app, ["Continue with email"])
        settle(app, 3)
        let field = app.textFields.firstMatch
        if field.waitForExistence(timeout: 8) { field.tap(); field.typeText(email) } else { XCTFail("no email field"); dump(app, "fail_email") }
        _ = tap(app, ["Continue"])
        settle(app, 3)
        let secure = app.secureTextFields.firstMatch
        if secure.waitForExistence(timeout: 8) { secure.tap(); secure.typeText(password) } else { XCTFail("no password field"); dump(app, "fail_password") }
        if !tap(app, ["Sign in"]) { _ = tap(app, ["Continue"]) }
        settle(app, 10)
    }

    private func open(_ app: XCUIApplication, _ url: String) {
        guard let u = URL(string: url) else { return }
        app.open(u)
        settle(app, 6)
    }

    // MARK: - helpers

    /// Waits, then answers system alerts (notifications, location, tracking) with the "no" choice and closes the
    /// app's own first-launch analytics consent with "Not now".
    private func settle(_ app: XCUIApplication, _ seconds: UInt32) {
        sleep(seconds)
        for _ in 0..<3 {
            let alert = springboard.alerts.firstMatch
            guard alert.exists else { break }
            let no = ["Don’t Allow", "Don't Allow", "Ask App Not to Track", "Not Now", "عدم السماح"]
            if let b = no.lazy.map({ alert.buttons[$0] }).first(where: { $0.exists }) { b.tap() } else { alert.buttons.element(boundBy: 0).tap() }
            sleep(1)
        }
        _ = tap(app, ["Not now", "ليس الآن"], timeout: 0.5)
    }

    private func element(_ app: XCUIApplication, _ labels: [String], timeout: TimeInterval = 4) -> XCUIElement? {
        for label in labels {
            let q = app.descendants(matching: .any).matching(NSPredicate(format: "label ==[c] %@", label))
            let e = q.firstMatch
            if e.waitForExistence(timeout: timeout) && e.isHittable { return e }
        }
        return nil
    }

    @discardableResult
    private func tap(_ app: XCUIApplication, _ labels: [String], timeout: TimeInterval = 4) -> Bool {
        guard let e = element(app, labels, timeout: timeout) else { return false }
        e.tap()
        return true
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let s = XCUIScreen.main.screenshot()
        try? s.pngRepresentation.write(to: outDir.appendingPathComponent("\(name).png"))
        let a = XCTAttachment(screenshot: s)
        a.name = name
        a.lifetime = .keepAlways
        add(a)
        dump(app, name)
    }

    private func dump(_ app: XCUIApplication, _ name: String) {
        try? app.debugDescription.write(to: outDir.appendingPathComponent("\(name).txt"), atomically: true, encoding: .utf8)
    }
}
