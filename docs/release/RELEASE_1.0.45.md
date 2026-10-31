# ProHost 1.0.45 (versionCode 45)

## What's in it (since 1.0.44)
- Listing links (share links and the Instagram agent's "View …" buttons, `pro-host.tech/listing/{id}?room=…`) always land:
  the listing is fetched by id when it is outside the loaded Explore page, the link survives sign-in / registration, `?room=`
  opens the details page on that room's card, and an unpublished or missing listing shows "This listing is no longer
  available" and Explore (`util/ListingDeepLink.kt`, `ProHostNavGraph`).
- Staged sign-in (intent first): Welcome → providers (Google, Apple-ready, email) → email → password; registration in
  three short steps. Every step fits a 360 dp phone without scrolling.
- "Create listing" wizard: category dropdown, one brand cover photo, compact pin with the address filled in the app's
  language (English country + `cityEn` for search), no visitor policy, Step 3 as division cards edited in a sheet; one
  division is saved as the whole place. Translation pass: glossary `docs/design/GLOSSARY.md`, sentence case, 68 unused
  strings removed.
- Create / continue / edit / manage use one editor: Edit (My Listings and the Manage page's new pencil) saves from any step;
  closing keeps work (drafts auto-save, edits ask); the Manage page can pause/resume; booked divisions can't be deleted and
  a booked listing never flips between whole place and divisions.
- Division sheet redesigned: live preview card, type as icon chips, "How is it rented?" as choice cards (by the hour /
  shift / day / month, then fixed price or per person), amenity chips, capacity for every division, rarely used settings
  under "More options". An undivided listing keeps its capacity (`SpaceListing.capacity`), which the Instagram finder
  uses. The agent's app-data access is read-only by test (`readOnlyAccess.test.ts`); data model in `docs/DATA_MODEL.md`.
- Server side (already on the feature branch, deploys with main): the Instagram agent's DM poller, whole-chat memory and
  listing finder; the listing page hands Android visitors (Instagram's in-app browser too) to the app.

## Status
Published to Google Play production on 2026-10-24 at 100 % (release-bundle run 40085616980). Backend (functions, rules,
hosting) deployed from main `941ee67` (CI run 40085012408). Live checks: prodcheck (83/83 functions) and igsmoke green.
`docs/release/published.txt` = 1.0.45.

## Check after release
- [ ] Tap a "View" button from the Instagram agent on Android: the app opens on that listing and room (signed in and signed
      out → after sign-in).
- [ ] A link to a listing outside the first 100 in Explore still opens it.
- [ ] A link to a paused listing shows the "no longer available" message and Explore.
- [ ] Create account by email, sign in, forgot password, Google (staged screens).
- [ ] Create a listing with one division (saved as whole place) and with two; edit an old whole-space listing.
- [ ] Arabic app: the pin fills the address in Arabic and Explore still finds the city in English.
