# ProHost iOS: readiness audit and parity plan (7 Nov 2026)

Decisions: native SwiftUI; no Mac (GitHub macOS runners + TestFlight); Individual Apple account; v1 = full parity for specialists,
Pro Hosts and Premium; **Google Maps SDK for iOS** (same map as Android); **no Admin Console on iOS** (Android/web only).
The roadmap and owner checklist live in `docs/IOS_PLAN.md`; the owner's step-by-step guide is `docs/OWNER_GUIDE.md`.
Update the tables below as milestones finish (a screen is done only when its Android | iOS contact sheet is reviewed and the checklist is ticked).

## 1. Audit: where iOS stands (7 Nov 2026)
Android reference: 37,393 lines of UI (screens 15.4k, components 12.5k, theme/util/navigation/viewmodels ~9k) plus about
12.7k lines of data, billing, auth, legal, analytics and service code; 1,225 strings (EN+AR). Admin tabs (~5k lines) are out of scope.
iOS today: **1,066 Swift lines, roughly 3% of the work by size**. It is a verified foundation, not an app yet.

| Area | Android | iOS now | Status |
|---|---|---|---|
| Build, CI, signing, TestFlight upload | n/a | `ios-ci.yml`, `ios-testflight.yml`, `ios-inspect.yml`; build 1.0.0 (9) processed VALID in App Store Connect | Done (entitlements not yet verified) |
| Strings (1,235 keys EN+AR) | strings.xml, plurals.xml | `Localizable.xcstrings` generated, parity-checked in CI | Done |
| Design tokens, Manrope fonts | Theme.kt, Type.kt, Radius/Spacing/Elevation | `PH` colors, Radius, Spacing, type scale, fonts registered | Done |
| Base components | CommonComponents.kt (1.6k lines) | ProCard, buttons, InputField, AlertBanner, Shimmer, Snackbar | ~15% (more needed: chips, sheets, dialogs, badges, avatar, nav pill, folder tabs...) |
| Firebase wiring | Firebase BoM | Auth, Firestore, Functions, Messaging, Storage, AppCheck linked; App Check debug/App Attest | Done; Crashlytics + analytics deferred |
| Auth core | AuthViewModel, AuthErrors, PasswordRules, GoogleNonce | `AuthService` (email, Apple), AuthError, PasswordRules, AuthNonce, isProfileComplete, 24 tests | ~30%; Google sign-in, sign-in UI, registration missing |
| Models | DataModels.kt (28 classes, ~2k lines with data/model) | `AppUser`, `UserRole` only | ~3% |
| Data layer | FirestoreService, repositories (3.8k lines) | none | 0% |
| Screens (non-admin, 14 + sheets) | 15.4k lines minus admin | splash only | 0% |
| Billing | Play Billing + BillingController (1k lines) | none; server side for Apple not written | 0% |
| Push | FCM service + channels | none (no APNs payload on the server) | 0% |
| Deep links | https pro-host.tech + `prohost://` | `prohost` scheme registered only | ~10% |
| Maps | Google Maps Compose + custom markers/clusters | none | 0% |
| Legal/consent/analytics | LegalContent, AnalyticsTracker (91 events), consent dialog | none | 0% |

### Backend / platform readiness for iOS
- Present and reusable: Firestore + rules, callables (22 used by the app), `assignInitialRole` (Play Integrity only when a token is
  sent, so iOS works), entitlement manager, display codes, FCM sender.
- Missing (Milestone M1): Apple purchase verification and App Store Server Notifications V2, `subscriptionPlatform: "ios"`,
  APNs block in `lib/push.ts`, App Attest handling log, **report/block for user content (App Review 1.2: not found on any platform)**,
  Apple sign-in token revocation on delete (client + Firebase Apple provider key), apple-app-site-association file, `/get` iOS redirect.
- Apple account (read from the API on 7 Nov): app record 6831983164; bundle ID has Sign in with Apple, Push, App Attest, In-App
  Purchase; **Associated Domains missing**; no devices or profiles; TestFlight build valid.
- Open technical risk: the pipeline archives unsigned and signs at export. Capabilities (Sign in with Apple, Push) need entitlements in
  the signature; verify by printing `codesign -d --entitlements` from the exported IPA; fall back to creating a distribution
  certificate and App Store profile through the Admin API key and archiving with manual signing.

