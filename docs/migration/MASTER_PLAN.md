# ProHost → one cross-platform app (KMP + Compose Multiplatform), Admin on the web, AI agent as its own project

Decided by the owner 8 Nov 2026. Supersedes the native-SwiftUI decision in `docs/IOS_PLAN.md` / `docs/IOS_READINESS.md` (their Apple account, backend and App Review parts stay valid).

## 1. Context

ProHost is a working, Play-approved Android app (147 Kotlin files, 49.5k lines; UI = Jetpack Compose + Material 3). iOS today is a 2.5k-line SwiftUI splash skeleton. The owner wants **one UI codebase for Android and iOS that looks and behaves exactly like today's Android app**, the **Admin Console moved to a web app** (complete, nothing missing, out of both mobile builds), and the **AI lead/marketing agent moved to its own project** that stays linked to ProHost and can later serve other apps.

**Non-negotiable owner rules (from the request):**
1. No change to UI, UX, layouts, behaviour or experience. Anything that would change them is *listed and asked*, never slipped in.
2. For every component/screen/phase: **learn → plan → execute → compare → confirm identical → only then move on.**
3. Every phase ends with an audit of **database ↔ backend ↔ frontend ↔ admin console (web)** integration.
4. The AI agent is being moved out: keep it linked and operating on this app, designed to serve many apps.
5. Admin Console: web app, "as it is", never miss a screen or function; kept away from Android and iOS builds.

**Decisions locked (owner answers 8 Nov):**
- Admin web = **Compose for Web reusing the Kotlin admin code** (React is the fallback only if the P1.0 spike fails its gate).
- Agent = **new repo + new Google project**, linked to ProHost through a versioned **adapter contract**; decoupled inside this repo first, moved after.
- Release order = **Android keeps shipping from the shared code at every phase; iOS goes to TestFlight once core screens work.**

**Withdrawn from my earlier recommendation** (it would change UX): moving Pro Host drawer items into tabs, dropping Explore's ☰, iOS-style back chevrons/ripples. The drawer, tabs, bottom pill and every layout stay as they are on both platforms. Only inevitable platform-owned things differ (system permission dialogs, purchase sheets, Sign in with Apple button, keyboard, status bar) and are listed in `docs/migration/PARITY.md`.

## 2. Facts that shape the plan (from the exploration)

- In-scope app code (non-admin) = 130 files / 42,169 lines. Class split by lines: **A1 pure Compose 16%** (47 files), **A2 pure but uses JVM `java.*`/`Calendar`/`String.format` 33%** (25 files), **B shareable Compose with an Android seam 41%** (38 files), **C Android-only 10%** (20 files). Admin = 17 files / 7,333 lines.
- The hidden cost is not Android classes. It is: `Context` threaded through `ProHostViewModel` (23 methods), `UiMessages.show(context…)` (84 call sites), ~380 `uiText/uiPlural` calls, and JVM-only APIs (≈159 `Calendar`, 42 `SimpleDateFormat`, 30 `UUID`, `Locale`, `NumberFormat`, `MessageDigest`, `Base64`).
- Navigation is a **hand-rolled state machine** (`ui/navigation/ProHostNavGraph.kt`, 1,020 lines). Navigation-Compose, Room, Moshi, Retrofit, OkHttp, CameraX, DataStore, kotlinx-serialization are declared but **unused** → remove.
- Firebase is already behind seams in places (`AuthGateway`, `SessionFinisher`, `AnalyticsSink`, `FirestoreService.localOnly()`, `FakeFunctionsClient`). Models are hand-written `Map<String,Any?>` mappers (`fromFirestoreMap`), not reflection → portable.
- Admin = **70 parity items** (7 tabs, dossier sheet, 34 callables incl. ~25 admin ones, direct Firestore/Storage writes for schema/legal/user-edit/listing-edit/delete, audit log, mailing console `public/admin.html`, IG control room, CI maintenance tasks). Detail table is in the exploration output and will be saved as `docs/migration/ADMIN_PARITY.md` in Phase 0.
- Agent = `functions/src/instagram/**` (61 files, 5.1k lines, 121 tests) + website lead/mailing functions + `tools/control-room` + `marketing/` (286 MB) + `public/social`. It reads ProHost data with the Admin SDK in three places (`aiListings.ts`, `aiAccountInsight.ts`, `aiOffers.ts`) plus `officeLeads.ts` imports the agent back (`dmOwner`). Brand abstraction already exists (`brands.ts`, `store.ts`, `config.ts`, `routing.ts`).
- Stale facts to correct in docs: wrapper/CI Gradle is **9.7.1** (CLAUDE.md says 9.6.0); `docs/INSTAGRAM.md` still mentions Gemini/Vertex; Maintenance workflow lists `aiprobe`/`aiquota` with no handler.
- Things I could not verify offline and must prove in the Phase 0 spike: CMP version that pairs with Kotlin 2.2.10 / AGP 9.4.1; Material 3 API drift vs Compose BOM 2025.02; Arabic plurals + ` ` handling in `composeResources`; Firebase iOS via Swift bridges; Google Maps iOS inside `UIKitView` with overlay controls; font-scale cap inside dialog windows; Compose-for-Web + Firebase JS reach.

