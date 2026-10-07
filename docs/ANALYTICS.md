# ProHost Analytics (GA4 / Google Analytics for Firebase)

How analytics is wired, what is tracked, and the console steps that only the project owner can do.

## How it works

- **Opt-in only.** `AndroidManifest.xml` sets `firebase_analytics_collection_enabled=false` and all
  Consent Mode defaults to denied. On first launch (after splash) `AnalyticsConsentDialog` asks
  **Allow / Don't allow** (equal weight). Profile › Privacy › "Share usage analytics" changes it later.
  Declining or withdrawing turns collection off and calls `resetAnalyticsData()`. Ad storage / ad user
  data / ad personalisation are always denied; no advertising ID or SSAID is collected (`AD_ID`
  permission is removed from the merged manifest).
- **One entry point.** Every event goes through `com.example.analytics.AnalyticsTracker`
  (typed functions). It sanitises params (drops keys like email/phone/name/uid/token, drops values that
  look like an email or phone number, trims strings to 100 chars, max 25 params, max 10 items).
- **Identity.** GA4 `user_id` = the server display code (`U-XXXXXX`), set only after opt-in and only
  once assigned. Never the Firebase UID, email or phone. Crashlytics stays anonymous (no user id).
- **Screens.** Automatic Activity screen reporting is off (it would always say `MainActivity`).
  `ProHostAppRoot` logs one `screen_view` per screen change: `splash`, `auth`, `suspended`, `kyc`,
  `manage_listing`, `space_details`, the drawer full-screen tabs and bottom tabs (by their ids),
  `dialog_<id>` for drawer dialogs and `admin_tab_<n>` for Admin Console tabs.
- **Server events.** `functions/src/lib/ga4.ts` sends Measurement Protocol events for things the app
  can't see: Play RTDN renewals/cancellations/holds/expiry, restores, package lapses, system-rejected
  bookings. It only sends when the person's profile has `analyticsConsent == "GRANTED"` and a
  `gaAppInstanceId` (both mirrored by the app). It never throws into billing/booking code.

## Event catalogue

★ = GA4 recommended event. ⚑ = mark as **key event** in GA4.

