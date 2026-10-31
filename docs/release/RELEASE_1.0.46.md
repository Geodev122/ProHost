# ProHost 1.0.46 (versionCode 46)

## What's in it (since 1.0.45)
- **Create listing footer**: the full-screen wizard's window fills the screen with adjust-resize and pads for the
  activity's own status/navigation bars, so Back / Next never sit under the gesture or 3-button bar; a hint line says why
  Next is disabled ("Add the space name…", "Tap “Mark pin location”…").
- **Listing location** (`ui/util/ListingLocation.kt`, `locationLine()`): every card and text shows the listing's own
  city, country (country in the app language). The Lebanese governorate enum defaulted to Beirut for listings abroad; it is
  now only a fallback for legacy Lebanese listings.
- **Saves analytics** (no new screens): the heart writes with arrayUnion/arrayRemove (rules cap 300); `favoritesSync`
  counts in a transaction — owner's own saves ignored, retries de-duplicated, saves / un-saves per month; Maintenance
  `favrecount` rebuilds the counts. My Listings card: "N saved"; Owner Analytics: "+N this month" on the Saved tile and
  "V views · S% saved · I% asked" per listing. GA4 wishlist events and Undo only after the write lands.
- **Auth background**: a blurred practice room (`auth_background.webp`, `scripts/marketing/blur_background.py`) under a
  surface scrim on every sign-in / sign-up step.
- **Office-sourcing leads** (backend + website, live with the deploy): `/list-your-office` form (EN + Arabic) →
  `websiteOfficeLead` → address book (tag `insta-leads-story`), the IG Automations sheet's Leads tab, admin push + email
  (+ Instagram DM to the owner inside Meta's 24 h window). Story graphics `public/social/story_list_your_office(_ar).jpg`;
  sheet rules `office-dm` / `office-story` answer "OFFICE" / "مكتب" with the form button.

## Status
Published (2026-10-24): backend deployed from main (CI green: rules, e2e, functions, Android, deploy), Play production
release run 40095966773, `favrecount apply` (4 listings, 1 corrected), `prodcheck` clean, `officeleadsmoke` (page 200,
submit accepted, address-book tags, Leads row synced, admin email delivered). `published.txt` = 1.0.46.

## Check after release
- [ ] Create listing on a phone with gesture navigation and one with 3-button navigation: Back / Next fully visible; with
      the keyboard open on step 1 the footer rides above it.
- [ ] A listing abroad shows "City, Country" on Explore, Saved, My Listings and the details page (no "Beirut").
- [ ] Save / unsave a listing from another account: the host's My Listings card and Owner Analytics move by one; the
      host saving their own listing changes nothing.
- [ ] Sign-in screens: the background reads well in light and dark.
- [ ] Submit `/list-your-office`: address book row with the tag, Leads tab row, admin push and email.
