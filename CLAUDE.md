# ProHost — Agent Notes

## Google Play Billing

**Google Play is the only billing authority.** One subscription, `package_pro_mrr`, with a monthly and a yearly
base plan (known ids `pro-montly`/`pro-monthly`/`pro-month1`, live `pro-monthly1`, and `pro-yearly`) — `data/billing/PlayCatalog.kt` (app) and
`functions/src/billing/playCatalog.ts` (server). Plans are told apart by **kind** (`PlayCatalog.PlanKind`): the app
classifies Play's ProductDetails by recurring billing period (`PlayOfferText.kindOf` / `plansByKind`, P1M/P1Y), stored ids
by `kindOf`/`planInterval` (known ids, then "month"/"year" in the id). Never compare against an exact id list again —
a Play id that differed from the constant produced a third "Premium" tile and lost the old purchase token on switching
(double charge). Admin › Packages › Billing health lists the real base plans (id · state · period) from
`monetization.subscriptions.get`. Retired plans (`package_growth_mrr`, `package_enterprise_mrr`) are gone: the client
never reads, restores or sends them (`PlayCatalog.isSupportedProduct` in PlayBillingManager/BillingController), and every
server path ignores them before any ownership check, claim, parking or admin push (`activatePlayPurchase` first lines,
RTDN, retry, sync, rescue). Their one holder was converted to a permanent admin grant (maintenance task `retiregrowth`). There is no plan
catalog, no stored price and no admin package management: `package_plans` is retired (read-only for old
app versions), `grantPackageToUser` is gone. Never reintroduce local plans, prices or durations.

App uses **Billing Client v9.1.0** (`gradle/libs.versions.toml` → `billing`; Play requires v8+).

- Prices, periods, trials and "Save XX%" come only from the Billing SDK (`PlayOfferText`, per base plan:
  `preferredOffer(details, basePlanId)` → pure `pickOffer`: free trial, else deepest discount, else base plan).
  Checkout always passes the chosen base plan's offer token — picking one offer per product made the yearly plan
  unbuyable. Plan cards render `PlayOfferText.summary()` (`OfferSummary`, pure `summarize`, unit-tested in
  `PlayOfferTextTest`): with an offer the headline is the price paid now, the regular price struck through,
  "% OFF" ribbon, "for your first month/year", "then $X / month"; `PlanExplainer` (box under the CTA for the
  selected tile, either plan) states today's price, the date the regular price starts (`regularPriceStartsAt`) and how
  to cancel — "HOW YOUR OFFER WORKS" with an offer, "HOW IT WORKS" (plain renewal terms) without one. Members
  switching plans never see offers. An offer edited in Play Console that "doesn't show" is almost always
  eligibility: Play returns "new customers" offers only to accounts that never had the subscription — check the live
  catalog with Maintenance task `playcatalog` or Admin › Packages › Billing health (offers listed per base plan).
  Only the selected plan card is orange (ribbon, price); the other card and the explainer use steel.
  Promo card (e.g. PROHOST2026) is **Play config only**: an offer tagged `promo` + `code-…` (+ `ends-YYYYMMDD`,
  `early-launch`) — `data/billing/PlayPromo.kt` (unit-tested) reads it from ProductDetails; no Firestore doc, no server counter
  (`promo_campaigns` is retired, rules deny all; `playcatalog` deletes the old doc). "Redeem code" (red dot while live) opens a
  sheet with `PromoCountdownCard` + the one-time-code field. A promo offer must sit on an ACTIVE base plan: Play Console
  can't resume an offer whose base plan is inactive ("An unexpected error has occurred … (5A39FA1E)"); Maintenance
  `playofferfix` (dry run; code `apply`) copies it onto the live monthly plan and widens claimed offers from "any
  subscription in this app" to "this subscription" (`billing/playOfferFix.ts`; lint flags both). Checkout offer = `OfferChoice` (`PlayBillingManager`): plan cards
  `Preferred` (`preferredOffer` skips `promo` offers), switches `BasePlanOnly` (regular prices shown ⇒ charged), promo
  `Specific(offerId)`; a missing offer aborts with a billing message. `lintOffers` (`playCatalogLive.ts`, Maintenance
  `playcatalog` step `playOfferLint`) flags Play Console slips (≥ 90 % off, promo tags, the website's offer claims in `offerClaims.ts`: monthly 4 months free, yearly 50 % off year one — the static pages, `public/admin.html` email template, marketing README/voiceover scripts and videos must state them; `offerClaims.test.ts` enforces it). Monthly ↔ yearly passes the old token
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
  linking to the Play subscription center (`sku=package_pro_mrr`); compact gradient hero (Play's product
  `description` as the non-member subtitle) + one chip row (Manage · Redeem code · Orders · Restore); both base plans
  as side-by-side `PlanTile`s (yearly selected by default) — a base plan Play didn't return shows
  `UnavailablePlanTile` ("Not available"), never an invented price; one CTA for the selected tile with its terms
  under it; always exactly two `PlanTile`s (Monthly · Yearly by kind, yearly with "≈ x / month" and SAVE %), current
plan + recurring price + date;
  "Switch to …" with Play's replacement modes (to yearly `CHARGE_FULL_PRICE`: full yearly price now + unused monthly
  days credited as extra time, never `CHARGE_PRORATED_PRICE`, which keeps the monthly renewal date; to monthly `DEFERRED`);
  trial disclosure; `SubscriptionTermsFooter` (renewal, cancel, "only publishing needs Premium", Terms &
  Privacy). Profile has a "Manage subscription" settings link. The Owner Analytics listing-health card reads the server mirror `isOwnerPackageLapsed`, never the admin listing toggle `isActiveSubscription`. Play's transactional in-app messages show on
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
  Screenshots/screen recording are allowed on purpose (owner, Oct 2026): never re-add `FLAG_SECURE`.
