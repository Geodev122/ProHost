# Specialist & Pro Host UI Overhaul — Report

Scope: every screen, drawer, nav bar, top bar, dialog, sheet, banner and badge used by the
`SPECIALIST` and `PRO_HOST` roles (including the shared components `ADMIN` also renders).
Brand mapping: **Special Blue = `VibrantBlue` #246BEE** (the repo's blue brand accent),
**Orange = `CarnationOrange` #F25F4C**.

> **Not compiled.** The cloud container has no Android SDK, so `./gradlew` could not run.
> Changes were checked by static scripts only (brace balance, `@Composable`-context check for
> every added `MaterialTheme.*` read, import check). Run `./gradlew :app:assembleDebug` and
> `node scripts/prohost-debugger.js` before merging. NIGHTHAWK was run: 0 critical, 0 high, 2 medium.

## 1. What changed

### Theme layer (`ui/theme`)
| File | Change |
|---|---|
| `Theme.kt` | Light `primary` = Special Blue, `secondary` = Orange (action shade). Dark `primary` #7FA6FF, `secondary` #FF8A75, tuned `on*`/container pairs. Provides `LocalProHostColors`. |
| `ProHostColors.kt` (new) | `MaterialTheme.proColors`: success / warning / info / locked (+containers), shimmer pair, drawer/hero gradient (`brandHeader*`, `onBrandHeader`) and `header{Success,Warning,Error}` tints — each with a light and a dark value. |
| `Color.kt` | New contrast-audited tokens (`CarnationOrangeAction`, `StatusSuccessStrong`, `StatusWarningStrong`, dark-mode variants). Legacy constants kept. |
| `Shape.kt` | Added shared `SheetShape` (24dp top) and `DrawerShape`. |
| `Spacing.kt` | Added `xxl` (32dp). Scale is 4/8/12/16/24/32. |
| `Type.kt` | `titleLarge` 20sp Bold, `titleMedium` line-height 24, `bodyMedium/Small` roomier line-height, `labelSmall` SemiBold. |

Orange is split in two on purpose: `#F25F4C` is only **3.22:1** on white, so it is kept for icons and
large fills, while filled controls that carry text use `#D2432F` (**4.58:1**).

### Shared component library (`ui/components/ProHostBrandComponents.kt`, new)
`ProHostDialog`, `ProHostBottomSheet`, `ProHostAlertBanner`, `ProHostStatusStrip`,
`ProHostAlertSeverity`, `ProHostCountBadge`, `ProHostDotBadge`, `ProHostBottomNavBar`,
`ProHostDrawerSheet`, `ProHostDrawerHeader`, `ProHostDrawerAvatar`, `ProHostRolePill`,
`ProHostDrawerSectionLabel`, `ProHostDrawerDivider`, `ProHostDrawerItem`, `ProHostDrawerHighlight`.

None contain a colour literal or a fixed brand constant; every colour is read from
`MaterialTheme.colorScheme` or `MaterialTheme.proColors`, so a theme switch re-colours them with no
component-side branching. Corners come from `MaterialTheme.shapes` (12/16dp) or the shared
sheet/drawer shapes; spacing from `Spacing`.

### De-duplication
* 24 `AlertDialog(` call sites → `ProHostDialog` (same parameter names; drop-in).
* 3 hand-rolled `ModalBottomSheet`s (Discovery filters, Check-Availability, Rental booking) → `ProHostBottomSheet`
  (removes three copies of the drag-handle/scrim/shape block).
