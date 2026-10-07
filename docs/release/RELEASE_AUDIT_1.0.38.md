# ProHost 1.0.38 (versionCode 38) — feature map, release audit and Play production package

Built from `main` after commit `429d99a`, on 2026-10-07. Release notes are in `docs/release/1.0.38-notes.txt`.

## 1. Feature map

Legend: **UI** = screen / component · **Logic** = ViewModel / repository · **Server** = callable or trigger
(`functions/src`) · **Data** = Firestore collections (all covered by `firestore.rules`; anything else is denied)
· **Tests** = automated coverage.

### 1.1 Everyone (sign-in and account)
| Feature | UI | Logic | Server | Data | Tests |
|---|---|---|---|---|---|
| Sign in: phone SMS, Google, email magic link or 6-digit code | `LoginAuthScreen` | `AuthViewModel`, `FirebaseAuthService` | `assignInitialRole`, `sendSignInEmailLink`, `sendEmailOtp`, `verifyEmailOtp`, `clickEmailOtpLink` | `user_profiles`, `email_otps`, `email_send_limits`, `mail` | rules: profiles + server-only collections; `emailVerifiedRule.test` |
| Registration and profile completeness | registration form, `SpecialistProfileScreen` | `isProfileComplete`, `ProfilesRepository` | `assignInitialRole` | `user_profiles` | rules: email must match, protected fields |
| Verification: photo + verified phone (+ country/city for hosts) | `RequirementsSheet`, `KycScreen` | `canTransact` / `canHost` | — | `user_profiles` | unit tests |
| Notifications: push + 48 h in-app centre | bell / `DrawerDialogsHandler` | `onNotificationsSynced` | `sendPushToUser`, `pruneExpiredNotifications` | `user_profiles/{uid}/notifications` | rules: owner may mark read only; `push.test` |
| Analytics (opt-in) | `AnalyticsConsentDialog`, Profile › Privacy | `AnalyticsTracker` | `ga4.ts` | — | NIGHTHAWK analytics hygiene |
| Delete account (in-app + web page) | Profile › Delete account | `deleteOwnAccount` | `deleteOwnAccount`, account cleanup | all user data | rules + cleanup code |
| Legal, support, sign out | Profile › More | `SupportContact` | `legalDocumentPage` | — | — |

### 1.2 Specialist (bottom tabs: Explore · Saved · My Rentals · Profile)
| Feature | UI | Logic | Server | Data | Tests |
|---|---|---|---|---|---|
| Explore: map/list, search, filters, paging, "Search this area" | `DiscoveryScreen`, `ExploreOverlayHeader` | `DiscoveryViewModel` | — | `workspace_listings` (public) | Discovery unit tests |
| Listing page: photos, room tabs, host card with "Ask the host on WhatsApp" | `SpaceDetailsScreen`, `RoomCards` | `launchWhatsAppInquiry`, `selectRoom` | `listingShareLanding` | `workspace_listings` | — |
| Availability: live occupancy, real dates | `AvailabilitySheet` | `SpaceCalculationUtils`, `watchOccupancy` | `onBookingOccupancySync` | `booking_occupancy` | `bookingTerms.test`, rules: occupancy read-only |
| Booking request (strategy or per-attendee price), "Request sent" sheet | `RentalBookingDialog` | `submitBookingRequest`, `BookingsRepository` | `onBookingRequestCreated` (re-prices attendee bookings) | `booking_requests` | rules: create; `attendeePricing.test`; lifecycle test |
| Edit a booking (change request), withdraw, cancel | `MyBookingsScreen` | `BookingsRepository` | conflict guard (supersede), status notifications | `booking_requests` | rules: transitions; end-to-end lifecycle test |
| My Rentals: status stepper, agreement, Mark as Paid, calendar reminder, "Price updated" note | `MyBookingsScreen` | `acknowledgePayment`, `PaymentCalendar` | `onBookingPaymentAcknowledged` | `booking_requests` | rules: payment flags |
| Saved | `MyFavoritesScreen` | favourites | `favoritesSync` | `user_profiles`, listings counter | rules: counter +1 only |
| Premium upgrade (only publishing needs it) | `OwnerSubscriptionsScreen` | `BillingController`, `PlayBillingManager` | `verifyAndRestorePurchase` | `subscriptions` (server-only) | billing unit tests, `playCatalog.test` |

