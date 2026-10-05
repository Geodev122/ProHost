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
  `BillingController` (`ui/viewmodel/BillingController.kt`, owned by `ProHostViewModel` as `viewModel.billing`
  — all app billing state and actions live there) routes it into `billingError`/`billingSuccess`. Never emit a billing result only to
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
  Demo seeding is debug-build only; the purge is server-side (`admin/purgeDemoContent.ts`: every isDemo /
  `demo-` user, listing and booking, their display codes and demo Auth users). `sendInquiryEmail` ("Email the Host") was retired on purpose.
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
- Paged live lists: Explore's public listings load `FirestoreService.PUBLIC_LISTINGS_PAGE` (100) at a time;
  "load more" grows the same snapshot listener's limit (`setPublicListingsLimit`, document-id order). Never
  re-add a fixed `.limit(200)` cap. While a search/filter is active and fewer than 20 listings match,
  `DiscoveryViewModel` auto-loads further pages (`shouldSearchMore`, cap 1,000) and shows "Searching more
  listings…"; "No workspaces found" appears only once the catalog is exhausted.
- Admin Console is search-first and loads no collections: admins get the same listeners as everyone (public +
  own listings, own profile, own bookings) plus the audit log and a 50-row verification review queue. Totals,
  search, the per-user dossier (U- code + UID, package, listings with D- room codes, Play subscription history
  with order ids — no amounts, rentals) and the all-users CSV come from `functions/src/admin/adminDirectory.ts`
  (`adminCounts` / `adminSearch` / `adminUserDossier` / `adminExportUsers`; query classifier `adminQuery.ts`,
  unit-tested). Name/title prefix search uses the server-only `searchName` field kept by triggers
  (`backfillSearchNames` fills old docs). Never re-add admin collection listeners or client-side admin lists.
- Verification rules (the only two): `AppUser.canTransact(phoneLinked)` = photo + verified phone, checked
  in place when a specialist sends a booking request; `AppUser.canHost(phoneLinked)` = that + country and
  city, checked at Premium checkout and after a plan activates. `hasVerifiedPhone` alone gates the Pro Host
  workspaces. My Rentals, browsing and Saved are never gated; there is no `isKycComplete` and email is never
  a requirement. "Verified phone" = `hasVerifiedPhone(PhoneLink.isLinked())` (phone linked in Firebase
  Auth) — never `isVerified`, which email verification also sets. Missing steps are collected by the one
  shared `RequirementsSheet` (photo, `PhoneVerificationSection` — the same body `KycScreen` uses — and the
  address step with `requireAddress`), opened over the booking page, Premium or Profile's
  `KycCompletionBanner`.
- Email verification is server-mirrored: `assignInitialRole` sets `emailVerified` on every sign-in when
  `emailVerifiedByToken` (email_verified claim or Google provider, `auth/emailVerifiedRule.ts`); the email
  code path marks the Auth user verified; `backfillEmailVerified` (admin) fixes existing profiles.
- Specialist routing: specialists have no drawer and no menu button: one shell with bottom tabs Explore · Saved ·
  My Rentals · Profile (`SPECIALIST_BOTTOM_TABS`), Explore as the centre. Legal, Contact Support
  (`ui/util/SupportContact.kt`) and Sign out live in Profile › More; full-screen destinations (Premium) get a
  back arrow (`ProHostFullScreenTopAppBar(isBackNavigation)`), other tabs a bar without ☰ (`showMenu = false`).
  Pro Host / Admin keep header + drawer. There is no "Saved only" filter and no drawer Billing dialog.
- Bottom nav is a floating pill (`ProHostBottomNavBar`): active tab = filled pill in the role accent (Steel Blue
  specialist, orange Pro Host). It overlays the content (not `Scaffold.bottomBar`); screens leave
  `LocalBottomNavInset` free at their bottom (the shell pads non-Explore tabs; Explore/map read it). On Explore's
  map it collapses to a handle (`DiscoveryViewModel.navExpandedOnMap`, re-collapses on map gesture / tab pick).
- Brand blue is Steel Blue `#2B5A8C` (dark `#93B8E0`, `SteelBlue` in `Color.kt`); never reintroduce `#246BEE`. Tapping a room in Explore opens
  its availability sheet; after a request the "Request sent" sheet offers "View request" (opens My Rentals
  highlighting it) and "Message host on WhatsApp".
  `DiscoveryViewModel` keeps Explore's list position and map camera across a visit to a listing.
