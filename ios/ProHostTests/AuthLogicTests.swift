import XCTest
@testable import ProHost

final class PasswordRulesTests: XCTestCase {
    func testValidPasswords() {
        XCTAssertTrue(PasswordRules.isValid("clinic2026"))
        XCTAssertTrue(PasswordRules.isValid("Ab3defgh"))
        XCTAssertTrue(PasswordRules.isValid("كلمةpass1"))
    }

    func testEachMissingRuleIsReportedOnItsOwn() {
        XCTAssertEqual(PasswordRules.check("ab1"), .init(longEnough: false, hasLowercase: true, hasNumber: true))
        XCTAssertEqual(PasswordRules.check("ABCDEFG1"), .init(longEnough: true, hasLowercase: false, hasNumber: true))
        XCTAssertEqual(PasswordRules.check("abcdefgh"), .init(longEnough: true, hasLowercase: true, hasNumber: false))
    }

    func testSevenIsTooShortEightIsEnough() {
        XCTAssertFalse(PasswordRules.isValid("abcde12"))
        XCTAssertTrue(PasswordRules.isValid("abcdef12"))
        XCTAssertEqual(PasswordRules.minLength, 8)
    }

    func testEmptyFailsEveryRule() {
        let c = PasswordRules.check("")
        XCTAssertFalse(c.longEnough || c.hasLowercase || c.hasNumber || c.ok)
    }
}

final class AuthErrorTests: XCTestCase {
    func testWrongEmailOrPasswordReadTheSame() {
        for code in ["ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_LOGIN_CREDENTIALS",
                     "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "error_wrong_password"] {
            XCTAssertEqual(AuthError.fromCode(code), .invalidCredentials, code)
        }
    }

    func testSignUpAndServiceStates() {
        XCTAssertEqual(AuthError.fromCode("ERROR_EMAIL_ALREADY_IN_USE"), .emailInUse)
        XCTAssertEqual(AuthError.fromCode("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL"), .emailInUse)
        XCTAssertEqual(AuthError.fromCode("ERROR_WEAK_PASSWORD"), .weakPassword)
        XCTAssertEqual(AuthError.fromCode("PASSWORD_DOES_NOT_MEET_REQUIREMENTS"), .weakPassword)
        XCTAssertEqual(AuthError.fromCode("ERROR_INVALID_EMAIL"), .invalidEmail)
        XCTAssertEqual(AuthError.fromCode("ERROR_TOO_MANY_REQUESTS"), .tooManyRequests)
        XCTAssertEqual(AuthError.fromCode("ERROR_USER_DISABLED"), .userDisabled)
        XCTAssertEqual(AuthError.fromCode("ERROR_NETWORK_REQUEST_FAILED"), .network)
        XCTAssertEqual(AuthError.fromCode("ERROR_OPERATION_NOT_ALLOWED"), .unavailable)
    }

    func testUnknownFallsBackAndMessageCanCarryTheReason() {
        XCTAssertEqual(AuthError.fromCode(nil), .unknown)
        XCTAssertEqual(AuthError.fromCode("SOMETHING_NEW"), .unknown)
        XCTAssertEqual(AuthError.fromCode("UNKNOWN", message: "PASSWORD_DOES_NOT_MEET_REQUIREMENTS: x"), .weakPassword)
    }

    func testEveryErrorHasItsOwnKeyAndARealTranslation() {
        XCTAssertEqual(Set(AuthError.allCases.map(\.messageKey)).count, AuthError.allCases.count)
        for error in AuthError.allCases {
            // The catalog is bundled: a missing key would come back as the key itself.
            XCTAssertNotEqual(error.message, error.messageKey, "\(error.messageKey) missing from Localizable.xcstrings")
        }
    }
}

final class AuthNonceTests: XCTestCase {
    private func jwt(_ payloadJson: String) -> String {
        let payload = Data(payloadJson.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        return "e30.\(payload).sig"
    }

    func testTokenCarryingTheNonceMatches() {
        let nonce = AuthNonce.newNonce()
        XCTAssertTrue(AuthNonce.idTokenMatches(jwt("{\"sub\":\"1\",\"nonce\":\"\(nonce)\",\"email_verified\":true}"), nonce: nonce))
    }

    func testOtherOrMissingNonceIsRefused() {
        XCTAssertFalse(AuthNonce.idTokenMatches(jwt("{\"nonce\":\"replayed\"}"), nonce: AuthNonce.newNonce()))
        XCTAssertFalse(AuthNonce.idTokenMatches(jwt("{\"sub\":\"1\"}"), nonce: AuthNonce.newNonce()))
        XCTAssertFalse(AuthNonce.idTokenMatches(jwt("{\"nonce\":\"\"}"), nonce: ""))
    }

    func testMalformedTokensAreRefusedNotThrown() {
        let nonce = AuthNonce.newNonce()
        XCTAssertFalse(AuthNonce.idTokenMatches("", nonce: nonce))
        XCTAssertFalse(AuthNonce.idTokenMatches("only.two", nonce: nonce))
        XCTAssertFalse(AuthNonce.idTokenMatches("a.%%%not-base64%%%.c", nonce: nonce))
        XCTAssertFalse(AuthNonce.idTokenMatches(jwt("not json at all"), nonce: nonce))
    }

    func testNoncesAreLongUrlSafeAndUnique() {
        let a = AuthNonce.newNonce(), b = AuthNonce.newNonce()
        XCTAssertEqual(a.count, 43)
        XCTAssertNotEqual(a, b)
        XCTAssertNil(a.firstIndex(where: { "+/=".contains($0) }))
    }

    func testSha256Hex() {
        XCTAssertEqual(AuthNonce.sha256Hex("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}

final class AppUserTests: XCTestCase {
    func testBlankNameSendsToRegistrationButAdminIsExempt() {
        XCTAssertFalse(AppUser(id: "u", email: "a@b.c", fullName: "  ", role: .specialist).isProfileComplete)
        XCTAssertTrue(AppUser(id: "u", email: "a@b.c", fullName: "Rana", role: .specialist).isProfileComplete)
        XCTAssertTrue(AppUser(id: "u", email: "a@b.c", fullName: "", role: .admin).isProfileComplete)
    }

    func testPhoneIsNeverPartOfCompleteness() {
        XCTAssertTrue(AppUser(id: "u", email: "a@b.c", fullName: "Rana", role: .proHost, phone: "").isProfileComplete)
    }
}
