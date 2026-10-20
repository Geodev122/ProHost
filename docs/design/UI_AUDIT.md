# ProHost Design Audit

Oct 19, 2026

ProHost scores 6.4/10 overall: a confident brand and solid flows, held back by an unfinished design system (no surface roles, an unused brand font, 15+ corner radii, three card types) and accessibility debt. Every fix below is additive or token-level first, so nothing in the UI or backend has to break.

## Scorecard

The brand and the flows are strong; the system underneath them is what costs points. Scores are out of 10, judged against Google's Material 3 apps and the best local marketplaces.

| Dimension | Score | Why |
| --- | --- | --- |
| Brand and identity | 7.5 | Steel Blue + Carnation Orange is distinctive and consistent; the 3D check logo and role tags feel owned. Manrope never reaches the screen. |
| Information architecture | 7 | Role-specific shells (Specialist tabs, Pro Host drawer) map to real jobs; Pro Hosts can't reach Explore from the bottom nav. |
| Key flows (book, host, pay) | 7 | Booking, requirements-in-place and Premium are well sequenced and policy-correct; some steps live in full-screen dialogs. |
| Color system | 6 | Good brand tokens and an audited contrast set, but no surface container roles, light-only constants in dark mode, white on green at 2.6:1. |
| Components | 5.5 | Strong shared kit (alert banner, empty state, shimmer) undermined by three card types, two button systems and 15 hand-built dialog surfaces. |
| Layout and spacing | 5.5 | Spacing tokens exist but 376 raw spacedBy values and off-scale 6/10/14 dp gaps make rhythm uneven. |
| Typography | 5 | Sensible Material roles, but the scale is inverted (titleLarge 20 > headlineSmall 18) and 11 sp is the most used style (305 uses). |
| Shape and elevation | 5 | 15+ corner radii and 19 elevation values; no elevation tokens. |
| Feedback and states | 5 | 75 Toasts and one Snackbar host; good shimmer and empty states, missing on admin tabs. |
| Motion and polish | 5 | Nice nav pill and banner motion; no screen transitions, no list item animation. |
| Accessibility | 4.5 | Many touch targets under 48 dp, almost no semantics, fixed heights that clip at large font sizes. |
| Localisation and RTL | 3 | No string resources (560 literals), no Arabic, plurals by concatenation, in a market where Arabic is primary. |

**Overall 6.4/10**: a good product wearing an unfinished design system. Phases 0–1 of the roadmap alone would move it to about 7.8 without touching a single flow.

## Top 10 fixes

These ten carry most of the gain; each is safe because it changes tokens or one shared component, not screen logic.

| # | Fix | Impact | Risk | Effort |
| --- | --- | --- | --- | --- |
| 1 | Define `surfaceContainerLowest…Highest`, `surfaceBright`, `surfaceDim` in both color schemes (Theme.kt) | High: menus, date pickers, sheets and chips stop showing stock lavender | Very low | 1 h |
| 2 | Make every primary call to action orange through `ProPrimaryButton` (raw `Button()` defaults to Steel Blue) | High: one clear "do this" color | Low | 0.5 day |
| 3 | Use Manrope for display, headline and title styles; fix the inverted scale | High: the brand reads premium immediately | Low | 2 h |
| 4 | Raise the floor: labelSmall 11 → 12 sp, no raw sizes below 12 sp | High for readability | Low (check 18 maxLines=1 spots) | 0.5 day |
| 5 | One Snackbar host in the shell; route the 75 Toasts through it | Medium-high: consistent, accessible feedback with Undo | Low | 1 day |
| 6 | Radius and elevation tokens (4 radii, 4 elevations) and replace literals | Medium: calmer, more coherent surfaces | Very low | 1 day |
| 7 | 48 dp minimum touch targets (map arrows, attendee ± buttons, chips, save heart) | High for usability | Low | 0.5 day |
| 8 | Fix the double app bar on Saved and Manage listing; one top bar pattern | Medium | Low | 2 h |
| 9 | Merge `ModernCard`/`ProCard`/`ProSurfaceCard` into one `ProCard(variant)`; cards use `onClick` so the ripple follows the shape | Medium | Low | 1 day |
| 10 | Extract strings to `strings.xml` and ship `values-ar` | Very high for Lebanon and Syria | Medium (volume) | 1–2 weeks |

