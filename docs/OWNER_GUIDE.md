# Owner guide: everything only you can do (7 Nov 2026)

Click-by-click steps for each item. Keep `docs/OWNER_TODO.md` as the short status list; this file is the detailed how-to.
Never paste or attach key files (.p8, JSON keys) in chat: keys go only into the GitHub, Firebase, Apple or Google consoles named below.

Legend: **[BLOCKER]** stops me soon, **[LIFT]** waits for Google, **[ANYTIME]**. Never paste or attach key files in chat: keys go only into
GitHub secrets or the Firebase/Apple/Google consoles named below.

### A. iOS: needed now or soon
**A1 [BLOCKER] Firebase: Apple sign-in, APNs, App Attest (about 15 minutes)**
1. developer.apple.com/account > Certificates, Identifiers & Profiles > Keys > "+". Name `ProHost Sign in with Apple`, tick **Sign in with Apple**,
   click Configure, choose primary App ID ProHost, Save, Continue, Register, **Download** (once only) and write down the Key ID.
2. Same page, "+" again. Name `ProHost APNs`, tick **Apple Push Notifications service (APNs)**, Continue, Register, Download, note the Key ID.
3. Firebase console (console.firebase.google.com) > ProHost > Build > Authentication > Sign-in method > Add new provider > **Apple**: leave
   Services ID empty, under "OAuth code flow configuration" enter Apple Team ID `884APQLMP4`, the Sign in with Apple Key ID, and paste the .p8 text. Enable, Save.
4. Firebase > Project settings (gear) > Cloud Messaging > Apple app configuration > APNs authentication key > **Upload**: choose the APNs .p8, enter its Key ID and Team ID `884APQLMP4`. **Skip the "APNs certificates" boxes below it**: the key already covers development and production, never expires and needs no Mac; certificates (.p12, one per environment, expire yearly) are the older alternative and uploading both adds nothing.
5. Firebase > Build > App Check > Apps > the iOS app > **App Attest** > Register (no key needed). Leave enforcement OFF.
6. Tell me "A1 done". (I never see the keys.)

**A2 [BLOCKER for maps] Google Maps iOS key (about 5 minutes)**
1. console.cloud.google.com > project prohost-f766f > APIs & Services > Library > search **Maps SDK for iOS** > Enable.
2. APIs & Services > Credentials > Create credentials > API key. Name it `ProHost iOS Maps`.
3. Edit the key: Application restrictions = **iOS apps**, add bundle ID `app.geonajjar.prohost`; API restrictions = Restrict key = **Maps SDK for iOS** only. Save.
4. Send me the key value in chat (a bundle-restricted key is not a secret; the Android one is also in the app). If Google blocks enabling the API because of the restriction, tell me the exact message.
5. Also restrict the Firebase iOS key that Firebase auto-created on 7 Nov: Credentials > "iOS key (auto created by Firebase)" > Application restrictions = iOS apps, same bundle ID.

