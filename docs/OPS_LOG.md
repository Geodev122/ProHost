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

## 8 Nov 2026 (evening) - architecture decision recorded (no GCP, Drive, Play or GitHub setting changed)
- Owner chose Kotlin Multiplatform + Compose Multiplatform for Android and iOS; Admin Console to become a web app; AI agent to become its own project. Plan and rules written to `CLAUDE.md` (new section), `docs/migration/*`; `docs/IOS_PLAN.md` and `docs/IOS_READINESS.md` marked superseded for the UI approach. CLAUDE.md Gradle note corrected to 9.7.1.


## 9 Nov 2026 — retention (owner-approved)
- Firestore TTL policies created via Maintenance `ttl apply` (runs 182): collection group `notifications` field `ttlAt`, collection group `mail` field `expireAt`.
- Storage lifecycle rules added via Maintenance `lifecycle apply` (run 183): Delete after 365 days for prefixes `listing_verification_docs/` and `booking_agreements/` on bucket `prohost-f766f.firebasestorage.app` (no rules existed before).
- Owner also (own hands, 9 Nov): removed the appspot service account from Play Console; restricted the "iOS key (auto created by Firebase)" to bundle `app.geonajjar.prohost`; confirmed the APNs key is uploaded; declared EU trader status; Paid Apps Agreement is processing.
- iOS S2 (session store, router, role tab shell) pushed; iOS CI green on 767d5fd. No TestFlight build uploaded for it yet.

## 14 Nov 2026 — branch merged to `main` (owner: "merge")
- `main` fast-forwarded 987c19a → 2ef0493 (204 commits). CI deploys functions, rules, indexes and the website.
- Goes live: dated-booking server checks (only for requests carrying `dateModel: 2`; the published app 1.0.46 does not send it), report/block callables + rules + indexes, `notifications.ttlAt`, operator block in the legal pages, `/.well-known/apple-app-site-association`, admin console page `/console`.
- `pruneExpiredNotifications` stays until the deploy has run 48 h with the TTL policy ACTIVE.
- App Store Connect: 6.9-inch screenshots (EN, AR) and the subscriptions' review screenshot uploaded through the API (`asc_prepare.py` sections `screenshots`, `subreview`).

## 14 Nov 2026 21:21 UTC — Maintenance `prodcheck` after the merge (run 43642323275): healthy
- Functions: 91 exported, 92 deployed (the extra one is the Trigger Email extension), none missing, none inactive, Node 22. No function errors in 6 h. Callables answer (401 without sign-in, as they should).
- Indexes: 5 ready, 2 building (the two new `reports` indexes). Cloud Billing enabled. Public pages, redirects, sitemap (14 URLs) and the 6 legacy redirects fine.
- Play catalog: yearly $79.99 and `pro-monthly1` $8.99 active with their launch offers; RTDN self-test delivered in ~5 s. Last real Play notification: 4 Nov (one subscriber, so quiet is normal).
- Data: 55 profiles, 87 Auth users (32 without a profile = abandoned registrations, known), 2 listings, 5 bookings, no legacy fields, no parked purchases.
- To watch: `subscriptions` still holds one ACTIVE record for the retired `package_growth_mrr` plan (its holder is on a permanent admin grant; the record is ignored by every code path).