## Color and theme

The palette is right; the gaps are in roles the theme never defines, so Material fills them with its own lavender defaults.

**What's wrong**

- No `surfaceContainer*`, `surfaceBright` or `surfaceDim` in either scheme (`ui/theme/Theme.kt:39-118`). Seven DropdownMenus, seven DatePickerDialogs, default ModalBottomSheets and chips read these roles and show off-brand purple-grey.
- Light `tertiary` FreshGreen #4CAF72 with white `onTertiary` is 2.6:1, below the 4.5:1 text minimum.
- `onSurfaceVariant` is OxfordBlue at 70% alpha (`Color.kt:50`), then often faded again to 50–60% (`MyBookingsScreen.kt:870`, `AvailabilitySheet.kt:244`): secondary text drops under 4.5:1 on tinted surfaces.
- Light-only brand constants used directly in screens: StatusError 12×, FreshGreen 13×, the Login success banners (`LoginAuthScreen.kt:686-729`), admin tabs. They glow in dark mode.
- Dark mode shows a light #ECEDF0 splash (no `values-night/`).
- About 30 legacy aliases (ProTeal, Sandstone, Clinical…) and unused template colors still ship.

**How to fix it, safely**

1. Add the surface ladder to both schemes, tinted toward Steel Blue:

| Role | Light | Dark |
| --- | --- | --- |
| surfaceContainerLowest | #FFFFFF | #0F1420 |
| surfaceContainerLow | #F6F8FB | #171E2C |
| surfaceContainer | #F0F3F8 | #1B2333 |
| surfaceContainerHigh | #EAEEF4 | #222B3D |
| surfaceContainerHighest | #E3E8F0 | #2A3447 |
| surfaceBright / surfaceDim | #FBFCFE / #DCE2EB | #303B50 / #121824 |

2. Darken light `tertiary` to #2E7D4F (already defined as StatusSuccessStrong) so white text passes at 5.0:1; keep FreshGreen for fills and icons only.
3. Make `onSurfaceVariant` an opaque #5A6372 (light) and #B4BDCC (dark) and stop stacking alpha on it.
4. Replace direct status constants with `MaterialTheme.proColors.success/warning/error` and their `on*` pairs; add `successContainer`/`onSuccessContainer` for the Login banners.
5. Add `res/values-night/colors.xml` with a #121824 splash background.
6. Delete the legacy aliases once a grep shows no users (NIGHTHAWK can flag new ones).

**Verify**: render the 11 previews in light and dark; open a DropdownMenu and a DatePicker on device; check secondary text with a contrast checker at 4.5:1.

## Typography

The app bundles Manrope but never shows it, and its most used style is 11 sp: fixing both is the single cheapest visual upgrade.

**What's wrong**

- Manrope is assigned only to displayLarge and headlineLarge (`Type.kt`), and neither is used anywhere. Everything renders in the system font.
- The scale is inverted: titleLarge 20 sp is larger than headlineSmall 18 sp, so a section title can outweigh a page headline.
- labelSmall (11 sp) is used 305 times, more than any other style; 19 raw sizes go down to 8–10 sp (`MyBookingsScreen.kt:613-896`, `LebanonMapCanvas.kt:713-766`, `SpecialistProfileScreen.kt:213`).
- Weight Black appears 14 times and Bold 252 times, so emphasis stops meaning anything.
- 10 letterSpacing overrides use 7 different values.
- `LoginAuthScreen` passes `fontSize = typography.X.fontSize` instead of the style, losing line height and weight.

**Target scale** (Manrope for headings, system sans for reading text)

| Role | Size / line height | Weight | Font | Use |
| --- | --- | --- | --- | --- |
| displaySmall | 28 / 36 | ExtraBold | Manrope | Hero numbers, Premium price |
| headlineMedium | 24 / 32 | Bold | Manrope | Page titles |
| headlineSmall | 20 / 28 | Bold | Manrope | Sheet titles |
| titleLarge | 18 / 24 | SemiBold | Manrope | Section headers (ProSectionHeader) |
| titleMedium | 16 / 22 | SemiBold | System | Card titles |
| bodyLarge / bodyMedium | 16 / 24, 14 / 20 | Regular | System | Reading text |
| labelLarge | 14 / 20 | SemiBold | System | Buttons |
| labelMedium | 12 / 16 | Medium | System | Chips, meta |
| labelSmall | 12 / 16 | Medium | System | Badges (floor: never below 12) |

