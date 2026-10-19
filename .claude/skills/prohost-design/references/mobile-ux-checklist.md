# Mobile UX review checklist (Android)

Merged from UI/UX Pro Max (UX guidelines, app interface), Vercel Web Interface Guidelines (adapted from web to
Android) and Material 3 / Android accessibility guidance. Use it on every changed screen; tick what the
screenshots and code prove, not what you expect.

## Touch
- [ ] Every tappable thing is ≥ 48 × 48 dp hit area (`minimumInteractiveComponentSize()` or padding); visuals may
      be smaller inside it. Icon buttons, chips, ± steppers, save hearts, carousel arrows.
- [ ] ≥ 8 dp between adjacent targets.
- [ ] Gestures (swipe, long-press, drag) always have a visible button alternative.
- [ ] Destructive actions confirm (`ProHostDialog`) or offer Undo (Snackbar); never both, never neither.
- [ ] A submitting button disables and shows progress; it re-enables on error.

## Accessibility semantics
- [ ] Icon-only buttons have `contentDescription` naming the action ("Save space", not "Heart").
- [ ] Decorative icons/images: `contentDescription = null`.
- [ ] Section titles: `Modifier.semantics { heading() }` (put it in `ProSectionHeader` once).
- [ ] Selectable things (plan tiles, room tabs, filter chips, nav tabs) expose `selected`/`role` and a
      `stateDescription` ("Selected", "Booked").
- [ ] Status/banners that appear on their own: `liveRegion = LiveRegionMode.Polite`.
- [ ] Information is never colour-only (booked/free cells get a legend or icon; errors get text).
- [ ] Contrast: text ≥ 4.5:1 (large ≥ 3:1) in light **and** dark. Orange text uses `secondary`; green text uses
      the strong success role, not FreshGreen.
- [ ] Font scale 1.3 screenshot: nothing clipped, nothing overlapping, buttons grow (`heightIn(min=)`).
- [ ] Merged semantics for cards so TalkBack reads one sentence per card (`Modifier.semantics(mergeDescendants = true)`
      or Card's `onClick`).

## Forms
- [ ] Visible label on every field (`InputField`), placeholder shows an example ("+961 70 123 456").
- [ ] Keyboard type matches (`KeyboardType.Email/Phone/Number`), IME action Next/Done/Search moves on.
- [ ] Errors inline under the field, specific ("Add the country code, e.g. +961"), and the first error is
      focused/scrolled to on submit.
- [ ] Never block paste; codes allow autofill (`SmsRetriever` not used — no SMS anywhere).
- [ ] `imePadding()` on screens with fields; the focused field is never under the keyboard.
- [ ] Don't ask twice for what the profile already has (prefill: WhatsApp number, name, city).

## Feedback and states
- [ ] Every list: loading (`ShimmerLoadingList`), empty (`ProEmptyState` + action), error (`ProHostAlertBanner`
      + Retry). Offline shows the offline banner, not a blank page.
- [ ] After an action: Snackbar with the verb echoed ("Request sent", "Listing paused · Undo").
- [ ] Skeleton → content uses `Crossfade`; no layout jump when content arrives (reserve image space).
- [ ] Haptics: `Confirm` on success moments, `Reject` on errors, nothing on ordinary taps.

## Navigation
- [ ] Back always does the expected thing (sheet → closes; full-screen page → previous tab).
- [ ] Exactly one top bar per destination; drawer icon is ☰, overflow is ⋮.
- [ ] 3–5 bottom tabs; the active one labelled; state (scroll, filters) preserved when returning.
- [ ] Sheets have a visible drag handle and a close/back affordance.

## Content and copy
- [ ] Sentence case for titles and buttons; plain verbs; the user's words, no internal jargon.
- [ ] Long titles/names: ellipsis + full text reachable (details page); never clip essential meaning.
- [ ] Prices via `SpaceCalculationUtils` unit labels; dates locale-formatted; numerals for counts.
- [ ] `…` for "Loading…"/"Saving…"; no straight-quote/`...` artefacts.

## Localisation and RTL (Phase 3)
- [ ] No new hard-coded user-facing strings in Phase 3+ code: `stringResource(R.string.…)`; counts via
      `pluralStringResource`.
- [ ] RTL screenshot: layout mirrors, directional icons use `Icons.AutoMirrored.*`, numbers/prices stay LTR.
- [ ] Text never assumes English length (Arabic/French can be 30 % longer): no fixed widths around text.