## 15 Nov 2026 10:52 UTC — backend deployed again (build-and-deploy run 43705512600, `main` 1f158a5)
First deploy since 2b1d587 (runs 336-338 were blocked by a missing PARITY.md row and an e2e test that used a price the Change price test had changed). Live now: report/block indexes, server checks for dated requests (`dateModel: 2`), the operating-days default in the date engine, price change by date + slot.
- 15 Nov 11:38 UTC: build-and-deploy run 43709428115 on main cab798d (FunctionsGateway, AuthGateway to :shared, SharedText seam) succeeded incl. e2e; no backend change.
- 15 Nov 12:15 UTC: build-and-deploy on main a1a63ab succeeded. Site deploy failing since 10:26 UTC (runs 17-20) at the Netlify site step (token missing/invalid) — owner item 54. iOS CI fixed: build_labels.py/build_icons.py read files moved to :shared.
- 16 Nov 09:42 UTC: first Compose-based iOS build uploaded to TestFlight (ios-testflight run 43838863898, main fd00871, build number = run number 14; shows the ProHost splash). Apple then mailed "certificate revoked": that is the workflow's own cleanup of its temporary distribution certificate (expected after every upload).
- 21 Nov 2026: `main` fast-forwarded to 73fbe7e (repositories, AnalyticsTracker, DiscoveryViewModel, MapModels, appString seam, keep.xml in :shared; Parity Gate 280/280 identical, iOS CI + Admin Web green on the same code). Backend unchanged by these commits.
- 21 Nov 2026: `main` fast-forwarded to b789517 (S3 libraries + strings codemod over ~40 UI files; Parity Gate 280/280 identical incl. Arabic, iOS CI green).
- 21 Nov 2026: owner saved new GitHub secrets `NETLIFY_AUTH_TOKEN` + `NETLIFY_SITE_ID`; Site deploy run 44888702006 (main b789517) succeeded: admin console built into `public/console`, production deploy, domain attached, live probes OK. `pro-host.tech/console` is live (admin email + password sign-in).
- 21 Nov 2026: `main` fast-forwarded to 0dcc452 (Coil 3 with BitmapFactory decoding; A2.1/A2.2: AuthViewModel, AuthFlow, ProHostViewModel, AnalyticsConsent in :shared; W1 batches 1-4: 33 UI files in :shared with PlatformLook/PhoneNumberHint/MediaPickers seams). Parity Gate 280/280, Admin Web + Migration Checks green; iOS CI verified on main.
- 21 Nov 2026: `main` fast-forwarded to 710d0d3 (W2 steps 1-3: shared LocationProvider/locate(), CountryLookup hook, PermissionPrompts split, AnalyticsConsentDialog; CI warm-up retries). Parity Gate 280/280, iOS/Admin Web/Migration green.
- 21 Nov 2026: `main` fast-forwarded to 58b40f6 (W2 step 4: shared PlatformMap seam; LebanonMapCanvas, DiscoveryScreen, MyFavoritesScreen in :shared). Parity Gate 280/280, Admin Web + Migration green; iOS CI verifies on main.
- 21 Nov 2026: main 58b40f6 iOS CI + backend deploy + site deploy green. `main` fast-forwarded to 660cf45 (W2 step 5: ListingLocationMapPicker on PlatformMap in :shared, shared Geocoding interface, `rememberAppLanguageTag`). Parity Gate 280/280, iOS/Admin Web/Migration green.
- 21 Nov 2026: `main` fast-forwarded to dcec8fc (W3/W5 batches 1-2: DateSlotPicker, listing wizard (CreateListingDialog, SubdivisionForm, EditListingDialog), RentalBookingDialog, AvailabilitySheet, RequirementsSheet, SpaceDetailsScreen, ManageListingScreen in :shared; seams LocaleDates, PlatformBackHandler, FullScreenDialogWindowSetup, fullScreenDialogProperties, rememberConfirmHaptic, formatGroupedDeviceLocale, StorageGateway.lastUploadFailure). Parity Gate 280/280, iOS CI + Admin Web green, Migration Checks green on d8f191a.
- 21 Nov 2026: `main` fast-forwarded to dba4718 (W4 batch 1 + W4/W5 batch 2 + AppUpdater: MyBookingsScreen, OwnerRentingProgressScreen, LegalContent + LegalDocumentDialog, ListingVerificationDialog, OwnerHubScreen, InAppUpdateBanner in :shared; seams SoundPlayer, PaymentReminders, LegalPdfs, HtmlDocumentView, openUrlFromScreen, AppUpdater). Parity Gate 280/280 + iOS CI + Admin Web + Migration Checks green on 4b00ec6.
- 21 Nov 2026: iPhone testers. Rony Braidi: App Store Connect team invitation sent (role Developer, ProHost only) by `ios-inspect.yml` `prepare=apply only=tester` (run 44954206310). Rewa Rammal: her earlier team invitation is still pending (run 44954228146). Both were e-mailed step-by-step instructions (accept Apple's invitation, install TestFlight, install ProHost, what to test in the current splash-only build, how to send feedback). After each replies "accepted": run the same workflow with `review_email` + `review_name` of that person to add them to the internal TestFlight group (TestFlight then e-mails them). `asc_prepare.py` tester names now come from `review_name` when given.
- 21 Nov 2026: `main` fast-forwarded to a115aec (A2.3 step 1: shared PlayProduct model, PlayOfferText + PlayPromo in :shared, BillingPlatform expect/actual; tester tooling; records). Parity Gate 280/280 (57994c5), iOS CI (a4cae99), Migration Checks (a115aec), Admin Web green.
- 21 Nov 2026: `main` fast-forwarded to fd92fb1 (A2.3 step 2 a-c: OfferChoice in :shared, PurchaseHost + rememberPurchaseHost, AppBilling surface, BillingController overrides). Parity Gate 280/280 (shard 3 warm-up flake, rerun), iOS CI, Admin Web, Migration Checks green.
- 21 Nov 2026: `main` fast-forwarded to 6db0c83 (A2.3 step d: Premium/Profile billing calls via platformBilling). Parity Gate 280/280, Migration Checks green (app-only change; iOS/web inputs unchanged).
- 21 Nov 2026: `main` fast-forwarded to 9987502 (A2.3 step e: formatMillisDate; OwnerSubscriptionsScreen (Premium) + PromoCountdownCard in :shared). Parity Gate 280/280, iOS CI, Admin Web, Migration Checks green.
- 22 Nov 2026: `main` fast-forwarded to 77c717e (Profile batch: SpecialistProfileScreen, SupportContact, friendlyErrorMessage in :shared; parseMillisDate, uriEncode + UriEncodingTest, isNoHandler, AppInfo.versionName). Parity Gate 280/280, iOS CI, Admin Web green.
- 22 Nov 2026: `main` fast-forwarded to d2f7ee7 (LoginAuthScreen in :shared over the AuthCredentials seam; CountryLookup.device; SessionAuth email). Parity Gate 280/280, iOS CI, Admin Web green.

### 22 Nov 2026 01:55 UTC — main ← nav graph in :shared, iOS shows the shared shell (a3f4222)
- Parity Gate (280/280, after one rerun of a render-shard warm-up flake), Migration Checks, Admin Web, iOS CI (incl. launch smoke) green.
- `ProHostAppRoot` lives in `:shared`; the Android app passes its splash and admin screens as `AndroidAppRootSlots`.
- iOS `MainViewController` renders `ProHostAppRoot`; iOS installs repository/auth defaults over the Swift bridges.

### 22 Nov 2026 04:25 UTC — TestFlight build 18: first iPhone build with the real shared app
- ios-testflight.yml run #18 (branch, ec62494) uploaded build 18: the shared `ProHostAppRoot` shell (same Compose UI as
  Android), email + password and Google sign-in (GoogleSignIn 9.x, nonce checked by shared code). Not yet on iOS: Sign in
  with Apple, map, photo picker, push.