* Bottom nav bar (strip + bloom + items) and drawer sheet extracted from `ProHostNavGraph` → `ProHostBottomNavBar`, `ProHostDrawerSheet`.
* Specialist and Admin drawer headers, the "Explore" highlight, 14 `NavigationDrawerItem` blocks, section labels, dividers → shared drawer parts (`AppDrawerContent.kt` shrank by ~490 lines).
* Offline strip → `ProHostStatusStrip`; update banner, KYC banner, `DrawerIdentityCard` re-based on theme tokens.
* Notification bell badge → `ProHostCountBadge`. `StatusBadge` / `CustomButton` / `ProCedarBadge` / shimmer now theme-aware.
* Well over 100 fixed semantic colours (`FreshGreen`, `StatusSuccess`, `CrimsonRed`, `CarnationOrange`, `VibrantBlue`, `PureWhite`, `LightGray`, …) in 24 screen/dialog files replaced with `proColors.*` / `colorScheme.*`. White-on-fill text pairs were moved to the matching `on*` token.

### Modified files
Theme: `Color, Shape, Spacing, Theme, Type`, new `ProHostColors`.
Components: `CommonComponents, ProHostBrandComponents (new), InAppUpdateBanner, OfflineStatusBanner, KycCompletionBanner, KycVerificationDialog, SubscriptionRenewalDialog, RentalBookingDialog, OwnerIncomingRequestsView, SpaceAvailabilityCalendarView, SubdivisionEditorSection, CountryPickers, AdminGrantAccessCard, ListingVerificationDialog, drawer/AppDrawerContent, drawer/DrawerIdentityCard, dialogs/DrawerDialogsHandler`.
Screens: `OwnerHub, SpaceDetails, OwnerSubscriptions, SpecialistProfile, MyBookings, ManageListing, OwnerAnalytics, OwnerRentingProgress, Kyc, Discovery, AdminConsole` (dialog swap only).
Navigation: `ProHostNavGraph`.

Left as-is on purpose: black scrims and white glyphs drawn over listing photos (always on a dark overlay),
the WhatsApp glyph tint, and `LebanonMapCanvas` / listing-type marker colours (map data colours).
`ADMIN` screens receive the new palette automatically through the shared theme and components.

## 2. Contrast (WCAG 2.1, computed)
| Pair | Light | Dark |
|---|---|---|
| `onPrimary` on `primary` | 4.76 | 7.11 |
| `primary` text on surface | 4.76 | 6.33 |
| `onSecondary` on `secondary` | 4.58 | 7.37 |
| `secondary` text on surface | 4.58 | 6.58 |
| success / `onSuccess` | 5.05 | 9.06 |
| warning / `onWarning` | 4.61 | 8.87 |
| `onSurfaceVariant` on surface | 4.90 | 6.58 |
| `error` on surface | 4.98 | 6.56 |
| `onSuccess/Warning/Error/InfoContainer` | 6.38 – 9.44 | 7.05 – 10.55 |
| `onBrandHeader` on header gradient | 7.66 – 10.26 | 12.34 |
| `header{Success,Warning,Error}` tints on gradient | ≥ 4.98 | ≥ 7.3 |
| White on WhatsApp green (`#1E7E34`) | 5.14 | 5.14 |

Findings fixed while testing: white on `#F25F4C` buttons (3.22), white on `#4CAF72` badges (2.73),
white on `#F98B1D` badges (2.40), `OxfordBlue` text on dark surfaces, light-only shimmer, and white text
hard-coded onto fills that turn light in dark mode.

## 3. Frontend / Backend Integrity Report ("System Gaps")

Verified OK: all 22 Android callables (`getHttpsCallable("…")`) have a matching export in `functions/src`;
every drawer action id (`owner_billing, legal_documents, fcm_alerts, admin_audit, admin_gov, system_debugger`) is handled by `DrawerDialogsHandler`.

