import Foundation

enum AppInfo {
    static let name = "ProHost"
    static let bundleId = "app.geonajjar.prohost"

    /// Unit tests launch the app as their host without Firebase (the delegate skips `FirebaseApp.configure()`), so nothing may touch Firebase then.
    static var isRunningUnitTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    static var versionLabel: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "v\(version) (\(build))"
    }
}