- Fixes on the way: release Kotlin/Native link needs a 6 GB heap (4 GB ran out of memory), own workflow step (~24 min);
  GoogleSignIn 8.x has no nonce call (9.0.0 is the first); job limit raised to 120 min.
- main ← ee102d2+ (Parity Gate 280/280 on 889eda9, the last app/shared change; later commits ios/, workflows, docs only):
  iOS shared shell, iOS auth/repository defaults, Google sign-in on iOS, TestFlight release-build fixes.
- 22 Nov 06:13 UTC main ← 9014a9d+: iOS map (Google Maps SDK, key at TestFlight build), pickers (PHPicker,
  UIDocumentPicker, camera), location (Core Location). iOS-only changes; iOS CI green on each.
- 22 Nov 06:15 UTC TestFlight build 19 (5680cd2): shared app + iOS map (Google Maps SDK with the MAPS_IOS_API_KEY secret).
  Build 20 dispatched at 06:20 on 821cdb2: + photo/document/camera pickers and location.
- 22 Nov 06:45 UTC main ← 0d0913c+: iOS push (FirebaseMessaging, APNs alert added to every FCM message by
  `fcmMessage` in functions/src/lib/push.ts; Android's data-only payload unchanged). All 4 branch checks green; the
  functions change deploys with this push.
- 22 Nov 07:00 UTC: owner enabled the Apple provider in Firebase Authentication; Sign in with Apple switched on in the
  iOS app (`AuthProviders.APPLE_ENABLED`).
- 22 Nov 07:05 UTC TestFlight build 20 (821cdb2): + photo/document/camera pickers and location. Backend deploy on main
  ea96445 succeeded (iOS push: APNs alert in FCM messages is live).

## 22 Nov 2026 — main e5c0729 deployed (build-and-deploy run 366)
- Live: single-listing tier on the server (tierOf / listingLimitFor, `ownerListingLimit` mirror, publish-limit trigger, rules
  protect the field), push to every device (`fcmTokens`, 48 h TTL, APNs thread-id/collapse-id), Apple payments server
  (`verifyAppleTransaction`, `appStoreNotifications`), App Store grants mirrored as app_store/ios.
- App-only (ships with the next Android/iOS build): Profile rework, booking timing bullets, Premium tier switch, StoreKit 2 client.
- Next: Maintenance `singleplan` (dry run, then apply) creates Play `package_single_mrr`; `ios-inspect` creates `prohost.single.*`.

### 22 Nov 2026, 12:46 UTC — single-listing tier live in the stores
- Google Play: Maintenance `singleplan apply` (run 45023959428) created `package_single_mrr` ("ProHost Premium · 1 listing",
  en-GB) with base plans `single-monthly` (P1M, US $4.49) and `single-yearly` (P1Y, US $44.99), 174 regions each, prices =
  Play's conversion (regions version 2026/01), both ACTIVE. The first attempt failed on regions version 2022/02 (Argentina
  priced in ARS); the tool now uses the version Play's conversion reports (main 2957e69).