### A. Frontend elements without (complete) backend/logic
| # | Where | Gap |
|---|---|---|
| A1 | `SpecialistProfileScreen.kt:123` | `onNavigateToIdUpload = { /* … */ }` is an empty handler, and `KycCompletionBanner` declares the parameter but never calls it. |
| A2 | `KycCompletionBanner.kt` | "Add a profile picture" and "Add your address" steps have `nextAction = null`: the banner tells the user what to do but offers no button; only the email step is actionable. |
| A3 | `AppDrawerContent.kt` (`SpecialistDrawerContent` / `AdminDrawerContent`) | `pendingRequestsCount` is accepted and passed from `ProHostNavGraph:430/451` but never rendered. The documented red dot on "Renting Requests" does not exist (its helper, `DrawerBadgedIcon`, was unused and has been removed; `ProHostDrawerItem(badgeDot = …)` is now available to wire it). |
| A4 | `ProHostNavGraph.kt:62` vs `AppDrawerContent.kt` | `OwnerRentalRequests` is a Pro Host destination, but the Pro Host drawer has no entry for it. It is only reachable through the "Renting Progress" screen's `onOpenRequests`. |
| A5 | `ProHostNavGraph.kt:48` vs `AppNavTab.kt:14` comment | Comment says Specialist and Pro Host share one tab set; code gives Pro Host `ManageListings / OwnerRentingProgress / Profile` and no "My Rentals" (`MyBookingsScreen`), so a Pro Host cannot open their own practitioner bookings from the bottom nav. |
| A6 | `AppDrawerContent.kt` `ProHostDrawerFooter` | Version chip is hard-coded `"v2.5"`; `versionName` is `1.0.28`. |
| A7 | `LoginAuthScreen.kt:127` | `getOrDefault("mock_web_client_id")` — a mock client id is used if `default_web_client_id` is missing, so Google sign-in fails with an opaque error instead of being hidden/disabled. |
| A8 | `ProHostRepository.seedDemoContent()` ↔ `AdminConsoleScreen:2355` | Demo seeding (`DemoDataGenerator`) is wired to a release-reachable Admin button writing to production Firestore. Not Specialist/Host UI, but flagged as mock data with a live backend path. |

### B. Broken, dead or weak backend logic
| # | Where | Gap |
|---|---|---|
| B1 | `functions/src` `resendEmailVerification` | Exported callable that no client calls (Android uses `sendVerificationEmailLink`; `FirebaseFunctionsClient.resendEmailVerification()` is a Kotlin alias to it). Dead backend export. |
| B2 | `ProHostViewModel` (23 functions: `acknowledgePayment, toggleSavedSpace, setListingStatus, addBlackoutSlot, removeBlackoutSlot, updateSpaceOperatingSchedule, addCustomFormula, deleteFormula, addSubdivision, removeSubdivision, sendInquiryEmail, resendEmailVerification, launchGooglePaySubscription, refreshTopHashtags, addUserSuggestedSchemaItem, initPlayBilling ×8 inner catches`) | The `success == false` path toasts, but a **thrown** exception is only `Log.e("Operation failed")`, so the user gets no feedback (e.g. `resendEmailVerification` shows nothing on a network exception). `initPlayBilling`'s catches log without a `BillingMessage`, which CLAUDE.md forbids. |
| B3 | `functions/src/lib/callable.ts:18` | `ENFORCE_APP_CHECK = false`. Callables currently accept unverified clients (known and documented, listed for completeness). |
| B4 | `AuthViewModel.kt:30` | `@Deprecated typealias`/alias still present beside its replacement. |
| B5 | `FirebaseAuthService.kt:336`, `PlayBillingManager.kt:167`, `ProHostMessagingService.kt:33` | `@Suppress("DEPRECATION")` over deprecated Firebase / Billing / FCM APIs, to migrate on the next SDK bump. |
| B6 | `OwnerAnalyticsScreen` | Demand-mix and yield figures are computed client-side from streamed bookings (no server aggregate), unlike Admin analytics (`getAdminAnalytics`). Correct, but unbounded on large histories. |

### C. Not found
No commented-out network calls, no `TODO/FIXME` markers, and no other empty `onClick` handlers in Specialist/Host screens.

## 4. Follow-ups (outside "zero structural change")
Wire A1–A5 (an ID-upload scroll target, a drawer dot, a Pro Host "Renting Requests" entry); surface B2 errors
through a shared `showError()`; replace A6 with `BuildConfig.VERSION_NAME`.
