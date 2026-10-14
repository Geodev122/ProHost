# ProHost 1.0.40 (versionCode 40) — Premium page, Play-configured offers and promo

## What's in it (since 1.0.39)
- **Premium page:**
  - offer-first plan cards: price today, regular price struck through, % off, renewal;
  - "How your offer works": today's price, the date the regular price starts, how to cancel;
  - only the selected card is orange.
- **Strict Play configuration:** every price, offer and promo comes from Play Console.
  - The promo card is the offer tagged `promo` (`prohost2026`: first month free, until 2026-12-31). It opens from
    "Redeem code", which shows a red dot while the promo is live.
  - Plan switches are charged, and described, at the plain base-plan price.
- **Billing disclosures checked against Play's subscriptions policy:**
  - before checkout: price and billing period, auto-renewal, free-trial / intro terms, the date the paid price starts,
    how to cancel, what Premium includes, Terms & Privacy;
  - always available: Manage subscription, Restore, Redeem code.
- **Backend (already live):**
  - Firestore promo campaign retired;
  - Play offer lint added to Maintenance `playcatalog`: no warnings on 2026-10-14.

## Files
The artifact link is filled in once the Release Bundle workflow finishes.

| File | Where it goes in Play Console |
|---|---|
| `app-release.aab` | Test and release › Production › Create new release › App bundles |
| `native-debug-symbols.zip` | App bundle explorer › version 40 › Downloads › Native debug symbols › Upload |
| `mapping.txt` | Same page, "ReTrace mapping file" (optional — Crashlytics already has it) |

## Release details
- **Release name:** `1.0.40 (40)`
- **Release notes:** paste all of `docs/release/1.0.40-notes.txt` (8 languages, each under 500 characters).
- **Rollout:** staged at **20%**. Watch Crashlytics and Android vitals for 48 h, then go to 100%.

## Before you press "Start rollout"
- [ ] In Play Console, offer `prohost2026` is Active on `pro-monthly`:
  - phase: 1 month free;
  - eligibility: new customers;
  - tags: `promo`, `code-prohost2026`, `ends-20261231`, `early-launch`.
- [ ] `launch-50-first-month` is 50% off for 1 month (fixed), and `launch-50-first-year` is 50% off for 1 year.
- [ ] One license-tester account that never subscribed sees:
  - the red dot on Redeem code;
  - the countdown card;
  - Play's checkout showing the free month.
- [ ] App content answers are unchanged: no new permissions and no new data types in this release.

## After rollout
- Run Maintenance `playcatalog`. Expect "playOfferLint: no warnings".
- Run Maintenance `prodcheck`.
- Deactivate `prohost2026` in Play Console when enough hosts have joined. The card disappears on everyone's next
  Premium visit.
