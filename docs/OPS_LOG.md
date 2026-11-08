# Operations log: Google Cloud hardening and runtime identities (3 to 5 Nov 2026)

Chronological record of everything Claude did, in the repo or outside it (GCP, Drive, GitHub), so any later session knows the real state.
Never put secrets here. Current state lives in `docs/GCP_IAM.md` ("CURRENT STATE"); open owner tasks live in `docs/OWNER_TODO.md`.

## 3 Nov
- Read-only audit of project `prohost-f766f` (credential: service account `claude-readonly`; its key `60ca2966...` is still to be deleted at the end).
- Remediation scripts written (later committed in `scripts/gcp-remediation/`).

## 4 Nov (times UTC)
- Temporary admin service account `claude-remediation` created by the owner (it has 5 keys; delete the account at the very end).
- 15:00 Firestore: point-in-time recovery ON, delete protection ON, daily (7 d) and weekly Sunday (8 w) backups. Firebase bucket: public access prevention, versioning, 7 d soft delete.
- Monitoring: email channel `ProHost owner email` (geo.elnajjar@gmail.com) and 5 log alerts (SA key created, project IAM changed, Vertex/Gemini use, Compute created, API key created/changed).
  Created through the Monitoring REST API because `gcloud alpha` cannot be installed behind the proxy. Data Access audit logs on for iam, secretmanager, cloudresourcemanager, aiplatform. `_Default` log retention 90 d.