- Admin Console tabs live in `ui/screens/admin/` (one file per tab, package `com.example.ui.screens`);
  room cards / folder tabs of the details page in `ui/screens/RoomCards.kt`, its availability sheet in
  `ui/screens/AvailabilitySheet.kt` (selection state stays in `SpaceDetailsScreenContent`, passed as
  `MutableState` holders). Repository: built-in schema catalogue `data/repository/DefaultSchema.kt`, CSV
  formatting `RepositoryCsv.kt`, booking engine `BookingsRepository.kt`, auth/profile `ProfilesRepository.kt`
  (each runs `with(repo)` over the shared internal state; `ProHostRepository` keeps same-signature delegates —
  add new functions to the area class and a delegate, never a second copy of the state). NIGHTHAWK's screen checks count
  only files declaring a public `fun XxxScreen(`. First-load lists show `ShimmerLoadingList`, not a spinner. Profile and Premium are `LazyColumn`s: screen-level
  `val`/`var`/effects sit above the list, dialogs below it, and a section that may be absent is gated outside
  its `item {}` (an empty item would still get the 16 dp gap).
- No secrets in code or scripts (GA4 secret comes from the caller or `GA4_API_SECRET`), and push tokens are
  never logged — NIGHTHAWK "Secrets & Token Hygiene".
- Explore filters: country matching uses `effectiveCountry` (blank = Lebanon, legacy listings) and the
  Country dropdown is always shown. "Pricing formula" options are `PricingFormulaFilter` (four strategies
  + PER_ATTENDEE, priced per person from the cheapest tier); never list `RentalStrategyType.entries` there.
- Explore draws its own header (`ExploreOverlayHeader`; no app bar for specialists): Map|List segmented toggle
  top-left, logo + name centre, notifications top-right, search (draft applied only on ✓ / IME Search) under the
  toggle and filters under the bell. Transparent with floating controls on the map; on the list it has the list
  background and sits above it in the layout (never overlapping). Map results follow `searchedBounds`, updated
  only by the "Search this area" pill after a gesture pan/zoom.
- The listing page's bottom bar is `DetailsBookingBar` (`RoomCards.kt`): room chip + live availability, price,
  Availability + WhatsApp "Message" actions (Preview mode for hosts/admins).
- The availability sheet is `ResizableBottomSheet` (`ui/components`): a standard Material `ModalBottomSheet` (via
  `ProHostBottomSheet`, half + full stops, pinned header, scrolling body) like every other sheet. Never resize a sheet's
  content height while it is open — that fought Material's positioning and slid the sheet's bottom away.
- Retired fields: `SpaceListing.rentalFormulas` and `subscriptionExpiryMillis` (listing and profile) are gone
  from the models. Pricing lives only in `pricing` / `Subdivision.pricing`; a not-yet-migrated document is still
  read through `RentalPricingConfig.fromLegacyFormula` (app) / `pricingFromLegacyFormula` (functions,
  `listings/legacyPricing.ts`, unit-tested — keep both identical). Admin › Demo tab › "Migrate Legacy Listing
  Fields" (`migrateLegacyListingFields`) writes `pricing` and deletes the old keys. `RentalFormula` itself stays:
  bookings record one (`representativeFormula`).
- Security rules are unit-tested in the emulator (`tests/rules`, CI job "Security Rules Tests"); add a test with
  every rules change. Bookings: create must be PENDING with no review/cancel state, on a bookable listing, and an
  edit (`replacesBookingId`) may only replace the caller's own booking; per-side transitions only (host
  PENDING→ACCEPTED/REJECTED, ACCEPTED→CANCELLED as PRO_HOST or superseded; practitioner →CANCELLED as SPECIALIST,
  even for a Pro Host's old rentals). Listings never write back counters, `publishBlockedReasons`,
  `verificationRequestedAt` or `ownerEmail`; the verification document is a private `gs://` reference opened via
  `adminVerificationDocUrl` — never store a download URL on a listing. Token claims are read with
  `token.get('x', default)` (a missing claim makes a direct read throw). Profile `phone` is the user's
  self-declared WhatsApp number (editable); `email` must equal the Auth email.
- Listing saves go through `SpaceListing.keepingServerOwnedFields(current)` in `ProHostRepository`
  (isVerified, subscription fields, isOwnerSuspended, isOwnerPackageLapsed, ownerId). Never echo
  those from a wizard-built listing: rules deny any save whose changed keys include them, which
  blocked hosts with a lapsed plan from even saving drafts.
- Never `set(..., merge: true)` / batch-set a counter or flag onto a document that might not exist
  (a deleted listing): it recreates a ghost doc. Use `update()` and skip not-found
  (see `favoritesSync.ts`). Analytics and the admin feed ignore ownerless listing docs.
- Email: no mail server of ours (Hostinger retired). Firebase Auth's built-in mailer sends magic links (the app calls
  `sendSignInLinkToEmail` first); everything else goes through `sendEmail()` → `mail/{id}` → the Trigger Email
  extension (installed from the console, not in firebase.json). Never add an SMTP client back. `docs/EMAIL.md`.