### 1.3 Pro Host (drawer: Hub · My Listings · Requests · Progress · Financials · Premium)
| Feature | UI | Logic | Server | Data | Tests |
|---|---|---|---|---|---|
| Listing wizard: rooms, photos, pricing (monthly, hourly, shift, day, per attendee), drafts, publish | `CreateListingDialog`, `SubdivisionEditorSection`, `RentalStrategyEditors` | `createNewSpaceListing`, `saveListingDraft`, `keepingServerOwnedFields` | `onWorkspaceListingPublishValidation`, display codes, `searchName` | `workspace_listings`, Storage | rules: server-owned fields; `legacyPricing.test` |
| Verification request with document | `ListingVerificationDialog` | — | `requestListingVerification`, `adminVerificationDocUrl` | listings + private Storage | rules: no download URL stored |
| My Listings + Manage page (performance, date-aware occupancy) | `OwnerHubScreen`, `ManageListingScreen` | `SpaceCalculationUtils` | — | listings, bookings | unit tests |
| **Change price** (Manage, request sheet, Progress card) with keep / now / next term | `ChangePriceSheet` | `PriceChange`, `changeSlotPrice` | `changeSlotPrice` (transaction + `PRICE_CHANGE` push) | listings, `booking_requests` | `priceChange.test` + `PriceChangeTest` (same cases), repository test, rules |
| Renting Requests: accept with agreement, decline, change requests | `OwnerIncomingRequestsView` | `acceptBookingRequest` | `onBookingAcceptConflictGuard`, status notifications | `booking_requests`, Storage | rules: host transitions; lifecycle test |
| Renting Progress: reminders, Mark as Paid, cancel, change price | `OwnerRentingProgressScreen` | `sendPaymentReminder`, `cancelAcceptedBooking` | `sendPaymentReminder` | `booking_requests` | rules: host cancel |
| Financials and stats | `OwnerAnalyticsScreen`, `OwnerHubScreen` | — | — | own bookings | — |
| Premium: monthly/yearly tiles, switch (to yearly = full price now + unused days), status banners, restore, redeem | `OwnerSubscriptionsScreen` | `BillingController` | `verifyAndRestorePurchase`, `playBillingRtdn`, `billingSyncJob`, `expirePackages`, `retryPendingPlayActivations` | `subscriptions`, `play_purchase_links`, `play_billing_pending` | `expiryLogic.test`, `billingRescueLogic.test`, `playCatalog.test` |

### 1.4 Admin (drawer: Admin Console · Analytics · Security ID)
| Feature | UI | Server | Tests |
|---|---|---|---|
| Search-first console: counts, search, user dossier, CSV export | `AdminConsoleScreen`, `AdminDossierSheet` | `adminCounts`, `adminSearch`, `adminUserDossier`, `adminExportUsers` | `adminQuery.test` |
| Users: suspend, grant admin, revoke Pro Host | `AdminUsersTab` | `setAccountSuspended`, `grantAdminRole`, `lookupUserForGrant`, `revokeProHostRole` | rules: suspended users blocked |
| Listings: verify, subscription flag, verification queue | `AdminListingsTab` | `setListingVerification`, `setListingSubscriptionActive`, `adminVerificationDocUrl` | rules |
| Packages: Force Upgrade, billing health, RTDN self-test, rescue (parked purchases) | `AdminPackagesTab`, `AdminBillingRescue` | `forceProHostUpgrade`, `billingHealthCheck`, `billingRtdnSelfTest`, `adminBillingPending`, `adminActivatePurchase`, `runBillingSync` | `billingRescueLogic.test` |
| Demo and data tools: purge demo, migrate legacy fields, backfills | `AdminDemoTab`, `AdminSchemaTab` | `purgeDemoContent`, `migrateLegacyListingFields`, `backfillDisplayCodes`, `backfillSearchNames`, `backfillEmailVerified`, `backfillProHostUpgradeDates` | — |
| Security / audit log, analytics | `AdminSecurityTab`, `AdminAnalyticsScreen` | `recordClientAuditLog`, `getAdminAnalytics` | rules |
| Pushes for every subscription change | — | `notifyAdminsOfSubscriptionChange` | — |

