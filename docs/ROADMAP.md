# ProHost roadmap: to 100 % (Android + iOS in production, admin console on the web)

Updated 22 Nov 2026, 13:00 UTC. ✅ done · ⏳ running now · ☐ to do · 👤 needs the owner.
Detail per unit: `docs/migration/PROGRESS.md`; owner items: `docs/OWNER_TODO.md`; history: `docs/OPS_LOG.md`.

## Where we are

| Track | Done | Left |
|---|---|---|
| Shared app (one codebase for Android + iOS) | all user screens, view models, repositories, models, logic, strings, theme | admin screens stay in Android until the web console replaces them (by design) |
| Android | live on Play: 1.0.46 · internal track: 1.0.49 | owner phone test, release from the shared code, staged rollout |
| iOS | TestFlight builds 18-21: full app, email/Google/Apple sign-in, map, pickers, location, push, StoreKit | a few native pieces, payments (StoreKit), App Review package, submission |
| Admin console (web) | live at pro-host.tech/console, all 7 tabs + reports queue in code | owner walk-through of 70 items, 2 missing areas, then remove admin from Android |

---

## 1. Foundations — ✅ all done
- ✅ Golden baseline (280 screenshots), Parity Gate in CI (every Android change compared pixel by pixel)
- ✅ Shared test vectors Kotlin ↔ server (pricing, booking terms, dates, listing prices, legacy pricing, date slots)
- ✅ Toolchain: Kotlin 2.3.20, Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.7.1, Coil 3
- ✅ Migration skills, agents, scripts, CI (Migration Checks, iOS CI, Admin Web, TestFlight)

## 2. Shared app code — ✅ done (for everything users see)
- ✅ Theme, fonts, strings (English + Arabic), images, all components
- ✅ Data models, gateways (Auth, Functions, Firestore, Storage), repositories
- ✅ View models (Auth, ProHost, Discovery), billing logic over a platform seam
- ✅ All screens: sign-in + registration, Explore (list + map), listing page, availability + booking (dated slots),
  Saved, My Rentals, notifications, Profile, legal, report/block, Pro Host hub, listing wizard, Manage, Change price,
  requests, Renting progress, analytics, verification, Premium
- ✅ Navigation shell (`ProHostAppRoot`) shared; Android passes only splash + admin screens
- ☐ (after admin cutover) delete the Android admin files and their slots

## 3. Features added this cycle — ✅ done
- ✅ Dated booking (calendar picker, closed dates, booking window, Manage calendar, server checks)
- ✅ Report / block (app, server, admin Reports queue)
- ✅ Retention (Firestore TTL, Storage lifecycle), operator legal block, App Store listing
- ✅ Second tier "ProHost Premium · 1 listing" ($4.49 / $44.99): server (listing limit, publish check, rules), Play product
  `package_single_mrr` live (both base plans ACTIVE, 174 regions), App Store `prohost.single.*` ready to submit,
  app tier switch on Premium (ships with 1.0.50 / next TestFlight)
- ✅ Profile rework (compact live requests, profile details page), booking timing bullets, push on every device

## 4. Android to production
- ✅ 1.0.49 on the internal track (dated booking, report/block, closed dates)
- ✅ Backend live (deploys on every `main` push; last deploy 22 Nov 06:50 UTC)
- ☐ 👤 Owner tests 1.0.49 on a phone (checklist in OWNER_TODO)
- ☐ Version 1.0.50 from the current shared code → internal track → 👤 owner phone test
- ☐ Integration audit (callables ↔ rules ↔ app ↔ admin web) before production
- ☐ NIGHTHAWK + `prodcheck` clean
- ☐ Staged production rollout 20 % → 100 %, then `docs/release/published.txt`
- ☐ (after admin cutover) Android release without the admin screens

