# App Store Connect: what is ready and what is left (started 14 Nov 2026)

App: **ProHost: Rooms by the Hour** · bundle `app.geonajjar.prohost` · Apple id 6831983164 · version 1.0 "Prepare for submission".
Source of the listing: `ios/store/metadata.json`. Tools: `scripts/ios/asc_store_status.py` (read-only report), `scripts/ios/asc_prepare.py`
(plan / apply). Run them from GitHub → Actions → **iOS Apple inspect** → Run workflow (`prepare`: none · plan · apply); after the next merge to
`main` also as **iOS App Store prepare**. Nothing these tools do submits the app or makes it public.

## Done through Apple's API (14 Nov 2026)
Name and subtitle (English, Arabic) · description, keywords, promotional text, support and marketing URLs (English, Arabic; country-neutral) ·
privacy policy URL · categories Business / Productivity · content-rights declaration · age-rating questionnaire (user-generated content = yes,
everything else none) · free app price · available in all 175 countries and regions · capability Associated Domains (plus the existing Sign in
with Apple, Push, In-App Purchase, App Attest) · TestFlight test information · subscription group "ProHost Premium" with
`prohost.premium.monthly` and `prohost.premium.yearly` (names and descriptions in both languages).

## Still open — Claude
| Item | Needs |
|---|---|
| Subscription prices (monthly $8.99, yearly as on Google Play) in every country | being applied; yearly price read from Play |
| Launch offers (1 month free monthly, 50 % off first year) | Paid Apps Agreement active |
| Screenshots, iPhone 6.9" (English, Arabic) | rendered from the store-screenshot source now; replaced by real iOS captures before submission |
| Review notes + two demo accounts (Specialist, Pro Host) | Maintenance task creates them; passwords only in GitHub secrets |
| App Store Server Notifications URLs | the Apple billing backend (milestone D) |
| Build attached to the version | the Compose iOS app (milestone C) |
| Privacy manifest, purpose strings, `apple-app-site-association` | repository work, in progress |
| Age shown on the store | Apple computed 4+ from the answers; the Terms require 18+. Decision for the owner: set the 18+ override (recommended) |

## Still open — owner (Apple offers no API for these, or they are personal details)
1. **Paid Apps Agreement, tax, bank.** appstoreconnect.apple.com → **Business** → **Agreements**. "Paid Apps" must read **Active** (it is
   processing). If a row shows a warning, open it and complete the tax form or bank details it asks for.
2. **App Privacy.** App Store Connect → **Apps** → ProHost → left menu **App Privacy** → **Get Started** (or **Edit**).
   - *Do you or your third-party partners collect data from this app?* → **Yes, we collect data from this app.**
   - *Tick these 13 data types, nothing else:* Contact Info: **Name**, **Email Address**, **Phone Number** · User Content: **Photos or Videos**,
     **Other User Content** · Identifiers: **User ID**, **Device ID** · Purchases: **Purchase History** · Location: **Precise Location**,
     **Coarse Location** · Usage Data: **Product Interaction** · Diagnostics: **Crash Data**, **Performance Data**.
   - Apple then asks three questions per type. Answers:

   | Data type | Purposes to tick | Linked to the user's identity? | Used for tracking? |
   |---|---|---|---|
   | Name | App Functionality | Yes | No |
   | Email Address | App Functionality | Yes | No |
   | Phone Number | App Functionality | Yes | No |
   | Photos or Videos | App Functionality | Yes | No |
   | Other User Content | App Functionality | Yes | No |
   | User ID | App Functionality, Analytics | Yes | No |
   | Device ID | App Functionality, Analytics | Yes | No |
   | Purchase History | App Functionality | Yes | No |
   | Precise Location | App Functionality | Yes | No |
   | Coarse Location | App Functionality | Yes | No |
   | Product Interaction | Analytics | Yes | No |
   | Crash Data | App Functionality | No | No |
   | Performance Data | App Functionality | No | No |

   - Never tick Third-Party Advertising, Developer's Advertising or Marketing, Product Personalization or Other Purposes.
   - Leave unticked: Physical Address, Other Contact Info, Health, Fitness, Payment Info, Credit Info, Other Financial Info, Contacts, Emails or
     Text Messages, Audio Data, Gameplay Content, Customer Support, Browsing History, Search History, Advertising Data, Other Usage Data,
     Other Diagnostic Data, Sensitive Info, Surroundings, Body, Other Data Types.
   - Press **Publish**. Why each answer: `docs/APPLE_DECLARATIONS.md` section 3.
3. **Review contact.** GitHub → the ProHost repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**, four
   times: `APP_REVIEW_FIRST_NAME`, `APP_REVIEW_LAST_NAME`, `APP_REVIEW_PHONE` (with country code, e.g. +961…), `APP_REVIEW_EMAIL`. Apple's
   reviewers use them only if they have a question. Tell Claude "review contact added"; Claude fills the form.
4. **Legal name** (for the copyright line "2026 <name>" and the legal pages): send it in chat, exactly as in your trader details.
5. **Age 18+ decision** (see the table above): reply "18+" or "keep 4+".
6. **TestFlight on an iPhone** (iOS 17 or later): install the TestFlight app, open the invitation email from Apple, tap **View in TestFlight** →
   **Accept**. If the email is lost: App Store Connect → **Users and Access** → your tester → **Resend invitation**.
7. **Firebase → Authentication → Sign-in method → Apple → Enable → Save** (only if Claude reports that the automatic attempt was refused).
8. **At submission time only:** look at the final screenshots, then App Store Connect → ProHost → **Add for Review**. Claude prepares
   everything before that click.
