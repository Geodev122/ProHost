# ProHost 1.0.42 (versionCode 42)

## What's in it (since 1.0.41)
- **No SMS codes any more.** Firebase phone verification never delivered a code in any country. The Firebase setup
  checked out (`smsauth`): phone sign-in enabled, Syria allowed, billing on, Play signing fingerprints registered. The
  remaining causes were on-device and couldn't be fixed from the server.
  - Booking and hosting now need a **WhatsApp number on the profile**, in international form, with no code.
  - It is entered on the registration form or in the "WhatsApp number" step (Profile banner, booking or Premium
    requirements sheet, workspace gate).
  - Gates are unchanged otherwise: photo + number to book; plus country and city to host.
- **Sign-in:** email (link or 6-digit code) or Google. The unreachable phone sign-in code is removed.
- **Legal/Privacy:** the text no longer says the number is verified by SMS.
- **App icon:** the original logo on a new soft background.
- **Registration requires the WhatsApp number.** Android's Phone Number Hint (the system SIM-number picker, no
  permission) opens once when the form appears. "Use this phone's number" re-opens it, there and in the WhatsApp step.
  That step always prefills the number already on the profile.
- **Premium cards:** Monthly now mirrors Yearly.
  - Ribbon: "4 MONTHS FREE" when Play returns the offer, else "FLEXIBLE".
  - Price: "Free" with the regular price struck through, then "then $9.99 / month".
  - Bottom chip: "NOTHING TO PAY TODAY", or "CANCEL ANYTIME".
- **Permission prompts:**
  - One native look (`SystemStylePermissionDialog`).
  - The analytics consent uses it.
  - The notification prompt waits for that choice, so prompts never stack.
  - Location asks with a rationale when Android wants one, offers "Open settings" when it is blocked, and uses Play
    services' own dialog to turn device location on. No more toasts.
- **Premium "How it works" box:** shows for whichever card is selected. With an offer it reads "HOW YOUR OFFER WORKS";
  without one, "HOW IT WORKS" with the price, first renewal date and how to cancel, all from Play.
- **Google Analytics routing** (consent-gated as before; catalogue in `docs/ANALYTICS.md`):
  - Readable screen names for every page (`explore_map`/`explore_list`, `my_rentals`, `premium`, `admin_users`, …),
    each login step, every sheet and dialog; the page underneath is re-reported when a sheet closes.
  - `space_details` carries the listing code and where it was opened from (`listing_source`).
  - Booking funnel: `begin_checkout`, slot select, requirements shown, confirm, request sent; `is_edit` split from
    `is_rebook`.
  - New events for auth errors on every path, registration, phone hint, KYC, profile photo/update, permissions,
    consent, filters/search, Premium plan select, promo checkout and restore trigger.
  - Noise removed (autosave drafts, map camera moves); price ranges and dates no longer dropped as "phone numbers".

## Files
Artifact **`prohost-release-main-14`** (≈ 22 MB, kept until 2027-01-17): https://github.com/Geodev122/ProHost/actions/runs/39298104939 → Artifacts, built from `12c2d8b` (includes the GA4 routing). It replaces `prohost-release-main-13`, which is older; don't upload that one. Upload `app-release.aab` (Production ›
Create new release), and `native-debug-symbols.zip` under App bundle explorer › version 42.

## Release details
- Release name `1.0.42 (42)`; notes: `docs/release/1.0.42-notes.txt` (8 languages).
- Staged rollout at 20%. Watch Crashlytics and Android vitals for 48 h, then go to 100%.
- Optional: upload `play/store-listing/icon-512.png` as the listing icon.

## Play Console: monthly offer eligibility
Fixed (Maintenance `playofferfix`, 2026-10-19): `free-4-months` and `prohost2026` are ACTIVE on `pro-monthly1` with
eligibility **"Never had this subscription"**; `playcatalog` lint reports no warnings. Accounts that already had the
subscription (test accounts) still see the regular price — that is Play's eligibility, not a bug.

## Check on a Play-installed build
- [ ] Sign in by email. Profile shows "Ready to book". Add the WhatsApp number; it saves at once with no SMS.
- [ ] Send a booking request, then check Premium checkout for a host account.
- [ ] Firebase › Analytics › DebugView (debug build, analytics consent granted): Explore list → map → filters → a
      listing → Availability → Request → requirements → Request sent shows one readable `screen_view` per step plus
      `begin_checkout` and `booking_request`.
