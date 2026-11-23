import XCTest

/// App Store screenshots of the real app (workflow `ios-screenshots.yml`, iPhone 6.9" simulator). Runs only when the workflow
/// passes SHOT_LANG (en or ar) and SCREENSHOT_DIR; every other test run skips it. The English pass signs in with the App Review
/// Specialist demo account (DEMO_EMAIL / DEMO_PASSWORD, from server-only app_config/app_review); the Arabic pass signs in
/// again (unsigned simulator builds keep no keychain session). Navigation taps the bottom-nav tabs by test tag (nav_item_<id>); Premium and the listing open last
/// through deep links (a relaunch signs in again), so no step depends on translated button text. Each step writes
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
        settle(app, 2)

        // Unsigned simulator builds cannot keep the Firebase session in the keychain, so each pass (and every
        // relaunch) signs in again; the auth buttons are found by their test tags in both languages.
        if byId(app, "auth_signin_button", timeout: 6) != nil {
            if lang == "en" { shot(app, "00_welcome") }
            signIn(app)
        }
        settle(app, 6)
        dump(app, "after_signin")
        shot(app, "01_explore")
        if tap(app, lang == "ar" ? ["قائمة", "List"] : ["List"]) { settle(app, 4); shot(app, "02_explore_list") }
        if tap(app, lang == "ar" ? ["خريطة", "Map"] : ["Map"]) { settle(app, 3) }

        if tab(app, "pro_rentals") { shot(app, "06_rentals") }
        if tab(app, "pro_profile") { shot(app, "07_profile") }
        if tab(app, "my_favorites") { shot(app, "08_saved") }

        // Deep links last: opening a URL may relaunch the app (then it signs in again and re-opens the link).
        open(app, "prohost://owner_subscriptions")
        shot(app, "05_premium")
        if let listing = env["LISTING_ID"], !listing.isEmpty {
            open(app, "https://pro-host.tech/listing/\(listing)")
            shot(app, "03_listing")
            if tap(app, lang == "ar" ? ["التوفر", "Availability"] : ["Availability", "Check Availability"]) { settle(app, 4); shot(app, "04_availability") }
        }
    }

    // MARK: - steps

    private func signIn(_ app: XCUIApplication) {
        guard let email = env["DEMO_EMAIL"], let password = env["DEMO_PASSWORD"], !email.isEmpty, !password.isEmpty else {
            XCTFail("DEMO_EMAIL / DEMO_PASSWORD missing"); return
        }
        if let b = byId(app, "auth_signin_button", timeout: 4) { b.tap() } else { _ = tap(app, ["I already have an account"]) }
        settle(app, 3)
        // Provider step (only when Google / Apple are offered first): pick email.
        if let p = byId(app, "auth_email_provider", timeout: 3) { p.tap(); settle(app, 2) }
        if let field = byId(app, "auth_email_input", timeout: 8) {
            field.tap(); sleep(1); app.typeText(email)
        } else { XCTFail("no email field"); dump(app, "fail_email") }
        if let c = byId(app, "auth_email_continue", timeout: 3) { c.tap() }
        settle(app, 3)
        if let field = byId(app, "auth_password_input", timeout: 8) {
            field.tap(); sleep(1); app.typeText(password)
        } else { XCTFail("no password field"); dump(app, "fail_password") }
        if let b = byId(app, "submit_email_button", timeout: 3) { b.tap() } else { dump(app, "fail_signin_button") }
        settle(app, 10)
    }

    private func open(_ app: XCUIApplication, _ url: String) {
        guard let u = URL(string: url) else { return }
        app.open(u)
        settle(app, 6)
        if byId(app, "auth_signin_button", timeout: 2) != nil {
            dump(app, "relaunched_" + (u.host ?? "link"))
            signIn(app)
            settle(app, 4)
            app.open(u)
            settle(app, 6)
        }
    }

    /// Bottom-nav tab by its test tag (`nav_item_<id>`); opens the collapsed pill's handle first when needed.
    private func tab(_ app: XCUIApplication, _ id: String) -> Bool {
        var item = byId(app, "nav_item_\(id)", timeout: 3)
        if item == nil, let handle = byId(app, "bottom_navigation_handle", timeout: 2) { handle.tap(); sleep(1); item = byId(app, "nav_item_\(id)", timeout: 3) }
        guard let found = item else { dump(app, "fail_tab_\(id)"); return false }
        found.tap()
        settle(app, 5)
        return true
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
        _ = tap(app, ["Don't allow", "Don’t allow", "Not now", "ليس الآن", "عدم السماح"], timeout: 0.5)
    }

    private func byId(_ app: XCUIApplication, _ id: String, timeout: TimeInterval) -> XCUIElement? {
        let e = app.descendants(matching: .any).matching(identifier: id).firstMatch
        return e.waitForExistence(timeout: timeout) ? e : nil
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
