# Play Console declarations: copy-paste answers (8 Nov 2026)

Owner-only console work. Facts come from the manifest (`app/src/main/AndroidManifest.xml`), `public/privacy.html`, `docs/ANALYTICS.md` and `docs/release/RELEASE_AUDIT_1.0.38.md`.
Google's form wording changes; if a question below does not match, answer by the meaning. Never put keys or secrets in any form.

## 1. Deep links and credential sharing
`https://pro-host.tech/.well-known/assetlinks.json` is live (checked on every site deploy). It carries `delegate_permission/common.handle_all_urls` and `common.get_login_creds` for `app.geonajjar.prohost` with six SHA-256 fingerprints, including Play's three app-signing ones, so the file needs no change.
1. Play Console > app ProHost > left menu **Grow users > Deep links** (or type "Deep links" in the search box at the top).
2. Open the domain `pro-host.tech`, press **Verify again / Re-check** for the domain and for the `/listing` link. Wait for green ticks.
3. Press **Turn on credential sharing**.
4. Publish the next app update so installed apps re-verify the links.

## 2. Precise-location (ACCESS_FINE_LOCATION) declaration
Open the policy notice of 29 Oct (Policy status, Inbox, or Policy and programs > App content) and follow its link. Suggested answers:
- **Why precise location:** the user taps "Locate me" to place a workspace pin exactly (a clinic, office or studio entrance) or to find spaces around their position. A city-level fix is not enough for placing a pin.
- **How it is requested:** at runtime, on the map, only after the user taps "Locate me"; never at launch; one-time (no background or continuous collection); Android 17 location button on API 37+ (developer task, due before 27 Jan 2027).
- **Storage:** the position is used once for that action. A host's saved pin is the host's own listing content.
- **Alternatives:** the user can decline, grant approximate location only, or place the pin by hand; the app still works.
- **Approximate location:** used while the app is open to centre the map and to detect the country at sign-up (coarse only).
- No background location permission is declared.

## 3. Data safety (Policy > App content > Data safety > Manage)
Collected data (all encrypted in transit):
| Category | Data | Optional? | Purpose |
|---|---|---|---|
| Personal info | Name, email address, phone number (WhatsApp, self-declared), user IDs (U- code, account id), profession | Required for the account | App functionality, account management, fraud prevention and security |
| Location | Approximate location; precise location | Optional | App functionality |
| Photos and videos | Profile photo, listing photos | Optional | App functionality |
| Files and docs | Verification document of a listing (Pro Hosts) | Optional | Fraud prevention and security |
| Financial info | Purchase history (Play subscription token, order, plan, status) | Only for subscribers | App functionality, fraud prevention |
| App activity | App interactions, searches and filters | Optional (analytics only if the user chooses Allow) | Analytics |
| App info and performance | Crash logs, diagnostics | Required (anonymised, not linked to the account) | Analytics |
| Device or other IDs | Firebase installation id, push token | Required for push; analytics id optional | App functionality, analytics |
- **Shared with third parties:** No. Google (Firebase, Play) acts as processor; the booking contact exchange between a Specialist and a Pro Host is started by the user.
- **Not collected:** payment card or bank details, contacts, calendar, SMS, messages (chat happens on WhatsApp), advertising ID (`AD_ID` removed), camera or photo-library access.
- **Deletion:** users can request deletion: yes. In-app Profile > Delete account and `https://pro-host.tech/delete-account`.
- **Independent security review:** no. **Families policy:** no. **Ads:** no.
- **Privacy policy URL:** `https://pro-host.tech/privacy`.
- Note: `public/privacy.html` section 2.1 still describes the retired email link/code sign-in and says passwords are not stored; the app signs in with email and password or Google. Update the policy before or with this form (owner's decision on legal pages).

## 4. Trader status (EU Digital Services Act)
Play Console > Settings (gear) > Developer account > account details, or the "Declare trader status" banner. Declare **trader** if ProHost is run for business or professional purposes (it sells a paid subscription); Google then publishes name, address, phone and email on the EU store page and verifies them (code or document). Use business contact details, for example admin@pro-host.tech. This is the owner's own legal declaration.