**A3 [BLOCKER for testing] An iPhone and TestFlight**
1. Borrow an iPhone with iOS 17 or newer. On it: App Store > install **TestFlight**. Sign in only under Settings > Media & Purchases (no need to touch the lender's iCloud) with your own Apple ID.
2. App Store Connect > Users and Access > Users: you are already a user. TestFlight > Internal Testing > "+" create group `Team` > add yourself (and psy.geo.elnajjar@gmail.com if you add that person under Users and Access > Users > "+" with role App Manager, accept the email invite) > turn on automatic distribution.
3. When I say a build is ready, open the TestFlight app on the phone, Install, and send me screenshots or notes.

**A4 [before Premium] Paid Apps Agreement, bank, tax, trader status, Small Business Program**
1. App Store Connect > Business > Agreements > Paid Apps > Request/Accept; add your bank account and the tax forms (non-US individual: W-8BEN).
2. Same area > **Trader status** (EU Digital Services Act): choose "I am a trader", enter name, address, phone, email (shown publicly on EU product pages).
3. developer.apple.com/app-store/small-business-program > Enroll (Account Holder only). Commission becomes 15% under $1M a year.

**A5 [before Premium] Subscription products** (I give the screenshot of the finished paywall later)
App Store Connect > Apps > ProHost > Monetization > Subscriptions > create group `ProHost Premium`; product `prohost.premium.monthly` (1 month, $8.99, introductory offer: free trial 1 month, new subscribers),
`prohost.premium.yearly` (1 year, $79.99, introductory offer: pay up front $39.99 for the first year, new subscribers). Ranking: yearly above monthly. Add EN and AR display names/descriptions.
Then Users and Access > Integrations > **In-App Purchase** > "+" > create the key, note the Key ID and Issuer, send the IDs (not the .p8) and put the .p8 in GitHub secret `APPLE_IAP_KEY_P8` (name to be confirmed when I write the function).

**A6 [before submission] Store and review package**: privacy URL and support URL (after the site returns; interim function URLs work), support email, two review demo accounts (specialist and Pro Host; passwords only in App Review notes), age rating questionnaire (answers drafted by me), App Privacy answers (drafted by me), your approval of EN and AR texts.

**A7 [decisions] answer in chat**: (a) Google Play subscriber opening the iOS app: "Active via Google Play, manage there" (recommended); (b) who answers abuse reports within 24 hours; (c) iOS prices equal to Play (recommended).

### B. Google project, site and verification [LIFT unless noted]
**B1 [LIFT] Google appeal and contacts**: Essential Contacts (recommended, not needed for the app to run; Google sends policy and appeal notices there. console.cloud.google.com with project `prohost-f766f` selected > search "Essential Contacts"; if it says the API is not enabled press **Enable** (free); **+ Add contact**: email `geo.elnajjar@gmail.com`, confirm it, tick Legal, Security, Suspension, Technical, Technical Incidents and Billing (leave Product Updates off), Save; repeat for `psy.geo.elnajjar@gmail.com`; if Google emails a confirmation link, press it); read the drafts in `docs/drafts/` and decide whether to send the appeal addendum and the two abuse reports (nothing has been sent). The watch checks every 3 hours; I will tell you the moment it lifts.
**B2 [ANYTIME] Make pro-host.tech answer (Netlify, free, deployed automatically from this repo, about 15 minutes once)**
The repo already contains everything (`.github/workflows/site-deploy.yml`, `public/_redirects`, `public/_headers`). You only create the account, one token and the DNS records; every later change to `public/` goes live by itself when it reaches `main`.
1. netlify.com > Sign up (free). Use your email or Google, **not** "Sign up with GitHub" (that would let Netlify read your private repo). Pick the Free plan (as I understand it, the free plan has a monthly credit allowance of roughly 20 production deploys; the workflow only runs when `public/` changes, so it should stay inside. Confirm the plan text on the sign-up page).
2. Avatar (bottom left) > User settings > Applications > Personal access tokens > New access token, name `github-deploy`, no expiry (or 1 year), Generate, copy it.
3. github.com/Geodev122/ProHost > Settings > Secrets and variables > **Actions** > New repository secret: name `NETLIFY_AUTH_TOKEN`, value the token. Tell me "token added". (Do not paste the token in chat.) I then run the deploy; it creates the site `prohost-tech-site`, attaches `pro-host.tech` and probes ten URLs. The first run needs no Site ID.
4. **DNS at Hostinger (the site is already deployed and tested at https://prohost-tech-site.netlify.app, so do this now).** hpanel.hostinger.com > **Domains** > `pro-host.tech` > **DNS / Nameservers** > tab **DNS records**. (The nameservers shown are `lunar.dns-parking.com` and `solar.dns-parking.com`: `dns-parking.com` is Hostinger's own DNS service, so the records you edit in hPanel are the live ones. Nameservers only say where the records are stored; they do not serve the website and are not why it is down. If the page ever lists a different provider, the records live there instead.) Also delete any `AAAA` (IPv6) record on `@` or `www`, and make sure only one `A` record remains on `@`. Edit exactly two existing records with the pencil icon (do not add duplicates):
   | Type | Name | Now | Change to |
   |---|---|---|---|
   | A | `@` | `199.36.158.100` | `75.2.60.5` |
   | CNAME | `www` | `prohost-f766f.web.app` | `prohost-tech-site.netlify.app` |
   Leave alone: every MX record (mail), the TXT `v=spf1 include:_spf.firebasemail.com ~all`, the TXT `firebase=prohost-f766f`, any DKIM (`_domainkey`) and Google verification records. TTL: the lowest offered (300). If Hostinger refuses to edit the A record, delete only that old A record and add a new A `@` = `75.2.60.5`. Going back to Firebase later = put the two old values back.
5. Wait 10 to 60 minutes. I re-run the workflow (it requests the HTTPS certificate once DNS points to Netlify) and check https://pro-host.tech, /privacy, /terms, assetlinks. Send a test mail to admin@pro-host.tech to confirm mail still arrives.
Going back to Firebase later: point `A @` at Firebase Hosting again (the console shows the address); nothing else changes.
**B3 [NOW] Search Console + branding**: search.google.com/search-console (signed in as geo.elnajjar@gmail.com) > property picker > Add property > card **Domain** > `pro-host.tech` > Continue > copy the `google-site-verification=...` TXT value. Hostinger hPanel > Domains > pro-host.tech > DNS / Nameservers > DNS records > add **TXT**, Name `@`, value = pasted text, TTL 300 (keep the two existing TXT records) > back in Search Console press Verify (if not found, wait 10 to 60 minutes and press again; keep the TXT forever). Shortcut: Add property > URL prefix `https://pro-host.tech/` > HTML file (`google74844ccf6165ffc0.html` is already on the site; works only if that token is yours). Then Sitemaps > add `sitemap.xml`. Then Auth Platform > Branding: privacy `https://pro-host.tech/privacy`, terms `/terms`, home `https://pro-host.tech`, authorised domains pro-host.tech, prohost-f766f.web.app, prohost-f766f.firebaseapp.com > Save > Verify branding. OAuth warning: Clients > each Android client > "Enable custom URI scheme" OFF; web client: remove `http://` and `localhost` entries. Keep all 7 Android clients.
**B4 [NOW] Play Console**: `https://pro-host.tech/.well-known/assetlinks.json` is live. Deep links + credential sharing, the fine-location declaration, Data safety and trader status are written out step by step with copy-paste answers in `docs/PLAY_DECLARATIONS.md`. Also trim run-billing / run-ig to View app information, View financial data, Manage orders and subscriptions.
**B5 [LIFT] Quotas and uptime**: I request Functions 240/min, Cloud Run writes 240, instances 300 the moment the restriction lifts. You send 2-4 public URLs for uptime checks.

### C. Security and housekeeping [ANYTIME]
**C1 Merge my branch (I open the pull request; you press Merge)**: github.com/Geodev122/ProHost > Pull requests > New > base `main`, compare `claude/implement-containment` > Create > Merge. (Triggers a normal deploy; contains docs, iOS, workflows, `public/_redirects` and `_headers`.)
**C2 Instagram check**: open @prohost.app, scroll Grid, Reels, Stories archive; delete or archive anything showing an AI presenter and tell me which.
**C3 Suspicious user**: Firebase > Authentication > Users > search `exploreprohost99@gmail.com` (disabled). Keep it disabled unless you recognise it.
**C4 Second owner [PENDING, checked 8 Nov]**: the invitation exists (psy.geo.elnajjar@gmail.com shows as `projectOwnerInvitee`), but it is **not accepted yet**. Open the mailbox of that account (also Spam), find the Google Cloud invitation "…invited you to be an Owner of prohost-f766f", press Accept. I verify afterwards that the role became Owner.
**C5 Billing budget**: Billing > Budgets & alerts > Create budget > scope project ProHost > USD 50 per month > alerts 50/90/100% + forecast > email geo.elnajjar@gmail.com.
**C6 Secrets [SAFE, checked 8 Nov]**: `BOOTSTRAP_SECRET` is referenced by no code and bound by no service; the only attempts to read it (5 Nov, intruder IP) were refused. Cautious order: console.cloud.google.com/security/secret-manager?project=prohost-f766f > BOOTSTRAP_SECRET > tab Versions > row `1` > three dots Actions > **Disable** (it must then read Disabled) (reversible; nothing can break because nothing uses it). If everything stays normal for a week, Delete the secret. Rotate `EMAIL_VERIFICATION_SECRET` and the SMTP password only when I say (rotating invalidates old email links).
**C7 Sheet cleanup**: open "ProHost IG Automations" > Share > remove `646730915838-compute@developer.gserviceaccount.com`.
**C8 Final cleanup (only when I say we are done)**: IAM > Service accounts > delete `claude-remediation`; remove the environment secret `GOOGLE_REMEDIATION_CREDENTIALS_JSON`; I delete audit key `60ca2966…`; optionally revoke ASC key HF7C34HW38 after the first rotation.
**C9 Optional**: WhatsApp alerts at /admin (after B2), refresh the Drive "IAM inventory" doc by pasting `docs/GCP_IAM.md`, check billing account type and Support plan.
Note: the appspot key e5b6952d… was disabled on 7 Nov by your "GO IMPLEMENT"; item 3 in the old list is superseded.

