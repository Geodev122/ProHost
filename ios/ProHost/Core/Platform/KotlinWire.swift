import Foundation

/// Values coming from Kotlin into a Firebase SDK. Kotlin/Native boxes a `Boolean` as an NSNumber subclass whose objCType is "c"
/// (not a CFBoolean), and Firebase's iOS serializers then send it as the number 1/0 — the server's `tosAccepted !== true` check
/// rejected every iOS registration ("You must accept the Terms of Use…") and Firestore stored integers where rules expect bools.
/// `normalize` turns those boxes into real Swift `Bool`s, recursively; everything else passes through unchanged.
enum KotlinWire {
    static func normalize(_ value: Any) -> Any {
        switch value {
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue }
            let type = String(cString: n.objCType)
            return (type == "c" || type == "B") ? n.boolValue : n
        case let a as [Any]:
            return a.map { normalize($0) }
        case let d as [String: Any]:
            return d.mapValues { normalize($0) }
        default:
            return value
        }
    }

    static func normalize(_ data: [String: Any]?) -> [String: Any]? {
        data.map { $0.mapValues { normalize($0) } }
    }
}