**How to fix it, safely**

1. Change `Type.kt` only: assign Manrope to display, headline and titleLarge; reorder sizes as above. Every screen picks it up with no code change.
2. Raise labelSmall to 12 sp, then search for the 18 `maxLines = 1` without `overflow = TextOverflow.Ellipsis` and add it so nothing clips.
3. Replace raw `fontSize` in the 19 spots with the nearest style.
4. Limit weight: Black only for the Premium price and hero numbers; Bold for titles; SemiBold for emphasis in body.
5. One uppercase label style: labelMedium, +0.8 sp letter spacing, used by every "YOUR PLAN"-style label.

**Verify**: screenshot Premium, Explore list and Profile before and after at font scale 1.0 and 1.3.

## Spacing, shape and elevation

Three small token sets would replace about 600 literals and give every screen the same rhythm.

**What's wrong**

- Spacing tokens exist (xs 4 … xxl 32) but most code bypasses them: 376 raw `spacedBy` vs 56 tokens, about 202 raw paddings vs 126. Off-scale values are common: 6 dp (40×), 2 dp (39×), 10 dp (32×), 14 dp (17×). Worst: AdminAnalyticsScreen 0 tokens / 31 raw, DiscoveryScreen 1 / 59, OwnerSubscriptionsScreen 4 / 78.
- Screen gutters differ: 16 dp on most screens, 20 dp on Login, KYC and the filter sheet, 12 dp on the Explore header.
- 44 `RoundedCornerShape(n.dp)` calls with 15+ radii (3, 4, 5, 6, 12, 14, 16, 18, 20, 22, 24, 28 and one-sided variants); OwnerSubscriptionsScreen alone uses 10.
- 19 different elevation literals (shadow 2/3/4/6/10/12, tonal 0/1/2/4/6/8, card 2/3/6) plus 7 `Modifier.shadow` calls; no Elevation tokens.

**The token sets**

| Token set | Values | Replaces |
| --- | --- | --- |
| Spacing (keep) | 4 · 8 · 12 · 16 · 24 · 32 + `gutter` 16 | 2/6/10/14/18 dp literals (round to the nearest step) |
| Radius (new `Radius.kt`) | xs 6 (chips, badges) · sm 12 (cards, fields) · md 20 (tiles, hero) · lg 28 (sheets, dialogs, nav pill) · full | the 15+ literal radii |
| Elevation (new `Elevation.kt`) | level0 0 · level1 1 (cards on page) · level2 3 (selected tile, app bar on scroll) · level3 6 (floating nav, FAB, sheets) | the 19 literal values |

**How to fix it, safely**

1. Add `Radius` and `Elevation` objects beside `Spacing`; map Material `Shapes` to them so `MaterialTheme.shapes.*` users change for free.
2. Replace literals file by file, starting with the three worst screens above. Rounding 6→8 and 10→12 shifts layouts by 2 dp at most.
3. Set one gutter: `Spacing.gutter = 16.dp` on every screen root and sheet.
4. Add a NIGHTHAWK check that counts raw `RoundedCornerShape(` and `elevation = n.dp` literals in `ui/`, so new ones are flagged.

**Verify**: before/after screenshots of Premium, Explore and the details page; diffs should be a few pixels, never a moved element.

## Components

The shared kit is good; the problem is that every core component has two or three rivals, so the same action looks different on each screen.