## 2. Audit results

### 2.1 Automated
| Check | Result |
|---|---|
| Cloud Functions build + unit tests | 52 / 52 pass |
| Firestore + Storage rules (emulator) | 31 / 31 pass |
| NIGHTHAWK (32 checks) | **0 critical, 0 high**; the mediums are known false positives (helper modules imported by other modules; "compileSdk < 34" while it is 37) plus one `!!` inside `runCatching` |
| Android build, unit tests, Lint (CI) | green on `429d99a` (see §4) |
| App ↔ backend: every callable the app calls is exported (36 callables) | pass |
| Every Firestore collection used has a rule; everything else is default-deny | pass |
| Every push `targetTab` opens a real tab; every push category is routed | pass |
| Every analytics event (61) is wired in `AnalyticsTracker` and documented | pass |

### 2.2 Fixed in this audit
1. **Play Photo & Video Permissions policy.** The app declared `READ_MEDIA_IMAGES` and asked for camera, photos and location at first launch. Play rejects apps that declare `READ_MEDIA_IMAGES` but only pick photos occasionally.
   - Fix: the camera and media permissions are removed (explicitly stripped from the merged manifest). Photos come from the system photo picker; "Take Photo" hands off to the camera app.
   - Launch now only asks for notifications. Location is asked in context, on the map.
   - This also fixes a crash: with CAMERA declared but denied, "Take Photo" threw a SecurityException.
2. "Take Photo" on a device with no camera app now shows a message instead of crashing.
3. Change price now works on listings not yet migrated off the legacy pricing fields.
4. Owner Hub utilisation no longer counts bookings whose term has ended.
5. Removed a dead one-time admin bootstrap function.

### 2.3 Known limits (acceptable for release)
- For bookings not priced per attendee, the total comes from the app and the server only bounds it (0–500,000). The host sees it before accepting, and Change price is recalculated server-side.
- When a host accepts one of two overlapping pending requests, the other stays pending. Accepting it later is blocked in the app, and if forced, reverted by the server.
- App Check stays unenforced until at least 99% of traffic is verified (`docs/APP_CHECK.md`).

### 2.4 Manual checks for the owner (internal testing track, Play-installed)
- [ ] Purchase monthly, switch to yearly, check the Premium status banners.
- [ ] Send a booking request, then accept it as host. The slot shows as taken for a second specialist account.
- [ ] Change price on a slot with a pending request and a tenant: check the Now / Next term / Keep previews, the push, and the "Price updated" note.
- [ ] "Take Photo" and "Choose file" in the listing wizard and in verification. No permission prompt should appear.
- [ ] Email sign-in for a returning user (link and code), then delete an account.

## 3. Play Console production package

### 3.1 Files (GitHub Actions artifact of "Build Play-Signed Official Release Bundle")
| File | Upload to |
|---|---|
| `app-release.aab` (versionCode 38, 1.0.38) | Production (or a test track) › Create new release |
| `native-debug-symbols.zip` | App bundle explorer › 38 › Downloads › Native debug symbols |
| `mapping.txt` | Optional (Crashlytics already has it) |

**Release name:** `1.0.38 (38)`. **Release notes:** paste `docs/release/1.0.38-notes.txt`; remove any language that isn't in your store listing.

### 3.2 "Apply for production" questionnaire
Replace **[fill in]** with your real closed-test figures. Google checks them against its own data.

**Part 1 — closed test**
- **How did you recruit testers?**
  - We invited our target market in Lebanon by WhatsApp and email, through a Google Group opted into the closed track:
    - workspace owners: clinics, therapy rooms, studios, coworking and meeting rooms;
    - professionals who rent space: therapists, trainers, consultants, tutors.
  - [fill in] testers stayed opted in for at least 14 consecutive days.
