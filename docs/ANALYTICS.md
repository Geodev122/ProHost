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
| | `booking_request_failed` | `reason` |
| | `booking_accepted` ⚑ | `value`, `strategy` (host side) |
| | `booking_rejected`, `booking_cancelled` | `strategy` / `by`, `reason` |
| | `payment_acknowledged`, `payment_reminder_sent`, `calendar_reminder_add` | `value` |
| Host listings | `listing_create_start`, `listing_draft_saved` | `item_category`, `subdivision_count` |
| | `listing_publish` ⚑ | `item_category`, `country`, `subdivision_count`, `has_attendee_pricing`, `items` |
| | `listing_publish_blocked` | `reason`=no_active_package |
| | `listing_update`, `listing_delete`, `listing_status_change` (`from`,`to`), `listing_verification_request` | |
| | `subdivision_add` (`subdivision_type`,`strategy`,`pricing_mode`), `subdivision_remove`, `schedule_update`, `blackout_add` | |
| Subscriptions | `view_plans` | `plan_count`, `products_loaded` |
| | `plans_load_failed` | `queried_ids_count` |
| | ★`begin_checkout` | `content_type`=subscription, `items`, `value`, `currency` |
| | ★`purchase` ⚑ | `transaction_id` (sha256(orderId)[0:24]), `value`, `currency`, `items` — client + server, deduped |
| | `purchase_cancelled`, `purchase_error` (`response_code`), `purchase_pending` | |
| | `restore_purchases` (`result`), `redeem_code_open`, `manage_subscription_open`, `order_history_open` | |
| Server only | `purchase` (renewal: `purchase_type`=renewal), `subscription_recovered/restarted/revoked/expired/on_hold/paused/cancelled/grace_period`, `subscription_restored`, `package_lapsed`, `booking_auto_rejected` | `item_id`, `plan_id`, `source`=server |
| Admin | `admin_action` | `action`, `result` |
| Errors | `app_error` | `area`, `code` (every `reportFailure`) |

GA4 `items[]` for a listing: `item_id`=`L-…`, `item_name`=title, `item_category`=SpaceType,
`item_category2`=division type (Level2Type), `item_category3`=pricing strategy, `item_category4`=country,
`item_category5`=governorate, `item_variant`=per_attendee/per_booking, `room_id`=`D-…`, `price`, `currency`=USD.

**User properties:** `user_role`, `user_country`, `user_governorate`, `specialty`, `kyc_complete`,
`is_verified`, `plan_id`, `plan_status` (active/expired/none), `listing_count_bucket`,
`account_age_bucket`, `signup_method`, `build_type` (filter out debug testers), `is_demo`.

## Owner setup (Firebase / GA4 console)

1. **Link GA4.** Firebase console › Project settings › Integrations › Google Analytics › Link (or confirm the
   existing property). Re-download `google-services.json` only if Firebase asks.
2. **Measurement Protocol secret (server events).** GA4 Admin › Data streams › the Android stream
   (`app.geonajjar.prohost`) › Measurement Protocol API secrets › Create. Then in Firestore create the
   document `app_config/ga4` with field `apiSecret` (string). Optional: `firebaseAppId` (defaults to
   `1:646730915838:android:345a7e12d5994456c8eaaf`) and `debug: true` to send to the validation endpoint
   and log responses. Rules deny all client access to `app_config`. Until this doc exists, server events
   are skipped (no deploy dependency).
3. **Custom definitions** (GA4 Admin › Custom definitions):
   - User-scoped: every user property above.
   - Event-scoped dimensions: `screen_name`, `method`, `role`, `filter_type`, `filter_value`, `strategy`,
     `subdivision_type`, `pricing_mode`, `channel`, `reason`, `source`, `notification_type`, `item_list_name`,
     `purchase_type`, `plan_id`, `action`, `area`, `code`.
   - Event-scoped metrics: `result_count`, `attendee_count`, `slot_count`, `open_slot_count`, `subdivision_count`.
4. **Key events:** `sign_up`, `kyc_complete`, `generate_lead`, `booking_request`, `booking_accepted`,
   `listing_publish`, `purchase`.
5. **BigQuery export:** Firebase console › Project settings › Integrations › BigQuery › Link. Enable
   Google Analytics (daily; streaming optional) and Crashlytics. Pick the project's region. In BigQuery
   set the `analytics_<propertyId>` dataset's default table expiration to 26 months (privacy policy).
6. **Data settings:** GA4 Admin › Data retention › 14 months. Google signals **off**. Under Data
   filters, keep "Internal/Developer traffic" active and build reports with `build_type = release` and
   `is_demo = false`.
7. **Play Console › Data safety:** declare *App activity* (app interactions, in-app search history),
   *App info and performance* (crash logs, diagnostics) and *Device or other IDs* (Firebase app instance
   ID) as collected, **optional** (user can opt out), purpose *Analytics*, not shared, encrypted in transit.

## Suggested explorations

- **Specialist funnel:** `view_item_list` → `select_item` → `view_item` → `view_availability` →
  `begin_checkout` → `booking_request` → `booking_accepted`.
- **Lead funnel:** `view_item` → `generate_lead` (WhatsApp).
- **Host funnel:** `listing_create_start` → `listing_publish_blocked` → `view_plans` → `begin_checkout`
  (subscription) → `purchase` → `listing_publish`.
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