| Kind | Today | One-of-each target | Notes |
| --- | --- | --- | --- |
| Primary button | `ProPrimaryButton` (orange, 11 uses) vs raw `Button()` (Steel Blue, 31 uses) | `ProPrimaryButton` for every main action; `CustomButton` variants for the rest | Details "Availability" (`RoomCards.kt:596`), "Check Availability" (`:326`), filter "Show N Results" (`DiscoveryScreen.kt:771`) are blue today |
| Card | `ModernCard`, `ProCard` (alias), `ProSurfaceCard`, raw `Card` 26× | `ProCard(variant = Elevated / Outlined / Filled, onClick?)` | Use Card's `onClick`, not `Modifier.clickable`, so the ripple follows the rounded shape |
| Dialog | `ProHostDialog` 32×, `SystemStylePermissionDialog`, 15 raw `Dialog(` each drawing its own surface | `ProHostDialog` for questions; `ProHostBottomSheet` for forms; full screens for wizards | `CreateListingDialog` (1411 lines) and `RentalBookingDialog` (1556) are full-screen flows inside a 96%×95% dialog: make them full-screen destinations |
| Bottom sheet | `ProHostBottomSheet` 8×, one raw `ModalBottomSheet` | `ProHostBottomSheet` | `OwnerIncomingRequestsView.kt:378` uses default styling |
| Banner | `ProHostAlertBanner` 15× + hand-built ones in Login, NavGraph, Premium | `ProHostAlertBanner` | Login's light-only success banner also breaks dark mode |
| Empty / loading | `ProEmptyState` 13×, `ShimmerLoadingList`; custom ones in My Rentals, plain text in Saved | Same two, everywhere | Admin tabs have neither |
| Badge / dot | `ProHostCountBadge`, `ProHostDotBadge`, `AttentionDot`, `BadgedControl`, hand-made Surface chips | `ProHostCountBadge` + `ProHostDotBadge` | Delete `AttentionDot` and `BadgedControl` |
| Text field | `InputField` 32×, raw `OutlinedTextField` 50× | `InputField` | Gives one label, error and helper style |
| Avatar | `AvatarCircle`/`ProMemberAvatar` vs `ProHostDrawerAvatar` | One `ProAvatar(size)` |  |

**How to fix it, safely**: change call sites, not the components. Each swap is a one-line change behind the same visual role, done screen by screen with a screenshot check. Keep old names as deprecated aliases for one release so nothing breaks mid-way.

**Also**: `WorkspaceCard` passes the same handler to `onClick` and `onDetailsClick` (`DiscoveryScreen.kt:812-850`), so its "Check Space" button duplicates the card tap. Drop the button, or make it "Check availability", which opens the sheet directly.

## Navigation and shell

The floating pill nav is the app's best piece of UI; the rest of the shell needs one top-bar pattern, transitions and plain-language labels.

**What's wrong**

- **Double app bars, likely.** The shell shows its top bar whenever no listing is open (`ProHostNavGraph.kt:539`), yet Saved (`MyFavoritesScreen.kt:72`) and Manage listing (`ManageListingScreen.kt:91`) draw their own `TopAppBar`. Saved's title is only an "N Saved" pill, empty at 0.
- **The drawer opens from a MoreVert (⋮) icon** in `ProHostTopAppBar` (`CommonComponents.kt:1361`), which Android users read as "more options". The full-screen variant uses a Menu icon and Black weight.
- **Pro Hosts can't reach Explore from the bottom nav**, only from the drawer, though they browse to price their own spaces.
- **Jargon in labels**: "Super Admin Node", "Host & Owner Hub", "Practitioner Circle" (`CommonComponents.kt:1423-1425`); admin drawer "CENTRAL SECURITY CORES", "SYSTEM AUDIT & PRICING". Drawer section labels use three different colors.
- **No transitions**: navigation is hand-rolled state, so tabs, details and full-screen pages swap without motion; the drawer has `gesturesEnabled = false`.
- **Explore for Pro Host and Admin** shows both an app bar titled "Explore" and the floating Explore header.

**How to fix it, safely**

1. One rule: the shell owns the top bar. Pages that need their own (Saved, Manage listing) set a flag the shell reads (`showShellTopBar = false`) instead of stacking a second bar.
2. Use the Menu (☰) icon for the drawer and keep MoreVert for overflow menus; titles in titleLarge (Manrope), never Black.
3. Pro Host bottom tabs: Listings · Explore · Rentals · Profile, with Progress moved into Listings as a segment (or a 5th tab if the pill has room at 360 dp width).
4. Rename to people's words: "Admin" / "Host" / "Member" subtitles; admin drawer "Security", "Billing & audit", "Pro Host access"; one section-label color (`onSurfaceVariant`).
5. Add an `AnimatedContent` around the shell body keyed by the destination: fade-through (120 ms out / 180 ms in) between tabs, slide-up for full-screen pages, shared-axis X for details. No routing change is needed.
6. On Explore for Pro Host/Admin, hide the shell app bar and use the same floating header Specialists get (brand in the pill).

