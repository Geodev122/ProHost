import CoreLocation
import ProHostShared

/// Core Location for the shared "locate me" buttons: When-In-Use authorisation, one fresh fix, the last known position.
/// All completions run on the main thread.
final class LocationBridgeImpl: NSObject, LocationBridge, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var authCompletion: BoolCallback?
    private var fixCompletions: [(LocationReply) -> Void] = []

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func locationStatus() -> Int32 {
        guard CLLocationManager.locationServicesEnabled() else { return 3 }
        switch manager.authorizationStatus {
        case .notDetermined: return 0
        case .authorizedWhenInUse, .authorizedAlways: return 1
        default: return 2
        }
    }

    func requestWhenInUse(completion: BoolCallback) {
        DispatchQueue.main.async {
            guard self.manager.authorizationStatus == .notDetermined else {
                return completion.done(value: self.locationStatus() == 1)
            }
            self.authCompletion = completion
            self.manager.requestWhenInUseAuthorization()
        }
    }

    func currentFix(completion: @escaping (LocationReply) -> Void) {
        DispatchQueue.main.async {
            self.fixCompletions.append(completion)
            if self.fixCompletions.count == 1 { self.manager.requestLocation() }
        }
    }

    func lastFix(completion: @escaping (LocationReply) -> Void) {
        DispatchQueue.main.async {
            if let last = self.manager.location {
                completion(Self.found(last))
            } else {
                completion(LocationReply(found: false, latitude: 0, longitude: 0, error: nil))
            }
        }
    }

    // MARK: CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus != .notDetermined, let completion = authCompletion else { return }
        authCompletion = nil
        completion.done(value: locationStatus() == 1)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let reply = locations.last.map(Self.found) ?? LocationReply(found: false, latitude: 0, longitude: 0, error: nil)
        finish(reply)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // kCLErrorLocationUnknown = no fix right now (Android's null), anything else = the provider failed.
        if let clError = error as? CLError, clError.code == .locationUnknown {
            finish(LocationReply(found: false, latitude: 0, longitude: 0, error: nil))
        } else {
            finish(LocationReply(found: false, latitude: 0, longitude: 0, error: error.localizedDescription))
        }
    }

    private func finish(_ reply: LocationReply) {
        let completions = fixCompletions
        fixCompletions = []
        completions.forEach { $0(reply) }
    }

    private static func found(_ location: CLLocation) -> LocationReply {
        LocationReply(found: true, latitude: location.coordinate.latitude, longitude: location.coordinate.longitude, error: nil)
    }
}
