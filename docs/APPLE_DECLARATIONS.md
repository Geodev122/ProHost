# Apple declarations: copy-paste answers and open requirements (8 Nov 2026)

Owner-only App Store Connect work plus the engineering gaps that must close before submission. Sibling of `docs/PLAY_DECLARATIONS.md`.
Facts come from `public/privacy.html`, `public/terms.html`, `ios/project.yml`, `ios/ProHost/Resources/PrivacyInfo.xcprivacy`, `docs/IOS_PLAN.md`.
Apple's form wording changes; answer by meaning where a label differs. Never put keys or secrets in any form.

## 1. EU trader status (Digital Services Act), account level
Only the **Account Holder** can do this. App Store Connect > **Business** (left menu) > your company > **Compliance** > **Digital Services Act > Trader status** (if the label differs, search the page for "trader").
1. Choose **"I am a trader"**. Same answer as on Google Play: ProHost sells a paid subscription as a business. (If you ever decide you are not a trader, the answer must be the same on both stores.)
2. Enter the same legal name, postal address, phone and email as in Play Console. Apple shows them on the EU App Store page of the app and verifies the phone and email with codes.
3. Accept the self-certification, Save.
Without it the app is not offered in EU storefronts. TestFlight is unaffected.

## 2. Store listing links (App Information)
- **Privacy Policy URL:** `https://pro-host.tech/privacy`
- **Terms of Use:** keep Apple's standard licence agreement and add the line "Terms of Use: https://pro-host.tech/terms" to the App Description (Guideline 3.1.2). Our Terms Section 15.3 already contains Apple's required minimum EULA terms, so the page also works as a custom EULA if you ever choose that. Section 15.3 asks for the operator's name and address: they are added with the operator block (see section 8).
- **Support URL:** `https://pro-host.tech`  **Marketing URL:** `https://pro-host.tech`
- **Subscription screen in the app** (iOS Premium screen): show title, length, price per period, auto-renew sentence ("Payment will be charged to your Apple ID account at confirmation of purchase. The subscription renews automatically unless cancelled at least 24 hours before the end of the current period...", free-trial forfeiture sentence), "Restore purchases", and working links to Privacy Policy and Terms of Use (mirror Android's `SubscriptionTermsFooter`).

## 3. App Privacy ("nutrition label")
Data collected, all **linked to the user** unless noted. Tracking = **No** (no data used to track; no IDFA; no ATT prompt).
| Apple category | Data type | Purposes | Notes |
|---|---|---|---|
| Contact info | Name, Email address, Phone number | App functionality, account management | Phone = WhatsApp number, self-declared |
| User content | Photos, Other user content | App functionality | Profile and listing photos; listing text; reports and support messages |
| Identifiers | User ID, Device ID | App functionality, security | ProHost code / account id; Firebase installation id and push token |
| Purchases | Purchase history | App functionality | App Store transaction ids and status |
| Location | Precise location, Coarse location | App functionality | Optional; only when the user taps "Locate me"; approximate while the map is open |
| Usage data | Product interaction | Analytics | Only if the user chooses Allow; mark as optional |
| Diagnostics | Crash data, Performance data | App functionality | Crashlytics; **not linked** to identity |
Also answer for the third-party SDKs: Firebase (Auth, Firestore, Functions, Messaging, Storage, App Check, Crashlytics, Analytics) and Google Maps SDK. Their own privacy manifests ship with the SDKs.
Not collected: financial info (cards), health and fitness, contacts, browsing history, search history outside analytics, advertising data.

## 4. Age rating and content
Answer the questionnaire truthfully: user-generated content = **Yes**; unrestricted web access = No; in-app messaging between users = No (users talk on WhatsApp); gambling, violence, mature themes = None. The Terms require users to be 18+ (Section 2); set the minimum age to 18+ if the form offers it.

## 5. Encryption (export compliance)
`ITSAppUsesNonExemptEncryption: false` is already set in `ios/project.yml` (only standard HTTPS/TLS and OS crypto are used). Answer "No" if App Store Connect still asks.

## 6. Account deletion and Sign in with Apple (Guideline 5.1.1(v), 4.8)
- Sign in with Apple is offered next to Google (4.8) and email.
- In-app deletion exists on Android (Profile > Delete Account); the iOS app must offer the same path.
- **Engineering requirement before submission:** when an account that signed in with Apple is deleted, ProHost must revoke the Apple token. Planned design (no Firebase "OAuth code flow"/Services ID needed): at deletion the iOS app asks for a fresh Sign in with Apple authorisation; the server exchanges the authorisation code at `https://appleid.apple.com/auth/token`, then calls `https://appleid.apple.com/auth/revoke` with `client_id` = bundle ID `app.geonajjar.prohost` and a client secret (ES256 JWT, `iss` = Team ID `884APQLMP4`, `kid` = Sign in with Apple key id, `aud` = `https://appleid.apple.com`, `sub` = bundle ID). The key (.p8) lives only in Secret Manager. The web pages (`/delete-account`, Privacy 7.1, Terms) already say the revoke happens, so this must ship with the iOS release.
- Subscriptions are not cancelled by account deletion; the delete confirmation must say so and link to iOS Settings > Subscriptions.

## 7. User-generated content (Guideline 1.2)
Required: a way to filter objectionable content, a way to report content with timely responses, the ability to block abusive users, and published contact details.
| Requirement | Status |
|---|---|
| Terms forbid objectionable content | Done (Terms 8, 9.2) |
| Report mechanism, 24 h acknowledgement | Email route done (Terms 9.3, admin@pro-host.tech); **in-app Report on listing, profile and request screens still to build (Android and iOS, milestone M1)** |
| Block abusive users | **To build (M1)**; Terms say users can ask us by email until then |
| Filtering | Listing review and admin tools exist; confirm before submission |
| Contact details | Done (Terms 1.1, 9.4, privacy contact) |
Submit with the in-app Report/Block built; do not rely on the email route alone.

## 8. Operator block still to insert
Both policies contain an `<!--OPERATOR-->` marker (Privacy 1, Terms 1.1) and Terms 15.3 an `<!--OPERATOR-APPLE-->` marker for the operator's legal name and postal address, as declared in the trader status. Owner sends the exact text; I insert it, run `python3 scripts/legal/sync_legal_content.py`, and push.

## 9. Privacy manifest (`ios/ProHost/Resources/PrivacyInfo.xcprivacy`)
Today it declares no tracking (correct), **no collected data types and no accessed-API reasons**. Before the first App Store submission it must match section 3 (collected types) and list the required-reason APIs the app code uses (typically UserDefaults `CA92.1`, file timestamps, system boot time, disk space as found by Xcode > Product > Archive > Generate Privacy Report). Update it with the screens that introduce them (S2 onward).

## 10. Info.plist purpose strings to add with the features
`NSLocationWhenInUseUsageDescription` (map and "Locate me"), `NSCameraUsageDescription` (only if "Take photo" uses the camera directly). No photo-library string (system picker), no tracking string. Strings must be localised (English, Arabic).

## 11. Review notes (App Store Connect > App Review Information)
- Two demo accounts (a Specialist and a Pro Host) with passwords written only in the review notes field.
- Explain: browsing and requesting spaces is free; publishing needs ProHost Premium (App Store subscription); reviewers can use a sandbox Apple ID.
- Location is used only when the user taps "Locate me"; account deletion is under Profile > Delete Account; the AI assistant runs on Instagram/WhatsApp only, not in the app.
