import SwiftUI
import UIKit
import XCTest
@testable import ProHost

final class DesignTokensTests: XCTestCase {
    private func components(_ color: Color, dark: Bool) -> [Int] {
        let trait = UITraitCollection(userInterfaceStyle: dark ? .dark : .light)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(color).resolvedColor(with: trait).getRed(&r, green: &g, blue: &b, alpha: &a)
        return [Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded())]
    }

    func testSteelBlueLightAndDark() {
        XCTAssertEqual(components(PH.primary, dark: false), [0x2B, 0x5A, 0x8C])
        XCTAssertEqual(components(PH.primary, dark: true), [0x93, 0xB8, 0xE0])
    }

    func testOrangeActionLightAndDark() {
        XCTAssertEqual(components(PH.secondary, dark: false), [0xD2, 0x43, 0x2F])
        XCTAssertEqual(components(PH.secondary, dark: true), [0xFF, 0x8A, 0x75])
    }

    func testMetricsMirrorAndroid() {
        XCTAssertEqual([Radius.xs, Radius.sm, Radius.md, Radius.lg], [6, 12, 20, 28])
        XCTAssertEqual([Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl], [4, 8, 12, 16, 24, 32])
        XCTAssertEqual([Elevation.level0, Elevation.level1, Elevation.level2, Elevation.level3], [0, 1, 3, 6])
    }

    func testNoTextBelowTwelvePoints() {
        for style in PHType.all {
            XCTAssertGreaterThanOrEqual(style.size, TextScale.minPointSize)
        }
    }

    func testManropeIsBundledAndRegistered() {
        for name in ["Manrope-SemiBold", "Manrope-Bold", "Manrope-ExtraBold", "Roboto-Regular", "Roboto-Medium", "Roboto-Bold"] {
            XCTAssertNotNil(UIFont(name: name, size: 16), "\(name) is not registered (UIAppFonts)")
        }
    }

    func testEveryTextStyleResolvesToARegisteredFont() {
        for style in PHType.all {
            XCTAssertNotNil(UIFont(name: style.fontName, size: style.size), "\(style.fontName) for size \(style.size)")
            XCTAssertGreaterThanOrEqual(style.lineHeight, style.size)
        }
    }

    func testEveryMaterialIconIsInTheAssetCatalog() {
        XCTAssertFalse(PHIcon.allCases.isEmpty)
        for icon in PHIcon.allCases {
            XCTAssertNotNil(UIImage(named: icon.rawValue), "\(icon.rawValue) is missing from Assets.xcassets/Icons")
        }
    }

    func testAutoMirroredIconsAreMarked() {
        XCTAssertTrue(PHIcon.allCases.contains { $0.autoMirrored })
    }

    func testAvatarInitialsFollowAndroid() {
        XCTAssertEqual(ProAvatar.initials(from: "Jane Doe"), "JD")
        XCTAssertEqual(ProAvatar.initials(from: "  rana   haddad  khoury "), "RH")
        XCTAssertEqual(ProAvatar.initials(from: "Layla"), "L")
        XCTAssertEqual(ProAvatar.initials(from: ""), "PS")
    }

    func testBadgeTypesMapToTheSameStatusesAsAndroid() {
        XCTAssertEqual(ProBadgeType.verifiedMember.status, .verified)
        XCTAssertEqual(ProBadgeType.acceptedLocked.status, .occupied)
        XCTAssertEqual(ProBadgeType.tierA.status, .gold)
        XCTAssertEqual(ProBadgeType.customWarning.status, .pending)
    }

    func testBrandImagesAndLocalizedKeysResolve() {
        for name in ["ic_prohost_logo_brand", "prohost_checkmark_logo", "prohost_brand_name", "prohost_login_lockup",
                     "img_discovery_hero", "img_login_banner", "img_owner_hero", "auth_background"] {
            XCTAssertNotNil(UIImage(named: name), name)
        }
        for key in ["available", "occupied_booked", "license_id_verified", "pending_approval", "maintenance_slot",
                    "cancelled_unavailable", "super_admin", "premium_tier", "information", "prohost", "member", "host", "admin",
                    "open_menu", "back", "next_photo", "previous_photo", "verified"] {
            XCTAssertNotEqual(L(key), key, "\(key) missing from Localizable.xcstrings")
        }
    }
}