- People see server-assigned display codes, never Firebase UIDs/doc ids: `U-`/`L-`/`D-`/`B-` +
  6 Crockford chars (`functions/src/ids/displayCodes.ts`, registry `display_codes/{code}`).
  In Kotlin use the `publicCode` extensions. `displayCode` is server-only (rules protect it);
  the client never writes it at top level, only echoes `Subdivision.displayCode` because the
  subdivisions array is saved whole. Doc ids stay the internal keys.
- Price units: `SpaceCalculationUtils.strategyUnitLabel` / `rateUnitLabel` /
  `lowestPriceFor`. Never hard-code "/mo" (WhatsApp texts used to).
- User-initiated `ProHostViewModel` actions must surface thrown exceptions via
  `reportFailure(appContext, e, R.string.fallback)` (a string resource: shown translated, `app_error` area from its
  English text), not only `Log.e`. Explore's load error comes from
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
- Verification rules (the only two): `AppUser.canTransact()` = photo + WhatsApp number, checked in place when a
  specialist sends a booking request; `AppUser.canHost()` = that + country and city, checked at Premium checkout and after
  a plan activates. `hasWhatsAppNumber()` (profile `phone` in international form, `isPlausibleE164`) alone gates the Pro
  Host workspaces. My Rentals, browsing and Saved are never gated; there is no `isKycComplete` and email is never a
  requirement. **There is no SMS code anywhere** (owner, Oct 2026: Firebase phone verification never delivered, in any
  country): the number is self-declared, entered on the registration form or in `WhatsAppNumberSection` (`KycScreen.kt`,
  saved by `ProHostViewModel.saveWhatsAppNumber`; it always prefills from the profile's `phone`). The registration form
  requires it and offers Android's Phone Number Hint (`ui/components/PhoneNumberHint.kt`: system SIM-number picker, no
  permission; opened once when the form appears, and "Use this phone's number" in both places) — a Google account never
  exposes a phone number. Never reintroduce `verifyPhoneNumber`/phone sign-in, and never use
  `isVerified` (phone-auth claim, legacy accounts only) as the phone check. Missing steps are collected by the one shared
  `RequirementsSheet` (photo, `WhatsAppNumberSection`, and the address step with `requireAddress`), opened over the booking
  page, Premium or Profile's `KycCompletionBanner`.
