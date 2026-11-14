# Owner queue — everything that needs your decision or your own hands

Kept by the migration sessions while they run on their own (owner request, 8 Nov 2026: "run automatically until the plan is 100% done; collect everything for me in one place"). Nothing in this file blocks the work that can be done without you; items are added as they come up and handed over together when the autonomous work has nothing left to do. Each decision has the **default the work proceeds with** if you do not answer. The standing list of older manual jobs stays in `docs/OWNER_TODO.md`.

## A. Decisions (default applies unless you say otherwise)
| # | Decision | Default taken |
|---|---|---|
| D1 | Mobile ADMIN account after the web console cutover | Info screen with a link to the web console |
| D2 | Admin quirks (CSV exports only 500 loaded rows; client sends 3 audit types the server rejects; "Delete user" removes the profile only; no documented bootstrap-admin path) | Keep behaviour, document it |
| D3 | Web push for admins | No (in-console notification list only) |
| D4 | System Debugger / GA4-secret UI on the web | Diagnostics page yes; GA4 secret stays CLI-only |
| D5 | Legal PDF: per-platform generation or hosted PDFs | Keep per-platform generation |
| D6 | Report/block UI for Apple Guideline 1.2 (the one planned UX addition) | **Not built until you approve the design** |
| D7 | Bundle Roboto on iOS for text parity | Yes |
| D8 | Agent: new Google project / billing account; Meta webhook cutover window | Prepared, **not switched** until you pick a window |
| D9 | Untrack `hs_err_pid32984.log` (1.9 MB) | Done in a housekeeping commit; keystore stays (your choice) |
| D10 | Roborazzi Gradle plugin dropped (needs AGP's old DSL; the library stays, record mode = `-Proborazzi.test.record=true`) | Adopted: build tooling only, no visible effect |
| D11 | Application module stays named `:app` (not renamed `androidApp`): renaming touches every workflow and script for no benefit | Adopted |
| D12 | Device-locale number/time formats (`hourLabel`, "this month's yield", availability total) follow the device language's digits, `PriceChange.pad` is fixed `Locale.US` | Unchanged; listed so you can choose one rule later |
| D13 | Dim behind sheets/dialogs is 4-6 % lighter under Compose 1.12 in Robolectric (<= 16 levels, nothing moves); tolerated only for the listed overlay screens (`docs/migration/overlay-dim.json`); verify on a device (M4) | Tolerated |
| D14 | Deferred deep links / attribution: Play Install Referrer on Android (free); on iOS a Universal Link + App Store campaign token, or a paid attribution vendor | Free options only |
| D15 | Public images behind a CDN: Firebase Hosting rewrite, Cloud CDN, or Cloudflare in front | Firebase/Cloud CDN, no third party |
| D16 | Retention periods: audit log (13 months hot), verification documents and booking agreements (**delete 1 year after review**, owner 9 Nov), notifications (48 h, existing), mail history (30 days) | As listed; privacy text must state it (owner review) |
| D17 | Search service when listings pass ~2 k-50 k (Typesense on Cloud Run vs Algolia vs Firestore geohash only) | Geohash first, decide on measured data |
| D18 | Terraform to codify budgets, alerts, IAM and TTL policies | Yes, after the parity work |

## B. Manual jobs (only you can do these)
Standing list: `docs/OWNER_TODO.md` (Google Trust & Safety case, Play domain checks and deep links, quotas, Play permission trim, Maps key restrictions, Play declarations, secret rotation, App Check decision, `BOOTSTRAP_SECRET`, uptime URLs, Auth Platform branding, iOS items, legal items). New from the migration:
- M1. ~~Firebase web app + admin subdomain~~ **Changed 9 Nov: no subdomain.** Web app registered (public config recorded in the plan file); admin console = a separate page on the existing Netlify site at `pro-host.tech/console` (built in CI into `public/console/`, email + password sign-in, `claims.role == ADMIN` guard). Nothing left for DNS/Netlify.
- M2. Create the new Google project (+ billing) for the AI agent when you choose to move it (D8).
- M3. iPhone for device tests, APNs key confirmation, Firebase iOS key restriction, Paid Apps Agreement, trader status (Apple/Play accounts).
- M4. Play Console: invite/confirm the CI service account for releases; internal-testing install to smoke-test the Kotlin 2.3 / Compose Multiplatform build before any production rollout; emulator check on API 24/25 for the desktop-java-time (desugaring) paths.
- M6. Create GCP budgets + alerts (25/50/90/100 %) and enable the BigQuery billing export (billing-account admin only).
- M7. Approve enabling Firestore TTL policies (console or Maintenance task) and Storage lifecycle rules (D16 periods).
- M8. Restrict/rotate API keys (Maps, Firebase iOS) and set per-key quotas; confirm the Anthropic monthly spend cap in the Anthropic console.
- M9. Pick the CDN option (D15) and, if wanted, the attribution vendor (D14); add DNS records if a CDN needs them.
- M5. Approve the plan/decisions list (A) and walk through the admin web console before the Android admin is removed (P1.F).

- M10. Install the CI build from an **internal-testing track** (or a debug APK from CI) on 2 real phones (one API 24-25, one current) and walk the main flows: the app now runs on Kotlin 2.3.20 and Compose Multiplatform 1.12; Robolectric cannot prove window dim, spring motion, fonts or the map on a device (D13).
- M11. When you want iOS to start: borrowed iPhone, Apple Developer / App Store Connect access for CI signing (already scripted), and a decision on D6 (report/block UI). The shared UI cannot be proven on iOS until the iOS host app is built on a macOS runner (P7).

## C. Notes the work will not decide for you
- Nothing is released to Play production or the App Store by the autonomous runs; version bumps and `docs/release/published.txt` stay yours.
- Server changes waiting for merge to `main`: notifications now also carry `ttlAt` (Timestamp; `expireAt` is epoch millis and Firestore TTL cannot use it), and the Maintenance task `ttl` (dry run, code `apply`) creates the TTL policies on `notifications.ttlAt` and `mail.expireAt`; the hourly `pruneExpiredNotifications` stays until the policies show ACTIVE and old notifications are gone; mail documents carry `expireAt` (needs the TTL policy, M7); month-end clamp in `bookingTerms.ts` (server end date moves 1-3 days earlier for bookings starting on the 29th-31st, equal to the app).

## D. State of the autonomous run (9 Nov 2026)
Done and proven by the 280-render Parity Gate: P0, P2 (all seams), P3 (Kotlin 2.3.20, built-in Kotlin, `:shared` module, Compose Multiplatform 1.12.2 with the app looking as before), P4.1-P4.3a (theme tokens, pure components, generated Compose string resources). Waiting for work that needs more than CI: P4.3b+ (move screens, Coil 3, string call sites, view models), P5 data gateways, P6 screens, P7-P8 iOS (macOS runners and a device), P1 admin web (D-items and M1), P9 agent move (D8, M2), P10 hardening, P11 backend efficiency (Q1-Q8 and sections 2-7 of `BACKLOG_BACKEND_COST.md`). Each is a sequence of small gated units; the order, risks and per-unit method are in `MASTER_PLAN.md`, `PROGRESS.md` and the backlog.

## E. Device test build (9 Nov 2026)
Play probe (Release bundle, `probe`): the CI account works; Play holds version codes up to 46, production = 1.0.46 (46), internal and beta empty. `main` is at versionCode 47 / 1.0.47 (not uploaded yet), so the migration build uses **versionCode 48 / 1.0.48** to leave 47 free for the real 1.0.47 release. Uploaded to the **internal** track only; production stays 1.0.46 and `docs/release/published.txt` is untouched.
- 9 Nov 2026: **1.0.48 (48) released on the internal track at 100 %** (Release bundle run 42611994800; notes en-GB; symbols uploaded). Production untouched (1.0.46). Owner to install from the internal opt-in link and report (M10). Decisions D1-D18 approved with defaults; D6 concept approved (mockups before build); Play Console: appspot account removed; trims of compute/run-billing/run-ig postponed (OWNER_TODO #5); Rewa Rammal's Admin access confirmed intended.

## F. Hand-off walkthrough answers (9 Nov 2026)
1. **D1-D18**: approved with all defaults (D6 stays blocked until a design is approved; D16 updated, see above).
2. **D6 report/block**: concept approved; screens shown as mockups before any build.
3. **M10 device test**: owner will test 1.0.48 and report later. Production stays 1.0.46.
4. **M1 admin web**: path `pro-host.tech/console`, email + password sign-in; web app registered. Do NOT initialise Analytics in adminWeb (opt-in policy).
5. **M6 budget**: $50/month (alerts about $12/$25/$45/$50 + forecast 100 %); owner creates it and enables Billing export to BigQuery dataset `billing_export` (EU), then says "done".
6. **M7 TTL/lifecycle**: approved for `notifications.expireAt` (48 h) and `mail.expireAt` (30 d) via Maintenance task `ttl` (dry run, then `apply`); Storage lifecycle about 365 days on `listing_verification_docs/` and `booking_agreements/`. Flag: an agreement for a booking longer than a year would be deleted by an age rule.
7. **M8 keys**: owner does 7a web key referrers, 7b Android Firebase key (package + SHA-1s), 7c Maps key, 7d Anthropic monthly cap; then tests sign-in and the map on the phone.
8. **D8/M2 agent**: "prepare, don't switch": P9 steps 1-5 inside this repo; nothing needed from the owner until a go-live window is chosen.
9. **iOS**: 9a APNs key uploaded (done); 9b iOS API key restricted to `app.geonajjar.prohost` (done); 9c Paid Apps Agreement **Processing** (waiting on Apple); 9d EU trader status **declared as Trader** (done); 9e legal name + postal address **later** (must equal the Apple trader details; goes into privacy 1, terms 1.1 and 15.3); 9f borrowed iPhone not selected, still open.
10. **Standing list**: see `docs/OWNER_TODO.md` (Trust & Safety case, quotas, Play trims #5, secret rotation, second-owner invitation, BOOTSTRAP_SECRET, Play declarations, App Check, Auth Platform branding, final removal of temporary access).


## G. New since the walkthrough
- **G1 (decision, 14 Nov 2026):** `main` contains native SwiftUI work from another session dated 8 Nov ("iOS S2": `SessionStore`, `RootView`, `MainShell`, 332 lines under `ios/ProHost/`). The SwiftUI screen port was cancelled by the KMP decision. Default if you say nothing: leave it untouched now; when the Compose iOS host (`iosApp`) exists, keep `SessionStore`/auth pieces as Swift gateway code and delete the SwiftUI shell/tab UI. Please also tell that other session to stop (prompt in CLAUDE.md migration section / plan §10).
