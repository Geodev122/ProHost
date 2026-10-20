---
name: prohost-design
description: How to change ProHost's Android UI (Jetpack Compose, Material 3) without breaking it — design tokens, the one-of-each component set, the audit roadmap in docs/design/UI_AUDIT.md, and how to verify every change with screenshots in light, dark, large font and RTL. Use for any visual, theme, component, layout, motion, accessibility, copy or localisation change in app/src/main/java/com/example/ui.
---

# ProHost design work

ProHost is a native Android app (Kotlin, Jetpack Compose, Material 3) for renting clinic/therapy/meeting rooms.
Two audiences: **Specialists** (book rooms; Steel Blue accent) and **Pro Hosts** (list rooms; orange accent).
The design plan is `docs/design/UI_AUDIT.md` (score 6.4/10, phases 0–3). This skill is how to execute it.

Precedence: the owner's words → `CLAUDE.md` (hard product rules, many are UI rules) → this skill → general taste.

## The loop for every change

1. **Pick one audit item** (or one owner request). Name the phase and the success check before touching code.
2. **Find every call site first** (`Grep` the component, colour, size or string). Count them. A change to a shared
   token or component reaches all of them — that is the point, and the risk.
3. **Change the lowest layer that fixes it**: theme token → shared component → call site. Never restyle one
   screen to fix a system problem.
4. **Keep APIs stable.** Rename by adding the new thing and leaving the old name as a `@Deprecated` alias for one
   release. Never change a composable's parameters and its look in the same commit.
5. **Verify visually** with the Design Screenshots workflow (see `references/verify.md`): before and after, light,
   dark, small phone at the text cap, RTL, Arabic. Look at the PNGs yourself (Read shows images). No screenshot, no claim that it looks right.
6. **Run the gates**: `node scripts/prohost-debugger.js` (NIGHTHAWK, 0 critical/high), Android CI green (unit tests
   + compile; there is no local Android SDK in the cloud sandbox — CI compiles).
7. **One theme/component change or one screen per commit**, message says what changed visually.

## Tokens (the only values UI code may use)

Files: `ui/theme/Color.kt`, `ProHostColors.kt` (`MaterialTheme.proColors` status roles), `Theme.kt` (schemes),
`Type.kt`, `Shape.kt`, `Spacing.kt`.

- **Colour**: `MaterialTheme.colorScheme.*` roles or `MaterialTheme.proColors.*`. Never a raw `Color(0x…)` or a
  brand constant (`FreshGreen`, `StatusError`, `CarnationOrange`) in a screen — those don't switch in dark mode.
  Brand: Steel Blue `#2B5A8C` (dark `#93B8E0`) primary; orange `secondary` `#D2432F` for text (4.6:1),
  `#F25F4C` only for fills/large shapes; never `#246BEE`.
  Phase 0 adds the surface ladder (`surfaceContainerLowest…Highest`, `surfaceBright/Dim`) — values in the audit.
- **Type**: `MaterialTheme.typography.*` only; never `fontSize =` in a screen. Floor 12 sp. Manrope for display,
  headline and titleLarge; system sans for reading text. Weights: Black only for the Premium price/hero numbers.
- **Spacing**: `Spacing.xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32`; screen gutter 16 dp. Round off-scale
  2/6/10/14 dp to the nearest step.
- **Radius** (`Radius.kt`): xs 6 chips/badges · sm 12 cards/fields · md 20 tiles/hero · lg 28
  sheets/dialogs/nav pill · full.
- **Elevation** (`Elevation.kt`): 0 · 1 cards on page · 3 selected tile / scrolled bar · 6 floating nav,
  FAB, sheets. Prefer tonal surface steps over shadows.

## One of each (components)

| Need | Use | Never |
|---|---|---|
| Main action | `ProPrimaryButton` (orange) | raw `Button()` (defaults to blue) |
| Other actions | `CustomButton` variants / `TextButton` | mixing Outlined + Text for the same rank |
| Card | `ProCard` (one, with variant + `onClick`) | `Modifier.clickable` on a Card (ripple ignores shape) |
| Question / confirm | `ProHostDialog` | hand-built `Dialog(` surfaces |
| Form or picker | `ProHostBottomSheet` / `ResizableBottomSheet` | raw `ModalBottomSheet` |
| Permission/consent prompt | `SystemStylePermissionDialog` | anything else (CLAUDE.md) |
| Status / info strip | `ProHostAlertBanner` | hand-made banners |
| Empty / loading | `ProEmptyState` (with a next step) / `ShimmerLoadingList` | spinners for first load, plain text |
| Feedback after an action | `UiMessages.show(...)` → shell Snackbar (supports Undo) | new `Toast.makeText` in UI |
| Count / dot | `ProHostCountBadge` / `ProHostDotBadge` | `AttentionDot`, `BadgedControl` |
| Text input | `InputField` | raw `OutlinedTextField` |
| Sheet/dialog analytics | `TrackScreen(name)` | nothing (every overlay reports itself) |

## Strings and Arabic

Every user-facing string is a resource with an Arabic twin (`values-ar`). Composable: `stringResource(R.string.x, args)`;
non-composable (remember, builders, view models): `uiText(R.string.x, args)`; data-layer English labels at display sites:
`localizedLabel(enum.displayName)`. Never translate values that are stored, compared or sent to analytics. Check the `ar`
screenshot variant for every changed screen.

## Product UI rules that must not regress (from CLAUDE.md)

Floating pill bottom nav overlaying content (`LocalBottomNavInset`); specialists have no drawer; white textured
page background via `proHostScreenBackground()`; Explore draws its own header; listing page bottom bar is
`DetailsBookingBar`; Premium: only the selected plan card is orange, always exactly two `PlanTile`s; no
`FLAG_SECURE`; Profile and Premium are `LazyColumn`s with sections gated outside `item {}`; first-load lists use
`ShimmerLoadingList`; never resize an open sheet's content height.

## Design judgement (distilled from Anthropic frontend-design, adapted to a native app)

- **Spend boldness in one place per screen** (the listing photo, the Premium price). Everything around it is quiet:
  one accent colour per screen, one primary action, no decoration that doesn't carry information.
- **Hierarchy by size and weight, not by colour or boxes.** Don't wrap every block in a card; a card marks a
  separate, tappable object. Same radius/shadow on everything flattens the hierarchy.
- **No template tells**: ALL-CAPS eyebrow over every heading, middle-dot meta strings everywhere, numbered
  01/02/03 markers for non-sequences, gradient washes as decoration.
- **Motion answers the user** (open, expand, confirm, navigate). No decorative loops; respect the system
  "remove animations" setting (`Settings.Global.ANIMATOR_DURATION_SCALE == 0` → snap).
- **Words are UI.** Plain verbs, sentence case, the user's vocabulary ("Your spaces", not "Catalog Spaces";
  "Admin", not "Super Admin Node"). A button says what happens ("Send request"), the result echoes it ("Request
  sent"). Errors say what happened and what to do; empty states invite the next step. `…` not `...`.

## References (read the one you need)

- `references/compose-rules.md` — Compose patterns: state, effects, lists, theming, previews, performance.
- `references/mobile-ux-checklist.md` — review checklist: touch, a11y semantics, forms, feedback, content, i18n.
- `references/motion.md` — durations, easing and the Compose APIs for each transition the audit asks for.
- `references/verify.md` — screenshot workflow, how to read results, before/after comparison, NIGHTHAWK.
