# Owner to-do list (things only the project owner can do)

**Rule for every Claude session:** read this file at the start, and end every status report with the open items below
(newest additions included) until the owner marks them done or decides otherwise. Add new items the moment they arise.
Update the status column and date when something changes. Never put secrets here.

Last updated: 8 Nov 2026

| # | Open item | Why it matters | How to do it |
|---|---|---|---|
| 1 | **Resolve the Google Trust & Safety case** (appeal ticket **35DQMHFCOIBXT6BBR6KMUFHCXI**, "Appeal Received" email of 31 Oct promising an answer in about two business days; no decision email as of 6 Nov, still restricted: quota requests refused, Hosting "Site Not Found") (warning of 29 Oct, appeal already submitted) | Google confirmed on 5 Nov that the project has an **active policy restriction** (a quota increase was refused with that reason). It explains Hosting being down ("Site Not Found" even on prohost-f766f.web.app), so pro-host.tech, legal pages, Play domain checks, credential sharing and /listing deep links all fail | (a) Console > IAM & Admin > **Essential Contacts**: the notification with the appeal/remediation steps went to those contacts; check those inboxes incl. Spam. (b) Reply on the 29 Oct compliance thread: the 5 Vertex GPU jobs on 25 Oct were yours, stopped; Vertex disabled; moved provider; lip-sync pipeline removed; no Lyria/Imagen claims (they run on Vertex). (c) Console ribbon > Read more shows appeal status. Claude can draft the text. |
| 2 | **Play Console: finish domain verification, credential sharing and /listing deep links** | All three fail only because https://pro-host.tech/.well-known/assetlinks.json returns "Site Not Found". The repo file already contains Play's 3 fingerprints (49:E8..A3:2D, 5B:A6..14:15, 7D:BC..85:AA), so nothing needs changing | After item 1 lifts the restriction and the URL opens in a browser: Play Console > Turn on credential sharing; Verify again on the /listing domain; then publish/update the app so users get verified links. |
| 3 | ~~Disable the appspot key e5b6952d...~~ **Owner decision 5 Nov: keep it enabled** (accepted risk) | Key was used 29 Oct to 2 Nov from two outside IPs (23.95.170.108, 185.121.179.67); unused since 2 Nov 05:21; the account still has Editor | No action. Claude will not raise it again unless asked. Option if ever wanted: disable (reversible) or narrow the Editor role. |
| 4 | ~~Delete/rotate firebase-adminsdk keys~~ **Owner decision 5 Nov: leave as is** | `02f9b0a5...` has 0 uses in 30 days; `16b78225...` is the live CI key | No action. |
| 5 | **Trim Play Console permissions** of run-billing and run-ig after everything is moved | They were invited with all permissions | Play Console > Users and permissions: keep View app information, View financial data, Manage orders and subscriptions; remove the rest. |
| 6 | **Maps API keys**: send Claude the Android package name, SHA-1 and the web domains | Keys are unrestricted; Claude will measure usage first, then restrict | Package is app.geonajjar.prohost; get the SHA-1 from Play Console > App signing. |
| 7 | **Rotate secrets** (EMAIL_VERIFICATION_SECRET, SMTP password, BOOTSTRAP_SECRET if used) | No rotation yet | Add a new secret version in Console > Secret Manager (never in chat); tell Claude, which redeploys. Rotating EMAIL_VERIFICATION_SECRET invalidates already-sent verification links. |
| 8 | **Accept the second-owner invitation: psy.geo.elnajjar@gmail.com** (**8 Nov: invitation sent, NOT accepted yet**: IAM shows `projectOwnerInvitee`) | Google refuses adding a Gmail account as Owner through the API (SOLO_MUST_INVITE_OWNERS); it must be an emailed invitation from the console | Console > IAM & Admin > IAM > Grant access > New principal psy.geo.elnajjar@gmail.com > role Owner > Save. The invite arrives by email; that person must open it and Accept. |
| 9 | **Uptime check URLs** (after item 1) | No uptime monitoring yet | Send 2 to 4 public URLs (site, sign-in page, a callable health URL). |
| 10 | **Quota increases** (Functions writes 60/min, Cloud Run writes 60/min, Cloud Run instances 100 per project; the 3-hourly watch re-requests 240/240/300 as soon as the restriction lifts) | Claude requested it on 5 Nov; Google blocked it because of the policy restriction (item 1) | Retried automatically by asking Claude after item 1; or Console > IAM & Admin > Quotas. |
| 11 | **App Check enforcement** decision | Unenforced on every service | Needs 99% verified traffic first (docs/APP_CHECK.md). Claude checks the numbers when asked. |
| 12 | **At the very end: remove temporary access** | Least privilege | Delete service account claude-remediation@prohost-f766f; remove env secret GOOGLE_REMEDIATION_CREDENTIALS_JSON; then tell Claude to delete the read-only audit key 60ca2966... |
| 16 | ~~Sheet access cleanup~~ **Done and verified 8 Nov** (only you and run-ig have access) - **Sheet access cleanup** ("ProHost IG Automations") | run-ig now has Editor (done). The default compute account no longer needs it. | Open the Sheet > Share > remove `646730915838-compute@developer.gserviceaccount.com`. Only if you re-run the Maintenance task `igsetup`, also share with firebase-adminsdk-fbsvc@prohost-f766f.iam.gserviceaccount.com as Editor. |
| 17 | **Play Console declarations** (from the Play policy notes) | Required by the 29 Oct policy update | Fine-location declaration; Data safety update (no camera/photo-library permissions); trader status (due Nov 2026); Android 17 location button before 27 Jan 2027 (dev task). |
| 18 | **Create the billing budget with email alerts** | Claude enabled the Budget API but has no permission on billing account 0180BD-0CF1C4-55BA21 | Either: Console > Billing > Budgets & alerts > Create budget > scope project ProHost, amount per month (suggest 50 USD), alerts 50/90/100% + forecast, add email geo.elnajjar@gmail.com. Or: Billing > Account management > Add principal claude-remediation@prohost-f766f.iam.gserviceaccount.com with role Billing Account Costs Manager, tell Claude, and remove it afterwards. |
| 19 | **BOOTSTRAP_SECRET: disable, then delete** (verified 8 Nov: unused by code and services; the intruder's only reads on 5 Nov were refused) | Nothing uses it; disabling is reversible | Console > Secret Manager > BOOTSTRAP_SECRET > Versions > Disable; delete after a quiet week (docs/OWNER_GUIDE.md C6). |
| 20 | Optional: **WhatsApp alerts** (blocked while Hosting is down: the setup page is /admin) and a **refreshed Drive inventory doc** | Health row shows WhatsApp alerts OFF; the Drive "IAM inventory" copy is stale | /admin > WhatsApp alerts for the first; for the second paste docs/GCP_IAM.md into the Drive doc (the connector cannot overwrite content). |
| 21 | **iOS: decide the approach and send Claude the basics** | Nothing can start without it. Plan: docs/IOS_PLAN.md (recommended: native SwiftUI on the existing backend) | Reply with: approach, Individual or Organization, Team ID (developer.apple.com > Membership details), bundle ID (suggest app.geonajjar.prohost), whether you have a Mac. |
| 22 | **iOS: App Store Connect business setup** | Subscriptions cannot be sold without it | App Store Connect > Business: accept Paid Apps Agreement; banking and tax forms; declare trader status (EU). |
| 23 | **iOS: identifiers and keys** | Needed for sign-in, push, purchases, CI | developer.apple.com: create App ID with Sign in with Apple, Push Notifications, Associated Domains, In-App Purchase, App Attest; create APNs Auth Key, App Store Connect API key, In-App Purchase key (.p8 files: never in chat or repo; GitHub secrets / Secret Manager). |
| 24 | **iOS: Firebase iOS app** | Links the app to your backend | Firebase console > add iOS app (bundle ID) > download GoogleService-Info.plist; Authentication > enable Apple; Cloud Messaging > upload APNs key; App Check > App Attest. Note: Firebase changes may be blocked while the policy restriction (item 1) is active. |
| 25 | **iOS: App Store Connect app record, subscriptions and store assets** | Required before TestFlight/review | New app; subscription group ProHost Premium (Monthly 8.99, Yearly 79.99 with the Play intro offers); screenshots (6.9-inch iPhone); privacy/support URLs; App Privacy answers; demo account for App Review. |
| 26 | Optional: **check the billing account type and support plan** | Quotas do not depend on a plan, but a free-trial billing account has lower limits, and only paid Support plans give technical cases (a plan does not override the Trust & Safety review) | Console > Billing > Overview: if a banner says Free trial, press Activate/Upgrade. Console > Support > Compare plans (prices on cloud.google.com/support). |
| 27 | **SECURITY, needs a decision: the appspot key `e5b6952d...` is being used by someone** | On 6 Nov 13:07 to 13:12 UTC it was used from IP 198.23.132.121 (python-urllib / httpx) to **re-enable Vertex AI** (`aiplatform`), call Gemini `GenerateContent` (refused, status 7), list service accounts and request access tokens. The key was last used on 2 Nov before that (185.121.179.67). Vertex is enabled again right now (aiplatform, cloudaicompanion, geminicloudassist, firebasevertexai). This is the same kind of activity Google is reviewing, so it can keep the restriction in place or make it permanent. Owner's 5 Nov decision was to keep the key; this is new evidence. | Recommended: Console > IAM & Admin > Service accounts > prohost-f766f@appspot... > Keys > e5b6952d... > Disable (reversible), and re-disable the AI APIs (Maintenance `apis apply` or tell Claude). Reply "disable key" and Claude does both; also consider removing the Editor role from that account afterwards. | Full evidence: docs/INCIDENT_2026-11-appspot-key.md; the prepared fix package is docs/PENDING_IMPLEMENTATION.md (say GO IMPLEMENT). Use it in the Google appeal and report the abuse to RackNerd.

## Done
- 4 Nov: invited run-billing and run-ig in Play Console; shared the "ProHost IG Automations" Sheet with run-ig.
- 4 Nov 20:37Z: billing self-test pressed (lastSelfTestAt recorded): purchase notifications reach the function under run-billing.
- 5 Nov: duplicate SPF record removed; admin@pro-host.tech confirmed receiving mail; meta-ads-play key recognised (kept).

### Update 7 Nov 2026
- Item 27 (appspot key): DONE, key disabled. Watch alerts 7 days, then approve deletion. If any tool breaks, re-enable with `keys enable`.
- NEW 28: review drafts in `docs/drafts/` and decide whether to send abuse reports / appeal addendum (nothing sent).
- NEW 29: unsuspend/delete exploreprohost99 only if you recognise it (Auth console).
- Later: A3 audit `onAuthUserDeleted` then narrow appspot SA Editor; admin batch needs your "go admin".

### Google identity / legitimacy checklist (7 Nov 2026, owner steps, console only)
30. Google Auth Platform (console.cloud.google.com/auth/branding, project prohost-f766f): app name ProHost, logo, support email, authorized domain `pro-host.tech`, homepage `https://pro-host.tech`, privacy `/privacy`, terms `/terms`; then "Verify branding". Needs the domain verified in Search Console by the owner account.
31. Cloud Console > IAM > Essential Contacts: add geo.elnajjar@gmail.com (Legal, Security, Technical) and the second owner. (The API is disabled for the CI account, so I could not read it.)
32. Business details: Billing/Support profile with the real business name, address and website matching the Play developer account.
33. AI disclosure: attach `docs/GEMINI_USE.md` facts to the appeal if you decide to send the addendum (`docs/drafts/`). AI in use: Claude (Anthropic API) for our own Instagram/WhatsApp assistant (says it is AI, opt-out, kill switch); Cloud Text-to-Speech for our own narration. No Google generative text, image, video or music.

### Auth Platform branding (7 Nov 2026)
34. OAuth warning "not configured to use secure OAuth flows": Google Auth Platform > Clients. Each Android client: "Enable custom URI scheme" OFF. Web client: remove any `http://` / `localhost` origins or redirects (keep the two `/__/auth/handler` https entries). Do not delete any client (7 SHA-1 entries are in use). Tell me which client the warning's "Learn more" names.
35. Branding URLs stay `https://pro-host.tech`, `/privacy`, `/terms` (authorised domains: pro-host.tech, prohost-f766f.web.app, prohost-f766f.firebaseapp.com). Function URLs (`cloudfunctions.net/legalDocumentPage/...`) work for Play and iOS but cannot be used for OAuth branding (Google-owned domain). Press "Verify branding" only when `pro-host.tech/privacy` returns 200. Verify `pro-host.tech` as a Domain property in Search Console (DNS TXT).

### Branding verification fix (7 Nov 2026)
36. Serve pro-host.tech from Netlify (free), deployed automatically from this repo (replaces the Cloudflare/drag-and-drop ideas). Steps in `docs/OWNER_GUIDE.md` B2: Netlify account (not via GitHub) > personal access token > GitHub Actions secret `NETLIFY_AUTH_TOKEN` > Hostinger `A @ 75.2.60.5` and `CNAME www prohost-tech-site.netlify.app` (keep MX/SPF/DKIM). I run the workflow and probe.
37. Then Auth Platform > Branding: privacy `https://pro-host.tech/privacy` (not the cloudfunctions.net URL), Save, Verify branding. Add the Search Console Domain property.

### iOS (7 Nov 2026) - supersedes items 21-25; full plan and how/where for each step in `docs/IOS_PLAN.md` section A
38. Send now: Team ID, confirm bundle ID `app.geonajjar.prohost`, iPhone model + iOS version + tester Apple ID, enrollment status (Individual, 2FA on).
39. Create the App Store Connect API key (Users and Access > Integrations, role App Manager) and add GitHub secrets `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_B64`, `APPLE_TEAM_ID` (never paste the .p8 in chat).
40. Create the App Store Connect app record (name ProHost, SKU `prohost-ios`, English (U.K.), Business); I give exact values.
41. Later (needed by M1-M5): App ID capabilities, APNs + Sign in with Apple + In-App Purchase keys, Firebase iOS app + Apple sign-in + APNs upload + App Attest, Paid Apps Agreement + bank/tax + EU trader status + Small Business Program, subscription group and products, decisions 12(a)-(c), review demo accounts.

### iOS progress (7 Nov 2026)
- Done: Team ID received (`884APQLMP4`), account active, 2FA on, legal name confirmed.
- Next: register the Bundle ID `app.geonajjar.prohost` (Identifiers), then App Store Connect > New App (Name ProHost, SKU `prohost-ios`, English (U.K.), Full Access); request API access and create the App Store Connect API key; decide how to test without an iPhone (borrow one, or a paid cloud device service).

### iOS M0 (7 Nov 2026)
42. Add GitHub secrets (repo > Settings > Secrets and variables > Actions): `ASC_KEY_ID` = HF7C34HW38, `ASC_ISSUER_ID` = 1d848c92-ff55-45cd-9071-e70a7837ee5d, `APPLE_TEAM_ID` = 884APQLMP4, and `ASC_KEY_P8` = the full text of the .p8 (open it in Notepad, copy everything from `-----BEGIN PRIVATE KEY-----` to `-----END PRIVATE KEY-----`, paste as the secret value). Never paste the key in chat. Then run Actions > "iOS TestFlight" > Run workflow.
43. Borrow an iPhone for TestFlight tests (needed from M2/M3): install TestFlight, sign in with the tester Apple ID (psy.geo.elnajjar@gmail.com) and I add it as an App Store Connect user.

### iOS status 7 Nov 2026 (evening) - replaces the key steps of item 42
- Run `iOS TestFlight` (branch `ios-beta/1`) failed only at "Check secrets": ASC_KEY_ID, ASC_ISSUER_ID, APPLE_TEAM_ID, ASC_KEY_P8 are not in GitHub yet. This session cannot set them (the sandbox proxy blocks the Actions secrets API).
- The `.p8` (key HF7C34HW38) was attached in chat, so it is exposed: in App Store Connect > Users and Access > Integrations > App Store Connect API, **revoke HF7C34HW38 and create a new key (App Manager)**, then put the NEW Key ID, Issuer ID, Team ID and the new key's text into the four GitHub secrets yourself. Never attach or paste a .p8 again.
- Then re-run: push any commit to a branch named `ios-beta/<n>` (tell me) or, once merged to main, Actions > iOS TestFlight > Run workflow.
- Upload `GoogleService-Info.plist` (Firebase iOS app is registered; the plist is not secret) as a chat attachment.

### iOS (7 Nov 2026, later)
- `GoogleService-Info.plist` received and committed at `ios/ProHost/GoogleService-Info.plist` (config file, same status as `google-services.json`; Firebase iOS app id 1:646730915838:ios:b816bc76af193424c8eaaf, analytics off until consent).
- Owner decision: keep ASC key HF7C34HW38 (not revoked) even though its .p8 was attached in chat. Risk accepted by the owner; revisit at the first key rotation. Still never attach or paste key files again.

### iOS TestFlight status (7 Nov 2026, 18:12 UTC)
- Secrets now found (Actions secrets); key parses; unsigned archive SUCCEEDS on the runner. Export/upload fails with "Cloud signing permission error": the App Manager API key cannot create the cloud-managed distribution certificate/profile.
- Owner step 44: create a NEW App Store Connect API key with role **Admin** (Users and Access > Integrations > App Store Connect API > "+"), then update GitHub Actions secrets `ASC_KEY_ID` (new Key ID) and `ASC_KEY_P8` (new key text, pasted in GitHub only). Issuer ID and Team ID stay the same. Tell me, I rerun (`ios-beta/<n>` branch push). The old key may stay or be revoked: owner's choice.

### iOS status 7 Nov 2026 (19:00 UTC) - items 42-44 are DONE (secrets, Admin key, first upload)
- TestFlight build 1.0.0 (9) is VALID in App Store Connect (visible under TestFlight once you have a device to install on).
- Still needed from you for sign-in: Firebase console > Authentication > Sign-in method > enable **Apple**; upload the **APNs key** (Cloud Messaging) and register **App Attest** (App Check). The Sign in with Apple key (.p8) and APNs key (.p8) come from developer.apple.com > Keys; paste them only into the Firebase console, never in chat.
- Borrowed iPhone (item 43) still needed from the first interactive build.

### Consolidated guide (7 Nov 2026, night)
- Everything open is now explained step by step in `docs/OWNER_GUIDE.md` (A iOS, B Google/site, C housekeeping). Blockers first: A1 (Firebase Apple provider, APNs key, App Attest), A2 (Google Maps iOS key), A3 (borrowed iPhone + TestFlight group).
- Decisions 7 Nov: Google Maps SDK for iOS; no Admin Console on iOS. Old item 3 (appspot key) is superseded: the key was disabled on 7 Nov by "GO IMPLEMENT".

### Audit of 8 Nov 2026 (read from the live systems; owner statements marked "owner")
| Item | Status |
|---|---|
| Essential Contacts "API disabled" | Explained: the API is switched off in the project (not your account); the console enables it when you add a contact |
| Apple sign-in provider in Firebase | Done (verified) |
| Apple keys (SIWA, APNs), App Attest in App Check, tester team | Done (owner) |
| **APNs key uploaded to Firebase Cloud Messaging** | **Please confirm** (Project settings > Cloud Messaging > Apple app configuration) |
| Maps iOS key restricted | Done (verified) |
| **Firebase "iOS key (auto created by Firebase)" restriction** | **Not applied (verified)**: the API still shows no application restriction. Open the key > Application restrictions > iOS apps > add `app.geonajjar.prohost` > Save, then check the key list |
| Instagram AI-presenter check | Done (owner) |
| Suspicious user disabled | Done (verified) |
| **Second owner** | **Done (verified 8 Nov: both accounts hold roles/owner)** |
| Billing budget | Done (owner; I cannot see billing) |
| **BOOTSTRAP_SECRET** | Safe to disable then delete (verified) |
| Sheet access | Done (verified) |
| **Pull request for the 25 commits not on main** | I open it; you press Merge (item C1) |
| TestFlight (verified 8 Nov via `ios-inspect/2`) | App ProHost exists; builds v1 and v13 VALID; bundle ID has Sign in with Apple, Push, App Attest, In-App Purchase; internal group "Team" has 1 tester, state **INVITED** (the tester has not accepted the TestFlight invitation yet; fine while the iPhone test is postponed) |
| Apple "certificate revoked" email | Expected: each TestFlight build creates a temporary Apple Distribution certificate and deletes it at the end; Apple mails the account holder on revocation. Apple now lists one leftover DEVELOPMENT certificate "Created via API" (valid to Nov 2027, from the cloud-signing step) and no distribution certificate, so nothing needs action; if the email names a different certificate, forward it to me |
| Hosting | **Deployed and tested on Netlify** (8 Nov 08:21 UTC, run 42462564247: ten probes OK on https://prohost-tech-site.netlify.app, domain attached). Only the Hostinger DNS change is left (item 36, docs/OWNER_GUIDE.md B2 step 4: `A @` 199.36.158.100 -> 75.2.60.5, `CNAME www` -> prohost-tech-site.netlify.app) |
| APNs "certificates" in Firebase | Skip: the uploaded authentication key covers development and production |
| Essential Contacts | Recommended: steps in docs/OWNER_GUIDE.md B1 |
| Google restriction (items 1, 2, 10) | Unchanged: quota requests refused, Hosting "Site Not Found" |
