# Google Cloud IAM inventory — project `prohost-f766f` (ProHost)

Snapshot of 2026-11-01, copied from the Cloud Console (IAM and Service Accounts pages). Principals, roles and key **IDs** only —
key IDs are public identifiers; never write key material, tokens or passwords here, in code, in logs or in chat.
Re-take the snapshot after any IAM change.

## Project IAM (Include Google-provided role grants was off)

| Principal | Purpose | Roles |
|---|---|---|
| `geo.elnajjar@gmail.com` (Georges El Najjar) | Owner | Owner (Legacy) |
| `firebase-adminsdk-fbsvc@prohost-f766f.iam.gserviceaccount.com` | Firebase Admin SDK; believed to be the CI `FIREBASE_SERVICE_ACCOUNT` (unconfirmed) | Agent Platform user, Android Management User, App Engine Deployer, Cloud Datastore backup schedules admin, Cloud Datastore Owner, Cloud Deploy Admin, Cloud Deploy custom target type admin, Cloud Functions Admin, Cloud Scheduler Admin, Cloud Storage for Firebase Admin, Commerce Price Management Private Offers Admin, Financial Services Viewer, Firebase Admin SDK Administrator Service Agent, Firebase App Check Admin, Firebase App Distribution Admin, Firebase Authentication Admin, Firebase Extensions Viewer, Firebase Rules Admin, Firebase rules Firestore service agent, Logs Viewer, Payments Reseller Admin, Payments Reseller Products Viewer, Payments Reseller Promotions Viewer, Payments Reseller Subscriptions Editor, Pub/Sub Publisher, Secret Manager Admin, Service Account Token Creator, Service Account User, Service Usage Admin, Vertex AI agent sandbox service agent |
| `646730915838-compute@developer.gserviceaccount.com` | Default compute service account (Cloud Run functions run as it unless overridden) | Billing Account Usage Commitment Recommender Viewer, Cloud Run Invoker, Editor (Legacy), Eventarc Event Receiver, Firebase Extensions Viewer, Subscribe with Google Developer, Vertex AI Custom Code Service Agent, Vertex AI Service Agent |
| `prohost-f766f@appspot.gserviceaccount.com` | App Engine default service account | Editor (Legacy), Firebase Extensions Viewer |
| `ext-firestore-send-email@prohost-f766f.iam.gserviceaccount.com` | Trigger Email extension (`mail/{id}` → email) | Cloud Datastore User |
| `prohost-map@prohost-f766f.iam.gserviceaccount.com` | Google Maps Platform administration | Google Maps Platform insights/recommendations admin, Maps API Admin, Maps Platform datasets admin |

## Service accounts page (all Enabled, none disabled)

| Account | Key IDs (created) | Notes |
|---|---|---|
| `prohost-f766f@appspot.gserviceaccount.com` | `e5b6952d1fe7c81e72085d3bf174b27035ca51b1` (5 Oct 2026) | Legacy Editor **with a user-managed key**: highest risk. Find where the key is used and replace it with a least-privilege account. |
| `646730915838-compute@developer.gserviceaccount.com` | none | Legacy Editor. |
| `ext-firestore-send-email@prohost-f766f.iam.gserviceaccount.com` | none | Needs only Datastore User. |
| `firebase-adminsdk-fbsvc@prohost-f766f.iam.gserviceaccount.com` | `16b78225b5f3b2a8c38ff7030cea7f9c2decd792`, `02f9b0a53752cfc8981f756b8b9a2527110f5fcf` (both 5 Sep 2026) | Two keys: delete the unused one, rotate the other. |
| `meta-ads-play@prohost-f766f.iam.gserviceaccount.com` | `e2d7d31ab7299061fb3b59e377e60abe291c623f` (11 Oct 2026) | **No role on the project IAM page**: its permissions are granted elsewhere (Play Console user, another project or resource-level IAM). Purpose to be confirmed. |
| `prohost-map@prohost-f766f.iam.gserviceaccount.com` | none | Maps only. |

## Risks and to-dos
- Legacy Editor/Owner on three principals (compute, App Engine default, the owner); prefer narrow roles.
- `firebase-adminsdk` can mint tokens for any service account in the project (Token Creator) and holds Secret Manager Admin and Datastore Owner.
- Four user-managed keys on three accounts; rotate on a schedule and delete unused ones.
- `Agent Platform user` and Vertex AI roles remain on adminsdk and compute. Text AI moved to the Anthropic API on 31 Oct 2026 (`docs/GEMINI_USE.md`),
  so Vertex / Generative Language grants and APIs can be removed once nothing else needs them (relevant to the Generative-AI policy warning).
