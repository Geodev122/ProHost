# iOS plan for ProHost (approved 7 Nov 2026; supersedes the 5 Nov outline)

**Status:** no iOS code yet. Decisions: native SwiftUI, no Mac (GitHub macOS runners + TestFlight on the owner's iPhone),
Individual Apple Developer account, v1 = full parity (Admin console stays Android/web). Known (non-secret): Apple Team ID `884APQLMP4`, seller = Georges El Najjar (Individual, 2FA on), Apple ID for the account psy.go.elnajjar@gmail.com (owner to confirm spelling), **no iPhone and no Mac available** (see section A item 3 / risk on device testing). M0 skeleton is in `ios/` (splash only; CI `ios-ci.yml`, manual `ios-testflight.yml`; direct `xcodebuild archive/-exportArchive` with the API key instead of fastlane). Apple IDs (not secret): Team `884APQLMP4`, ASC Key ID `HF7C34HW38`, Issuer `1d848c92-ff55-45cd-9071-e70a7837ee5d`; the .p8 goes into GitHub secret `ASC_KEY_P8` as raw PEM text. Firebase iOS app registered (App ID `1:646730915838:ios:b816bc76af193424c8eaaf`, bundle `app.geonajjar.prohost`); `GoogleService-Info.plist` still to be uploaded by the owner (not a secret). TestFlight upload runs from the Actions tab or by pushing a branch `ios-beta/N`. Borrowed iPhone planned for TestFlight tests. Correct Apple ID email is psy.geo.elnajjar@gmail.com. Earlier note:
owner sends section A items 1-5 and 10. Backend/web work that does not need Apple keys can start earlier.
Update this file as milestones finish. Never put `.p8` keys, passwords or tokens in the repo, Drive or chat.

## Context
Owner wants to use the wait for Google's restriction to lift (everything else stays as is) to build the iOS app.
Decisions taken (7 Nov 2026): **native SwiftUI** on the existing backend; **no Mac, iPhone only** (builds and tests run on
GitHub macOS runners, owner tests through TestFlight); **Individual** Apple Developer account; **v1 = full parity**
(specialist + Pro Host + Premium; Admin console stays Android/web). Starting point: no iOS code (`docs/IOS_PLAN.md` of
5 Nov holds the earlier outline, to be superseded by this plan). Android reference: 147 Kotlin files / ~49k lines,
18 screens + sheets, 28 Firestore model classes, 1,225 strings EN/AR (10 plurals), 22 callables, FCM, Billing 9.1.
Nothing in the Android app, functions, rules or site is changed by this plan except where stated in M1 (additive).

Hard limits: the sandbox is Linux without Xcode. Swift is written and reviewed here, but only compiled/tested on a macOS
runner, so each feedback loop is a CI run (about 10-20 min). Plan for many small CI iterations. Apple review is the
slow external step (typically 1-3 days per submission, more for the first one).

---------------------------------------------------------------------------------------------------------------------
## A. What I need from you (with how and where)  - nothing here is secret unless marked
Do these in this order; I start M0/M1 as soon as items 1-4 are answered, the rest are needed by the milestone named.

| # | You give me | How and where to get it | Needed by |
|---|---|---|---|
| 1 | **Team ID** (10 characters, not secret) | developer.apple.com/account > Membership details > Team ID | M0 |
| 2 | **Bundle ID** confirmation: `app.geonajjar.prohost` (same as Android) | Just confirm; it can never be changed after the app record exists | M0 |
| 3 | **iPhone model + iOS version**, and the **Apple ID email** that will test | Settings > General > About on the iPhone; use the Apple ID signed into it | M0 |
| 4 | Enrollment status: Individual account **active**, two-factor on, legal name as you want it shown as seller | developer.apple.com/account; the seller name = your legal name for Individual | M0 |
| 5 | **App Store Connect API key** (CI uploads builds) - gives me Issuer ID, Key ID (not secret) and the `.p8` (SECRET) | appstoreconnect.apple.com > Users and Access > Integrations > App Store Connect API > "+" > role **App Manager**. Download the `.p8` once. Put in GitHub secrets (see section F), never in chat | M0 |
| 6 | **App ID** with capabilities: Sign in with Apple, Push Notifications, Associated Domains, In-App Purchase, App Attest | developer.apple.com > Certificates, Identifiers & Profiles > Identifiers > "+" > App IDs > App > bundle ID above > tick the five capabilities. (I can also create it through the API key in item 5 if you prefer; say so) | M1 |
| 7 | **Four keys (.p8, SECRET)** + Key IDs: (a) APNs Auth Key, (b) Sign in with Apple key, (c) In-App Purchase key (App Store Server API), (d) the ASC key from item 5 | (a)(b): developer.apple.com > Keys > "+" (tick Apple Push Notifications service / Sign in with Apple, choose the App ID). (c): App Store Connect > Users and Access > Integrations > In-App Purchase > "+". Download each `.p8` once; store as GitHub secrets / Secret Manager | M1 |
| 8 | **Firebase iOS app**: add iOS app in Firebase console (project prohost-f766f), bundle ID above, download `GoogleService-Info.plist`; Authentication > Sign-in method > enable **Apple** (paste Team ID, Key ID, key from 7b); Project settings > Cloud Messaging > upload APNs key (7a); App Check > register **App Attest**. Send me the plist (it is a config file like `google-services.json`, not a secret) | console.firebase.google.com > ProHost > Project settings > Your apps > Add app > iOS | M2 |
| 9 | **Paid Apps Agreement**, bank account and tax forms, **EU trader status** (Individual traders publish name, address, phone/email on the EU product page) | App Store Connect > Business > Agreements; Tax and Banking; Trader status. Also join the **App Store Small Business Program** (15% commission under $1M/yr): developer.apple.com/app-store/small-business-program | M5 |
| 10 | **App record** in App Store Connect (name "ProHost" if free, SKU `prohost-ios`, primary language English (U.K.), category Business) | App Store Connect > Apps > "+" > New App. I give you the exact field values at M0 | M0 |
| 11 | **Subscription group "ProHost Premium"** with 2 products (ids I will give: `prohost.premium.monthly`, `prohost.premium.yearly`), same prices as Play ($8.99 / $79.99), intro offers (monthly: 1 month free; yearly: 50% off the first year, pay-up-front), localizations EN + AR, review screenshot. Plus an **offer code** campaign for the PROHOST2026 equivalent | App Store Connect > your app > Monetization > Subscriptions. I give a click-by-click sheet in M5 | M5 |
| 12 | **Decisions** (answer in chat): (a) a user who already has Premium through Google Play opens the iOS app: show "Active via Google Play, manage there" (recommended) or allow a second purchase; (b) report/block moderation: you or an admin answers reports within 24 h (Apple 1.2); (c) iOS prices equal to Play (recommended) | Chat | M5 / M6 |
| 13 | **Support and policy URLs**: support email, privacy `https://pro-host.tech/privacy`, terms, marketing URL (need the site served again: after the Google lift or on the Netlify temporary host). Interim: the `legalDocumentPage` function URLs work for App Store Connect (Apple does not verify domains) | Already in the repo | M7 |
| 14 | **Review demo accounts**: one specialist and one Pro Host account (email + password) with a sample listing, written into the App Review notes only (never in the repo) | Create in the production app yourself; or tell me and I prepare a clearly labelled test listing through the admin tools | M7 |
| 15 | **App Store text/legal**: EN + AR description, subtitle (30 chars), keywords (100 chars), promo text. I draft all of it; you approve | Chat | M7 |

---------------------------------------------------------------------------------------------------------------------
## B. Architecture (what gets built)

### B1. Repository layout (all new, under `ios/`; nothing else touched)
```
ios/project.yml                      XcodeGen spec (no .xcodeproj in git); targets ProHost, ProHostTests, ProHostUITests
ios/ProHost/App/                     ProHostApp.swift, AppDelegate (APNs, Firebase), RootView, Router (deep links)
ios/ProHost/DesignSystem/            Tokens (Colors, Radius, Spacing, Elevation, Typography), ProCard, ProPrimaryButton,
                                     InputField, AlertBanner, Badges, ProAvatar, SnackbarHost, PermissionPrompt, Shimmer
ios/ProHost/Core/Models/             Codable ports of DataModels.kt (28 types) + legacy pricing adapter
ios/ProHost/Core/Data/               FirestoreService, ProHostRepository (+ Bookings/Profiles areas), Storage uploads
ios/ProHost/Core/Functions/          FunctionsClient (callables, error -> user message mapping)
ios/ProHost/Core/Auth/               AuthService (email, Google, Apple), AuthError mapping, PasswordRules, GoogleNonce
ios/ProHost/Core/Billing/            StoreKit 2 manager, Offer summary (SDK text only), entitlement observer
ios/ProHost/Core/Push/               UNUserNotificationCenter, token registration, notification center mapping
ios/ProHost/Core/Util/               SpaceCalculationUtils, AttendeePricing, PriceChange, BookingTerm, ListingLocation, PublicCode
ios/ProHost/Features/...             Auth, Explore (list+map), ListingDetails, Saved, MyRentals, Notifications, Profile,
                                     Premium, Kyc/Requirements, OwnerHub (Listings, Wizard, Manage, ChangePrice, Requests,
                                     RentingProgress, Analytics)
ios/ProHost/Resources/               Localizable.xcstrings (en, ar), Assets.xcassets, Fonts (Manrope), PrivacyInfo.xcprivacy
ios/fastlane/                        Fastfile (build, test, upload to TestFlight, metadata, screenshots)
scripts/ios/                         strings_convert.py, check_strings_parity.py, export_fixtures.ts, render_screenshots.sh
.github/workflows/ios-ci.yml         build + test on every push touching ios/ ; ios-testflight.yml (manual)
```
Dependencies (Swift Package Manager): `firebase-ios-sdk` (Auth, Firestore, Functions, Messaging, Storage, Crashlytics,
AppCheck, **FirebaseAnalyticsWithoutAdIdSupport** so no IDFA and no App Tracking prompt), `GoogleSignIn-iOS`, `Nuke`
(image cache). Maps: **MapKit** (no API key, no Google dependency). Min iOS 17 (async/await, `@Observable`, StoreKit 2).

### B2. Key design decisions
1. **Backend is the source of truth, client never grants entitlement** (same rule as Android): purchase -> signed
   transaction (JWS) sent to a callable -> server verifies with Apple -> `EntitlementManager` -> profile mirror. The
   client finishes the StoreKit transaction only after the server confirms.
2. **Prices and offer text come only from StoreKit** (`Product.displayPrice`, `subscription.introductoryOffer`), never
   hard-coded, mirroring `PlayOfferText`. Plan cards, "HOW YOUR OFFER WORKS", terms footer ported 1:1 in wording.
3. **Mirrored logic gets shared test vectors**: add `scripts/ios/export_fixtures.ts` to dump the existing TS unit-test
   cases (attendee pricing, price change, booking terms, lowestPriceFor, listing location, legacy pricing) to JSON that
   XCTest also runs, so Swift ports cannot drift from `functions/src` and Android.
4. **Strings**: one-time converter `scripts/ios/strings_convert.py` turns `res/values{,-ar}/strings.xml` + `plurals.xml`
   into `Localizable.xcstrings` (placeholders `%1$s` -> `%1$@`, ` ` fixed, Arabic plural categories zero/one/two/few/
   many/other). `check_strings_parity.py` runs in CI: every key exists in en and ar. RTL is automatic; phone numbers LTR.
5. **Carry over the CLAUDE.md rules** that apply: email + password / Google / Apple only (no SMS), enumeration-safe
   auth errors, no raw Firebase messages, Google sign-in nonce checked locally, email verification never blocks,
   profile completeness rule, display codes (U-/L-/D-/B-) not UIDs, no AI-presenter media, analytics opt-in, typed
   events only, Crashlytics without user id, server-owned fields never echoed on listing saves, no `set(merge)` of counters.
6. **Photos/permissions**: `PhotosPicker` (no permission), camera only via system camera with usage string, location
   When-In-Use for "Locate me"/pin picker, notifications asked after the analytics-consent choice (same order as Android).
7. **Maps**: MapKit annotations with clustering and the same "Search this area" pill behavior.
8. **Payment reminders**: EventKitUI `EKEventEditViewController` with monthly recurrence (no calendar permission).

---------------------------------------------------------------------------------------------------------------------
## C. Milestones (each ends with something you can verify)

### M0 - Foundations (no Apple keys beyond item 5)
- Create `ios/` skeleton, `project.yml`, SPM deps, `ios-ci.yml` (macOS runner, XcodeGen, `xcodebuild build` + unit tests
  on an iPhone simulator), fastlane lane for TestFlight upload using the ASC API key.
- Signing: **automatic signing through the ASC API key** (`xcodebuild -allowProvisioningUpdates` with the key) so no
  certificate files or match repo are needed.
- App icon from the existing logo (1024 px, no alpha), launch screen, Info.plist keys (usage strings, URL scheme `prohost`,
  `ITSAppUsesNonExemptEncryption=false`), `PrivacyInfo.xcprivacy` manifest.
- **Done when**: CI is green and a build with a "ProHost" splash screen appears in TestFlight on your iPhone.
- Cost note: private-repo macOS minutes count 10x against the free allowance; budget about 150-300 macOS minutes a
  month for CI. Alternative: Xcode Cloud (25 compute hours/month are included with the Developer Program); I will try
  it only if its workflows can be created without a Mac.

### M1 - Backend for iOS (additive, behind new function names; Android unaffected)
Files (functions/src): 
- `billing/appleSubscription.ts`: verify transactions with Apple's `@apple/app-store-server-library` (JWS signature
  chain, bundle ID, environment sandbox/production).
