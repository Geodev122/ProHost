# Owner to-do list (things only the project owner can do)

**Rule for every Claude session:** read this file at the start, and end every status report with the open items below
(newest additions included) until the owner marks them done or decides otherwise. Add new items the moment they arise.
Update the status column and date when something changes. Never put secrets here.

Last updated: 5 Nov 2026

| # | Open item | Why it matters | How to do it |
|---|---|---|---|
| 1 | **Resolve the Google Trust & Safety case** (warning of 29 Oct, appeal already submitted) | Hosting is down ("Site Not Found" even on prohost-f766f.web.app), so pro-host.tech, the legal pages, Play's domain checks and assetlinks all fail | Reply on the compliance email thread. State: the 5 Vertex GPU jobs on 25 Oct were yours and were stopped; Vertex is disabled; moved to another provider; the lip-sync pipeline is removed; keep claims consistent (Lyria/Imagen run on Vertex, which is off). Check status under the Console ribbon "Read more". Ask Claude to draft the text. |
| 2 | After the site returns: **Play Console > verify the domain again** and compare fingerprints | Play shows "Domain ownership not verified", "JSON content type failed", "Assetlinks failed" because the site is down | Press "Verify again". Play Console > Setup > App signing: check the SHA-256 of the app-signing and upload keys appear in public/.well-known/assetlinks.json (file lists 6; starts 90:5D:E4...10:07 and 35:06:F5...AE:C5). |
| 3 | **Disable the App Engine key `e5b6952d...`** (prohost-f766f@appspot) | Used 29 Oct to 2 Nov from two outside IPs (23.95.170.108 script, 185.121.179.67 Windows browser); could not be confirmed as yours. Not used since 2 Nov 05:21, so disabling is low risk | Console > IAM & Admin > Service accounts > prohost-f766f@appspot... > Keys > Disable (reversible). Tell Claude if anything breaks. |
| 4 | **firebase-adminsdk keys**: delete the unused `02f9b0a5...` now; rotate the CI key `16b78225...` after item 1 | `02f9b0a5` has 0 uses in 30 days; `16b78225` is the live CI key (GitHub runners) | Create a new key; update GitHub secret FIREBASE_SERVICE_ACCOUNT; confirm CI deploys; delete both old keys (IDs in docs/GCP_IAM.md). |
| 5 | **Trim Play Console permissions** of run-billing and run-ig after everything is moved | They were invited with all permissions | Play Console > Users and permissions: keep View app information, View financial data, Manage orders and subscriptions; remove the rest. |
| 6 | **Maps API keys**: send Claude the Android package name, SHA-1 and the web domains | Keys are unrestricted; Claude will measure usage first, then restrict | Package is app.geonajjar.prohost; get the SHA-1 from Play Console > App signing. |
| 7 | **Rotate secrets** (EMAIL_VERIFICATION_SECRET, SMTP password, BOOTSTRAP_SECRET if used) | No rotation yet | Add a new secret version in Console > Secret Manager (never in chat); tell Claude, which redeploys. Rotating EMAIL_VERIFICATION_SECRET invalidates already-sent verification links. |
| 8 | **Add a second owner** (email) | Single owner, personal Gmail, no org | Console > IAM > Grant access > role Owner for a trusted second account. |
| 9 | **Uptime check URLs** (after item 1) | No uptime monitoring yet | Send 2 to 4 public URLs (site, sign-in page, a callable health URL). |
| 10 | Optional: **request a higher Cloud Functions write quota** | Deploys hit "Quota Exceeded" and retry (slow) | Console > IAM & Admin > Quotas > Cloud Functions API, V2WriteRequestsPerMinutePerProjectPerRegion, europe-west1. |
| 11 | **App Check enforcement** decision | Unenforced on every service | Needs 99% verified traffic first (docs/APP_CHECK.md). Claude checks the numbers when asked. |
| 12 | **At the very end: remove temporary access** | Least privilege | Delete service account claude-remediation@prohost-f766f; remove env secret GOOGLE_REMEDIATION_CREDENTIALS_JSON; then tell Claude to delete the read-only audit key 60ca2966... |
| 13 | **Fix the duplicate SPF record on pro-host.tech** | Two `v=spf1` TXT records exist (firebasemail and the retired Hostinger one). More than one SPF record is invalid, so receivers may reject or spam-fold ProHost email | At your DNS host: delete the Hostinger SPF record (`include:_spf.mail.hostinger.com include:_spf.reach.hostinger.com`); keep `v=spf1 include:_spf.firebasemail.com ~all`. Keep the `firebase=prohost-f766f` TXT. Check docs/EMAIL.md if sending via Resend (it needs its own records). |
| 14 | **Confirm admin@pro-host.tech receives mail** | Contact-form messages and campaign BCCs go there, but the Hostinger mailbox was retired on 5 Oct | Send a test email to admin@pro-host.tech from Gmail. If it bounces, add forwarding (or an MX) at your DNS/mail host. |
| 15 | **Identify or disable the `meta-ads-play` service account key `e2d7d31a...`** | No role on the project and no use in 30 days; purpose unknown | Console > IAM > Service accounts > meta-ads-play > Keys: if you do not recognise it, Disable (reversible), then delete after a week. |
| 16 | **Sheet access cleanup** ("ProHost IG Automations") | run-ig now has Editor (done). The default compute account no longer needs it. | Open the Sheet > Share > remove `646730915838-compute@developer.gserviceaccount.com`. Only if you re-run the Maintenance task `igsetup`, also share with firebase-adminsdk-fbsvc@prohost-f766f.iam.gserviceaccount.com as Editor. |
| 17 | **Play Console declarations** (from the Play policy notes) | Required by the 29 Oct policy update | Fine-location declaration; Data safety update (no camera/photo-library permissions); trader status (due Nov 2026); Android 17 location button before 27 Jan 2027 (dev task). |
| 18 | **Set a billing budget with email alerts** | An outside actor ran GPU jobs on 25 Oct; a budget is the early-warning net | Console > Billing > Budgets & alerts > Create budget for the project; thresholds 50/90/100% to geo.elnajjar@gmail.com. |
| 19 | **Delete the unused secret BOOTSTRAP_SECRET** | Nothing in the code or any service uses it | Console > Secret Manager > BOOTSTRAP_SECRET > Delete (or tell Claude to). |
| 20 | Optional: **WhatsApp alerts** and a **refreshed Drive inventory doc** | Health row shows WhatsApp alerts OFF; the Drive "IAM inventory" copy is stale | /admin > WhatsApp alerts for the first; for the second paste docs/GCP_IAM.md into the Drive doc (the connector cannot overwrite content). |

## Done
- 4 Nov: invited run-billing and run-ig in Play Console; shared the "ProHost IG Automations" Sheet with run-ig.
- 4 Nov 20:37Z: billing self-test pressed (lastSelfTestAt recorded): purchase notifications reach the function under run-billing.