## 5. iOS to the App Store
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
- ✅ Payment reminder to the calendar (EventKit) · ☐ haptics, sounds
- ☐ Analytics consent + Crashlytics on iOS (opt-in like Android)
- ✅ Arabic purpose strings (camera, location) — `InfoPlist.strings`
- ☐ Notification permission at the same moment as Android (after the analytics choice)
- ☐ Admin accounts on iPhone: "use the web console" screen (decision D1 default)
- ☐ Sign in with Apple token revocation on account deletion (Apple rule 5.1.1(v)), server side
- ✅ **Payments: StoreKit 2** in the app (prices/offers from StoreKit, buy, restore, manage, offer codes, both tiers)
  · ☐ 👤 sandbox purchase on TestFlight 21
- ✅ **Server: `verifyAppleTransaction` + App Store Server Notifications V2** (live)
  · ✅ the two notification URLs (production + sandbox) · ☐ daily Apple re-sync (needs an In-App Purchase key
  from the owner) · ☐ admin console shows the platform per subscription
- ☐ Integration audit + iOS-on-device pass (Arabic, dark mode, small phone, VoiceOver basics)
- ☐ Real iOS screenshots (EN + AR) replace the mock-ups; review notes + demo accounts; build attached
- ☐ 👤 Testers on TestFlight (owner, Rewa, Rony) report what differs from Android
- ☐ 👤 Owner approves screenshots and presses **Add for Review** → App Review → phased release

## 6. Admin console on the web
- ✅ `pro-host.tech/console` live (email + password, ADMIN claim, own page on the main site)
- ✅ Tabs: Users (+ dossier), Listings & Bookings, Packages (billing health, rescue, force upgrade), Analytics,
  Security (audit log, legal docs), Schema, Demo; Reports queue
- ☐ Admin-as-Pro-Host tools on the web (all listings, all requests, renting progress, change price, create/edit listing)
  — host the shared owner screens in the console
- ☐ Diagnostics page (replaces the Android debug dialog)
- ☐ 👤 Owner walk-through of all 70 items in `docs/migration/ADMIN_PARITY.md` (0 of 70 ticked so far)
- ☐ Fix whatever the walk-through finds
- ☐ Cutover: remove the admin from the Android app in one reviewed change (mobile admins see "use the web console")

## 7. Backend and operations
- ✅ Report/block, dated-booking checks, TTL, iOS push alert, admin console hosting
- ☐ Retire `pruneExpiredNotifications` once the TTL policy shows ACTIVE
- ☐ Apple payments backend (see 5)
- ☐ App Check enforcement decision (≥ 99 % verified traffic)
- ☐ 👤 Cost and security items: $50 budget + billing export, API key restrictions, Anthropic cap, key/secret rotation

## 8. AI agent move (independent of the apps)
- ✅ Decoupled inside this repo (adapter interface, contract tests)
- ☐ 👤 Decision D8 (new Google project + quiet window for the Meta webhook cutover), then the move

---

## Owner items (👤), in order of impact
1. Install TestFlight build 21 on an iPhone (try a sandbox purchase of either tier); tell me what differs from Android.
2. Test Android 1.0.49 (internal track) on your phone.
3. Rewa and Rony accept Apple's invitation (then I add them to TestFlight).
4. Walk the admin console at pro-host.tech/console with `docs/migration/ADMIN_PARITY.md`.
5. Verify pro-host.tech in Search Console.
6. $50 budget + BigQuery billing export; API key restrictions; Anthropic spend cap.
7. Review the privacy retention sentence.
8. Decide D8 (agent move).
9. At the end: approve iOS screenshots, press Add for Review; approve the Android production rollout.

## Order from here
1. ✅ `main` (Apple server live) → notification URLs → TestFlight 21; Play + App Store single-listing products created.
2. iOS gaps left: analytics consent + Crashlytics, notification permission timing, admin "use the web console" screen,
   Apple token revocation on delete, haptics/sounds.
3. Sandbox purchase test on TestFlight; daily Apple re-sync.
4. Android 1.0.50 → owner test → staged production.
5. Admin web: Pro Host tools + Diagnostics → owner walk-through → Android admin removal.
6. App Review package → submission → iOS phased release.
