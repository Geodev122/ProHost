import CryptoKit
import Foundation
import Security

/// Per-attempt nonce for Google and Apple sign-in (`data/auth/GoogleNonce.kt`): the ID token's `nonce` claim is checked
/// locally before Firebase sees it, so a token replayed from another request is refused.
enum AuthNonce {
    /// 32 random bytes, URL-safe base64 without padding (43 characters).
    static func newNonce() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess, "SecRandomCopyBytes failed")
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// Apple's request carries the SHA-256 (hex) of the raw nonce; Firebase gets the raw one.
    static func sha256Hex(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// True only when `idToken` is a JWT whose payload carries exactly `nonce`; false on any parse problem.
    static func idTokenMatches(_ idToken: String, nonce: String) -> Bool {
        guard !nonce.isEmpty, let claim = nonceClaim(idToken) else { return false }
        return claim == nonce
    }

    private static func nonceClaim(_ idToken: String) -> String? {
        let parts = idToken.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        var b64 = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64.append("=") }
        guard let data = Data(base64Encoded: b64), let payload = String(data: data, encoding: .utf8) else { return nil }
        guard let regex = try? NSRegularExpression(pattern: "\"nonce\"\\s*:\\s*\"([^\"\\\\]*)\"") else { return nil }
        let range = NSRange(payload.startIndex..., in: payload)
        guard let match = regex.firstMatch(in: payload, range: range), match.numberOfRanges > 1,
              let r = Range(match.range(at: 1), in: payload) else { return nil }
        return String(payload[r])
    }
}
