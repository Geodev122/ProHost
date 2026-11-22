# ProHost: hand-over notes for the next session or model

Read this, then CLAUDE.md's "KMP + Compose Multiplatform migration" section, then the open items in `OWNER_QUEUE.md` and `docs/OWNER_TODO.md`.
Detailed history: `PROGRESS.md` (newest at the bottom), operations: `docs/OPS_LOG.md`, decisions: `DECISIONS.md`.

## Where things stand (22 Nov 2026)
- Branch for this work: `claude/sharp-clarke-w4rlh2`. After a green Parity Gate, fast-forward `main` (`git push origin HEAD:main`, never force, no PR). Every push to `main` deploys functions, rules, indexes (incl. TTL `fieldOverrides`) and hosting (`build-and-deploy.yml`, gated on unit, rules and e2e tests) and the site + `/console` (`site-deploy.yml`).
- Live roadmap with task ids: `docs/ROADMAP.md`, mirrored on the phone tracker artifact (db collections `tasks`, `meta`; update rows with ArtifactData + `if_version`).
- Android: production 1.0.46; internal track 1.0.50. Bump `versionCode` for every upload (`release-bundle.yml`, `play_track=internal`; never `production` without the owner).
- iOS: TestFlight build 22 uploaded. App Store Connect complete except attaching a build (readiness report, `ios-inspect.yml`): listing EN+AR, 8 mock-up screenshots per language (replace with real captures before "Add for Review", the owner's click), 4 subscriptions READY_TO_SUBMIT (Unlimited + 1 listing, monthly/yearly), notification URLs, review contact, demo accounts (Maintenance `reviewaccounts`, passwords only in server-only `app_config/app_review`) and notes. App Store daily re-sync `appleSyncJob` live with the In-App Purchase key.
- Backend: Firestore TTL ACTIVE (`notifications.ttlAt`, `mail.expireAt`); `pruneExpiredNotifications` retired; `prodcheck` clean (22 Nov).
- Console (`pro-host.tech/console`): all 7 tabs + Bookings, Diagnostics, Reports, Change price, payment reminder, notification bell; Edit listing with the app's own wizard (`ListingEditor.kt`; web pickers `WebFiles`, `WebSeams`, `WebStorageBridge`) on the branch as of 908b4c5. ADMIN_PARITY Proof/Owner columns wait for the owner's walk-through.
- Migration: repositories, view models, nav graph and most screens are in `:shared`; next is W2 Explore's map seam (`PlatformMap`, plan section 85), then the remaining waves and the iOS native layer (Maps, pickers, push, StoreKit client).

## Owner rules for working (all from the owner, in force)
1. No change to UI, UX or behaviour unless the owner asked for it; anything else is listed in `DECISIONS.md` and asked.
2. No Before | After or image work: never fetch, view or publish render images. Verify by the Parity Gate's text result; intended screen changes go on `docs/migration/approved-changes.json` with a one-line reason.
3. Work on your own until the plan is done; collect what needs the owner in `OWNER_QUEUE.md`; record every change and lesson in `PROGRESS.md` / `OPS_LOG.md` / CLAUDE.md.
4. End every status report with the open owner items.
5. Secrets are named, never shown: GitHub secrets `FIREBASE_SERVICE_ACCOUNT`, `PLAY_APP_SIGNING_SHA1/256`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8`, `APP_REVIEW_DEMO_USER/PASSWORD`, `APP_CHECK_DEBUG_TOKEN`, IG/Anthropic keys. Never print or commit values.

## CI with few tokens
- Latest run: `list_workflow_runs <file> perPage=1`. Jobs: `list_workflow_jobs` only for small runs (never the Parity Gate, 8+ jobs).
- Failure: `get_job_logs run_id failed_only tail_lines=1500` (it is saved to a file), then `python3 scripts/ci/errors.py <file> 60`. Small jobs: `get_job_logs job_id tail_lines<=90`.
- Wait with `send_later` (one-line prompt saying what to read and what to do next), never poll.
- No Android SDK in the sandbox: CI is the compiler. The Admin Web (wasm) build is the check that `commonMain` code is platform-free.
- Local checks before a push: `cd functions && npm test`; `node scripts/migration/export_vectors.js --check`; `python3 scripts/migration/gen_parity_table.py --check` (`--write` after adding/moving Kotlin files); `python3 scripts/legal/sync_legal_content.py --check`; after string changes `python3 scripts/i18n/to_compose_resources.py` and `python3 scripts/ios/strings_convert.py` (iOS CI fails on a stale `Localizable.xcstrings`).
- The e2e flows (`tests/e2e/flows.test.js`) run only on `main`; earlier tests in that file leave accepted bookings behind, so new dated tests pick dates past their terms.

## App Store Connect tooling
`ios-inspect.yml` (dispatch on the branch): `prepare=plan|apply`, `only=<sections>` (app, categories, info, age, version, review, capability, price, availability, testflight, subscriptions, betareview, tester, offers, screenshots, subreview). Metadata: `ios/store/metadata.json` (18+ is pinned there as `ageRatingOverride`). The readiness report runs at the end of every dispatch. Apple has no API for Agreements and App Privacy (both done by the owner).