- **How easy was recruiting?** Neither easy nor difficult.
- **Engagement:**
  - Professionals browsed on the map and the list, filtered, saved favourites, sent and edited booking requests, and contacted hosts on WhatsApp.
  - Hosts created listings with rooms and pricing, managed availability, accepted or declined requests, changed slot prices, and went through Premium with Play test cards.
  - Crashlytics showed no crash clusters in the final week.
- **Feedback and how it was collected:** We collected it through a WhatsApp tester group, calls, Play private feedback and Crashlytics. Main points:
  - availability was hard to read at a glance;
  - hosts needed to change prices without re-editing the whole listing;
  - the email sign-in link failed for returning users;
  - the Premium page should show monthly and yearly clearly, with fair upgrade terms;
  - testers asked for fewer permission prompts.

**Part 2 — about the app**
- **Audience:**
  - Adults (18+) in Lebanon and the region: professionals who rent workspace by the hour, shift, day or month, and owners who rent out offices, clinics, studios and meeting rooms.
  - Not designed for children.
- **Value:**
  - One marketplace instead of phone calls and social posts.
  - Professionals get a live map of verified spaces, real-time availability and transparent prices, can request a booking in a few taps, and can message the host on WhatsApp.
  - Hosts publish spaces, manage availability, prices and requests, and track occupancy and revenue.
  - Browsing and booking are free. Only publishing needs ProHost Premium, sold through Google Play Billing.
- **Expected installs in year one:** 1,000 – 10,000.

**Part 3 — production readiness**
- **Changes made from testing:**
  - real booking dates, with slots booked by others shown as taken;
  - one-step change requests;
  - host "Change price" with Keep, Now or Next term for tenants, who are notified;
  - a WhatsApp inquiry button on listings;
  - fixed returning-user email sign-in;
  - the Premium page shows both plans, and upgrading to yearly gives a full year plus the unused days;
  - the camera and photo-library permissions were removed in favour of the system photo picker.
- **Why it is ready:**
  - every critical flow passed on the internal and closed tracks;
  - no open Crashlytics clusters;
  - unit, rules-emulator and functions tests plus the 32-check release audit pass, with 0 critical and 0 high findings.

### 3.3 App content (Policy › App content)
- **Ads:** No ads. `AD_ID` is removed.
- **Target audience:** 18+.
- **Data safety:**
  - **Collected:** name, email address, phone number, approximate and precise location (optional, for the map), photos (profile, listing and verification documents chosen by the user), purchase history (Play subscriptions), app interactions and crash logs (analytics is opt-in).
  - **Encrypted in transit:** yes.
  - **Deletion:** in-app "Delete account", plus https://pro-host.tech/delete-account.html.
  - **Shared with third parties:** none. Firebase and Google Play act as processors.
- **Permissions:** Internet, network state, location (fine and coarse, requested on the map), post notifications, vibrate, and Play Billing. No camera and no photo/video permissions. If the Photo & Video permissions declaration form was filled in for a previous version, it no longer applies.
- **Financial features:** none. The subscription is sold through Google Play Billing.
- **Privacy policy:** https://pro-host.tech/privacy.html.
- **Account deletion URL:** https://pro-host.tech/delete-account.html.
- **Content rating:** answer the questionnaire. Expect Everyone / 3+, with "users interact" and "digital purchases" flagged.
- **App access:**
  - All features need sign-in. Give reviewers a test account: email [fill in], with sign-in by the 6-digit email code or Google.
  - Publishing needs Premium; use a license-tester account.

### 3.4 Release settings
- **Staged rollout:** 20%, then 100% after 48 hours with no new Crashlytics clusters.
- **Countries:** as in your store listing, including Lebanon.
- **Before submitting, confirm:**
  - `package_pro_mrr` has both base plans **Active**;
  - the Maps key allows the Play app-signing SHA-1.

## 4. Build record
- CI on `429d99a` (run 37613732542): Android build and tests, rules tests, functions compile and live deploy all green.
- Release bundle: GitHub Actions › "ProHost - Build Play-Signed Official Release Bundle" on `main` (artifact `prohost-release-main-<run number>`).