### App Store submission readiness (Apple rules)
| Rule | Need | State |
|---|---|---|
| 4.8 Sign in with Apple | offered next to Google | code started; Firebase Apple provider not enabled (owner) |
| 5.1.1(v) account deletion | in-app, tokens revoked | `deleteOwnAccount` exists; iOS UI + revoke pending |
| 1.2 user-generated content | report, block, 24 h response | **missing everywhere** (new callable, rules, UI, admin queue) |
| 3.1.1 In-App Purchase | all Premium via StoreKit | not started (needs Paid Apps Agreement, products) |
| Privacy | manifest, nutrition labels, ATT not needed | manifest stub; labels drafted at M6 |
| Metadata | support/privacy URLs, screenshots, demo accounts, age rating | site down until the lift; function URLs usable meanwhile |

## 2. "Exactly the same front end": how parity is achieved and proven
What "exact" means here: same layouts, colors, copy, icons, typography, spacing, animations, states and flows, screen by screen.
Differences that stay (system-owned, cannot be restyled): permission dialogs, Sign in with Apple sheet, StoreKit purchase sheet,
photo picker, keyboard, status bar and home indicator, swipe-back gesture, text rendering. Decisions to make it as identical as possible:
1. **Material look on iOS**: bundle the Material Icons used by the app (SVG -> asset catalog, Apache 2.0) instead of SF Symbols,
   bundle Roboto for body text (the Android default font) next to Manrope headings, and rebuild M3 pieces (filled/tonal buttons,
   chips, switches, text fields, date and time pickers, snackbars, bottom sheets with the same detents, floating bottom pill) in SwiftUI.
2. **Google Maps SDK for iOS** with the same custom marker bitmaps and cluster logic as `LebanonMapCanvas.kt`.
3. **Port screen by screen from the Compose source**, keeping the same file names (`DiscoveryScreen.kt` -> `DiscoveryScreen.swift`),
   the same state holders and the same string keys, so every later Android change has an obvious iOS counterpart.
4. **Proof**: reuse the Android "Design Screenshots" harness (`DesignScreenshotTest`: OVERLAYS and TAPS, states light, dark, small phone,
   RTL, Arabic) as the spec; build an XCUITest harness that drives the same states with fixture data, upload PNGs as CI artifacts,
   and I generate side-by-side contact sheets (Android | iOS) per screen and fix differences until a written parity checklist
   per screen is ticked. You review the sheets before each TestFlight.

### Screen port list (Android source -> iOS), non-admin only
| Order | Screens (lines) | Notes |
|---|---|---|
| P1 | Splash (73), LoginAuthScreen + AuthSteps (1.1k), KycScreen/RequirementsSheet (380), consent/permission dialogs | sign-in, registration, WhatsApp number, Apple/Google/email |
| P2 | DiscoveryScreen (904) + LebanonMapCanvas (833) + filters | Explore list/map, load-more, search |
| P3 | SpaceDetailsScreen (1.1k), RoomCards (599), AvailabilitySheet (686), RentalBookingDialog (1.6k), calendar view (335) | details, rooms, availability, request |
| P4 | MyFavorites (181), MyBookings (901), Notifications, SpecialistProfile (1.1k) | saved, rentals, profile, language, legal |
| P5 | OwnerHub (1.0k), CreateListingDialog (1.5k), SubdivisionForm (934), RentalStrategyEditors (491), ManageListing (640), ChangePriceSheet (454), OwnerIncomingRequests (510), OwnerRentalRequests (113), OwnerRentingProgress (513), OwnerAnalytics (703) | Pro Host side |
| P6 | OwnerSubscriptionsScreen (1.2k), PromoCountdownCard, BillingController | Premium with StoreKit |
Data layer in parallel: models, FirestoreService, repositories, shared util ports with test fixtures.

## 3. Work I do after approval (in order)
1. Write `docs/IOS_READINESS.md` (this audit) and `docs/OWNER_GUIDE.md` (section 4 below, final wording), update OWNER_TODO/OPS_LOG, commit, push.
2. Entitlement spike (workflow step printing the IPA entitlements; manual-signing fallback).
3. Foundations still missing: Material icon assets, Roboto, remaining components, Google Maps SDK dependency (once you send the iOS key), Crashlytics, analytics consent.
4. P1 sign-in and registration with screenshot harness; TestFlight.
5. Data layer + P2..P6 in the order above, each ending with side-by-side sheets and a TestFlight build.
6. M1 backend (Apple purchases, notifications, push, report/block, AASA) in parallel; Android gets the additive report/block UI.
7. Store listing assets and App Review package (M7).

