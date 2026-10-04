# ProHost — Agent Notes

## Google Play Billing

**Google Play is the only billing authority.** One subscription, `package_pro_mrr`, with base plans
`pro-montly` (spelled exactly so in Play Console) and `pro-yearly` — constants in
`data/billing/PlayCatalog.kt` (app) and `functions/src/billing/playCatalog.ts` (server). There is no plan
catalog, no stored price and no admin package management: `package_plans` is retired (read-only for old
app versions), `grantPackageToUser` is gone. Never reintroduce local plans, prices or durations.

App uses **Billing Client v9.1.0** (`gradle/libs.versions.toml` → `billing`; Play requires v8+).

- Prices, periods, trials and "Save XX%" come only from the Billing SDK (`PlayOfferText`, per base plan:
  `preferredOffer(details, basePlanId)`). Checkout always passes the chosen base plan's offer token —
  picking one offer per product made the yearly plan unbuyable. Monthly ↔ yearly passes the old token
  (`SubscriptionUpdateParams`). The screen is "ProHost Premium" (`OwnerSubscriptionsScreen.kt`).
- Product queries use the Billing 8+ callback `queryProductDetailsAsync(params) { billingResult,
  queryProductDetailsResult -> }` (`productDetailsList` / `unfetchedProductList`). Never cast the callback
  argument. Re-query on every Premium visit.
- The client never grants or acknowledges and never sets a role. `handlePurchase` sends PURCHASED
  (non-suspended) purchases through `purchaseEvents` to `verifyAndRestorePurchase` (with `fromCheckout`);
  the local RSA check is advisory. Purchases are re-queried on every foreground.
- Server: `SubscriptionService` (`billing/subscriptionService.ts`) records every verified purchase in
  `subscriptions/{sha256(token)}` (server-only) and applies its status through the `EntitlementManager`
  (`billing/entitlementManager.ts`: `grantProHost` / `removeProHost` / `isPremium` /
  `getSubscriptionStatus`). ACTIVE, GRACE_PERIOD and CANCELED-until-expiry ⇒ PRO_HOST; ON_HOLD, PAUSED,
  EXPIRED, REVOKED, REFUNDED ⇒ SPECIALIST; PENDING never grants. Profile mirror (server-only, rules-protected):
  `ownerPackageId` (base plan id or `admin_forced`), `ownerPackageExpiryMillis`, `entitlementSource`,
  `billingStatus`, `subscriptionExpiry`, `subscriptionPlatform`, `subscriptionId`, `lastPurchaseToken`.
  `removeProHost` ignores forced upgrades and notifications for a replaced subscription (`subscriptionId`).
- RTDN (`playBillingRtdn.ts`, `retry: true`) and the daily `billingSyncJob` (also admin `runBillingSync`)
  both go through `syncSubscription`; `expirePackages` re-reads Play before demoting a Play subscriber. The
  sync job also migrates pre-refactor entitlements once (Play purchase ⇒ synced, otherwise ⇒ admin_forced).
- Admin → Packages has one billing action: **Force Upgrade → ProHost** (`forceProHostUpgrade`, permanent,
  `entitlementSource: admin_forced`). Undo = Users tab → Revoke Pro Host (`revokeProHostRole`).
- Premium screen follows Play's subscription UX guidance: a `SubscriptionStatusBanner` per lifecycle state
  (grace period / on hold → "Fix payment", paused → "Resume", canceled → "Resubscribe", pending), all deep-
  linking to the Play subscription center (`sku=package_pro_mrr`); current plan + recurring price + date;
  "Switch to …" with Play's replacement modes (to yearly `CHARGE_PRORATED_PRICE`, to monthly `DEFERRED`);
  trial disclosure; `SubscriptionTermsFooter` (renewal, cancel, "only publishing needs Premium", Terms &
  Privacy). Profile has a "Manage subscription" settings link. Play's transactional in-app messages show on
  every app resume for Play subscribers (`showBillingInAppMessages`, re-syncs on SUBSCRIPTION_STATUS_UPDATED).
- Admins get a push for every subscription change (`billing/adminBillingAlerts.ts`
  `notifyAdminsOfSubscriptionChange`, category `ADMIN_SUBSCRIPTION`, opens `admin_console`): new subscriber,
  renewal, plan switch, every status change (from `syncSubscription` via `adminEventFor`, skipped for
  unchanged re-syncs and the migration), forced upgrade, admin revoke, expiry sweep, unlinked Play-Store
  purchase, activation parked/stuck/given up. Billing pushes use the `prohost_billing` channel. The 3-day
  "ends soon" push goes only to subscriptions that won't renew (canceled) — never to auto-renewing ones.
