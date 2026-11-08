---
name: admin-web-parity
description: Rebuild the ProHost Admin Console as a web app (Compose for Web) with every screen and function of the Android admin, then remove the admin from the mobile apps. Use for adminWeb work, docs/migration/ADMIN_PARITY.md, admin callables, or admin rules.
---

# Admin web parity

Decision: Compose for Web reusing the Kotlin admin code (`adminWeb/`, never a dependency of Android/iOS); React only if the P1.0 spike fails its gate.
Origin `admin.pro-host.tech` (own Netlify site; add to Firebase authorized domains via `scripts/maintenance/run.js authconfig`, which hard-codes only `pro-host.tech`;
OAuth web-client origins; keep `localhost`/`http://` out of the web client per OWNER_GUIDE). The Android admin stays untouched until the cutover gate.

## Sign-in and gate
Firebase Auth Web (Google + email/password) → callable `assignInitialRole` → force ID-token refresh → require `claims.role === "ADMIN"`. Rules' `isAdmin()` is a pure
token-claim check — never make it read a document. App Check is unenforced; decide a web provider before it is ever enforced. There is no bootstrap-admin code path:
document how the first admin is created (out of band).

## Scope: 70 items (`docs/migration/ADMIN_PARITY.md`)
Shell and gate (7) · Packages: billing health, self-test, payments needing attention (Retry / Activate / reassign), Force Upgrade, Play sync, governance tag, users CSV (10) ·
Users: tiles, smart search, role chips, cards, dossier sheet, edit, delete, grant admin, suspend, revoke Pro Host, CSV (11) · Listings & Bookings: tiles, search, filters, review
queue, booking cards, verification doc, activate, verify, edit wizard, delete, host shortcut (11) · Analytics (5) · Schema (8) · Security: audit log, CSV, legal documents (4) ·
Demo Control (7) · debugger, admin-as-Pro-Host tools, GA4 secret config, mailing console, WhatsApp alerts, maintenance tasks, IG control room (7).
Admin edit wizard and Pro Host tools reuse the shared owner screens after P6-W5. Direct client writes (not callables) that the web app needs under the same rules:
`schema_architecture`, `legal_documents` (+ Storage `legal_documents/**`), `user_profiles` edit/delete, `workspace_listings` edit/delete (+ Storage `listings/**` purge).

## Per tab
Learn the Kotlin tab → list controls/strings/callables → build → run against the emulators (`firebase emulators:exec`) → Playwright screenshots (pre-installed browser, deviceScaleFactor 1)
of the web tab vs the Android golden of the same state (`admin_*` screen names, `ScreenNames.kt`) → tick ADMIN_PARITY.md. Quirks are carried over, not fixed (DECISIONS.md list).
Add positive admin cases to `tests/rules` (none exist today) and unit tests for the admin callables as each tab lands.

## Cutover gate (P1.F)
All 70 ticked + owner walk-through → in one reviewed change remove the 17 admin Kotlin files and the admin branches in the mixed files (FirebaseFunctionsClient ~lines 77-340,
ProHostNavGraph, ProHostRepository, DataModels, FirestoreService, AppDrawerContent.AdminDrawerContent, DrawerDialogsHandler, SpecialistProfileScreen, OwnerHubScreen…). A mobile ADMIN
account then sees the "use the web console" screen (wording approved in DECISIONS.md); admin push categories deep-link to the web console URL.
