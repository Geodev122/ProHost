# ProHost — Agent Notes

## Google Play Billing

App uses **Billing Client v9.1.0** (`gradle/libs.versions.toml` → `billing`). Google Play
requires v8.0.0+ for any app selling in-app products/subscriptions.

- `QueryProductDetailsResult` / `ProductDetailsResult` callback types changed shape across
  v8→v9. Never assign the raw callback result to a typed list directly — always safe-cast
  first: `(result as ProductDetailsResult).productDetailsList?.filterNotNull() ?: emptyList()`.
- `PlayBillingManager.kt` is the single source of truth for billing state
  (`_productDetailsList: MutableStateFlow<List<ProductDetails>>`). Don't reintroduce raw
  `List<Any>` intermediates.
- Verify `queryProductDetailsAsync` and `acknowledgePurchase` signatures against the installed
  Billing Client version before any future dependency bump — check NIGHTHAWK's "Billing
  Acknowledgement" check after upgrading.
- Every billing outcome goes through `PlayBillingManager.billingMessages` (typed `BillingMessage`,
  buffered) and `ProHostViewModel` routes it into `billingError`/`billingSuccess`. Never emit a
  billing result only to Logcat — an uncollected message is what made "tap a plan, nothing
  happens" (Oct 2026). `launchSubscriptionPurchase` returns whether Play's sheet opened.
- Debug/App Distribution APKs are not Play-installed: billing returns DEVELOPER_ERROR there.
  Test purchases with an internal-testing track install.

## Kotlin type safety

- Never `as List<T>` (or any raw unchecked cast) on Firestore/Flow collection emissions from
  `combine()`. Use `(value as? List<*>)?.filterIsInstance<T>() ?: emptyList()` instead
  (see `DiscoveryViewModel.kt`).
- `typealias` must be declared at file top-level, never nested inside a class/ViewModel
  (see `AuthViewModel.kt` — `PendingPhoneRegistration`).
- Don't declare unused generic type parameters on extension functions — it can break Kotlin's
  inference for the receiver type (`ProHostRepository.tog()` was `fun <T : Any> List<SchemaItem>.tog()`
  with `T` unused; fixed to `fun List<SchemaItem>.tog()`).

## Auth, IDs and money display

- One completeness rule: `AppUser.isProfileComplete()` (blank `fullName` ⇒ registration form,
  ADMIN exempt). Use it for cold start and every sign-in path; never re-introduce a
  `phone.isBlank()` gate — email/Google users have no phone until KYC.
- Email sign-in has no "does this email exist" lookup (always empty under enumeration
  protection). `AuthViewModel.startEmailSignIn` sends a magic link and falls back to a 6-digit
  code when the link callable is unavailable. Show callable errors via
  `Throwable.toUserMessage()` / `friendlyErrorMessage()` — never a raw `e.message` ("NOT_FOUND").
- The email-code path creates the Auth user server-side, so `isNewUser` is false for brand-new
  people — decide registration from the stored profile, not `isNewUser`.
- Session lock (biometric/PIN) was removed on purpose; `MainActivity` is a `ComponentActivity`.
- People see server-assigned display codes, never Firebase UIDs/doc ids: `U-`/`L-`/`D-`/`B-` +
  6 Crockford chars (`functions/src/ids/displayCodes.ts`, registry `display_codes/{code}`).
  In Kotlin use the `publicCode` extensions. `displayCode` is server-only (rules protect it);
  the client never writes it at top level, only echoes `Subdivision.displayCode` because the
  subdivisions array is saved whole. Doc ids stay the internal keys.
- Price units: `SpaceCalculationUtils.strategyUnitLabel` / `rateUnitLabel` /
  `lowestPriceFor`. Never hard-code "/mo" (WhatsApp texts used to).
- Payment reminders go to the user's own calendar via `PaymentCalendar` (insert intent, monthly
  RRULE) — no calendar permission, no Google Calendar API.

## Jetpack Compose hygiene

