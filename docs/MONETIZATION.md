# ProHost Monetization — Billing review, health checks & promo-code playbook

Last reviewed: 2026-10-04 (Play Billing Library 9.1.0).

## 0. Architecture (refactor of 2026-10-04): Google Play is the only authority

```
Play subscription package_pro_mrr ── base plans pro-montly / pro-yearly
  → Billing SDK (prices, offers, Save %) → purchase token
  → verifyAndRestorePurchase / RTDN / billingSyncJob (daily) → Play Developer API (subscriptionsv2)
  → SubscriptionService (subscriptions/{sha256(token)}) → EntitlementManager → role specialist ↔ prohost
```

- **No admin catalog, no stored prices.** Change prices, trials and offers only in Play Console. The
  `package_plans` document is retired.
- **Admin → Packages → Force Upgrade → ProHost** is the only admin billing action. It is permanent, and
  Users → Revoke Pro Host undoes it.
- **Only `package_pro_mrr` grants Pro Host.** Until 2026-10-04 the app also sold `package_growth_mrr` and
  `package_enterprise_mrr`, so old app versions can still sell them. Deactivate their base plans in Play
  Console › Monetize › Subscriptions so nobody can buy them any more; existing subscribers keep renewing.
  The server never grants or acknowledges a purchase of a retired plan on its own:
  - it parks the purchase with `needsAdmin` and `lastError: unsupported_product: …`;
  - admins get an `UNSUPPORTED_PRODUCT` push;
  - the buyer is told the team will review it.

  In Admin › Packages › Payments needing attention, **Activate** honours it. That writes an `adminOverride`
  link, which also covers its renewals. If you leave it, Google Play refunds the buyer 3 days after purchase.
- **Status to role:**
  - ACTIVE, GRACE_PERIOD, and CANCELED until its expiry: prohost.
  - ON_HOLD, PAUSED, EXPIRED, REVOKED, REFUNDED: specialist.
- **After deploying the refactor:** run Admin → Packages → "Sync with Google Play now" once. It migrates
  existing Pro Hosts: users with a verifiable Play purchase are synced from Google, everyone else becomes a
  forced upgrade, so nobody loses access. Check the `BILLING_SYNC` audit-log entry for the counts.
- **Firestore check:** `subscriptions` holds one document per purchase. `user_profiles.billingStatus`
  mirrors Google's lifecycle.

## 1. Billing implementation review

### Fixed in this pass

| # | Problem | Impact | Fix |
|---|---|---|---|
| 1 | Plan list from Play was **always empty**: `queryProductDetailsAsync`'s callback result was cast with `as? List<*>` (an earlier "fix" used `as? ProductDetailsResult`). Billing 8+ passes a `QueryProductDetailsResult`, so every cast gave `null`. | Prices never loaded, "Subscribe" failed with "plan not available", and the "No subscription plans were returned" banner showed. | Uses the documented typed callback: `queryProductDetailsResult.productDetailsList`, and `unfetchedProductList` for per-product failure reasons. |
| 1b | The device acknowledged purchases itself, before the server had verified and granted them. A failing local signature check blocked real purchases. RTDN acknowledged before checking for a PENDING payment. | Diverged from Play's verify → grant → acknowledge order. A wrong embedded key meant no activation. | Every purchase goes through the backend (`verifyAndRestorePurchase` / RTDN), which verifies, grants, then acknowledges, never while the purchase is pending. The local signature check is advisory. Also: `isFeatureSupported(SUBSCRIPTIONS)` before checkout, `includeSuspendedSubscriptions`, purchases re-queried on every app resume, product details re-queried each time Subscriptions opens. |
| 2 | Purchases started **outside the app** (promo code redeemed in the Play Store, resubscribe from Play) carry no `obfuscatedExternalAccountId`. The RTDN handler threw and retried forever without acknowledging, and `verifyAndRestorePurchase` rejected them as "not your purchase". | Promo-code subscribers could **never** activate. Play auto-refunds/cancels purchases left unacknowledged for 3 days. | New `functions/src/billing/purchaseLinks.ts`. The RTDN handler acknowledges immediately and parks the purchase in `play_billing_unresolved`. The first ProHost account whose phone holds the token claims it (`play_purchase_links`). All later renewals resolve through that link. A token is never re-assigned to another account. |
| 3 | "Restore Purchases" waited for the *next* purchase-list emission, which never comes when the list is unchanged. | Restore could hang silently. | `fetchActivePurchases()` (billing-ktx `queryPurchasesAsync`) returns a fresh list. |
| 4 | Offer selection used whichever offer Play listed first, for both price text and checkout. | Possibly the wrong offer: a trial shown but the base plan charged, or the reverse. | `PlayOfferText.preferredOffer()`: eligible free-trial offer, then intro-price offer, then base plan. Display and checkout share it. |
| 5 | A plan only activated after a Play-Store redemption if the person opened Subscriptions. | Confusing. | The app root checks on every resume for signed-in members without a plan, and links silently. |