- Not in this snapshot: the earlier "many service accounts" the owner remembers. Check Audit Logs (`DeleteServiceAccount`, `DisableServiceAccount`,
  `DeleteServiceAccountKey`, `SetIamPolicy`), the Recently deleted list (undelete within 30 days), other Google Cloud projects, and org policies.

## Not shown on the project IAM page — check separately
- Pub/Sub topic `play-billing-rtdn`: `google-play-developer-notifications@system.gserviceaccount.com` must be Publisher.
- Secret Manager per-secret accessors, Cloud Run/Functions invokers, Storage buckets, Artifact Registry, Billing account IAM.
- Play Console › Users and permissions: the CI service account must be invited there for release and billing APIs.
- Firebase console › Project settings › Users and permissions; App Distribution testers.
- IAM Deny policies, Recommendations history, Organization policies (key creation), org/folder IAM.
- GitHub secrets: `FIREBASE_SERVICE_ACCOUNT`, `PLAY_APP_SIGNING_SHA1/256`, `ANTHROPIC_API_KEY`, `IG_*`, `APP_CHECK_DEBUG_TOKEN`.

## Live read of 1 Nov 2026 (Maintenance `iamaudit`, read-only, run as the CI service account)
- 27 principals, 60 bindings. Google-provided agents aside, the people/accounts that matter: owner `geo.elnajjar@gmail.com` (Owner), `firebase-adminsdk-fbsvc`
  (CI; confirmed able to read the project IAM policy; holds `aiplatform.user`, `serviceUsageAdmin`, `secretmanager.admin`, `datastore.owner`, token creator),
  default compute, App Engine default and Cloud Services accounts (all **Editor**, which includes `aiplatform.*` and therefore Vertex / Gemini calls), `prohost-map` (Maps only),
  `ext-firestore-send-email` (Datastore User). `meta-ads-play` has no project role.
- Enabled AI-related APIs: `aiplatform.googleapis.com` (Vertex / Agent Platform), `cloudaicompanion.googleapis.com` (Gemini for Google Cloud), `visionai.googleapis.com`,
  `texttospeech.googleapis.com`. `generativelanguage.googleapis.com` (Gemini API) is **not** enabled in this project.
- The API-key list is not readable by the CI account (`apikeys.keys.list` denied): check Console › APIs & Services › Credentials for unrestricted keys.
- Changes proposed for the Generative-AI access-control concern (none applied yet): remove `aiplatform.user` from `firebase-adminsdk` and grant it only to a dedicated
  media account when a TTS / music / image run is needed; replace Editor on the compute and App Engine accounts with narrow roles (test in steps: the functions run as the compute account);
  disable `cloudaicompanion` and `visionai`; delete the unused service-account keys; keep `aiplatform.googleapis.com` enabled only while Lyria / Imagen / TTS runs are needed.

## Activity review of 2 Nov 2026 (Maintenance `activityaudit`, read-only, last 30 days)
- 128 Admin Activity entries after dropping routine deploy traffic. Principals seen: the owner, `firebase-adminsdk-fbsvc` (CI), `prohost-f766f@appspot` (App Engine default SA), Google service agents.
- **Needs an answer from the owner:** on 2 Nov 05:21 UTC the App Engine default service account (Editor, user-managed key from 5 Oct) enabled `aiplatform.googleapis.com`
  twice, from a Windows browser user-agent and an IP that is not the owner's 10:19 session; CI had disabled that API on 1 Nov 08:17 (`apis apply`). A service account acting with a
  browser user-agent means its key (or a token minted from it) is in use outside CI. Treat the key as exposed: delete key `e5b6952d…` and re-disable the API.
- Vertex `CreateCustomJob` by `firebase-adminsdk` in October = the GPU lip-sync attempts from the reel workflows (quota-denied); no other AI jobs in the log.
- Cloud Run warnings (30 days, 16 services, 288 entries) are `app_check_unverified` (App Check deliberately unenforced) and 2 `igTokenRefresh` failures; no abuse pattern.
- Compute Engine: not readable by the CI account (API off or no permission); there should be no VMs.

## 2 Nov 2026 follow-up
- `aiplatform.googleapis.com` was re-enabled on 2 Nov (05:21 by the App Engine default service account from a browser user-agent; owner session at 10:19 enabled more services) and re-disabled by Maintenance `apis apply` the same day.
- `cloudaicompanion.googleapis.com` stays enabled because `geminicloudassist.googleapis.com` (Gemini Cloud Assist) depends on it: disable `geminicloudassist` first, then re-run `apis apply`.
- CI can't list service-account keys (`keysecurity` is read-only and showed none): key review and disabling (App Engine account key `e5b6952d…`, the unused `firebase-adminsdk` key) is done in the console.
- Play Data safety deletion URL while Hosting is down: `https://europe-west1-prohost-f766f.cloudfunctions.net/legalDocumentPage/delete-account` (served by the function, verified 200).