**Verify**: walk every destination for all three roles and confirm one bar each; record a screen video to judge the transitions.

## Screen by screen

Each screen's three highest-value changes, all inside existing layouts.

| Screen | Score | Change 1 | Change 2 | Change 3 |
| --- | --- | --- | --- | --- |
| Explore (list + map) | 7 | Raise card cover images from 100 dp to 16:9 (about 180 dp): the photos sell the space | Search field text 13 sp → bodyLarge 16 sp, and submit on IME Search without a separate ✓ | Remove the duplicate "Check Space" button, or make it open availability directly |
| Space details | 7.5 | The hero likely doesn't reach under the status bar (shell insets consumed first, `SpaceDetailsScreen.kt:425`): pass insets through so the photo bleeds to the top | Merge the two copies of the photo carousel arrows and dots (`SpaceDetailsScreen.kt:353-408`, `RoomCards.kt:105-156`) into one component with 48 dp arrows | Make the bottom bar's "Availability" the orange primary; show "from $X / unit" in titleLarge Manrope |
| Premium | 8 | Ten custom radii and Black weight on 7 elements: move to tokens; Black only on the price | Purchase button: fixed 52 dp → `heightIn(min = 52.dp)` so large fonts don't clip | Hero subtitle at 0.75 alpha on the gradient: use full-opacity onBrandHeader for contrast |
| Profile | 6 | Replace developer tiles ("Firestore Sync – Real-time bridge active", "Potential Yield – Monthly gross MRR", "Catalog Spaces") with people's words: "Synced", "Monthly income", "Your spaces" | Split the 1,030-line composable into sections (identity, readiness, stats, settings) for faster recomposition | App update row: "Install" and "Check Update" in labelSmall → a proper `ProOutlinedButton` |
| Login and registration | 6.5 | No IME or status-bar insets (`LoginAuthScreen.kt:274-283`): add `imePadding()` + `statusBarsPadding()` so the keyboard never hides the 4 fields | Replace the hand-built banners with `ProHostAlertBanner` (fixes dark mode) | One secondary-action style (TextButton) instead of a TextButton/OutlinedButton mix |
| My Rentals | 6.5 | Six 10 sp labels (`MyBookingsScreen.kt:613-896`) → labelMedium 12 sp | Use `ProEmptyState` instead of the custom one (`:248-300`) | Status as a `ProStatusBadge` at the card's top-right, same place on every card |
| Owner Hub / Manage listing | 6.5 | Fix the double app bar on Manage listing | Occupancy cells: add a legend row ("Free · Booked") for colour-blind users | The wizard: full-screen destination with a top progress bar (Step 1 of 3), not a dialog |
| Admin | 5.5 | Add `ShimmerLoadingList` and `ProEmptyState` to every tab | Raw dp and light-only colors in Users, Schema, Security tabs → tokens | Tab labels: drop the counts from the tab text into a badge |

## Feedback, states and motion

Feedback is the most visible inconsistency after color: 75 Toasts against one Snackbar host means messages look and behave differently, can't be undone and are missed by TalkBack users.

**What's wrong**

- 75 `Toast.makeText` calls (34 in ProHostViewModel); Snackbars only on the details page.
- Toasts can't carry actions: "Slot is back on offer" or "Listing paused" can't offer Undo.
- Empty and loading states are missing on admin tabs; Saved shows a plain-text error.
- Motion is limited to the nav pill and banners: no list item animations, no screen transitions, no Crossfade on loading → content.
- Haptics only on long-press and plan selection; purchase and request success play sounds via MediaPlayer without honouring silent mode.

**How to fix it, safely**

1. Add one `SnackbarHostState` in the shell and a `UiMessage` flow in ProHostViewModel (`message`, `actionLabel?`, `onAction?`). `reportFailure` and the success Toasts emit to it; the shell shows them above the nav pill (`LocalBottomNavInset`). Keep Toast only where no UI is on screen.
2. Undo for reversible actions: pause/resume listing, remove a blackout, unsave a space.
3. `Modifier.animateItem()` on LazyColumn items in Saved, My Rentals, requests and notifications; `Crossfade` from shimmer to content.
4. Haptics: `Confirm` on request sent / plan activated, `Reject` on errors; route sounds through the notification audio stream so silent mode mutes them.
5. Every list gets the trio: shimmer while loading, `ProEmptyState` with a next step when empty, `ProHostAlertBanner` + Retry on error.

