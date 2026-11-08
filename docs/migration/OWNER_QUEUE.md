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

## B. Manual jobs (only you can do these)
Standing list: `docs/OWNER_TODO.md` (Google Trust & Safety case, Play domain checks and deep links, quotas, Play permission trim, Maps key restrictions, Play declarations, secret rotation, App Check decision, `BOOTSTRAP_SECRET`, uptime URLs, Auth Platform branding, iOS items, legal items). New from the migration:
- M1. Register a Firebase **web app** for the admin console (public config values) and create the site + DNS for `admin.pro-host.tech`; add the domain to authorized domains (`authconfig`).
- M2. Create the new Google project (+ billing) for the AI agent when you choose to move it (D8).
- M3. iPhone for device tests, APNs key confirmation, Firebase iOS key restriction, Paid Apps Agreement, trader status (Apple/Play accounts).
- M4. Play Console: invite/confirm the CI service account for releases; internal-testing install to smoke-test the Kotlin 2.3 / Compose Multiplatform build before any production rollout; emulator check on API 24/25 for the desktop-java-time (desugaring) paths.
- M5. Approve the plan/decisions list (A) and walk through the admin web console before the Android admin is removed (P1.F).

## C. Notes the work will not decide for you
- Nothing is released to Play production or the App Store by the autonomous runs; version bumps and `docs/release/published.txt` stay yours.
- Server change waiting for merge to `main`: month-end clamp in `bookingTerms.ts` (server end date moves 1-3 days earlier for bookings starting on the 29th-31st, equal to the app).