- `billing/verifyAppleTransaction.ts` (callable, `onCall` from `lib/callable.ts`): receives the signed transaction,
  records `subscriptions/{sha256(originalTransactionId)}` with `platform: "ios"`, calls the existing
  `SubscriptionService`/`EntitlementManager`; make `subscriptionService.ts` (l.55) and `entitlementManager.ts` (l.82)
  platform-aware (`subscriptionPlatform: "ios"`). Link by `appAccountToken` = a UUID derived from the uid (the iOS
  analogue of `obfuscatedExternalAccountId`). Rule 12(a) for users already premium via Play.
- `billing/appStoreNotifications.ts` (HTTPS, App Store Server Notifications V2): SUBSCRIBED, DID_RENEW,
  DID_CHANGE_RENEWAL_STATUS, DID_FAIL_TO_RENEW (grace/billing retry), EXPIRED, GRACE_PERIOD_EXPIRED, REFUND, REVOKE,
  OFFER_REDEEMED -> same status mapping table as Play (ACTIVE/GRACE/CANCELED-until-expiry => PRO_HOST; ON_HOLD, EXPIRED,
  REVOKED, REFUNDED => SPECIALIST). Daily sync job also re-reads Apple (`getAllSubscriptionStatuses`).
  Run as `runAs("billing")`; grant `run-billing` accessor on the new secrets; add the two functions to `identityGuard.test.ts`
  only if they reach Google Play (they do not).