## 3. Target architecture

```
settings.gradle.kts
shared/            KMP library (commonMain + androidMain + iosMain). Theme, components, screens, ViewModels,
                   models, pricing/booking rules, repositories, composeResources (strings en/ar, plurals, fonts, drawables, WAVs)
androidApp/        com.android.application only: MainActivity, ProHostApplication, manifest, xml/, mipmaps, signing,
                   Crashlytics/google-services, R8, baseline profile, Roborazzi design harness, android actuals
iosApp/            Xcode host (XcodeGen), SwiftUI shell that hosts ComposeUIViewController + Swift actuals/bridges
                   (Firebase, Sign in with Apple, Google Sign-In, StoreKit 2, Maps, push) ; reuses existing signing/TestFlight CI
adminWeb/          Compose for Web (wasmJs) admin console; own Gradle module, NOT a dependency of androidApp/iosApp
functions/ rules/ public/   unchanged except additive items listed in §8
(separate repo)    ai-agent project (see §9)
```

Seams (interfaces in `commonMain`, one actual per platform — full list of 27 in the exploration): StringProvider, Messenger(`UiMessages`), PlatformActions (links/mailto/WhatsApp/share/calendar/settings), Clipboard, MediaPicker/FileExport/TakePhoto (`Uri` → `PickedFile`), ImageCodec, Permissions, LocationProvider+Geocoder, MapView, GoogleSignIn/AppleSignIn, PhoneHint (hidden on iOS), AppAttestation, Auth/Functions/Firestore/Storage gateways, SubscriptionStore (Play / StoreKit 2), PushRegistrar, DeepLinkRouter, Haptics, SoundPlayer, LocaleInfo, SystemUi, KeyValueStore, Logger, Crash/Analytics, LegalPdf+HtmlView, InAppUpdate (no-op iOS), Autofill, time/number/UUID (kotlinx-datetime, kotlin.uuid, expect formatters).

