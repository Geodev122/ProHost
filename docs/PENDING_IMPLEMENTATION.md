# Pending implementation package (prepared 7 Nov 2026, waiting for the owner's trigger word)

> STATUS 7 Nov 2026: A1, A2, B, D executed; C drafts written (not sent). A3 still pending.

Nothing below has been executed. Trigger word: **GO IMPLEMENT** (everything marked "on trigger"), or name a package (A, B, C, D).
Every step is reversible unless stated. Evidence: docs/INCIDENT_2026-11-appspot-key.md.

## A. Contain the intruder (on trigger)
1. Disable key `e5b6952d...` of prohost-f766f@appspot (`gcloud iam service-accounts keys disable e5b6952d1fe7c81e72085d3bf174b27035ca51b1 --iam-account=prohost-f766f@appspot.gserviceaccount.com`). Undo: `keys enable`. Watch 7 days (alert D2 + docs/LOG_QUERIES.md query C), then delete. No replacement key unless something proves it is needed.
   Impact: nothing in the app, CI (workflows use only FIREBASE_SERVICE_ACCOUNT), Instagram agent, billing, email or functions uses the file; they use attached identities. Unknown: ad-hoc scripts or third-party tools holding a copy of the JSON (earlier admin reads were not logged: Data Access logs were off before 4 Nov).
2. Disable `aiplatform.googleapis.com` (the intruder re-enabled it). If dependents (cloudaicompanion, geminicloudassist, firebasevertexai) block it, report instead of forcing. Undo: enable.
3. LATER, with a test (not on the first trigger): the account still has Editor. Before removing it, find what the first-generation function `onAuthUserDeleted` (runs as this account) needs, grant only that, then remove Editor, then run the health window like the compute account.

## B. Suspicious sign-up exploreprohost99@gmail.com (uid kuBhi1EQ..., on trigger unless the owner says it is theirs)
Facts: created 24 Oct 13:29 UTC with email+password in the Android app (okhttp/4.12.0 via a Google front end); email never verified; one sign-in (at creation); profile has role SPECIALIST, no name, phone, photo, listings, bookings or audit entries; not in the newsletter list or Instagram leads; the only trace in the repo/Claude/Instagram agent is none (no code creates Auth users). The caller's IP is NOT recorded anywhere (Firebase Auth stores none; Cloud Run logs show Google's front end), so an IP block is not possible.
Steps: (1) disable the Auth user and revoke its sessions; (2) mirror the app's own suspend: custom claim `suspended: true` and profile `isSuspended: true`, plus `flaggedSpam: true` and `flaggedReason` with the date (the app has no spam concept yet; this is an internal marker); (3) keep the account and its data as evidence (no deletion); (4) write an audit-log entry. Undo: enable the user, clear the flags.
Also review (read-only, same method): four name patterns appear twice among the 85 accounts (martyhoffman.#, jermainestrickland.#, rodgercowan.#, wyattminer.#).

## C. Evidence and reports (drafts only; the owner sends)
Appeal addendum for ticket 35DQMHFCOIBXT6BBR6KMUFHCXI (unauthorized use of a leaked key from third-party servers, blocked by Google's own check, key disabled); abuse reports to RackNerd LLC (RDAP handle RL-872, addresses 23.95.170.108 and 198.23.132.121) and Zappie Host / WEYRO LTD (185.121.179.67). Claude can save them as Gmail drafts.

## D. Detection (on trigger)
1. Keep alert VertexAI-or-Gemini-use. 2. New log alert: `EnableService` of aiplatform/generativelanguage by any principal. 3. New log alert: any use of key `e5b6952d...` after step A1 (it must be silent).

## E. Not part of this package (owner decisions)
Google appeal follow-up and Essential Contacts; billing budget; second-owner invitation; Maps SHA-1 and domains; secret rotation; uptime URLs; Play declarations; iOS decisions; final cleanup. See docs/OWNER_TODO.md.