**Verify**: TalkBack announces each Snackbar; airplane-mode run shows an error banner with Retry on every list.

## Accessibility and localisation

These two carry the lowest scores and the biggest audience upside: Arabic is the first language of most ProHost users, and Play's pre-launch report flags small touch targets.

**Accessibility: what's wrong**

- Touch targets under 48 dp: 20–28 dp icon buttons (`SubdivisionEditorSection.kt:596`, `RoomCards.kt:112/130`, `DrawerDialogsHandler.kt:173`), 32–36 dp (attendee ± at `RentalBookingDialog.kt:1464/1477`, save heart at `CommonComponents.kt:1072`, admin row actions).
- Semantics: one `Modifier.semantics` in the whole app; no `heading()`, `stateDescription` or `liveRegion`; plan tiles and room tabs aren't announced as selected.
- Fixed heights hold text: nav pill 64 dp, plan ribbon 28 dp, buttons at 40/50/52 dp, the 28 dp notification "Open" button with 10 sp text. At font scale 1.3 text clips.
- 18 `maxLines = 1` without ellipsis.
- Contrast: orange text on light surfaces (RoomCards price, Owner Hub), green text on 12% green, secondary text faded below 4.5:1.

**Accessibility: how to fix it**

1. A `Modifier.minimumTouchTarget()` helper (or `minimumInteractiveComponentSize()`) on every icon button and clickable chip; visuals can stay small inside the 48 dp hit area.
2. `semantics { heading() }` on `ProSectionHeader`; `selected` + `stateDescription` on PlanTile, room tabs and filter chips; `liveRegion = Polite` on banners.
3. `heightIn(min = …)` instead of `height(…)` wherever text sits inside.
4. Text in brand orange uses `secondary` (#D2432F, 4.6:1) not CarnationOrange (#F25F4C, 3.2:1).

**Localisation: what's wrong**

- `strings.xml` holds only `app_name`; about 560 literal strings; 0 `stringResource` calls.
- No `values-ar`; plurals built by concatenation (`RoomCards.kt:528`, `DiscoveryScreen.kt:777`).
- RTL is enabled in the manifest and most arrows auto-mirror (one ChevronRight at `OwnerIncomingRequestsView.kt:350` doesn't).

**Localisation: how to fix it**

1. Extract strings screen by screen (Android Studio "Extract string resource"), starting with the Specialist path: Explore, Details, booking, My Rentals, Profile.
2. `plurals.xml` for counts ("1 room / 3 rooms", "N results").
3. `values-ar/strings.xml`, translated with the website's Arabic copy (`/ar`) as the voice reference; numbers stay Western digits in prices.
4. Test with Settings › Languages › Arabic: mirrored layouts, chips and the nav pill.

**Verify**: Accessibility Scanner on 8 key screens (zero touch-target and contrast warnings); TalkBack walkthrough of booking; screenshots at font scale 1.3 and in Arabic.

## Roadmap

Four phases, each shippable on its own and ordered so earlier ones make later ones cheaper; nothing changes a flow, a data model or the backend.

| Phase | Scope | Why it can't break anything | Effort | Score after |
| --- | --- | --- | --- | --- |
| 0 — Tokens | Surface roles, tertiary and onSurfaceVariant fixes, Manrope + scale, labelSmall 12 sp, Radius and Elevation tokens, `values-night` splash | Theme files only; every screen inherits | 2–3 days | 7.0 |
| 1 — Components | Orange primary everywhere, one card, one dialog surface, Snackbar host, one banner, one app-bar rule, 48 dp targets | Call-site swaps behind the same roles; deprecated aliases for one release | 1–2 weeks | 7.8 |
| 2 — Screens | The screen-by-screen table, transitions, list animations, Undo, Pro Host Explore tab, plain-language labels | One screen per PR with before/after screenshots | 2–3 weeks | 8.4 |
| 3 — Reach | Semantics, font-scale fixes, string extraction, plurals, Arabic + RTL test | Additive resources; English stays the default | 2–3 weeks | 8.8 |

**Guardrails for every phase**

- Screenshot tests: extend the 11 `@Preview`s to light/dark × font 1.0/1.3 × en/ar and compare per PR.
- NIGHTHAWK checks: raw radii, raw elevation, `Toast.makeText` in `ui/`, font sizes below 12 sp, `Button(` outside the components package.
- Ship each phase in a release with a staged rollout (20% → 100%) and watch Crashlytics and the new GA4 screen names for drop-offs.

**Open question**: should Pro Hosts get Explore as a bottom tab (replacing Progress) or as a 5th tab? Both fit at 360 dp; a 5th tab keeps today's habits.

## Addendum: screenshot baseline (Oct 19, 2026)

The **Design Screenshots** workflow now renders 12 key screens in light, dark, font scale 1.3 and RTL
(`app/src/test/java/com/example/design/DesignScreenshotTest.kt`; how to fetch: `.claude/skills/prohost-design/references/verify.md`).
The first baseline confirms the audit and adds three findings:

- **RTL breaks text built by concatenation.** Number + unit strings reorder under Arabic layout: "72 hrs/wk Open"
  renders "hrs/wk Open 72", "3 slots open" → "slots open 3", "6 days/wk" → "days/wk 6". Fix with Phase 3 string
  resources using placeholders (`%1$d slots open`) and `BidiFormatter`/`⁨…⁩` isolation for numbers and
  prices, not with layout changes.
- **Font scale 1.3 clips chips and titles.** The hero "Verified" chip on the details page and long listing titles on
  Explore cards cut off ("Achrafieh Executive Me…" with the price squeezing it). Chips need `heightIn(min)` and no
  fixed width; card titles should wrap to two lines before ellipsis, with the price on its own line at large scales.
- **Warning colour used for a to-do.** Profile's "Ready to book" checklist uses the error/warning container (salmon)
  for a normal next step; it reads as an error. Use `surfaceContainerHigh` with a steel accent, keep red for real
  problems.

## Implementation status (1.0.43, Oct 20, 2026)

All four phases shipped in one release, verified with the Design Screenshots workflow (15 screens × light, dark, font
1.3, RTL, Arabic) and green CI.

| Phase | Done |
| --- | --- |
| 0 — Tokens | Surface container ladder (light/dark), tertiary #2E7D4F, opaque secondary text, Manrope headings with a monotonic scale and 12 sp floor, `Radius` and `Elevation` tokens |
| 1 — Components | One `ProCard`, orange main actions, `UiMessages` Snackbar (75 Toasts) with Undo, 48 dp targets + one `PhotoCarouselArrow`, shared badges/banners, dialogs on the Material spec, literal radii/elevations → tokens, NIGHTHAWK check 34 |
| 2 — Shell and screens | Pro Host bottom bar Listings · Explore · Rentals · Profile (Progress in the drawer); one header on Explore for every role; nav pill restyled (behaviour unchanged); one top bar per page; plain-language labels; page-change motion; Explore 16:9 cards + Availability button; neutral to-do checklist; My Rentals empty state; confirm haptic |
| 3 — Reach | ~1,340 UI strings in resources with a full Arabic translation, per-app language (Profile › Language), data labels localized at display sites, heading / live-region / selected semantics |

Re-score (owner review pending on device): ≈ 8.4 / 10.

Previously deferred, completed in the same release:
- Dark splash: night artwork (`drawable-night-*/splash.png`, lighter blue wordmark on #121824) and a matching
  `values-night` system splash colour.
- Every non-admin text field is `InputField` (34 call sites; `InputField` gained suffix, minLines, keyboard actions and
  text style).
- The listing wizard is a full-screen page: Close, "Step n of 3", a three-segment step bar (finished steps tappable),
  system Back steps back before closing; the country name left the step label.
- `app_error.area` comes from the English text of the fallback string resource, so Arabic sessions report the same areas.
- The Design Screenshots harness renders every sheet, dialog and form (24 overlays, opened by tapping where a person
  would) in all five variants.

Stays English on purpose: Google Play's period words ("first year", "4 months") and Admin-only tools.