### Already correct (keep it this way)
- Billing Library 9.1.0, `enablePendingPurchases(oneTime + prepaid)`, `enableAutoServiceReconnection()`.
- `obfuscatedAccountId` = Firebase UID on every in-app purchase. RTDN maps it to the account.
- Purchases are acknowledged both client-side and server-side (`acknowledgeIfNeeded`). The server grant uses Play's `expiryTimeMillis` as the source of truth, and pending payments (`paymentState 0`) never grant.
- Upgrades pass the old purchase token (`SubscriptionUpdateParams`). Play in-app messages (payment declined or grace period) are shown on Subscriptions.
- Every outcome reaches the UI through `billingMessages` and is tracked in GA4: `begin_checkout`, `purchase`, `purchase_cancelled`, `purchase_error`, `restore_purchases`, `plans_load_failed`, plus server events.

### Fixed 2026-10-04: paid, but "Can't reach the server"
The server used the v1 `purchases.subscriptions.get` and turned **any** Play API failure into
`unavailable`. That included a missing permission, which the app showed as "Can't reach the server". RTDN
dropped the same failure without retrying, so the purchase was never acknowledged, and Play refunds those
after 3 days. The "Activating" banner was also set as soon as the Play sheet opened, so it kept spinning
after a cancel.

What changed:
- The server now uses `subscriptionsv2` and classifies Play errors.
- Failed activations are parked in `play_billing_pending` and retried every 15 minutes.
- RTDN retries.
- The app shows the server's real message, and the banner appears only after Play confirms payment.

**Required one-time setup (otherwise every activation fails with a `config` error in the logs):**
1. **Play Console › Users and permissions › Invite new user:** add the service account that Cloud Functions
   runs as. For 2nd-gen functions this is usually `<project-number>-compute@developer.gserviceaccount.com`.
   It is printed in the `verifyAndRestorePurchase` error log. Grant **View financial data** and **Manage
   orders and subscriptions** for this app.
2. **Google Cloud console › APIs & Services:** enable the **Google Play Android Developer API** for the
   Firebase project.
3. **Check:** Cloud Logging, filter `retryPendingPlayActivations`. A parked purchase should log
   `activated … after N attempt(s)`. In Firestore, `play_billing_pending` with `resolved == false` should
   be empty.

### Still worth doing
1. **Check the licence key.** `PlayBillingSecurity.MERCHANT_BASE64_PUBLIC_KEY` must equal Play Console › Monetization setup › Licensing. If it's wrong, every client-side check fails ("Purchase security check failed") and only the server path activates plans.
2. **Upgrade proration.** Upgrades use `CHARGE_FULL_PRICE`. Consider `CHARGE_PRORATED_PRICE` for upgrades and `DEFERRED` for downgrades once you sell more than one tier.
3. **Remove the hard-coded GA4 secret** from `functions/src/admin/ga4Config.ts` and `scripts/seed-ga4-config.mjs` (it now lives in `app_config/ga4`).

## 2. How to check billing & monetization health