- App Store: `prohost.single.monthly` / `.yearly` in the "ProHost Premium" group (levels 4/3), $4.49 / $44.99 in 175 of 175
  countries, review note + screenshot, READY_TO_SUBMIT; App Store Server Notifications V2 URLs set (production + sandbox).
- TestFlight build 21 uploaded (run 45013804079): Sign in with Apple, StoreKit 2 (both tiers), deep links, calendar.
- Deploy fix: `build-and-deploy.yml` now redeploys functions that hit the Cloud Functions mutation quota (they kept old code
  while `firebase deploy` exited 0).
- 1.0.50 (50) on the Play internal track (release run 45024466296, Parity Gate 45024453594 green): second Premium tier
  with tier switch, Profile rework, booking timing bullets, push on every device. Production stays 1.0.46.

### 22 Nov 2026, 17:28 UTC — App Store daily re-sync live
- main 1665ba0 deployed (`appleSyncJob`, Maintenance tasks `applekey` / `applesync`). Maintenance `applekey` (run 45049541076) stored
  the In-App Purchase key (…L86D) in server-only `app_config/apple_iap`; the first `applesync` found 0 App Store subscriptions,
  so the key is not yet proven against Apple — it will be on the first TestFlight sandbox purchase (re-run `applesync`).
- main f75758a deploy published functions/rules/hosting but its quota-redeploy step failed on "no function changed"
  (empty grep under pipefail); fixed in 4a13fc4.
- 17:49 UTC Maintenance `ttl` dry run: **no TTL policy exists** on `notifications.ttlAt` or `mail.expireAt` (ttlState none; the 9 Nov
  "CREATING" request never completed). Owner-approved 9 Nov → `ttl apply` dispatched again 17:51; `pruneExpiredNotifications` stays.
