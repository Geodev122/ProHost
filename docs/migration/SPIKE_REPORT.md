# P0 spike report: Kotlin Multiplatform + Compose Multiplatform for ProHost

Status: **FINAL** (8 Nov 2026). The spike was throw-away code (`spike/kmp`, workflow `spike-kmp.yml`, CI runs 42476244758 … 42500151896). It touched no product build and has been deleted; this report, `DECISIONS.md` and the scripts under `scripts/migration/` are what remains.

## 1. Verdict

**GO for the toolchain, with two plan changes** (section 3): Kotlin must be >= 2.3.20, and the font-scale cap cannot live in Compose alone. One shared Kotlin module builds the same Compose UI for Android, iOS, web and a JVM test host, with Arabic and English resources working end to end, and Compose for Web drives Firebase JS through a Kotlin interface.

Versions to use: **Kotlin 2.3.20 (minimum), Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.7.1** (Xcode as on `macos-latest`).

Two items need the real iOS host app and move to the iOS-host milestone with a gate and a named fallback each (section 5): Google Maps iOS inside `UIKitView`, and the Swift `AuthService` behind a Kotlin gateway.

## 2. Results by item

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | AGP 9 module split: `shared` (`com.android.kotlin.multiplatform.library`) + `androidApp` (`com.android.application`), no `org.jetbrains.kotlin.android` | **PASS** | `:androidApp:assembleDebug` green (every run since 42478543724) |
| 1b | Same shared UI links as an iOS framework; its tests run on the iOS simulator | **PASS** | macOS job: link ~4.7 min, simulator tests ~4 min (run 42496806490) |
| 1c | Same shared UI as Compose for Web (wasmJs) | **PASS** | dist 5.5 MB zipped (skiko.wasm 8.25 MiB, app wasm 2.71 MiB, js 681 KiB uncompressed, now incl. Firebase JS) |
| 2 | Splash rendered on Compose 1.12 vs the golden rendered on Compose 1.7 | **PASS with a documented dynamic region** | 1170x2532 renders: artwork, wordmark, background, layout identical (906 / 1023 px differ of 3 M, all inside the 84x85 px spinner box). The spinner ring, stroke, round caps and colours match (contact strip of 16 animation frames); the golden shows the animation's very first frame (a dot at the top), the new one has already started moving, and no frame of a 2.7 s scan equals it, so an exact pixel match of a free-running indeterminate spinner is not a valid test. Handling in section 3.3 |
| 4 | composeResources: English plurals, positional format args, ` ` escape | **PASS** on iOS simulator and JVM host | commonTest `ResourcesTest` |
| 4b | composeResources: **Arabic** plural categories (zero/one/two/few/many/other incl. 3, 10, 11, 99, 100, 103), reordered positional args (`%2$s من %1$s`), ` ` in Arabic | **PASS** on the JVM host | `ArabicResourcesTest` (plural rules are common Kotlin, valid for iOS and web) |
| 5 | Font-scale cap (1.15) inside content, `Dialog`, `ModalBottomSheet` at system font scale 1.5 | **PARTIAL: content only** | measured `{baseline 1.5, UNCAPPED content/dialog/sheet 1.5, CAPPED content 1.15, CAPPED dialog 1.5, CAPPED sheet 1.5}`: a `LocalDensity` cap inside Compose reaches content but **not** Dialog / ModalBottomSheet windows (they read their own window configuration). Handling in section 3.2 |
| 7 | Compose for Web + Firebase JS (init, auth, callable) behind a Kotlin interface | **PASS** | Chromium (Playwright, software GL): canvas drawn, no page/console errors, `title = "firebase:ok app=[DEFAULT] signedIn=false callable=true"` (`webApp` + `AdminGateway`). Live sign-in needs a registered Firebase *web app* (P1.0) |
| 3 | Google Maps iOS inside `UIKitView` with Compose overlay controls | **DEFERRED** to the iOS-host milestone | needs an Xcode host app, the Maps iOS key and the Firebase iOS config |
| 6 | Existing Swift `AuthService` behind a Kotlin gateway | **DEFERRED** to the iOS-host milestone | same prerequisites |

## 3. Findings that change the plan

### 3.1 Kotlin must move to >= 2.3.20
With Kotlin 2.2.10 and CMP 1.12.2 the iOS build stops with `KLIB resolver: ... incompatible ABI version '2.3.0'. The library was produced by '2.3.20' compiler`; on the JVM the generated resource accessors fail with `Missing stdlib class`. JetBrains' "at least Kotlin 2.1.0" is not true in practice for 1.12.2. The bump (and Jetpack Compose 1.7 to 1.12) happens in P3 together with the module split; Material 3 drift is caught by the golden diff.

