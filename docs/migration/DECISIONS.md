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

## Baseline and findings (P0, 8 Nov 2026)
- **Golden baseline:** the git proxy refuses tag pushes, so there is no `kmp-golden-v1047` tag on GitHub (a local tag at `d30c331` was not pushed; creating the tag in the GitHub UI is optional).
  The baseline is the Design Screenshots run 42474484795 on commit `7ed5728686cd7e7c9abe16c655493f296953e443` (app code identical to `d30c331`), stored as git commit
  `d841cccf2e7e1acbd8acf32a2226ba221444d3ad` under `refs/design-shots/golden-v1047`: **280 PNGs, 0 render errors**, flat names `<screen>_<variant>.png` (variants light, dark, small, rtl, ar).
- **Golden determinism (checked 8 Nov 2026):** rendering the same app code twice gave 274 of 280 identical PNGs. The 6 that differ are test-harness noise, not app changes: the five `request_detail_*`
  renders print a wall-clock "Submitted" time from mock data (11:16 vs 11:23), and `explore_list_light`'s first hero photo is a network image that loaded in one render and not in the other. They are masked in
  `docs/migration/golden-masks.json` (`diff_shots.py --masks`; with the masks the two sets compare 280/280 identical; a third render confirms it). The hero-image mask is large (it also covers the Verified chip and
  heart on that one card), so P2 should freeze the clock in the preview data and preload/replace the network image in the harness, then recapture the golden once and delete the masks.
- **Known Kotlin/TypeScript divergences** (pinned by `kotlinExpected` in `tests/vectors/booking-terms.json`; behaviour NOT changed):
  1. ~~`bookingTerm` month arithmetic~~ **FIXED 8 Nov 2026 (owner request):** the server (`functions/src/bookings/bookingTerms.ts` `addMonths`) overflowed into the next month
     (2026-01-31 + 1 month = 2026-03-03); it now clamps to the last day of the target month like the app's `Calendar.add(MONTH)` (2026-02-28). Effect on production once deployed (merge to `main`):
     for bookings that start on the 29th-31st the server's end date moves 1-3 days earlier, so conflict checks (`bookingConflictGuard`), `hasEnded` and the price-change term logic now agree with what
     the app shows; no stored data changes (terms are computed on read). The Android code was already correct and is untouched. 12 month-end cases (leap year, year rollover, 12/13-month terms) are in
     `tests/vectors/booking-terms.json` and run on both sides; `kotlinExpected` entries are now empty. Still different and NOT changed: `hasEnded`'s "today" is the device date in the app and UTC on the server.
  2. `findLowestPriceForSubdivision` returns `0.0` for an unpriced room where TypeScript `roomPrice` returns `null` (representation only; not vectored).
  3. TypeScript `pricingFromLegacyFormula` defaults a missing `rateUsd` to 300 and missing days to Mon-Fri; the Kotlin `RentalFormula` always has both (no practical difference for stored documents).
- **Version facts** (JetBrains pages read 8 Nov 2026; to be confirmed by the spike): latest stable Compose Multiplatform **1.12.2** (15 Oct 2026, Jetpack Compose 1.12.1), 1.13.0-beta02 pre-release (4 Nov); latest stable Kotlin
  **2.4.21** (8 Oct 2026); CMP docs say "at least Kotlin 2.1.0" and recommend Kotlin 2.2.20 for iOS and web. AGP 9 KMP plugin id `com.android.kotlin.multiplatform.library`, Gradle >= 9.1.0, remove `org.jetbrains.kotlin.android`,
  enable resources with `androidResources { enable = true }`. This repo: Kotlin 2.2.10, AGP 9.4.1, Gradle 9.7.1, Compose BOM 2025.02.00 (Jetpack Compose 1.7.x).
- **Spike round 1 (8 Nov 2026, CI run 42476244758): Kotlin 2.2.10 + CMP 1.12.2 does NOT work.** The iOS build refuses CMP 1.12.2's klibs: "incompatible ABI version '2.3.0'. The library was produced by '2.3.20' compiler";
  on JVM the generated resource accessors fail with "Missing stdlib class". The CMP docs' "at least Kotlin 2.1.0" is wrong for 1.12.2 in practice. **Kotlin must move to >= 2.3.20 with CMP 1.12.2** (or CMP must go back to a release built with <= 2.2.10).
  The Kotlin bump is therefore part of the migration (P3), and it also moves Compose from Jetpack 1.7 to 1.12 (Material 3 drift to be caught by the golden diff). Round 2 tests Kotlin 2.3.20.
- **Spike rounds 2-4 (8 Nov 2026): Kotlin 2.3.20 + CMP 1.12.2 + AGP 9.4.1 + Gradle 9.7.1 works** for Android, iOS (framework + simulator tests), wasmJs and a JVM test host, including Arabic plurals/format/`\u0020` through composeResources.
  **Target versions for P3: Kotlin 2.3.20 (minimum), Compose Multiplatform 1.12.2, AGP 9.4.1, Gradle 9.7.1.** Full results, fallbacks and the items still open (Splash parity, font cap, web interop, map, Swift auth): `docs/migration/SPIKE_REPORT.md`.
- **Spike rounds 5-6 and close (8 Nov 2026; `SPIKE_REPORT.md` is final):** Compose for Web + Firebase JS passes (Kotlin `AdminGateway`, canvas drawn, no errors). Splash on Compose 1.12 vs the golden: artwork/layout identical; only the endless spinner differs
  by animation phase (no exact frame match in a 2.7 s scan; ring/stroke/colours match visually), so from P4 on indeterminate spinners are **known dynamic regions** (125x125 px box around the 28 dp indicator) pinned by a structural test (28 dp, 3 dp stroke, white-35 % track, `primary`).
  A Compose-level font cap (1.15) reaches content but **not** Dialog/ModalBottomSheet windows at system scale 1.5 (they read 1.5): Android keeps the cap in `attachBaseContext`; shared dialogs/sheets get their own `FontScaleCap` at the `ProHostDialog`/`ProHostBottomSheet`/popup choke points, with a gate test (P4).
- **Gate hardening (8 Nov 2026):** the five `request_detail_*` masks were widened leftwards (the clock text is wider at some times of day: measured differing boxes sat directly beside the old masks, same text line; Before|After sheet confirmed "11:16" vs "16:06").
  A differing render is re-rendered once; only a reproducing difference fails (network listing photos sometimes miss the fixed 400 ms wait: `explore_list_light` once rendered grey placeholders). Every changed render gets a Before | After | Difference sheet (`refs/design-shots/parity-<branch>`).
- **Billing dates keep the current behaviour (owner confirmed, 8 Nov 2026):** `PlayOfferText.regularPriceStartsAt` and `PlayPromo.endOfDayBeirut` stay on `java.util.Calendar` (Android-only billing code) because a `java.time`/kotlinx-datetime port differs by one hour when the result lands in the repeated hour of a clock change (`Calendar` takes the LATER occurrence, `java.time` the earlier; `endOfDayBeirut`: 8 of 2,976 dates 2025-2032, e.g. `ends-20261024`). When the shared offer model is built (P5) the port must reproduce the current rule explicitly (resolve an overlap to the later offset); `BillingDatesCharacterizationTest` is the spec. Offered, not adopted: a "light" tier for harmless one-hour edge cases approved in bulk; until the owner says so, every behaviour difference is listed and asked individually.