- Launch tracker page (owner's phone): https://claude.ai/artifact/EKxcJsvvHzHkxZLV4hQyx3 (source `docs/progress/prohost-progress.html`).
- 17:42 UTC TestFlight build 22 uploaded (run 45045601019, main f75758a).
- 18:30 UTC root cause of the missing TTL: each deploy (`firebase deploy --only firestore --force`) resets field settings to
  `firestore.indexes.json`, which had no TTL entries, so the policy requested 17:50 was gone again by 18:18. Fix: TTL declared in
  `fieldOverrides` (`notifications.ttlAt`, `mail.expireAt`); the next main deploy creates them.

## 22 Nov 2026 (evening)
- build-and-deploy on `main` ea673a3 green: TTL policies now live in `firestore.indexes.json` fieldOverrides.
- Maintenance `ttl` (run 199): `notifications.ttlAt` and `mail.expireAt` ttlState=ACTIVE, backlog 0 → `pruneExpiredNotifications` retired
  (export + file removed; deleted from production by the next `main` deploy).
- Maintenance `prodcheck` (run 200): exported 94 / deployed 95 (the extra one is the Trigger Email extension), 7 indexes READY, no function
  errors in 6 h, public pages + SEO + legacy 301s OK, Play base plans/offers as expected, RTDN self-test delivered in ~10 s, no parked purchases.
- New Maintenance task `reviewaccounts` (App Review demo accounts; passwords only in server-only `app_config/app_review`, masked into the job
  env, then `asc_prepare.py --only review` fills the form). `asc_prepare` no longer clears a stored demo login when its env has none.
- 22 Nov 20:12: console Edit listing (ADMIN_PARITY 37, `ListingEditor.kt` + web pickers/Storage bridge/shared strings) green on the branch
  (Parity Gate run 200 after one rerun of an environment-wide test-JVM flake; Admin Web, iOS CI, Migration Checks) → fast-forwarded to `main`.
- 22 Nov 20:35: owner granted the CI account Monitoring AlertPolicy Editor + NotificationChannel Viewer and ticked Play Data safety
  "Other user-generated content". Maintenance `alerts apply` (run 202) read the 7 existing policies, then failed with 403
  `logging.notificationRules.create` (log-based alerts) → owner to add Logs Configuration Writer.
- 22 Nov 20:43: owner added Logs Configuration Writer; Maintenance `alerts apply` (run 203) created alert policies Function-errors,
  AppStore-sync-failed, Play-sync-failed (email channel "ProHost owner email", max one per hour each).
- 22 Nov: owner approved the three retention sentences; added to `public/privacy.html` §6 and the in-app legal text (af7a28a).
- 22 Nov 20:57: owner's "Add for Review" was refused (China mainland security assessment + AI-content filing, no price tier,
  no build). ios-inspect run 17 (sections price, nochina, build=22): app price recreated as Free (base USA; the old schedule
  had no manual price), China mainland removed from availability (174 countries + new ones), build 22 attached to version 1.0.
  Left for the owner: tick the 4 subscriptions on the 1.0 page; real iOS screenshots; test TestFlight 22 before submitting.
- 22 Nov 23:48-23:50: store parity. Play: Maintenance `singleplan apply` (run 206) created `package_single_mrr` base plans single-monthly
  ($4.49) and single-yearly ($44.99), ACTIVE in 174 regions (the 1-listing tier had existed only on the App Store). App Store
  (ios-inspect run 19): phased release on (7 days), offer code PROHOST2026 created on prohost.premium.monthly (1 month free, new
  customers, replaces the intro offer, 175 countries), promotional images 1024x1024 on all 4 subscriptions. The custom code text
  "PROHOST2026" was refused (409: app not yet approved) → re-run `ios-inspect prepare=apply only=offercode` after approval.
  New read-only audit `scripts/ios/asc_audit.py` (runs in every ios-inspect).
