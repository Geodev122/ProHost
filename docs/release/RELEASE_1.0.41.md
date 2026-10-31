# ProHost 1.0.41 (versionCode 41)

## What's in it (since 1.0.40)
- **Explore header:** one row. Search on the left, the logo with the Map | List switch in the centre, filters on the
  right. No bell on Explore (notifications stay on the other tabs' bar).
- **Profile:** compact identity card with a one-word 3D role tag (Specialist · Host · Admin), no "Phone verified" tag;
  "Become a Pro Host" carries a "FOR SPACE OWNERS" tag.
- **Premium:** the 50% banner is gone; plan selection prefers the plan that has offers and a current id over a retired one
  (`pro-monthly1` next to the old `pro-monthly`), so the monthly card shows its offer (4 months free).
- **Phone verification:** failures end with a short reference, e.g. `(ref: app_not_verified)`. It names the failure class
  (region_blocked, quota, invalid_number, app_not_verified, recaptcha_activity, network, other) so a missing SMS can be
  diagnosed on any build. The same code is logged as `auth_error(phone_verification, <kind>)`.

## Files
Artifact **`prohost-release-main-10`** (≈ 22 MB, kept until 2027-01-16):
https://github.com/Geodev122/ProHost/actions/runs/39167924385 → Artifacts. Built from `bb7ca85`; signature and Play app-signing fingerprint checks passed.

| File | Where it goes in Play Console |
|---|---|
| `app-release.aab` | Production › Create new release › App bundles |
| `native-debug-symbols.zip` | App bundle explorer › version 41 › Downloads › Native debug symbols |
| `mapping.txt` | Same page, "ReTrace mapping file" (optional) |

## Release details
- **Release name:** `1.0.41 (41)`; paste `docs/release/1.0.41-notes.txt` (8 languages, each under 500 characters).
- **Rollout:** staged at 20%, watch Crashlytics and Android vitals for 48 h, then 100%.

## Before you press "Start rollout"
- [ ] `pro-monthly1` is ACTIVE with offer `free-4-months`, and the old `pro-monthly` stays inactive.
- [ ] `prohost2026` is recreated on `pro-monthly1` (tags `promo`, `code-prohost2026`, `ends-20261231`) if you want the promo card.
- [ ] On a Play-installed build, request a code for a Lebanese and a Syrian number. If either shows `(ref: …)`, send that
      text. Nothing is sent and nothing shows? Then it's carrier or provider delivery.