- Secrets via `defineSecret`-free path like GA4 (`app_config/apple`, server-only, rules + rules test) or Secret Manager:
  issuer ID, key ID, `.p8`, appAppleId. Never in code or chat.
- `roles/assignInitialRole.ts`: already enforces Play Integrity only when a token is present, so iOS works; add an optional
  `platform: "ios"` field for logging and require a valid **App Check (App Attest)** token for iOS callers (log-only
  while `ENFORCE_APP_CHECK=false`).
- `lib/push.ts`: add an `apns` block (alert/sound/badge, `thread-id`, `mutable-content`) next to `android: {priority}`;
  keep `notificationId` and the routing keys identical so "Open" routes the same.
- Account deletion on iOS: `deleteOwnAccount` stays; client first calls Firebase `Auth.revokeToken(withAuthorizationCode:)`
  for Apple users (Apple requirement 5.1.1(v)).
- **Report/block (Apple 1.2)**: the quick scan found no report or block feature in the app, rules or functions. Add (on
  both platforms) `reportContent` callable + `content_reports` collection (server-only, rules + rules test), "Report
  listing" and "Block user" actions, admin queue; this is an additive Android change shipped in the next Android release.
- Website (served again after the lift / on the Netlify temporary host): `public/.well-known/apple-app-site-association` (paths
  `/listing/*`, `/redeem`, `/get`) with `Content-Type: application/json` in `firebase.json` and `public/_headers`;
  `/get` smart link sends iOS to the App Store. Until then deep links use `prohost://` (already used by emails/pushes).