- 23 Nov: callables now also log `app_check_verified` (lib/callable.ts); Maintenance `appcheck [days]` reports the verified share per
  function from both log lines (roadmap E2: enforce only at >= 99 %). Read it after a week of traffic on the new deploy.
- 23 Nov: E4 batch 4 — six admin callables moved to `run-admin` (Firestore/Auth/Storage only; Play-bound functions stay on the default
  account). Check after the main deploy with Maintenance `logerrors 6` (PERMISSION_DENIED would show there).

## 23 Nov 2026 — iOS submitted; real screenshots ready for the next version
- App Store Connect (ios-inspect run 20): version 1.0 and app info are **WAITING_FOR_REVIEW** (submitted by the owner), build attached,
  both App Store Server Notification URLs set, all 4 subscriptions READY_TO_SUBMIT. The version is no longer editable, so the
  submitted listing keeps the 8 mock-up screenshots per language.
- Real iOS screenshots now work (ios-screenshots.yml): the earlier runs failed because the unsigned simulator build had no
  keychain, so Firebase Auth refused every sign-in ("Sign-in failed"); the build is now ad-hoc signed (`CODE_SIGN_IDENTITY=-`).
  Captured in ios/store/screenshots-real: en 8 screens, ar 6. `asc_prepare` section `screenshots` now prefers them
  (explore, listing, availability, premium, profile, welcome; empty Rentals/Saved left out) — run it for the next version (1.0.1).
- Prodcheck after E4 batch 4 (run 45092680761): 93/93 functions active, no function errors in 6 h, RTDN self-test ok.
- 23 Nov 2026: Rony Braidi accepted Apple's team invitation; added to the internal TestFlight group "Team" (ios-inspect run 45145851015, only=tester) — TestFlight e-mails him the install invitation (all builds, latest 22). Rewa still pending.
- 23 Nov 2026 (owner): Unlimited doubled on both stores — $17.99 / month, $159.99 / year (yearly launch offer 50 % → $79.99).
  Play: Maintenance `unlimitedprice` (`billing/playUnlimitedPrice.ts`, dry run · `apply`; Play keeps current subscribers on
  their price cohort). Apple: `asc_prepare` subscriptions (new price with `preserveCurrentPrice`) + offers (`yearly_intro_usd` 79.99).
- 23 Nov 2026: iOS registration failed for everyone ("You must accept the Terms…"): Kotlin/Native boxes Booleans as an
  NSNumber with objCType "c", Firebase iOS sent `tosAccepted: 1`. Fix: `KotlinWire.normalize` in the callable and Firestore
  bridges; `assignInitialRole` also accepts 1 (build 22 is in review). Registration step 2 = "Use my current location" or
  country + area dropdowns (`Regions`, generated from country-region-data, MIT); step 3's SIM button only where Android's
  Phone Number Hint exists (iOS: keyboard AutoFill via the phone content type), with a message when no number is offered.
- 23 Nov 2026: Android 1.0.51 (51) released to Play production, staged 20 % (release-bundle, notes en-GB); `docs/release/published.txt` → 1.0.51.
- 23 Nov 2026: Maintenance `unlimitedprice` dry run (run 45162403689): pro-monthly1 USD 8.99 → 17.99, pro-yearly 79.99 → 159.99,
  174 regions; offers free-4-months, prohost2026, launch-50-first-year are free/relative (follow the base price). Applied with code `apply`.
- 23 Nov 2026: Maintenance `clearsaves` dry run (run 45162409505): 3 profiles with 3 old listing-level saves; applied with code `apply`
  (saves are per room since 1.0.51).
- 23 Nov 2026 11:17 UTC: `unlimitedprice apply` (run 45163840570): Play patched (regions version 2026/01), pro-monthly1 $17.99 and
  pro-yearly $159.99 for new subscribers in 174 regions; offers unchanged (relative). `clearsaves apply` (run 45163845586): 3 old saves cleared.
