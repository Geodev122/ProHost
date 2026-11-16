import FirebaseFunctions
import Foundation
import ProHostShared

/// Runs Cloud Functions callables (europe-west1, like Android) for the shared Kotlin `CallableFunctionsGateway`.
/// Errors are reported with the Android code names (`PERMISSION_DENIED`, ...) so shared code reads them the same way.
final class FirebaseCallableTransport: NSObject, CallableTransport {
    private let functions = Functions.functions(region: "europe-west1")

    func call(name: String, data: [String: Any]?, timeoutSeconds: Int32, completion: @escaping (CallableReply) -> Void) {
        let callable = functions.httpsCallable(name)
        if timeoutSeconds > 0 { callable.timeoutInterval = TimeInterval(timeoutSeconds) }
        callable.call(data) { result, error in
            if let error = error as NSError? {
                completion(CallableReply(data: nil, errorCode: Self.codeName(error), errorMessage: error.localizedDescription))
            } else {
                completion(CallableReply(data: result?.data as? [String: Any] ?? [:], errorCode: nil, errorMessage: nil))
            }
        }
    }

    /// Firebase's iOS error code mapped to the Android `FirebaseFunctionsException.Code` names.
    static func codeName(_ error: NSError) -> String {
        guard error.domain == FunctionsErrorDomain, let code = FunctionsErrorCode(rawValue: error.code) else {
            return error.domain == NSURLErrorDomain ? "UNAVAILABLE" : "UNKNOWN"
        }
        switch code {
        case .OK: return "OK"
        case .cancelled: return "CANCELLED"
        case .unknown: return "UNKNOWN"
        case .invalidArgument: return "INVALID_ARGUMENT"
        case .deadlineExceeded: return "DEADLINE_EXCEEDED"
        case .notFound: return "NOT_FOUND"
        case .alreadyExists: return "ALREADY_EXISTS"
        case .permissionDenied: return "PERMISSION_DENIED"
        case .resourceExhausted: return "RESOURCE_EXHAUSTED"
        case .failedPrecondition: return "FAILED_PRECONDITION"
        case .aborted: return "ABORTED"
        case .outOfRange: return "OUT_OF_RANGE"
        case .unimplemented: return "UNIMPLEMENTED"
        case .internal: return "INTERNAL"
        case .unavailable: return "UNAVAILABLE"
        case .dataLoss: return "DATA_LOSS"
        case .unauthenticated: return "UNAUTHENTICATED"
        @unknown default: return "UNKNOWN"
        }
    }
}