- Tests: Jest unit tests for Apple status mapping and the callable (fixtures of signed test transactions from Apple's
  sandbox), emulator flow in `tests/e2e/flows.test.js`, rules tests for new collections.
- **Done when**: `npm test` green in `functions/`; Apple's "Request test notification" returns 200 from the sandbox.

### M2 - Auth, shell, design system
- Design tokens ported from `Color.kt`/`Theme.kt` (Steel Blue `#2B5A8C`, dark `#93B8E0`, orange primary, radii, spacing,
  12 pt text floor, Manrope headings, Dynamic Type capped near Android's 1.15 cap), light/dark/RTL.
- Staged sign-in exactly like Android: Welcome -> providers (Google, **Apple**, email) -> email -> password; 3-step
  registration (profile, place, WhatsApp + terms); credential autofill (iOS Passwords). `AuthError` mapping, enumeration
  safe, forgot password via `sendPasswordResetLink`, `isProfileComplete` gate, email-verification banner.
- Floating bottom bar with role tabs (Specialist: Explore, Saved, My Rentals, Profile; Pro Host: Listings, Explore,
  Rentals, Profile), Profile > More (legal, contact support, language, sign out).
- Language: Profile > More > Language uses the iOS per-app language; en/ar.
- **Done when**: you can create an account, sign in with email, Google and Apple, see the shell in English and Arabic RTL,
  sign out, delete the account (Apple tokens revoked).

### M3 - Specialist core (Explore -> request -> My Rentals)
- Public listings paged 100 at a time with "load more" and auto-search-more (cap 1,000), filters (country, pricing formula
  incl. per-attendee), search draft applied on submit, list <-> MapKit map, "Search this area".
- Listing details with folder-tab rooms, `selectRoom()` single source of truth, availability sheet (half/full detents),
  per-attendee pricing, `booking_occupancy` merge, request flow + requirements sheet (photo, WhatsApp number, address),
  "Request sent" sheet (View request, Message host on WhatsApp), Ask-the-host WhatsApp button.
- Saved (heart = arrayUnion/arrayRemove, cap 300), My Rentals (live, edit = "Change request", withdraw, cancel),
  notification center (live listener, 48 h, read marks, Open routes), push registration and deep links.
- **Done when**: end-to-end booking request against the production backend from TestFlight, host receives the push.

### M4 - Pro Host (parity)
- Owner hub, My Listings, the unified listing wizard (create/edit/draft, divisions, per-attendee tiers, photo upload with
  PhotosPicker, pin picker with MapKit + reverse geocoding for city/country), Manage page (date-aware occupancy), request
  inbox (accept/reject with conflict guard), Change price sheet, Renting Progress, Owner Analytics (saves/inquiries).
- Server-owned fields never echoed (`keepingServerOwnedFields` port), verification request flow (private gs:// doc).
- **Done when**: create + publish a listing on iOS (needs an active Premium or admin grant), accept a request, change a price.

### M5 - Premium (StoreKit 2) - needs items 9, 11, 12
- Paywall with two plan tiles (yearly selected), offer summary text from StoreKit, explainer ("HOW YOUR OFFER WORKS"),
  terms footer, Restore (`AppStore.sync()`), Manage (`AppStore.showManageSubscriptions`), offer-code redeem sheet
  (`presentOfferCodeRedeemSheet`), status banners (grace, billing retry, expired, pending/Ask to Buy), "active via Google
  Play" state per decision 12(a). Subscription group levels: yearly above monthly (immediate upgrade, prorated).
- Checkout runs `verifyAppleTransaction`; the "Activating your subscription" banner mirrors Android.
- Apple review needs: price, period, renewal and cancellation text on the paywall, links to Terms and Privacy.
- **Done when**: sandbox purchase of monthly and yearly from TestFlight flips the account to Pro Host, renewal,
  cancel and refund notifications change the status, and the admin push fires.

### M6 - Quality, accessibility, localization, compliance
- Full XCUITest screenshot harness (like `DesignScreenshotTest`): light, dark, small phone, large text, RTL/Arabic,
  every sheet; artifacts from CI reviewed by me and you.
- VoiceOver labels, 44 pt targets, contrast, Reduce Motion, Dynamic Type; Arabic QA against the Android copy.
- Privacy: `PrivacyInfo.xcprivacy` (required-reason APIs), App Privacy answers (email, phone, photos, location coarse/
  fine, user content, purchases, diagnostics; no tracking), analytics opt-in dialog before any collection.
- Security: App Attest, no secrets in the app, ATS defaults, keychain for nothing sensitive (Firebase handles tokens).
- Crash-free soak on your iPhone for several days; Crashlytics dSYM upload in CI.

### M7 - TestFlight beta and App Store submission
- Internal TestFlight (you + testers added as ASC users), then external beta (needs Beta App Review) with 5-10 trusted
  hosts/specialists.
- Store listing: EN + AR text (I draft), 6.9" and 6.5" iPhone screenshots from the harness, icon, age rating
  questionnaire (user-generated content => 12+/17+ per Apple's questions), App Review notes with demo accounts, Sign in
  with Apple offered, deletion, report/block, subscription disclosures, export compliance (HTTPS only).
- Submit; handle review feedback loop; staged "manually release" first, then phased release.
- **Done when**: the app is Ready for Sale and `/get` sends iOS visitors to the App Store.

### M8 - After launch
- Bump docs: `docs/IOS_PLAN.md` (rewrite as state + runbook), `docs/OWNER_TODO.md` (replace items 21-25),
  `docs/OPS_LOG.md`, `docs/MONETIZATION.md` (Apple section), `docs/DATA_MODEL.md` (`platform` fields), CLAUDE.md iOS notes.
- Release routine: version bump, `ios-testflight.yml`, release notes EN/AR, NIGHTHAWK extended with iOS checks.

---------------------------------------------------------------------------------------------------------------------
## D. Order and dependencies
M0 -> M1 (backend, parallel with M2 UI) -> M2 -> M3 -> M4 -> M5 (needs Paid Apps Agreement + products) -> M6 -> M7.
Blocked only by the Google case: the Universal Links file and anything served from `pro-host.tech` (App Store URLs can
use the function URLs meanwhile). The Apple/Firebase side does not depend on the restriction. Rough size: M0-M1 small,
M2-M3 large, M4 large, M5 medium, M6-M7 medium (+ Apple review waits). Full parity is the biggest scope option; I will
ship TestFlight builds at the end of M0, M2, M3, M4 and M5 so you see progress every step.

## E. Risks and mitigations
- **No local compile**: every Swift change is validated only by CI. Mitigate with small PRs, strict SwiftLint/swift-format,
  unit tests for all pure logic, and a simulator smoke test on every push.
- **App Review rejections** (first submission): pre-empt with demo accounts, report/block, Sign in with Apple, deletion,
  full subscription disclosures, no hidden Android/Google-Play wording or links to external payment.
- **Cross-platform subscriptions**: one entitlement source of truth (server mirror); decision 12(a) prevents double charging.
- **Google Auth Platform branding** (pending) also governs the iOS Google sign-in client: keep it verified.
- **Costs**: macOS CI minutes; Apple 15%/30% commission; Apple Developer fee already paid.
- **Policy**: no synthetic-person media in App Store assets; screenshots rendered from the real app only.

## F. Secrets and where they live (never in chat or the repo)
GitHub repo secrets (Settings > Secrets and variables > Actions): `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_B64`
(base64 of the .p8), `APPLE_TEAM_ID`. Backend: `app_config/apple` (server-only doc) or Secret Manager for the In-App
Purchase key (issuer, key id, `.p8`, appAppleId) read by `run-billing`. Firebase console only: APNs key and the Sign in
with Apple key (uploaded by you). I never see the values; I only reference the secret names.

## G. Verification (end to end)
1. `cd functions && npm test` (Apple status mapping, callable, rules) and the emulator flow `tests/e2e/flows.test.js`.
2. `ios-ci.yml`: XcodeGen -> build -> XCTest (shared fixtures) -> XCUITest screenshots -> artifacts.
3. TestFlight on your iPhone: sign-in (3 providers), booking request, push, sandbox purchase/renewal/refund, Arabic RTL.
4. App Store Connect: App Review checklist items green (1.2, 3.1.1, 4.8, 5.1.1(v)), then submit.

## H. First actions after approval (this plan stays read-only until you say go)
1. You answer section A items 1-5 and 10. 2. I create the `ios/` skeleton + CI (M0) and the M1 backend branch.
3. I update `docs/IOS_PLAN.md`, `docs/OWNER_TODO.md` and `docs/OPS_LOG.md`, commit on a branch, no deploy of functions
   until you approve (functions deploy goes through CI on merge to `main`).

> Note 7 Nov 2026: in firebase-ios-sdk 12.x the SPM product `FirebaseAnalytics` is the one without IDFA support (IDFA is the separate `FirebaseAnalyticsIdentitySupport`, not linked); collection stays off until the consent dialog (Info.plist `FIREBASE_ANALYTICS_COLLECTION_ENABLED=false`).

## State snapshot 7 Nov 2026 (19:00 UTC, from `ios-inspect.yml`, read-only)
- App Store Connect app id `6831983164` (ProHost, SKU prohost-ios). Bundle ID resource `88L988UK3A` (UNIVERSAL) with capabilities APPLE_ID_AUTH, APP_ATTEST, IN_APP_PURCHASE, PUSH_NOTIFICATIONS. **Missing: ASSOCIATED_DOMAINS** (needed for Universal Links; enable with the Admin key when the AASA file is ready).
- TestFlight build 1.0.0 (build 9) uploaded and `processing=VALID`. No devices, no profiles; one DEVELOPMENT certificate created by Xcode cloud signing.
- Built so far (CI green, 24 unit tests): `ios/` skeleton, string catalog from Android (1,235 keys, en+ar, parity checked in CI), design tokens and base components (Manrope registered), Firebase SPM (Auth, Firestore, Functions, Messaging, Storage, AppCheck), `ProHostAppDelegate` (App Check: debug provider in debug, App Attest in release), `FunctionsClient`, `AuthService` (email + Apple), ports of PasswordRules, AuthError, AuthNonce, profile completeness.
- Next: staged sign-in UI, Google sign-in (needs GoogleSignIn-iOS nonce API check), entitlements spike (does unsigned-archive + cloud-signed export keep Sign in with Apple / Push entitlements? print `codesign -d --entitlements` from the exported IPA), role-tab shell.

## Decisions 7 Nov 2026 (night) - override earlier notes
- Maps: **Google Maps SDK for iOS** (not MapKit) so the map, markers and clusters match Android; needs an iOS-restricted key (owner guide A2).
- Admin Console is **not** built on iOS (Android/web only).
- "Exactly the same front end": Material Icons + Roboto bundled, M3 components rebuilt in SwiftUI, screens ported from the Compose source with the same names and string keys, parity proven with side-by-side Android | iOS screenshot sheets. Audit and port order: `docs/IOS_READINESS.md`.
