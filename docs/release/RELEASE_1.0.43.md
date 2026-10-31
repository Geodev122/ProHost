# ProHost 1.0.43 (versionCode 43)

## What's in it (since 1.0.42)
The design audit (`docs/design/UI_AUDIT.md`, phases 0–3) in one release. App only: no backend, rules or billing change.
- **Arabic.** Every screen is translated (~1,340 strings, `values-ar`). The app follows the phone's language; on
  Android 13+ it can be picked per app (Profile › Language). Layouts mirror right-to-left. Stored values, analytics and
  Play's own offer wording stay as they are.
- **Look:** Manrope headings on one type scale (12 sp floor), surface-container ladder for clearer dark mode, one card,
  one main-button colour (orange), radius and elevation tokens, Material-spec dialogs.
- **Feedback:** every message is a Snackbar above the nav pill, with **Undo** for pausing/resuming a listing, switching
  a slot off/on and removing a saved space.
- **Pro Hosts:** bottom bar is Listings · Explore · Rentals · Profile. Renting Progress moved to the drawer (pushes and
  notifications still open it). Explore uses the same floating header for every role.
- **Bottom nav:** restyled only; tabs, collapse-to-handle, scroll and map behaviour are unchanged.
- **Explore:** 16:9 photos, two-line titles, an Availability button on each card that opens the booking sheet.
- **Accessibility:** 48 dp touch targets (carousel arrows, save heart, steppers), heading / live-region / selected
  semantics, plain-language labels.
- Saved and Manage listing no longer draw a second top bar; My Rentals has a proper empty state; confirm haptic after a
  request is sent; page-change motion that respects "Remove animations".
- **Dark-mode splash** with its own artwork (no light flash on dark phones).
- **Forms:** every text field is the shared `InputField` (same outline, label, colours in light/dark).
- **Listing wizard** is a full-screen page with a three-step progress bar; Back goes to the previous step.
- Error analytics report the same areas in Arabic as in English.
- Text grows with the phone's font setting only up to Android's "Large" step, so every screen fits small phones.
- Arabic polish from the screenshot review: price units (/ساعة, /مناوبة, /يوم, /شهر, /شخص), day names, plan names
  and role tags translated; time ranges stay left-to-right; Premium chips wrap instead of being cut off; with large
  text a section badge moves under its subtitle.

## Files
Artifact **`prohost-release-main-15`** (≈ 22 MB, kept until 2027-01-18):
https://github.com/Geodev122/ProHost/actions/runs/39360645996 → Artifacts, built from `00967e9`.
Upload `app-release.aab` (Production › Create new release) and `native-debug-symbols.zip` under App bundle explorer ›
version 43. If 1.0.42 (`prohost-release-main-14`) was never uploaded, skip it: 1.0.43 contains everything in it — use
the 1.0.42 notes' highlights only if you want to mention the WhatsApp-number change again.

Tester build (not Play-installed, so billing returns DEVELOPER_ERROR): `prohost-debug-apk` from
https://github.com/Geodev122/ProHost/actions/runs/39360632109.

## Release details
- Release name `1.0.43 (43)`; notes: `docs/release/1.0.43-notes.txt` (8 languages, each ≤ 500 characters).
- Released 2026-10-20 to production at 100% (owner's call) by `release-bundle.yml` run 39402278230 from main `9c04ec0`:
  bundle versionCode 43 (sha256 `1b65d3ad…b72025`), R8 mapping and native symbols uploaded; probe run 39403229281 shows
  production `1.0.43 (43) completed`. Watch Crashlytics and Android vitals for 48 h; halt from Play Console if needed.

## Check after release
- [ ] Phone language Arabic: sign-in, Explore (map + list), a listing, Availability sheet, My Rentals, Profile,
      Premium read right-to-left with no clipped text; prices and codes stay in Western digits.
- [ ] Profile › Language (Android 13+) switches between English and Arabic without a restart loop.
- [ ] Pro Host: bottom bar Listings · Explore · Rentals · Profile; Renting Progress opens from the drawer and from a
      booking push.
- [ ] Bottom nav still collapses to the handle on scroll and on the map, and comes back on scroll up.
- [ ] Pause a listing → Undo in the Snackbar restores it; unsave a space → Undo puts it back.
- [ ] TalkBack: section headings announce as headings, the selected Premium plan reads "selected", banners are read
      when they appear.
- [ ] Dark mode: cards, dialogs and sheets sit on distinct surfaces; secondary text is readable.

## Known, deferred
Dark splash (needs dark artwork), `InputField` migration, full-screen listing wizard, and English Play period words /
admin tools — see "Implementation status" in `docs/design/UI_AUDIT.md`.