- Email verification is server-mirrored: `assignInitialRole` sets `emailVerified` on every sign-in when
  `emailVerifiedByToken` (email_verified claim or Google provider, `auth/emailVerifiedRule.ts`); the email
  code path marks the Auth user verified; `backfillEmailVerified` (admin) fixes existing profiles.
- Specialist routing: specialists have no drawer and no menu button: one shell with bottom tabs Explore · Saved ·
  My Rentals · Profile (`SPECIALIST_BOTTOM_TABS`), Explore as the centre. Legal, Contact Support
  (`ui/util/SupportContact.kt`) and Sign out live in Profile › More; full-screen destinations (Premium) get a
  back arrow (`ProHostFullScreenTopAppBar(isBackNavigation)`), other tabs a bar without ☰ (`showMenu = false`).
  Pro Host / Admin keep header + drawer. There is no "Saved only" filter and no drawer Billing dialog.
- Pro Host bottom tabs: Listings · Explore · Rentals · Profile (`PRO_HOST_BOTTOM_TABS`); Renting Progress is a drawer
  destination (`PRO_HOST_FULLSCREEN_TABS`, still reachable by id from pushes and notifications).
- Bottom nav is a floating pill (`ProHostBottomNavBar`): active tab = filled pill in the role accent (Steel Blue
  specialist, orange Pro Host) with its one-word `AppNavTab.shortLabel` (never truncated; the other tabs are icons).
  It overlays the content (not `Scaffold.bottomBar`); screens leave `LocalBottomNavInset` free at their bottom (the
  shell pads non-Explore tabs; Explore/map read it). It collapses to a handle on every page, both roles: on Explore's
  map via `DiscoveryViewModel.navExpandedOnMap` (re-collapses on map gesture / tab pick), elsewhere via the shell's
  `navExpandedOffMap` (tucks away on scroll down, returns on scroll up — nested-scroll connection on the shell).
- Page backgrounds are white: `Modifier.proHostScreenBackground()` (`ui/theme/Theme.kt`) = the theme gradient plus a
  faint dot texture in light theme only (one shader tile, not per-dot drawing). The shell Scaffold is transparent over it;
  drawer and app bars use `surface` with tonal elevation 0 (no blue tint); cards keep their 1 dp outline. Never
  `.background(premiumBackgroundBrush())` a screen directly.
- Sign-in is email (magic link, 6-digit code fallback) or Google only; `FirebaseAuthService` has no phone methods. Legacy
  phone-auth accounts keep their Firebase `phone_number` claim (rules still tie `isVerified` to it); maintenance `smsauth`
  remains only as a diagnostic.
- Email sign-in links are handled every time (`AuthViewModel.onEmailLinkArrived`; never a session-long "consumed" flag —
  that blocked every returning user's link after the first sign-in). The saved address (`pending_email_link`) is read with
  `peekPendingEmailLink` and cleared only after a successful sign-in; a link without one asks for the email
  (`linkAwaitingEmail` dialog); `restorePendingEmail` refills the address after process death so resend/code never send "".
- Email sign-in layer 1 (`sendSignInLinkToEmail`) returns a `Result`; its Firebase error code is logged as
  `auth_error(method=email_link_native)` before falling back. Diagnose delivery with the Maintenance workflow
  task `emailauth` (Auth config, mail queue states, send limits).
- Brand blue is Steel Blue `#2B5A8C` (dark `#93B8E0`, `SteelBlue` in `Color.kt`); never reintroduce `#246BEE`. Tapping a room in Explore opens
  the listing on that room's card (the sheet opens only from Availability / Check availability); after a request the "Request sent" sheet offers "View request" (opens My Rentals
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
- Explore draws its own header (`ExploreOverlayHeader`; no shell app bar for any role), one row: search top-left (Pro Host /
  Admin get a ☰ control before it, `onOpenMenu`), the Map|List toggle top-centre wrapped in one pill with the brand logo and
  filters top-right. Explore cards: 16:9 photo, two-line title, "Availability" opens the listing with its sheet up
  (`onOpenAvailability`). No notification bell on Explore (notifications are the bell of the other tabs'
  app bar). Search is a draft applied only on ✓ / IME Search and opens a full-width field under the row. Transparent with
  floating controls on the map; on the list it has the list background and sits above it in the layout (never
  overlapping). Map results follow `searchedBounds`, updated only by the "Search this area" pill after a gesture pan/zoom.
- Profile identity card (`DrawerIdentityCard`): one-word 3D role tag (Specialist steel blue · Host orange · Admin gold,
  `RoleTag3D`), no separate "phone verified" tag (verification shows as the avatar and name checks). The specialist's
  "Become a Pro Host" button carries a "FOR SPACE OWNERS" tag.
- The listing page's bottom bar is `DetailsBookingBar` (`RoomCards.kt`): room chip + live availability, price and the
  Availability action (Preview mode for hosts/admins) — no Message button. WhatsApp is the host card's "Ask the host on
  WhatsApp" button under the photos (general inquiry via `launchWhatsAppInquiry(context, space)`; the number is never
  printed; hidden for hosts/admins).
