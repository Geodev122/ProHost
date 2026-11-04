# Owner to-do list (things only the project owner can do)

**Rule for every Claude session:** read this file at the start, and end every status report with the open items below
(newest additions included) until the owner marks them done or decides otherwise. Add new items the moment they arise.
Update the status column and date when something changes. Never put secrets here.

Last updated: 4 Nov 2026

| # | Open item | Why it matters | How to do it |
|---|---|---|---|
| 1 | **Resolve the Google Trust & Safety case** (warning of 29 Oct, appeal already submitted) | Hosting is down ("Site Not Found" even on prohost-f766f.web.app), so pro-host.tech, the legal pages, Play's domain checks and assetlinks all fail | Reply on the compliance email thread. State: the 5 Vertex GPU jobs on 25 Oct were yours and were stopped; Vertex is disabled; moved to another provider; the lip-sync pipeline is removed; keep claims consistent (Lyria/Imagen run on Vertex, which is off). Check status under the Console ribbon "Read more". Ask Claude to draft the text. |
| 2 | After the site returns: **Play Console > verify the domain again** and compare fingerprints | Play shows "Domain ownership not verified", "JSON content type failed", "Assetlinks failed" because the site is down | Press "Verify again". Play Console > Setup > App signing: check the SHA-256 of the app-signing and upload keys appear in public/.well-known/assetlinks.json (file lists 6; starts 90:5D:E4...10:07 and 35:06:F5...AE:C5). |
| 3 | **Disable the App Engine key `e5b6952d...`** (prohost-f766f@appspot) | Used 29 Oct to 2 Nov from two outside IPs (23.95.170.108 script, 185.121.179.67 Windows browser); could not be confirmed as yours | Console > IAM & Admin > Service accounts > prohost-f766f@appspot... > Keys > Disable (reversible). Tell Claude if anything breaks. |
| 4 | **Rotate the firebase-adminsdk keys** (after item 1) | Two user-managed keys, one is the CI key | Create a new key; update GitHub secret FIREBASE_SERVICE_ACCOUNT; confirm CI deploys; delete both old keys (IDs in docs/GCP_IAM.md). |
| 5 | **Trim Play Console permissions** of run-billing and run-ig after everything is moved | They were invited with all permissions | Play Console > Users and permissions: keep View app information, View financial data, Manage orders and subscriptions; remove the rest. |
| 6 | **Maps API keys**: send Claude the Android package name, SHA-1 and the web domains | Keys are unrestricted; Claude will measure usage first, then restrict | Package is app.geonajjar.prohost; get the SHA-1 from Play Console > App signing. |
| 7 | **Rotate secrets** (EMAIL_VERIFICATION_SECRET, SMTP password, BOOTSTRAP_SECRET if used) | No rotation yet | Add a new secret version in Console > Secret Manager (never in chat); tell Claude, which redeploys. Rotating EMAIL_VERIFICATION_SECRET invalidates already-sent verification links. |
| 8 | **Add a second owner** (email) | Single owner, personal Gmail, no org | Console > IAM > Grant access > role Owner for a trusted second account. |
| 9 | **Uptime check URLs** (after item 1) | No uptime monitoring yet | Send 2 to 4 public URLs (site, sign-in page, a callable health URL). |
| 10 | Optional: **request a higher Cloud Functions write quota** | Deploys hit "Quota Exceeded" and retry (slow) | Console > IAM & Admin > Quotas > Cloud Functions API, V2WriteRequestsPerMinutePerProjectPerRegion, europe-west1. |
| 11 | **App Check enforcement** decision | Unenforced on every service | Needs 99% verified traffic first (docs/APP_CHECK.md). Claude checks the numbers when asked. |
| 12 | **At the very end: remove temporary access** | Least privilege | Delete service account claude-remediation@prohost-f766f; remove env secret GOOGLE_REMEDIATION_CREDENTIALS_JSON; then tell Claude to delete the read-only audit key 60ca2966... |

## Done
- 4 Nov: invited run-billing and run-ig in Play Console; shared the "ProHost IG Automations" Sheet with run-ig.