### Before every release (15 min)
1. Install from the **internal testing** track with a **licence-tester** account (Play Console › Settings › License testing). Debug and App Distribution APKs always get `DEVELOPER_ERROR`.
2. Subscriptions screen: real Play prices and trial text appear. **No "No subscription plans were returned" banner.**
3. Buy with the test card "always approves". The plan activates within about 10 s and the role switches to Pro Host.
4. Buy with "always declines" and check that a clear error shows. Cancel the sheet and check "Purchase canceled — you weren't charged".
5. Reinstall, sign in, and tap Restore. It should say "already active" or "restored".
6. Play Console › Monetize › Subscriptions › Real-time developer notifications › **Send test notification**. The Cloud Functions log should show `playBillingRtdn: testNotification`.
7. `node scripts/prohost-debugger.js` should show no CRITICAL or HIGH findings.

### Weekly (Play Console)
- **Monetize › Subscriptions dashboard:** active subscribers, new subscriptions, cancellations, trial→paid conversion, retention by cohort.
- **Order management:** refunds and chargebacks. A spike means a bad offer description or a broken activation.
- **Financial reports › Estimated sales / Earnings:** revenue after Google's fee (15% for subscriptions).
- **Android vitals:** crash rate under 1.09%, ANR rate under 0.47%. Above those thresholds Play limits your visibility.

### Weekly (GA4 / Firebase, see `docs/ANALYTICS.md`)
- **Host funnel exploration:** `listing_create_start` → `listing_publish_blocked` → `view_plans` → `begin_checkout` (content_type = subscription) → `purchase`.
- **Billing errors:** count of `purchase_error` by `response_code`, plus `plans_load_failed`. Both should be about zero.
- **Monetization › Overview:** purchase revenue, ARPPU. Renewals are `purchase` events with `purchase_type = renewal` from the server.
- **Retention** by `plan_status` and `signup_method`.

### Firestore (Firebase console)
- `play_billing_unresolved` where `resolved == false`: purchases waiting for their owner to open the app. They should clear within days. Old ones are people who redeemed but never signed in, so follow up.
- `play_purchase_links`: one document per purchase linked outside the app. During the promo campaign, this is your **redemption count**.

### KPIs to watch

| KPI | Where | Healthy |
|---|---|---|
| View plans → checkout | GA4 funnel | > 25% |
| Checkout → purchase | GA4 funnel | > 60% (lower means price or trust issues) |
| `purchase_cancelled` / `begin_checkout` | GA4 | < 40% |
| Trial → paid | Play subscriptions dashboard | > 40% |
| Month-1 churn | Play dashboard | < 10% |
| Unresolved purchases older than 7 days | Firestore | 0 |

### Runbook: "I paid but I'm not Pro Host" (Play says "activate within 3 days")
Google Play refunds a subscription the server hasn't **acknowledged** within 3 days and warns the buyer.
The server acknowledges only after it has verified the purchase with Google and granted it, so this warning
means verification is failing.

1. **Admin › Packages › Billing health.**
   - **Red "Google Play access is not set up"** (`config`): in Play Console › Users and permissions, invite the
     service account shown, with *View financial data* and *Manage orders and subscriptions*. Then enable the
     Google Play Android Developer API in Google Cloud and tap **Check again**. Nothing activates until this is green.
   - **Amber "no Play notifications received yet"**: set the RTDN topic `play-billing-rtdn`
     (Play Console › Monetize › Monetization setup).
2. **Payments needing attention.** Every paid purchase that isn't active yet is listed with the hours left before the refund.
   - **Retry now**: verify → grant → acknowledge for the account it is parked under.
   - **Activate for…**: the purchase is tagged for another ProHost account. This happens when someone pays while signed in
     to a different ProHost account on the same device; a different *Google Play* email is fine and is never the cause.
     Confirm with the buyer, search their account, and confirm the move. Renewals then follow that account
     (`play_purchase_links` admin override).
3. The user's profile in Admin › Users shows the same rows under "Payments not active yet".

## 3. Promo-code campaign (50 early-subscriber codes)

### How a redemption flows

```
Code shared as https://pro-host.tech/redeem?code=XXXX   (or in-app: prohost://redeem?code=XXXX)
  → "Redeem on Google Play" (play.google.com/redeem?code=XXXX, same Google account as the phone)
  → Play starts the subscription with your promo benefit; RTDN: server acknowledges it and parks it
  → person installs/opens ProHost and signs in
  → app finds the active purchase and calls verifyAndRestorePurchase
  → token claimed for that account; plan granted; role becomes Pro Host
  → later renewals arrive by RTDN and resolve through the link automatically
```