- The availability sheet is `ResizableBottomSheet` (`ui/components`): a standard Material `ModalBottomSheet` (via
  `ProHostBottomSheet`, half + full stops, pinned header, scrolling body) like every other sheet. Never resize a sheet's
  content height while it is open — that fought Material's positioning and slid the sheet's bottom away.
- Retired fields: `SpaceListing.rentalFormulas` and `subscriptionExpiryMillis` (listing and profile) are gone
  from the models. Pricing lives only in `pricing` / `Subdivision.pricing`; a not-yet-migrated document is still
  read through `RentalPricingConfig.fromLegacyFormula` (app) / `pricingFromLegacyFormula` (functions,
  `listings/legacyPricing.ts`, unit-tested — keep both identical). Admin › Demo tab › "Migrate Legacy Listing
  Fields" (`migrateLegacyListingFields`) writes `pricing` and deletes the old keys. `RentalFormula` itself stays:
  bookings record one (`representativeFormula`).
- Backend flows are end-to-end tested in the emulators (`tests/e2e/flows.test.js`, CI job "End-to-end Backend Flows",
  gates the deploy): request → code + host push, accept → occupancy, conflict guard, accepted edit, Change price,
  per-attendee auto-reject, withdraw/cancel pushes. firebase-tools proxies even localhost through `HTTPS_PROXY`, so in a
  proxied sandbox run it in CI, not locally. Production readiness: Maintenance task `prodcheck` (deployed functions vs
  exports, indexes, Cloud Billing, functions answering, public pages, audit); `authorphans` explains Auth users with no
  profile (abandoned Google registrations: the profile is created only when the registration form is submitted).
  Auth users with **no provider** in the console are email 6-digit-code sign-ins (`verifyEmailOtp`/`clickEmailOtpLink`
  create them with the Admin SDK and sign in by custom token) — expected. Inspect one account with Maintenance `authuser`
  (uid in `code`): Auth record, profile, audit log, sign-in calls around creation and a verdict; `audit` lists sign-in
  methods and any provider-less account that is not a verified-email code sign-in.
  A deploy failing with Secret Manager "requires billing to be enabled" means the GCP billing account lapsed — every
  function bound to a secret (`assignInitialRole`) then 500s; re-enable billing and redeploy.
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
- Bookings have a real term: `startDate` is always an ISO date (never a preset label like "Next Monday"), so
  `SpaceCalculationUtils.bookingTerm/hasEnded/termsOverlap` (mirrored in `functions/src/bookings/bookingTerms.ts`,
  unit-tested) stop ended bookings locking slots and bookings on different dates clashing. Legacy label start dates are
  "unknown term" (ongoing). Specialists see slots other people hold through `booking_occupancy/{bookingId}` — a
  person-free projection of ACCEPTED bookings kept by `onBookingOccupancySync` (readable by any signed-in user, server-
  written; maintenance task `occupancy` rebuilds it); screens merge it with the viewer's own bookings
  (`ProHostViewModel.watchOccupancy`). An accepted edit's original booking is released by the server in the conflict
  guard's transaction (`supersededBy`), never by a second client write. Auto-rejected requests (`rejectedBySystem`) never
  reach host lists/stats; withdrawn requests and admin cancels notify the host; edits read "Change request".