- 23 Nov 2026 12:31-12:42 UTC, iOS resubmission (owner: "Replace build in review"): withdraw (ios-inspect run 45175084540) cancelled
  the open submission → 1.0 DEVELOPER_REJECTED; TestFlight 23 (main 83e69d9, run 45162416590) VALID; `only=build 23` (run 45176100607)
  attached it; `only=submit` (run 45176661576) created a new review submission with version 1.0 + build 23 → WAITING_FOR_REVIEW.
  The four subscriptions could NOT be added through the API (HTTP 409 STATE_ERROR.INVALID_REQUEST_ENTITY_STATE_INVALID "This
  subscription cannot be reviewed"): a first subscription must be ticked on the version page ("In-App Purchases and Subscriptions")
  in App Store Connect, which the API cannot do. They stay READY_TO_SUBMIT. Lesson: for a version carrying first-time subscriptions,
  the final Add for Review is the owner's click in the web UI; asc_prepare `submit` is only for versions without new subscriptions.
- 24 Nov 2026 (owner: iOS map blank, Manage subscription opened a broken sandbox page, Rony's Apple sign-up crashed):
  - Manage subscription / Orders now follow the store that bills the plan, per device (`StoreBrand.manageRoute`, shared,
    unit-tested): iOS App Store plan → Apple's sheet (falls back to apps.apple.com/account/subscriptions), Google Play plan
    → Play's web page; Android App Store plan → Apple's page. iOS texts naming Google Play read as the App Store (2.3.10).
  - Rony: the only Sign in with Apple account (Hide My Email relay, profile never completed, created 23 Nov) deleted with
    Maintenance `appleusers delete:<uid>` (run 45423828323) so he can sign up fresh. The sign-up form now says "Signed in
    with Apple · email hidden" instead of showing the relay address.
  - Crash hardening: every native callback resumes its coroutine at most once (`resumeOnce`: Apple/Google sign-in,
    Firebase, Storage, location, push) — a second resume terminates a Kotlin/Native app; the Apple sheet controller is
    retained and an overlapping request is closed first. Crashlytics' read API was enabled (Maintenance `crashes enable`),
    but the CI account lacks the Crashlytics Viewer role, so stacks were not readable from CI.
  - Maps: maps-ios-backend is ENABLED; key restrictions not readable by CI (apikeys.keys.list denied). TestFlight now
    annotates whether GMSApiKey is in the IPA; ios-screenshots checks whether the map draws tiles.
- 24 Nov 2026 15:50 UTC: main e6727bd (Parity Gate, iOS CI, Admin Web green). Android 1.0.52 (52) released to Play production,
  staged 20 % (release-bundle run 45430103617); published.txt → 1.0.52. TestFlight 24 from main (run 45430110313).
  ios-screenshots map check failed to run (no Pillow on the macOS runner) — install line fixed.
- 24 Nov 2026 16:49 UTC: iOS map blank — root cause found: the GitHub repository secret `MAPS_IOS_API_KEY` is EMPTY / not set
  (Maintenance `mapskey`, run 45441464601); TestFlight 24 confirmed `GMSApiKey` missing in the IPA, so the app never installs
  the Maps factory. Owner action: add the repository secret (value = Maps SDK for iOS key restricted to bundle
  app.geonajjar.prohost), then a new TestFlight build. Maps SDK for iOS API is enabled.
- ITMS-90725 on build 24 (built with iOS 26.5 SDK): warning only; from April 2027 uploads need Xcode 27. TestFlight picks the
  newest Xcode on the runner and now warns while it is below 27.
- 24 Nov 2026: "Function-errors" alert (23 Nov) explained — Trigger Email extension `processqueue` logged 2 errors (23 Nov 11:55,
  24 Nov 18:42): the mail provider refused messages to `@example.com` test addresses (550 "use our testing email address").
  No real user mail was lost. Fix: `sendEmail` skips reserved, never-deliverable domains (`isUndeliverableAddress`, tested).
  Also 374 errors/72 h were plain GET probes on callables ("Invalid request, unable to process", rejected before our code);
  the Function-errors alert filter now excludes both noise patterns (Maintenance `alerts apply` updates existing policies).
