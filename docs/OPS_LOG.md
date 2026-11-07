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