- Pro Host **Change price** (`ui/components/ChangePriceSheet.kt`; Manage page card above Availability, request detail
  sheet, Renting Progress card): listing → division → slots with prices → new price → per affected pending/accepted
  booking Keep · Now (every remaining date/month from today) · Next term (1st of next month for monthly, next Monday for
  dated bookings; undated hourly/tier units apply on renewal). Only the `changeSlotPrice` callable
  (`listings/changeSlotPrice.ts`) writes it — listing price + booking `totalAmountUsd`, `lastPriceChange`, `priceChanges`
  (rules deny clients) — and pushes each specialist `PRICE_CHANGE` → My Rentals. Slots are `SlotRef(scopeId, kind, key)`;
  matching/re-pricing is `ui/util/PriceChange.kt` mirrored in `bookings/priceChange.ts` (shared test cases — keep identical).
  Per-attendee rooms change their tiers, not slot markers.
- Manage page occupancy (`ManageListingScreen.kt`) is date-aware: weekly strategies show the next 7 dates via
  `isCalendarDateLocked` limited to bookings whose term covers the date; monthly uses the booking's months. Cells are
  filled soft green (free) / soft red (booked) with no status words.
- Per-attendee Shift rooms: shifts carry `AVAILABILITY_MARKER_PRICE` (`RentalPricingConfigEditor` in availability mode
  and `AttendeePricing.markShifts` when the room is built) — otherwise `hasRealPrice()` fails and Save never enables.