- Any private helper that calls `Text`, `Column`, `remember`, `mutableStateOf`, etc. must be
  annotated `@Composable`. Top-level constants (e.g. color preset lists) must NOT be annotated
  `@Composable`.
- Don't declare the same local `var`/`val` name twice in one Composable scope (copy/paste of
  state blocks is the usual cause — check before adding a new `remember { mutableStateOf(...) }`
  block to an existing screen).

## Build & release engineering

- Release build type requires `isMinifyEnabled = true` and `isShrinkResources = true`
  (`app/build.gradle.kts`) — already set, do not disable.
- Keep `ndk { debugSymbolLevel = "FULL" }` in the release build type, and upload the generated
  `native-debug-symbols.zip` to Play Console with every `.aab`.
- **Always run `node scripts/prohost-debugger.js` (NIGHTHAWK) before tagging a release.** The
  script currently runs 30 checks — Cloud Function consistency, Firestore rules coverage, forced
  unwraps, coroutine error handling, screen loading/error/empty states, deep links, TypeScript
  safety, payment/billing plumbing, Android Vitals (StrictMode/LeakCanary/instrumentation),
  performance profiling, app size, localization, accessibility, Kotlin type safety, Android
  Lint, Detekt, manifest security, Gradle config audit, and code style. Reports are written to
  `scripts/nighthawk-report.{json,html}`.
- CI (`.github/workflows/*.yml`) installs Gradle itself: keep `gradle-version` equal to
  `gradle/wrapper/gradle-wrapper.properties` (AGP 9.4 needs Gradle 9.6.0). A mismatch broke every
  CI build from 2026-10-01; earlier pushes also failed. Functions run on Node 22.
- CI deploys functions + rules + hosting on every push to `main`. The deploy fails with
  `firebaseextensions.instances.list` 403 unless the CI service account
  (`FIREBASE_SERVICE_ACCOUNT` secret) has the **Firebase Extensions Viewer** role — firebase-tools
  checks it whenever the functions SDK manifest has an `extensions` key, which it always does.
  Until that IAM role is granted, nothing in `functions/`, `firestore.rules` or `storage.rules`
  reaches production (this is why `sendSignInEmailLink`/`deleteOwnAccount` returned NOT_FOUND).
- NIGHTHAWK's "Orphaned Module: billingHelpers" and "compileSdk below 34" MEDIUMs are false
  positives (billingHelpers is imported by other modules; compileSdk is 37).
- `main` history shows NIGHTHAWK checks are sometimes extended directly on `main` (not always
  routed through a feature-branch PR) — before adding new checks or fixing findings on a
  feature branch, `git fetch origin main` and check `git merge-base --is-ancestor <branch-tip>
  origin/main` to see if your branch is already behind. If so, merge/fast-forward first so you
  aren't duplicating check numbers or fixes already on `main`.

## Crashlytics & App Check

- Crashlytics is on in debug and release (testers get debug APKs via App Distribution). Don't
  call `setUserId` or log PII — `public/privacy.html` promises crash logs are anonymised.
- Every callable must use `onCall` from `functions/src/lib/callable.ts`, not
  `firebase-functions/v2/https`. That wrapper applies `ENFORCE_APP_CHECK` and logs
  `app_check_unverified` for calls without a valid token. Only flip `ENFORCE_APP_CHECK` to `true`
  after those logs show real app traffic is verified. It was flipped to `true` on main (v1.0.26)
  and reverted to `false`: testers' debug APKs carry no Play Integrity token, so enforcement
  rejects every callable for them (delete account, role assignment, email sign-in).

## Signing material

- By the owner's choice, `my-upload-key.jks` (upload key) and its store/key passwords
  (`.env.example`) are committed to this private repo (main `ab12eba`, `f4990ee`). Treat the repo
  as sensitive; don't copy those values into code, logs, artifacts or other repos.

## Branch policy

Direct pushes and merges to `main` are allowed. Feature branches (e.g.
`claude/mobile-app-analysis-sqxny6`) may still be used for larger or
in-progress work, but merging or pushing straight to `main` no longer
requires opening a PR first.
