import FirebaseAnalytics
import ProHostShared

/// The only place in the iOS app that touches the Firebase Analytics SDK (Android: FirebaseAnalyticsSink.kt).
/// Collection stays off (Info.plist) until the shared consent dialog's "Allow"; ad consent is always denied.
final class FirebaseAnalyticsSink: NSObject, AnalyticsSink {
    func logEvent(name: String, params: [String: Any]) {
        Analytics.logEvent(name, parameters: params)
    }

    func setUserId(id: String?) {
        Analytics.setUserID(id)
    }

    func setUserProperty(name: String, value: String?) {
        Analytics.setUserProperty(value, forName: name)
    }

    func setCollectionEnabled(enabled: Bool) {
        Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    func setAnalyticsConsent(granted: Bool) {
        Analytics.setConsent([
            .analyticsStorage: granted ? .granted : .denied,
            .adStorage: .denied,
            .adUserData: .denied,
            .adPersonalization: .denied,
        ])
    }

    func resetData() {
        Analytics.resetAnalyticsData()
    }

    func appInstanceId(onResult: @escaping (String?) -> Void) {
        onResult(Analytics.appInstanceID())
    }
}
