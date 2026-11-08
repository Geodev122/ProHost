import XCTest
@testable import ProHost

final class SessionResolverTests: XCTestCase {
    func testNoProfileSendsThePersonToRegistrationWithTheServerRole() {
        let phase = SessionResolver.resolve(uid: "u1", email: "a@b.co", role: .specialist, profile: nil)
        XCTAssertEqual(phase, .needsRegistration(AppUser(id: "u1", email: "a@b.co", fullName: "", role: .specialist)))
    }

    func testBlankFullNameMeansRegistrationEvenWithAPhone() {
        let phase = SessionResolver.resolve(uid: "u1", email: "a@b.co", role: .specialist,
                                            profile: ["fullName": "  ", "phone": "+96170111222", "email": "a@b.co"])
        guard case .needsRegistration(let user) = phase else { return XCTFail("expected registration, got \(phase)") }
        XCTAssertEqual(user.phone, "+96170111222")
    }

    func testCompleteProfileIsReadyAndTheTokenRoleWinsOverTheProfileMirror() {
        let phase = SessionResolver.resolve(uid: "u1", email: "a@b.co", role: .proHost,
                                            profile: ["fullName": "Dana Haddad", "role": "SPECIALIST", "email": "dana@x.co"])
        guard case .ready(let user) = phase else { return XCTFail("expected ready, got \(phase)") }
        XCTAssertEqual(user.role, .proHost)
        XCTAssertEqual(user.fullName, "Dana Haddad")
        XCTAssertEqual(user.email, "dana@x.co")
    }

    func testMissingProfileEmailFallsBackToTheAuthEmail() {
        let phase = SessionResolver.resolve(uid: "u1", email: "auth@x.co", role: .specialist, profile: ["fullName": "Dana"])
        guard case .ready(let user) = phase else { return XCTFail("expected ready") }
        XCTAssertEqual(user.email, "auth@x.co")
    }

    func testAdminWithoutAFullNameIsExempt() {
        let phase = SessionResolver.resolve(uid: "u1", email: "a@b.co", role: .admin, profile: ["fullName": ""])
        guard case .ready = phase else { return XCTFail("admins are provisioned server-side") }
    }

    func testProfileDefaultsMirrorAndroid() {
        let user = AppUser.fromProfile(id: "u9", data: [:])
        XCTAssertEqual(user, AppUser(id: "u9", email: "", fullName: "", role: .specialist, phone: ""))
        XCTAssertEqual(AppUser.fromProfile(id: "u9", data: ["role": "PRO_HOST"]).role, .proHost)
        XCTAssertEqual(AppUser.fromProfile(id: "u9", data: ["role": "SOMETHING_ELSE"]).role, .specialist)
    }

    func testRoleClaimParsing() {
        XCTAssertEqual(UserRole.fromClaim("PRO_HOST"), .proHost)
        XCTAssertEqual(UserRole.fromClaim("ADMIN"), .admin)
        XCTAssertNil(UserRole.fromClaim(nil))
        XCTAssertNil(UserRole.fromClaim(42))
        XCTAssertNil(UserRole.fromClaim("owner"))
    }
}

final class ShellTabsTests: XCTestCase {
    func testSpecialistTabsAreExploreSavedRentalsProfile() {
        XCTAssertEqual(ShellTabs.tabs(for: .specialist).map(\.id), ["search_map", "my_favorites", "pro_rentals", "pro_profile"])
    }

    func testProHostTabsStartWithListingsAndKeepExploreSecond() {
        XCTAssertEqual(ShellTabs.tabs(for: .proHost).map(\.id), ["manage_listings", "search_map", "pro_rentals", "pro_profile"])
    }

    func testAdminSeesTheSpecialistTabsBecauseThereIsNoAdminConsoleOnIOS() {
        XCTAssertEqual(ShellTabs.tabs(for: .admin).map(\.id), ShellTabs.tabs(for: .specialist).map(\.id))
    }

    func testExploreIsTheCentreAndOnlyHostsGetTheOrangeAccent() {
        XCTAssertEqual(ShellTabs.defaultTabId, "search_map")
        XCTAssertTrue(ShellTabs.usesSecondaryAccent(.proHost))
        XCTAssertFalse(ShellTabs.usesSecondaryAccent(.specialist))
        XCTAssertFalse(ShellTabs.usesSecondaryAccent(.admin))
    }
}