- Every billing outcome goes through `PlayBillingManager.billingMessages` (typed, buffered) and
  `ProHostViewModel` routes it into `billingError`/`billingSuccess`. Never emit a billing result only to
  Logcat. `launchSubscriptionPurchase` returns whether Play's sheet opened.
- Analytics: `premium_page_viewed`, `premium_plan_viewed`, `premium_checkout_started`,
  `premium_purchase_failed`, `premium_purchase_pending`, `premium_restore_result` (app);
  `premium_purchase_success` (server, once per subscription via `logPurchaseOnce`), `subscription_renewed`,
  `subscription_restored`, `subscription_expired`, … (server). There is no GA4 `purchase` event any more.
- Verify `queryProductDetailsAsync` / `queryPurchasesAsync` signatures before any Billing bump — check
  NIGHTHAWK's "Billing Acknowledgement" check after upgrading.
- Debug/App Distribution APKs are not Play-installed: billing returns DEVELOPER_ERROR there.
  Test purchases with an internal-testing track install.

## Kotlin type safety

- Never `as List<T>` (or any raw unchecked cast) on Firestore/Flow collection emissions from
  `combine()`. Use `(value as? List<*>)?.filterIsInstance<T>() ?: emptyList()` instead
  (see `DiscoveryViewModel.kt`).
- `typealias` must be declared at file top-level, never nested inside a class/ViewModel.
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
- User-initiated `ProHostViewModel` actions must surface thrown exceptions via
  `reportFailure(appContext, e, fallback)`, not only `Log.e`. Explore's load error comes from
  `ProHostRepository.spacesLoadError` (listener failures), never from `sessionRestoreError`.
- Pro Hosts are landlord-only: firestore.rules blocks them from creating bookings. They keep
  the My Rentals tab (read-only, for bookings made before upgrading; Re-book/Edit hidden)
  and My Favorites.
  Demo seeding is debug-build only. `sendInquiryEmail` ("Email the Host") was retired on purpose.
- Per-attendee pricing: a room configures WHEN it's offered (Hourly/Shift/Day, never Monthly),
  then `Subdivision.attendeeTiers` price it: total = attendees × the matching tier's per-person
  price, once for the whole booking. Offered slots carry `AttendeePricing.AVAILABILITY_MARKER_PRICE`
  (1.0) so slot/availability/conflict code is untouched — never display or charge slot prices
  for these rooms; go through `ui/util/AttendeePricing.kt` (mirrored in
  `functions/src/lib/attendeePricing.ts`, unit-tested). `onBookingRequestCreated` re-prices
  attendee bookings and auto-rejects mismatches (`rejectedBySystem`). Booking docs store the
  total as `totalAmountUsd` (functions once read `totalAmount`/`totalUsd` → $0 emails).
- Space details lists rooms as folder tabs (name + type) over the selected room's card. Every
  room choice — tab (page or availability sheet), Explore deep link, card buttons — goes through
  `selectRoom()` in `SpaceDetailsScreenContent`, so page, bottom strip and sheet share one active
  room and the sheet is never a mixed view of all rooms. A space with rooms always has one selected.
- Paged live lists: Explore's public listings load `FirestoreService.PUBLIC_LISTINGS_PAGE` (100) at a time
  and admin lists `ADMIN_PAGE` (300); "load more" grows the same snapshot listener's limit
  (`setPublicListingsLimit` / `setAdminPageLimit`, document-id order, no index needed). Never re-add a fixed
  `.limit(200)` cap or unbounded admin collection listeners. While a search/filter is active and fewer than
  20 listings match, `DiscoveryViewModel` auto-loads further pages (`shouldSearchMore`, cap 1,000) and shows
  "Searching more listings…"; "No workspaces found" appears only once the catalog is exhausted.
- Verification rules: `AppUser.hasVerifiedPhone` gates only the Pro Host workspaces; `AppUser.canTransact()`
  (photo + verified phone) is checked in place when a specialist sends a booking request. My Rentals,
  browsing and Saved are never gated. `isKycComplete` is profile completeness (banner, Premium checkout).
  "Verified phone" = `hasVerifiedPhone(PhoneLink.isLinked())` (phone linked in Firebase Auth) — never
  `isVerified`, which email verification also sets. Missing steps are collected by the shared
  `RequirementsSheet` (photo + `PhoneVerificationSection`, the same body `KycScreen` and
  `KycVerificationDialog` use) over the booking page, keeping the selected slots.
