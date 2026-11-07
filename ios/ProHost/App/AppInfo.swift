import Foundation

enum AppInfo {
    static let name = "ProHost"
    static let bundleId = "app.geonajjar.prohost"

    static var versionLabel: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "v\(version) (\(build))"
    }
}
