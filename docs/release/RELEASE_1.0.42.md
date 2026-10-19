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

## Files
Release Bundle workflow run on the latest `main` (artifact `prohost-release-main-*`). Upload `app-release.aab` (Production ›
Create new release), and `native-debug-symbols.zip` under App bundle explorer › version 42.

## Release details
- Release name `1.0.42 (42)`; notes: `docs/release/1.0.42-notes.txt` (8 languages).
- Staged rollout at 20%. Watch Crashlytics and Android vitals for 48 h, then go to 100%.
- Optional: upload `play/store-listing/icon-512.png` as the listing icon.

## Play Console: monthly offer eligibility
`free-4-months` (on `pro-monthly1`) is set to **"New customers: never had any subscription in this app"**. Any account that
ever subscribed (your test accounts) never gets it, so the monthly card shows the regular price for them. To match the
yearly offer, edit the offer: Eligibility → **"Never had this subscription"**. Brand-new accounts see it either way.

## Check on a Play-installed build
- [ ] Sign in by email. Profile shows "Ready to book". Add the WhatsApp number; it saves at once with no SMS.
- [ ] Send a booking request, then check Premium checkout for a host account.