### Setting up
1. Play Console › your app › **Monetize with Play › Promotions** (Promo codes). Create a promotion for the Pro Host subscription you want to give, and set the benefit and the end date. Download the codes.
2. Keep a tracking sheet with columns: code | recipient | channel | sent date | redeemed? | activated (U-code) | converted to paid?
3. Give each person their own link: `https://pro-host.tech/redeem?code=<CODE>`. The page shows the code with a copy button and a one-tap "Redeem on Google Play" button. It also works for people who don't have the app yet.
4. For people who already have the app (WhatsApp, push or email), use `prohost://redeem?code=<CODE>`. It opens Subscriptions with the Redeem dialog prefilled.

### Suggested WhatsApp message
> Hi {name} 👋 You're one of the first 50 Pro Hosts on ProHost! Here's your personal code to list your space free: https://pro-host.tech/redeem?code={CODE}
> 1) Tap the link → Redeem on Google Play (same Google account as your phone)
> 2) Open ProHost and sign in — your Pro Host plan switches on automatically.

### Measuring the campaign
- **Redemptions:** Play Console promotion report (codes redeemed) and the count of `play_purchase_links` documents.
- **Activations:** GA4 `restore_purchases` with `result = restored`, `deep_link_open` with `source = promo_link`, and server event `subscription_restored`.
- **Activation to first listing:** GA4 `listing_publish` by users whose `plan_id` is set. This is the real goal of the campaign.
- **Conversion to paid** when the promo period ends: server `purchase` events with `purchase_type = renewal`, and `subscription_cancelled`, for those accounts.

### Rules of thumb
- One code per person. Codes are single-use and bound to the Google account that redeems them.
- Ask people to redeem with the **same Google account as their phone**. Otherwise the app can't see the purchase, and nothing activates until they sign in to Play with that account.
- In the last week of the promo period, remind them what they'll pay afterwards, and how to cancel (Play › Payments & subscriptions). Clear terms keep refunds and chargebacks down.

## 4. Performance & UX recommendations

**Done now:** the splash screen held for at least 1.3 s on every cold start (a 400 ms fade plus a fixed 900 ms). The fixed part is now 300 ms. Session restore still keeps the splash up when it is genuinely slower.

**Status of the recommendations (2026-10-04):**

| # | Recommendation | Status |
|---|---|---|
| 1 | Baseline Profile | **Partly done.** A hand-written startup profile (`app/src/main/baseline-prof.txt`) covers the launch → Explore path and is compiled into release builds. Compose, Firebase and Coil ship their own library profiles. **Still to do in Android Studio:** add a `:baselineprofile` module (`androidx.baselineprofile` + macrobenchmark), run *Generate Baseline Profile* on a device or emulator, and replace the hand-written file with the generated one. |
| 2 | Stable list keys | **Done.** Every live, data-backed list is keyed by its id: Explore, My Bookings, Favorites, Owner Hub, Renting Progress, Analytics, admin users and listings, plus the admin and drawer audit logs and push alerts. The remaining unkeyed `items()` iterate fixed option lists (weekdays, months, enum values, colour presets), where keys make no difference. |
| 3 | Image sizes | **Already correct.** Listing and map cards request sized images (800 / 200 px), and avatars request 256 px. The other thumbnails use Coil 2.7 `AsyncImage`, which decodes at the composable's own size when none is given. |
| 4 | Firestore pagination | **Deferred on purpose.** Explore filters and searches client-side over the full live list, so paging it means moving search and filters server-side. Revisit at about 1,000 listings or users. Firestore's offline cache already makes repeat loads cheap. |
| 5 | Subscriptions UX | **Done.** Plan cards show the full terms ("Free for 1 month, then $X / month") under the price. The cards are the primary Subscribe buttons. The banner's purchase shortcut now appears only for renewals, with its terms under it. "Redeem Code" stays visible to everyone. |
| 6 | Play policy hygiene | **Verified.** The renewal disclaimer sits under the plan list and on the renewal button. "Manage in Play Store" shows for any active Play subscription. "Restore Purchases" is always available. |