| Area | Event | Main params |
|---|---|---|
| Acquisition | `deep_link_open` | `source` (share_link/email/prohost_scheme/web_link), `target` |
| | `notification_open` | `notification_type`, `target` |
| | `notification_permission` | `granted` |
| Auth | `auth_start` | `method` (email/google/phone) |
| | `auth_fallback` | `from`=magic_link, `to`=email_code |
| | `auth_error` | `method`, `code` (machine code only) |
| | ★`login` | `method` (email_link/email_code/google/phone) |
| | ★`sign_up` ⚑ | `method`, `role` |
| | `logout`, `account_deleted` | — |
| | `kyc_start`, `kyc_complete` ⚑ | `source` |
| Discovery | ★`search` | `search_term` (≥3 chars, 1.5 s settled), `result_count` |
| | `filter_apply` | `filter_type` (category/division_type/strategy/price/country/verified_only/saved_only), `filter_value` |
| | `filter_reset`, `map_toggle` | `view` |
| | ★`view_item_list` | `item_list_name` (explore_list/explore_map), `result_count`, `items` (first 10) |
| | ★`select_item` | `item_list_name`, `items[1]` with `index` |
| | ★`view_item` | `items[1]`, `value`, `subdivision_type` |
| | `select_room` | `item_id`, `subdivision_type`, `strategy` |
| | `view_availability` | `item_id`, `subdivision_type`, `open_slot_count` |
| | ★`add_to_wishlist` / `remove_from_wishlist` | `items`, `value` |
| | ★`share` | `method` (share_sheet/copy_link), `content_type`=listing, `item_id` |
| | ★`generate_lead` ⚑ | `channel`=whatsapp, `item_id`, `item_category`, `subdivision_type`, `value` |
| | `contact_specialist` | `channel` (host → specialist WhatsApp) |
| Bookings | ★`begin_checkout` | `content_type`=booking, `items`, `value`, `strategy` |
| | `booking_slot_select` | `strategy`, `slot_count` |
| | `booking_request` ⚑ | `value` (USD total), `strategy`, `attendee_count`, `pricing_mode`, `is_rebook`, `items` |
| | `booking_request_failed` | `reason` (`write_failed` = the request didn't save; or an error code) |
| | `booking_accepted` ⚑ | `value`, `strategy`, `is_rebook` (true = an accepted change to an existing booking) (host side) |
| | `booking_rejected`, `booking_cancelled` | `strategy` / `by`, `reason` |
| | `payment_acknowledged`, `payment_reminder_sent`, `calendar_reminder_add` | `value` |
| Host listings | `listing_create_start`, `listing_draft_saved` | `item_category`, `subdivision_count` |
| | `listing_publish` ⚑ | `item_category`, `country`, `subdivision_count`, `has_attendee_pricing`, `items` |
| | `listing_publish_blocked` | `reason`=no_active_package |
| | `listing_update`, `listing_delete`, `listing_status_change` (`from`,`to`), `listing_verification_request` | |
| | `subdivision_add` (`subdivision_type`,`strategy`,`pricing_mode`), `subdivision_remove`, `blackout_add` | |
| | `listing_price_change` (`item_id`, `strategy` = slot kind MONTHLY/HOURLY/SHIFT/DAY/TIER, `direction` up/down, `affected_count`, `updated_now_count`, `next_term_count`, `kept_count`) — Pro Host "Change price"; no amounts | |
| ProHost Premium | `premium_page_viewed` | `source`, `plans_loaded` |
| | `premium_plan_viewed` | `plan` (monthly / yearly) |
| | `premium_plans_load_failed` | `reason` |
| | `premium_checkout_started` | `plan`, `product_id`, `value`, `currency` |
| | `premium_purchase_failed` | `plan`, `reason` (user_cancelled, billing_error, launch_failed, feature_not_supported, server_activation), `response_code` |
| | `premium_purchase_pending`, `premium_restore_result` (`result`), `redeem_code_open`, `manage_subscription_open`, `order_history_open` | |
| Server only | ⚑`premium_purchase_success` (once per subscription: `plan`, `product_id`, `base_plan_id`, `value`, `currency`, `transaction_id`), `subscription_renewed` (`value`, `currency`), `subscription_restored`, `subscription_expired`, `subscription_recovered/restarted/revoked/on_hold/paused/cancelled/grace_period`, `booking_auto_rejected` | `plan`, `product_id`, `source`=server |
| Admin | `admin_action` | `action`, `result` |
| Errors | `app_error` | `area`, `code` (every `reportFailure`) |

GA4 `items[]` for a listing: `item_id`=`L-…`, `item_name`=title, `item_category`=SpaceType,
`item_category2`=division type (Level2Type), `item_category3`=pricing strategy, `item_category4`=country,
`item_category5`=governorate, `item_variant`=per_attendee/per_booking, `room_id`=`D-…`, `price`, `currency`=USD.

**User properties:** `user_role`, `user_country`, `user_governorate`, `specialty`, `kyc_complete`,
`is_verified`, `plan_id`, `plan_status` (active/expired/none), `listing_count_bucket`,
`account_age_bucket`, `signup_method`, `build_type` (filter out debug testers), `is_demo`.

## Owner setup (Firebase / GA4 console)

Follow these step-by-step instructions in the Firebase, GA4, and Google Cloud consoles:

### Step 1: Link Analytics Property in Firebase
1. Open [Firebase Console](https://console.firebase.google.com/project/prohost-f766f/overview) → **Project Settings** → **Integrations**.
2. Under **Google Analytics**, click **Link** (or confirm the property is linked). Re-download `google-services.json` if prompted.

### Step 2: Create Measurement Protocol API Secret & Firestore Doc
1. Open [Google Analytics Admin](https://analytics.google.com/) → **Data Streams** → Select the Android stream (`app.geonajjar.prohost`).
2. Click **Measurement Protocol API secrets** → Click **Create** → Name it `ProHostServerEvents` → Copy the generated **Secret value**.
3. In [Firebase Console → Firestore Database](https://console.firebase.google.com/project/prohost-f766f/firestore), create document at path `app_config/ga4`:
   - Field: `apiSecret` (string) = `<your_copied_secret_value>`
   - *(Optional)*: Field: `firebaseAppId` (string) = `1:646730915838:android:345a7e12d5994456c8eaaf`
   - *(Optional)*: Field: `debug` (boolean) = `true` (enables GA4 validation logging)
   > **Note:** Firestore security rules deny all client access to `app_config`. Until this document exists in Firestore, server events are safely skipped without breaking any Cloud Function or app feature.
   > **Alternatives:** call the admin callable `configureGa4ApiSecret({ apiSecret })`, or run
   > `GA4_API_SECRET=<secret> node scripts/seed-ga4-config.mjs`. The secret is never stored in the repo
   > (NIGHTHAWK "Secrets & Token Hygiene" flags literals). A secret that was ever committed must be
   > deleted in GA4 Admin and replaced with a new one.

### Step 3: Register Custom Dimensions & Metrics
In [GA4 Admin](https://analytics.google.com/) → **Custom Definitions**:
- **User-Scoped Custom Dimensions**:
  `user_role`, `user_country`, `user_governorate`, `specialty`, `kyc_complete`, `is_verified`, `plan_id`, `plan_status`, `listing_count_bucket`, `account_age_bucket`, `signup_method`, `build_type`, `is_demo`.
- **Event-Scoped Custom Dimensions**:
  `screen_name`, `method`, `role`, `filter_type`, `filter_value`, `strategy`, `subdivision_type`, `pricing_mode`, `channel`, `reason`, `source`, `notification_type`, `item_list_name`, `purchase_type`, `plan_id`, `action`, `area`, `code`.
- **Event-Scoped Custom Metrics**:
  `result_count`, `attendee_count`, `slot_count`, `open_slot_count`, `subdivision_count`.

### Step 4: Mark Key Events (Conversions)
In [GA4 Admin](https://analytics.google.com/) → **Key Events** (Conversions), toggle these 7 events as Key Events:
1. `sign_up` ⚑
2. `kyc_complete` ⚑
3. `generate_lead` ⚑
4. `booking_request` ⚑
5. `booking_accepted` ⚑
6. `listing_publish` ⚑
7. `premium_purchase_success` ⚑

### Step 5: Link BigQuery Export
1. In [Firebase Console](https://console.firebase.google.com/project/prohost-f766f/overview) → **Project Settings** → **Integrations** → **BigQuery** → Click **Link**.
2. Enable **Google Analytics** export (Daily required; streaming optional) and **Crashlytics** export.
3. Select region (`europe-west1`).
4. In [BigQuery Console](https://console.cloud.google.com/bigquery), set dataset `analytics_<propertyId>` default table expiration to **26 months** (per Privacy Policy).

### Step 6: Configure Data Retention & Disable Google Signals
1. In [GA4 Admin](https://analytics.google.com/) → **Data Settings** → **Data Retention**: Set Event Data Retention to **14 months**.
2. In [GA4 Admin](https://analytics.google.com/) → **Data Settings** → **Data Collection**: Turn **Google signals OFF** (privacy compliance; no ad identifiers collected).
3. Under **Data Filters**: Keep "Internal / Developer traffic" active.

### Step 7: Declare in Play Console Data Safety
In [Google Play Console](https://play.google.com/console) → **App Content** → **Data Safety**:
- **App activity**: App interactions, in-app search history (Collected, Optional / Opt-in, Analytics purpose, Encrypted in transit, Not shared).
- **App info and performance**: Crash logs, diagnostics (Collected, Analytics purpose, Encrypted in transit).
- **Device or other IDs**: Firebase App Instance ID (Collected, Optional, Analytics purpose, Encrypted in transit).

## Suggested explorations

- **Specialist funnel:** `view_item_list` → `select_item` → `view_item` → `view_availability` →
  `begin_checkout` → `booking_request` → `booking_accepted`.
- **Lead funnel:** `view_item` → `generate_lead` (WhatsApp).
- **Host funnel:** `listing_create_start` → `listing_publish_blocked` → `premium_page_viewed` →
  `premium_checkout_started` → `premium_purchase_success` → `listing_publish`.
- **Subscription revenue:** billing events use the `premium_*` / `subscription_*` names (owner's choice), so
  GA4's built-in Monetization reports (which read only `purchase`) stay empty. Build a free-form exploration
  summing `value` of `premium_purchase_success` + `subscription_renewed`, split by `plan`. Play Console's
  financial reports remain the source of truth for money.
- **Supply mix:** `listing_publish` by `item_category`, `country`, `has_attendee_pricing`.
- **Demand mix:** `view_item` / `generate_lead` by `item_category`, `item_category2`, `item_category4`.
- **Retention:** cohort exploration split by `user_role`, `signup_method`.
- **Search quality:** `search` where `result_count = 0`.

## Verifying

- Debug a device: `adb shell setprop debug.firebase.analytics.app app.geonajjar.prohost`, then Firebase
  › Analytics › DebugView. Decline → nothing arrives. Allow → walk the funnels above.
- Server: set `debug: true` on `app_config/ga4`, trigger an RTDN test notification from Play Console, and
  read the `ga4 debug` lines in the functions logs.
- Static: `node scripts/prohost-debugger.js` (check "Analytics Hygiene"), `npm test` in `functions/`,
  `AnalyticsTrackerTest` in `app/src/test`.