- Unauthenticated email callables (`sendEmailOtp`, `sendSignInEmailLink`) must call
  `takeEmailSendSlot` (5/hour per address) and throw `HttpsError` (never plain `Error`, which
  reaches users as a generic internal error). Throwing inside a Firestore transaction rolls its
  writes back — return an outcome and throw after commit. Server-only collections
  (`email_otps`, `email_send_limits`) need explicit deny rules.
- In-app notification centre: `sendPushToUser` (`functions/src/lib/push.ts`) stores every push in
  `user_profiles/{uid}/notifications/{id}` (48 h `expireAt`, `read`) before sending FCM (with `notificationId`), even
  without a device token; `pruneExpiredNotifications` deletes expired ones hourly (collection-group index on
  `expireAt`). The app lists them from a live listener (`FirestoreService` → `ProHostRepository.onNotificationsSynced`),
  marks read in Firestore (owner may change only `read`/`readAt`), and "Open" routes like a push tap.
- Manage page occupancy (`ManageListingScreen.kt`) is date-aware: weekly strategies show the next 7 dates via
  `isCalendarDateLocked` limited to bookings whose term covers the date; monthly uses the booking's months. Cells are
  filled soft green (free) / soft red (booked) with no status words.
- Per-attendee Shift rooms: shifts carry `AVAILABILITY_MARKER_PRICE` (`RentalPricingConfigEditor` in availability mode
  and `AttendeePricing.markShifts` when the room is built) — otherwise `hasRealPrice()` fails and Save never enables.
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
- `expirePackages` decides per profile with `packages/expiryLogic.ts` (unit-tested): a Play subscriber is demoted
  only when Play says the subscription ended (or doesn't know the token); config/transient Play errors are skipped and
  retried next hour — never demote blindly. Cursor-paged, 540 s, deleted Auth users just get their expiry cleared.
- `retryPendingPlayActivations` queries only `needsAdmin == false` rows (composite index) and closes `needsAdmin` rows
  older than 4 days as `expired_unclaimed`. `parkPendingActivation` returns new/existing; admin pushes go out once.
- Admin Revoke Pro Host (`revokeProHostRole`) lapses the host's listings (`isOwnerPackageLapsed`) and marks the Play
  subscription `adminRevoked` in `subscriptions/{id}`, so `syncSubscription` never re-grants it; an admin Activate in the
  billing rescue panel clears the flag. An unchanged re-grant still finishes a half-done grant (lapsed listings, parked draft).
- Email bodies escape every user-supplied value with `esc()` (`lib/emailTemplates.ts`). The email-code link
  (`clickEmailOtpLink`) shows a confirm page on GET and consumes the code only on POST, because mail scanners open links.
- `grantProHost`/`removeProHost` return early for deleted accounts (no ghost profile, no RTDN retry storm) and write
  with `update()`; a PRO_HOST claim whose profile `role` mirror lagged is repaired on the next sync.
- Only `package_pro_mrr` grants (`isSupportedProduct`, `playCatalog.ts`). Retired plans sold by old app versions
  (`package_growth_mrr`, `package_enterprise_mrr`) are never granted or acknowledged automatically, in any path:
  activation, RTDN (it doesn't acknowledge an unlinked one either), retry or migration. They are parked `needsAdmin`
  (`unsupported_product`) with an `UNSUPPORTED_PRODUCT` admin push; an admin Activate (adminOverride link) honours
  them and their renewals. Otherwise Play refunds them after 3 days.
- Billing rescue (`billing/billingRescue.ts`, Admin › Packages + the user dossier): `billingHealthCheck`
  (probes subscriptionsv2 with a dummy token — 400/404 = credentials OK, 401/403 = Play Console access missing;
  RTDN heartbeat in `app_config/billing_health`: `lastRtdnAt` only from Play-published messages, `lastSelfTestAt` from the
  admin "Send server self-test" button → `billingRtdnSelfTest`, which proves topic → function. Play needs
  `google-play-developer-notifications@system.gserviceaccount.com` as Pub/Sub Publisher on `play-billing-rtdn`), `adminBillingPending` (parked + unlinked purchases, hours left
  before Play's 3-day refund) and `adminActivatePurchase` (Retry / Activate for an account, with explicit
  confirmation when the purchase is tagged for another account). A purchase tagged for another ProHost account is
  parked with `needsAdmin` (never silently dropped) and admins get an `OWNERSHIP_MISMATCH` push. An admin
  assignment is an `adminOverride` link in `play_purchase_links`, which `resolvePurchaseUid` and
  `activatePlayPurchase` honour before Play's account id. Runbook: `docs/MONETIZATION.md`.
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
- App Check is UNENFORCED on every Firebase service (2026-10-05, below 98% verified). Tester builds share one
  registered debug token (GitHub secret `APP_CHECK_DEBUG_TOKEN`). Re-enforce only at ≥ 99% verified —
  `docs/APP_CHECK.md`.
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