- Specialist routing: bottom tabs Explore · Saved · My Rentals · Profile (`SPECIALIST_BOTTOM_TABS`; there is
  no "Saved only" filter and no drawer Billing dialog — Premium covers it). Tapping a room in Explore opens
  its availability sheet; after a request the "Request sent" sheet offers "View request" (opens My Rentals
  highlighting it) and "Message host on WhatsApp".
  `DiscoveryViewModel` keeps Explore's list position and map camera across a visit to a listing.
- Admin Console tabs live in `ui/screens/admin/` (one file per tab, package `com.example.ui.screens`);
  room cards / folder tabs of the details page in `ui/screens/RoomCards.kt`. NIGHTHAWK's screen checks count
  only files declaring a public `fun XxxScreen(`. First-load lists show `ShimmerLoadingList`, not a spinner.
- No secrets in code or scripts (GA4 secret comes from the caller or `GA4_API_SECRET`), and push tokens are
  never logged — NIGHTHAWK "Secrets & Token Hygiene".
- Explore filters: country matching uses `effectiveCountry` (blank = Lebanon, legacy listings) and the
  Country dropdown is always shown. "Pricing formula" options are `PricingFormulaFilter` (four strategies
  + PER_ATTENDEE, priced per person from the cheapest tier); never list `RentalStrategyType.entries` there.
- Explore controls are attached under the app header (bottom-rounded strip, no count chip). Map results
  follow `searchedBounds`, updated only by the "Search this area" pill after a gesture pan/zoom.
- The availability sheet is `ResizableBottomSheet` (`ui/components`): resize by dragging the handle or
  header between stops; the body scrolls and its leftover scroll never drags the sheet.
- Listing saves go through `SpaceListing.keepingServerOwnedFields(current)` in `ProHostRepository`
  (isVerified, subscription fields, isOwnerSuspended, isOwnerPackageLapsed, ownerId). Never echo
  those from a wizard-built listing: rules deny any save whose changed keys include them, which
  blocked hosts with a lapsed plan from even saving drafts.
- Never `set(..., merge: true)` / batch-set a counter or flag onto a document that might not exist
  (a deleted listing): it recreates a ghost doc. Use `update()` and skip not-found
  (see `favoritesSync.ts`). Analytics and the admin feed ignore ownerless listing docs.
- Unauthenticated email callables (`sendEmailOtp`, `sendSignInEmailLink`) must call
  `takeEmailSendSlot` (5/hour per address) and throw `HttpsError` (never plain `Error`, which
  reaches users as a generic internal error). Throwing inside a Firestore transaction rolls its
  writes back — return an outcome and throw after commit. Server-only collections
  (`email_otps`, `email_send_limits`) need explicit deny rules.
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
- Never remove the **Crashlytics Gradle plugin** (`alias(libs.plugins.firebase.crashlytics)` in both
  build files, plus the release `CrashlyticsExtension` block) while `firebase-crashlytics` is a
  dependency: without it there is no Crashlytics build ID and the SDK throws during Firebase startup —
  v1.0.33 crashed on launch for every user. NIGHTHAWK's Gradle audit flags it as CRITICAL.
- Unit tests must stay hermetic: build repositories with `hermeticRepository()`
  (`app/src/test/.../TestFixtures.kt` → `FirestoreService.localOnly()` + `FakeFunctionsClient`), never
  `ProHostRepository()`, which talks to production Firestore and hung CI for 6 h per run. Gradle caps
  each test task at 15 min and the CI Android job at 45 min.
- Keep `ndk { debugSymbolLevel = "FULL" }` in the release build type, and upload the generated
  `native-debug-symbols.zip` to Play Console with every `.aab`.
- **Always run `node scripts/prohost-debugger.js` (NIGHTHAWK) before tagging a release.** The
  script currently runs 32 checks — Cloud Function consistency, Firestore rules coverage, forced
  unwraps, coroutine error handling, screen loading/error/empty states, deep links, TypeScript
  safety, payment/billing plumbing, Android Vitals (StrictMode/LeakCanary/instrumentation),
  performance profiling, app size, localization, accessibility, Kotlin type safety, Android
  Lint, Detekt, manifest security, Gradle config audit, code style, and analytics hygiene. Reports are written to
  `scripts/nighthawk-report.{json,html}`.
- Keep `android.newDsl=false` (with `DEPRECATED_DSL` suppressed) in `gradle.properties` while the build
  applies `org.jetbrains.kotlin.android` (`android.builtInKotlin=false`): `newDsl=true` makes that plugin
  fail with `ApplicationExtensionImpl cannot be cast to BaseExtension` (broke main run 11, 2026-10-03).