- 15:13 Lesson: `gcloud run services update` cannot create new revisions (Firebase's cleanup policy deletes function images after 1 day). Identities must be applied by source deploy.
- 15:18 Editor removed from the default compute service account, replaced by narrow roles chosen from IAM Recommender usage; health window clean. `aiplatform.user` removed later (owner: Vertex not needed).
- Created `run-admin|auth|billing|ig|public|triggers` service accounts with narrow roles. Added to all six: run.invoker, eventarc.eventReceiver, explicit serviceAccountUser for the CI account. Later: `run-auth` + FCM, `run-billing` + Pub/Sub publisher, `run-ig` + firebaseauth.viewer.
- Owner invited `run-billing` and `run-ig` in Play Console (all permissions). Claude shared the Sheet "ProHost IG Automations" with `run-ig` as Editor (Drive connector).
- Code and deploys, all merged to `main` by Claude on the owner's instruction, every CI run green:
  #28 docs/scripts, #29 `run-public` (3 pages), #30 `run-triggers` (18 triggers), #31 `run-auth` (20), #32/#33 `hostingcheck` output compaction (script-only), #34 Play canary (`onMaintenanceRequest` on run-billing),
  #35/#36 `run-ig` (7 functions incl. Instagram/WhatsApp agent, after proving Play access with the `playcatalog` task), #37 `run-billing` (14 billing functions), #38/#39 owner to-do docs.
  Guard test `functions/src/lib/identityGuard.test.ts` blocks moving Play-reaching functions to an account that is not Play-invited.
- Owner pressed the billing self-test at 20:37 (`app_config/billing_health.lastSelfTestAt`): purchase notifications reach the function under `run-billing`.

## 5 Nov
- Hosting is down ("Site Not Found", even on prohost-f766f.web.app) although the live release (62 files) and the custom domain are healthy. Google confirmed an **active policy restriction** on the project
  (Cloud Quotas refused a quota increase for that reason). Trigger: Trust & Safety warning of 29 Oct (the 25 Oct Vertex GPU jobs). Play's assetlinks/credential-sharing/`/listing` checks fail for the same reason; the repo file already matches Play's fingerprints.
- Claude enabled `billingbudgets.googleapis.com`; creating the budget failed (no billing-account permission). Quota request (60 to 240/min Functions writes, europe-west1) refused by the restriction.
- Adding psy.geo.elnajjar@gmail.com as Owner via API is impossible (SOLO_MUST_INVITE_OWNERS): the owner must send the invitation from the console.
- Owner decisions: keep the appspot key `e5b6952d...` enabled, leave the firebase-adminsdk keys, keep the meta-ads-play key. Owner removed the duplicate SPF record and confirmed admin@pro-host.tech receives mail.
- Current identities: 64 services on group accounts (auth 20, triggers 18, billing 15 incl. maintenance runner, ig 7, public 3), 23 admin-type functions still on the default account.

## 6 Nov
- Status check: project lifecycle ACTIVE; policy restriction still active (quota request refused again; pro-host.tech and prohost-f766f.web.app still "Site Not Found"). Only email from Google is the 31 Oct "Appeal Received" (ticket 35DQMHFCOIBXT6BBR6KMUFHCXI); no decision yet. The Console warning ribbon is gone, which does NOT mean resolved.
- Added docs/LOG_QUERIES.md (tested Logs Explorer queries).

- 6 Nov (later): scheduled task "ProHost restriction watch (every 3h)" (trigger trig_01NNwUWdmzuvdVmubU1stB3T, bound to the working session) checks Gmail, the quota probe, the Hosting probes and the project state, notifies the owner on any change and runs the after-lift runbook (quota requests, prodcheck, Play/WhatsApp/Universal Links follow-ups), then disables itself.
- 6 Nov: Hosting "refresh" attempts did nothing: a fresh release on the live channel (switched to the identical previous version f988949e...) still answers "Site Not Found" on prohost-f766f.web.app. Re-releasing the already-live version is refused by Hosting. Conclusion stays: serving is off at the policy-restriction level.
- 6 Nov: quota facts (Cloud Quotas API): Cloud Functions V2 write requests 60/min/region (deploys of many functions queue for 10+ min), Cloud Run writes 60/min/region, Cloud Run InstancesPerProject 100 (87 services x maxInstances 10 can exceed it under load). All are increase-eligible but requests are refused while the restriction is active.

## 6 to 7 Nov (watch findings)
- 6 Nov 13:07 to 13:12 UTC: the appspot service account key `e5b6952d...` was used from 198.23.132.121 to enable `aiplatform` (EnableService), call Vertex `GenerateContent` (denied), list service accounts and request access tokens. Our `VertexAI-or-Gemini-use` alert emailed the owner at 13:10. Vertex/AI APIs are enabled again as a result. Owner notified by push on 7 Nov 03:57; awaiting decision (OWNER_TODO #27). Restriction still active at 06:56 (quota refused, Hosting "Site Not Found", no Google decision email).

- 7 Nov: investigation of 198.23.132.121 (and the two earlier addresses) completed; added Maintenance task `ipwhois` (read-only owner lookup). Findings and recommendations: docs/INCIDENT_2026-11-appspot-key.md.

## Not done yet
Admin batch (`assignInitialRole`, `forceProHostUpgrade`, `revokeProHostRole`, other admin callables); Maps key restrictions; secret rotation; budget alert; uptime checks; App Check enforcement; final cleanup of temporary access (see OWNER_TODO).

## 7 Nov 2026 - GO IMPLEMENT (package A, B, D; C drafts only)
- A1 appspot key e5b6952d disabled (undo: `gcloud iam service-accounts keys enable`; delete after 7 quiet days).
- A2 `aiplatform.googleapis.com` disabled (no dependents blocked).
- B Auth user kuBhi1EQ... (exploreprohost99@gmail.com): disabled, sessions revoked (validSince), claim `suspended:true`, profile `isSuspended/flaggedSpam/flaggedReason`, audit_log entry. Data kept. IP block impossible (no sign-up IP recorded).
- D alert policies `AI-API-enabled-attempt`, `Disabled-appspot-key-used` (email channel).
- C drafts in `docs/drafts/` - NOT sent. Appeal NOT submitted (owner instruction).

## 7 Nov 2026 - avatar/persona audit
- Deployed site (`public/`): no avatar or synthetic-person media (homepage/story videos are UI-only, frames checked).
- Removed repo leftovers: `marketing/source/persona.js` (cartoon avatar template), `marketing/audio/voice1/*.wav` (synthetic voice lines); renamed cover `11-office-sourcing-presenter.png` to `11-office-sourcing.png` (plain text card).
- Not checkable from the repo: earlier Instagram posts; owner to confirm in the app.

## 7 Nov 2026 - AI policy audit
- AI in use: Claude (Anthropic API) for the Instagram/WhatsApp assistant; Cloud Text-to-Speech for our own narration. No Google generative text/image/video/music.
- Deleted dead Vertex workflows/scripts (Lyria music, Imagen backgrounds); corrected `docs/GEMINI_USE.md`, `docs/INSTAGRAM.md`.

## 7 Nov 2026 - Auth Platform branding review
- Screenshot values match the repo (home, /privacy, /terms, 3 authorised domains). Interim working legal URLs are the `legalDocumentPage` function URLs (Play/iOS only). OAuth warning is client-settings only (no code change). Items 34, 35 in OWNER_TODO.

## 7 Nov 2026 - branding verification fix prepared
- Google's six branding findings all trace to pro-host.tech not being served + privacy URL on cloudfunctions.net. Added `public/_redirects`, `public/_headers` and a DEPLOYMENT.md note for a Cloudflare Pages fallback. Owner steps 36, 37.

## 7 Nov 2026 - iOS plan approved
- Approved plan saved as `docs/IOS_PLAN.md` (milestones M0-M8, owner checklist, secrets locations). No iOS code or backend change yet.

## 7 Nov 2026 - iOS M0 skeleton
- Added `ios/` (XcodeGen spec, splash app, unit test, icon), `ios-ci.yml` and `ios-testflight.yml`. Nothing deployed; no Android/functions/site change.

## 7 Nov 2026 - iOS: first TestFlight attempt
- Owner attached the ASC `.p8` in chat (key id HF7C34HW38): treat as exposed, rotate after the first successful upload (new key straight into GitHub secrets). Sandbox proxy blocks api.appstoreconnect.apple.com (403), so Apple state is checked from the macOS runner only.
- ios-testflight.yml gained a branch trigger `ios-beta/**` (workflow_dispatch needs the file on main; the git proxy refuses tag pushes); branch `ios-beta/1` pushed to run it.

## 7 Nov 2026 - iOS TestFlight pipeline
- Workflow hardened: key text accepted as PEM/body/base64 and validated (`openssl pkey -check`); archive is unsigned (no registered device needed), export signs for distribution via the API key. Result: archive OK, export blocked by "Cloud signing permission error" (needs an Admin-role key). Runs ios-beta/1..8.

## 7 Nov 2026 (night) - iOS readiness audit and owner guide
- Added `docs/IOS_READINESS.md` (audit: iOS is ~3% of the work by size; backend/App Review gaps; exact-parity strategy and screen port order) and `docs/OWNER_GUIDE.md` (click-by-click owner steps). Decisions: Google Maps iOS SDK, no Admin on iOS.

## 7 Nov 2026 (late) - iOS signing fixed, build 13 uploaded
- Unsigned-archive flow dropped: entitlements were missing and embedded frameworks failed validation. New flow: temporary distribution certificate + App Store profile via the ASC Admin API key, manual signing for Release of the app target, upload, cleanup (deleted profile and certificate, HTTP 204). Fixed the build number (Info.plist now follows CURRENT_PROJECT_VERSION). Signed entitlements verified: Sign in with Apple, push, App Attest. Runs ios-beta/10-12.

## 8 Nov 2026 - audit of owner statuses and automated temporary hosting
- Read-only audit (IAM, API keys, Secret Manager data-access logs, Sheet permissions): second owner still `projectOwnerInvitee`; Firebase iOS/Android/Browser keys have no application restriction (Maps iOS key is restricted); BOOTSTRAP_SECRET unused, only probed (refused) by the intruder IP on 5 Nov. No change made to GCP.
- Hosting: replaced the Cloudflare idea with Netlify free, deployed from `.github/workflows/site-deploy.yml` (CLI upload of `public/`, creates/finds the site, attaches pro-host.tech, probes 10 URLs). `public/_redirects` and `_headers` rewritten for Netlify (clean URLs, proxies to the five public Cloud Run services of the functions, URLs from `gcloud run services list`; all have `allUsers` invoker and ingress `all`). The sandbox proxy blocks `*.cloudfunctions.net` and `*.a.run.app`, so the proxies are first exercised by the workflow probes. `DEPLOYMENT.md` and `OWNER_GUIDE.md` B2 updated. Needs the owner's `NETLIFY_AUTH_TOKEN` secret and the Hostinger DNS change.
- `ios-inspect/2` (read-only): app ProHost, builds v1 and v13 VALID, capabilities APPLE_ID_AUTH / APP_ATTEST / IN_APP_PURCHASE / PUSH_NOTIFICATIONS, TestFlight group "Team" (internal) with 1 tester in state INVITED, certificates: one DEVELOPMENT "Created via API" (exp 7 Nov 2027), no distribution certificate left, no devices, no profiles. `asc_inspect.py` now also lists tester groups. Pull request #51 opened for the commits not on main.

## 8 Nov 2026 (morning) - temporary host deployed
- Owner merged PR #52 (carried all branch commits); its automatic deploy failed only because `NETLIFY_AUTH_TOKEN` was not yet set. After the secret was added, `site-deploy.yml` was dispatched by hand (run 42462564247): created Netlify site `prohost-tech-site` (id 405a7a7b-45d3-4a3f-9b58-6ac556dcddcc, not a secret), deployed `public/`, attached `pro-host.tech` + `www`, certificate request HTTP 200, ten probes OK on the unique deploy address (home, privacy, terms, delete-account, get, landing page, assetlinks, robots, sitemap via the `siteSitemap` proxy, `/ig-webhook` 403 as expected). DNS before the change (hostingcheck 06:57 UTC): apex A 199.36.158.100, TXT SPF `_spf.firebasemail.com` + `firebase=prohost-f766f`, www CNAME prohost-f766f.web.app. DNS change is the owner's step.

## 8 Nov 2026 (08:30 UTC) - pro-host.tech now served by Netlify
- Owner changed DNS at Hostinger (nameservers lunar/solar.dns-parking.com kept). hostingcheck: apex A 75.2.60.5, www CNAME prohost-tech-site.netlify.app, TXT/MX untouched. `site-deploy.yml` run 42463344558 probed https://pro-host.tech: ten of ten OK (home, privacy, terms, delete-account, get, landing page, assetlinks, robots, sitemap via proxy, ig-webhook 403). Certificate request returned 422 but HTTPS already validates (curl without -k succeeded), so a certificate exists. Workflow gained www and http->https probes.
- Verified: second owner holds roles/owner; Firebase iOS key restricted to the bundle ID; the Android Firebase key is still without application restriction (optional; it also lists 25 API targets).
- Observed: the merge of PR #52 triggered the normal Firebase deploy and a hosting release (03942acc, 64 files) was FINALIZED at 08:07 UTC; whether prohost-f766f.web.app serves it is checked by the 3-hourly watch.

## 8 Nov 2026 (afternoon) - legal texts rewritten for Android + iOS
- Rewrote `public/privacy.html`, `public/terms.html` (Section 17 byte-identical, numbering kept; new 1.1/1.2, 7.1 per store, 9.2-9.4, 15.2-15.3, cancel paragraph in 6) and `public/delete-account.html`; removed stale SMS-OTP, ID-document KYC, email-code sign-in, and "no passwords" claims; added Apple/iOS, Google Maps, AI assistant, Netlify, backups, DSA.
- In-app Privacy/Terms in `LegalContent.kt` are now generated from the website pages by `scripts/legal/sync_legal_content.py` (CI step `--check` in build-and-deploy.yml); revocation policy stale lines fixed. `EFFECTIVE_DATE` and `CURRENT_CONSENT_VERSION` = "November 8, 2026". The in-app Terms now carry the website's Section 17 (arbitration in Beirut) instead of the old "exclusive courts of Beirut" sentence.
- `functions/src/legal/deleteAccountPage.ts` regenerated from the HTML (test keeps them equal). New `docs/APPLE_DECLARATIONS.md`. site-deploy probes now require "Sign in with Apple" on /privacy and "Apple App Store" on /terms.
- Search Console: the failed sitemap row belongs to the `prohost-f766f.web.app` property; submit `sitemap.xml` under the `pro-host.tech` property instead.