- Permission prompts look native: runtime permissions are the system dialogs; every in-app pre/post prompt or consent uses
  `SystemStylePermissionDialog` (`ui/components/PermissionPrompts.kt`, Android 12+ permission-dialog look: centred icon,
  "Allow **ProHost** to …?", stacked equal-weight choices). Location goes through `rememberLocationAccess` (rationale →
  system prompt → "Open settings" when blocked for good → Play services' "turn on location" dialog), never a toast.
  The notification prompt waits for the first-launch analytics choice (`MainActivity`), so prompts never stack.
- No camera / photo-library permissions (Play Photo & Video Permissions policy): the manifest strips CAMERA,
  READ_MEDIA_IMAGES/VIDEO and READ_EXTERNAL_STORAGE (`tools:node="remove"`); photos come from `PickVisualMedia` /
  `GetContent`, "Take Photo" hands off via `TakePicture` (needs no permission when CAMERA isn't declared — declaring it
  makes the intent throw for users who denied it). Launch asks only for notifications; location is asked on the map.
- Website (pro-host.tech, `public/`): `index.html` is the Premium-conversion landing page (every CTA goes to the smart link
  `/get?utm_*`; `/ig /fb /wa /li /qr /app` are tagged redirects in firebase.json). Contact + newsletter forms call the
  `websiteContact` / `websiteSubscribe` callables (`functions/src/website/`, plain fetch, honeypot + per-IP/address limits)
  → address book `mailing_list/{sha256(email)[0:32]}` + `mailing_messages`; contact requests are emailed to
  admin@pro-host.tech (reply-to the visitor). `/admin` (footer link) is the code-locked console: the code is checked only
  server-side (`mailingAdminLogin`; salted scrypt hash in `app_config/mailing_admin`, set/rotated by Maintenance task
  `mailingcode` — never put the code in client code or docs), 12 h sessions in `mailing_admin_sessions`. Console: Excel
  export (SheetJS), campaigns to `subscribed == true` only, one `mail` doc per recipient from noreply@pro-host.tech with a
  personal unsubscribe link + RFC 8058 List-Unsubscribe headers, admin BCC'd once per campaign, progress from the
  extension's `delivery.state` (`mailingAdminCampaign`, Retry failed). Template: `website/mailTemplates.ts`
  (unit-tested). `/unsubscribe` (→ `mailingUnsubscribe`) confirms on GET and unsubscribes on POST. Live check: Maintenance
  task `mailingsmoke` with the code. All four collections are server-only (rules + rules tests).
- SEO (pro-host.tech): ProHost is open to every market, so the website names **no country or city**: no "Lebanon"/"Beirut" in
  slugs, titles, copy, JSON-LD (`areaServed`, `addressCountry`) or the OG image. Search landing pages (`/clinic-room-rental`,
  `/therapy-room-rental`, `/meeting-room-rental`, `/training-room-rental`, `/studio-rental`, `/office-space-by-the-hour`,
  `/list-your-space`, Arabic `/ar`) are generated — edit `scripts/website/seo-pages.js` and run
  `node scripts/website/build-seo-pages.js` (never hand-edit the HTML); shared CSS `public/landing.css`. The retired
  `-lebanon` / `-beirut` slugs (`LEGACY_SLUGS`) are 301-redirected in `firebase.json`; `sitemapXml.test.ts` fails if one
  comes back or a sitemap page has no file/canonical. Hosting uses `cleanUrls`. `/sitemap.xml` is the `siteSitemap`
  function (static pages + publicly browsable listings, `website/sitemapXml.ts`, unit-tested — add new pages to
  `STATIC_PAGES`); `/listing/{id}` (`listingShareLanding`) is indexable only for listings passing `isPublicListing`, with
  Place/BreadcrumbList JSON-LD whose place comes from the listing itself (`listings/listingLocation.ts`: stored
  country/city win; only legacy blank-country listings inside Lebanon count as Lebanon, like the app's `effectiveCountry`).
  The legal pages (terms governing law and arbitration seat, privacy) are a separate, deliberate decision.
  Share cards: `scripts/website/build-og-images.js` renders 1200x630 previews in the home-hero look (`public/img/og-hero.jpg` for `/`, `/ar` and policy pages, `public/img/og/<slug>.jpg` per landing page; commit the JPGs after changing a headline). Titles stay <= 60 chars, descriptions <= 160. The sitemap carries the en/ar hreflang pair.
  `/partner` (generated by `partner()` in `build-seo-pages.js`, noindex, not in the sitemap) is the strategic-partner offer linked from the outreach email's closing line; its form posts `websiteContact` with `topic: "partner"` (tagged subject, X-ProHost headers, address-book tags; `docs/EMAIL.md`).
  Utility pages (`/get`, `/admin`, `/redeem`, `/emaillink`) are noindex. Maintenance `prodcheck` step `seo` verifies the
  sitemap pages and the legacy 301s live.
  Play screenshots: `play/screenshots/` (rendered from `source/shots.html`); listing text: `play/store-listing/`.
- Instagram automation (@prohost.app, `functions/src/instagram/`, `docs/INSTAGRAM.md`): `igWebhook` (`/ig-webhook`) → pure `engine.ts` →
  `graph.ts` (Instagram login API, fixed version). Starts and stays `dry-run` until the sheet's Status `mode` is `live`; `kill_switch` stops sends.
  Rules live in the Google Sheet "ProHost IG Automations" (`igSheetSync` mirrors Rules/Status in, Log/Contacts out); the IG Control Room artifact
  edits that sheet (an artifact can't call functions). Secrets only via GitHub secrets + Maintenance `igsetup`/`igsmoke` — never in chat/code/logs;
  collections `ig_*` are server-only. The engine's matching is mirrored in the artifact Simulator (keep both identical; `engine.test.ts` has the cases).
- AI DM agent conversation flow (`aiAgent.ts`, `aiFlow.ts`, `aiRuntime.ts`, `aiOffers.ts`, `aiAccountInsight.ts`, `aiSweeper.ts`): how-to questions are answered
  like a person, as separate DMs (`messages`, up to 3, pause between them, buttons on the last): greet by first name (Instagram profile fetch,
  fallback "Hey there"), ask permission to look up the account by registration email ("no" = new), then guide. An email sent in reply to that
  question is the consent for ONE read-only lookup (`lookupInsight`: role, Premium, listings, rentals, profile gaps); the email is unverified,
  so facts are never recited back and "not found" reads like "no" (no probing); only a coarse tag (`insight`) is kept on `ig_threads`.
  Offers/prices come ONLY from `liveOffersText` (Play via `readPlayCatalog`, cached 15 min in `app_config/instagram_offers`, promo/upgrade
  offers excluded); if unavailable the agent quotes nothing. Silence: `igFollowUps` (every minute) sends one nudge 2 min after our last
  question and a goodbye 2 min later, only inside Meta's 24 h window; thanks/ok/bye (`isExitIntent`) ends the chat with a warm goodbye
  without a model call. Never reintroduce hard-coded offer text in the knowledge or prompt.
- Comment agent (`commentClass.ts`, `aiComment.ts`, `commentDecide` in `aiRuntime.ts`, poller `commentPoller.ts`/`commentFeed.ts`): a public comment no
  keyword rule matched is classified by intent without a model (positive / question / negative / spam / neutral, en·ar·fr·es). Positive: public
  thanks + at most ONE opening DM per person per day (private reply, Status `ai_comment_dm`). Question: public "see your DMs" + the answer as the one
  private reply Instagram allows per comment (model, live offers), saved as the first turns of `ig_threads` so the DM chat continues. Negative: nothing
  sent, never liked, event status `flagged` + admin email (`Decision.flag`). Status `ai_comments` turns the agent off. Instagram's API has NO endpoint
  to like a comment: never promise likes. `igCommentPoll` (every minute) reads the 5 latest media's comments from the API and feeds them through
  `handleEvent` (dedupe by `c_<id>`), so comments are caught even when the unpublished Meta app delivers no webhook; first run only sets a baseline;
  health in `ig_stats/comment_poll`, `igsmoke` and the sheet row `health_comment_poll`.
- Instagram feed/Story publishing: the grid style (flat navy/off-white, checkmark logo, Inter ExtraBold headline, one orange rule + block) is documented in `marketing/README.md`
  ("Instagram grid"). Assets live in `public/social/`, captions in `marketing/social/captions.json`, publishing goes only through Maintenance `igpublish`
  (idempotent via `ig_stats/published`); keep the light/dark alternation, no prices/offers in graphics unless they match `offerClaims.ts`.
- Payment reminders go to the user's own calendar via `PaymentCalendar` (insert intent, monthly
  RRULE) — no calendar permission, no Google Calendar API.

## Design system and languages (1.0.43)

- Follow `.claude/skills/prohost-design/` for any UI change; verify with the Design Screenshots workflow (light, dark,
  small = 360 dp phone at the text cap, RTL, Arabic) via `.claude/skills/prohost-design/scripts/fetch_shots.py`. Plan:
  `docs/design/UI_AUDIT.md`.
- Text size follows the phone only up to Android's "Large" step: `FontScaleCap` (1.15, `MainActivity.attachBaseContext`,
  owner's choice — text stays adequate and fits small phones). Never remove the cap or design for larger scales.
- Tokens only: `MaterialTheme.colorScheme` (surface ladder defined), `Radius.Xs/Sm/Md/Lg/Full`, `Elevation.level0–3`,
  `Spacing`, typography (Manrope headings, 12 sp floor). NIGHTHAWK "Design System Tokens" flags Toasts in UI, text under
  12 sp and literal radii/elevations.
- One of each: `ProCard(variant, onClick)` (ModernCard/ProSurfaceCard delegate to it), main action colours
  `proPrimaryButtonColors()` / `ProPrimaryButton` (orange), `ProHostAlertBanner`, `ProHostCountBadge`/`ProHostDotBadge`,
  `PhotoCarouselArrow` (48 dp), dialogs on 28 dp `surfaceContainerHigh`.
- Feedback goes through `UiMessages.show(context, text, actionLabel?, onAction?)` → Snackbar above the nav pill
  (`ProHostSnackbarHost`; Toast only when no shell is on screen). Reversible actions offer Undo.
- Page changes fade/rise in the shell (`rememberReducedMotion()` snaps when animations are off).
- Strings: user-facing text lives in `res/values/strings.xml` with Arabic in `res/values-ar/strings.xml` (keep keys and
  placeholders identical). In composables use `stringResource`; elsewhere `uiText(R.string.x, args)` (`ui/util/UiText.kt`,
  initialised in `ProHostApplication`). Fixed English labels from the data layer (enum `displayName`, Play badges, shift and
  day names) are translated only at display sites with `localizedLabel()` — stored values, comparisons and analytics stay
  English. New strings: add both languages; `scripts/i18n/` re-runs the extraction passes. Admin-only tools stay English.
- Per-app language: `res/xml/locales_config.xml` (en, ar) + Profile › More › Language.
- Counted sentences are `res/values*/plurals.xml` (Arabic needs zero/one/two/few/many/other): `pluralStringResource` in
  composables, `uiPlural(R.plurals.x, count, args)` elsewhere. Resource strings that end or start with a space use
  `\u0020` (aapt trims plain spaces, which glued joined sentences together: "AllowProHostto"). Never split a sentence into
  two resources mid-word. One avatar: `ProAvatar(size)`. Phone numbers render LTR (`PhoneNumberField`).
- Text fields are `InputField` (never a raw `OutlinedTextField` outside admin tools). The listing wizard
  (`CreateListingDialog`) is a full-screen page with `WizardStepBar`; Back steps back. Dark mode has its own splash art
  (`drawable-night-*`, `values-night/colors.xml`).
- The Design Screenshots harness covers screens and every sheet/dialog/form (`OVERLAYS`, `TAPS` in
  `DesignScreenshotTest`); add a new overlay there when you add one.

## Google Play releases

- `release-bundle.yml` (manual): `play_track` none = build only; probe = read Play tracks (no build, no change);
  internal / production = build, upload the `.aab` + R8 mapping + native symbols, release with
  `docs/release/<version>-notes.txt` (a listing language without its exact note takes the same base language, e.g.
  en-GB ← en-US; the store listing currently has only en-GB) — production as a staged rollout
  (`rollout`, default 0.2). Script: `scripts/release/play-publish.js`. It uses the CI service account, which must be invited
  in Play Console › Users and permissions with release permissions; a 403 names the account. A version code Play has
  seen before is refused: bump `versionCode` for every upload.

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
- Play app-signing fingerprints live only in the repo secrets `PLAY_APP_SIGNING_SHA1` / `PLAY_APP_SIGNING_SHA256`.
  The release workflow fails unless that SHA-1 is a `certificate_hash` in `app/google-services.json` (registered in
  Firebase; Google sign-in and phone-auth app verification need it). Never write the values into code or docs.
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
- A function whose Cloud Run service still binds a secret the code no longer declares (the retired
  `HOSTINGER_SMTP_API_KEY`) fails every deploy with "Permission denied on secret" and keeps running old code — this
  froze the email callables on Hostinger SMTP until Oct 2026. Maintenance task `clearstalesecrets` removes such bindings.
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
- Only `package_pro_mrr` exists (`isSupportedProduct`). Retired plans are ignored everywhere with no admin alert; the client
  also skips purchases tagged (`obfuscatedAccountId`) for another ProHost account, so a shared device never raises
  OWNERSHIP_MISMATCH for someone else's purchase.
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
- NIGHTHAWK's "Orphaned Module" MEDIUMs (billingHelpers, purchaseLinks, playCatalog, playCatalogLive, offerClaims, sitemapXml, priceChange, …) and "compileSdk below 34" are
  false positives (both are imported by other modules; compileSdk is 37), as is "Screen Loading State" on
  `SpaceDetailsScreen` (the nav graph opens it only with an already-loaded listing).
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
- Screens: the shell's page name comes only from `analytics/ScreenNames.kt` (never a raw tab id); every sheet, dialog or
  step calls `TrackScreen(name)` (`ui/components/TrackScreen.kt`), which stacks on the page and re-reports the page when it
  closes. Funnel: `begin_checkout` on the listing page's Request tap, `requirements_shown`/`kyc_start` from
  RequirementsSheet. Autosaves and camera moves are never events.
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