- CI (`.github/workflows/*.yml`) installs Gradle itself: keep `gradle-version` equal to
  `gradle/wrapper/gradle-wrapper.properties` (AGP 9.4 needs Gradle 9.6.0). A mismatch broke every
  CI build from 2026-10-01; earlier pushes also failed. Functions run on Node 22.
- CI deploys functions + rules + hosting on every push to `main`. The deploy fails with
  `firebaseextensions.instances.list` 403 unless the CI service account
  (`FIREBASE_SERVICE_ACCOUNT` secret) has the **Firebase Extensions Viewer** role — firebase-tools
  checks it whenever the functions SDK manifest has an `extensions` key, which it always does.
  Until that IAM role is granted, nothing in `functions/`, `firestore.rules` or `storage.rules`
  reaches production (this is why `sendSignInEmailLink`/`deleteOwnAccount` returned NOT_FOUND).
- Purchases started outside the app (promo codes redeemed in the Play Store) have no
  `obfuscatedExternalAccountId`: RTDN acknowledges + parks them, `verifyAndRestorePurchase` claims the
  token for the first account that restores it (`billing/purchaseLinks.ts`, `play_purchase_links`), and
  renewals resolve through that link. The app root auto-restores on resume for members without a plan.
  Promo links: `pro-host.tech/redeem?code=` (public/redeem.html), in-app `prohost://redeem?code=`.
  Monetization checks + campaign playbook: `docs/MONETIZATION.md`.
- Server Play lookups use `purchases.subscriptionsv2.get` (`billing/playSubscription.ts`, v1 only as a
  fallback for unknown legacy tokens) and classify failures: 401/403 = `config` (functions service
  account not invited in Play Console › Users and permissions, or the Android Publisher API disabled),
  400/404/410 = `invalid`, else `transient`. Never map every Play failure to `unavailable` again — that
  showed paid users "Can't reach the server" (Oct 2026). Activation lives in `billing/activatePurchase.ts`;
  failed or unacknowledged paid purchases are parked in `play_billing_pending` and retried every 15 min by
  `retryPendingPlayActivations` (before Play's 3-day refund). RTDN runs with `retry: true` and throws on
  config/transient Play errors.
- The "Activating your subscription" banner shows only after Play returns PURCHASED and clears on any
  billing message (cancel, pending, error) or server answer. `toUserMessage` passes a function's own
  UNAVAILABLE message through; only transport failures get the generic connection text.
- NIGHTHAWK's "Orphaned Module: billingHelpers / purchaseLinks / playCatalog" and "compileSdk below 34" MEDIUMs are
  false positives (both are imported by other modules; compileSdk is 37).
- `main` history shows NIGHTHAWK checks are sometimes extended directly on `main` (not always
  routed through a feature-branch PR) — before adding new checks or fixing findings on a
  feature branch, `git fetch origin main` and check `git merge-base --is-ancestor <branch-tip>
  origin/main` to see if your branch is already behind. If so, merge/fast-forward first so you
  aren't duplicating check numbers or fixes already on `main`.

## Analytics (GA4)

- Opt-in only: the manifest keeps `firebase_analytics_collection_enabled=false` and Consent Mode
  denied; `AnalyticsConsent` (first-launch `AnalyticsConsentDialog`, Profile › Privacy toggle) turns it
  on. Ads consent is always denied; `AD_ID` is removed from the merged manifest. Don't flip these.
- Log only through typed functions on `com.example.analytics.AnalyticsTracker` — never
  `FirebaseAnalytics`/`logEvent` elsewhere (NIGHTHAWK "Analytics Hygiene"). No PII params; listing/room
  ids are `publicCode`s. GA4 `user_id` is the display code (`U-…`), never the UID. Crashlytics stays
  without a user id.
- Log at ViewModel success/failure points, not in UI, except UI-only moments (screen views in
  `ProHostAppRoot`, availability sheet, booking dialog). `reportFailure` also logs `app_error`.
- Server events: `functions/src/lib/ga4.ts` `sendGa4Event(uid, …)` — consent-gated on
  `user_profiles.analyticsConsent`/`gaAppInstanceId`, never throws. Its API secret lives in the
  server-only doc `app_config/ga4` (not `defineSecret`, so a missing secret can't break deploys).
  Purchases use `transaction_id = sha256(orderId)[0:24]` on both sides so GA4 dedupes.
- Event catalogue, console setup (custom definitions, key events, BigQuery): `docs/ANALYTICS.md`.

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
