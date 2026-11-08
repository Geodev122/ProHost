# P0 spike report: Kotlin Multiplatform + Compose Multiplatform for ProHost

Status: **DRAFT, rounds 1-4 final, round 5 running, round 6 not started** (8 Nov 2026). The spike is throw-away code under `spike/kmp` with the manual-push workflow `.github/workflows/spike-kmp.yml`; it touches no product build and is deleted when this report is final.

## 1. Verdict so far

**GO for the toolchain.** One shared Kotlin module builds the same Compose UI for Android, iOS, web and a JVM test host, with Arabic and English resources working end to end. The migration is feasible on **Kotlin 2.3.20, Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.7.1**. One thing changes the plan: **Kotlin must be raised from 2.2.10 to at least 2.3.20** (see 3.1).

Not yet proven (stated honestly, each with its gate and fallback in section 5): pixel parity of a real screen on the new Compose version (round 5, running), Compose-for-Web with Firebase JS (round 6), the Google map inside `UIKitView` and the Swift auth gateway (need the real iOS host app).

## 2. Results by item

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | AGP 9 module split: `shared` (`com.android.kotlin.multiplatform.library`) + `androidApp` (`com.android.application`), no `org.jetbrains.kotlin.android` plugin | **PASS** | `:androidApp:assembleDebug` green (run 42478543724) |
| 1b | Same shared UI links as an iOS framework and its tests run on the iOS simulator | **PASS** | macOS job: `linkDebugFrameworkIosSimulatorArm64` 4.4 min, `iosSimulatorArm64Test` 2.4 min |
| 1c | Same shared UI as Compose for Web (wasmJs) | **PASS** (build) | `:webApp:wasmJsBrowserDistribution`; hello-world dist 4.6 MB zipped (skiko.wasm 8.25 MiB, app wasm 2.71 MiB, js 513 KiB uncompressed) |
| 4 | composeResources: English plurals, positional format args, ` ` escape | **PASS** on iOS simulator and on the JVM host | commonTest `ResourcesTest` |
| 4b | composeResources: **Arabic** plural categories (zero/one/two/few/many/other incl. 3, 10, 11, 99, 100, 103), reordered positional args (`%2$s من %1$s`), ` ` in Arabic | **PASS** on the JVM host | `ArabicResourcesTest` (plural rules are common Kotlin, so valid for iOS and web; the simulator cannot switch language inside a unit test) |
| 2 | Splash rendered on Compose 1.12 vs the golden rendered on Compose 1.7 | **RUNNING** (round 5) | `SplashParityTest` + `scripts/migration/diff_shots.py` against `refs/design-shots/golden-v1047` |
| 5 | Font-scale cap (1.15) inside content, `Dialog` and `ModalBottomSheet` at font scale 1.5 | **RUNNING** (round 5) | `FontScaleCapTest` (also asserts the uncapped baseline really reads 1.5) |
| 7 | Compose for Web + Firebase JS (init, callable) behind a Kotlin interface | **NOT STARTED** (round 6) | needs a Firebase *web app* registered for live sign-in; round 6 proves interop with a dummy config |
| 3 | Google Maps iOS inside `UIKitView` with Compose overlay controls | **DEFERRED to the iOS-host milestone** | needs an Xcode host app, the Maps iOS key and the Firebase iOS config (P3 creates `iosApp`) |
| 6 | Existing Swift `AuthService` behind a Kotlin gateway | **DEFERRED to the iOS-host milestone** | same prerequisites; gate: email/Apple sign-in round-trip on TestFlight |

## 3. Findings that change the plan

### 3.1 Kotlin must move to >= 2.3.20 (round 1 failure, reproduced and explained)
With the project's Kotlin 2.2.10 and CMP 1.12.2 the iOS build stops with `KLIB resolver: ... incompatible ABI version '2.3.0'. The library was produced by '2.3.20' compiler`, and on the JVM the generated resource accessors fail with `Missing stdlib class`. The JetBrains compatibility page says "at least Kotlin 2.1.0" for the latest release; **that is not true in practice for 1.12.2**. Consequences for P3: the Kotlin bump and the Jetpack Compose jump (1.7 to 1.12) happen together with the module split; Material 3 look differences, if any, are caught by the golden diff (item 2).

### 3.2 Build-script facts learned (record in CLAUDE.md at P3)
- Declare all plugins once in the root `build.gradle.kts` with `apply false`; applying versioned Kotlin plugins in several subprojects fails with "Kotlin Gradle plugin was loaded multiple times".
- `ResourceEnvironment`'s constructor is Kotlin-`internal`: switch language in tests with `Locale.setDefault` on the JVM host and the public `getString` / `getPluralString`.
- The JVM host target needs `compose.desktop.currentOs` on its test classpath and, on Linux runners, `libgl1 libfontconfig1 libxrender1 libxtst6 libxi6` (Skia native runtime), otherwise `LibraryLoadException`.
- composeResources theme qualifier is `-dark`/`-light` (not Android's `-night`): `drawable-night-xxhdpi/splash.png` becomes `drawable-dark-xxhdpi/splash.png`.
- Android: the new AGP DSL (`androidLibrary { }`, `androidResources { enable = true }`) works with Gradle 9.7.1; the repo's `android.newDsl=false` / `builtInKotlin=false` opt-outs are not needed in the spike.

### 3.3 Golden baseline determinism (separate finding, already in DECISIONS.md)
Three renders of the unchanged app: 280/280 identical with `docs/migration/golden-masks.json`; without masks only the five `request_detail_*` clock-time renders (and once the `explore_list_light` hero image) differ.

## 4. Sizes and timings (for planning)
| Item | Value |
|---|---|
| iOS framework link (macOS runner, cold cache) | ~4.4 min; simulator tests ~2.4 min |
| Linux job (JVM tests + Android + web) | ~8 min warm |
| Web hello-world download | 4.6 MB zipped (will grow with the real admin console; acceptable for an admin tool) |

## 5. Deferred items: gates and fallbacks
| Item | Gate (in the iOS-host milestone) | Fallback if it fails |
|---|---|---|
| Google Maps iOS in `UIKitView` with the "Search this area" pill, listing strip and controls overlaid | pan/zoom/tap markers works, overlay controls receive taps, no touch-through, screenshot sheet reviewed | native map view hosted by SwiftUI with the Compose overlay controls in a transparent layer, or MapLibre via a shared `MapView` seam |
| Swift `AuthService` / `FunctionsClient` behind Kotlin gateways | email, Google and Apple sign-in round-trip on TestFlight; `assignInitialRole` succeeds; error mapping identical | GitLive firebase-kotlin-sdk for Auth/Functions only |
| Compose for Web + Firebase JS | round 6 smoke passes; live sign-in verified in P1.0 with a registered web app | React admin (same ADMIN_PARITY.md), reusing nothing from Kotlin |

## 6. Remaining work before this report is final
Round 5 results (splash diff, font cap), round 6 (web interop), then delete `spike/` and `spike-kmp.yml`, update `DECISIONS.md` (versions line) and tick P0 in `PARITY.md` / `MASTER_PLAN.md`. P1 and P2 start only after that.
