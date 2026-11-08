# Migration decisions (KMP + Compose Multiplatform)

## Locked by the owner (8 Nov 2026)
| # | Decision | Notes |
|---|---|---|
| 1 | Kotlin Multiplatform + Compose Multiplatform for Android and iOS | Replaces native SwiftUI. Apple account, backend and review work unchanged. |
| 2 | Admin Console = web app, Compose for Web reusing the Kotlin admin code | React only as fallback if the P1.0 spike fails its gate. Out of both mobile builds. Complete: 70 items in ADMIN_PARITY.md. |
| 3 | AI agent = new repo + new Google project, linked through an adapter contract | Decouple in this repo first (P9 steps 1-5), then move. |
| 4 | Android ships from the shared code at every phase; iOS to TestFlight when core screens work | No freeze of Android fixes. |
| 5 | No UI/UX change. Deviations are listed here and asked | Unit protocol: learn, plan, execute, compare, confirm, proceed. |

## Withdrawn recommendations (would change UX)
Moving Pro Host drawer items into tabs, removing Explore's menu button, iOS-style back chevrons/ripples. Drawer, tabs and layouts stay as today on both platforms.

## Open owner decisions (default if no answer)
1. Mobile ADMIN account after cutover: info screen with link to the web console (default).
2. Admin quirks: keep as is or fix? Audit CSV exports only the 500 loaded rows; client sends 3 audit types the server rejects (`SCHEMA_DEFAULTS_MERGED`, `DEMO_CONTENT_SEEDED`, `DEMO_CONTENT_PURGED`); "Delete user" removes the profile only, not the Auth account; no documented bootstrap-admin path. (Default: keep behaviour, document.)
3. Web push for admins (default: no; in-console notification list only).
4. System Debugger and GA4-secret UI on the web (default: Diagnostics page yes, GA4 secret stays CLI-only).
5. Legal PDF: keep per-platform generation or host PDFs (default: keep behaviour per platform).
6. Report/block UI for Apple 1.2: the one planned UX addition; needs owner approval of the design before build.
7. Roboto bundled on iOS for text parity (default: yes).
8. Agent: new Google project/billing account; Meta webhook cutover window.
9. Housekeeping: untrack `hs_err_pid32984.log` (1.9 MB); keystore stays by owner choice.

## Versions (fill in at the P0 spike)
Kotlin ___ / CMP ___ / AGP ___ / Gradle 9.7.1 / Coil 3 ___ / Firebase BoM ___ / Xcode ___
