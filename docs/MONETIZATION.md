# ProHost Monetization — Billing review, health checks & promo-code playbook

Last reviewed: 2026-10-04 (Play Billing Library 9.1.0).

## 1. Billing implementation review

### Fixed in this pass

| # | Problem | Impact | Fix |
|---|---|---|---|
| 1 | Plan list from Play was **always empty**: `queryProductDetailsAsync`'s callback result was cast with `as? List<*>` (an earlier "fix" used `as? ProductDetailsResult`). Billing 8+ passes neither type, so every cast gave `null`. | Prices never loaded, "Subscribe" failed with "plan not available", and the "No subscription plans were returned" banner showed. | `PlayBillingManager` uses billing-ktx `queryProductDetails()`, which returns a typed `ProductDetailsResult`. The compiler now checks it. |
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

**Next, in priority order:**
1. **Baseline Profile** (`androidx.baselineprofile` with a macrobenchmark module). Usually 20–30% faster cold start and less scroll jank in Compose. It must be generated on a device or emulator, so add it in Android Studio.
2. **List keys:** about 35 `items(...)` calls in `ui/` have no `key =`. Add stable keys (doc ids) so Compose doesn't recompose or reuse the wrong rows when Firestore updates arrive.
3. **Image sizes:** request thumbnails at card size (`ImageRequest.size(…)`) everywhere listing photos appear in lists. Full-resolution photos in lists are the main memory and scroll cost.
4. **Firestore listeners:** most live listeners have no `.limit()`. Fine at the current scale, but paginate Explore and the admin lists (`limit` plus "load more") before reaching about 1,000 listings or users.
5. **Subscriptions UX:** show the trial or promo terms on the plan card first ("Free for 1 month, then …"), keep one primary "Subscribe with Google Play" button, and keep "Redeem a code" visible for non-subscribers.
6. **Play policy hygiene:** keep the renewal disclaimer and price and period text next to every Subscribe button (already present). Keep the "Manage in Play Store" link reachable.
