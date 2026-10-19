# Compose rules for UI changes

Distilled from UI/UX Pro Max's Jetpack Compose stack (52 rules), Vercel's composition patterns and React Native
list/image rules, mapped to this codebase. Severity: **H** = would break or visibly regress, **M** = quality.

## Structure
- **H** A composable renders state it receives and calls callbacks; no repository or Firebase calls inside UI
  (screens get `viewModel` only where the codebase already does — don't add new ones).
- **M** Split screens over ~400 lines into section composables in the same file (`ProfileIdentitySection`, …);
  keep the public `XxxScreen(` signature (NIGHTHAWK counts those).
- **M** Explicit variants beat boolean soup: `ProCard(variant = CardVariant.Outlined)` instead of
  `isOutlined`, `isElevated`, `isFilled` flags. Add an enum, keep old params as defaults for one release.
- **M** Slots over parameters for content: `title: @Composable () -> Unit` when callers need different content.
- **H** Modifier is the first optional parameter and is applied to the root only once.

## State and effects
- **H** `remember` UI-only state; `rememberSaveable` for anything the user typed or chose (tab, filter draft).
- **H** Effect keys are the inputs: `LaunchedEffect(id)`, never `LaunchedEffect(Unit)` for something that
  depends on an input. Long-lived callbacks use `rememberUpdatedState`.
- **H** `DisposableEffect` must clean up (listeners, `TrackScreen` pops).
- **M** `derivedStateOf` for values computed from scroll/list state (e.g. "show collapsed header").
- **H** No duplicate local `val`/`var` names in one composable (CLAUDE.md).
- **H** Collect flows with `collectAsState()` as the codebase does; don't mix in a second pattern per screen.

## Lists and images
- **H** `LazyColumn`/`LazyRow` with `key = { it.id }` for every data list; `contentType` when rows differ.
- **H** One vertical scroll container per screen; no `verticalScroll` inside a `LazyColumn`.
- **M** `Modifier.animateItem()` for insert/remove (Phase 2), not on first load.
- **M** Images: Coil `AsyncImage` with `ContentScale.Crop`, fixed aspect ratio (16:9 cover), a placeholder in
  `surfaceContainerHigh`, `contentDescription` = the space's title (decorative images `null`).

## Theming
- **H** Colours from `MaterialTheme.colorScheme` / `MaterialTheme.proColors`; text from `MaterialTheme.typography`.
  `.copy(fontWeight = …)` is allowed; `.copy(fontSize = …)` is not.
- **H** Dark mode: check every changed screen in the dark screenshot. Alpha on `onSurfaceVariant` stacks into
  illegible text — use the opaque role.
- **M** Shapes from `MaterialTheme.shapes` / `Radius`; elevation from `Elevation` (Phase 0).
- **H** Text in a fixed-height container uses `heightIn(min = …)`; single-line text has
  `maxLines = 1, overflow = TextOverflow.Ellipsis`.

## Previews and tests
- **M** New shared components get a `@Preview` with light + dark (`uiMode = UI_MODE_NIGHT_YES`).
- **H** Unit tests stay hermetic (`hermeticRepository()`); screenshot tests live in `app/src/test/.../design/`.
- **M** `Modifier.testTag` on elements tests target, not on everything.

## Performance
- **M** Hoist expensive objects (`Brush`, `Painter`, formatters) with `remember`; no allocation per frame in
  `drawBehind`.
- **M** Pass stable/immutable data to list items (data classes of vals; no `MutableList` params).
- **M** Animate `graphicsLayer` (alpha, translation, scale) rather than layout size where possible.
