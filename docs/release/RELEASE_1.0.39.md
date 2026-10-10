# ProHost 1.0.39 (versionCode 39) — first production release

This build has the same app code as the audited 1.0.38 build (`docs/release/RELEASE_AUDIT_1.0.38.md`, sections
1–5). Only the version number changed, so it is uploadable even if 38 already went to a test track. The backend it
talks to passed `prodcheck` three times in a row on 2026-10-10 (08:17–08:19 UTC).

## Files (GitHub Actions › "ProHost - Build Play-Signed Official Release Bundle" › artifact)
| File | Where it goes in Play Console |
|---|---|
| `app-release.aab` | Test and release › Production › Create new release › App bundles |
| `native-debug-symbols.zip` | App bundle explorer › version 39 › Downloads › Native debug symbols › Upload |
| `mapping.txt` | Same page, "ReTrace mapping file" (optional — Crashlytics already has it) |

## Release details
- **Release name:** `1.0.39 (39)`
- **Release notes:** paste all of `docs/release/1.0.39-notes.txt` (8 languages, each under 500 characters).
  Remove any language that isn't in your store listing.
- **Rollout:**
  - Start with a staged rollout at **20%**.
  - Watch Crashlytics and Android vitals for 48 hours.
  - If no new crash or ANR clusters appear, go to 100%.
- **Countries:** as in your store listing, including Lebanon.

## Before you press "Start rollout"
- [ ] App content (Policy) answers match this build. See `RELEASE_AUDIT_1.0.38.md` §3.3:
  - no camera or photo permissions;
  - analytics opt-in;
  - no ads;
  - deletion URL `https://pro-host.tech/delete-account.html`.
- [ ] `package_pro_mrr` has both base plans **Active** (Admin › Packages › Billing health).
- [ ] The Google Cloud billing account (…BA21) shows no payment warnings. The backend stops when billing lapses.
- [ ] One license-tester purchase on an internal-testing install activates Pro Host.
- [ ] The retired growth-plan subscription is cancelled in Play Console › Order management.

## After rollout
- Run the maintenance task `prodcheck` (or ask Claude: "re-run prodcheck"). Expect:
  - every function answers;
  - RTDN self-test delivered;
  - no parked purchases.
- Upload `native-debug-symbols.zip` if you didn't do it before rollout. Without it, native crashes in Play
  Console are unreadable.