### 3.2 Font-scale cap (new item for P4)
Android keeps the cap where it is: `attachBaseContext` caps the `Configuration`, which every window (dialogs, sheets) inherits. For the shared code (iOS, web) a Compose-level `FontScaleCap` is **not enough**: wrap each window's content too (a `FontScaleCap` inside `ProHostDialog`, `ProHostBottomSheet`, `Popup` and `DropdownMenu` content, which are all already single choke points in `ui/components`), and add the Robolectric/iOS test above (CAPPED dialog and sheet must read <= 1.15) to the P4 gate. Until that test passes, iOS/web must not ship the shared dialogs.

### 3.3 Endless animations vs a pixel golden
Indeterminate progress indicators (splash, loading states) cannot be compared by exact pixels across Compose versions because the animation phase at capture differs. Rule for the golden diff from P4 on: such regions go into `golden-masks.json` as *known dynamic regions* (the splash spinner box, 125x125 px around the 28 dp indicator), and a structural test pins what pixels cannot prove after masking (size 28 dp, stroke 3 dp, track = white 35 %, colour = `primary`). Today's Parity Gate (app still on Compose 1.7) needs no spinner mask: its 280/280 comparison already passes.

### 3.4 Build-script facts (also in CLAUDE.md at P3)
- Declare all plugins once in the root `build.gradle.kts` with `apply false`; applying versioned Kotlin plugins in several subprojects fails with "Kotlin Gradle plugin was loaded multiple times".
- Expose Compose from `shared` with `api(compose.runtime/foundation/material3)` when `androidApp` (or its tests) uses those types.
- `ResourceEnvironment`'s constructor is Kotlin-`internal`: switch language in tests with `Locale.setDefault` on the JVM host and the public `getString` / `getPluralString`.
- The JVM host target needs `compose.desktop.currentOs` on its test classpath and, on Linux runners, `libgl1 libfontconfig1 libxrender1 libxtst6 libxi6`.
- composeResources theme qualifier is `-dark`/`-light` (not Android's `-night`).
- Compose-for-Web smoke on a GPU-less runner needs Chromium `--use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader --ignore-gpu-blocklist`; the canvas can sit in a shadow root (`scripts/migration/web_smoke.js` handles both). Kotlin/Wasm JS interop declarations need `@OptIn(ExperimentalWasmJsInterop::class)` (warning today).
- The new AGP DSL (`androidLibrary { }`, `androidResources { enable = true }`) works with Gradle 9.7.1.

### 3.5 Golden baseline determinism (also in DECISIONS.md)
Two harness inputs are non-deterministic: the wall-clock "Submitted" time (masked; its box was widened leftwards on 8 Nov after the measured differences sat directly beside it) and network listing photos that may not load within the fixed 400 ms wait. The Parity Gate now re-renders any differing screen once and fails only on a difference that reproduces (no tolerance involved).

## 4. Sizes and timings (for planning)
| Item | Value |
|---|---|
| iOS framework link (macOS runner) | ~4.7 min; simulator tests ~4 min |
| Linux job (JVM tests + Android + Splash/font tests + web + smoke) | ~8 min warm |
| Web hello-world + Firebase JS download | 5.5 MB zipped (re-measure with the real console; acceptable for an admin tool) |
| Parity Gate (compile + unit tests + R8 + 280 renders + diff) | ~12 min |

## 5. Deferred items: gates and fallbacks
| Item | Gate (iOS-host milestone) | Fallback if it fails |
|---|---|---|
| Google Maps iOS in `UIKitView` with the "Search this area" pill, listing strip and controls overlaid | pan/zoom/tap markers works, overlay controls receive taps, no touch-through, screenshot sheet reviewed | native map view hosted by SwiftUI with the Compose overlay controls in a transparent layer, or MapLibre via a shared `MapView` seam |
| Swift `AuthService` / `FunctionsClient` behind Kotlin gateways | email, Google and Apple sign-in round-trip on TestFlight; `assignInitialRole` succeeds; error mapping identical | GitLive firebase-kotlin-sdk for Auth/Functions only |
| Compose for Web + live Firebase sign-in | P1.0 live sign-in with a registered web app and an ADMIN account | React admin (same `ADMIN_PARITY.md`), reusing nothing from Kotlin |
| Font cap in shared dialogs/sheets | test in 3.2 green on Android host and iOS simulator | platform-level cap per window (UIKit content size category cap) behind the `SystemUi` seam |

## 6. Closing
P0 is complete. `spike/` and `.github/workflows/spike-kmp.yml` are removed in the same change as this report; the CI evidence stays in the run history. P1 (admin web) and P2 (de-Android in place, already running: P2.1, P2.2a, P2.2b ticked) continue under the unit protocol.
