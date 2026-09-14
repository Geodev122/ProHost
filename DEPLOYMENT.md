# Deploying the Backend (Hosting + Cloud Functions)

This covers `pro-host.tech` (Firebase Hosting — the marketing site + legal
pages + listing share links) and the Cloud Functions the Android app calls.
Do this from any machine/IDE (VS Code, Android Studio's terminal, a plain
shell) — it doesn't require this repo's sandbox, just the Firebase CLI.

## 1. One-time setup

```bash
npm install -g firebase-tools     # if you don't already have it
firebase login                    # opens a browser to authenticate
cd ProHost                        # repo root — where firebase.json lives
firebase use prohost-f766f        # selects the project (see .firebaserc)
```

You need to be added as a collaborator on the `prohost-f766f` Firebase
project first (Firebase Console → Project Settings → Users and permissions)
if `firebase login` doesn't already give you access.

## 2. Deploy the website (Hosting)

Edits under `public/` — `index.html` (home page), `payment/*.html`,
`delete-account.html`, `404.html`, `.well-known/assetlinks.json`.

```bash
firebase deploy --only hosting
```

Live at `https://pro-host.tech` and `https://prohost-f766f.web.app` (same
deployment, two domains). `/privacy`, `/terms`, `/revocation` are **not**
static files — they're server-rendered by `legalDocumentPage` (see below)
from whatever an Admin last published in the app's Admin Console.

## 3. Deploy the backend (Cloud Functions)

Edits under `functions/src/**` — everything the Android app calls
(`assignInitialRole`, `initiateWhishPayment`, `deleteOwnAccount`, etc.) plus
`legalDocumentPage` and `listingShareLanding`, which back the website.

```bash
cd functions
npm install          # first time, or after a functions/package.json change
cd ..
firebase deploy --only functions
```

This runs `npm run build` (TypeScript → `functions/lib`) automatically
before uploading — see `firebase.json`'s `predeploy` hook. To deploy just
one function (faster, safer for a small fix):

```bash
firebase deploy --only functions:legalDocumentPage
```

## 4. Deploy both together

```bash
firebase deploy --only hosting,functions
```

## 5. Sanity-check before deploying anything non-trivial

```bash
cd functions && npx tsc --noEmit && cd ..   # TypeScript compiles clean
firebase deploy --only hosting,functions --dry-run   # validates without pushing
```

A dry run hits the same Firebase APIs and reports errors (bad rules,
TypeScript failures, config typos) without actually releasing anything.

## 6. Firestore rules / Storage rules / indexes

Not part of "deploy both" above, but the same CLI:

```bash
firebase deploy --only firestore:rules,storage
```

## Where things live

| What | Path | Deploys via |
|---|---|---|
| Marketing home page | `public/index.html` | `hosting` |
| Legal pages (dynamic) | `functions/src/legal/legalDocumentPage.ts` | `functions` (+ `hosting` rewrite) |
| Listing share landing page | `functions/src/listings/shareLanding.ts` | `functions` (+ `hosting` rewrite) |
| Payment success/failure pages | `public/payment/*.html` | `hosting` |
| Account-deletion page | `public/delete-account.html` | `hosting` |
| Every other Cloud Function the app calls | `functions/src/**` | `functions` |
| Hosting routes (`/privacy`, `/listing/**`, etc.) | `firebase.json` → `hosting.rewrites` | `hosting` |

## Notes

- `firebase deploy` always deploys **every** function under `functions/src`
  unless you scope it with `--only functions:<name>` — Firebase skips
  functions with no code changes automatically, so a full `functions` deploy
  is safe to run even when you only touched one file.
- Two domains serve the same Hosting site: `pro-host.tech` (the real,
  registered domain — use this one in links/App Links/QR codes) and
  `prohost-f766f.web.app` (Firebase's default domain — works identically,
  kept as a fallback). `hopebearer-award.com` is a **separate**, dedicated
  domain reserved only for Whish's payment-gateway channel config — never
  point anything else at it.
- No `GOOGLE_APPLICATION_CREDENTIALS` env var is needed for a normal deploy
  from your own machine — `firebase login` handles auth. That variable is
  only relevant for service-account/CI-style deploys (see
  `.github/workflows/android-firebase-distribution.yml` for the app's own
  CI example).
