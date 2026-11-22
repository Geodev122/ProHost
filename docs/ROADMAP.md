# ProHost roadmap: to 100 % (Android + iOS in production, admin console on the web)

Updated 22 Nov 2026, 17:45 UTC. Status keys: ✅ done · ⏳ running · ◐ partly done · ☐ to do · 👤 needs the owner.
Each task has an ID (e.g. **B5**) so progress can be reported against it. Detail per unit: `docs/migration/PROGRESS.md`;
owner items: `docs/OWNER_TODO.md`; history: `docs/OPS_LOG.md`.

## Where we are

| Track | State | What finishes it |
|---|---|---|
| Android | live on Play: 1.0.46 · internal track: 1.0.50 (22 Nov) | owner test → integration audit → staged production |
| iOS | TestFlight 21 uploaded, 22 building; App Store listing, prices and both plans ready to submit | device test, real screenshots, review package, Add for Review |
| Admin console (web) | live at pro-host.tech/console: 7 tabs + Reports + Diagnostics + Bookings | Pro Host tools, shell items, owner walk-through, Android admin removal |
| Backend | live; daily Play and App Store syncs; quota-safe deploys | retire the old notification pruner, App Check decision, monitoring |

Audit of 22 Nov: NIGHTHAWK 0 critical, 1 high (stale path: `AuthFlow.kt` moved to `:shared`, the check still looks in `:app`;
the shared code does refresh the token), 66 medium (64 known "Orphaned Module" false positives, compileSdk 37 read as < 34,
Splash "no loading state"). Admin console: 70 parity items, code exists for 8-36, 38-57, 59-65; 0 walked through by the owner.

---

## The finish line: every remaining task

### A. Build, CI and checks (Claude)
- ☐ **A1** NIGHTHAWK paths for the KMP layout (`shared/src/commonMain/...` as well as `app/`): the token-refresh, screen-state and
  orphan checks read the moved files; mark the known false positives; target: 0 high. *Done when NIGHTHAWK prints 0 critical / 0 high.*
- ☐ **A2** Parity Gate warm-up: retry the Robolectric download up to 3 times (2 shards failed on 22 Nov), like the console's cache repair.
- ☐ **A3** `ADMIN_PARITY.md`: tick the **Web** column for every item already built (its legend: Web = built), so the walk-through list is honest.
- ✅ **A4** Console builds repair a broken Gradle cache; quota-redeploy step no longer fails when no function changed (22 Nov).

### B. iOS to the App Store
- ⏳ **B1** TestFlight build 22 (Apple revoke, admin notice, analytics consent, sounds/geocoding/PDF crash fixes). *Claude reports when it is in TestFlight.*
- ☐ 👤 **B2** Install build 22; buy each plan once with a sandbox account (1 listing monthly, Unlimited yearly); restore; cancel in Settings.
- ☐ **B3** After B2: run Maintenance `applesync` (proves the In-App Purchase key against Apple), check the grant, the listing limit
  (second publish refused on 1 listing) and the App Store notification log. *Done when applesync shows synced ≥ 1, failed 0.*
