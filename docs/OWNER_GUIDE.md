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
4. Firebase > Project settings (gear) > Cloud Messaging > Apple app configuration > APNs authentication key > **Upload**: choose the APNs .p8, enter its Key ID and Team ID `884APQLMP4`.
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
**B1 [LIFT] Google appeal and contacts**: Essential Contacts (IAM & Admin > Essential Contacts > Add contact: geo.elnajjar@gmail.com with Legal, Security, Technical, Suspension; same for psy.geo.elnajjar@gmail.com); read the drafts in `docs/drafts/` and decide whether to send the appeal addendum and the two abuse reports (nothing has been sent). The watch checks every 3 hours; I will tell you the moment it lifts.
**B2 [ANYTIME] Make pro-host.tech answer**: Cloudflare Pages with Direct Upload (I prepare the zip of `public/`): Cloudflare > Workers & Pages > Create > Pages > Upload assets > name `prohost-site` > upload zip > Deploy > Custom domains > `pro-host.tech`. At Hostinger copy the MX, SPF, DKIM and google-site-verification records, add the site to Cloudflare, then change nameservers at Hostinger to the two Cloudflare ones; test mail to admin@pro-host.tech. Or wait for the lift.
**B3 [after B2/LIFT] Search Console + branding**: search.google.com/search-console > Add property > Domain `pro-host.tech` > add the TXT at DNS > Verify. Auth Platform > Branding: privacy `https://pro-host.tech/privacy`, terms `/terms`, home `https://pro-host.tech`, authorised domains pro-host.tech, prohost-f766f.web.app, prohost-f766f.firebaseapp.com > Save > Verify branding. OAuth warning: Clients > each Android client > "Enable custom URI scheme" OFF; web client: remove `http://` and `localhost` entries. Keep all 7 Android clients.
**B4 [LIFT] Play Console**: after `https://pro-host.tech/.well-known/assetlinks.json` opens in a browser: Play Console > Turn on credential sharing; Verify again on the /listing domain; publish an update so verified links work. Trader status (due Nov 2026), fine-location declaration, Data safety update. Trim run-billing / run-ig to View app information, View financial data, Manage orders and subscriptions.
**B5 [LIFT] Quotas and uptime**: I request Functions 240/min, Cloud Run writes 240, instances 300 the moment the restriction lifts. You send 2-4 public URLs for uptime checks.

### C. Security and housekeeping [ANYTIME]
**C1 Merge my branch**: github.com/Geodev122/ProHost > Pull requests > New > base `main`, compare `claude/implement-containment` > Create > Merge. (Triggers a normal deploy; contains docs, iOS, workflows, `public/_redirects` and `_headers`.)
**C2 Instagram check**: open @prohost.app, scroll Grid, Reels, Stories archive; delete or archive anything showing an AI presenter and tell me which.
**C3 Suspicious user**: Firebase > Authentication > Users > search `exploreprohost99@gmail.com` (disabled). Keep it disabled unless you recognise it.
**C4 Second owner**: IAM & Admin > IAM > Grant access > `psy.geo.elnajjar@gmail.com` > Owner; that person accepts the email invite.
**C5 Billing budget**: Billing > Budgets & alerts > Create budget > scope project ProHost > USD 50 per month > alerts 50/90/100% + forecast > email geo.elnajjar@gmail.com.
**C6 Secrets**: Secret Manager > delete the unused `BOOTSTRAP_SECRET`; rotate `EMAIL_VERIFICATION_SECRET` and the SMTP password only when I say (rotating invalidates old email links).
**C7 Sheet cleanup**: open "ProHost IG Automations" > Share > remove `646730915838-compute@developer.gserviceaccount.com`.
**C8 Final cleanup (only when I say we are done)**: IAM > Service accounts > delete `claude-remediation`; remove the environment secret `GOOGLE_REMEDIATION_CREDENTIALS_JSON`; I delete audit key `60ca2966…`; optionally revoke ASC key HF7C34HW38 after the first rotation.
**C9 Optional**: WhatsApp alerts at /admin (after B2), refresh the Drive "IAM inventory" doc by pasting `docs/GCP_IAM.md`, check billing account type and Support plan.
Note: the appspot key e5b6952d… was disabled on 7 Nov by your "GO IMPLEMENT"; item 3 in the old list is superseded.

