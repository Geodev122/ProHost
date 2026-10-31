# App Check

**Current state (2026-10-05): every service is UNENFORCED** (Authentication, Firestore, Storage) — the
owner turned enforcement off because verified traffic was below 98%. The app works fully without
enforcement: App Check only attaches tokens; callables keep `ENFORCE_APP_CHECK=false` and just log
`app_check_unverified`.

## Why traffic was unverified
1. **Tester builds (debug APKs from App Distribution)** use the App Check *debug* provider. Firebase had
   **no debug token registered**, so every tester request was rejected (most of the failures were one
   tester's OnePlus). Fixed 2026-10-05: one shared token "CI tester builds (shared)" is registered; CI
   bakes it into every debug APK from the `APP_CHECK_DEBUG_TOKEN` GitHub secret
   (`app/build.gradle.kts` → `ProHostApplication.seedSharedDebugToken`).
   **Owner:** GitHub › geodev122/prohost › Settings › Secrets and variables › Actions ›
   `APP_CHECK_DEBUG_TOKEN` → paste the registered token (given privately), then rebuild.
2. **Release (Play) builds** use Play Integrity. They pass only if:
   - Play Console › your app › **Release › App integrity › Play Integrity API** › *Link Cloud project* →
     `prohost-f766f` (646730915838);
   - Firebase Console › Project settings › your Android app has the **App signing key SHA-256** from Play
     Console › App integrity › App signing (it is registered — 8 SHA-256 certs present).
3. Old app versions installed before App Check was added send no token at all.

## Re-enforcing (later)
Firebase Console › App Check › APIs › each service shows *Verified / Unverified (outdated client /
unknown origin / invalid)* for the last 7 days. Enforce a service only when **≥ 99%** is verified and
the remaining unverified traffic is not real users. Then (optional) set `ENFORCE_APP_CHECK=true` for
callables in a later release — see CLAUDE.md for why it was reverted once.

## Play Integrity on sign-in (server)
Separate from App Check: `assignInitialRole` sends the app's Play Integrity token to
`functions/src/lib/playIntegrity.ts`, which decodes it with Google's `decodeIntegrityToken` and blocks
`UNRECOGNIZED_VERSION` / `UNLICENSED` / wrong package or nonce / stale tokens on release builds.
- Until 1.0.44 every decode returned **403**: the call used firebase-admin's credential token, which is not scoped
  for the Play Integrity API, and a decode failure lets sign-in through — so nothing was enforced. It now uses a
  googleapis `GoogleAuth` token with scope `https://www.googleapis.com/auth/playintegrity`.
- Logs: `play_integrity_ok` (verdicts) on success; `play_integrity_decode_failed {status, body}` on failure (body
  truncated, never the token). Maintenance `logerrors` groups them.
- Check it any time with Maintenance **`integrity`**: API state, then a dummy-token decode with the functions' own
  credentials — **400 = OK** (credentials and Play Console link fine, the token is just fake), 403 = the project
  isn't linked in Play Console (step 2 above) or the API is off (`code=enable` turns it on).