- ☐ 👤 **B4** Device pass (owner + Rewa + Rony), checklist: sign-in with email, Google and Apple · Explore map and list · listing page,
  dated booking request · push arrives and opens the right screen · links from pro-host.tech open the app · Arabic + dark mode ·
  Premium plan switch · delete account (Apple account shows Apple's sheet). Report anything that differs from Android.
- ☐ **B5** Fixes from B4, each in a new TestFlight build.
- ☐ **B6** Real iPhone screenshots (6.9", EN + AR): a CI job runs the app in the simulator with demo data, captures the same 8 screens
  as the mock-ups and uploads them with `asc_prepare` section `screenshots` (replaces the mock-ups, which Apple does not accept for review).
- ☐ **B7** App Review package: two demo accounts (Specialist, Pro Host with a listing) created by a Maintenance task, passwords as
  GitHub secrets; review notes with sign-in steps, the report/block feature, the plans and how to cancel; sign-in info entered in App Store Connect.
- ☐ **B8** Attach the final build to version 1.0 and the two subscriptions; readiness report shows every line OK.
- ☐ 👤 **B9** Approve the screenshots; press **Add for Review**. Claude answers any App Review message the same day.
- ☐ **B10** After approval: phased release (7 days), Crashlytics and the App Store daily check watched for a week.

### C. Android to production
- ☐ 👤 **C1** Test 1.0.50 (internal track): Premium plan switch (1 listing first), Profile (live requests, profile details page),
  My Rentals time bullets, a notification on two devices, a dated booking, report/block.
- ☐ **C2** Fixes from C1 → 1.0.51 on internal (if needed).
- ☐ **C3** Integration audit: every callable the apps and console call ↔ functions exports; every Firestore/Storage path ↔ rules ↔
  rules tests; `docs/DATA_MODEL.md` (new fields: `ownerListingLimit`, `fcmTokens`, `app_config/apple_iap`); Kotlin ↔ TypeScript
  vectors; e2e flows (incl. a 1-listing publish refusal). Findings fixed before C5.
- ☐ **C4** NIGHTHAWK clean (A1) + Maintenance `prodcheck` clean.
- ☐ 👤 **C5** Approve production: staged rollout 20 % → watch Crashlytics/Play vitals 48 h → 100 %; then bump `docs/release/published.txt`
  (feeds the Instagram agent's release knowledge).

### D. Admin console on the web (then out of the Android app)
- ⏳ **D1** Bookings + Diagnostics tabs live on pro-host.tech/console (site deploy of 22 Nov).
- ☐ **D2** Change price from the console (`changeSlotPrice`, same choices as the app: Keep · Now · Next term per booking).
- ☐ **D3** Payment reminder from the console (`sendPaymentReminder`).
- ☐ **D4** Create / edit listing from the console (item 37): the app's own wizard running in the browser (needs browser versions of
  the Firestore and Storage gateways). Largest console task.
- ☐ **D5** Shell items 1-7: count badges on tabs, admin notification centre (subscription, lead, verification alerts), online/offline pill.
- ☐ **D6** Show the store (Google Play / App Store) per subscription in Packages and the user dossier.
- ☐ 👤 **D7** Walk the 70 items in `docs/migration/ADMIN_PARITY.md` at pro-host.tech/console; tell me what is missing or wrong.
- ☐ **D8** Fix D7 findings.
- ☐ **D9** Cutover: remove the admin screens from the Android app in one reviewed change (17 files + admin slots); admin accounts on
  Android then see "Admin tools are on the web", like iOS. Ships in the next Android release.

### E. Backend and operations
- ☐ **E1** Check the Firestore TTL policy (Maintenance `ttl` dry run); when ACTIVE, retire `pruneExpiredNotifications`.
- ☐ **E2** App Check: measure verified traffic per service (logs `app_check_unverified`); enforce only at ≥ 99 % (decision recorded).
- ☐ **E3** Error alerting: a log-based alert on function errors and on `appleSync` / `billingSync` failures, emailed to the owner.
- ☐ **E4** Move function groups to their own service accounts (`run-billing`, `run-auth`, …; prepared 4 Nov), one group per deploy.
- ☐ 👤 **E5** Cost and security: $50 budget + BigQuery billing export; API key restrictions (web, Android, Maps); Anthropic spending cap;
  disable the old appspot key `e5b6952d…`; rotate the firebase-adminsdk key.

### F. Legal and store compliance
- ☐ 👤 **F1** Approve the privacy sentence on retention (notifications 48 h, mail log 30 days, documents 1 year); Claude regenerates the in-app text.
- ☐ **F2** Re-check Play Data safety and Apple App Privacy against the current app (iOS analytics now opt-in; no tracking).

### G. AI agent (independent)
- ☐ 👤 **G1** Decide D8 (new Google project + a quiet window for the Meta webhook switch); then Claude moves it (steps 3-6 of P9).

### H. Hand-over hygiene (Claude, alongside)
- ☐ **H1** CLAUDE.md slimmed to rules + pointers (topic files under `docs/`), no rule lost.
- ☐ **H2** `docs/migration/HANDOVER.md` refreshed with this plan's state.

## Critical path
1. **Now:** B1 → 👤 B2 → B3 · 👤 C1 · A1-A3 · D1.
2. **This week:** B4/B5 · B6 · B7 · C3/C4 · D2/D3/D6 · E1.
3. **Submission:** B8 → 👤 B9 (App Review, usually 1-3 days) → B10.
4. **Android production:** C2 → 👤 C5 (can run in parallel with App Review).
5. **Console completion:** D4/D5 → 👤 D7 → D8 → D9 (Android release without admin).
6. **Anytime:** 👤 E5, 👤 F1, 👤 G1; E2-E4; H1-H2.

**Definition of done (100 %):** Android production on the shared code at 100 % rollout; iOS approved and released; every admin
task done from pro-host.tech/console and the admin screens removed from both apps; NIGHTHAWK 0 high; integration audit clean.

---

## History: what is already done
### 1. Foundations — ✅ all done
- ✅ Golden baseline (280 screenshots), Parity Gate in CI (every Android change compared pixel by pixel)
- ✅ Shared test vectors Kotlin ↔ server (pricing, booking terms, dates, listing prices, legacy pricing, date slots)
- ✅ Toolchain: Kotlin 2.3.20, Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.7.1, Coil 3
- ✅ Migration skills, agents, scripts, CI (Migration Checks, iOS CI, Admin Web, TestFlight)

### 2. Shared app code — ✅ done (for everything users see)
- ✅ Theme, fonts, strings (English + Arabic), images, all components
- ✅ Data models, gateways (Auth, Functions, Firestore, Storage), repositories
- ✅ View models (Auth, ProHost, Discovery), billing logic over a platform seam
- ✅ All screens: sign-in + registration, Explore (list + map), listing page, availability + booking (dated slots),
  Saved, My Rentals, notifications, Profile, legal, report/block, Pro Host hub, listing wizard, Manage, Change price,
  requests, Renting progress, analytics, verification, Premium
- ✅ Navigation shell (`ProHostAppRoot`) shared; Android passes only splash + admin screens
- ☐ (after admin cutover) delete the Android admin files and their slots

### 3. Features added this cycle — ✅ done
- ✅ Dated booking (calendar picker, closed dates, booking window, Manage calendar, server checks)
- ✅ Report / block (app, server, admin Reports queue)
- ✅ Retention (Firestore TTL, Storage lifecycle), operator legal block, App Store listing
- ✅ Second tier "ProHost Premium · 1 listing" ($4.49 / $44.99): server (listing limit, publish check, rules), Play product
  `package_single_mrr` live (both base plans ACTIVE, 174 regions), App Store `prohost.single.*` ready to submit,
  app tier switch on Premium (ships with 1.0.50 / next TestFlight)
- ✅ Profile rework (compact live requests, profile details page), booking timing bullets, push on every device

### 4. Android to production
- ✅ 1.0.49 on the internal track (dated booking, report/block, closed dates)
- ✅ Backend live (deploys on every `main` push; last deploy 22 Nov 06:50 UTC)
- ☐ 👤 Owner tests 1.0.49 on a phone (checklist in OWNER_TODO)
- ✅ Version 1.0.50 (50) from the current shared code on the internal track (22 Nov) · ☐ 👤 owner phone test
- ☐ Integration audit (callables ↔ rules ↔ app ↔ admin web) before production
- ☐ NIGHTHAWK + `prodcheck` clean
- ☐ Staged production rollout 20 % → 100 %, then `docs/release/published.txt`
- ☐ (after admin cutover) Android release without the admin screens

### 5. iOS to the App Store
Done:
- ✅ Swift host + shared framework, TestFlight pipeline (temporary signing certificate, 6 GB release build, 120-min job)
- ✅ Firebase on iOS: Auth, Functions, Firestore, Storage, App Check (App Attest)
- ✅ Sign-in: email + password, Google (nonce-checked), Sign in with Apple
- ✅ Map (Google Maps SDK), photo / document / camera pickers, location, push notifications (server sends the iOS alert)
- ✅ App Store Connect: listing EN + AR, categories, age 18+, 175 countries, prices, subscriptions + launch offers,
  review contact, copyright, privacy answers published, mock-up screenshots uploaded

To do (code, Claude):
- ✅ CI for Sign in with Apple + StoreKit → `main` → TestFlight build 21
- ✅ Deep links: Universal Links + `prohost://`, push-tap → listing (code; CI running)
- ✅ Legal viewer in a WKWebView · ☐ legal PDFs
- ✅ Payment reminder to the calendar (EventKit) · ✅ haptics (success feedback), sounds (same WAVs, ambient session)
- ✅ Analytics consent + Crashlytics on iOS (opt-in like Android: Consent Mode denied in Info.plist, choice kept in NSUserDefaults)
- ✅ Arabic purpose strings (camera, location) — `InfoPlist.strings`
- ✅ Notification permission at the same moment as Android (after the analytics choice)
- ✅ Admin accounts on iPhone: "use the web console" screen (decision D1 default)
- ✅ Sign in with Apple token revocation on account deletion (Apple rule 5.1.1(v)): Firebase `revokeToken` before delete
- ✅ **Payments: StoreKit 2** in the app (prices/offers from StoreKit, buy, restore, manage, offer codes, both tiers)
  · ☐ 👤 sandbox purchase on TestFlight 21
- ✅ **Server: `verifyAppleTransaction` + App Store Server Notifications V2** (live)
  · ✅ the two notification URLs (production + sandbox) · ✅ daily Apple re-sync (`appleSyncJob`, In-App Purchase key in
  `app_config/apple_iap` via Maintenance `applekey`) · ☐ admin console shows the platform per subscription
- ☐ Integration audit + iOS-on-device pass (Arabic, dark mode, small phone, VoiceOver basics)
- ☐ Real iOS screenshots (EN + AR) replace the mock-ups; review notes + demo accounts; build attached
- ☐ 👤 Testers on TestFlight (owner, Rewa, Rony) report what differs from Android
- ☐ 👤 Owner approves screenshots and presses **Add for Review** → App Review → phased release

### 6. Admin console on the web
- ✅ `pro-host.tech/console` live (email + password, ADMIN claim, own page on the main site)
- ✅ Tabs: Users (+ dossier), Listings & Bookings, Packages (billing health, rescue, force upgrade), Analytics,
  Security (audit log, legal docs), Schema, Demo; Reports queue
- ◐ Admin-as-Pro-Host tools on the web: ✅ Bookings tab (all requests, all bookings, renting progress, search, CSV) ·
  ☐ change price, payment reminder, create/edit listing (host the shared owner screens in the console)
- ✅ Diagnostics tab (replaces the Android debug dialog: session, callables, Firestore, Play billing + heartbeat, queues; health score)
- ☐ 👤 Owner walk-through of all 70 items in `docs/migration/ADMIN_PARITY.md` (0 of 70 ticked so far)
- ☐ Fix whatever the walk-through finds
- ☐ Cutover: remove the admin from the Android app in one reviewed change (mobile admins see "use the web console")

### 7. Backend and operations
- ✅ Report/block, dated-booking checks, TTL, iOS push alert, admin console hosting
- ☐ Retire `pruneExpiredNotifications` once the TTL policy shows ACTIVE
- ☐ Apple payments backend (see 5)
- ☐ App Check enforcement decision (≥ 99 % verified traffic)
- ☐ 👤 Cost and security items: $50 budget + billing export, API key restrictions, Anthropic cap, key/secret rotation

### 8. AI agent move (independent of the apps)
- ✅ Decoupled inside this repo (adapter interface, contract tests)
- ☐ 👤 Decision D8 (new Google project + quiet window for the Meta webhook cutover), then the move

---
