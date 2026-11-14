# App Store Connect: what is ready and what is left (started 14 Nov 2026)

App: **ProHost: Rooms by the Hour** · bundle `app.geonajjar.prohost` · Apple id 6831983164 · version 1.0 "Prepare for submission".
Source of the listing: `ios/store/metadata.json`. Tools: `scripts/ios/asc_store_status.py` (read-only report), `scripts/ios/asc_prepare.py`
(plan / apply). Run them from GitHub → Actions → **iOS Apple inspect** → Run workflow (`prepare`: none · plan · apply); after the next merge to
`main` also as **iOS App Store prepare**. Nothing these tools do submits the app or makes it public.

## Done through Apple's API (14 Nov 2026)
Name and subtitle (English, Arabic) · description, keywords, promotional text, support and marketing URLs (English, Arabic; country-neutral) ·
privacy policy URL · categories Business / Productivity · content-rights declaration · age-rating questionnaire + 18+ override · copyright
"2026 Georges El Najjar" · free app price · available in all 175 countries and regions · capability Associated Domains (plus Sign in with
Apple, Push, In-App Purchase, App Attest) · TestFlight test information and beta-review contact · App Review contact · subscription group
"ProHost Premium": `prohost.premium.monthly` $8.99 and `prohost.premium.yearly` $79.99 with Apple's equivalent price in every country, names in
both languages, launch offers for new subscribers (monthly: 1 month free; yearly: first year $39.99), as on Google Play.

## Still open — Claude
| Item | Needs |
|---|---|
| Screenshots, iPhone 6.9" (English, Arabic) and the subscription review screenshot | being rendered and uploaded; replaced by real iOS captures before submission |
| Review notes + two demo accounts (Specialist, Pro Host) | notes now; accounts when the iOS build exists |
| App Store Server Notifications URLs | the Apple billing backend (milestone D) |
| Build attached to the version | the Compose iOS app (milestone C) |
| Privacy manifest, purpose strings, `apple-app-site-association` | repository work, in progress |

## Still open — owner (Apple offers no API for these, or they are personal details)
1. ~~Paid Apps Agreement~~ Active (owner, 14 Nov 2026).
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
3. ~~Review contact~~, 4. ~~legal name~~, 5. ~~age 18+~~: given 14 Nov 2026 and applied. Please look once at App Store Connect → ProHost →
   **App Information** → Age Rating and confirm it reads 18+ (Apple's older data field only goes up to 17+).
6. **Rewa's invitation.** Apple already holds a team invitation for her e-mail. She opens Apple's e-mail "You're invited to join a development
   team" (from App Store Connect), taps **Accept invitation** and signs in with her Apple ID. If the e-mail is lost: App Store Connect →
   **Users and Access** → her row → **Resend invitation**. Then tell Claude "Rewa accepted": Claude adds her to the TestFlight group and Apple
   sends the TestFlight e-mail; on her iPhone (iOS 17+) she installs the TestFlight app and taps **View in TestFlight** → **Accept**.
7. **Firebase → Authentication → Sign-in method → Apple → Enable → Save** (only if Claude reports that the automatic attempt was refused).
8. **At submission time only:** look at the final screenshots, then App Store Connect → ProHost → **Add for Review**. Claude prepares
   everything before that click.