Firebase on iOS: **gateway interfaces with native implementations** (Android keeps today's `FirestoreService`/`FirebaseFunctionsClient`; iOS reuses the already-written Swift `AuthService`, `FunctionsClient`, App Check delegate). Keeps `fromFirestoreMap` and every payload byte-identical. GitLive is a possible swap-in per gateway later, not the base.

## 4. The parity protocol (applied to every unit)

A **unit** = one screen/sheet/dialog/component group/logic file. Tracked in `docs/migration/PARITY.md` (one row per unit: source file, class, seam, golden ids, status, date, reviewer).

1. **Learn** – read the Android source; list every state, string key, branch, callable/collection used.
2. **Plan** – a 5-line unit card in PARITY.md (what moves, which seam, which goldens/tests prove it).
3. **Execute** – smallest possible change; Android first.
4. **Compare** – (a) unit tests identical/green; (b) **design-screenshot diff**: the existing `DesignScreenshotTest` matrix (14 screens + 42 overlays × light/dark/small/rtl/ar = 280 renders) is captured **once from the untouched app at tag `kmp-golden-v1047`** and diffed after each unit with a pixel tool (threshold set in P0); (c) string-key/plural count unchanged; (d) behaviour tests for flows with no golden; (e) on iOS, Android | iOS contact sheets from the macOS CI job.
5. **Confirm** – all checks green + unit reviewed in the contact sheet; anything different goes to a "Deviations" list in PARITY.md for the owner; no silent acceptance.
6. **Proceed** – next unit only after the row is ticked. Phase gates need an owner OK on the contact sheets.

**Integration audit at every phase end** (`integration-audit` skill, run via Firebase MCP + emulator tests): (i) 34 app callables ↔ `functions/src/index.ts` exports ↔ `FirebaseFunctionsClient` (incl. names routed via `callAdmin`/`runAdminMaintenance`, which NIGHTHAWK misses); (ii) every Firestore path the client touches ↔ `firestore.rules` ↔ `tests/rules` (add positive admin tests); (iii) Storage paths ↔ `storage.rules`; (iv) `docs/DATA_MODEL.md` fields vs `toFirestoreMap`/`fromFirestoreMap`; (v) mirrored logic pairs with **shared JSON test vectors** (attendeePricing, priceChange, bookingTerms, listingPrices, legacyPricing, listingLocation, playCatalog, roles, consent version); (vi) `tests/e2e` flows; (vii) admin web ↔ rules/callables; (viii) agent ↔ adapter contract tests. Output: `docs/migration/audits/<phase>.md`.

## 5. Phases

Three tracks run in parallel after P0: **Track K** (KMP app, P2→P7, P10), **Track W** (admin web, P1), **Track A** (agent, P9). Backend-for-iOS (P8) starts once P5 starts.

### P0 – Freeze, knowledge, tooling (no product-code change) — ✔ DONE 8 Nov 2026 (spike report final: GO, Kotlin 2.3.20 / CMP 1.12.2 / AGP 9.4.1 / Gradle 9.7.1)
- Tag `kmp-golden-v1047`; run `testDebugUnitTest -Proborazzi.test.record=true` once; store the 280 PNGs on `refs/design-shots/golden-v1047`; write `scripts/migration/diff_shots.py` (pixel diff + HTML contact sheet) and `compare_android_ios.py`.
- Write `docs/migration/MASTER_PLAN.md` (this plan, condensed), `PARITY.md` (130 non-admin units, classes A1/A2/B/C), `ADMIN_PARITY.md` (70 items), `AGENT_ADAPTER.md` (contract), `DECISIONS.md`.
- **Project memory** (§10): CLAUDE.md section, updates to `docs/IOS_PLAN.md`, `OWNER_TODO.md`, `OPS_LOG.md`; new skills/agents/hooks (§11).
- **Test vectors first**: add Kotlin tests for `bookingTerms`, `listingPrices`, `legacyPricing` (TS-only today); export shared JSON fixtures consumed by `node --test` and Kotlin.
- **Spike (2 branches, throw-away)**: (1) CMP+AGP 9 module split compiles Android + iOS framework on the macOS runner with Kotlin 2.2.10 or the minimal bump; (2) one A1 screen (`SplashScreen`) + a theme renders identical on Android; (3) Google Map in `UIKitView` with overlay pill/strip; (4) Arabic plural + ` ` string through `composeResources`; (5) font-scale cap inside a dialog/sheet; (6) Firebase Auth via the existing Swift `AuthService` behind a Kotlin gateway; (7) Compose-for-Web: Firebase Web Auth + one callable + the Users tab list.
- **Gate**: written spike report with go/no-go per item and the exact Kotlin/CMP/AGP/Gradle versions. Failing items get a named fallback (e.g. admin → React; map → platform-owned view with shared controls).

### P1 – Admin Console to the web (Track W)  *Android admin stays untouched until P1.F*
- P1.0 Auth & shell: Firebase Web Auth (Google + email/password), `assignInitialRole`, force ID-token refresh, require `claims.role=="ADMIN"`; new origin `admin.pro-host.tech` (Netlify second site, DNS at Hostinger, add to `authconfig` authorized domains — `scripts/maintenance/run.js` hard-codes only `pro-host.tech`); OAuth web-client origins; CORS check; App Check provider decision (unenforced today). Shell: header, online/offline pill, 7 tabs with badges, 900 dp content width.
- P1.1 Tabs one at a time, each ticked against `ADMIN_PARITY.md`: **Users** (+ Dossier sheet) → **Listings & Bookings** (review queue, verify/activate/delete; edit wizard waits for P6-W5) → **Packages** (billing health, payments needing attention, Retry/Activate/reassign, Force Upgrade, Play sync, governance tag, CSV) → **Analytics** → **Security** (live audit log, CSV, legal-doc versioning with Storage) → **Schema** (all 5 categories + attendee packages, defaults port of `DefaultSchema.kt`, hashtag usage) → **Demo Control**.
- P1.2 Items outside the 7 tabs: Security ID/admin profile; admin notification centre (`ADMIN_SUBSCRIPTION`, `ADMIN_LEAD`, `LISTING_VERIFICATION_REQUEST` — web notifications via Firestore listener; web push optional); **Admin-as-Pro-Host tools** (all listings, all requests, renting progress, change price, payment reminder, create/edit listing) — delivered by hosting the shared owner screens in `adminWeb` after P6-W5; system debugger (debug-only → web "Diagnostics" page, owner decision); GA4 secret config (callable has no UI today — expose or keep CLI, owner decision).
- P1.3 Mailing console (`public/admin.html`, 5 tabs + WhatsApp alerts) stays live as is; optional later port to the web console under the admin claim. IG Control Room moves with the agent (P9). Maintenance CI tasks stay CI tasks (listed, not ported).
- P1.4 **Bring-over of quirks, not silent fixes**: list in `DECISIONS.md` for the owner — audit CSV exports only 500 loaded rows; client sends 3 audit types the server rejects; "Delete user" removes profile only (not Auth); dead code (`AdminExportDataDialog`…); no bootstrap-admin path documented; rules tests lack positive admin cases (add them — safe, no behaviour change).
- P1.F Cutover gate: all 70 items ticked, side-by-side Playwright screenshots (Playwright is pre-installed) of web vs Android admin per tab, a live walk-through by the owner. **Only then** delete admin files from Android (17 files + admin branches in 12 mixed files: `FirebaseFunctionsClient` lines ~77–340, `ProHostNavGraph`, `ProHostRepository`, `DataModels`, `FirestoreService`, `AppDrawerContent.AdminDrawerContent`, …). Mobile ADMIN accounts then see a "use the web console" screen — **this is a necessary behaviour change; confirm wording/behaviour with the owner in DECISIONS.md before P1.F**, incl. where admin pushes deep-link.

### P2 – De-Android the Android app in place (no visible change)
Goal: make the code compile in `commonMain` while still living in `:app`. Each step is mechanical and proven by existing unit tests + golden diff.
1. Remove dead deps (Room/KSP, Moshi, Retrofit, OkHttp, serialization, Navigation, CameraX) and their proguard blocks; fix stale docs (Gradle 9.7.1).
2. `Context`-free ViewModels: introduce `StringProvider`, `Messenger`, `PlatformActions`; replace `context` params (23 VM methods, 31 UI call sites, 84 `UiMessages` calls). Extract `UpdateState` from `InAppUpdateManager.kt`.
3. Replace JVM APIs in A2 files: `kotlinx-datetime` (all `Calendar`/`SimpleDateFormat`), `kotlin.uuid`, `kotlin.io.encoding.Base64`, hash lib, `expect` currency/number formatter; keep rounding identical (guarded by shared vectors).
4. `Uri` → `PickedFile`; `LatLng`/`CameraPosition` → common `GeoPoint`/`MapCamera` in view-model state; `rememberSaveable` savers → primitives.
5. Cut the seams listed in §3 (interfaces + current Android code as the actual).
- **Gate**: all unit tests green, 280-render diff = 0 (or approved), app smoke on an internal-testing build, integration audit.

### P3 – Gradle split (Android must stay green)
- Create `shared` (`org.jetbrains.kotlin.multiplatform` + `org.jetbrains.compose` + `com.android.kotlin.multiplatform.library`) and `androidApp` (application only). Drop `org.jetbrains.kotlin.android`, `android.builtInKotlin=false`, `android.newDsl=false` (CLAUDE.md warning replaced by the new DSL rules). Keep Crashlytics + google-services + R8 + signing + `ndk.debugSymbolLevel=FULL` in `androidApp`.
- Move A1/A2 files first, then B/C behind seams. Roborazzi/Robolectric harness stays in `androidApp/src/test`. Update CI task paths, `release-bundle.yml` (versionName grep, `google-services.json` path, AAB/mapping/native-symbol paths), `design-screenshots.yml` path filters, `scripts/website/gen-agent-release.js`, NIGHTHAWK path constants, baseline profile paths.
- **Gate**: signed release AAB builds from CI with identical R8 behaviour (mapping check), `release-bundle probe`, golden diff = 0.

### P4 – Theme, components, resources into `shared`
Units in order (each a PARITY row): tokens (`Color/Elevation/Radius/Shape/Spacing/Type/ProHostColors`), `Theme.kt` (+dot texture, status-bar contrast via SystemUi seam), `FontScaleCap` → shared density provider **and inside every dialog/sheet/popup choke point (spike: a Compose-level cap does not reach those windows; gate test CAPPED dialog/sheet <= 1.15)**, `CommonComponents` (1,608 lines, Coil 3), `ProHostBrandComponents`, `ProCard`/buttons/`InputField`/`SelectField`/banners/badges/avatar, bottom nav pill, snackbars, `ResizableBottomSheet`, dialogs, `CountryPickers`, `DateRangePickerRow`, `AutofillHints`. Resources: 1,225 strings + 10 plurals (en/ar), Manrope + Roboto, drawables, WAVs → `composeResources`; icons (169 names) via CMP icons or vendored vectors; `scripts/i18n/*` and legal sync script re-pointed.
- **Gate**: golden diff for every component preview; Arabic/RTL set identical; string-key parity script (EN = AR keys, placeholders equal).

### P5 – Data layer behind gateways
Models (`DataModels.kt` 1,819 lines, 47 types), `FirestoreService` behind `FirestoreGateway` (listeners as Flows, paging limit grow, `localOnly()` for tests), `FirebaseFunctionsClient` behind `FunctionsGateway` (admin callables already trimmed), Storage + `ImageCodec`, Auth gateway, `ProHostRepository` + `BookingsRepository` + `ProfilesRepository` + `DefaultSchema` + `RepositoryCsv`, analytics (91 events, consent store), billing models (`PlayCatalog`, common `OfferPhase` model feeding `PlayOfferText`/`PlayPromo`). iOS gateways = Swift implementations (reuse `AuthService.swift`, `FunctionsClient.swift`, App Check delegate).
- **Gate**: all ~116 portable unit tests run from `commonTest` on Android host **and** iOS simulator; emulator e2e (`tests/e2e/flows.test.js`) green; integration audit (rules ↔ every path).

### P6 – Screens in waves (Android parity first, then iOS), each wave = learn/plan/execute/compare/confirm per screen
- **W1 Auth & shell**: Splash, `LoginAuthScreen`+`AuthSteps` (Google, **Apple** slot, email), registration (3 steps), `KycScreen`/`RequirementsSheet`, consent/permission dialogs, `ProHostNavGraph` (state machine, back handling, deep-link router, nav-pill nested scroll).
- **W2 Explore**: `DiscoveryScreen` + `DiscoveryViewModel`, `LebanonMapCanvas` (marker bitmaps → common drawing; map view = `expect`), filters, load-more/auto-search, "Search this area".
- **W3 Listing & booking**: `SpaceDetailsScreen`, `RoomCards`, `AvailabilitySheet`, `SpaceAvailabilityCalendarView`, `RentalBookingDialog` (1,562 lines, UTC date logic → kotlinx-datetime, DatePicker parity incl. Arabic), requirements, "Request sent".
- **W4 Specialist**: `MyFavoritesScreen`, `MyBookingsScreen`, notification centre, `SpecialistProfileScreen` (drawer identity card, language/legal/support), legal viewer (WebView → platform view), PDF (decision: keep generator per platform or host PDFs — owner decision, behaviour must not change).
- **W5 Pro Host**: `OwnerHubScreen`, `CreateListingDialog` + `SubdivisionForm` + `RentalStrategyEditors` + `ListingLocationMapPicker`, `ManageListingScreen`, `ChangePriceSheet`, `OwnerIncomingRequestsView`, `OwnerRentalRequestsScreen`, `OwnerRentingProgressScreen`, `OwnerAnalyticsScreen`, `ListingVerificationDialog`. (Unblocks admin-web items 37 and 65.)
- **W6 Premium**: `OwnerSubscriptionsScreen` (1,190), `PromoCountdownCard`, `BillingController`, `PlayOfferText` summaries; Play actual unchanged; StoreKit 2 actual in P7.
- **Gate per wave**: Android golden diff = 0 for the wave's screens, then iOS contact sheets reviewed by the owner on TestFlight; integration audit.

### P7 – iOS native layer (feature by feature, after the screen it serves)
Sign in with Apple + Google Sign-In (nonce check stays common), APNs/FCM push + notification routing, StoreKit 2 (price/offer text from StoreKit only, server verification, Restore, Manage, offer codes, "active via Google Play" state — decision 12(a) in IOS_PLAN), Google Maps iOS SDK view, PHPicker/camera, CoreLocation+CLGeocoder (When-In-Use), EventKitUI monthly reminder, Universal Links + `prohost://`, AVAudioPlayer sounds, haptics, `WKWebView`, Dynamic Type mapping to the 1.15 cap, VoiceOver labels, privacy manifest with real content, ATS, Crashlytics dSYM (script exists).

### P8 – Backend/platform for iOS (additive; Android unaffected) — starts with P5
`appleSubscription.ts` + `verifyAppleTransaction` + App Store Server Notifications V2 + daily Apple re-sync (platform-aware `SubscriptionService`/`EntitlementManager`), `apns` block in `lib/push.ts`, App Attest logging in `assignInitialRole`, Sign in with Apple token revocation on delete, `apple-app-site-association` + `/get` iOS redirect + Associated Domains capability, **report/block (App Review 1.2)** = new `reportContent` callable + collection + rules/tests + admin queue (**a visible feature on both platforms and in the admin web → needs explicit owner approval of its UI before building; it is the one planned UX addition**). Tests: Jest, rules, e2e.

### P9 – AI agent as its own project (Track A, independent of KMP)
1. Replace the `officeLeads.ts → instagram/config + graph` import (`dmOwner`) with a lead-sink callback.
2. Introduce an in-process `AppAdapter` interface (`searchListings`, `accountInsight`, `offers`, `leadSink`, `adminNotify`, `knowledge`, `health`) with a ProHost implementation; retarget `aiRuntime.ts`/`aiSweeper.ts`; remove imports of billing/listings/website/bookings from `instagram/`.
3. Move the hard-coded ProHost strings (allowed URLs, fallback/handoff lines in 4 languages, default vars, notify subjects) into `BrandProfile`.
4. Make lead recording work for every brand (today `onOfficeLeadSaved` listens to the legacy `ig_leads` only).
5. Contract tests shared by agent mocks and ProHost implementation; extend `readOnlyAccess.test.ts` into a contract-level guard (it cannot see writes outside `instagram/` today).
6. Create the new repo + Google project; move `functions/src/instagram/**`, `tools/control-room`, `docs/INSTAGRAM.md`, `marketing/**`, `public/social`, agent Maintenance tasks, `ig_*` rules/tests, `run-ig` identity; secrets move (Firestore-stored today → Secret Manager decision). HTTPS adapter with OIDC ID tokens; Meta webhook callback + `public/_redirects` + `firebase.json` rewrites repointed in one coordinated, reversible cutover.
- ProHost keeps: adapter implementation, contact book `mailing_list`, `public/admin.html`, website forms, `shareLanding`/deep links, Play offers, release-notes producer.
- **Gate**: `igsmoke`, `officeleadsmoke`, `aitest` equivalents green before and after; no change in DM/comment behaviour; Meta app review/permissions untouched.

### P10 – Hardening and release
Performance (startup, scroll, app size — Skia adds a few MB), accessibility (TalkBack + VoiceOver, 48/44 pt targets), battery/memory soak on a borrowed iPhone, Arabic QA, NIGHTHAWK updated (paths, callable-name check through `callAdmin`/`runAdminMaintenance`, new KMP checks), docs rewritten (`IOS_PLAN.md` as state + runbook, `MONETIZATION.md` Apple section, `DATA_MODEL.md` platform fields), TestFlight internal → external beta → App Store submission with App Review package (demo accounts, deletion, report/block, subscription disclosures) → phased release; Android release continues via `release-bundle.yml` (staged 20%). Bump `docs/release/published.txt` only when production is live.

## 6. Reuse (do not rebuild)
- Signing/TestFlight: `.github/workflows/ios-testflight.yml`, `scripts/ios/asc_signing.py`, `asc_api.py`, `asc_inspect.py`, `ios/ExportOptions*.plist`, `ios/project.yml` (trimmed), `GoogleService-Info.plist`, `PrivacyInfo.xcprivacy`, `ProHostAppDelegate.swift` (Firebase + App Check), `AuthService.swift`, `AuthNonce.swift`, `AuthError.swift`, `FunctionsClient.swift`, Manrope/Roboto fonts.
- Obsolete after P4: `ios/ProHost/DesignSystem/*.swift`, icon pipeline (`build_icons.py`, 178 imagesets), `strings_convert.py`/`check_strings_parity.py`/`build_labels.py`, `Localizable.xcstrings`, `DesignTokensTests.swift`.
- Existing seams/tests to extend, not replace: `AuthGateway`, `SessionFinisher`, `AnalyticsSink`, `FirestoreService.localOnly()`, `FakeFunctionsClient`, `hermeticRepository()`, `DesignScreenshotTest` (`OVERLAYS`/`TAPS`), `readOnlyAccess.test.ts`, `tests/rules`, `tests/e2e`.

## 7. Risks and mitigations
- Map on iOS (`UIKitView` + overlays) → P0 spike; fallback = native map view with shared controls.
- Material 3 drift (Compose 1.7 → CMP) → diff harness catches it; pin versions; fix in tokens/components, not screens.
- `FontScaleCap` in dialog windows → P0 spike; fallback platform setting.
- Compose-for-Web maturity/bundle size/Firebase JS → P1.0 gate; fallback React with the same `ADMIN_PARITY.md`.
- No Mac/Android emulator in the cloud sandbox → macOS runners + Roborazzi; each iOS feedback loop ~10–20 min; keep units small.
- Silent pricing/date drift Kotlin↔TS → shared JSON vectors before any port.
- Admin-role mobile behaviour change → explicit owner decision before P1.F.
- Secrets/IAM: new function features need roles added deliberately (never re-grant Editor); update `docs/GCP_IAM.md` and `OPS_LOG.md` with any IAM change.
- Housekeeping found: `my-upload-key.jks`/`upload_certificate.pem`/`hs_err_pid32984.log` tracked in git (owner chose to keep keystore; the 1.9 MB crash log can go).

## 8. Owner decisions to collect (go in `DECISIONS.md`, default shown)
1. Mobile ADMIN account behaviour after cutover (info screen + link to web console).  2. Admin quirks list (§P1.4): keep as-is or fix.  3. Web push for admin (no).  4. System Debugger / GA4-secret UI on web (include Diagnostics, keep GA4 secret CLI-only).  5. Legal PDF generation per platform vs hosted PDFs.  6. Report/block UI (required by Apple 1.2).  7. Roboto bundling on iOS for text parity (yes).  8. New Google project/billing for the agent; Meta webhook cutover window.

## 9. Skills, tools and connectors upgrade (Phase 0 deliverables)
- **New project skills** in `.claude/skills/`: `kmp-migration` (rules, class table, seam list, Gradle/AGP 9 gotchas, commands), `parity-audit` (golden capture/diff/contact-sheet procedure, PARITY.md format), `admin-web-parity` (70-item checklist + Playwright diff), `integration-audit` (DB↔backend↔frontend↔admin checklist and commands), `agent-adapter` (contract + guards). Update `prohost-design` (paths `shared/…`, CMP rules) and the session-start-hook (prime Gradle/Node caches and JDK in cloud sessions).
- **Project subagents** in `.claude/agents/`: `parity-reviewer` (read-only: diffs shots, reads PARITY.md), `integration-auditor` (read-only: rules/callables/vectors), `kmp-porter` (one unit at a time following the protocol).
- **Scripts**: `scripts/migration/{diff_shots,compare_android_ios,check_parity_table,export_vectors}.{py,js}`; CI jobs: shared tests on Android host + iOS simulator, golden diff on PR, macOS contact-sheet artifact.
- **Connectors available now**: Firebase MCP (rules, functions logs, Firestore, Crashlytics — use for audits), GitHub MCP (CI logs/PR), Drive/Sheets/Docs (agent sheet, owner docs), Playwright (pre-installed) for admin web diffs. Registry search found no KMP/Xcode/device-farm connector, and several plugin MCPs (Figma, Canva, Linear…) failed to connect through the sandbox proxy, so tooling is built in-repo rather than assumed. Sandbox network allowlist must reach Maven/Gradle plugin portal/CMP/Coil/CocoaPods/SPM hosts — check in the P0 spike and request the exact hosts via `update_environment` (only on a real block).
- Use `WebSearch`/`WebFetch` on JetBrains release notes at each version bump; record versions in `DECISIONS.md`.

## 10. Project memory (to be written right after approval — plan mode blocks edits now)
Add to `CLAUDE.md` a section **"KMP + Compose Multiplatform migration (decided 8 Nov 2026)"** containing: the three locked decisions; the five owner rules; "no UI/UX change, deviations are listed and asked"; the unit protocol (learn/plan/execute/compare/confirm); the module layout; "admin code never goes to shared/iOS; admin = `adminWeb`"; "agent = separate project via adapter, never read ProHost collections directly from new code"; "SwiftUI port is cancelled — do not add to `ios/ProHost/DesignSystem`"; pointers to `docs/migration/*`; the per-phase integration audit; the stale-fact corrections. Also: mark `docs/IOS_PLAN.md`/`IOS_READINESS.md` headers superseded (Apple account/backend sections still valid), update `docs/OWNER_TODO.md` (new items: decisions §8, admin subdomain/DNS, agent GCP project, borrowed iPhone still needed, report/block approval), append `docs/OPS_LOG.md`, and fix CLAUDE.md's Gradle 9.6.0 → 9.7.1.

**Prompt for sessions that are already open** (they loaded CLAUDE.md at start, so they must re-read; memory only reaches them once it is on the branch they use — I will push it to `claude/sharp-clarke-w4rlh2` and, if you say so, to `main` per the branch policy):
```
Stop your current task at a safe point. Run: git fetch origin && git merge origin/main (or origin/claude/sharp-clarke-w4rlh2).
Then read CLAUDE.md section "KMP + Compose Multiplatform migration (decided 8 Nov 2026)" and docs/migration/MASTER_PLAN.md,
PARITY.md and DECISIONS.md. Reply with: (1) the five owner rules in your own words, (2) which phase/unit your current task
belongs to, (3) anything you were doing that conflicts (SwiftUI port, admin work in the Android app, direct agent reads of
ProHost data). Do not change any UI/UX; do not continue SwiftUI screens; follow the unit protocol; end with the open items
from docs/OWNER_TODO.md.
```

## 11. Verification (how we will know each stage is right)
- P0: spike report with versions; golden set (280 PNGs) reproducible twice with 0 diff; new Kotlin tests for the three TS-only pairs pass; vectors consumed by both runtimes.
- Every unit: unit tests + `diff_shots.py` = 0 for its goldens; PARITY row ticked; for iOS the Android | iOS contact sheet reviewed.
- Admin web: each of 70 items exercised against the emulator suite (`firebase emulators:exec`) and Playwright screenshots vs Android admin; positive admin cases in `tests/rules`; owner walk-through before Android admin removal.
- Backend: `cd functions && npm test`; `tests/rules`; `tests/e2e/flows.test.js` in CI; Maintenance `prodcheck`; NIGHTHAWK (updated paths) before any tag.
- Agent: contract tests + `igsmoke`/`officeleadsmoke`/`aitest` before and after; webhook cutover with rollback.
- Release: `release-bundle.yml` probe → internal → staged production; iOS TestFlight internal → external → App Review.

## 12. Immediate next steps after approval (in order)
1. Write memory (§10) + `docs/migration/*` skeletons + skills/agents (§9); commit and push to `claude/sharp-clarke-w4rlh2`; show you the open-session prompt.
2. Tag `kmp-golden-v1047` and capture goldens; add the three missing Kotlin test suites and shared vectors.
3. Run the P0 spike and report go/no-go before P1/P2 start.
